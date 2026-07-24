package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCapacityService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConversationMemoryManualRefreshTest {

    @Test
    void manualRefresh_shouldRejectWhenVisibleMessagesAreInsufficient() {
        Fixture fixture = fixture();
        when(fixture.messageMapper.countMemorySourceByConversationBranchId(10L, 20L)).thenReturn(5);

        assertThatThrownBy(() -> fixture.service.refreshConversationMemoryManual(10L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(fixture.memoryMapper, never()).tryAcquireManualRefresh(
                eq(10L), eq(20L), anyString(), org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void manualRefresh_shouldUsePersistentCooldownGuardAndReleaseLease() {
        Fixture fixture = fixture();
        when(fixture.messageMapper.countMemorySourceByConversationBranchId(10L, 20L)).thenReturn(6);
        when(fixture.memoryMapper.tryAcquireManualRefresh(
                eq(10L), eq(20L), anyString(), org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(1);
        when(fixture.llmService.tryStructuredMemoryExtract(eq(10L), eq(20L), eq(List.of())))
                .thenReturn(Optional.empty());
        when(fixture.llmService.tryLlmRollup(10L, 20L)).thenReturn(Optional.empty());
        when(fixture.memoryMapper.findByConversationBranchId(10L, 20L)).thenReturn(new AppConversationMemory());

        fixture.service.refreshConversationMemoryManual(10L, 20L);

        verify(fixture.memoryMapper).ensureForBranch(10L, 20L);
        verify(fixture.memoryMapper).releaseManualRefresh(eq(10L), eq(20L), anyString());
    }

    @Test
    void structuredRefresh_shouldStoreFullVisibleMessageCountInsteadOfWindowSize() {
        Fixture fixture = fixture();
        AppMessage message = new AppMessage();
        message.setId(99L);
        message.setRole("user");
        message.setStatus("SUCCESS");
        message.setContent("记住这件事");

        when(fixture.entryMapper.listAllByConversationBranchId(10L, 20L)).thenReturn(List.of());
        when(fixture.llmService.tryStructuredMemoryExtract(10L, 20L, List.of()))
                .thenReturn(Optional.of(new StructuredMemoryExtraction("摘要", List.of(), List.of())));
        when(fixture.messageMapper.listRecentMemorySourceByConversationBranchAsc(10L, 20L, 80))
                .thenReturn(List.of(message));
        when(fixture.messageMapper.countMemorySourceByConversationBranchId(10L, 20L)).thenReturn(220);
        when(fixture.entryMapper.countAllByConversationBranchId(10L, 20L)).thenReturn(0);
        when(fixture.entryMapper.countEnabledByConversationBranchId(10L, 20L)).thenReturn(0);
        when(fixture.memoryMapper.findByConversationBranchId(10L, 20L)).thenReturn(new AppConversationMemory());

        fixture.service.refreshConversationMemory(10L, 20L);

        verify(fixture.memoryMapper).upsertRefreshStateForBranch(
                10L, 20L, "", 0, null, 0, 0, 99L, 220, "SKIPPED", null);
    }

    @Test
    void structuredRefresh_shouldDiscardLlmResultWhenSourceMessagesChanged() {
        Fixture fixture = fixture();
        AppMessage before = message(99L, "旧内容");
        AppMessage after = message(99L, "修改后的内容");
        when(fixture.entryMapper.listAllByConversationBranchId(10L, 20L)).thenReturn(List.of());
        when(fixture.messageMapper.listRecentMemorySourceByConversationBranchAsc(10L, 20L, 80))
                .thenReturn(List.of(before))
                .thenReturn(List.of(after));
        when(fixture.messageMapper.countMemorySourceByConversationBranchId(10L, 20L)).thenReturn(1);
        when(fixture.llmService.tryStructuredMemoryExtract(10L, 20L, List.of()))
                .thenReturn(Optional.of(new StructuredMemoryExtraction("过期摘要", List.of(), List.of())));
        when(fixture.memoryMapper.findByConversationBranchId(10L, 20L)).thenReturn(new AppConversationMemory());

        fixture.service.refreshConversationMemory(10L, 20L);

        verify(fixture.memoryMapper, never()).upsertRefreshStateForBranch(
                eq(10L), eq(20L), anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.nullable(Long.class),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class)
        );
        verify(fixture.syncService, never()).syncWorldbook(10L, 20L);
    }

    private static Fixture fixture() {
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryLlmService llmService = mock(ConversationMemoryLlmService.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setFallbackToHeuristic(false);
        properties.setMaxMessages(80);
        ConversationMemorySanitizer sanitizer = new ConversationMemorySanitizer(properties);
        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                messageMapper,
                llmService,
                sanitizer,
                syncService,
                mock(ConversationMemoryCapacityService.class),
                properties
        );
        return new Fixture(service, memoryMapper, entryMapper, messageMapper, llmService, syncService);
    }

    private static AppMessage message(long id, String content) {
        AppMessage message = new AppMessage();
        message.setId(id);
        message.setRole("user");
        message.setStatus("SUCCESS");
        message.setContent(content);
        return message;
    }

    private record Fixture(
            AppConversationMemoryService service,
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppMessageMapper messageMapper,
            ConversationMemoryLlmService llmService,
            ConversationMemoryWorldbookSyncService syncService
    ) {
    }
}
