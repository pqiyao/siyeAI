package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ConversationMemoryAutoRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryAutoRefreshService.class);

    private final AppMessageMapper messageMapper;
    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryService memoryService;
    private final MemoryLlmProperties properties;
    private final ConversationMemoryRefreshLeaseManager leaseManager;
    private final AppFeatureSettingsService featureSettingsService;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor;

    public ConversationMemoryAutoRefreshService(
            AppMessageMapper messageMapper,
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryService memoryService,
            MemoryLlmProperties properties,
            ConversationMemoryRefreshLeaseManager leaseManager
    ) {
        this(messageMapper, memoryMapper, memoryService, properties, leaseManager, null);
    }

    @Autowired
    public ConversationMemoryAutoRefreshService(
            AppMessageMapper messageMapper,
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryService memoryService,
            MemoryLlmProperties properties,
            ConversationMemoryRefreshLeaseManager leaseManager,
            AppFeatureSettingsService featureSettingsService
    ) {
        this.messageMapper = messageMapper;
        this.memoryMapper = memoryMapper;
        this.memoryService = memoryService;
        this.properties = properties;
        this.leaseManager = leaseManager;
        this.featureSettingsService = featureSettingsService;
        int workerThreads = Math.max(1, properties.getAutoRefreshWorkerThreads());
        int queueCapacity = Math.max(1, properties.getAutoRefreshQueueCapacity());
        AtomicInteger threadIndex = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "conversation-memory-auto-refresh-" + threadIndex.incrementAndGet()
                    );
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void maybeTriggerAfterGenerationSuccess(long conversationId) {
        maybeTriggerAfterGenerationSuccess(conversationId, null);
    }

    public void maybeTriggerAfterGenerationSuccess(long conversationId, Long branchId) {
        if (conversationId <= 0 || !isLongTermMemoryEnabled()) {
            return;
        }
        String key = memoryKey(conversationId, branchId);
        submit(key, () -> runMaybeRefresh(conversationId, branchId));
    }

    public void triggerAfterHistoryChange(long conversationId, long branchId) {
        if (conversationId <= 0 || branchId <= 0) {
            return;
        }
        try {
            // History edits only invalidate generated entries. The next eligible
            // automatic or manual refresh performs the full rebuild.
            memoryService.invalidateConversationMemoryAfterHistoryChange(conversationId, branchId);
        } catch (RuntimeException ex) {
            log.error("conversation memory history invalidation failed conversationId={} branchId={}",
                    conversationId, branchId, ex);
            throw ex;
        }
    }

    private void runMaybeRefresh(long conversationId, Long branchId) {
        if (!isLongTermMemoryEnabled() || !shouldRefresh(conversationId, branchId)) {
            return;
        }
        runWithLease(
                conversationId,
                branchId,
                () -> memoryService.refreshConversationMemory(conversationId, branchId)
        );
    }

    private void runWithLease(long conversationId, Long branchId, Runnable action) {
        String key = memoryKey(conversationId, branchId);
        Duration ttl = Duration.ofSeconds(Math.max(30, properties.getAutoRefreshLeaseSeconds()));
        ConversationMemoryRefreshLeaseManager.LeaseAttempt attempt = leaseManager.tryAcquire(key, ttl);
        if (!attempt.acquired()) {
            if (attempt.status() == ConversationMemoryRefreshLeaseManager.Status.UNAVAILABLE) {
                log.debug("conversation memory background task skipped because Redis coordination is unavailable "
                                + "conversationId={} branchId={}",
                        conversationId, branchId);
            }
            return;
        }
        try (ConversationMemoryRefreshLeaseManager.Lease ignored = attempt.lease()) {
            action.run();
        }
    }

    private void submit(String key, Runnable action) {
        if (!inFlight.add(key)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    log.warn("conversation memory background task failed key={}", key, ex);
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RejectedExecutionException ex) {
            inFlight.remove(key);
            log.warn("conversation memory background queue full; task skipped key={}", key);
        } catch (RuntimeException ex) {
            inFlight.remove(key);
            log.warn("conversation memory background task submission failed key={}", key, ex);
        }
    }

    public boolean shouldRefresh(long conversationId) {
        return shouldRefresh(conversationId, null);
    }

    public boolean shouldRefresh(long conversationId, Long branchId) {
        if (!isLongTermMemoryEnabled()) {
            return false;
        }
        int visibleCount = hasBranch(branchId)
                ? messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId)
                : messageMapper.countMemorySourceByConversationId(conversationId);
        int minimumVisible = Math.max(1, properties.getAutoMinVisibleMessages());
        if (visibleCount < minimumVisible) {
            return false;
        }

        AppConversationMemory memory = hasBranch(branchId)
                ? memoryMapper.findByConversationBranchId(conversationId, branchId)
                : memoryMapper.findByConversationId(conversationId);
        int lastRefreshedMessageCount = memory == null ? 0 : memory.getLastRefreshedMessageCount();
        int minimumNewMessages = Math.max(1, properties.getAutoEveryMessages());
        if (visibleCount - lastRefreshedMessageCount < minimumNewMessages) {
            return false;
        }

        LocalDateTime updatedAt = memory == null ? null : memory.getUpdatedAt();
        if (updatedAt == null) {
            return true;
        }
        int cooldownMinutes = hasPendingHistoryRebuild(memory)
                ? properties.getHistoryRebuildCooldownMinutes()
                : properties.getAutoMinMinutesBetween();
        Duration minimumInterval = Duration.ofMinutes(Math.max(0, cooldownMinutes));
        return !updatedAt.plus(minimumInterval).isAfter(LocalDateTime.now());
    }

    private static boolean hasPendingHistoryRebuild(AppConversationMemory memory) {
        return memory != null
                && memory.getMemoryRevision() > 0
                && memory.getAppliedSourceRevision() == 0
                && memory.getLastSourceMessageId() == null
                && memory.getLastRefreshedMessageCount() == 0;
    }

    private boolean isLongTermMemoryEnabled() {
        return featureSettingsService == null || featureSettingsService.isLongTermMemoryEnabled();
    }

    private static String memoryKey(long conversationId, Long branchId) {
        return conversationId + ":" + (branchId == null ? 0 : branchId);
    }

    private static boolean hasBranch(Long branchId) {
        return branchId != null && branchId > 0;
    }

    @PreDestroy
    public void shutdown() {
        inFlight.clear();
        executor.shutdownNow();
    }
}
