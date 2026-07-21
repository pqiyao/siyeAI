package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAttachService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ConversationMemoryAttachServiceTest {

    @Test
    void attachMemoryWorldbookIfAvailable_shouldAppendSyncedConversationWorldbook() {
        long conversationId = 123L;
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper,
                entryMapper,
                syncService,
                properties()
        );

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
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper,
                entryMapper,
                syncService,
                properties()
        );

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
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper,
                entryMapper,
                syncService,
                properties()
        );

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
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper,
                entryMapper,
                syncService,
                properties()
        );

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
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper,
                entryMapper,
                syncService,
                properties(6, 48)
        );

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
}
