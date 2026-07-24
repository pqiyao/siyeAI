package com.example.sillyspringboot.chat.web;

import com.example.sillyspringboot.chat.dto.AppChatStopRequest;
import com.example.sillyspringboot.chat.dto.AppChatStreamRequest;
import com.example.sillyspringboot.chat.dto.AppChatContinueRequest;
import com.example.sillyspringboot.chat.dto.AppChatRegenerateRequest;
import com.example.sillyspringboot.chat.dto.AppChatListSwipesRequest;
import com.example.sillyspringboot.chat.dto.AppChatSwitchSwipeRequest;
import com.example.sillyspringboot.chat.dto.ChatSseEvent;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.chat.service.ChatGenerationDispatcher;
import com.example.sillyspringboot.chat.service.ChatConcurrencyGate;
import com.example.sillyspringboot.chat.service.ChatGenerationTimeout;
import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.shared.error.AppErrorResponse;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.web.ApiResult;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static com.example.sillyspringboot.shared.error.GlobalExceptionHandler.MDC_TRACE_ID;

@RestController
@RequestMapping("/api/app/chat")
public class AppChatController {

    private final AppChatService chatService;
    private final ChatGenerationDispatcher dispatcher;
    private final ChatAuditService auditService;
    private final ChatSnapshotService snapshotService;
    private final H5UploadService uploadService;

