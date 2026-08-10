package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookSaveRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookOptionDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ConversationMemoryWorldbookSyncServiceTest {

    @Test
    void syncWorldbook_shouldSaveEnabledEntriesWithConversationScopedHashedName() {
        long conversationId = 123L;
        StAdapter stAdapter = mock(StAdapter.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        MemoryLlmProperties properties = properties();
        ConversationMemoryWorldbookSyncService service = new ConversationMemoryWorldbookSyncService(
                stAdapter,
                conversationMapper,
                memoryMapper,
                entryMapper,
                new ConversationMemorySanitizer(properties),
                properties
        );

        when(conversationMapper.findById(conversationId)).thenReturn(conversation(conversationId, 77L, 88L));
        when(memoryMapper.findByConversationBranchId(conversationId, 0L)).thenReturn(memory(conversationId, 0L, 3L));
        allowRevisionStatusUpdate(memoryMapper);
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of(
                entry(conversationId, "identity_user_call_gege", "identity",
                        "User call name", "User wants the character to call him gege.", "[\"gege\",\"call\"]", 200, true),
                entry(conversationId, "relationship_close", "relationship",
                        "Close relationship", "User and character are close.", "[\"relationship\",\"close\"]", 160, true),
                entry(conversationId, "event_old_station", "event",
                        "Old station", "They promised to meet at the old station.", "[\"station\",\"promise\"]", 120, false)
        ));
        when(entryMapper.countAllByConversationId(conversationId)).thenReturn(3);

        String worldName = service.syncWorldbook(conversationId);

        assertThat(worldName).matches("jg_memory_conv_123_[0-9a-f]{10}_r3");
        ArgumentCaptor<StWorldbookSaveRequest> requestCaptor = ArgumentCaptor.forClass(StWorldbookSaveRequest.class);
        verify(stAdapter).saveWorldbook(requestCaptor.capture());
        StWorldbookSaveRequest request = requestCaptor.getValue();
        assertThat(request.name()).isEqualTo(worldName);

        Map<String, Object> data = request.data();
        assertThat(data.get("name")).isEqualTo(worldName);
        assertThat(data.get("extensions")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) data.get("entries");
        assertThat(entries).containsOnlyKeys("identity_user_call_gege", "relationship_close", "event_old_station");

        @SuppressWarnings("unchecked")
        Map<String, Object> callName = (Map<String, Object>) entries.get("identity_user_call_gege");
        assertThat(callName.get("content")).asString().startsWith("Long-term memory: User wants the character to call him gege.");
        assertThat(callName.get("key")).isEqualTo(List.of("gege", "call"));
        assertThat(callName.get("position")).isEqualTo("before_char");
        assertThat(callName.get("constant")).isEqualTo(true);
        assertThat(callName.get("vectorized")).isEqualTo(false);
        assertThat(callName.get("priority")).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> oldStation = (Map<String, Object>) entries.get("event_old_station");
        assertThat(oldStation.get("constant")).isEqualTo(false);
        assertThat(oldStation.get("vectorized")).isEqualTo(true);
        assertThat(oldStation.get("key")).isEqualTo(List.of("station", "promise"));

        verify(memoryMapper).updateWorldbookSyncStatusWithRevision(
                conversationId,
                0L,
                worldName,
                3,
                3,
                ConversationMemoryWorldbookSyncService.SYNC_SUCCESS,
                null,
                3L
        );
    }

    @Test
    void syncWorldbook_shouldKeepSkippedStateWhenEmptyWorldbookCleanupIsTemporarilyUnavailable() {
        long conversationId = 124L;
        StAdapter stAdapter = mock(StAdapter.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        MemoryLlmProperties properties = properties();
        ConversationMemoryWorldbookSyncService service = new ConversationMemoryWorldbookSyncService(
                stAdapter,
                conversationMapper,
                memoryMapper,
                entryMapper,
                new ConversationMemorySanitizer(properties),
                properties
        );

        when(conversationMapper.findById(conversationId)).thenReturn(conversation(conversationId, 77L, 88L));
        when(memoryMapper.findByConversationBranchId(conversationId, 0L)).thenReturn(memory(conversationId, 0L, 4L));
        allowRevisionStatusUpdate(memoryMapper);
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of());
        when(entryMapper.countAllByConversationId(conversationId)).thenReturn(2);
        doThrow(new RuntimeException("st delete unavailable")).when(stAdapter).deleteWorldbook(org.mockito.ArgumentMatchers.anyString());

        String worldName = service.syncWorldbook(conversationId);

        assertThat(worldName).endsWith("_r4");

        verify(memoryMapper).updateWorldbookSyncStatusWithRevision(
                org.mockito.ArgumentMatchers.eq(conversationId),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(ConversationMemoryWorldbookSyncService.SYNC_SKIPPED),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(4L)
        );
    }

    @Test
    void syncWorldbook_shouldPersistTheNumberActuallyWrittenAfterDefensiveLimit() {
        long conversationId = 127L;
        StAdapter stAdapter = mock(StAdapter.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        MemoryLlmProperties properties = properties();
        properties.setMaxEnabledEntries(2);
        ConversationMemoryWorldbookSyncService service = new ConversationMemoryWorldbookSyncService(
                stAdapter,
                conversationMapper,
                memoryMapper,
                entryMapper,
                new ConversationMemorySanitizer(properties),
                properties
        );
        when(conversationMapper.findById(conversationId)).thenReturn(conversation(conversationId, 77L, 88L));
        when(memoryMapper.findByConversationBranchId(conversationId, 0L)).thenReturn(memory(conversationId, 0L, 5L));
        allowRevisionStatusUpdate(memoryMapper);
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of(
                entry(conversationId, "one", "identity", "One", "One", "[]", 300, false),
                entry(conversationId, "two", "relationship", "Two", "Two", "[]", 200, false),
                entry(conversationId, "three", "event", "Three", "Three", "[]", 100, false)
        ));
        when(entryMapper.countAllByConversationId(conversationId)).thenReturn(3);

        service.syncWorldbook(conversationId);

        verify(memoryMapper).updateWorldbookSyncStatusWithRevision(
                org.mockito.ArgumentMatchers.eq(conversationId),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(ConversationMemoryWorldbookSyncService.SYNC_SUCCESS),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(5L)
        );
    }

    @Test
    void syncWorldbook_shouldDeleteItsVersionWhenRevisionWasSuperseded() {
        long conversationId = 128L;
        StAdapter stAdapter = mock(StAdapter.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        MemoryLlmProperties properties = properties();
        ConversationMemoryWorldbookSyncService service = new ConversationMemoryWorldbookSyncService(
                stAdapter, conversationMapper, memoryMapper, entryMapper,
                new ConversationMemorySanitizer(properties), properties
        );
        when(conversationMapper.findById(conversationId)).thenReturn(conversation(conversationId, 77L, 88L));
        when(memoryMapper.findByConversationBranchId(conversationId, 0L)).thenReturn(memory(conversationId, 0L, 7L));
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of(
                entry(conversationId, "identity_name", "identity", "Name", "Call me Ayao.", "[\"Ayao\"]", 200, true)
        ));
        when(entryMapper.countAllByConversationId(conversationId)).thenReturn(1);
        when(memoryMapper.updateWorldbookSyncStatusWithRevision(
                org.mockito.ArgumentMatchers.eq(conversationId),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(ConversationMemoryWorldbookSyncService.SYNC_SUCCESS),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(7L)
        )).thenReturn(0);

        String worldName = service.syncWorldbook(conversationId);

        assertThat(worldName).endsWith("_r7");
        verify(stAdapter).saveWorldbook(org.mockito.ArgumentMatchers.argThat(request -> worldName.equals(request.name())));
        verify(stAdapter).deleteWorldbook(worldName);
    }

    @Test
    void deleteWorldbookByName_shouldRejectWorldbookFromAnotherConversation() {
        long conversationId = 125L;
        StAdapter stAdapter = mock(StAdapter.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        MemoryLlmProperties properties = properties();
        ConversationMemoryWorldbookSyncService service = new ConversationMemoryWorldbookSyncService(
                stAdapter,
                conversationMapper,
                memoryMapper,
                entryMapper,
                new ConversationMemorySanitizer(properties),
                properties
        );

        assertThatThrownBy(() -> service.deleteWorldbookByName(
                conversationId,
                "jg_memory_conv_999_b1_deadbeef00"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");

        verifyNoInteractions(stAdapter);
    }

    @Test
    void deleteWorldbooksByName_shouldFailWhenExistingWorldbookDeleteIsNotConfirmed() {
        long conversationId = 126L;
        String deletedWorld = "jg_memory_conv_126_b1_aaaaaaaaaa";
        String failedWorld = "jg_memory_conv_126_b2_bbbbbbbbbb";
        String alreadyMissingWorld = "jg_memory_conv_126_b3_cccccccccc";
        StAdapter stAdapter = mock(StAdapter.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        MemoryLlmProperties properties = properties();
        ConversationMemoryWorldbookSyncService service = new ConversationMemoryWorldbookSyncService(
                stAdapter,
                conversationMapper,
                memoryMapper,
                entryMapper,
                new ConversationMemorySanitizer(properties),
                properties
        );

        when(stAdapter.listWorldbooks()).thenReturn(List.of(
                new StWorldbookOptionDto(deletedWorld, deletedWorld),
                new StWorldbookOptionDto(failedWorld, failedWorld),
                new StWorldbookOptionDto("unrelated_world", "unrelated_world")
        ));
        when(stAdapter.deleteWorldbook(deletedWorld)).thenReturn(true);
        when(stAdapter.deleteWorldbook(failedWorld)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteWorldbooksByName(
                conversationId,
                List.of(deletedWorld, failedWorld, alreadyMissingWorld)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(failedWorld);

        verify(stAdapter).listWorldbooks();
        verify(stAdapter).deleteWorldbook(deletedWorld);
        verify(stAdapter).deleteWorldbook(failedWorld);
        verifyNoMoreInteractions(stAdapter);
    }

    private static AppConversation conversation(long conversationId, long userId, long characterId) {
        AppConversation conversation = new AppConversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setCharacterId(characterId);
        return conversation;
    }

    private static AppConversationMemory memory(long conversationId, long branchId, long revision) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setBranchId(branchId);
        memory.setMemoryRevision(revision);
        return memory;
    }

    private static AppConversationMemoryEntry entry(
            long conversationId,
            String entryKey,
            String memoryType,
            String title,
            String content,
            String keywordsJson,
            int priority,
            boolean constant
    ) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setConversationId(conversationId);
        entry.setEntryKey(entryKey);
        entry.setMemoryType(memoryType);
        entry.setTitle(title);
        entry.setContent(content);
        entry.setKeywordsJson(keywordsJson);
        entry.setSecondaryKeywordsJson("[]");
        entry.setPriority(priority);
        entry.setPosition("before_char");
        entry.setConstantInjection(constant);
        entry.setSelective(false);
        entry.setEnabled(true);
        entry.setConfidence(new BigDecimal("0.95"));
        return entry;
    }

    private static MemoryLlmProperties properties() {
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setMaxEnabledEntries(80);
        properties.setMaxConstantEntries(12);
        properties.setMaxEntryContentChars(300);
        properties.setMaxKeywords(8);
        return properties;
    }

    private static void allowRevisionStatusUpdate(AppConversationMemoryMapper memoryMapper) {
        when(memoryMapper.updateWorldbookSyncStatusWithRevision(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.anyLong()
        )).thenReturn(1);
    }
}
