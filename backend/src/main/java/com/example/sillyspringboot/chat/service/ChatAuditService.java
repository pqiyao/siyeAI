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

    public ChatAuditService(
            AppConversationMapper conversationMapper,
            AppMessageMapper messageMapper,
            AppGenerationTaskMapper taskMapper,
            AppTokenService tokenService,
            AppConversationArchiveMapper conversationArchiveMapper,
            OpenRouterGenerationSettingsService generationSettingsService,
            OperationalStatsService operationalStatsService,
            ConversationMemoryAutoRefreshService memoryAutoRefreshService
    ) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.taskMapper = taskMapper;
        this.tokenService = tokenService;
        this.conversationArchiveMapper = conversationArchiveMapper;
        this.generationSettingsService = generationSettingsService;
        this.operationalStatsService = operationalStatsService;
        this.memoryAutoRefreshService = memoryAutoRefreshService;
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
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }

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
            userMsg.setRole("user");
            userMsg.setClientMessageId(clientMessageId);
            userMsg.setContent(hasUserMessage ? userMessage : "");
            userMsg.setVoiceUrl(normalizeVoiceUrl(voiceUrl));
            userMsg.setVoiceDurationMs(normalizeVoiceDurationMs(voiceDurationMs));
            userMsg.setStatus("QUEUED");
            userMsg.setTraceId(traceId);
            messageMapper.insert(userMsg);
            messageMapper.incrementTotalMessageCounter();
        }

        AppMessage assistantMsg = new AppMessage();
        assistantMsg.setUserId(userId);
        assistantMsg.setConversationId(conversationId);
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
        task.setModel(generationSettingsService.currentModel());
        task.setClientMessageId(clientMessageId);
        task.setStatus("QUEUED");
        task.setTraceId(traceId);
        task.setQueuedAt(LocalDateTime.now());
        taskMapper.insert(task);
        operationalStatsService.recordGenerationTaskStatus(task.getId(), "QUEUED");

        long userMessageId = userMsg == null ? 0L : userMsg.getId();
        return new AuditContext(userId, userMessageId, assistantMsg.getId(), task.getId());
    }

    @Transactional
    public void onGenerating(long assistantMessageId, long taskId, String traceId) {
        taskMapper.updateStatus(taskId, "GENERATING", null, null, traceId, null);
        operationalStatsService.recordGenerationTaskStatus(taskId, "GENERATING");
        messageMapper.updateStatusAndContent(assistantMessageId, "GENERATING", null, null, traceId);
    }

    @Transactional
    public void onSuccess(long assistantMessageId, long taskId, String finalAssistantText, String traceId) {
        taskMapper.updateStatus(taskId, "SUCCESS", null, null, traceId, null);
        operationalStatsService.recordGenerationTaskStatus(taskId, "SUCCESS");
        messageMapper.updateStatusAndContent(assistantMessageId, "SUCCESS", finalAssistantText, null, traceId);
        Long conversationId = touchConversationByAssistantMessageId(assistantMessageId);
        if (conversationId != null) {
            triggerMemoryRefreshAfterCommit(conversationId);
        }
    }

    @Transactional
    public void onStopped(long assistantMessageId, long taskId, String partialAssistantText, String traceId) {
        taskMapper.updateStatus(taskId, "STOPPED", null, null, traceId, null);
        operationalStatsService.recordGenerationTaskStatus(taskId, "STOPPED");
        messageMapper.updateStatusAndContent(assistantMessageId, "STOPPED", partialAssistantText, null, traceId);
        touchConversationByAssistantMessageId(assistantMessageId);
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
        touchConversationByAssistantMessageId(assistantMessageId);
    }

    private static String buildTitle(String userMessage) {
        String normalized = userMessage.strip().replaceAll("\\s+", " ");
        int max = 30;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private Long touchConversationByAssistantMessageId(long assistantMessageId) {
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
        return conversationId;
    }

    private void triggerMemoryRefreshAfterCommit(long conversationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            memoryAutoRefreshService.maybeTriggerAfterGenerationSuccess(conversationId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                memoryAutoRefreshService.maybeTriggerAfterGenerationSuccess(conversationId);
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
        taskMapper.updateStatus(taskId, "FAILED", errorCode, errorMessage, traceId, httpStatus);
        operationalStatsService.recordGenerationTaskStatus(taskId, "FAILED");
        messageMapper.updateStatusAndContent(assistantMessageId, "FAILED", null, errorCode, traceId);
        touchConversationByAssistantMessageId(assistantMessageId);
    }

    public record AuditContext(long userId, long userMessageId, long assistantMessageId, long taskId) {}
}
