package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppConversationArchiveMapper;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import com.example.sillyspringboot.conversation.service.ConversationBranchService;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.integration.sillytavern.OpenRouterGenerationSettingsService;
import com.example.sillyspringboot.ops.service.OperationalStatsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ChatAuditService {

    private final AppConversationMapper conversationMapper;
    private final AppMessageMapper messageMapper;
    private final AppGenerationTaskMapper taskMapper;
    private final AppTokenService tokenService;
    private final AppConversationArchiveMapper conversationArchiveMapper;
    private final OpenRouterGenerationSettingsService generationSettingsService;
    private final OperationalStatsService operationalStatsService;
    private final ConversationMemoryAutoRefreshService memoryAutoRefreshService;
    private final ConversationBranchService branchService;

    public ChatAuditService(
            AppConversationMapper conversationMapper,
            AppMessageMapper messageMapper,
            AppGenerationTaskMapper taskMapper,
            AppTokenService tokenService,
            AppConversationArchiveMapper conversationArchiveMapper,
            OpenRouterGenerationSettingsService generationSettingsService,
            OperationalStatsService operationalStatsService,
            ConversationMemoryAutoRefreshService memoryAutoRefreshService,
            ConversationBranchService branchService
    ) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.taskMapper = taskMapper;
        this.tokenService = tokenService;
        this.conversationArchiveMapper = conversationArchiveMapper;
        this.generationSettingsService = generationSettingsService;
        this.operationalStatsService = operationalStatsService;
        this.memoryAutoRefreshService = memoryAutoRefreshService;
        this.branchService = branchService;
    }

    @Transactional
    public AuditContext onQueued(long conversationId, String userMessage, String clientMessageId, String token, String traceId) {
        return onQueued(conversationId, userMessage, clientMessageId, token, traceId, null, false, null, null);
    }

    @Transactional
    public AuditContext onQueued(
            long conversationId,
            String userMessage,
            String clientMessageId,
            String token,
            String traceId,
            String channel
    ) {
        return onQueued(conversationId, userMessage, clientMessageId, token, traceId, channel, false, null, null);
    }

    @Transactional
    public AuditContext onQueued(
            long conversationId,
            String userMessage,
            String clientMessageId,
            String token,
            String traceId,
            String channel,
            boolean ensureUserMessage
    ) {
        return onQueued(conversationId, userMessage, clientMessageId, token, traceId, channel, ensureUserMessage, null, null);
    }

    @Transactional
    public AuditContext onQueued(
            long conversationId,
            String userMessage,
            String clientMessageId,
            String token,
            String traceId,
            String channel,
            boolean ensureUserMessage,
            String voiceUrl,
            Integer voiceDurationMs
    ) {
        return onQueued(
                conversationId,
                userMessage,
                clientMessageId,
                token,
                traceId,
                channel,
                ensureUserMessage,
                voiceUrl,
                voiceDurationMs,
                null
        );
    }

    @Transactional
    public AuditContext onQueued(
            long conversationId,
            String userMessage,
            String clientMessageId,
            String token,
            String traceId,
            String channel,
            boolean ensureUserMessage,
            String voiceUrl,
            Integer voiceDurationMs,
            String model
    ) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationBranch activeBranch = branchService.requireActiveBranch(conversation);
        Long previousMessageId = messageMapper.findLatestIdByConversationBranch(conversationId, activeBranch.getId());

        boolean hasUserMessage = userMessage != null && !userMessage.isBlank();
        conversationMapper.touchUpdatedAt(conversationId);
        if (hasUserMessage) {
            conversationMapper.setTitleIfNull(conversationId, buildTitle(userMessage));
        }

        AppMessage userMsg = null;
        if (hasUserMessage || ensureUserMessage) {
            userMsg = new AppMessage();
            userMsg.setUserId(userId);
            userMsg.setConversationId(conversationId);
            userMsg.setBranchId(activeBranch.getId());
            userMsg.setParentMessageId(previousMessageId);
            userMsg.setRole("user");
            userMsg.setClientMessageId(clientMessageId);
            userMsg.setContent(hasUserMessage ? userMessage : "");
            userMsg.setVoiceUrl(normalizeVoiceUrl(voiceUrl));
            userMsg.setVoiceDurationMs(normalizeVoiceDurationMs(voiceDurationMs));
            userMsg.setStatus("QUEUED");
            userMsg.setTraceId(traceId);
            messageMapper.insert(userMsg);
            messageMapper.incrementTotalMessageCounter();
            branchService.incrementMemorySourceRevision(conversationId, activeBranch.getId());
            previousMessageId = userMsg.getId();
        }

        AppMessage assistantMsg = new AppMessage();
        assistantMsg.setUserId(userId);
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setBranchId(activeBranch.getId());
        assistantMsg.setParentMessageId(previousMessageId);
        assistantMsg.setRole("assistant");
        assistantMsg.setClientMessageId(clientMessageId);
        assistantMsg.setContent(null);
        assistantMsg.setStatus("QUEUED");
        assistantMsg.setTraceId(traceId);
        messageMapper.insert(assistantMsg);
        messageMapper.incrementTotalMessageCounter();

        String normalizedChannel = normalizeChannel(channel, hasUserMessage ? userMessage : null);
        AppGenerationTask task = new AppGenerationTask();
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setRequestType(normalizedChannel.toLowerCase(Locale.ROOT));
        task.setChannel(normalizedChannel);
        task.setModel(firstNonBlank(model, generationSettingsService.currentModel()));
        task.setClientMessageId(clientMessageId);
        task.setStatus("QUEUED");
        task.setTraceId(traceId);
        task.setQueuedAt(LocalDateTime.now());
        taskMapper.insert(task);
        operationalStatsService.recordGenerationTaskStatus(task.getId(), "QUEUED");

        long userMessageId = userMsg == null ? 0L : userMsg.getId();
        return new AuditContext(userId, userMessageId, assistantMsg.getId(), task.getId());
    }

    private static String firstNonBlank(String preferred, String fallback) {
        String safePreferred = preferred == null ? "" : preferred.trim();
        if (!safePreferred.isBlank()) {
            return safePreferred;
        }
        return fallback == null ? "" : fallback.trim();
    }

    @Transactional
    public void onGenerating(long assistantMessageId, long taskId, String traceId) {
        if (taskMapper.updateStatus(taskId, "GENERATING", null, null, traceId, null) <= 0) {
            return;
        }
        operationalStatsService.recordGenerationTaskStatus(taskId, "GENERATING");
        messageMapper.updateStatusAndContent(assistantMessageId, "GENERATING", null, null, traceId);
    }

    /**
     * Persists canonical content while the task is still non-terminal. Mutation workflows such as
     * regenerate must not mark SUCCESS until the target/swipe promotion has actually completed.
     */
    @Transactional
    public void stageFinalAssistantContent(long assistantMessageId, String finalAssistantText, String traceId) {
        messageMapper.updateStatusAndContent(
                assistantMessageId,
                "GENERATING",
                finalAssistantText,
                null,
                traceId
        );
    }

    @Transactional
    public void onSuccess(long assistantMessageId, long taskId, String finalAssistantText, String traceId) {
        if (taskMapper.updateStatus(taskId, "SUCCESS", null, null, traceId, null) <= 0) {
            return;
        }
        operationalStatsService.recordGenerationTaskStatus(taskId, "SUCCESS");
        messageMapper.updateStatusAndContent(assistantMessageId, "SUCCESS", finalAssistantText, null, traceId);
        AppMessage message = touchConversationByAssistantMessageId(assistantMessageId);
        if (message != null && message.getConversationId() != null) {
            incrementRevisionForVisibleAssistant(message);
            triggerMemoryRefreshAfterCommit(message.getConversationId(), message.getBranchId());
        }
    }

    @Transactional
    public void onStopped(long assistantMessageId, long taskId, String partialAssistantText, String traceId) {
        if (taskMapper.updateStatus(taskId, "STOPPED", null, null, traceId, null) <= 0) {
            return;
        }
        operationalStatsService.recordGenerationTaskStatus(taskId, "STOPPED");
        messageMapper.updateStatusAndContent(assistantMessageId, "STOPPED", partialAssistantText, null, traceId);
        AppMessage message = touchConversationByAssistantMessageId(assistantMessageId);
        if (message != null && message.getConversationId() != null) {
            incrementRevisionForVisibleAssistant(message);
            triggerMemoryRefreshAfterCommit(message.getConversationId(), message.getBranchId());
        }
    }

    @Transactional
    public void onFailed(long assistantMessageId, long taskId, ErrorCode code, String traceId) {
        onFailed(assistantMessageId, taskId, code, traceId, null);
    }

    @Transactional
    public void onFailed(long assistantMessageId, long taskId, ErrorCode code, String traceId, String rawMessage) {
        persistFailure(
                assistantMessageId,
                taskId,
                ChatFailureDetails.fromCode(code, rawMessage, traceId),
                traceId
        );
    }

    @Transactional
    public void onFailed(long assistantMessageId, long taskId, BusinessException exception, String traceId) {
        persistFailure(
                assistantMessageId,
                taskId,
                ChatFailureDetails.fromBusinessException(exception, traceId),
                traceId
        );
    }

    @Transactional
    public void onFailed(long assistantMessageId, long taskId, Throwable throwable, ErrorCode fallbackCode, String traceId) {
        persistFailure(
                assistantMessageId,
                taskId,
                ChatFailureDetails.fromThrowable(fallbackCode, throwable, traceId),
                traceId
        );
    }

    @Transactional
    public void touchAfterAssistantContentUpdate(long assistantMessageId) {
        AppMessage message = touchConversationByAssistantMessageId(assistantMessageId);
        incrementRevisionForVisibleAssistant(message);
    }

    private void incrementRevisionForVisibleAssistant(AppMessage message) {
        if (message == null
                || message.getConversationId() == null
                || message.getBranchId() == null
                || message.getBranchId() <= 0
                || message.getContent() == null
                || message.getContent().isBlank()) {
            return;
        }
        String status = message.getStatus() == null ? "" : message.getStatus();
        if ("SUCCESS".equalsIgnoreCase(status) || "STOPPED".equalsIgnoreCase(status)) {
            branchService.incrementMemorySourceRevision(message.getConversationId(), message.getBranchId());
        }
    }

    private static String buildTitle(String userMessage) {
        String normalized = userMessage.strip().replaceAll("\\s+", " ");
        int max = 30;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private AppMessage touchConversationByAssistantMessageId(long assistantMessageId) {
        AppMessage message = messageMapper.findById(assistantMessageId);
        if (message == null || message.getConversationId() == null) {
            return null;
        }
        long conversationId = message.getConversationId();
        conversationMapper.touchUpdatedAt(conversationId);
        conversationMapper.setTitleToCharacterNameIfNull(conversationId);
        if (message.getUserId() != null) {
            conversationArchiveMapper.deleteByUserAndConversation(message.getUserId(), conversationId);
        }
        if (message.getBranchId() != null && message.getBranchId() > 0) {
            try {
                branchService.touchBranch(message.getBranchId());
            } catch (Exception ignored) {
            }
        }
        return message;
    }

    private void triggerMemoryRefreshAfterCommit(long conversationId, Long branchId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            memoryAutoRefreshService.maybeTriggerAfterGenerationSuccess(conversationId, branchId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                memoryAutoRefreshService.maybeTriggerAfterGenerationSuccess(conversationId, branchId);
            }
        });
    }

    private static String normalizeChannel(String channel, String userMessage) {
        if (channel != null && !channel.isBlank()) {
            return channel.trim().toUpperCase(Locale.ROOT);
        }
        return userMessage != null && !userMessage.isBlank() ? "CHAT_STREAM" : "CONTINUE";
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

    private void persistFailure(
            long assistantMessageId,
            long taskId,
            ChatFailureDetails.TaskFailure failure,
            String traceId
    ) {
        String errorCode = failure == null ? ErrorCode.INTERNAL_ERROR.name() : failure.errorCode();
        String errorMessage = failure == null ? ErrorCode.INTERNAL_ERROR.name() : failure.errorMessage();
        Integer httpStatus = failure == null ? 500 : failure.httpStatus();
        if (taskMapper.updateStatus(taskId, "FAILED", errorCode, errorMessage, traceId, httpStatus) <= 0) {
            return;
        }
        operationalStatsService.recordGenerationTaskStatus(taskId, "FAILED");
        messageMapper.updateStatusAndContent(assistantMessageId, "FAILED", null, errorCode, traceId);
        touchConversationByAssistantMessageId(assistantMessageId);
    }

    public record AuditContext(long userId, long userMessageId, long assistantMessageId, long taskId) {}
}
