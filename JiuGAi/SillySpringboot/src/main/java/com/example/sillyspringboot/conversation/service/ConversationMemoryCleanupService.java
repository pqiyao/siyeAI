package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryCleanupService {

    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryEntryMapper entryMapper;
    private final ConversationMemoryWorldbookSyncService worldbookSyncService;

    public ConversationMemoryCleanupService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            ConversationMemoryWorldbookSyncService worldbookSyncService
    ) {
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.worldbookSyncService = worldbookSyncService;
    }

    public void clearConversationMemory(long conversationId) {
        worldbookSyncService.deleteWorldbook(conversationId);
        entryMapper.softDeleteByConversationId(conversationId);
        memoryMapper.deleteByConversationId(conversationId);
    }
}
