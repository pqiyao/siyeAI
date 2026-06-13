package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCleanupService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class ConversationMemoryCleanupServiceTest {

    @Test
    void clearConversationMemory_shouldDeleteWorldbookEntriesAndMemoryRowForOnlyThatConversation() {
        long conversationId = 123L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService worldbookSyncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryCleanupService service = new ConversationMemoryCleanupService(
                memoryMapper,
                entryMapper,
                worldbookSyncService
        );

        service.clearConversationMemory(conversationId);

        InOrder inOrder = inOrder(worldbookSyncService, entryMapper, memoryMapper);
        inOrder.verify(worldbookSyncService).deleteWorldbook(conversationId);
        inOrder.verify(entryMapper).softDeleteByConversationId(conversationId);
        inOrder.verify(memoryMapper).deleteByConversationId(conversationId);
        verifyNoMoreInteractions(worldbookSyncService, entryMapper, memoryMapper);
    }
}
