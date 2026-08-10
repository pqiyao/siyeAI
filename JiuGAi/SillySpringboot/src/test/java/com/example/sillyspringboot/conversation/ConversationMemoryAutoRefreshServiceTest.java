package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryRefreshLeaseManager;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ConversationMemoryAutoRefreshServiceTest {

    @Test
    void disabledFeature_shouldSkipAutomaticRefreshBeforeQueryingConversationState() {
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryService memoryService = mock(AppConversationMemoryService.class);
        ConversationMemoryRefreshLeaseManager leaseManager = mock(ConversationMemoryRefreshLeaseManager.class);
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        when(featureSettingsService.isLongTermMemoryEnabled()).thenReturn(false);
        ConversationMemoryAutoRefreshService service = new ConversationMemoryAutoRefreshService(
                messageMapper,
                memoryMapper,
                memoryService,
                defaults(),
                leaseManager,
                featureSettingsService
        );

        try {
            assertThat(service.shouldRefresh(122L)).isFalse();
            service.maybeTriggerAfterGenerationSuccess(122L);
            verifyNoInteractions(messageMapper, memoryMapper, memoryService, leaseManager);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void shouldRefresh_shouldRequireVisibleMessagesAndTwentyNewMessages() {
        long conversationId = 123L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryAutoRefreshService service = service(messageMapper, memoryMapper);

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(5);
        assertThat(service.shouldRefresh(conversationId)).isFalse();

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(19);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(null);
        assertThat(service.shouldRefresh(conversationId)).isFalse();

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(20);
        assertThat(service.shouldRefresh(conversationId)).isTrue();
    }

    @Test
    void shouldRefresh_shouldRequireSixtyMinutesSinceLastRefresh() {
        long conversationId = 124L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryAutoRefreshService service = service(messageMapper, memoryMapper);

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(46);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(26, LocalDateTime.now().minusMinutes(30)));
        assertThat(service.shouldRefresh(conversationId)).isFalse();

        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(26, LocalDateTime.now().minusMinutes(61)));
        assertThat(service.shouldRefresh(conversationId)).isTrue();
    }

    @Test
    void shouldRefresh_shouldUseTenMinuteCooldownForPendingHistoryRebuild() {
        long conversationId = 1241L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryAutoRefreshService service = service(messageMapper, memoryMapper);

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(46);
        AppConversationMemory memory = memory(0, LocalDateTime.now().minusMinutes(11));
        memory.setMemoryRevision(1);
        memory.setAppliedSourceRevision(0);
        memory.setLastSourceMessageId(null);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory);
        assertThat(service.shouldRefresh(conversationId)).isTrue();

        memory.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        assertThat(service.shouldRefresh(conversationId)).isFalse();
    }

    @Test
    void shouldRefresh_shouldSkipWhenLessThanTwentyNewMessagesSinceLastRefresh() {
        long conversationId = 125L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryAutoRefreshService service = service(messageMapper, memoryMapper);

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(45);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(26, LocalDateTime.now().minusMinutes(31)));

        assertThat(service.shouldRefresh(conversationId)).isFalse();
    }

    @Test
    void shouldRefresh_shouldUseConfiguredThresholds() {
        long conversationId = 126L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        MemoryLlmProperties properties = defaults();
        properties.setAutoMinVisibleMessages(3);
        properties.setAutoEveryMessages(4);
        properties.setAutoMinMinutesBetween(0);
        ConversationMemoryAutoRefreshService service = service(messageMapper, memoryMapper, properties);

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(4);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(null);

        assertThat(service.shouldRefresh(conversationId)).isTrue();
    }

    @Test
    void triggerAfterHistoryChange_shouldOnlyInvalidateAndDeferRebuild() {
        long conversationId = 127L;
        long branchId = 31L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryService memoryService = mock(AppConversationMemoryService.class);
        ConversationMemoryAutoRefreshService service = new ConversationMemoryAutoRefreshService(
                messageMapper,
                memoryMapper,
                memoryService,
                defaults(),
                acquiredLeaseManager()
        );
        when(memoryService.invalidateConversationMemoryAfterHistoryChange(conversationId, branchId)).thenReturn(true);

        try {
            service.triggerAfterHistoryChange(conversationId, branchId);

            verify(memoryService).invalidateConversationMemoryAfterHistoryChange(conversationId, branchId);
            verify(memoryService, never()).reconcileConversationMemoryAfterHistoryChange(anyLong(), anyLong(), any(Boolean.class));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void triggerAfterHistoryChange_shouldNotRebuildWhenTooShortEither() {
        long conversationId = 128L;
        long branchId = 32L;
        AppConversationMemoryService memoryService = mock(AppConversationMemoryService.class);
        ConversationMemoryAutoRefreshService service = new ConversationMemoryAutoRefreshService(
                mock(AppMessageMapper.class),
                mock(AppConversationMemoryMapper.class),
                memoryService,
                defaults(),
                acquiredLeaseManager()
        );
        when(memoryService.invalidateConversationMemoryAfterHistoryChange(conversationId, branchId)).thenReturn(false);

        try {
            service.triggerAfterHistoryChange(conversationId, branchId);

            verify(memoryService, never()).reconcileConversationMemoryAfterHistoryChange(anyLong(), anyLong(), any(Boolean.class));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void maybeTrigger_shouldReserveKeyBeforeQueueingAndDeduplicateSameBranch() throws Exception {
        long conversationId = 129L;
        long branchId = 33L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryService memoryService = mock(AppConversationMemoryService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(messageMapper.countMemorySourceByConversationBranchId(conversationId, branchId)).thenReturn(20);
        doAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(memoryService).refreshConversationMemory(conversationId, branchId);
        ConversationMemoryAutoRefreshService service = new ConversationMemoryAutoRefreshService(
                messageMapper,
                memoryMapper,
                memoryService,
                defaults(),
                acquiredLeaseManager()
        );

        try {
            service.maybeTriggerAfterGenerationSuccess(conversationId, branchId);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            service.maybeTriggerAfterGenerationSuccess(conversationId, branchId);
            release.countDown();

            verify(memoryService, timeout(2000).times(1))
                    .refreshConversationMemory(conversationId, branchId);
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    @Test
    void maybeTrigger_shouldBoundQueueAndReleaseRejectedReservation() throws Exception {
        MemoryLlmProperties properties = defaults();
        properties.setAutoRefreshWorkerThreads(1);
        properties.setAutoRefreshQueueCapacity(1);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryService memoryService = mock(AppConversationMemoryService.class);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(messageMapper.countMemorySourceByConversationId(anyLong())).thenReturn(20);
        doAnswer(invocation -> {
            long conversationId = invocation.getArgument(0);
            if (conversationId == 201L) {
                firstStarted.countDown();
                assertThat(releaseFirst.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(memoryService).refreshConversationMemory(anyLong(), any());
        ConversationMemoryAutoRefreshService service = new ConversationMemoryAutoRefreshService(
                messageMapper,
                memoryMapper,
                memoryService,
                properties,
                acquiredLeaseManager()
        );

        try {
            service.maybeTriggerAfterGenerationSuccess(201L);
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            service.maybeTriggerAfterGenerationSuccess(202L);
            service.maybeTriggerAfterGenerationSuccess(203L);
            verify(memoryService, never()).refreshConversationMemory(203L, null);

            releaseFirst.countDown();
            verify(memoryService, timeout(2000)).refreshConversationMemory(202L, null);
            service.maybeTriggerAfterGenerationSuccess(203L);
            verify(memoryService, timeout(2000)).refreshConversationMemory(203L, null);
        } finally {
            releaseFirst.countDown();
            service.shutdown();
        }
    }

    private static ConversationMemoryAutoRefreshService service(
            AppMessageMapper messageMapper,
            AppConversationMemoryMapper memoryMapper
    ) {
        return service(messageMapper, memoryMapper, defaults());
    }

    private static ConversationMemoryAutoRefreshService service(
            AppMessageMapper messageMapper,
            AppConversationMemoryMapper memoryMapper,
            MemoryLlmProperties properties
    ) {
        return new ConversationMemoryAutoRefreshService(
                messageMapper,
                memoryMapper,
                mock(AppConversationMemoryService.class),
                properties,
                acquiredLeaseManager()
        );
    }

    private static ConversationMemoryRefreshLeaseManager acquiredLeaseManager() {
        ConversationMemoryRefreshLeaseManager leaseManager = mock(ConversationMemoryRefreshLeaseManager.class);
        when(leaseManager.tryAcquire(anyString(), any(Duration.class))).thenReturn(
                new ConversationMemoryRefreshLeaseManager.LeaseAttempt(
                        ConversationMemoryRefreshLeaseManager.Status.ACQUIRED,
                        ConversationMemoryRefreshLeaseManager.Lease.NOOP,
                        false
                )
        );
        return leaseManager;
    }

    private static MemoryLlmProperties defaults() {
        return new MemoryLlmProperties();
    }

    private static AppConversationMemory memory(int lastRefreshedMessageCount, LocalDateTime updatedAt) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setLastRefreshedMessageCount(lastRefreshedMessageCount);
        memory.setUpdatedAt(updatedAt);
        return memory;
    }
}
