package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshSnapshot;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
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
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConversationMemoryRevisionFenceOrchestrationTest {

    @Test
    void incrementalRolloutUsesOverlapAndRelevantExistingMemory() {
        long conversationId = 61L;
        long branchId = 62L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryLlmService llmService = mock(ConversationMemoryLlmService.class);
        ConversationMemoryWorldbookSyncService worldbook = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryCapacityService capacity = mock(ConversationMemoryCapacityService.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        ConversationMemoryApplyService applyService = mock(ConversationMemoryApplyService.class);
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setIncrementalRolloutPercent(100);
        properties.setIncrementalOverlapMessages(3);

        AppConversationBranch branch = new AppConversationBranch();
        branch.setId(branchId);
        branch.setConversationId(conversationId);
        branch.setMemorySourceRevision(3L);
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setBranchId(branchId);
        memory.setManualRevision(2L);
        memory.setMemoryRevision(4L);
        memory.setLastSourceMessageId(100L);
        memory.setLastRefreshedMessageCount(20);

        List<AppMessage> rows = List.of(
                message(96L, "旧消息 96"),
                message(97L, "旧消息 97"),
                message(98L, "重叠消息 98"),
                message(99L, "重叠消息 99"),
                message(100L, "上次游标"),
                message(101L, "想再去海边"),
                message(102L, "可以一起看日落"),
                message(103L, "就这么约定了")
        );
        AppConversationMemoryEntry pinned = entry("manual_boundary", "boundary", "[\"花生\"]");
        pinned.setManualPinned(true);
        AppConversationMemoryEntry core = entry("identity_name", "identity", "[\"阿曜\"]");
        AppConversationMemoryEntry relevant = entry("event_beach", "event", "[\"海边\",\"日落\"]");
        AppConversationMemoryEntry unrelated = entry("event_library", "event", "[\"图书馆\"]");

        when(branchMapper.findByIdForConversation(conversationId, branchId)).thenReturn(branch);
        when(memoryMapper.findByConversationBranchId(conversationId, branchId)).thenReturn(memory);
        when(messageMapper.listRecentMemorySourceByConversationBranchAsc(
                conversationId, branchId, properties.getMaxMessages()
        )).thenReturn(rows);
        when(messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId)).thenReturn(23);
        when(entryMapper.listAllByConversationBranchId(conversationId, branchId))
                .thenReturn(List.of(pinned, core, relevant, unrelated));
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

        ArgumentCaptor<ConversationMemoryRefreshSnapshot> captor =
                ArgumentCaptor.forClass(ConversationMemoryRefreshSnapshot.class);
        verify(llmService).tryStructuredMemoryExtract(captor.capture());
        ConversationMemoryRefreshSnapshot snapshot = captor.getValue();
        assertThat(snapshot.extractionMode()).isEqualTo("INCREMENTAL");
        assertThat(snapshot.messageIds()).containsExactly(98L, 99L, 100L, 101L, 102L, 103L);
        assertThat(snapshot.existingEntries())
                .extracting(ConversationMemoryRefreshSnapshot.EntrySnapshot::entryKey)
                .containsExactly("manual_boundary", "identity_name", "event_beach");
    }

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

    private static AppMessage message(long id, String content) {
        AppMessage message = new AppMessage();
        message.setId(id);
        message.setRole("user");
        message.setStatus("SUCCESS");
        message.setContent(content);
        return message;
    }

    private static AppConversationMemoryEntry entry(String key, String type, String keywordsJson) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setEntryKey(key);
        entry.setMemoryType(type);
        entry.setContent(key);
        entry.setKeywordsJson(keywordsJson);
        entry.setEnabled(true);
        return entry;
    }
}
