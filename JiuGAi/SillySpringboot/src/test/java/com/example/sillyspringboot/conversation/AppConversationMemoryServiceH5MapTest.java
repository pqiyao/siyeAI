package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
                .containsEntry("syncStatus", "SUCCESS")
                .containsEntry("syncError", "");
        assertThat(result.get("lastSyncedAt")).isEqualTo(
                memory.getLastSyncedAt().atZone(ZoneId.systemDefault()).toInstant().toString()
        );
        assertThat(result.get("updatedAt")).isEqualTo(
                memory.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toString()
        );
    }
}
