package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppConversationArchiveMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.service.ConversationBranchService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import com.example.sillyspringboot.integration.sillytavern.OpenRouterGenerationSettingsService;
import com.example.sillyspringboot.ops.service.OperationalStatsService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAuditServiceTerminalGuardTest {

    @Test
    void lateSuccessCannotOverwriteAnExistingTerminalTaskOrItsStatistics() {
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppGenerationTaskMapper taskMapper = mock(AppGenerationTaskMapper.class);
        OperationalStatsService statsService = mock(OperationalStatsService.class);
        when(taskMapper.updateStatus(anyLong(), anyString(), any(), any(), any(), any())).thenReturn(0);

        ChatAuditService service = new ChatAuditService(
                mock(AppConversationMapper.class),
                messageMapper,
                taskMapper,
                mock(AppTokenService.class),
                mock(AppConversationArchiveMapper.class),
                mock(OpenRouterGenerationSettingsService.class),
                statsService,
                mock(ConversationMemoryAutoRefreshService.class),
                mock(ConversationBranchService.class)
        );

        service.onSuccess(11L, 22L, "late content", "trace-late");

        verify(statsService, never()).recordGenerationTaskStatus(anyLong(), anyString());
        verify(messageMapper, never()).updateStatusAndContent(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void successfulTerminalTaskIncrementsTheResponseCounterOnce() {
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppGenerationTaskMapper taskMapper = mock(AppGenerationTaskMapper.class);
        when(taskMapper.updateStatus(anyLong(), anyString(), any(), any(), any(), any())).thenReturn(1);

        ChatAuditService service = new ChatAuditService(
                mock(AppConversationMapper.class),
                messageMapper,
                taskMapper,
                mock(AppTokenService.class),
                mock(AppConversationArchiveMapper.class),
                mock(OpenRouterGenerationSettingsService.class),
                mock(OperationalStatsService.class),
                mock(ConversationMemoryAutoRefreshService.class),
                mock(ConversationBranchService.class)
        );

        service.onSuccess(11L, 22L, "success content", "trace-success");

        verify(messageMapper).incrementSuccessfulAiResponseCounter(22L);
    }
}
