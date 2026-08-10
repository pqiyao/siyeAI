package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCapacityService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConversationMemoryHistoryChangeTest {

    @Test
    void invalidateAfterHistoryChange_shouldWithdrawGeneratedEntriesAndResetRefreshCursor() {
        long conversationId = 10L;
        long branchId = 20L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryLlmService llmService = mock(ConversationMemoryLlmService.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setAutoMinVisibleMessages(6);
        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                messageMapper,
                llmService,
                new ConversationMemorySanitizer(properties),
                syncService,
                mock(ConversationMemoryCapacityService.class),
                properties
        );

        when(entryMapper.countAllByConversationBranchId(conversationId, branchId)).thenReturn(2);
        when(entryMapper.countEnabledByConversationBranchId(conversationId, branchId)).thenReturn(0);
        when(entryMapper.listEnabledByConversationBranchId(conversationId, branchId)).thenReturn(List.of());
        when(messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId)).thenReturn(8);

        boolean rebuildQueued = service.invalidateConversationMemoryAfterHistoryChange(conversationId, branchId);

        assertThat(rebuildQueued).isTrue();
        verify(memoryMapper).ensureForBranch(conversationId, branchId);
        verify(entryMapper).softDeleteGeneratedByConversationBranchId(conversationId, branchId);
        verify(memoryMapper).upsertRefreshStateForBranch(
                conversationId,
                branchId,
                "",
                0,
                null,
                2,
                0,
                null,
                0,
                "SKIPPED",
                null
        );
        verify(syncService, never()).syncWorldbook(conversationId, branchId);
    }
}
