package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAttachService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ConversationMemoryAttachServiceTest {

    @Test
    void attachMemoryWorldbookIfAvailable_shouldAppendSyncedConversationWorldbook() {
        long conversationId = 123L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryAttachService service = service(memoryMapper, entryMapper, syncService, properties());

        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                "jg_memory_conv_123_abcd1234ef",
                6,
                ConversationMemoryWorldbookSyncService.SYNC_SUCCESS
        ));

        List<String> result = service.attachMemoryWorldbookIfAvailable(conversationId, List.of(
                "base_world",
                "jg_memory_conv_123_abcd1234ef"
        ));

        assertThat(result).containsExactly("base_world", "jg_memory_conv_123_abcd1234ef");
        verifyNoInteractions(syncService);
    }

    @Test
    void attachMemoryWorldbookIfAvailable_shouldSkipWhenMemoryIsNotSynced() {
        long conversationId = 124L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryAttachService service = service(memoryMapper, entryMapper, syncService, properties());

        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                "jg_memory_conv_124_deadbeef00",
                3,
                ConversationMemoryWorldbookSyncService.SYNC_FAILED
        ));

        List<String> result = service.attachMemoryWorldbookIfAvailable(conversationId, List.of("base_world"));

        assertThat(result).containsExactly("base_world");
        verifyNoInteractions(syncService);
    }

    @Test
    void attachMemoryWorldbookIfAvailable_shouldResolveWorldNameWhenStoredNameIsMissing() {
        long conversationId = 125L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryAttachService service = service(memoryMapper, entryMapper, syncService, properties());

        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                "",
                2,
                ConversationMemoryWorldbookSyncService.SYNC_SUCCESS
        ));
        when(syncService.resolveWorldName(conversationId)).thenReturn("jg_memory_conv_125_fallback00");

        List<String> result = service.attachMemoryWorldbookIfAvailable(conversationId, List.of("base_world"));

        assertThat(result).containsExactly("base_world", "jg_memory_conv_125_fallback00");
    }

    @Test
    void buildTailMemoryPromptIfAvailable_shouldUseEnabledEntriesEvenWhenSyncFailed() {
        long conversationId = 126L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryAttachService service = service(memoryMapper, entryMapper, syncService, properties());

        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                "jg_memory_conv_126_feedface00",
                2,
                ConversationMemoryWorldbookSyncService.SYNC_FAILED
        ));
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of(
                entry("用户希望角色称呼他为哥哥。", 200),
                entry("用户和角色已经确认恋人关系。", 180)
        ));

        String prompt = service.buildTailMemoryPromptIfAvailable(conversationId);

        assertThat(prompt)
                .contains("Long-term memory for this conversation:")
                .contains("- 用户希望角色称呼他为哥哥。")
                .contains("- 用户和角色已经确认恋人关系。")
                .contains("Keep the character's original personality and setting.");
    }

    @Test
    void buildTailMemoryPromptIfAvailable_shouldTrimLongEntriesToKeepTailPromptStable() {
        long conversationId = 127L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryAttachService service = service(
                memoryMapper, entryMapper, syncService, properties(6, 48));

        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                "jg_memory_conv_127_facefeed00",
                1,
                ConversationMemoryWorldbookSyncService.SYNC_SUCCESS
        ));
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of(
                entry("Long-term memory: " + "A".repeat(80) + " Please use this memory naturally; do not repeat it mechanically.", 200)
        ));

        String prompt = service.buildTailMemoryPromptIfAvailable(conversationId);

        assertThat(prompt)
                .contains("- " + "A".repeat(48))
                .doesNotContain("A".repeat(49));
    }

    @Test
    void missingSyncedWorldbook_shouldFailClosedAndUseDatabasePromptFallback() {
        long conversationId = 128L;
        String missingWorldName = "jg_memory_conv_128_missing000_r9";
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        StWorldbookCatalogService catalog = mock(StWorldbookCatalogService.class);
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper, entryMapper, syncService, properties(), catalog);

        AppConversationMemory memory = memory(
                missingWorldName,
                2,
                ConversationMemoryWorldbookSyncService.SYNC_SUCCESS
        );
        memory.setEntryCount(2);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory);
        when(catalog.resolveWorldNames(anyList())).thenReturn(
                new StWorldbookCatalogService.WorldbookResolution(List.of(), List.of(missingWorldName))
        );
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of(
                entry("用户明确不接受花生，属于安全边界。", 300),
                entry("用户希望被称呼为哥哥。", 200)
        ));

        List<String> attached = service.attachMemoryWorldbookIfAvailable(
                conversationId, List.of("base_world"));
        String fallback = service.buildTailMemoryPromptFallbackIfWorldbookUnavailable(conversationId);

        assertThat(attached).containsExactly("base_world");
        assertThat(fallback)
                .contains("用户明确不接受花生，属于安全边界。")
                .contains("用户希望被称呼为哥哥。");
        verify(memoryMapper).updateSyncStatusForBranch(
                eq(conversationId),
                eq(0L),
                eq(missingWorldName),
                eq(2),
                eq(2),
                eq(ConversationMemoryWorldbookSyncService.SYNC_FAILED),
                eq("memory worldbook missing from SillyTavern")
        );
    }

    @Test
    void disabledFeature_shouldPreserveOriginalWorldbooksAndSkipAllMemoryInjection() {
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        StWorldbookCatalogService catalog = mock(StWorldbookCatalogService.class);
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        when(featureSettingsService.isLongTermMemoryEnabled()).thenReturn(false);
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper,
                entryMapper,
                syncService,
                properties(),
                catalog,
                featureSettingsService
        );

        assertThat(service.attachMemoryWorldbookIfAvailable(129L, List.of("base_world")))
                .containsExactly("base_world");
        assertThat(service.hasSyncedMemoryWorldbook(129L)).isFalse();
        assertThat(service.buildTailMemoryPromptFallbackIfWorldbookUnavailable(129L)).isEmpty();
        assertThat(service.buildTailMemoryPromptIfAvailable(129L)).isEmpty();
        verifyNoInteractions(memoryMapper, entryMapper, syncService, catalog);
    }

    private static AppConversationMemory memory(String worldName, int enabledEntryCount, String syncStatus) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setMemoryWorldName(worldName);
        memory.setEnabledEntryCount(enabledEntryCount);
        memory.setSyncStatus(syncStatus);
        return memory;
    }

    private static AppConversationMemoryEntry entry(String content, int priority) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setEnabled(true);
        entry.setContent(content);
        entry.setPriority(priority);
        return entry;
    }

    private static MemoryLlmProperties properties() {
        return properties(6, 160);
    }

    private static MemoryLlmProperties properties(int maxConstantEntries, int maxEntryContentChars) {
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setMaxConstantEntries(maxConstantEntries);
        properties.setMaxEntryContentChars(maxEntryContentChars);
        return properties;
    }

    private static ConversationMemoryAttachService service(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            ConversationMemoryWorldbookSyncService syncService,
            MemoryLlmProperties properties
    ) {
        StWorldbookCatalogService catalog = mock(StWorldbookCatalogService.class);
        when(catalog.resolveWorldNames(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> {
                    List<String> names = invocation.getArgument(0);
                    return new StWorldbookCatalogService.WorldbookResolution(names, List.of());
                });
        return new ConversationMemoryAttachService(
                memoryMapper, entryMapper, syncService, properties, catalog);
    }
}