    public AppChatController(
            AppChatService chatService,
            ChatGenerationDispatcher dispatcher,
            ChatAuditService auditService,
            ChatSnapshotService snapshotService,
            H5UploadService uploadService
    ) {
        this.chatService = chatService;
        this.dispatcher = dispatcher;
        this.auditService = auditService;
        this.snapshotService = snapshotService;
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Valid @RequestBody AppChatStreamRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        SseEmitter emitter = new SseEmitter(chatService.sseTimeoutMillis());
        String traceId = traceId();

        StStreamControl control = new StStreamControl();
        long conversationId = request.getConversationId();
        String clientMessageId = request.getClientMessageId();

        chatService.registerControl(conversationId, control);
        emitter.onTimeout(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            emitter.complete();
        });
        emitter.onError(ex -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
        });
        emitter.onCompletion(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
        });

        // 先入队：立刻发 QUEUED；队列满则直接标准化繁忙
        sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "QUEUED"));
        ChatAuditService.AuditContext audit;
        try {
            audit = auditService.onQueued(
                    conversationId,
                    request.getUserMessage() == null ? "" : request.getUserMessage(),
                    clientMessageId,
                    token,
                    traceId,
                    "CHAT_STREAM"
            );
            chatService.bindControlTask(conversationId, audit.taskId(), control);
        } catch (RuntimeException ex) {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            throw ex;
        }
        try {
            dispatcher.submit(() -> runGeneration(emitter, request, token, control, traceId, audit));
        } catch (RejectedExecutionException ex) {
            chatService.unregisterControl(conversationId, control);
            auditService.onFailed(
                    audit.assistantMessageId(),
                    audit.taskId(),
                    ErrorCode.SERVICE_BUSY,
                    traceId,
                    "dispatcher rejected execution"
            );
            sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试", traceId));
            emitter.complete();
        }

        return emitter;
    }

    private void runGeneration(
            SseEmitter emitter,
            AppChatStreamRequest request,
            String token,
            StStreamControl control,
            String traceId,
            ChatAuditService.AuditContext audit
    ) {
        long conversationId = request.getConversationId();
        String clientMessageId = request.getClientMessageId();
        long start = System.nanoTime();
        long maxWaitNanos = Duration.ofSeconds(chatService.maxQueueWaitSeconds()).toNanos();

        ChatConcurrencyGate.Lease lease = null;
        try {
            // 排队等待闸门：避免无界阻塞；超时则返回繁忙
            while (!control.isCancelled()) {
                try {
                    lease = chatService.acquireLease(token);
                    break;
                } catch (BusinessException be) {
                    if (be.getErrorCode() != ErrorCode.SERVICE_BUSY && be.getErrorCode() != ErrorCode.RATE_LIMITED) {
                        throw be;
                    }
                    if (System.nanoTime() - start > maxWaitNanos) {
                        auditService.onFailed(
                                audit.assistantMessageId(),
                                audit.taskId(),
                                ErrorCode.SERVICE_BUSY,
                                traceId,
                                "queue wait timeout"
                        );
                        sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试", traceId()));
                        emitter.complete();
                        return;
                    }
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (control.isCancelled()) {
                auditService.onStopped(audit.assistantMessageId(), audit.taskId(), "", traceId);
                sendQuietly(emitter, "state", ChatSseEvent.stateWithFinalContent(
                        conversationId,
                        clientMessageId,
                        "STOPPED",
                        ""
                ));
                emitter.complete();
                return;
            }
            if (lease == null) {
                auditService.onFailed(
                        audit.assistantMessageId(),
                        audit.taskId(),
                        ErrorCode.SERVICE_BUSY,
                        traceId(),
                        "queue wait timeout"
                );
                sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试", traceId()));
                emitter.complete();
                return;
            }

            try (ChatConcurrencyGate.Lease lease0 = lease;
                 ChatGenerationTimeout timeout = ChatGenerationTimeout.start(control, chatService.generationTimeoutSeconds())) {
                auditService.onGenerating(audit.assistantMessageId(), audit.taskId(), traceId());
                sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "PENDING"));
                sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "GENERATING"));

                StringBuilder assistant = new StringBuilder();
                boolean[] frontendBridgeGenerated = {false};
                String userRef = audit.userMessageId() > 0 ? ("root:" + audit.userMessageId()) : "";
                chatService.streamGenerate(request, token, userRef, (ChatGenerateChunk c) -> {
                    if (isFrontendBridgeChunk(c)) frontendBridgeGenerated[0] = true;
                    if (c.delta() != null) assistant.append(c.delta());
                    sendQuietly(emitter, control, "chunk", ChatSseEvent.chunk(conversationId, clientMessageId, c.chunkIndex(), c.delta(), c.done()));
                }, control);

                if (timeout.isTimedOut()) {
                    auditService.onFailed(
                            audit.assistantMessageId(),
                            audit.taskId(),
                            ErrorCode.UPSTREAM_ERROR,
                            traceId(),
                            "generation timed out after " + chatService.generationTimeoutSeconds() + " seconds"
                    );
                    sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试", traceId()));
                } else {
                    AppChatService.AssistantOutputNormalization normalization = frontendBridgeGenerated[0]
                            ? AppChatService.AssistantOutputNormalization.passthrough(assistant.toString())
                            : chatService.normalizeAssistantOutput(conversationId, assistant.toString(), token);
                    String finalContent = normalization.content();
                    if (timeout.isTimedOut()) {
                        auditService.onFailed(
                                audit.assistantMessageId(),
                                audit.taskId(),
                                ErrorCode.UPSTREAM_ERROR,
                                traceId(),
                                "generation timed out after " + chatService.generationTimeoutSeconds() + " seconds"
                        );
                        sendQuietly(emitter, "error", ChatSseEvent.error(
                                conversationId,
                                clientMessageId,
                                ErrorCode.UPSTREAM_ERROR,
                                "生成超时，请稍后重试",
                                traceId()
                        ));
                    } else {
                        boolean cancelled = control.isCancelled();
                        if (cancelled) {
                            auditService.onStopped(audit.assistantMessageId(), audit.taskId(), finalContent, traceId());
                        } else {
                            auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), finalContent, traceId());
                        }
                        if (!frontendBridgeGenerated[0]) {
                            boolean assistantSynced = false;
                            try {
                                assistantSynced = chatService.syncAssistantReplyToSt(
                                        conversationId,
                                        "root:" + audit.assistantMessageId(),
                                        finalContent,
                                        token,
                                        normalization.finalized()
                                );
                            } catch (Exception ignored) {
                            }
                            if (!assistantSynced) {
                                saveSnapshotQuietly(conversationId);
                            }
                        }
                        sendQuietly(emitter, "state", ChatSseEvent.stateWithFinalContent(
                                conversationId,
                                clientMessageId,
                                cancelled ? "STOPPED" : "SUCCESS",
                                finalContent
                        ));
                    }
                }
                emitter.complete();
            }
        } catch (BusinessException be) {
            auditService.onFailed(audit.assistantMessageId(), audit.taskId(), be, traceId());
            sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, be.getErrorCode(), be.getMessage(), traceId()));
            emitter.complete();
        } catch (Exception ex) {
            auditService.onFailed(audit.assistantMessageId(), audit.taskId(), ex, ErrorCode.INTERNAL_ERROR, traceId());
            sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.INTERNAL_ERROR, "服务暂时不可用，请稍后重试", traceId()));
            emitter.complete();
        } finally {
            chatService.unregisterControl(conversationId, control);
        }
    }

    @PostMapping("/stop")
    public ApiResult<Boolean> stop(
            @Valid @RequestBody AppChatStopRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        boolean cancelled = chatService.stop(request.getConversationId(), token);
        return ApiResult.ok(cancelled);
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        long userId = chatService.resolveUserId(token);
        return ApiResult.ok(Map.of("url", uploadService.saveOwnedImageAndGetUrl(file, userId)));
    }

    // ===== Phase 5 endpoints (stubs until ST snapshot/swipe wiring is implemented) =====

    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter continueStream(
            @Valid @RequestBody AppChatContinueRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        SseEmitter emitter = new SseEmitter(chatService.sseTimeoutMillis());
        String traceId = traceId();

        StStreamControl control = new StStreamControl();
        long conversationId = request.getConversationId();
        String clientMessageId = request.getClientMessageId();
        chatService.registerControl(conversationId, control);
        emitter.onTimeout(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            emitter.complete();
        });
        emitter.onError(ex -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
        });
        emitter.onCompletion(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
        });

        sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "QUEUED"));
        // continue 不新增 user 消息，只落 assistant 占位 + task
        ChatAuditService.AuditContext audit;
        try {
            audit = auditService.onQueued(
                    conversationId,
                    "",
                    clientMessageId,
                    token,
                    traceId,
                    "CONTINUE_STREAM"
            );
            chatService.bindControlTask(conversationId, audit.taskId(), control);
        } catch (RuntimeException ex) {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            throw ex;
        }
        try {
            dispatcher.submit(() -> runPhase5(emitter, token, control, traceId, audit, conversationId, clientMessageId,
                    (onChunk) -> chatService.streamContinue(request, token, onChunk, control),
                    (finalText, generatedByFrontendBridge, outputRegexApplied, cancelled) -> {
                        long anchorId = safeParseLong(request.getTargetMessageId());
                        if (cancelled && finalText.isBlank()) {
                            chatService.abortContinueEmpty(
                                    conversationId,
                                    audit.assistantMessageId(),
                                    audit.taskId(),
                                    token
                                );
                                return finalText;
                            }
                        chatService.finalizeContinueAsMessage(
                                conversationId,
                                anchorId,
                                audit.assistantMessageId(),
                                audit.taskId(),
                                finalText,
                                token,
                                !generatedByFrontendBridge,
                                outputRegexApplied,
                                cancelled
                        );
                        return finalText;
                    },
                    null));
        } catch (RejectedExecutionException ex) {
            chatService.unregisterControl(conversationId, control);
            auditService.onFailed(
                    audit.assistantMessageId(),
                    audit.taskId(),
                    ErrorCode.SERVICE_BUSY,
                    traceId,
                    "dispatcher rejected execution"
            );
            sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试", traceId));
            emitter.complete();
        }
        return emitter;
    }

    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateStream(
            @Valid @RequestBody AppChatRegenerateRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        SseEmitter emitter = new SseEmitter(chatService.sseTimeoutMillis());
        String traceId = traceId();

        StStreamControl control = new StStreamControl();
        long conversationId = request.getConversationId();
        String clientMessageId = request.getClientMessageId();
        chatService.registerControl(conversationId, control);
        emitter.onTimeout(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            emitter.complete();
        });
        emitter.onError(ex -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
        });
        emitter.onCompletion(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
        });

        sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "QUEUED"));
        ChatAuditService.AuditContext audit;
        try {
            audit = auditService.onQueued(
                    conversationId,
                    "",
                    clientMessageId,
                    token,
                    traceId,
                    "REGEN_STREAM"
            );
            chatService.bindControlTask(conversationId, audit.taskId(), control);
        } catch (RuntimeException ex) {
            control.cancel();
            chatService.unregisterControl(conversationId, control);
            throw ex;
        }
        try {
            dispatcher.submit(() -> runPhase5(emitter, token, control, traceId, audit, conversationId, clientMessageId,
                    (onChunk) -> chatService.streamRegenerate(request, token, onChunk, control),
                    (finalText, generatedByFrontendBridge, outputRegexApplied, cancelled) -> {
                        long targetId = Long.parseLong(request.getTargetMessageId());
                        if (cancelled) {
                            String restoredContent = chatService.getAssistantMessageContent(
                                    conversationId,
                                    targetId,
                                    token
                            );
                            auditService.onStopped(audit.assistantMessageId(), audit.taskId(), "", traceId);
                            saveSnapshotQuietly(conversationId);
                            return restoredContent;
                        }
                        auditService.stageFinalAssistantContent(audit.assistantMessageId(), finalText, traceId);
                        chatService.promoteRegenerateVariant(
                                conversationId,
                                targetId,
                                audit.assistantMessageId(),
                                token,
                                !generatedByFrontendBridge,
                                outputRegexApplied
                        );
                        auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), finalText, traceId);
                        return finalText;
                    },
                    () -> snapshotService.saveSnapshotFromDb(conversationId, 800)));
        } catch (RejectedExecutionException ex) {
            chatService.unregisterControl(conversationId, control);
            auditService.onFailed(
                    audit.assistantMessageId(),
                    audit.taskId(),
                    ErrorCode.SERVICE_BUSY,
                    traceId,
                    "dispatcher rejected execution"
            );
            sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试", traceId));
            emitter.complete();
        }
        return emitter;
    }

    @FunctionalInterface
    private interface Phase5Runner {
        void run(java.util.function.Consumer<ChatGenerateChunk> onChunk);
    }

    @FunctionalInterface
    private interface Phase5OnFinalize {
        String accept(
                String finalAssistantText,
                boolean generatedByFrontendBridge,
                boolean outputRegexApplied,
                boolean cancelled
        );
    }

    private void runPhase5(
            SseEmitter emitter,
            String token,
            StStreamControl control,
            String traceId,
            ChatAuditService.AuditContext audit,
            long conversationId,
            String clientMessageId,
            Phase5Runner runner,
            Phase5OnFinalize onFinalize,
            Runnable restoreRuntimeState
    ) {
        long start = System.nanoTime();
        long maxWaitNanos = Duration.ofSeconds(chatService.maxQueueWaitSeconds()).toNanos();
        ChatConcurrencyGate.Lease lease = null;
        try {
            while (!control.isCancelled()) {
                try {
                    lease = chatService.acquireLease(token);
                    break;
                } catch (BusinessException be) {
                    if (be.getErrorCode() != ErrorCode.SERVICE_BUSY && be.getErrorCode() != ErrorCode.RATE_LIMITED) {
                        throw be;
                    }
                    if (System.nanoTime() - start > maxWaitNanos) {
                        auditService.onFailed(
                                audit.assistantMessageId(),
                                audit.taskId(),
                                ErrorCode.SERVICE_BUSY,
                                traceId(),
                                "queue wait timeout"
                        );
                        sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试", traceId()));
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(200);
                }
            }
            if (control.isCancelled() || lease == null) {
                String terminalContent = "";
                if (onFinalize == null) {
                    auditService.onStopped(audit.assistantMessageId(), audit.taskId(), "", traceId());
                } else {
                    terminalContent = onFinalize.accept("", false, false, true);
                }
                sendQuietly(emitter, "state", ChatSseEvent.stateWithFinalContent(
                        conversationId,
                        clientMessageId,
                        "STOPPED",
                        terminalContent == null ? "" : terminalContent
                ));
                emitter.complete();
                return;
            }

            try (ChatConcurrencyGate.Lease lease0 = lease;
                 ChatGenerationTimeout timeout = ChatGenerationTimeout.start(control, chatService.generationTimeoutSeconds())) {
                auditService.onGenerating(audit.assistantMessageId(), audit.taskId(), traceId());
                sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "PENDING"));
                sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "GENERATING"));

                StringBuilder assistant = new StringBuilder();
                boolean[] frontendBridgeGenerated = {false};
                runner.run((ChatGenerateChunk c) -> {
                    if (isFrontendBridgeChunk(c)) frontendBridgeGenerated[0] = true;
                    if (c.delta() != null) assistant.append(c.delta());
                    sendQuietly(emitter, control, "chunk", ChatSseEvent.chunk(conversationId, clientMessageId, c.chunkIndex(), c.delta(), c.done()));
                });

                if (timeout.isTimedOut()) {
                    auditService.onFailed(
                            audit.assistantMessageId(),
                            audit.taskId(),
                            ErrorCode.UPSTREAM_ERROR,
                            traceId(),
                            "generation timed out after " + chatService.generationTimeoutSeconds() + " seconds"
                    );
                    restoreRuntimeStateQuietly(restoreRuntimeState);
                    sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试", traceId()));
                } else {
                    boolean cancelled = control.isCancelled();
                    String rawContent = assistant.toString().trim();
                    if (!cancelled && rawContent.isBlank()) {
                        throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "模型返回空内容，生成失败");
                    }
                    AppChatService.AssistantOutputNormalization normalization = frontendBridgeGenerated[0]
                            ? AppChatService.AssistantOutputNormalization.passthrough(rawContent)
                            : chatService.normalizeAssistantOutput(conversationId, rawContent, token);
                    if (timeout.isTimedOut()) {
                        throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试");
                    }
                    cancelled = control.isCancelled();
                    String finalContent = normalization.content();
                    if (onFinalize == null) {
                        throw new IllegalStateException("phase5 finalizer is required");
                    }
                    String terminalContent = onFinalize.accept(
                            finalContent,
                            frontendBridgeGenerated[0],
                            normalization.finalized(),
                            cancelled
                    );
                    sendQuietly(emitter, "state", ChatSseEvent.stateWithFinalContent(
                            conversationId,
                            clientMessageId,
                            cancelled ? "STOPPED" : "SUCCESS",
                            terminalContent == null ? "" : terminalContent
                    ));
                }
                emitter.complete();
            }
        } catch (BusinessException be) {
            restoreRuntimeStateQuietly(restoreRuntimeState);
            auditService.onFailed(audit.assistantMessageId(), audit.taskId(), be, traceId());
            sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, be.getErrorCode(), be.getMessage(), traceId()));
            emitter.complete();
        } catch (Exception ex) {
            restoreRuntimeStateQuietly(restoreRuntimeState);
            auditService.onFailed(audit.assistantMessageId(), audit.taskId(), ex, ErrorCode.INTERNAL_ERROR, traceId());
            sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.INTERNAL_ERROR, "服务暂时不可用，请稍后重试", traceId()));
            emitter.complete();
        } finally {
            chatService.unregisterControl(conversationId, control);
        }
    }

    private static void restoreRuntimeStateQuietly(Runnable restoreRuntimeState) {
        if (restoreRuntimeState == null) {
            return;
        }
        try {
            restoreRuntimeState.run();
        } catch (Exception ignored) {
        }
    }

    private void saveSnapshotQuietly(long conversationId) {
        try {
            snapshotService.saveSnapshotFromDb(conversationId, 800);
        } catch (Exception ignored) {
        }
    }

    @PostMapping("/swipe/list")
    public ApiResult<Object> listSwipes(
            @Valid @RequestBody AppChatListSwipesRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        Object variants = chatService.listSwipes(request.getConversationId(), request.getMessageId(), token);
        return ApiResult.ok(variants);
    }

    @PostMapping("/swipe/switch")
    public ApiResult<Object> switchSwipe(
            @Valid @RequestBody AppChatSwitchSwipeRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        Object chosen = chatService.switchSwipe(request.getConversationId(), request.getMessageId(), request.getVariantIndex(), token);
        return ApiResult.ok(chosen);
    }

    private static void sendQuietly(SseEmitter emitter, String eventName, ChatSseEvent payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException ignored) {
            // 客户端断开：交由上层 complete
        }
    }

    private static boolean sendQuietly(SseEmitter emitter, StStreamControl control, String eventName, ChatSseEvent payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            return true;
        } catch (IOException | IllegalStateException ignored) {
            if (control != null) {
                control.cancel();
            }
        }
        return false;
    }

    private static boolean isFrontendBridgeChunk(ChatGenerateChunk chunk) {
        String metrics = chunk == null ? "" : chunk.metrics();
        return metrics != null && metrics.contains("frontend_bridge");
    }

    private static String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    private static String traceId() {
        String id = MDC.get(MDC_TRACE_ID);
        return id != null ? id : "unknown";
    }

    private static long safeParseLong(String s) {
        if (s == null) return -1;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }
}
