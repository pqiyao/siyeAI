package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCapacityService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConversationMemoryDeleteTest {

    @Test
    void deleteMemoryEntry_shouldDeleteOnlyOwnedBranchEntryAndResyncWorldbook() {
        long conversationId = 701L;
        long branchId = 81L;
        long entryId = 901L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setId(entryId);
        entry.setConversationId(conversationId);
        entry.setBranchId(branchId);
        entry.setEnabled(true);

        when(entryMapper.findByIdForConversationBranch(entryId, conversationId, branchId)).thenReturn(entry);
        when(entryMapper.softDeleteManualById(entryId, conversationId, branchId)).thenReturn(1);
        when(entryMapper.listEnabledByConversationBranchId(conversationId, branchId)).thenReturn(List.of());
        when(entryMapper.listAllByConversationBranchId(conversationId, branchId)).thenReturn(List.of());
        when(entryMapper.countAllByConversationBranchId(conversationId, branchId)).thenReturn(0);
        when(entryMapper.countEnabledByConversationBranchId(conversationId, branchId)).thenReturn(0);

        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setBranchId(branchId);
        memory.setEntryCount(0);
        memory.setEnabledEntryCount(0);
        memory.setSyncStatus("SKIPPED");
        when(memoryMapper.findByConversationBranchId(conversationId, branchId)).thenReturn(memory);

        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                mock(AppMessageMapper.class),
                mock(ConversationMemoryLlmService.class),
                new ConversationMemorySanitizer(new MemoryLlmProperties()),
                syncService,
                mock(ConversationMemoryCapacityService.class),
                new MemoryLlmProperties()
        );

        Map<String, Object> result = service.deleteMemoryEntry(conversationId, branchId, entryId);

        verify(entryMapper).softDeleteManualById(entryId, conversationId, branchId);
        verify(memoryMapper).upsertRollupForBranch(conversationId, branchId, "", 0);
        verify(syncService).syncWorldbook(conversationId, branchId);
        assertThat(result)
                .containsEntry("entryCount", 0)
                .containsEntry("enabledEntryCount", 0)
                .containsEntry("syncStatus", "SKIPPED");
    }
}
