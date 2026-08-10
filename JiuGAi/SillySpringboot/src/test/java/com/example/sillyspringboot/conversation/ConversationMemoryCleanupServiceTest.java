package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.admin.service.ExternalCleanupTaskService;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCleanupService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConversationMemoryCleanupServiceTest {

    @Test
    void clearConversationMemory_shouldDeleteStoredLegacyAndEveryKnownBranchWorldbook() {
        long conversationId = 123L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        ConversationMemoryWorldbookSyncService worldbookSyncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryCleanupService service = service(memoryMapper, entryMapper, branchMapper, worldbookSyncService);

        when(memoryMapper.listByConversationId(conversationId)).thenReturn(List.of(
                memory(conversationId, 11L, "jg_memory_conv_123_b11_saved"),
                memory(conversationId, 22L, "jg_memory_conv_123_b22_current")
        ));
        // Includes branch 33 even if it has no memory row, for example an old or soft-deleted branch.
        when(branchMapper.listAllIdsByConversationId(conversationId)).thenReturn(List.of(11L, 22L, 33L));
        when(worldbookSyncService.resolveWorldName(conversationId, null))
                .thenReturn("jg_memory_conv_123_legacy");
        when(worldbookSyncService.resolveWorldName(conversationId, 11L))
                .thenReturn("jg_memory_conv_123_b11_current");
        when(worldbookSyncService.resolveWorldName(conversationId, 22L))
                .thenReturn("jg_memory_conv_123_b22_current");
        when(worldbookSyncService.resolveWorldName(conversationId, 33L))
                .thenReturn("jg_memory_conv_123_b33_current");

        service.clearConversationMemory(conversationId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> namesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(worldbookSyncService).deleteWorldbooksByName(org.mockito.ArgumentMatchers.eq(conversationId), namesCaptor.capture());
        assertThat(namesCaptor.getValue()).containsExactly(
                "jg_memory_conv_123_b11_saved",
                "jg_memory_conv_123_b22_current",
                "jg_memory_conv_123_legacy",
                "jg_memory_conv_123_b11_current",
                "jg_memory_conv_123_b33_current"
        );

        InOrder localDeleteOrder = inOrder(entryMapper, memoryMapper);
        localDeleteOrder.verify(entryMapper).softDeleteByConversationId(conversationId);
        localDeleteOrder.verify(memoryMapper).deleteByConversationId(conversationId);
    }

    @Test
    void clearConversationMemory_shouldKeepLocalEvidenceWhenOneWorldbookDeleteFails() {
        long conversationId = 124L;
        String firstWorld = "jg_memory_conv_124_b41_saved";
        String failedWorld = "jg_memory_conv_124_b42_saved";
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        ConversationMemoryWorldbookSyncService worldbookSyncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryCleanupService service = service(memoryMapper, entryMapper, branchMapper, worldbookSyncService);

        when(memoryMapper.listByConversationId(conversationId)).thenReturn(List.of(
                memory(conversationId, 41L, firstWorld),
                memory(conversationId, 42L, failedWorld)
        ));
        when(branchMapper.listAllIdsByConversationId(conversationId)).thenReturn(List.of());
        when(worldbookSyncService.resolveWorldName(conversationId, null))
                .thenReturn("jg_memory_conv_124_legacy");
        when(worldbookSyncService.resolveWorldName(conversationId, 41L)).thenReturn(firstWorld);
        when(worldbookSyncService.resolveWorldName(conversationId, 42L)).thenReturn(failedWorld);
        doThrow(new RuntimeException("st delete unavailable"))
                .when(worldbookSyncService).deleteWorldbooksByName(
                        org.mockito.ArgumentMatchers.eq(conversationId),
                        org.mockito.ArgumentMatchers.anyCollection()
                );

        assertThatThrownBy(() -> service.clearConversationMemory(conversationId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("st delete unavailable");

        verify(worldbookSyncService).deleteWorldbooksByName(
                org.mockito.ArgumentMatchers.eq(conversationId),
                org.mockito.ArgumentMatchers.anyCollection()
        );
        verify(entryMapper, never()).softDeleteByConversationId(conversationId);
        verify(memoryMapper, never()).deleteByConversationId(conversationId);
    }

    @Test
    void clearConversationMemory_shouldBeSafeWhenNoMemoryOrBranchRowsExist() {
        long conversationId = 125L;
        String legacyWorld = "jg_memory_conv_125_legacy";
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        ConversationMemoryWorldbookSyncService worldbookSyncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryCleanupService service = service(memoryMapper, entryMapper, branchMapper, worldbookSyncService);

        when(memoryMapper.listByConversationId(conversationId)).thenReturn(List.of());
        when(branchMapper.listAllIdsByConversationId(conversationId)).thenReturn(List.of());
        when(worldbookSyncService.resolveWorldName(conversationId, null)).thenReturn(legacyWorld);

        service.clearConversationMemory(conversationId);

        verify(worldbookSyncService).deleteWorldbooksByName(
                org.mockito.ArgumentMatchers.eq(conversationId),
                org.mockito.ArgumentMatchers.argThat(names -> names.size() == 1 && names.contains(legacyWorld))
        );
        verify(entryMapper).softDeleteByConversationId(conversationId);
        verify(memoryMapper).deleteByConversationId(conversationId);
    }

    @Test
    void clearBranchMemory_shouldPersistCleanupBeforeDeletingLocalMemory() {
        long conversationId = 126L;
        long branchId = 43L;
        long userId = 7L;
        String worldName = "jg_memory_conv_126_b43";
        String taskId = "cleanup-task-126-43";
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        ConversationMemoryWorldbookSyncService worldbookSyncService = mock(ConversationMemoryWorldbookSyncService.class);
        ExternalCleanupTaskService cleanupTaskService = mock(ExternalCleanupTaskService.class);
        ConversationMemoryCleanupService service = service(
                memoryMapper,
                entryMapper,
                branchMapper,
                worldbookSyncService,
                cleanupTaskService
        );

        when(worldbookSyncService.resolveWorldName(conversationId, branchId)).thenReturn(worldName);
        when(cleanupTaskService.enqueueMemoryWorldbookSetDeletion(userId, conversationId, worldName))
                .thenReturn(taskId);

        service.clearBranchMemory(conversationId, branchId, userId);

        InOrder order = inOrder(cleanupTaskService, entryMapper, memoryMapper);
        order.verify(cleanupTaskService).enqueueMemoryWorldbookSetDeletion(userId, conversationId, worldName);
        order.verify(entryMapper).deleteByConversationBranchId(conversationId, branchId);
        order.verify(memoryMapper).deleteByConversationBranchId(conversationId, branchId);
        order.verify(cleanupTaskService).processImmediately(List.of(taskId));
    }

    @Test
    void clearBranchMemory_shouldKeepCommittedDeletionSuccessfulWhenImmediateProcessorFails() {
        long conversationId = 127L;
        long branchId = 44L;
        long userId = 8L;
        String worldName = "jg_memory_conv_127_b44";
        String taskId = "cleanup-task-127-44";
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        ConversationMemoryWorldbookSyncService worldbookSyncService = mock(ConversationMemoryWorldbookSyncService.class);
        ExternalCleanupTaskService cleanupTaskService = mock(ExternalCleanupTaskService.class);
        ConversationMemoryCleanupService service = service(
                memoryMapper,
                entryMapper,
                branchMapper,
                worldbookSyncService,
                cleanupTaskService
        );

        when(worldbookSyncService.resolveWorldName(conversationId, branchId)).thenReturn(worldName);
        when(cleanupTaskService.enqueueMemoryWorldbookSetDeletion(userId, conversationId, worldName))
                .thenReturn(taskId);
        when(cleanupTaskService.processImmediately(List.of(taskId)))
                .thenThrow(new IllegalStateException("cleanup processor unavailable"));

        service.clearBranchMemory(conversationId, branchId, userId);

        verify(entryMapper).deleteByConversationBranchId(conversationId, branchId);
        verify(memoryMapper).deleteByConversationBranchId(conversationId, branchId);
        verify(cleanupTaskService).processImmediately(List.of(taskId));
    }

    private static ConversationMemoryCleanupService service(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppConversationBranchMapper branchMapper,
            ConversationMemoryWorldbookSyncService worldbookSyncService
    ) {
        return service(
                memoryMapper,
                entryMapper,
                branchMapper,
                worldbookSyncService,
                mock(ExternalCleanupTaskService.class)
        );
    }

    private static ConversationMemoryCleanupService service(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppConversationBranchMapper branchMapper,
            ConversationMemoryWorldbookSyncService worldbookSyncService,
            ExternalCleanupTaskService cleanupTaskService
    ) {
        return new ConversationMemoryCleanupService(
                memoryMapper,
                entryMapper,
                branchMapper,
                worldbookSyncService,
                cleanupTaskService
        );
    }

    private static AppConversationMemory memory(long conversationId, long branchId, String worldName) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setBranchId(branchId);
        memory.setMemoryWorldName(worldName);
        return memory;
    }
}
