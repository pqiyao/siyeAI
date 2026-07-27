package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.chat.service.StaleGenerationTaskService;
import com.example.sillyspringboot.compat.h5.mapper.AppConversationArchiveMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationIdempotencyMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCleanupService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class H5TavernSessionServiceTest {

    @Test
    void restartFresh_shouldClearConversationMemory() {
        long conversationId = 123L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppGenerationTaskMapper taskMapper = mock(AppGenerationTaskMapper.class);
        ConversationMemoryCleanupService memoryCleanupService = mock(ConversationMemoryCleanupService.class);
        ChatSnapshotService snapshotService = mock(ChatSnapshotService.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        H5TavernSessionService service = service(
                messageMapper, taskMapper, memoryCleanupService, snapshotService, conversationMapper);

        when(conversationMapper.findByIdForUser(conversationId, 7L)).thenReturn(new AppConversation());
        when(taskMapper.countActiveByConversationId(conversationId)).thenReturn(0);
        when(messageMapper.countActiveByConversationId(conversationId)).thenReturn(0);

        service.restartFresh(7L, conversationId);

        verify(taskMapper).softDeleteByConversationId(conversationId);
        verify(memoryCleanupService).clearConversationMemory(conversationId);
        verify(messageMapper).softDeleteByConversationId(conversationId, "conversation_wipe");
        verify(snapshotService).saveEmptySnapshot(conversationId);
    }

    @Test
    void everyCrossUserWipeEntryPointIsRejectedBeforeConversationDataIsTouched() {
        long conversationId = 123L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppGenerationTaskMapper taskMapper = mock(AppGenerationTaskMapper.class);
        ConversationMemoryCleanupService memoryCleanupService = mock(ConversationMemoryCleanupService.class);
        ChatSnapshotService snapshotService = mock(ChatSnapshotService.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        H5TavernSessionService service = service(
                messageMapper, taskMapper, memoryCleanupService, snapshotService, conversationMapper);

        assertThrows(BusinessException.class, () -> service.restartFresh(8L, conversationId));
        assertThrows(BusinessException.class, () -> service.archiveHideAndWipe(8L, conversationId));
        assertThrows(BusinessException.class, () -> service.wipeConversationMessages(8L, conversationId));

        verify(taskMapper, never()).softDeleteByConversationId(conversationId);
        verify(memoryCleanupService, never()).clearConversationMemory(conversationId);
        verify(messageMapper, never()).softDeleteByConversationId(conversationId, "conversation_wipe");
        verify(snapshotService, never()).saveEmptySnapshot(conversationId);
    }

    private static H5TavernSessionService service(
            AppMessageMapper messageMapper,
            AppGenerationTaskMapper taskMapper,
            ConversationMemoryCleanupService memoryCleanupService,
            ChatSnapshotService snapshotService,
            AppConversationMapper conversationMapper
    ) {
        return new H5TavernSessionService(
                messageMapper,
                taskMapper,
                memoryCleanupService,
                conversationMapper,
                mock(AppConversationIdempotencyMapper.class),
                mock(AppConversationStBindingMapper.class),
                mock(AppConversationArchiveMapper.class),
                snapshotService,
                mock(StaleGenerationTaskService.class)
        );
    }
}
