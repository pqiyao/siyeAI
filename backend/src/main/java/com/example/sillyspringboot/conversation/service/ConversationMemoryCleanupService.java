package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.admin.service.ExternalCleanupTaskService;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConversationMemoryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryCleanupService.class);

    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryEntryMapper entryMapper;
    private final AppConversationBranchMapper branchMapper;
    private final ConversationMemoryWorldbookSyncService worldbookSyncService;
    private final ExternalCleanupTaskService externalCleanupTaskService;

    public ConversationMemoryCleanupService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppConversationBranchMapper branchMapper,
            ConversationMemoryWorldbookSyncService worldbookSyncService,
            ExternalCleanupTaskService externalCleanupTaskService
    ) {
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.branchMapper = branchMapper;
        this.worldbookSyncService = worldbookSyncService;
        this.externalCleanupTaskService = externalCleanupTaskService;
    }

    @Transactional
    public void clearConversationMemory(long conversationId) {
        List<AppConversationMemory> memories = memoryMapper.listByConversationId(conversationId);
        Set<Long> branchIds = new LinkedHashSet<>();
        Set<String> worldNames = new LinkedHashSet<>();
        branchIds.add(0L);

        if (memories != null) {
            for (AppConversationMemory memory : memories) {
                if (memory == null) {
                    continue;
                }
                Long branchId = memory.getBranchId();
                if (branchId != null && branchId > 0) {
                    branchIds.add(branchId);
                }
                addWorldName(worldNames, memory.getMemoryWorldName());
            }
        }

        List<Long> knownBranchIds = branchMapper.listAllIdsByConversationId(conversationId);
        if (knownBranchIds != null) {
            knownBranchIds.stream()
                    .filter(branchId -> branchId != null && branchId > 0)
                    .forEach(branchIds::add);
        }

        for (Long branchId : branchIds) {
            Long scopedBranchId = branchId != null && branchId > 0 ? branchId : null;
            addWorldName(worldNames, worldbookSyncService.resolveWorldName(conversationId, scopedBranchId));
        }

        worldbookSyncService.deleteWorldbooksByName(conversationId, worldNames);
        entryMapper.softDeleteByConversationId(conversationId);
        memoryMapper.deleteByConversationId(conversationId);
    }

    @Transactional
    public void clearBranchMemory(long conversationId, long branchId, long sourceUserId) {
        if (conversationId <= 0 || branchId <= 0 || sourceUserId <= 0) {
            return;
        }
        String baseWorldName = worldbookSyncService.resolveWorldName(conversationId, branchId);
        String cleanupTaskId = externalCleanupTaskService.enqueueMemoryWorldbookSetDeletion(
                sourceUserId,
                conversationId,
                baseWorldName
        );

        entryMapper.deleteByConversationBranchId(conversationId, branchId);
        memoryMapper.deleteByConversationBranchId(conversationId, branchId);
        processCleanupAfterCommit(cleanupTaskId);
    }

    private void processCleanupAfterCommit(String cleanupTaskId) {
        Runnable cleanup = () -> {
            try {
                externalCleanupTaskService.processImmediately(List.of(cleanupTaskId));
            } catch (RuntimeException ex) {
                log.warn(
                        "memory worldbook cleanup remains queued taskId={}: {}",
                        cleanupTaskId,
                        ex.getMessage()
                );
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanup.run();
            }
        });
    }

    private static void addWorldName(Set<String> worldNames, String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            worldNames.add(worldName.trim());
        }
    }
}
