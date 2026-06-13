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
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
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
    private final SocialUploadRateLimiter rateLimiter;

    public AppChatController(
            AppChatService chatService,
            ChatGenerationDispatcher dispatcher,
            ChatAuditService auditService,
            ChatSnapshotService snapshotService,
            H5UploadService uploadService,
            SocialUploadRateLimiter rateLimiter
    ) {
        this.chatService = chatService;
        this.dispatcher = dispatcher;
        this.auditService = auditService;
        this.snapshotService = snapshotService;
        this.uploadService = uploadService;
        this.rateLimiter = rateLimiter;
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
            chatService.unregisterControl(conversationId);
            emitter.complete();
        });
        emitter.onError(ex -> {
            control.cancel();
            chatService.unregisterControl(conversationId);
        });
        emitter.onCompletion(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId);
        });

        // 先入队：立刻发 QUEUED；队列满则直接标准化繁忙
        sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "QUEUED"));
        ChatAuditService.AuditContext audit = auditService.onQueued(
                conversationId,
                request.getUserMessage() == null ? "" : request.getUserMessage(),
                clientMessageId,
                token,
                traceId,
                "CHAT_STREAM"
        );
        try {
            dispatcher.submit(() -> runGeneration(emitter, request, token, control, traceId, audit));
        } catch (RejectedExecutionException ex) {
            chatService.unregisterControl(conversationId);
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
                sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "STOPPED"));
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

                // 运营级：生成前确保 ST chat 快照存在，便于恢复与后续玩法（continue/regen/swipe）消息定位
                snapshotService.ensureSnapshot(conversationId);

                StringBuilder assistant = new StringBuilder();
                String userRef = audit.userMessageId() > 0 ? ("root:" + audit.userMessageId()) : "";
                chatService.streamGenerate(request, token, userRef, (ChatGenerateChunk c) -> {
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
                } else if (control.isCancelled()) {
                    auditService.onStopped(audit.assistantMessageId(), audit.taskId(), assistant.toString(), traceId());
                    sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "STOPPED"));
                } else {
                    auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), assistant.toString(), traceId());
                    try {
                        chatService.syncAssistantReplyToSt(conversationId, "root:" + audit.assistantMessageId(), assistant.toString(), token);
                    } catch (Exception ignored) {
                    }
                    try {
                        snapshotService.saveSnapshotFromDb(conversationId, 800);
                    } catch (Exception ignored) {
                        // 快照回写失败不影响本次生成对用户可用性（可观测/可恢复在后续补齐）
                    }
                    sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "SUCCESS"));
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
            chatService.unregisterControl(conversationId);
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
        rateLimiter.checkUpload(userId, "app_chat_image");
        return ApiResult.ok(Map.of("url", uploadService.saveImageAndGetUrl(file)));
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
            chatService.unregisterControl(conversationId);
            emitter.complete();
        });
        emitter.onError(ex -> {
            control.cancel();
            chatService.unregisterControl(conversationId);
        });
        emitter.onCompletion(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId);
        });

        sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "QUEUED"));
        // continue 不新增 user 消息，只落 assistant 占位 + task
        ChatAuditService.AuditContext audit = auditService.onQueued(
                conversationId,
                "",
                clientMessageId,
                token,
                traceId,
                "CONTINUE_STREAM"
        );
        try {
            dispatcher.submit(() -> runPhase5(emitter, token, control, traceId, audit, conversationId, clientMessageId,
                    (onChunk) -> chatService.streamContinue(request, token, onChunk, control),
                    null,
                    "root:" + audit.assistantMessageId()));
        } catch (RejectedExecutionException ex) {
            chatService.unregisterControl(conversationId);
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
            chatService.unregisterControl(conversationId);
            emitter.complete();
        });
        emitter.onError(ex -> {
            control.cancel();
            chatService.unregisterControl(conversationId);
        });
        emitter.onCompletion(() -> {
            control.cancel();
            chatService.unregisterControl(conversationId);
        });

        sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "QUEUED"));
        ChatAuditService.AuditContext audit = auditService.onQueued(
                conversationId,
                "",
                clientMessageId,
                token,
                traceId,
                "REGEN_STREAM"
        );
        try {
            long targetIdSafe = safeParseLong(request.getTargetMessageId());
            String stRef = targetIdSafe > 0 ? ("root:" + targetIdSafe) : ("root:" + audit.assistantMessageId());
            dispatcher.submit(() -> runPhase5(emitter, token, control, traceId, audit, conversationId, clientMessageId,
                    (onChunk) -> chatService.streamRegenerate(request, token, onChunk, control),
                    (finalText) -> {
                        try {
                            long targetId = Long.parseLong(request.getTargetMessageId());
                            chatService.promoteRegenerateVariant(conversationId, targetId, audit.assistantMessageId(), token);
                        } catch (Exception ignored) {
                        }
                    },
                    stRef));
        } catch (RejectedExecutionException ex) {
            chatService.unregisterControl(conversationId);
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
    private interface Phase5OnSuccess {
        void accept(String finalAssistantText);
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
            Phase5OnSuccess onSuccess,
            String stMessageRef
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
                auditService.onStopped(audit.assistantMessageId(), audit.taskId(), "", traceId());
                sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "STOPPED"));
                emitter.complete();
                return;
            }

            try (ChatConcurrencyGate.Lease lease0 = lease;
                 ChatGenerationTimeout timeout = ChatGenerationTimeout.start(control, chatService.generationTimeoutSeconds())) {
                auditService.onGenerating(audit.assistantMessageId(), audit.taskId(), traceId());
                sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "PENDING"));
                sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "GENERATING"));

                snapshotService.ensureSnapshot(conversationId);

                StringBuilder assistant = new StringBuilder();
                runner.run((ChatGenerateChunk c) -> {
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
                    sendQuietly(emitter, "error", ChatSseEvent.error(conversationId, clientMessageId, ErrorCode.UPSTREAM_ERROR, "生成超时，请稍后重试", traceId()));
                } else if (control.isCancelled()) {
                    auditService.onStopped(audit.assistantMessageId(), audit.taskId(), assistant.toString(), traceId());
                    sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "STOPPED"));
                } else {
                    auditService.onSuccess(audit.assistantMessageId(), audit.taskId(), assistant.toString(), traceId());
                    if (onSuccess != null) {
                        try {
                            onSuccess.accept(assistant.toString());
                        } catch (Exception ignored) {
                        }
                    }
                    try {
                        chatService.syncAssistantReplyToSt(conversationId, stMessageRef, assistant.toString(), token);
                    } catch (Exception ignored) {
                    }
                    try {
                        snapshotService.saveSnapshotFromDb(conversationId, 800);
                    } catch (Exception ignored) {
                    }
                    sendQuietly(emitter, "state", ChatSseEvent.state(conversationId, clientMessageId, "SUCCESS"));
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
            chatService.unregisterControl(conversationId);
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
