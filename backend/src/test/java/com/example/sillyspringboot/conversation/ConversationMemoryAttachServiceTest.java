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
                new MemoryLlmProperties()
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
                new MemoryLlmProperties()
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
                new MemoryLlmProperties()
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
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setMaxConstantEntries(4);
        properties.setMaxEntryContentChars(80);
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper,
                entryMapper,
                syncService,
                properties
        );

        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                "jg_memory_conv_126_deadbeef00",
                2,
                ConversationMemoryWorldbookSyncService.SYNC_FAILED
        ));
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of(
                entry(10L, 200, "Long-term memory: 用户希望角色称呼他为哥哥。 Please use this memory naturally; do not repeat it mechanically."),
                entry(11L, 160, "用户和角色已经确认恋人关系。")
        ));

        String prompt = service.buildTailMemoryPromptIfAvailable(conversationId);

        assertThat(prompt).contains("Long-term memory for this conversation:");
        assertThat(prompt).contains("- 用户希望角色称呼他为哥哥。");
        assertThat(prompt).contains("- 用户和角色已经确认恋人关系。");
        assertThat(prompt).doesNotContain("Please use this memory naturally");
        verifyNoInteractions(syncService);
    }

    @Test
    void buildTailMemoryPromptIfAvailable_shouldReturnBlankWhenNoEnabledEntries() {
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        ConversationMemoryAttachService service = new ConversationMemoryAttachService(
                memoryMapper,
                entryMapper,
                syncService,
                new MemoryLlmProperties()
        );

        when(memoryMapper.findByConversationId(127L)).thenReturn(memory("", 0, ""));

        assertThat(service.buildTailMemoryPromptIfAvailable(127L)).isBlank();
        verifyNoInteractions(syncService);
    }

    private static AppConversationMemory memory(String worldName, int enabledEntryCount, String syncStatus) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setMemoryWorldName(worldName);
        memory.setEnabledEntryCount(enabledEntryCount);
        memory.setSyncStatus(syncStatus);
        return memory;
    }

    private static AppConversationMemoryEntry entry(Long id, int priority, String content) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setId(id);
        entry.setPriority(priority);
        entry.setContent(content);
        entry.setEnabled(true);
        return entry;
    }
}
