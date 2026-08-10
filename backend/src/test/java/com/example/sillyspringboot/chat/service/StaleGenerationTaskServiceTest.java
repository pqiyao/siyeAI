package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.ops.service.OperationalStatsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleGenerationTaskServiceTest {

    @Test
    void staleTaskReconciliationUpdatesTaskMessagesAndOperationalTrend() {
        AppGenerationTaskMapper taskMapper = mock(AppGenerationTaskMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        OperationalStatsService statsService = mock(OperationalStatsService.class);
        AppChatProperties properties = new AppChatProperties();
        properties.setGenerationTimeoutSeconds(10);
        properties.setMaxQueueWaitSeconds(5);

        AppGenerationTask task = new AppGenerationTask();
        task.setId(22L);
        task.setConversationId(33L);
        task.setClientMessageId("client-44");
        when(taskMapper.listStaleActiveBefore(any(LocalDateTime.class), anyInt())).thenReturn(List.of(task));
        when(taskMapper.updateStatus(
                22L,
                "FAILED",
                "STALE_GENERATION",
                "generation worker exited without a terminal state",
                "stale-generation-scheduler",
                499
        )).thenReturn(1);

        StaleGenerationTaskService service = new StaleGenerationTaskService(
                taskMapper,
                messageMapper,
                statsService,
                properties
        );

        service.scheduledReconcile();

        verify(statsService).recordGenerationTaskStatus(22L, "FAILED");
        verify(messageMapper).markStaleActiveByTask(33L, "client-44", "stale-generation-scheduler");
    }
}
