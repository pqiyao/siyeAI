package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshSnapshot;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryApplyService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCapacityService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConversationMemoryRevisionFenceOrchestrationTest {

    @Test
    void staleStructuredApplyDoesNotCallWorldbook() {
        long conversationId = 51L;
        long branchId = 52L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryLlmService llmService = mock(ConversationMemoryLlmService.class);
        ConversationMemoryWorldbookSyncService worldbook = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryCapacityService capacity = mock(ConversationMemoryCapacityService.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        ConversationMemoryApplyService applyService = mock(ConversationMemoryApplyService.class);
        MemoryLlmProperties properties = new MemoryLlmProperties();

        AppConversationBranch branch = new AppConversationBranch();
        branch.setId(branchId);
        branch.setConversationId(conversationId);
        branch.setMemorySourceRevision(3L);
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setBranchId(branchId);
        memory.setManualRevision(2L);
        memory.setMemoryRevision(4L);
        AppMessage message = new AppMessage();
        message.setId(90L);
        message.setRole("user");
        message.setStatus("SUCCESS");
        message.setContent("以后叫我阿曜");

        when(branchMapper.findByIdForConversation(conversationId, branchId)).thenReturn(branch);
        when(memoryMapper.findByConversationBranchId(conversationId, branchId)).thenReturn(memory);
        when(messageMapper.listRecentMemorySourceByConversationBranchAsc(
                conversationId, branchId, properties.getMaxMessages()
        )).thenReturn(List.of(message));
        when(messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId)).thenReturn(1);
        when(entryMapper.listAllByConversationBranchId(conversationId, branchId)).thenReturn(List.of());
        when(entryMapper.listManualDeletedByConversationBranchId(conversationId, branchId)).thenReturn(List.of());
        when(llmService.tryStructuredMemoryExtract(any(ConversationMemoryRefreshSnapshot.class)))
                .thenReturn(Optional.of(new StructuredMemoryExtraction("摘要", List.of(), List.of())));
        when(applyService.applyStructured(any(), any()))
                .thenReturn(ConversationMemoryApplyService.ApplyStatus.STALE);

        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                messageMapper,
                llmService,
                new ConversationMemorySanitizer(properties),
                worldbook,
                capacity,
                properties,
                branchMapper,
                applyService
        );

        service.refreshConversationMemory(conversationId, branchId);

        verify(worldbook, never()).syncWorldbook(conversationId, branchId);
    }
}
