package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.mapper.AppConversationArchiveMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationIdempotencyMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCleanupService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
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
        H5TavernSessionService service = service(messageMapper, taskMapper, memoryCleanupService, snapshotService);

        when(taskMapper.countActiveByConversationId(conversationId)).thenReturn(0);
        when(messageMapper.countActiveByConversationId(conversationId)).thenReturn(0);

        service.restartFresh(conversationId);

        verify(taskMapper).softDeleteByConversationId(conversationId);
        verify(memoryCleanupService).clearConversationMemory(conversationId);
        verify(messageMapper).softDeleteByConversationId(conversationId, "conversation_wipe");
        verify(snapshotService).saveEmptySnapshot(conversationId);
    }

    private static H5TavernSessionService service(
            AppMessageMapper messageMapper,
            AppGenerationTaskMapper taskMapper,
            ConversationMemoryCleanupService memoryCleanupService,
            ChatSnapshotService snapshotService
    ) {
        return new H5TavernSessionService(
                messageMapper,
                taskMapper,
                memoryCleanupService,
                mock(AppConversationMapper.class),
                mock(AppConversationIdempotencyMapper.class),
                mock(AppConversationStBindingMapper.class),
                mock(AppConversationArchiveMapper.class),
                snapshotService
        );
    }
}
