package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.chat.dto.AppChatContinueRequest;
import com.example.sillyspringboot.chat.dto.AppChatRegenerateRequest;
import com.example.sillyspringboot.chat.dto.AppChatStreamRequest;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatAudioTranscriptionService;
import com.example.sillyspringboot.chat.service.ChatAudioSpeechService;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.chat.service.ChatGenerationDispatcher;
import com.example.sillyspringboot.chat.service.ChatGenerationTimeout;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.compat.h5.web.dto.H5ChatPayload;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.example.sillyspringboot.shared.error.GlobalExceptionHandler.MDC_TRACE_ID;

@RestController
@RequestMapping("/api/v1/tavern")
public class ApiV1TavernChatController {

    private static final Logger log = LoggerFactory.getLogger(ApiV1TavernChatController.class);
    private static final int ASSISTANT_ANCHOR_RETRY_ATTEMPTS = 6;
    private static final long ASSISTANT_ANCHOR_RETRY_DELAY_MS = 150L;
    private static final ScheduledExecutorService SSE_HEARTBEAT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "h5-chat-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private final H5ClientUidAuthService h5Auth;
    private final AppConversationService conversationService;
    private final AppChatService chatService;
    private final ChatGenerationDispatcher dispatcher;
    private final ChatAuditService auditService;
    private final ChatAudioTranscriptionService chatAudioTranscriptionService;
    private final ChatAudioSpeechService chatAudioSpeechService;
    private final ChatSnapshotService snapshotService;
    private final AppMessageMapper messageMapper;
    private final H5EntitlementService entitlementService;
    private final H5VisitorTrialGuardService visitorTrialGuardService;
    private final AppFeatureSettingsService featureSettingsService;
    private final SocialUploadRateLimiter rateLimiter;

    public ApiV1TavernChatController(
            H5ClientUidAuthService h5Auth,
            AppConversationService conversationService,
            AppChatService chatService,
            ChatGenerationDispatcher dispatcher,
            ChatAuditService auditService,
            ChatAudioTranscriptionService chatAudioTranscriptionService,
            ChatAudioSpeechService chatAudioSpeechService,
            ChatSnapshotService snapshotService,
            AppMessageMapper messageMapper,
            H5EntitlementService entitlementService,
            H5VisitorTrialGuardService visitorTrialGuardService,
            AppFeatureSettingsService featureSettingsService,
            SocialUploadRateLimiter rateLimiter
    ) {
        this.h5Auth = h5Auth;
        this.conversationService = conversationService;
        this.chatService = chatService;
        this.dispatcher = dispatcher;
        this.auditService = auditService;
        this.chatAudioTranscriptionService = chatAudioTranscriptionService;
        this.chatAudioSpeechService = chatAudioSpeechService;
        this.snapshotService = snapshotService;
        this.messageMapper = messageMapper;
        this.entitlementService = entitlementService;
        this.visitorTrialGuardService = visitorTrialGuardService;
        this.featureSettingsService = featureSettingsService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        String userText = payload.getContent() == null ? "" : payload.getContent().trim();
        if (userText.isBlank() && !hasImageUrls(payload)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "消息内容不能为空");
        }

        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        H5EntitlementService.AccessTicket accessTicket =
                entitlementService.guardChat(clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.GENERATE);
        long conversationId = ensureConversationId(characterId, clientUid, token);
        String clientMessageId = "h5_" + System.currentTimeMillis();

        AppChatStreamRequest req = new AppChatStreamRequest();
        req.setConversationId(conversationId);
        req.setUserMessage(userText);
        req.setImageUrls(payload.getImageUrls());
        req.setVoiceUrl(normalizeVoiceUrl(payload.getVoiceUrl()));
        req.setVoiceDurationMs(normalizeVoiceDurationMs(payload.getVoiceDurationMs()));
        req.setAttachmentMode(payload.getAttachmentMode());
        req.setAttachmentHint(payload.getAttachmentHint());
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        req.setClientMessageId(clientMessageId);

