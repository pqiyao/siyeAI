package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookRetryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationMemoryWorldbookRetryServiceTest {

    @Test
    void disabledFeatureSkipsRetryScanAndWorldbookWrites() {
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        when(featureSettingsService.isLongTermMemoryEnabled()).thenReturn(false);
        ConversationMemoryWorldbookRetryService service = new ConversationMemoryWorldbookRetryService(
                memoryMapper,
                syncService,
                new MemoryLlmProperties(),
                featureSettingsService
        );

        service.retryDueWorldbooks();

        verifyNoInteractions(memoryMapper, syncService);
    }

    @Test
    void retryDueWorldbooks_claimsPersistentWorkBeforePublishing() {
        AppConversationMemoryMapper mapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        MemoryLlmProperties properties = new MemoryLlmProperties();
        AppConversationMemory memory = memory(42L, 7L);
        when(mapper.listWorldbookSyncRetryCandidates(any(), any(), anyInt())).thenReturn(List.of(memory));
        when(mapper.tryClaimWorldbookSync(anyLong(), anyLong(), any(), any())).thenReturn(1);
        ConversationMemoryWorldbookRetryService service =
                new ConversationMemoryWorldbookRetryService(mapper, syncService, properties);

        service.retryDueWorldbooks();

        verify(mapper).tryClaimWorldbookSync(anyLong(), anyLong(), any(), any());
        verify(syncService).syncWorldbook(42L, 7L);
    }

    @Test
    void retryDueWorldbooks_skipsPublishingWhenAnotherWorkerWonTheClaim() {
        AppConversationMemoryMapper mapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        MemoryLlmProperties properties = new MemoryLlmProperties();
        when(mapper.listWorldbookSyncRetryCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(memory(43L, 0L)));
        when(mapper.tryClaimWorldbookSync(anyLong(), anyLong(), any(), any())).thenReturn(0);
        ConversationMemoryWorldbookRetryService service =
                new ConversationMemoryWorldbookRetryService(mapper, syncService, properties);

        service.retryDueWorldbooks();

        verify(syncService, never()).syncWorldbook(anyLong(), any());
    }

    private static AppConversationMemory memory(long conversationId, long branchId) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setBranchId(branchId);
        memory.setEnabledEntryCount(1);
        memory.setSyncStatus(ConversationMemoryWorldbookSyncService.SYNC_FAILED);
        return memory;
    }
}
