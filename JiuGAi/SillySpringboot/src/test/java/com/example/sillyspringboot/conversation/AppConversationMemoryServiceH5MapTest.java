package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCapacityService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppConversationMemoryServiceH5MapTest {

    @Test
    void toH5MemoryMap_shouldExposeEntryAndSyncStatusForFrontend() {
        long conversationId = 321L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setSummaryPreview("User wants the character to call him gege.");
        memory.setFactsCount(1);
        memory.setEntryCount(8);
        memory.setEnabledEntryCount(6);
        memory.setMemoryWorldName("jg_memory_conv_321_abcd1234ef");
        memory.setLastSourceMessageId(88L);
        memory.setLastRefreshedMessageCount(24);
        memory.setLastManualRefreshAt(LocalDateTime.of(2026, 5, 24, 22, 29));
        memory.setLastSyncedAt(LocalDateTime.of(2026, 5, 24, 22, 30));
        memory.setSyncStatus("SUCCESS");
        memory.setSyncError(null);
        memory.setUpdatedAt(LocalDateTime.of(2026, 5, 24, 22, 31));
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory);

        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                mock(AppConversationMemoryEntryMapper.class),
                mock(AppMessageMapper.class),
                mock(ConversationMemoryLlmService.class),
                new ConversationMemorySanitizer(new MemoryLlmProperties()),
                mock(ConversationMemoryWorldbookSyncService.class),
                mock(ConversationMemoryCapacityService.class),
                new MemoryLlmProperties()
        );

        Map<String, Object> result = service.toH5MemoryMap(conversationId);

        assertThat(result)
                .containsEntry("summaryPreview", "User wants the character to call him gege.")
                .containsEntry("factsCount", 1)
                .containsEntry("entryCount", 8)
                .containsEntry("enabledEntryCount", 6)
                .containsEntry("memoryWorldName", "jg_memory_conv_321_abcd1234ef")
                .containsEntry("lastSourceMessageId", 88L)
                .containsEntry("lastRefreshedMessageCount", 24)
                .containsEntry("manualRefreshCooldownSeconds", 60)
                .containsEntry("syncStatus", "SUCCESS")
                .containsEntry("syncError", "");
        assertThat(result.get("lastSyncedAt")).isEqualTo(
                memory.getLastSyncedAt().atZone(ZoneId.systemDefault()).toInstant().toString()
        );
        assertThat(result.get("lastManualRefreshAt")).isEqualTo(
                memory.getLastManualRefreshAt().atZone(ZoneId.systemDefault()).toInstant().toString()
        );
        assertThat(result.get("updatedAt")).isEqualTo(
                memory.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toString()
        );
    }

    @Test
    void paginatedDetailUsesStableBranchScopedFilteringAndReturnsExactCounts() {
        long conversationId = 321L;
        long branchId = 17L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppConversationMemoryEntry enabledHigh = entry(1L, 100, true, false, null, 1);
        enabledHigh.setManualPinned(true);
        AppConversationMemoryEntry enabledLow = entry(2L, 20, true, false, null, 2);
        AppConversationMemoryEntry disabled = entry(3L, 90, false, true, null, 3);
        AppConversationMemoryEntry systemDisabled = entry(6L, 70, false, false, null, 6);
        AppConversationMemoryEntry archivedHigh = entry(4L, 80, false, false, "CAPACITY", 4);
        archivedHigh.setRetiredAt(LocalDateTime.of(2026, 5, 24, 22, 40));
        archivedHigh.setDeletedAt(LocalDateTime.of(2026, 5, 24, 22, 40));
        AppConversationMemoryEntry archivedLow = entry(5L, 10, false, false, "DUPLICATE", 5);
        archivedLow.setManualDisabled(true);
        archivedLow.setRetiredAt(LocalDateTime.of(2026, 5, 24, 22, 41));
        archivedLow.setDeletedAt(LocalDateTime.of(2026, 5, 24, 22, 41));
        AppConversationMemoryEntry historyChanged = entry(7L, 999, false, false, "HISTORY_CHANGED", 7);
        historyChanged.setRetiredAt(LocalDateTime.of(2026, 5, 24, 22, 42));
        historyChanged.setDeletedAt(LocalDateTime.of(2026, 5, 24, 22, 42));
        AppConversationMemoryEntry userDeleted = entry(8L, 999, false, false, "USER_DELETED", 8);
        userDeleted.setRetiredAt(LocalDateTime.of(2026, 5, 24, 22, 43));
        userDeleted.setDeletedAt(LocalDateTime.of(2026, 5, 24, 22, 43));
        when(entryMapper.listPanelByConversationBranchId(conversationId, branchId))
                .thenReturn(List.of(
                        historyChanged,
                        archivedLow,
                        disabled,
                        enabledLow,
                        userDeleted,
                        systemDisabled,
                        archivedHigh,
                        enabledHigh
                ));
        when(entryMapper.countAllByConversationBranchId(conversationId, branchId)).thenReturn(4);
        when(entryMapper.countEnabledByConversationBranchId(conversationId, branchId)).thenReturn(2);
        AppConversationMemoryService service = service(memoryMapper, entryMapper);

        Map<String, Object> firstPage = service.toH5MemoryDetailMap(
                conversationId,
                branchId,
                "all",
                1,
                2
        );
        Map<String, Object> secondPage = service.toH5MemoryDetailMap(
                conversationId,
                branchId,
                "all",
                2,
                2
        );
        Map<String, Object> archivedPage = service.toH5MemoryDetailMap(
                conversationId,
                branchId,
                "archived",
                1,
                20
        );
        Map<String, Object> disabledPage = service.toH5MemoryDetailMap(
                conversationId,
                branchId,
                "disabled",
                1,
                20
        );

        assertThat(firstPage)
                .containsEntry("entryFilter", "all")
                .containsEntry("entryPage", 1)
                .containsEntry("entryPageSize", 2)
                .containsEntry("entryTotal", 6)
                .containsEntry("entryHasMore", true)
                .containsEntry("archivedEntryCount", 2)
                .containsEntry("disabledEntryCount", 2)
                .containsEntry("entryCount", 4)
                .containsEntry("enabledEntryCount", 2);
        assertThat(entryIds(firstPage)).containsExactly(1L, 2L);
        assertThat(entryIds(secondPage)).containsExactly(3L, 6L);
        assertThat(archivedPage)
                .containsEntry("entryTotal", 2)
                .containsEntry("entryHasMore", false);
        assertThat(entryIds(archivedPage)).containsExactly(4L, 5L);
        assertThat(entryIds(disabledPage)).containsExactly(3L, 6L);
        assertThat(entryIds(firstPage)).doesNotContain(7L, 8L);
        assertThat(firstEntry(firstPage))
                .containsEntry("manualPinned", true)
                .containsEntry("archived", false)
                .containsEntry("retiredReason", "");
        assertThat(firstEntry(archivedPage))
                .containsEntry("archived", true)
                .containsEntry("retiredReason", "CAPACITY")
                .containsKey("retiredAt");
        verify(entryMapper, times(4)).listPanelByConversationBranchId(conversationId, branchId);
    }

    @Test
    void paginatedDetailNeverFallsBackToAnotherBranch() {
        long conversationId = 654L;
        long firstBranchId = 21L;
        long secondBranchId = 22L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        when(entryMapper.listPanelByConversationBranchId(conversationId, firstBranchId))
                .thenReturn(List.of(entry(11L, 10, true, false, null, 1)));
        when(entryMapper.listPanelByConversationBranchId(conversationId, secondBranchId))
                .thenReturn(List.of(entry(12L, 10, true, false, null, 1)));
        AppConversationMemoryService service = service(memoryMapper, entryMapper);

        Map<String, Object> first = service.toH5MemoryDetailMap(conversationId, firstBranchId, "all", 1, 20);
        Map<String, Object> second = service.toH5MemoryDetailMap(conversationId, secondBranchId, "all", 1, 20);

        assertThat(entryIds(first)).containsExactly(11L);
        assertThat(entryIds(second)).containsExactly(12L);
        verify(entryMapper).listPanelByConversationBranchId(conversationId, firstBranchId);
        verify(entryMapper).listPanelByConversationBranchId(conversationId, secondBranchId);
    }

    @Test
    void paginatedDetailRejectsInvalidFilterAndBoundsBeforeReadingEntries() {
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppConversationMemoryService service = service(mock(AppConversationMemoryMapper.class), entryMapper);

        assertThatThrownBy(() -> service.toH5MemoryDetailMap(1L, 2L, "deleted", 1, 20))
                .isInstanceOf(com.example.sillyspringboot.shared.error.BusinessException.class);
        assertThatThrownBy(() -> service.toH5MemoryDetailMap(1L, 2L, "all", 0, 20))
                .isInstanceOf(com.example.sillyspringboot.shared.error.BusinessException.class);
        assertThatThrownBy(() -> service.toH5MemoryDetailMap(1L, 2L, "all", 1, 51))
                .isInstanceOf(com.example.sillyspringboot.shared.error.BusinessException.class);
        assertThatThrownBy(() -> service.toH5MemoryDetailMap(1L, 0L, "all", 1, 20))
                .isInstanceOf(com.example.sillyspringboot.shared.error.BusinessException.class);
        verifyNoInteractions(entryMapper);
    }

    private static AppConversationMemoryService service(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper
    ) {
        MemoryLlmProperties properties = new MemoryLlmProperties();
        return new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                mock(AppMessageMapper.class),
                mock(ConversationMemoryLlmService.class),
                new ConversationMemorySanitizer(properties),
                mock(ConversationMemoryWorldbookSyncService.class),
                mock(ConversationMemoryCapacityService.class),
                properties
        );
    }

    private static AppConversationMemoryEntry entry(
            long id,
            int priority,
            boolean enabled,
            boolean manualDisabled,
            String retiredReason,
            int updatedMinute
    ) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setId(id);
        entry.setEntryKey("entry_" + id);
        entry.setMemoryType("EVENT");
        entry.setTitle("Memory " + id);
        entry.setContent("Content " + id);
        entry.setPriority(priority);
        entry.setEnabled(enabled);
        entry.setManualDisabled(manualDisabled);
        entry.setRetiredReason(retiredReason);
        entry.setUpdatedAt(LocalDateTime.of(2026, 5, 24, 22, updatedMinute));
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<String, Object> detail) {
        return (List<Map<String, Object>>) detail.get("entries");
    }

    private static List<Object> entryIds(Map<String, Object> detail) {
        return entries(detail).stream().map(row -> row.get("id")).toList();
    }

    private static Map<String, Object> firstEntry(Map<String, Object> detail) {
        return entries(detail).get(0);
    }
}
