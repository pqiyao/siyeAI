package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCapacityService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConversationMemoryCapacityIntegrationTest {

    @Test
    void structuredBranchRefreshEnforcesCapacityBeforeStateAndWorldbookSync() {
        long conversationId = 10L;
        long branchId = 20L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryLlmService llmService = mock(ConversationMemoryLlmService.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryCapacityService capacityService = mock(ConversationMemoryCapacityService.class);
        MemoryLlmProperties properties = new MemoryLlmProperties();
        AppConversationMemoryEntry kept = new AppConversationMemoryEntry();
        kept.setId(30L);
        kept.setEnabled(true);
        kept.setContent("保留事实");
        AppMessage source = new AppMessage();
        source.setId(40L);
        source.setRole("user");

        when(entryMapper.listAllByConversationBranchId(conversationId, branchId)).thenReturn(List.of(kept));
        when(entryMapper.listManualDeletedByConversationBranchId(conversationId, branchId)).thenReturn(List.of());
        when(llmService.tryStructuredMemoryExtract(eq(conversationId), eq(branchId), anyList()))
                .thenReturn(Optional.of(new StructuredMemoryExtraction("过期模型摘要", List.of(), List.of())));
        when(messageMapper.listRecentMemorySourceByConversationBranchAsc(
                conversationId, branchId, properties.getMaxMessages()
        )).thenReturn(List.of(source));
        when(messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId)).thenReturn(1);
        when(entryMapper.countAllByConversationBranchId(conversationId, branchId)).thenReturn(1);
        when(entryMapper.listEnabledByConversationBranchId(conversationId, branchId)).thenReturn(List.of(kept));

        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                messageMapper,
                llmService,
                new ConversationMemorySanitizer(properties),
                syncService,
                capacityService,
                properties
        );

        service.refreshConversationMemory(conversationId, branchId);

        InOrder order = inOrder(capacityService, syncService);
        order.verify(capacityService).enforceAfterRefresh(conversationId, branchId);
        order.verify(syncService).syncWorldbook(conversationId, branchId);
        verify(memoryMapper).upsertRefreshStateForBranch(
                conversationId,
                branchId,
                "保留事实",
                1,
                null,
                1,
                1,
                40L,
                1,
                "PENDING",
                null
        );
        verify(entryMapper).listEnabledByConversationBranchId(conversationId, branchId);
    }
}
