package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import com.example.sillyspringboot.conversation.service.ConversationMemoryWorldbookSyncService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookSaveRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        when(entryMapper.listEnabledByConversationId(conversationId)).thenReturn(List.of(
                entry(conversationId, "identity_user_call_gege", "identity",
                        "User call name", "User wants the character to call him gege.", "[\"gege\",\"call\"]", 200, true),
                entry(conversationId, "relationship_close", "relationship",
                        "Close relationship", "User and character are close.", "[\"relationship\",\"close\"]", 160, true)
        ));
        when(entryMapper.countAllByConversationId(conversationId)).thenReturn(3);

        String worldName = service.syncWorldbook(conversationId);

        assertThat(worldName).matches("jg_memory_conv_123_[0-9a-f]{10}");
        ArgumentCaptor<StWorldbookSaveRequest> requestCaptor = ArgumentCaptor.forClass(StWorldbookSaveRequest.class);
        verify(stAdapter).saveWorldbook(requestCaptor.capture());
        StWorldbookSaveRequest request = requestCaptor.getValue();
        assertThat(request.name()).isEqualTo(worldName);

        Map<String, Object> data = request.data();
        assertThat(data.get("name")).isEqualTo(worldName);
        assertThat(data.get("extensions")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) data.get("entries");
        assertThat(entries).containsOnlyKeys("identity_user_call_gege", "relationship_close");

        @SuppressWarnings("unchecked")
        Map<String, Object> callName = (Map<String, Object>) entries.get("identity_user_call_gege");
        assertThat(callName.get("content")).asString().startsWith("Long-term memory: User wants the character to call him gege.");
        assertThat(callName.get("key")).isEqualTo(List.of("gege", "call"));
        assertThat(callName.get("position")).isEqualTo("before_char");
        assertThat(callName.get("constant")).isEqualTo(true);
        assertThat(callName.get("priority")).isEqualTo(200);

        verify(memoryMapper).updateSyncStatus(
                conversationId,
                worldName,
                3,
                2,
                ConversationMemoryWorldbookSyncService.SYNC_SUCCESS,
                null
        );
    }

    private static AppConversation conversation(long conversationId, long userId, long characterId) {
        AppConversation conversation = new AppConversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setCharacterId(characterId);
        return conversation;
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
}
