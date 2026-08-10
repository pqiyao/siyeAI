package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationMemoryWorldbookRetryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryWorldbookRetryService.class);

    private final AppConversationMemoryMapper memoryMapper;
    private final ConversationMemoryWorldbookSyncService syncService;
    private final MemoryLlmProperties properties;
    private final AppFeatureSettingsService featureSettingsService;

    public ConversationMemoryWorldbookRetryService(
            AppConversationMemoryMapper memoryMapper,
            ConversationMemoryWorldbookSyncService syncService,
            MemoryLlmProperties properties
    ) {
        this(memoryMapper, syncService, properties, null);
    }

    @Autowired
    public ConversationMemoryWorldbookRetryService(
            AppConversationMemoryMapper memoryMapper,
            ConversationMemoryWorldbookSyncService syncService,
            MemoryLlmProperties properties,
            AppFeatureSettingsService featureSettingsService
    ) {
        this.memoryMapper = memoryMapper;
        this.syncService = syncService;
        this.properties = properties;
        this.featureSettingsService = featureSettingsService;
    }

    @Scheduled(
            initialDelayString = "${app.memory.worldbook-sync-retry-initial-delay-ms:30000}",
            fixedDelayString = "${app.memory.worldbook-sync-retry-interval-ms:30000}"
    )
    public void retryDueWorldbooks() {
        if (!isLongTermMemoryEnabled() || !properties.isWorldbookSyncRetryEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime retryCutoff = now.minusSeconds(Math.max(1, properties.getWorldbookSyncRetryDelaySeconds()));
        LocalDateTime leaseCutoff = now.minusSeconds(Math.max(5, properties.getWorldbookSyncLeaseSeconds()));
        int batchSize = Math.max(1, Math.min(100, properties.getWorldbookSyncRetryBatchSize()));
        List<AppConversationMemory> candidates = memoryMapper.listWorldbookSyncRetryCandidates(
                retryCutoff,
                leaseCutoff,
                batchSize
        );
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (AppConversationMemory memory : candidates) {
            if (memory == null || memory.getConversationId() == null) {
                continue;
            }
            long conversationId = memory.getConversationId();
            long branchId = memory.getBranchId() == null ? 0L : memory.getBranchId();
            int claimed = memoryMapper.tryClaimWorldbookSync(
                    conversationId,
                    branchId,
                    retryCutoff,
                    leaseCutoff
            );
            if (claimed != 1) {
                continue;
            }
            try {
                syncService.syncWorldbook(conversationId, branchId > 0 ? branchId : null);
            } catch (Exception ex) {
                log.warn("memory worldbook retry failed conversationId={} branchId={} cause={}",
                        conversationId, branchId, rootCauseMessage(ex));
            }
        }
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor != null && cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor == null || cursor.getMessage() == null ? "" : cursor.getMessage();
    }

    private boolean isLongTermMemoryEnabled() {
        return featureSettingsService == null || featureSettingsService.isLongTermMemoryEnabled();
    }
}
