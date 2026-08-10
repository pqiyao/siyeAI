package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.ops.service.OperationalStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaleGenerationTaskService {

    private static final Logger log = LoggerFactory.getLogger(StaleGenerationTaskService.class);
    private static final int BATCH_SIZE = 200;
    private static final int MAX_BATCHES = 10;

    private final AppGenerationTaskMapper taskMapper;
    private final AppMessageMapper messageMapper;
    private final OperationalStatsService operationalStatsService;
    private final AppChatProperties chatProperties;

    public StaleGenerationTaskService(
            AppGenerationTaskMapper taskMapper,
            AppMessageMapper messageMapper,
            OperationalStatsService operationalStatsService,
            AppChatProperties chatProperties
    ) {
        this.taskMapper = taskMapper;
        this.messageMapper = messageMapper;
        this.operationalStatsService = operationalStatsService;
        this.chatProperties = chatProperties;
    }

    @Scheduled(
            initialDelayString = "${app.chat.stale-task-reconcile-initial-delay-ms:60000}",
            fixedDelayString = "${app.chat.stale-task-reconcile-interval-ms:60000}"
    )
    @Transactional
    public void scheduledReconcile() {
        int reconciled = 0;
        LocalDateTime cutoff = staleCutoff();
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<AppGenerationTask> tasks = taskMapper.listStaleActiveBefore(cutoff, BATCH_SIZE);
            reconciled += reconcile(tasks, "stale-generation-scheduler");
            if (tasks.size() < BATCH_SIZE) {
                break;
            }
        }
        if (reconciled > 0) {
            log.warn("Reconciled {} stale generation tasks", reconciled);
        }
    }

    @Transactional
    public int reconcileConversation(long conversationId) {
        LocalDateTime cutoff = staleCutoff();
        String traceId = "stale-generation-conversation-cleanup";
        int tasks = reconcile(taskMapper.listStaleActiveByConversationId(conversationId, cutoff), traceId);
        int messages = messageMapper.markStaleActiveByConversationId(conversationId, cutoff, traceId);
        return tasks + Math.max(0, messages);
    }

    private int reconcile(List<AppGenerationTask> tasks, String traceId) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int reconciled = 0;
        for (AppGenerationTask task : tasks) {
            if (task == null || task.getId() == null) {
                continue;
            }
            int updated = taskMapper.updateStatus(
                    task.getId(),
                    "FAILED",
                    "STALE_GENERATION",
                    "generation worker exited without a terminal state",
                    traceId,
                    499
            );
            if (updated <= 0) {
                continue;
            }
            operationalStatsService.recordGenerationTaskStatus(task.getId(), "FAILED");
            if (task.getConversationId() != null && task.getClientMessageId() != null) {
                messageMapper.markStaleActiveByTask(
                        task.getConversationId(),
                        task.getClientMessageId(),
                        traceId
                );
            }
            reconciled++;
        }
        return reconciled;
    }

    private LocalDateTime staleCutoff() {
        long configuredWindow = (long) Math.max(1, chatProperties.getGenerationTimeoutSeconds())
                + Math.max(1, chatProperties.getMaxQueueWaitSeconds())
                + 60L;
        return LocalDateTime.now().minusSeconds(Math.max(180L, configuredWindow));
    }
}