        return runStream(req, token, conversationId, clientMessageId, userText, StreamKind.GENERATE, 0L, accessTicket);
    }

    @PostMapping(value = "/chat")
    public ApiV1Result<Map<String, Object>> chat(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        String userText = payload.getContent() == null ? "" : payload.getContent().trim();
        if (userText.isBlank() && !hasImageUrls(payload)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "消息内容不能为空");
        }

        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        H5EntitlementService.AccessTicket accessTicket =
                entitlementService.guardChat(clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.GENERATE);
        long conversationId = ensureConversationId(characterId, clientUid, token);
        String clientMessageId = "h5_http_" + System.currentTimeMillis();

        AppChatStreamRequest req = new AppChatStreamRequest();
        req.setConversationId(conversationId);
        req.setUserMessage(userText);
        req.setImageUrls(payload.getImageUrls());
        req.setVoiceUrl(normalizeVoiceUrl(payload.getVoiceUrl()));
        req.setVoiceDurationMs(normalizeVoiceDurationMs(payload.getVoiceDurationMs()));
        req.setAttachmentMode(payload.getAttachmentMode());
        req.setAttachmentHint(payload.getAttachmentHint());
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        req.setClientMessageId(clientMessageId);

        return ApiV1Result.ok(
                runBlockingGenerate(req, token, conversationId, clientMessageId, userText, StreamKind.GENERATE, 0L, accessTicket)
        );
    }

    @PostMapping(value = "/chat/continue")
    public ApiV1Result<Map<String, Object>> continueChat(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        H5EntitlementService.AccessTicket accessTicket =
                entitlementService.guardChat(clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.CONTINUE);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String clientMessageId = "h5_http_cont_" + System.currentTimeMillis();

        long anchorId = resolveAssistantAnchor(payload, conversationId);
        AppChatContinueRequest req = new AppChatContinueRequest();
        req.setConversationId(conversationId);
        req.setClientMessageId(clientMessageId);
        req.setTargetMessageId(String.valueOf(anchorId));
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        return ApiV1Result.ok(
                runBlockingGenerate(req, token, conversationId, clientMessageId, "", StreamKind.CONTINUE, anchorId, accessTicket)
        );
    }

    @PostMapping(value = "/chat/regenerate")
    public ApiV1Result<Map<String, Object>> regenerateChat(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        H5EntitlementService.AccessTicket accessTicket =
                entitlementService.guardChat(clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.REGENERATE);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String clientMessageId = "h5_http_regen_" + System.currentTimeMillis();

        long anchorId = resolveAssistantAnchor(payload, conversationId);
        AppChatRegenerateRequest req = new AppChatRegenerateRequest();
        req.setConversationId(conversationId);
        req.setClientMessageId(clientMessageId);
        req.setTargetMessageId(String.valueOf(anchorId));
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        return ApiV1Result.ok(
                runBlockingGenerate(req, token, conversationId, clientMessageId, "", StreamKind.REGENERATE, anchorId, accessTicket)
        );
    }

    @PostMapping(value = "/chat/continue/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter continueStream(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        H5EntitlementService.AccessTicket accessTicket =
                entitlementService.guardChat(clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.CONTINUE);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String clientMessageId = "h5_cont_" + System.currentTimeMillis();
        long anchorId = resolveAssistantAnchor(payload, conversationId);

        AppChatContinueRequest req = new AppChatContinueRequest();
        req.setConversationId(conversationId);
        req.setClientMessageId(clientMessageId);
        req.setTargetMessageId(String.valueOf(anchorId));
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        return runStream(req, token, conversationId, clientMessageId, "", StreamKind.CONTINUE, anchorId, accessTicket);
    }

    @PostMapping(value = "/chat/regenerate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateStream(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        H5EntitlementService.AccessTicket accessTicket =
                entitlementService.guardChat(clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.REGENERATE);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String clientMessageId = "h5_regen_" + System.currentTimeMillis();
        long anchorId = resolveAssistantAnchor(payload, conversationId);

        AppChatRegenerateRequest req = new AppChatRegenerateRequest();
        req.setConversationId(conversationId);
        req.setClientMessageId(clientMessageId);
        req.setTargetMessageId(String.valueOf(anchorId));
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        return runStream(req, token, conversationId, clientMessageId, "", StreamKind.REGENERATE, anchorId, accessTicket);
    }

    @PostMapping("/chat/stop")
    public ApiV1Result<Boolean> chatStop(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        ConversationDetailDto detail = conversationService.findDetailByH5Character(clientUid, characterId, token);
        if (detail == null) {
            return ApiV1Result.ok(true);
        }
        return ApiV1Result.ok(chatService.stop(detail.conversationId(), token));
    }

    @PostMapping("/reply-suggestions")
    public ApiV1Result<Map<String, Object>> replySuggestions(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        entitlementService.guardChat(clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.GENERATE);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        List<String> suggestions = chatService.suggestReplies(conversationId, token, payload == null ? "" : payload.getContent());
        Map<String, Object> data = new HashMap<>();
        data.put("suggestions", suggestions);
        data.put("conversationId", conversationId);
        return ApiV1Result.ok(data);
    }

    @PostMapping(value = "/chat/transcribe-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiV1Result<Map<String, Object>> transcribeAudio(
            @RequestPart("file") MultipartFile file,
            @RequestParam("clientUid") String clientUid
    ) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        String safeClientUid = requireClientUidValue(clientUid);
        visitorTrialGuardService.guardAnonymousChatAttempt(safeClientUid);
        String token = h5Auth.issueTokenForClientUid(safeClientUid);
        long userId = chatService.resolveUserId(token);
        rateLimiter.checkUpload(userId, "tavern_audio_transcribe");
        ChatAudioTranscriptionService.AudioTranscriptionResult result =
                chatAudioTranscriptionService.transcribeForUser(userId, file);
        Map<String, Object> data = new HashMap<>();
        data.put("text", result.text());
        data.put("modelName", result.modelName());
        data.put("audioUrl", result.audioUrl());
        return ApiV1Result.ok(data);
    }

    @PostMapping(value = "/chat/tts")
    public ApiV1Result<Map<String, Object>> synthesizeSpeech(@RequestBody H5ChatPayload payload) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        String clientUid = requireClientUid(payload);
        String text = payload.getContent() == null ? "" : payload.getContent().trim();
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音内容不能为空");
        }
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = chatService.resolveUserId(token);
        ChatAudioSpeechService.AudioSpeechResult result = chatAudioSpeechService.synthesizeForUser(
                userId,
                text,
                payload.getTtsModelName(),
                payload.getTtsVoiceName(),
                payload.getTtsVoiceTemplateCode()
        );
        Map<String, Object> data = new HashMap<>();
        data.put("audioDataUrl", "data:" + result.mimeType() + ";base64," + Base64.getEncoder().encodeToString(result.audioBytes()));
        data.put("mimeType", result.mimeType());
        data.put("modelName", result.modelName());
        data.put("voiceName", result.voiceName());
        return ApiV1Result.ok(data);
    }

    private enum StreamKind { GENERATE, CONTINUE, REGENERATE }

    private static String channelFor(StreamKind kind, boolean streaming) {
        return switch (kind) {
            case GENERATE -> streaming ? "CHAT_STREAM" : "CHAT_SYNC";
            case CONTINUE -> streaming ? "CONTINUE_STREAM" : "CONTINUE";
            case REGENERATE -> streaming ? "REGEN_STREAM" : "REGEN";
        };
    }

    private static boolean shouldFailEmptyGeneratedContent(StreamKind kind, boolean cancelled, String content) {
        return !cancelled
                && (kind == StreamKind.CONTINUE || kind == StreamKind.REGENERATE)
                && (content == null || content.isBlank());
    }

    private static String emptyGeneratedContentMessage(StreamKind kind) {
        return kind == StreamKind.CONTINUE
                ? "模型返回空内容，续写失败。请检查当前模型/厂商是否支持续写格式。"
                : "模型返回空内容，重新生成失败。请检查当前模型/厂商配置。";
    }

    private Map<String, Object> runBlockingGenerate(
            Object request,
            String token,
            long conversationId,
            String clientMessageId,
            String userMessage,
            StreamKind kind,
            long anchorOrTargetMessageId,
            H5EntitlementService.AccessTicket accessTicket
    ) {
        String traceId = traceId();
        StStreamControl control = new StStreamControl();
        boolean ensureUserMessage = kind == StreamKind.GENERATE
                && request instanceof AppChatStreamRequest streamRequest
                && hasImageUrls(streamRequest);
        String voiceUrl = request instanceof AppChatStreamRequest streamRequest
                ? normalizeVoiceUrl(streamRequest.getVoiceUrl())
                : null;
        Integer voiceDurationMs = request instanceof AppChatStreamRequest streamRequest
                ? normalizeVoiceDurationMs(streamRequest.getVoiceDurationMs())
                : null;
        ChatAuditService.AuditContext audit = auditService.onQueued(
                conversationId,
                userMessage,
                clientMessageId,
                token,
                traceId,
                channelFor(kind, false),
                ensureUserMessage,
                voiceUrl,
                voiceDurationMs
        );
        try (var lease = chatService.acquireLease(token);
             ChatGenerationTimeout timeout = ChatGenerationTimeout.start(control, chatService.generationTimeoutSeconds())) {
            auditService.onGenerating(audit.assistantMessageId(), audit.taskId(), traceId);
            snapshotService.ensureSnapshot(conversationId);
            StringBuilder assistant = new StringBuilder();
            switch (kind) {
                case GENERATE -> {
                    String userRef = audit.userMessageId() > 0
                            ? ("root:" + audit.userMessageId())
                            : ("client:" + clientMessageId);
                    chatService.streamGenerate(
                            (AppChatStreamRequest) request, token, userRef, c -> appendDelta(assistant, c), control
                    );
                }
                case CONTINUE -> chatService.streamContinue(
                        (AppChatContinueRequest) request, token, c -> appendDelta(assistant, c), control
                );
                case REGENERATE -> chatService.streamRegenerate(
                        (AppChatRegenerateRequest) request, token, c -> appendDelta(assistant, c), control
                );
            }

            if (timeout.isTimedOut()) {
                auditService.onFailed(
                        audit.assistantMessageId(),
                        audit.taskId(),
                        ErrorCode.UPSTREAM_ERROR,
                        traceId,
                        "generation timed out after " + chatService.generationTimeoutSeconds() + " seconds"
                );
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试");
            }

            String content = assistant.toString().trim();
            if (shouldFailEmptyGeneratedContent(kind, false, content)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, emptyGeneratedContentMessage(kind));
            }
            applyBlockingPostStream(kind, conversationId, anchorOrTargetMessageId, audit, content, token, traceId);
            if (kind == StreamKind.GENERATE) {
                saveSnapshotQuietly(conversationId);
            }
            entitlementService.recordSuccessfulChat(accessTicket, !content.isBlank());
            Map<String, Object> done = buildDonePayload(kind, anchorOrTargetMessageId, audit, content);
            done.put("cancelled", false);
            return done;
        } catch (BusinessException be) {
            auditService.onFailed(audit.assistantMessageId(), audit.taskId(), be, traceId);
            throw be;
        } catch (Exception ex) {
            auditService.onFailed(audit.assistantMessageId(), audit.taskId(), ex, ErrorCode.INTERNAL_ERROR, traceId);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "服务暂时不可用，请稍后重试");
        } finally {
            chatService.unregisterControl(conversationId);
        }
    }

    private void applyBlockingPostStream(
            StreamKind kind,
            long conversationId,
            long anchorOrTargetMessageId,
            ChatAuditService.AuditContext audit,
            String content,
            String token,
            String traceId
    ) {
        switch (kind) {
            case GENERATE -> {
                auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), content, traceId);
                markUserMessageSuccessIfQueued(audit);
                try {
                    // A：写回 ST chat，绑定 assistant ↔ message_ref（root:<assistantMessageId>）
                    chatService.syncAssistantReplyToSt(conversationId, "root:" + audit.assistantMessageId(), content, token);
                } catch (Exception ignored) {
                }
            }
            case CONTINUE -> chatService.finalizeContinueAsMessage(
                    conversationId,
                    anchorOrTargetMessageId,
                    audit.assistantMessageId(),
                    audit.taskId(),
                    content,
                    token
            );
            case REGENERATE -> {
                if (content.isBlank()) {
                    auditService.onFailed(
                            audit.assistantMessageId(),
                            audit.taskId(),
                            ErrorCode.VALIDATION_FAILED,
                            traceId,
                            "重新生成结果为空"
                    );
                    messageMapper.deleteById(audit.assistantMessageId());
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "重新生成结果为空");
                }
                auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), content, traceId);
                chatService.promoteRegenerateVariant(
                        conversationId,
                        anchorOrTargetMessageId,
                        audit.assistantMessageId(),
                        token
                );
            }
        }
    }

    private void applyStreamPostGenerate(
            StreamKind kind,
            long conversationId,
            long anchorOrTargetMessageId,
            ChatAuditService.AuditContext audit,
            String content,
            boolean cancelled,
            String token,
            String traceId
    ) {
        switch (kind) {
            case GENERATE -> {
                if (cancelled) {
                    auditService.onStopped(audit.assistantMessageId(), audit.taskId(), content, traceId);
                } else {
                    auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), content, traceId);
                }
                markUserMessageSuccessIfQueued(audit);
                try {
                    chatService.syncAssistantReplyToSt(conversationId, "root:" + audit.assistantMessageId(), content, token);
                } catch (Exception ignored) {
                }
            }
            case CONTINUE -> {
                if (cancelled && content.isBlank()) {
                    chatService.abortContinueEmpty(conversationId, audit.assistantMessageId(), audit.taskId(), token);
                } else {
                    chatService.finalizeContinueAsMessage(
                            conversationId,
                            anchorOrTargetMessageId,
                            audit.assistantMessageId(),
                            audit.taskId(),
                            content,
                            token
                    );
                }
            }
            case REGENERATE -> {
                if (content.isBlank()) {
                    if (cancelled) {
                        auditService.onStopped(audit.assistantMessageId(), audit.taskId(), "", traceId);
                    } else {
                        auditService.onFailed(
                                audit.assistantMessageId(),
                                audit.taskId(),
                                ErrorCode.VALIDATION_FAILED,
                                traceId,
                                "重新生成结果为空"
                        );
                        messageMapper.deleteById(audit.assistantMessageId());
                    }
                } else {
                    if (cancelled) {
                        auditService.onStopped(audit.assistantMessageId(), audit.taskId(), content, traceId);
                    } else {
                        auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), content, traceId);
                    }
                    try {
                        // A：regenerate 的 ST 语义是“替换目标 assistant”，所以 message_ref 绑定 root:<targetMessageId>
                        chatService.syncAssistantReplyToSt(conversationId, "root:" + anchorOrTargetMessageId, content, token);
                    } catch (Exception ignored) {
                    }
                    chatService.promoteRegenerateVariant(
                            conversationId,
                            anchorOrTargetMessageId,
                            audit.assistantMessageId(),
                            token
                    );
                }
            }
        }
    }

    private Map<String, Object> buildDonePayload(
            StreamKind kind,
            long anchorOrTargetMessageId,
            ChatAuditService.AuditContext audit,
            String streamedRaw
    ) {
        Map<String, Object> done = new HashMap<>();
        if (kind == StreamKind.GENERATE) {
            done.put("content", streamedRaw);
            done.put("messageId", h5MessageId(audit.assistantMessageId()));
            if (audit.userMessageId() > 0) {
                done.put("userMessageId", h5MessageId(audit.userMessageId()));
            }
            done.put("swipes", List.of(streamedRaw));
            done.put("swipeIndex", 0);
            return done;
        }
        if (kind == StreamKind.CONTINUE) {
            AppMessage continuation = messageMapper.findById(audit.assistantMessageId());
            String content = continuation == null || continuation.getContent() == null ? "" : continuation.getContent().trim();
            H5SwipeStateSupport.SwipeState swipeState = H5SwipeStateSupport.build(continuation, messageMapper);
            done.put("content", content);
            done.put("messageId", h5MessageId(audit.assistantMessageId()));
            done.put("messageKind", "CONTINUATION");
            done.put("continueFromMessageId", h5MessageId(anchorOrTargetMessageId));
            done.put("swipes", swipeState.swipes());
            done.put("swipeIndex", swipeState.swipeIndex());
            return done;
        }
        fillRegenerateDone(done, anchorOrTargetMessageId);
        return done;
    }

    private static String h5MessageId(long dbId) {
        return "db_" + dbId;
    }

    private void markUserMessageSuccessIfQueued(ChatAuditService.AuditContext audit) {
        if (audit.userMessageId() <= 0) {
            return;
        }
        AppMessage userMessage = messageMapper.findById(audit.userMessageId());
        if (userMessage == null || !"user".equalsIgnoreCase(userMessage.getRole())) {
            return;
        }
        if (!"QUEUED".equalsIgnoreCase(userMessage.getStatus())
                && !"GENERATING".equalsIgnoreCase(userMessage.getStatus())) {
            return;
        }
        String text = userMessage.getContent() == null ? "" : userMessage.getContent();
        messageMapper.updateStatusAndContent(
                userMessage.getId(),
                "SUCCESS",
                text,
                userMessage.getErrorCode(),
                userMessage.getTraceId()
        );
    }

    private void fillRegenerateDone(Map<String, Object> done, long targetMessageId) {
        AppMessage target = messageMapper.findById(targetMessageId);
        if (target == null) {
            done.put("content", "");
            done.put("messageId", h5MessageId(targetMessageId));
            done.put("swipes", List.of(""));
            done.put("swipeIndex", 0);
            return;
        }
        String ref = target.getStMessageRef();
        if (ref != null && !ref.isBlank()) {
            H5SwipeStateSupport.SwipeState swipeState = H5SwipeStateSupport.build(target, messageMapper);
            done.put("swipes", swipeState.swipes());
            done.put("swipeIndex", swipeState.swipeIndex());
        } else {
            done.put("swipes", List.of(target.getContent() == null ? "" : target.getContent()));
            done.put("swipeIndex", 0);
        }
        done.put("content", target.getContent() == null ? "" : target.getContent().trim());
        done.put("messageId", h5MessageId(targetMessageId));
    }

    private SseEmitter runStream(
            Object request,
            String token,
            long conversationId,
            String clientMessageId,
            String userMessage,
            StreamKind kind,
            long anchorOrTargetMessageId,
            H5EntitlementService.AccessTicket accessTicket
    ) {
        SseEmitter emitter = new SseEmitter(chatService.sseTimeoutMillis());
        StStreamControl control = new StStreamControl();
        chatService.registerControl(conversationId, control);
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter, control);
        emitter.onTimeout(() -> {
            cancelHeartbeat(heartbeat);
            control.cancel();
            chatService.unregisterControl(conversationId);
            emitter.complete();
        });
        emitter.onError(ex -> {
            cancelHeartbeat(heartbeat);
            control.cancel();
            chatService.unregisterControl(conversationId);
        });
        emitter.onCompletion(() -> {
            cancelHeartbeat(heartbeat);
            control.cancel();
            chatService.unregisterControl(conversationId);
        });
        sendEvent(emitter, "ping", Map.of());

        boolean ensureUserMessage = kind == StreamKind.GENERATE
                && request instanceof AppChatStreamRequest streamRequest
                && hasImageUrls(streamRequest);
        String voiceUrl = request instanceof AppChatStreamRequest streamRequest
                ? normalizeVoiceUrl(streamRequest.getVoiceUrl())
                : null;
        Integer voiceDurationMs = request instanceof AppChatStreamRequest streamRequest
                ? normalizeVoiceDurationMs(streamRequest.getVoiceDurationMs())
                : null;
        String traceId = traceId();
        ChatAuditService.AuditContext audit = auditService.onQueued(
                conversationId,
                userMessage,
                clientMessageId,
                token,
                traceId,
                channelFor(kind, true),
                ensureUserMessage,
                voiceUrl,
                voiceDurationMs
        );

        try {
            dispatcher.submit(() -> {
                try {
                    long start = System.nanoTime();
                    long maxWaitNanos = Duration.ofSeconds(chatService.maxQueueWaitSeconds()).toNanos();
                    while (!control.isCancelled()) {
                        try (var lease = chatService.acquireLease(token);
                             ChatGenerationTimeout timeout = ChatGenerationTimeout.start(control, chatService.generationTimeoutSeconds())) {
                            snapshotService.ensureSnapshot(conversationId);

                            StringBuilder assistant = new StringBuilder();
                            switch (kind) {
                                case GENERATE -> {
                                    String userRef = audit.userMessageId() > 0
                                            ? ("root:" + audit.userMessageId())
                                            : ("client:" + clientMessageId);
                                    chatService.streamGenerate(
                                            (AppChatStreamRequest) request,
                                            token,
                                            userRef,
                                            c -> streamDelta(emitter, assistant, c, control),
                                            control
                                    );
                                }
                                case CONTINUE -> chatService.streamContinue(
                                        (AppChatContinueRequest) request,
                                        token,
                                        c -> streamDelta(emitter, assistant, c, control),
                                        control
                                );
                                case REGENERATE -> chatService.streamRegenerate(
                                        (AppChatRegenerateRequest) request,
                                        token,
                                        c -> streamDelta(emitter, assistant, c, control),
                                        control
                                );
                            }

                            boolean timedOut = timeout.isTimedOut();
                            boolean cancelled = control.isCancelled();
                            String content = assistant.toString().trim();
                            if (timedOut) {
                                auditService.onFailed(
                                        audit.assistantMessageId(),
                                        audit.taskId(),
                                        ErrorCode.UPSTREAM_ERROR,
                                        traceId,
                                        "generation timed out after " + chatService.generationTimeoutSeconds() + " seconds"
                                );
                                sendEvent(emitter, "error", Map.of("message", "生成超时，请稍后重试"), control);
                                emitter.complete();
                                return;
                            }
                            if (shouldFailEmptyGeneratedContent(kind, cancelled, content)) {
                                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, emptyGeneratedContentMessage(kind));
                            }
                            applyStreamPostGenerate(
                                    kind,
                                    conversationId,
                                    anchorOrTargetMessageId,
                                    audit,
                                    content,
                                    cancelled,
                                    token,
                                    traceId
                            );

                            if (kind == StreamKind.REGENERATE && !cancelled && content.isBlank()) {
                                sendEvent(emitter, "error", Map.of("message", "重新生成结果为空"));
                                emitter.complete();
                                return;
                            }

                            if (kind == StreamKind.GENERATE && !cancelled) {
                                saveSnapshotQuietly(conversationId);
                            }

                            entitlementService.recordSuccessfulChat(accessTicket, !content.isBlank());

                            Map<String, Object> done = buildDonePayload(kind, anchorOrTargetMessageId, audit, content);
                            done.put("cancelled", cancelled);
                            sendEvent(emitter, "done", done, control);
                            emitter.complete();
                            return;
                        } catch (BusinessException be) {
                            if (be.getErrorCode() != ErrorCode.SERVICE_BUSY
                                    && be.getErrorCode() != ErrorCode.RATE_LIMITED) {
                                throw be;
                            }
                            if (System.nanoTime() - start > maxWaitNanos) {
                                throw new BusinessException(ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试");
                            }
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new BusinessException(ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试");
                            }
                        }
                    }

                    Map<String, Object> done = new HashMap<>();
                    done.put("content", "");
                    done.put("messageId", h5MessageId(audit.assistantMessageId()));
                    if (audit.userMessageId() > 0) {
                        done.put("userMessageId", h5MessageId(audit.userMessageId()));
                    }
                    done.put("swipes", List.of(""));
                    done.put("swipeIndex", 0);
                    done.put("cancelled", true);
                    sendEvent(emitter, "done", done, control);
                    emitter.complete();
                } catch (BusinessException be) {
                    log.warn("h5 stream business error conversationId={} kind={} code={} message={}",
                            conversationId, kind, be.getErrorCode(), be.getMessage());
                    auditService.onFailed(audit.assistantMessageId(), audit.taskId(), be, traceId);
                    sendEvent(emitter, "error", Map.of("message", be.getMessage()), control);
                    emitter.complete();
                } catch (Exception ex) {
                    log.error("h5 stream unhandled error conversationId={} kind={} clientMessageId={}",
                            conversationId, kind, clientMessageId, ex);
                    auditService.onFailed(audit.assistantMessageId(), audit.taskId(), ex, ErrorCode.INTERNAL_ERROR, traceId);
                    sendEvent(emitter, "error", Map.of("message", "服务暂时不可用，请稍后重试"));
                    emitter.complete();
                } finally {
                    cancelHeartbeat(heartbeat);
                    chatService.unregisterControl(conversationId);
                }
            });
        } catch (RejectedExecutionException ex) {
            chatService.unregisterControl(conversationId);
            auditService.onFailed(
                    audit.assistantMessageId(),
                    audit.taskId(),
                    ErrorCode.SERVICE_BUSY,
                    traceId,
                    "dispatcher rejected execution"
            );
            sendEvent(emitter, "error", Map.of("message", "系统繁忙，请稍后重试"));
            emitter.complete();
        }

        return emitter;
    }

    private static void appendDelta(StringBuilder assistant, ChatGenerateChunk chunk) {
        if (chunk.delta() != null) {
            assistant.append(chunk.delta());
        }
    }

    private static void streamDelta(SseEmitter emitter, StringBuilder assistant, ChatGenerateChunk chunk, StStreamControl control) {
        if (chunk.delta() != null) {
            assistant.append(chunk.delta());
            sendEvent(emitter, "delta", Map.of("t", chunk.delta()), control);
        }
    }

    private void saveSnapshotQuietly(long conversationId) {
        try {
            snapshotService.saveSnapshotFromDb(conversationId, 800);
        } catch (Exception ignored) {
        }
    }

    private static void sendEvent(SseEmitter emitter, String event, Object data) {
        sendEvent(emitter, event, data, null);
    }

    private static boolean sendEvent(SseEmitter emitter, String event, Object data, StStreamControl control) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (IOException | IllegalStateException ignored) {
            if (control != null) {
                control.cancel();
            }
        }
        return false;
    }

    private static ScheduledFuture<?> startHeartbeat(SseEmitter emitter, StStreamControl control) {
        return SSE_HEARTBEAT_EXECUTOR.scheduleAtFixedRate(() -> {
            if (control.isCancelled()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("ping").data(Map.of()));
            } catch (IOException | IllegalStateException ignored) {
                control.cancel();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private static void cancelHeartbeat(ScheduledFuture<?> heartbeat) {
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
    }

    private static String traceId() {
        String id = MDC.get(MDC_TRACE_ID);
        return id != null ? id : "unknown";
    }

    private long ensureConversationId(long characterId, String clientUid, String token) {
        return conversationService.ensureDetailByH5Character(clientUid, characterId, token).conversationId();
    }

    private long requireExistingConversationId(long characterId, String clientUid, String token) {
        ConversationDetailDto detail = conversationService.findDetailByH5Character(clientUid, characterId, token);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return detail.conversationId();
    }

    private long findLastAssistantMessageId(long conversationId) {
        List<AppMessage> list = messageMapper.listByConversation(conversationId, 200);
        for (AppMessage message : list) {
            if (isUsableAssistantAnchor(message)) {
                return canonicalAssistantAnchorId(message, conversationId);
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "当前没有可继续或重生的 AI 回复");
    }

    private long resolveAssistantAnchor(H5ChatPayload payload, long conversationId) {
        String raw = payload == null ? null : payload.getTargetAssistantMessageId();
        if (raw == null || raw.isBlank()) {
            return waitForLastAssistantAnchor(conversationId);
        }
        String normalized = raw.trim();
        if (normalized.startsWith("db_")) {
            normalized = normalized.substring(3);
        }
        long messageId;
        try {
            messageId = Long.parseLong(normalized);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "targetAssistantMessageId 非法");
        }
        if (messageId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "targetAssistantMessageId 非法");
        }
        return awaitUsableAssistantAnchor(messageId, conversationId);
        /*
        AppMessage message = messageMapper.findById(messageId);
        if (message == null || message.getConversationId() == null || message.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标 AI 消息不存在");
        }
        if (!"assistant".equalsIgnoreCase(message.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标 AI 消息不存在");
        }
        long canonicalId = canonicalAssistantAnchorId(message, conversationId);
        AppMessage canonical = messageMapper.findById(canonicalId);
        if (!isUsableAssistantAnchor(canonical)) {
            throw new BusinessException(ErrorCode.CONFLICT, "这条 AI 回复尚未完成，请稍后再试");
        }
        return canonicalId;
        */
    }

    private long waitForLastAssistantAnchor(long conversationId) {
        BusinessException lastError = null;
        for (int attempt = 0; attempt < ASSISTANT_ANCHOR_RETRY_ATTEMPTS; attempt++) {
            try {
                return findLastAssistantMessageId(conversationId);
            } catch (BusinessException ex) {
                lastError = ex;
                if (attempt >= ASSISTANT_ANCHOR_RETRY_ATTEMPTS - 1 || ex.getErrorCode() != ErrorCode.NOT_FOUND) {
                    throw ex;
                }
                pauseAssistantAnchorRetry();
            }
        }
        throw lastError == null
                ? new BusinessException(ErrorCode.NOT_FOUND, "当前没有可继续或重生的 AI 回复")
                : lastError;
    }

    private long awaitUsableAssistantAnchor(long messageId, long conversationId) {
        AppMessage lastSeen = null;
        for (int attempt = 0; attempt < ASSISTANT_ANCHOR_RETRY_ATTEMPTS; attempt++) {
            AppMessage message = messageMapper.findById(messageId);
            if (message != null) {
                lastSeen = message;
            }
            if (message != null
                    && message.getConversationId() != null
                    && message.getConversationId() == conversationId
                    && "assistant".equalsIgnoreCase(message.getRole())) {
                long canonicalId = canonicalAssistantAnchorId(message, conversationId);
                AppMessage canonical = messageMapper.findById(canonicalId);
                if (isUsableAssistantAnchor(canonical)) {
                    return canonicalId;
                }
            }
            if (attempt < ASSISTANT_ANCHOR_RETRY_ATTEMPTS - 1) {
                pauseAssistantAnchorRetry();
            }
        }
        if (lastSeen == null || lastSeen.getConversationId() == null || lastSeen.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标 AI 消息不存在");
        }
        if (!"assistant".equalsIgnoreCase(lastSeen.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标 AI 消息不存在");
        }
        throw new BusinessException(ErrorCode.CONFLICT, "这条 AI 回复尚未完成，请稍后再试");
    }

    private static void pauseAssistantAnchorRetry() {
        try {
            Thread.sleep(ASSISTANT_ANCHOR_RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasImageUrls(H5ChatPayload payload) {
        if (payload == null || payload.getImageUrls() == null || payload.getImageUrls().isEmpty()) {
            return false;
        }
        return payload.getImageUrls().stream().anyMatch(url -> url != null && !url.isBlank());
    }

    private static boolean hasImageUrls(AppChatStreamRequest request) {
        if (request == null || request.getImageUrls() == null || request.getImageUrls().isEmpty()) {
            return false;
        }
        return request.getImageUrls().stream().anyMatch(url -> url != null && !url.isBlank());
    }

    private static String normalizeVoiceUrl(String voiceUrl) {
        if (voiceUrl == null) {
            return null;
        }
        String value = voiceUrl.trim();
        if (value.isBlank()) {
            return null;
        }
        if (!value.startsWith("/uploads/h5/") || value.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音消息地址非法");
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static Integer normalizeVoiceDurationMs(Integer voiceDurationMs) {
        if (voiceDurationMs == null) {
            return null;
        }
        int value = voiceDurationMs;
        if (value <= 0) {
            return null;
        }
        return Math.min(value, 10 * 60 * 1000);
    }

    private static long requireCharacterId(H5ChatPayload payload) {
        if (payload == null || payload.getCharacterId() == null || payload.getCharacterId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId 缺失");
        }
        return payload.getCharacterId();
    }

    private static String requireClientUid(H5ChatPayload payload) {
        if (payload == null || payload.getClientUid() == null || payload.getClientUid().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 缺失");
        }
        return payload.getClientUid();
    }

    private static String requireClientUidValue(String clientUid) {
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 缺失");
        }
        return clientUid.trim();
    }

    private long canonicalAssistantAnchorId(AppMessage message, long conversationId) {
        if (message == null || message.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标 AI 消息不存在");
        }
        String ref = message.getStMessageRef();
        if (ref == null || !ref.startsWith("root:")) {
            return message.getId();
        }
        long rootId;
        try {
            rootId = Long.parseLong(ref.substring("root:".length()));
        } catch (Exception ignored) {
            return message.getId();
        }
        if (rootId <= 0 || rootId == message.getId()) {
            return message.getId();
        }
        AppMessage root = messageMapper.findById(rootId);
        if (root == null
                || root.getConversationId() == null
                || root.getConversationId() != conversationId
                || !"assistant".equalsIgnoreCase(root.getRole())) {
            return message.getId();
        }
        return rootId;
    }

    private static boolean isUsableAssistantAnchor(AppMessage message) {
        if (message == null || message.getId() == null) {
            return false;
        }
        if (!"assistant".equalsIgnoreCase(message.getRole())) {
            return false;
        }
        if (message.getContent() == null || message.getContent().isBlank()) {
            return false;
        }
        String status = message.getStatus() == null ? "" : message.getStatus();
        if (!"SUCCESS".equalsIgnoreCase(status) && !"STOPPED".equalsIgnoreCase(status)) {
            return false;
        }
        String ref = message.getStMessageRef();
        if (ref != null && ref.startsWith("root:")) {
            try {
                long rootId = Long.parseLong(ref.substring("root:".length()));
                return rootId <= 0 || rootId == message.getId();
            } catch (Exception ignored) {
                return true;
            }
        }
        return true;
    }
}
