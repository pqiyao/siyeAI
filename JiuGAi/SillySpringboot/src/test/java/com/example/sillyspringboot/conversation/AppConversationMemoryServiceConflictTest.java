package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshResult;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AppConversationMemoryServiceConflictTest {

    @Test
    void refreshConversationMemory_shouldDisableOldCallNameWhenNewCallNameReplacesIt() {
        long conversationId = 123L;

        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryLlmService llmService = mock(ConversationMemoryLlmService.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        MemoryLlmProperties properties = properties();
        ConversationMemorySanitizer sanitizer = new ConversationMemorySanitizer(properties);

        when(entryMapper.listAllByConversationId(conversationId))
                .thenReturn(List.of(existingGegeEntry(conversationId)));
        when(messageMapper.listRecentByConversationAsc(eq(conversationId), eq(properties.getMaxMessages())))
                .thenReturn(List.of(
                        message(10L, "user", "以后叫我哥哥。", "SUCCESS"),
                        message(11L, "assistant", "好呀。", "SUCCESS"),
                        message(12L, "user", "以后别叫哥哥了，叫我阿曜。", "SUCCESS")
                ));
        when(llmService.tryStructuredMemoryExtract(eq(conversationId), anyList()))
                .thenReturn(Optional.of(new StructuredMemoryExtraction(
                        "用户希望改称呼为阿曜。",
                        List.of(new ExtractedMemoryEntry(
                                "identity_user_call_ayao",
                                "identity",
                                "用户称呼",
                                "用户希望角色称呼他为阿曜。",
                                List.of("阿曜", "称呼"),
                                List.of(),
                                200,
                                "before_char",
                                true,
                                false,
                                true,
                                new BigDecimal("0.95"),
                                List.of("identity_user_call_gege")
                        )),
                        List.of("identity_user_call_gege")
                )));
        when(entryMapper.countAllByConversationId(conversationId)).thenReturn(2);
        when(entryMapper.countEnabledByConversationId(conversationId)).thenReturn(1);
        when(memoryMapper.findByConversationId(conversationId))
                .thenReturn(memoryState(conversationId));

        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                messageMapper,
                llmService,
                sanitizer,
                syncService,
                properties
        );

        ConversationMemoryRefreshResult result = service.refreshConversationMemory(conversationId);

        verify(entryMapper).disableByKey(conversationId, "identity_user_call_gege");
        ArgumentCaptor<AppConversationMemoryEntry> upserted =
                ArgumentCaptor.forClass(AppConversationMemoryEntry.class);
        verify(entryMapper).upsert(upserted.capture());
        AppConversationMemoryEntry newEntry = upserted.getValue();
        assertThat(newEntry.getEntryKey()).isEqualTo("identity_user_call_ayao");
        assertThat(newEntry.getMemoryType()).isEqualTo("identity");
        assertThat(newEntry.getContent()).isEqualTo("用户希望角色称呼他为阿曜。");
        assertThat(newEntry.getKeywordsJson()).contains("阿曜", "称呼");
        assertThat(newEntry.isConstantInjection()).isTrue();
        assertThat(newEntry.isEnabled()).isTrue();
        assertThat(newEntry.getSourceMessageFromId()).isEqualTo(10L);
        assertThat(newEntry.getSourceMessageToId()).isEqualTo(12L);

        verify(memoryMapper).upsertRefreshState(
                eq(conversationId),
                eq("用户希望改称呼为阿曜。"),
                eq(1),
                eq(null),
                eq(2),
                eq(1),
                eq(12L),
                eq(3),
                eq("PENDING"),
                eq(null)
        );
        verify(syncService).syncWorldbook(conversationId);
        verify(llmService, never()).tryLlmRollup(conversationId);

        assertThat(result.conversationId()).isEqualTo(conversationId);
        assertThat(result.summaryPreview()).isEqualTo("User wants the character to call him Ayao.");
        assertThat(result.factsCount()).isEqualTo(1);
        assertThat(result.entryCount()).isEqualTo(2);
        assertThat(result.enabledEntryCount()).isEqualTo(1);
        assertThat(result.memoryWorldName()).isEqualTo("jg_memory_conv_123_abcd1234ef");
        assertThat(result.syncStatus()).isEqualTo("SUCCESS");
        assertThat(result.syncError()).isEmpty();
    }

    @Test
    void refreshConversationMemory_shouldReturnFailedSyncStateWhenWorldbookSyncFails() {
        long conversationId = 124L;

        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryLlmService llmService = mock(ConversationMemoryLlmService.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        MemoryLlmProperties properties = properties();
        ConversationMemorySanitizer sanitizer = new ConversationMemorySanitizer(properties);

        when(entryMapper.listAllByConversationId(conversationId)).thenReturn(List.of());
        when(messageMapper.listRecentByConversationAsc(eq(conversationId), eq(properties.getMaxMessages())))
                .thenReturn(List.of(message(20L, "user", "remember this", "SUCCESS")));
        when(llmService.tryStructuredMemoryExtract(eq(conversationId), anyList()))
                .thenReturn(Optional.of(new StructuredMemoryExtraction(
                        "summary",
                        List.of(new ExtractedMemoryEntry(
                                "identity_user_call_gege",
                                "identity",
                                "title",
                                "User wants the character to call him gege.",
                                List.of("gege"),
                                List.of(),
                                200,
                                "before_char",
                                true,
                                false,
                                true,
                                new BigDecimal("0.95"),
                                List.of()
                        )),
                        List.of()
                )));
        when(entryMapper.countAllByConversationId(conversationId)).thenReturn(1);
        when(entryMapper.countEnabledByConversationId(conversationId)).thenReturn(1);
        when(memoryMapper.findByConversationId(conversationId))
                .thenReturn(failedMemoryState(conversationId));
        when(syncService.syncWorldbook(conversationId)).thenThrow(new RuntimeException("st offline"));

        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                messageMapper,
                llmService,
                sanitizer,
                syncService,
                properties
        );

        ConversationMemoryRefreshResult result = service.refreshConversationMemory(conversationId);

        assertThat(result.conversationId()).isEqualTo(conversationId);
        assertThat(result.syncStatus()).isEqualTo("FAILED");
        assertThat(result.syncError()).isEqualTo("st offline");
        assertThat(result.memoryWorldName()).isEqualTo("jg_memory_conv_124_deadbeef00");
    }

    private static AppConversationMemoryEntry existingGegeEntry(long conversationId) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setConversationId(conversationId);
        entry.setEntryKey("identity_user_call_gege");
        entry.setMemoryType("identity");
        entry.setContent("用户希望角色称呼他为哥哥。");
        entry.setKeywordsJson("[\"哥哥\",\"称呼\"]");
        entry.setPriority(200);
        entry.setPosition("before_char");
        entry.setConstantInjection(true);
        entry.setEnabled(true);
        return entry;
    }

    private static AppMessage message(Long id, String role, String content, String status) {
        AppMessage message = new AppMessage();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        return message;
    }

    private static AppConversationMemory memoryState(long conversationId) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setSummaryPreview("User wants the character to call him Ayao.");
        memory.setFactsCount(1);
        memory.setEntryCount(2);
        memory.setEnabledEntryCount(1);
        memory.setMemoryWorldName("jg_memory_conv_123_abcd1234ef");
        memory.setSyncStatus("SUCCESS");
        memory.setSyncError(null);
        memory.setUpdatedAt(LocalDateTime.of(2026, 5, 24, 21, 20));
        return memory;
    }

    private static AppConversationMemory failedMemoryState(long conversationId) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setSummaryPreview("summary");
        memory.setFactsCount(1);
        memory.setEntryCount(1);
        memory.setEnabledEntryCount(1);
        memory.setMemoryWorldName("jg_memory_conv_124_deadbeef00");
        memory.setSyncStatus("FAILED");
        memory.setSyncError("st offline");
        memory.setUpdatedAt(LocalDateTime.of(2026, 5, 24, 21, 21));
        return memory;
    }

    private static MemoryLlmProperties properties() {
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setMaxMessages(80);
        properties.setMaxEntryContentChars(300);
        return properties;
    }
}
