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
import com.example.sillyspringboot.chat.service.MediaConcurrencyGate;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.ai.service.AiChatModelService;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.compat.h5.web.dto.H5ChatPayload;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.integration.sillytavern.StModelRoutingService;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ops.service.UserTtsVoiceService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final MediaConcurrencyGate mediaConcurrencyGate;
    private final ChatSnapshotService snapshotService;
    private final AppMessageMapper messageMapper;
    private final H5EntitlementService entitlementService;
    private final H5VisitorTrialGuardService visitorTrialGuardService;
    private final AppFeatureSettingsService featureSettingsService;
    private final H5UserAiProviderService userAiProviderService;
    private final UserTtsVoiceService userTtsVoiceService;
    private final StModelRoutingService modelRoutingService;

    @Autowired(required = false)
    private AiChatModelService chatModelService;

    @Autowired(required = false)
    private CharacterStudioMapper characterStudioMapper;

    private final ObjectMapper memberVoiceMapper = new ObjectMapper();

    public ApiV1TavernChatController(
            H5ClientUidAuthService h5Auth,
            AppConversationService conversationService,
            AppChatService chatService,
            ChatGenerationDispatcher dispatcher,
            ChatAuditService auditService,
            ChatAudioTranscriptionService chatAudioTranscriptionService,
            ChatAudioSpeechService chatAudioSpeechService,
            MediaConcurrencyGate mediaConcurrencyGate,
            ChatSnapshotService snapshotService,
            AppMessageMapper messageMapper,
            H5EntitlementService entitlementService,
            H5VisitorTrialGuardService visitorTrialGuardService,
            AppFeatureSettingsService featureSettingsService,
            H5UserAiProviderService userAiProviderService,
            StModelRoutingService modelRoutingService,
            UserTtsVoiceService userTtsVoiceService
    ) {
        this.h5Auth = h5Auth;
        this.conversationService = conversationService;
        this.chatService = chatService;
        this.dispatcher = dispatcher;
        this.auditService = auditService;
        this.chatAudioTranscriptionService = chatAudioTranscriptionService;
        this.chatAudioSpeechService = chatAudioSpeechService;
        this.mediaConcurrencyGate = mediaConcurrencyGate;
        this.snapshotService = snapshotService;
        this.messageMapper = messageMapper;
        this.entitlementService = entitlementService;
        this.visitorTrialGuardService = visitorTrialGuardService;
        this.featureSettingsService = featureSettingsService;
        this.userAiProviderService = userAiProviderService;
        this.modelRoutingService = modelRoutingService;
        this.userTtsVoiceService = userTtsVoiceService;
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
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = ensureConversationId(characterId, clientUid, token);
        String clientMessageId = generationRequestId(payload, "h5");
        AiChatModelService.ResolvedChatModel chatModel = resolveChatModel(token, conversationId, payload);
        H5EntitlementService.AccessTicket accessTicket = guardChat(
                clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.GENERATE,
                chatModel, clientMessageId, conversationId);

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
        req.setReplySplitMode(payload.getReplySplitMode());
        req.setVisionRequestId(payload.getVisionRequestId());
        req.setClientMessageId(clientMessageId);
        applyChatModel(req, chatModel);

        return runStream(req, token, conversationId, clientMessageId, userText,
                StreamKind.GENERATE, 0L, accessTicket, chatModel);
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
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = ensureConversationId(characterId, clientUid, token);
        String clientMessageId = generationRequestId(payload, "h5_http");
        AiChatModelService.ResolvedChatModel chatModel = resolveChatModel(token, conversationId, payload);
        H5EntitlementService.AccessTicket accessTicket = guardChat(
                clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.GENERATE,
                chatModel, clientMessageId, conversationId);

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
        req.setReplySplitMode(payload.getReplySplitMode());
        req.setVisionRequestId(payload.getVisionRequestId());
        req.setClientMessageId(clientMessageId);
        applyChatModel(req, chatModel);

        return ApiV1Result.ok(
                runBlockingGenerate(req, token, conversationId, clientMessageId, userText,
                        StreamKind.GENERATE, 0L, accessTicket, chatModel)
        );
    }

    @PostMapping(value = "/chat/continue")
    public ApiV1Result<Map<String, Object>> continueChat(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String clientMessageId = generationRequestId(payload, "h5_http_cont");
        AiChatModelService.ResolvedChatModel chatModel = resolveChatModel(token, conversationId, payload);
        H5EntitlementService.AccessTicket accessTicket = guardChat(
                clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.CONTINUE,
                chatModel, clientMessageId, conversationId);

        long anchorId = resolveAssistantAnchor(payload, conversationId, token);
        AppChatContinueRequest req = new AppChatContinueRequest();
        req.setConversationId(conversationId);
        req.setClientMessageId(clientMessageId);
        req.setTargetMessageId(String.valueOf(anchorId));
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        req.setReplySplitMode(payload.getReplySplitMode());
        applyChatModel(req, chatModel);
        return ApiV1Result.ok(
                runBlockingGenerate(req, token, conversationId, clientMessageId, "",
                        StreamKind.CONTINUE, anchorId, accessTicket, chatModel)
        );
    }

    @PostMapping(value = "/chat/regenerate")
    public ApiV1Result<Map<String, Object>> regenerateChat(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String clientMessageId = generationRequestId(payload, "h5_http_regen");
        AiChatModelService.ResolvedChatModel chatModel = resolveChatModel(token, conversationId, payload);
        H5EntitlementService.AccessTicket accessTicket = guardChat(
                clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.REGENERATE,
                chatModel, clientMessageId, conversationId);

        long anchorId = resolveAssistantAnchor(payload, conversationId, token);
        AppChatRegenerateRequest req = new AppChatRegenerateRequest();
        req.setConversationId(conversationId);
        req.setClientMessageId(clientMessageId);
        req.setTargetMessageId(String.valueOf(anchorId));
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        req.setReplySplitMode(payload.getReplySplitMode());
        applyChatModel(req, chatModel);
        return ApiV1Result.ok(
                runBlockingGenerate(req, token, conversationId, clientMessageId, "",
                        StreamKind.REGENERATE, anchorId, accessTicket, chatModel)
        );
    }

    @PostMapping(value = "/chat/continue/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter continueStream(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String clientMessageId = generationRequestId(payload, "h5_cont");
        AiChatModelService.ResolvedChatModel chatModel = resolveChatModel(token, conversationId, payload);
        H5EntitlementService.AccessTicket accessTicket = guardChat(
                clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.CONTINUE,
                chatModel, clientMessageId, conversationId);
        long anchorId = resolveAssistantAnchor(payload, conversationId, token);

        AppChatContinueRequest req = new AppChatContinueRequest();
        req.setConversationId(conversationId);
        req.setClientMessageId(clientMessageId);
        req.setTargetMessageId(String.valueOf(anchorId));
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        req.setReplySplitMode(payload.getReplySplitMode());
        applyChatModel(req, chatModel);
        return runStream(req, token, conversationId, clientMessageId, "",
                StreamKind.CONTINUE, anchorId, accessTicket, chatModel);
    }

    @PostMapping(value = "/chat/regenerate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateStream(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String clientMessageId = generationRequestId(payload, "h5_regen");
        AiChatModelService.ResolvedChatModel chatModel = resolveChatModel(token, conversationId, payload);
        H5EntitlementService.AccessTicket accessTicket = guardChat(
                clientUid, characterId, EntitlementPolicyService.ChatQuotaAction.REGENERATE,
                chatModel, clientMessageId, conversationId);
        long anchorId = resolveAssistantAnchor(payload, conversationId, token);

        AppChatRegenerateRequest req = new AppChatRegenerateRequest();
        req.setConversationId(conversationId);
        req.setClientMessageId(clientMessageId);
        req.setTargetMessageId(String.valueOf(anchorId));
        req.setExpressionHints(payload.getExpressionHints());
        req.setAvoidExpressionHints(payload.getAvoidExpressionHints());
        req.setReplySplitMode(payload.getReplySplitMode());
        applyChatModel(req, chatModel);
        return runStream(req, token, conversationId, clientMessageId, "",
                StreamKind.REGENERATE, anchorId, accessTicket, chatModel);
    }

    @PostMapping("/chat/stop")
    public ApiV1Result<Boolean> chatStop(@RequestBody H5ChatPayload payload) {
        long characterId = requireCharacterId(payload);
        String clientUid = requireClientUid(payload);
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
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
        visitorTrialGuardService.guardAnonymousChatAttempt(clientUid);
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        String requestId = generationRequestId(payload, "h5_suggest");
        H5EntitlementService.AccessTicket ticket = entitlementService.guardChat(
                clientUid,
                characterId,
                EntitlementPolicyService.ChatQuotaAction.GENERATE,
                requestId,
                conversationId,
                null
        );
        try {
            List<String> suggestions = chatService.suggestReplies(
                    conversationId, token, payload == null ? "" : payload.getContent());
            if (suggestions == null || suggestions.isEmpty()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 帮答暂时不可用，请稍后再试");
            }
            entitlementService.recordSuccessfulChat(ticket, true);
            Map<String, Object> data = new HashMap<>();
            data.put("suggestions", suggestions);
            data.put("conversationId", conversationId);
            return ApiV1Result.ok(data);
        } catch (RuntimeException ex) {
            entitlementService.refundFailedChat(ticket, false);
            throw ex;
        }
    }

    @PostMapping(value = "/chat/transcribe-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiV1Result<Map<String, Object>> transcribeAudio(
            @RequestPart("file") MultipartFile file,
            @RequestParam("clientUid") String clientUid,
            @RequestParam(value = "sttRequestId", required = false) String rawSttRequestId
    ) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        String safeClientUid = requireClientUidValue(clientUid);
        visitorTrialGuardService.guardAnonymousChatAttempt(safeClientUid);
        String token = h5Auth.requireAuthenticatedTokenForClientUid(safeClientUid);
        long userId = chatService.resolveUserId(token);
        String sttRequestId = normalizeMediaRequestId(rawSttRequestId, "stt");
        H5EntitlementService.AccessTicket sttTicket = entitlementService.guardStt(safeClientUid, sttRequestId);
        boolean sttChargeCreated = sttTicket.usesWallet() && entitlementService.reserveSttCharge(sttTicket);
        try (MediaConcurrencyGate.Lease ignored = mediaConcurrencyGate.acquire(
                MediaConcurrencyGate.Capability.STT, userId, sttRequestId)) {
            ChatAudioTranscriptionService.AudioTranscriptionResult result =
                    chatAudioTranscriptionService.transcribeForUser(userId, file);
            Map<String, Object> data = new HashMap<>();
            data.put("text", result.text());
            data.put("modelName", result.modelName());
            data.put("audioUrl", result.audioUrl());
            return ApiV1Result.ok(data);
        } catch (RuntimeException ex) {
            if (sttChargeCreated) {
                entitlementService.refundSttCharge(sttTicket);
            }
            throw ex;
        }
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
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        String ttsRequestId = normalizeTtsRequestId(payload.getTtsRequestId());
        int ttsSegmentIndex = validateTtsSegmentMetadata(
                ttsRequestId,
                payload.getTtsSegmentIndex(),
                payload.getTtsSegmentCount()
        );
        long userId = chatService.resolveUserId(token);
        requireMemberVoiceCharacterVisible(payload, userId);
        boolean userByokTts = userAiProviderService.isCustomModeSelectedForUser(userId);
        H5UserAiProviderService.UserTtsSettings activeTtsSettings = userByokTts
                ? userAiProviderService.resolveActiveTtsSettingsForUser(userId)
                : null;
        boolean roleOverrideActive = userByokTts && ttsOverrideScopeMatchesProvider(
                payload.getTtsProviderSource(), activeTtsSettings);
        String roleModelOverride = roleOverrideActive ? firstVoiceValue(payload.getTtsModelName(), "") : "";
        String roleVoiceOverride = roleOverrideActive || !userByokTts
                ? firstVoiceValue(payload.getTtsVoiceName(), "")
                : "";
        String roleTemplateOverride = roleOverrideActive || !userByokTts
                ? firstVoiceValue(payload.getTtsVoiceTemplateCode(), "")
                : "";
        H5EntitlementService.AccessTicket ttsTicket = null;
        boolean ttsChargeCreated = false;
        if (!userByokTts) {
            ttsTicket = ttsRequestId.isBlank()
                    ? entitlementService.guardTts(clientUid)
                    : entitlementService.guardTts(clientUid, ttsRequestId);
            // Official TTS deducts before synthesis; user BYOK never consumes the platform wallet.
            ttsChargeCreated = ttsTicket.usesWallet()
                    && entitlementService.recordSuccessfulTts(ttsTicket);
        }
        try {
            try (MediaConcurrencyGate.Lease ignored =
                         mediaConcurrencyGate.acquire(MediaConcurrencyGate.Capability.TTS, userId, ttsRequestId)) {
                MemberVoiceOverride memberVoice = resolveMemberVoiceOverride(
                        payload,
                        activeTtsSettings == null ? "" : activeTtsSettings.providerSource(),
                        !userByokTts);
                Long boundUserVoiceId = userByokTts ? payload.getTtsUserVoiceId() : null;
                if (shouldResolveSpecificPrivateVoice(
                        boundUserVoiceId,
                        firstVoiceValue(memberVoice.voiceName(), roleVoiceOverride),
                        firstVoiceValue(memberVoice.voiceTemplateCode(), roleTemplateOverride))) {
                    boundUserVoiceId = userTtsVoiceService.resolveSpecificBoundVoiceId(
                            userId,
                            payload.getCharacterId() == null ? 0L : payload.getCharacterId(),
                            payload.getSpeakerMemberId() == null ? 0L : payload.getSpeakerMemberId());
                    if (shouldUseGlobalPrivateVoice(
                            boundUserVoiceId,
                            roleVoiceOverride,
                            roleTemplateOverride,
                            memberVoice.voiceName(),
                            memberVoice.voiceTemplateCode())) {
                        boundUserVoiceId = userTtsVoiceService.resolveGlobalBoundVoiceId(userId);
                    }
                }
                if (boundUserVoiceId != null && boundUserVoiceId <= 0) {
                    boundUserVoiceId = null;
                }
                boolean memberVoiceOverrideActive = hasVoiceValue(
                        memberVoice.voiceName(), memberVoice.voiceTemplateCode());
                String finalModelOverride = firstVoiceValue(memberVoice.modelName(), roleModelOverride);
                String finalVoiceOverride = memberVoiceOverrideActive
                        ? memberVoice.voiceName()
                        : roleVoiceOverride;
                String finalTemplateOverride = memberVoiceOverrideActive
                        ? memberVoice.voiceTemplateCode()
                        : roleTemplateOverride;
                String overrideProviderScope = hasVoiceValue(
                        finalModelOverride, finalVoiceOverride, finalTemplateOverride)
                        && activeTtsSettings != null
                        ? activeTtsSettings.providerSource()
                        : "";
                ChatAudioSpeechService.AudioSpeechResult result = chatAudioSpeechService.synthesizeForUser(
                        userId,
                        text,
                        finalModelOverride,
                        finalVoiceOverride,
                        finalTemplateOverride,
                        boundUserVoiceId,
                        overrideProviderScope
                );
                Map<String, Object> data = new HashMap<>();
                data.put("audioDataUrl", "data:" + result.mimeType() + ";base64," + Base64.getEncoder().encodeToString(result.audioBytes()));
                data.put("mimeType", result.mimeType());
                data.put("modelName", result.modelName());
                data.put("voiceName", result.voiceName());
                return ApiV1Result.ok(data);
            }
        } catch (RuntimeException ex) {
            if (ttsChargeCreated && (ttsRequestId.isBlank() || ttsSegmentIndex == 0)) {
                entitlementService.refundWalletConsume(ttsTicket);
            }
            throw ex;
        }
    }

    private MemberVoiceOverride resolveMemberVoiceOverride(
            H5ChatPayload payload,
            String activeProviderSource,
            boolean officialMode
    ) {
        if (characterStudioMapper == null || payload == null || payload.getCharacterId() == null
                || payload.getCharacterId() <= 0 || payload.getSpeakerMemberId() == null
                || payload.getSpeakerMemberId() <= 0) {
            return MemberVoiceOverride.EMPTY;
        }
        try {
            for (AppCharacterMember member : characterStudioMapper.listMembers(payload.getCharacterId())) {
                if (member == null || member.getId() == null
                        || member.getId().longValue() != payload.getSpeakerMemberId()
                        || member.getVoiceConfigJson() == null || member.getVoiceConfigJson().isBlank()) {
                    continue;
                }
                JsonNode root = memberVoiceMapper.readTree(member.getVoiceConfigJson());
                String providerSource = voiceJsonText(root, "ttsProviderSource", "providerSource");
                if (officialMode) {
                    return new MemberVoiceOverride(
                            "",
                            voiceJsonText(root, "ttsVoiceName", "voiceName", "voice"),
                            voiceJsonText(root, "ttsVoiceTemplateCode", "voiceTemplateCode", "templateCode"));
                }
                if (!sameProviderSource(providerSource, activeProviderSource)) {
                    return MemberVoiceOverride.EMPTY;
                }
                return new MemberVoiceOverride(
                        "",
                        voiceJsonText(root, "ttsVoiceName", "voiceName", "voice"),
                        voiceJsonText(root, "ttsVoiceTemplateCode", "voiceTemplateCode", "templateCode")
                );
            }
        } catch (Exception ex) {
            log.warn("member voice config skipped characterId={} memberId={}",
                    payload.getCharacterId(), payload.getSpeakerMemberId(), ex);
        }
        return MemberVoiceOverride.EMPTY;
    }

    private void requireMemberVoiceCharacterVisible(H5ChatPayload payload, long userId) {
        if (characterStudioMapper == null || payload == null || payload.getCharacterId() == null
                || payload.getCharacterId() <= 0 || payload.getSpeakerMemberId() == null
                || payload.getSpeakerMemberId() <= 0) {
            return;
        }
        entitlementService.requireCharacterVisibleToUser(payload.getCharacterId(), userId);
    }

    private static String voiceJsonText(JsonNode root, String... keys) {
        if (root == null || keys == null) return "";
        for (String key : keys) {
            JsonNode value = root.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText().trim();
        }
        return "";
    }

    private static boolean hasVoiceValue(String... values) {
        if (values == null) return false;
        for (String value : values) if (value != null && !value.isBlank()) return true;
        return false;
    }

    static boolean shouldResolveSpecificPrivateVoice(
            Long requestedPrivateVoiceId,
            String requestedVoiceName,
            String requestedTemplateCode
    ) {
        return (requestedPrivateVoiceId == null || requestedPrivateVoiceId <= 0)
                && !hasVoiceValue(requestedVoiceName, requestedTemplateCode);
    }

    private static String firstVoiceValue(String requested, String fallback) {
        return requested != null && !requested.trim().isBlank() ? requested.trim() : fallback;
    }

    static boolean ttsOverrideScopeMatchesProvider(
            String requestedProviderSource,
            H5UserAiProviderService.UserTtsSettings activeSettings
    ) {
        return activeSettings != null
                && sameProviderSource(requestedProviderSource, activeSettings.providerSource());
    }

    private static boolean sameProviderSource(String left, String right) {
        return left != null && right != null
                && !left.trim().isBlank()
                && left.trim().equalsIgnoreCase(right.trim());
    }

    static boolean shouldUseGlobalPrivateVoice(
            Long specificPrivateVoiceId,
            String requestedVoiceName,
            String requestedTemplateCode,
            String memberVoiceName,
            String memberTemplateCode
    ) {
        if (specificPrivateVoiceId != null && specificPrivateVoiceId > 0) return false;
        return firstVoiceValue(requestedVoiceName, memberVoiceName).isBlank()
                && firstVoiceValue(requestedTemplateCode, memberTemplateCode).isBlank();
    }

    private record MemberVoiceOverride(String modelName, String voiceName, String voiceTemplateCode) {
        private static final MemberVoiceOverride EMPTY = new MemberVoiceOverride("", "", "");
        private boolean isEmpty() {
            return modelName.isBlank() && voiceName.isBlank() && voiceTemplateCode.isBlank();
        }
    }

    private static String normalizeTtsRequestId(String value) {
        String requestId = value == null ? "" : value.trim();
        if (requestId.isBlank()) {
            return "";
        }
        if (requestId.length() < 8 || requestId.length() > 160
                || !requestId.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "TTS 请求标识不合法");
        }
        return requestId;
    }

    private static String normalizeMediaRequestId(String value, String prefix) {
        String requestId = value == null ? "" : value.trim();
        if (requestId.isBlank()) {
            return prefix + "-legacy-" + UUID.randomUUID().toString().replace("-", "");
        }
        if (requestId.length() < 8 || requestId.length() > 160
                || !requestId.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "媒体请求标识不合法");
        }
        return requestId;
    }

    private static int validateTtsSegmentMetadata(String requestId, Integer segmentIndex, Integer segmentCount) {
        if (requestId.isBlank()) {
            if (segmentIndex != null || segmentCount != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "TTS 分段参数缺少请求标识");
            }
            return 0;
        }
        if (segmentIndex == null || segmentCount == null
                || segmentCount < 1 || segmentCount > 256
                || segmentIndex < 0 || segmentIndex >= segmentCount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "TTS 分段参数不合法");
        }
        return segmentIndex;
    }

    private AiChatModelService.ResolvedChatModel resolveChatModel(
            String token,
            long conversationId,
            H5ChatPayload payload
    ) {
        if (chatModelService == null) return null;
        long userId = chatService.resolveUserId(token);
        return chatModelService.resolveForGeneration(
                userId,
                conversationId,
                payload == null ? "" : payload.getChatModelSource(),
                payload == null ? "" : payload.getChatModelRef(),
                payload == null ? null : payload.getChatModelSelectionVersion()
        );
    }

    private H5EntitlementService.AccessTicket guardChat(
            String clientUid,
            long characterId,
            EntitlementPolicyService.ChatQuotaAction action,
            AiChatModelService.ResolvedChatModel chatModel,
            String generationRequestId,
            long conversationId
    ) {
        if (chatModel == null || chatModel.byok()) {
            return entitlementService.guardChat(
                    clientUid, characterId, action, generationRequestId, conversationId, chatModel);
        }
        return entitlementService.guardChatModel(
                clientUid, characterId, action, chatModel, generationRequestId, conversationId);
    }

    private static String generationRequestId(H5ChatPayload payload, String prefix) {
        String supplied = payload == null || payload.getGenerationRequestId() == null
                ? "" : payload.getGenerationRequestId().trim();
        if (!supplied.isBlank()) {
            if (supplied.length() > 96 || !supplied.matches("[A-Za-z0-9_.:-]+")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生成请求 ID 不合法");
            }
            return supplied;
        }
        return prefix + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void applyChatModel(
            AppChatStreamRequest request,
            AiChatModelService.ResolvedChatModel model
    ) {
        if (model == null) return;
        request.setChatModelSource(model.source());
        request.setChatRouteKey(model.routeKey());
        request.setChatModelName(model.modelName());
    }

    private static void applyChatModel(
            AppChatContinueRequest request,
            AiChatModelService.ResolvedChatModel model
    ) {
        if (model == null) return;
        request.setChatModelSource(model.source());
        request.setChatRouteKey(model.routeKey());
        request.setChatModelName(model.modelName());
    }

    private static void applyChatModel(
            AppChatRegenerateRequest request,
            AiChatModelService.ResolvedChatModel model
    ) {
        if (model == null) return;
        request.setChatModelSource(model.source());
        request.setChatRouteKey(model.routeKey());
        request.setChatModelName(model.modelName());
    }

    enum StreamKind { GENERATE, CONTINUE, REGENERATE }

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
            H5EntitlementService.AccessTicket accessTicket,
            AiChatModelService.ResolvedChatModel chatModel
    ) {
        String traceId = traceId();
        StStreamControl control = new StStreamControl();
        chatService.registerControl(conversationId, control);
        boolean ensureUserMessage = kind == StreamKind.GENERATE
                && request instanceof AppChatStreamRequest streamRequest
                && hasImageUrls(streamRequest);
        String voiceUrl = request instanceof AppChatStreamRequest streamRequest
                ? normalizeVoiceUrl(streamRequest.getVoiceUrl())
                : null;
        Integer voiceDurationMs = request instanceof AppChatStreamRequest streamRequest
                ? normalizeVoiceDurationMs(streamRequest.getVoiceDurationMs())
                : null;
        ChatAuditService.AuditContext audit;
        try {
            audit = auditService.onQueued(
                    conversationId,
                    userMessage,
                    clientMessageId,
                    token,
                    traceId,
                    channelFor(kind, false),
                    ensureUserMessage,
                    voiceUrl,
                    voiceDurationMs,
                    auditModelForToken(token, ensureUserMessage, chatModel)
            );
            chatService.bindControlTask(conversationId, audit.taskId(), control);
        } catch (RuntimeException ex) {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            refundFailedChatQuietly(accessTicket, false, "blocking audit queue failed");
            throw ex;
        }
        boolean[] contentEmitted = {false};
        StringBuilder assistant = new StringBuilder();
        boolean[] frontendBridgeGenerated = {false};
        try (var lease = chatService.acquireLease(token);
             ChatGenerationTimeout timeout = ChatGenerationTimeout.start(control, chatService.generationTimeoutSeconds())) {
            auditService.onGenerating(audit.assistantMessageId(), audit.taskId(), traceId);
            switch (kind) {
                case GENERATE -> {
                    String userRef = audit.userMessageId() > 0
                            ? ("root:" + audit.userMessageId())
                            : ("client:" + clientMessageId);
                    chatService.streamGenerate(
                            (AppChatStreamRequest) request,
                            token,
                            userRef,
                            c -> {
                                if (isFrontendBridgeChunk(c)) frontendBridgeGenerated[0] = true;
                                observeChatContent(accessTicket, contentEmitted, c);
                                appendDelta(assistant, c);
                            },
                            control
                    );
                }
                case CONTINUE -> chatService.streamContinue(
                        (AppChatContinueRequest) request,
                        token,
                        c -> {
                            if (isFrontendBridgeChunk(c)) frontendBridgeGenerated[0] = true;
                            observeChatContent(accessTicket, contentEmitted, c);
                            appendDelta(assistant, c);
                        },
                        control
                );
                case REGENERATE -> chatService.streamRegenerate(
                        (AppChatRegenerateRequest) request,
                        token,
                        c -> {
                            if (isFrontendBridgeChunk(c)) frontendBridgeGenerated[0] = true;
                            observeChatContent(accessTicket, contentEmitted, c);
                            appendDelta(assistant, c);
                        },
                        control
                );
            }

            if (timeout.isTimedOut()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试");
            }

            boolean cancelled = control.isCancelled();
            String content = assistant.toString().trim();
            if (shouldFailEmptyGeneratedContent(kind, cancelled, content)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, emptyGeneratedContentMessage(kind));
            }
            AppChatService.AssistantOutputNormalization normalization = frontendBridgeGenerated[0]
                    ? AppChatService.AssistantOutputNormalization.passthrough(content)
                    : chatService.normalizeAssistantOutput(conversationId, content, token);
            content = normalization.content();
            content = chatService.finalizeEnsembleOutput(
                    conversationId, audit.assistantMessageId(), content, token);
            if (timeout.isTimedOut()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试");
            }
            cancelled = control.isCancelled();
            boolean assistantSynced = applyStreamPostGenerate(
                    kind,
                    conversationId,
                    anchorOrTargetMessageId,
                    audit,
                    content,
                    cancelled,
                    token,
                    traceId,
                    frontendBridgeGenerated[0],
                    normalization.finalized()
            );
            if (kind == StreamKind.GENERATE
                    && !frontendBridgeGenerated[0]
                    && !assistantSynced) {
                saveSnapshotQuietly(conversationId);
            }
            settleFinalizedChat(
                    kind, accessTicket, cancelled, contentEmitted[0], !content.isBlank(), false);
            Map<String, Object> done = buildDonePayload(kind, anchorOrTargetMessageId, audit, content);
            done.put("cancelled", cancelled);
            return done;
        } catch (BusinessException be) {
            boolean partialSaved = persistPartialAfterFailure(
                    kind, conversationId, anchorOrTargetMessageId, audit, assistant.toString(), token, traceId,
                    frontendBridgeGenerated[0], contentEmitted[0], accessTicket);
            if (!partialSaved) {
                refundUnpersistedFailure(accessTicket, kind, contentEmitted[0]);
                auditService.onFailed(audit.assistantMessageId(), audit.taskId(), be, traceId);
                restoreSnapshotAfterFailedMutation(kind, conversationId);
            }
            throw be;
        } catch (Exception ex) {
            boolean partialSaved = persistPartialAfterFailure(
                    kind, conversationId, anchorOrTargetMessageId, audit, assistant.toString(), token, traceId,
                    frontendBridgeGenerated[0], contentEmitted[0], accessTicket);
            if (!partialSaved) {
                refundUnpersistedFailure(accessTicket, kind, contentEmitted[0]);
                auditService.onFailed(audit.assistantMessageId(), audit.taskId(), ex, ErrorCode.INTERNAL_ERROR, traceId);
                restoreSnapshotAfterFailedMutation(kind, conversationId);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "服务暂时不可用，请稍后重试");
        } finally {
            chatService.unregisterControl(conversationId, control);
        }
    }

    private boolean applyStreamPostGenerate(
            StreamKind kind,
            long conversationId,
            long anchorOrTargetMessageId,
            ChatAuditService.AuditContext audit,
            String content,
            boolean cancelled,
            String token,
            String traceId,
            boolean generatedByFrontendBridge,
            boolean outputRegexApplied
    ) {
        boolean assistantSynced = false;
        switch (kind) {
            case GENERATE -> {
                if (cancelled) {
                    auditService.onStopped(audit.assistantMessageId(), audit.taskId(), content, traceId);
                } else {
                    auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), content, traceId);
                }
                markUserMessageSuccessIfQueued(audit);
                if (!generatedByFrontendBridge) {
                    try {
                        assistantSynced = chatService.syncAssistantReplyToSt(
                                conversationId,
                                "root:" + audit.assistantMessageId(),
                                content,
                                token,
                                outputRegexApplied
                        );
                    } catch (Exception ignored) {
                    }
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
                            token,
                            !generatedByFrontendBridge,
                            outputRegexApplied,
                            cancelled
                    );
                }
            }
            case REGENERATE -> {
                if (cancelled) {
                    restoreSnapshotAfterFailedMutation(kind, conversationId);
                    auditService.onStopped(audit.assistantMessageId(), audit.taskId(), "", traceId);
                } else {
                    auditService.stageFinalAssistantContent(audit.assistantMessageId(), content, traceId);
                    chatService.promoteRegenerateVariant(
                            conversationId,
                            anchorOrTargetMessageId,
                            audit.assistantMessageId(),
                            token,
                            !generatedByFrontendBridge,
                            outputRegexApplied
                    );
                    auditService.onHiddenVariantSuccess(audit.assistantMessageId(), audit.taskId(), content, traceId);
                }
            }
        }
        return assistantSynced;
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
            done.put("segments", chatService.messageSegments(audit.assistantMessageId()));
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
            done.put("segments", chatService.messageSegments(audit.assistantMessageId()));
            return done;
        }
        fillRegenerateDone(done, anchorOrTargetMessageId);
        done.put("segments", chatService.messageSegments(anchorOrTargetMessageId));
        return done;
    }

    private static String h5MessageId(long dbId) {
        return "db_" + dbId;
    }

    private String auditModelForToken(
            String token,
            boolean preferVisionModel,
            AiChatModelService.ResolvedChatModel selectedModel
    ) {
        try {
            if (selectedModel != null) {
                return selectedModel.byok()
                        ? firstNonBlank(selectedModel.modelName())
                        : firstNonBlank(selectedModel.offeringCode(), selectedModel.displayName());
            }
            long userId = chatService.resolveUserId(token);
            UserModelOverride override = userAiProviderService.resolveActiveOverrideForUser(userId);
            if (override != null) {
                String userModel = preferVisionModel
                        ? firstNonBlank(override.visionModelOrFallback(), override.textModelOrFallback())
                        : firstNonBlank(override.textModelOrFallback());
                if (!userModel.isBlank()) {
                    return userModel;
                }
            }
            StModelRoutingService.ResolvedRoute route =
                    modelRoutingService.resolveForScene(StModelRoutingService.DEFAULT_SCENE);
            if (route != null && route.providers() != null && !route.providers().isEmpty()) {
                return firstNonBlank(route.providers().get(0).modelName());
            }
        } catch (Exception ex) {
            log.warn("resolve audit model skipped: {}", ex.toString());
        }
        return "";
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
            H5EntitlementService.AccessTicket accessTicket,
            AiChatModelService.ResolvedChatModel chatModel
    ) {
        SseEmitter emitter = new SseEmitter(chatService.sseTimeoutMillis());
        StStreamControl control = new StStreamControl();
        chatService.registerControl(conversationId, control);
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter, control);
        emitter.onTimeout(() -> {
            cancelHeartbeat(heartbeat);
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            emitter.complete();
        });
        emitter.onError(ex -> {
            cancelHeartbeat(heartbeat);
            control.cancel();
            chatService.unregisterControl(conversationId, control);
        });
        emitter.onCompletion(() -> {
            cancelHeartbeat(heartbeat);
            control.cancel();
            chatService.unregisterControl(conversationId, control);
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
        ChatAuditService.AuditContext audit;
        try {
            audit = auditService.onQueued(
                    conversationId,
                    userMessage,
                    clientMessageId,
                    token,
                    traceId,
                    channelFor(kind, true),
                    ensureUserMessage,
                    voiceUrl,
                    voiceDurationMs,
                    auditModelForToken(token, ensureUserMessage, chatModel)
            );
            chatService.bindControlTask(conversationId, audit.taskId(), control);
        } catch (RuntimeException ex) {
            cancelHeartbeat(heartbeat);
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            refundFailedChatQuietly(accessTicket, false, "stream audit queue failed");
            throw ex;
        }

        try {
            dispatcher.submit(() -> {
                boolean[] contentEmitted = {false};
                StringBuilder assistant = new StringBuilder();
                boolean[] frontendBridgeGenerated = {false};
                try {
                    long start = System.nanoTime();
                    long maxWaitNanos = Duration.ofSeconds(chatService.maxQueueWaitSeconds()).toNanos();
                    boolean[] contentFinalized = {false};
                    while (!control.isCancelled()) {
                        try (var lease = chatService.acquireLease(token);
                             ChatGenerationTimeout timeout = ChatGenerationTimeout.start(control, chatService.generationTimeoutSeconds())) {
                            auditService.onGenerating(audit.assistantMessageId(), audit.taskId(), traceId);
                            assistant.setLength(0);
                            frontendBridgeGenerated[0] = false;
                            switch (kind) {
                                case GENERATE -> {
                                    String userRef = audit.userMessageId() > 0
                                            ? ("root:" + audit.userMessageId())
                                            : ("client:" + clientMessageId);
                                    chatService.streamGenerate(
                                            (AppChatStreamRequest) request,
                                            token,
                                            userRef,
                                            c -> {
                                                if (isFrontendBridgeChunk(c)) frontendBridgeGenerated[0] = true;
                                                observeChatContent(accessTicket, contentEmitted, c);
                                                streamDelta(emitter, assistant, c, control);
                                            },
                                            control
                                    );
                                }
                                case CONTINUE -> chatService.streamContinue(
                                        (AppChatContinueRequest) request,
                                        token,
                                        c -> {
                                            if (isFrontendBridgeChunk(c)) frontendBridgeGenerated[0] = true;
                                            observeChatContent(accessTicket, contentEmitted, c);
                                            streamDelta(emitter, assistant, c, control);
                                        },
                                        control
                                );
                                case REGENERATE -> chatService.streamRegenerate(
                                        (AppChatRegenerateRequest) request,
                                        token,
                                        c -> {
                                            if (isFrontendBridgeChunk(c)) frontendBridgeGenerated[0] = true;
                                            observeChatContent(accessTicket, contentEmitted, c);
                                            streamDelta(emitter, assistant, c, control);
                                        },
                                        control
                                );
                            }

                            boolean timedOut = timeout.isTimedOut();
                            boolean cancelled = control.isCancelled();
                            String content = assistant.toString().trim();
                            if (timedOut) {
                                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试");
                            }
                            if (shouldFailEmptyGeneratedContent(kind, cancelled, content)) {
                                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, emptyGeneratedContentMessage(kind));
                            }
                            AppChatService.AssistantOutputNormalization normalization = frontendBridgeGenerated[0]
                                    ? AppChatService.AssistantOutputNormalization.passthrough(content)
                                    : chatService.normalizeAssistantOutput(conversationId, content, token);
                            content = normalization.content();
                            content = chatService.finalizeEnsembleOutput(
                                    conversationId, audit.assistantMessageId(), content, token);
                            if (timeout.isTimedOut()) {
                                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试");
                            }
                            cancelled = control.isCancelled();
                            boolean assistantSynced = applyStreamPostGenerate(
                                    kind,
                                    conversationId,
                                    anchorOrTargetMessageId,
                                    audit,
                                    content,
                                    cancelled,
                                    token,
                                    traceId,
                                    frontendBridgeGenerated[0],
                                    normalization.finalized()
                            );
                            contentFinalized[0] = true;

                            if (kind == StreamKind.GENERATE
                                    && !frontendBridgeGenerated[0]
                                    && !assistantSynced) {
                                saveSnapshotQuietly(conversationId);
                            }

                            settleFinalizedChat(
                                    kind, accessTicket, cancelled, contentEmitted[0], !content.isBlank(), true);

                            Map<String, Object> done = buildDonePayload(kind, anchorOrTargetMessageId, audit, content);
                            done.put("cancelled", cancelled);
                            sendEvent(emitter, "done", done, control);
                            emitter.complete();
                            return;
                        } catch (BusinessException be) {
                            if (contentFinalized[0]) {
                                throw be;
                            }
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

                    auditService.onStopped(audit.assistantMessageId(), audit.taskId(), "", traceId);
                    settleFinalizedChat(
                            kind, accessTicket, true, contentEmitted[0], false, true);
                    markUserMessageSuccessIfQueued(audit);
                    restoreSnapshotAfterFailedMutation(kind, conversationId);
                    Map<String, Object> done;
                    if (kind == StreamKind.REGENERATE) {
                        done = buildDonePayload(kind, anchorOrTargetMessageId, audit, "");
                    } else {
                        done = new HashMap<>();
                        done.put("content", "");
                        done.put("messageId", h5MessageId(audit.assistantMessageId()));
                        if (audit.userMessageId() > 0) {
                            done.put("userMessageId", h5MessageId(audit.userMessageId()));
                        }
                        done.put("swipes", List.of(""));
                        done.put("swipeIndex", 0);
                    }
                    done.put("cancelled", true);
                    sendEvent(emitter, "done", done, control);
                    emitter.complete();
                } catch (BusinessException be) {
                    log.warn("h5 stream business error conversationId={} kind={} code={} message={}",
                            conversationId, kind, be.getErrorCode(), be.getMessage());
                    try {
                        boolean partialSaved = persistPartialAfterFailure(
                                kind, conversationId, anchorOrTargetMessageId, audit, assistant.toString(), token,
                                traceId, frontendBridgeGenerated[0], contentEmitted[0], accessTicket);
                        if (!partialSaved) {
                            refundUnpersistedFailure(accessTicket, kind, contentEmitted[0]);
                            recordStreamFailureQuietly(conversationId, kind, audit, be, traceId);
                            restoreSnapshotAfterFailedMutation(kind, conversationId);
                        }
                        sendEvent(emitter, "error", Map.of("message", be.getMessage()), control);
                    } finally {
                        completeEmitterQuietly(emitter);
                    }
                } catch (Exception ex) {
                    log.error("h5 stream unhandled error conversationId={} kind={} clientMessageId={}",
                            conversationId, kind, clientMessageId, ex);
                    try {
                        boolean partialSaved = persistPartialAfterFailure(
                                kind, conversationId, anchorOrTargetMessageId, audit, assistant.toString(), token,
                                traceId, frontendBridgeGenerated[0], contentEmitted[0], accessTicket);
                        if (!partialSaved) {
                            refundUnpersistedFailure(accessTicket, kind, contentEmitted[0]);
                            recordStreamFailureQuietly(conversationId, kind, audit, ex, traceId);
                            restoreSnapshotAfterFailedMutation(kind, conversationId);
                        }
                        sendEvent(emitter, "error", Map.of("message", "服务暂时不可用，请稍后重试"), control);
                    } finally {
                        completeEmitterQuietly(emitter);
                    }
                } finally {
                    cancelHeartbeat(heartbeat);
                    chatService.unregisterControl(conversationId, control);
                }
            });
        } catch (RejectedExecutionException ex) {
            entitlementService.refundFailedChat(accessTicket, false);
            chatService.unregisterControl(conversationId, control);
            try {
                recordStreamFailureQuietly(conversationId, kind, audit, ex, traceId);
                sendEvent(emitter, "error", Map.of("message", "系统繁忙，请稍后重试"), control);
            } finally {
                completeEmitterQuietly(emitter);
            }
        }

        return emitter;
    }

    private static void appendDelta(StringBuilder assistant, ChatGenerateChunk chunk) {
        if (chunk.delta() != null) {
            assistant.append(chunk.delta());
        }
    }

    private boolean persistPartialAfterFailure(
            StreamKind kind,
            long conversationId,
            long anchorOrTargetMessageId,
            ChatAuditService.AuditContext audit,
            String rawPartial,
            String token,
            String traceId,
            boolean generatedByFrontendBridge,
            boolean contentEmitted,
            H5EntitlementService.AccessTicket accessTicket
    ) {
        String partial = rawPartial == null ? "" : rawPartial.trim();
        if (!contentEmitted || partial.isBlank() || kind == StreamKind.REGENERATE) return false;
        try {
            boolean assistantSynced = applyStreamPostGenerate(
                    kind, conversationId, anchorOrTargetMessageId, audit, partial, true, token, traceId,
                    generatedByFrontendBridge, false);
            if (kind == StreamKind.GENERATE && !generatedByFrontendBridge && !assistantSynced) {
                saveSnapshotQuietly(conversationId);
            }
            entitlementService.refundFailedChat(accessTicket, true);
            return true;
        } catch (RuntimeException ex) {
            log.error("partial response persistence failed conversationId={} kind={} taskId={}",
                    conversationId, kind, audit.taskId(), ex);
            return false;
        }
    }

    private void refundUnpersistedFailure(
            H5EntitlementService.AccessTicket accessTicket,
            StreamKind kind,
            boolean contentEmitted
    ) {
        if (contentEmitted) {
            entitlementService.refundDiscardedChat(accessTicket);
            return;
        }
        entitlementService.refundFailedChat(accessTicket, contentEmitted);
    }

    void settleFinalizedChat(
            StreamKind kind,
            H5EntitlementService.AccessTicket accessTicket,
            boolean cancelled,
            boolean contentEmitted,
            boolean generatedContentReady,
            boolean streaming
    ) {
        if (kind == StreamKind.REGENERATE && cancelled) {
            entitlementService.refundDiscardedChat(accessTicket);
            return;
        }
        if (streaming) {
            entitlementService.settleStreamingChat(accessTicket, contentEmitted, generatedContentReady);
            return;
        }
        entitlementService.settleBlockingChat(
                accessTicket, cancelled, contentEmitted, generatedContentReady);
    }

    private void observeChatContent(
            H5EntitlementService.AccessTicket accessTicket,
            boolean[] contentEmitted,
            ChatGenerateChunk chunk
    ) {
        if (chunk == null || chunk.delta() == null || chunk.delta().isEmpty() || contentEmitted[0]) return;
        contentEmitted[0] = true;
        entitlementService.markChatFirstContent(accessTicket);
    }

    private void refundFailedChatQuietly(
            H5EntitlementService.AccessTicket accessTicket,
            boolean contentEmitted,
            String reason
    ) {
        try {
            entitlementService.refundFailedChat(accessTicket, contentEmitted);
        } catch (RuntimeException refundError) {
            log.error("chat charge refund failed: reason={}", reason, refundError);
        }
    }

    private static boolean isFrontendBridgeChunk(ChatGenerateChunk chunk) {
        String metrics = chunk == null ? "" : chunk.metrics();
        return metrics != null && metrics.contains("frontend_bridge");
    }

    private static void streamDelta(SseEmitter emitter, StringBuilder assistant, ChatGenerateChunk chunk, StStreamControl control) {
        if (chunk.delta() != null && !chunk.delta().isEmpty()) {
            assistant.append(chunk.delta());
            sendEvent(emitter, "delta", Map.of("t", chunk.delta()), control);
        }
    }

    private void restoreSnapshotAfterFailedMutation(StreamKind kind, long conversationId) {
        if (kind == StreamKind.REGENERATE) {
            saveSnapshotQuietly(conversationId);
        }
    }

    private void recordStreamFailureQuietly(
            long conversationId,
            StreamKind kind,
            ChatAuditService.AuditContext audit,
            BusinessException exception,
            String traceId
    ) {
        try {
            auditService.onFailed(audit.assistantMessageId(), audit.taskId(), exception, traceId);
        } catch (Exception auditException) {
            log.error("h5 stream failure persistence failed conversationId={} kind={} taskId={}",
                    conversationId, kind, audit.taskId(), auditException);
        }
    }

    private void recordStreamFailureQuietly(
            long conversationId,
            StreamKind kind,
            ChatAuditService.AuditContext audit,
            Exception exception,
            String traceId
    ) {
        try {
            auditService.onFailed(
                    audit.assistantMessageId(),
                    audit.taskId(),
                    exception,
                    ErrorCode.INTERNAL_ERROR,
                    traceId
            );
        } catch (Exception auditException) {
            log.error("h5 stream failure persistence failed conversationId={} kind={} taskId={}",
                    conversationId, kind, audit.taskId(), auditException);
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

    private static void completeEmitterQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
        }
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

    private long findLastAssistantMessageId(long conversationId, long branchId) {
        List<AppMessage> list = messageMapper.listByConversationBranch(conversationId, branchId, 200);
        for (AppMessage message : list) {
            if (isUsableAssistantAnchor(message)) {
                return canonicalAssistantAnchorId(message, conversationId, branchId);
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "当前没有可继续或重生的 AI 回复");
    }

    private long resolveAssistantAnchor(H5ChatPayload payload, long conversationId, String token) {
        long branchId = chatService.requireActiveBranchId(conversationId, token);
        String raw = payload == null ? null : payload.getTargetAssistantMessageId();
        if (raw == null || raw.isBlank()) {
            return waitForLastAssistantAnchor(conversationId, branchId);
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
        return awaitUsableAssistantAnchor(messageId, conversationId, branchId);
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

    private long waitForLastAssistantAnchor(long conversationId, long branchId) {
        BusinessException lastError = null;
        for (int attempt = 0; attempt < ASSISTANT_ANCHOR_RETRY_ATTEMPTS; attempt++) {
            try {
                return findLastAssistantMessageId(conversationId, branchId);
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

    private long awaitUsableAssistantAnchor(long messageId, long conversationId, long branchId) {
        AppMessage lastSeen = null;
        for (int attempt = 0; attempt < ASSISTANT_ANCHOR_RETRY_ATTEMPTS; attempt++) {
            AppMessage message = messageMapper.findById(messageId);
            if (message != null) {
                lastSeen = message;
            }
            if (message != null
                    && message.getConversationId() != null
                    && message.getConversationId() == conversationId
                    && message.getBranchId() != null
                    && message.getBranchId().longValue() == branchId
                    && "assistant".equalsIgnoreCase(message.getRole())) {
                long canonicalId = canonicalAssistantAnchorId(message, conversationId, branchId);
                AppMessage canonical = messageMapper.findById(canonicalId);
                if (isUsableAssistantAnchor(canonical)) {
                    return canonicalId;
                }
            }
            if (attempt < ASSISTANT_ANCHOR_RETRY_ATTEMPTS - 1) {
                pauseAssistantAnchorRetry();
            }
        }
        if (lastSeen == null
                || lastSeen.getConversationId() == null
                || lastSeen.getConversationId() != conversationId
                || lastSeen.getBranchId() == null
                || lastSeen.getBranchId().longValue() != branchId) {
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String safe = value == null ? "" : value.trim();
            if (!safe.isBlank()) {
                return safe;
            }
        }
        return "";
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

    private long canonicalAssistantAnchorId(AppMessage message, long conversationId, long branchId) {
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
                || root.getBranchId() == null
                || root.getBranchId().longValue() != branchId
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
