package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConversationMemoryCleanupService {

    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryEntryMapper entryMapper;
    private final AppConversationBranchMapper branchMapper;
    private final ConversationMemoryWorldbookSyncService worldbookSyncService;

    public ConversationMemoryCleanupService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppConversationBranchMapper branchMapper,
            ConversationMemoryWorldbookSyncService worldbookSyncService
    ) {
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.branchMapper = branchMapper;
        this.worldbookSyncService = worldbookSyncService;
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

    private static void addWorldName(Set<String> worldNames, String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            worldNames.add(worldName.trim());
        }
    }
}
