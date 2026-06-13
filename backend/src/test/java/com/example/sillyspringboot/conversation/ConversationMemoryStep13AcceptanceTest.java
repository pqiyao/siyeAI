package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
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

class ConversationMemoryStep13AcceptanceTest {

    @Test
    void acceptance01_callNameMemory_shouldCreateHighPriorityConstantIdentityEntry() {
        long conversationId = 1301L;
        Harness h = newHarness(conversationId);
        when(h.entryMapper.countAllByConversationId(conversationId)).thenReturn(1);
        when(h.entryMapper.countEnabledByConversationId(conversationId)).thenReturn(1);
        when(h.memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                conversationId,
                "User wants the character to call him gege.",
                1,
                1,
                1,
                "jg_memory_conv_1301_callname",
                "SUCCESS",
                ""
        ));
        givenStructured(h, conversationId, extraction(
                "User wants the character to call him gege.",
                List.of(entry(
                        "identity_user_call_gege",
                        "identity",
                        "User call name",
                        "User wants the character to call him gege.",
                        List.of("gege", "call"),
                        200,
                        true,
                        true,
                        "0.95",
                        List.of()
                )),
                List.of()
        ));

        h.service.refreshConversationMemory(conversationId);

        AppConversationMemoryEntry saved = captureSavedEntry(h.entryMapper);
        assertThat(saved.getEntryKey()).isEqualTo("identity_user_call_gege");
        assertThat(saved.getMemoryType()).isEqualTo("identity");
        assertThat(saved.getPriority()).isEqualTo(200);
        assertThat(saved.isConstantInjection()).isTrue();
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void acceptance02_relationshipMemory_shouldCreateRelationshipEntry() {
        long conversationId = 1302L;
        Harness h = newHarness(conversationId);
        when(h.entryMapper.countAllByConversationId(conversationId)).thenReturn(1);
        when(h.entryMapper.countEnabledByConversationId(conversationId)).thenReturn(1);
        when(h.memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                conversationId,
                "User and character confirmed a romantic relationship.",
                1,
                1,
                1,
                "jg_memory_conv_1302_relationship",
                "SUCCESS",
                ""
        ));
        givenStructured(h, conversationId, extraction(
                "User and character confirmed a romantic relationship.",
                List.of(entry(
                        "relationship_romantic_confirmed",
                        "relationship",
                        "Romantic relationship",
                        "User and character have confirmed they are lovers.",
                        List.of("lovers", "relationship"),
                        200,
                        true,
                        true,
                        "0.92",
                        List.of()
                )),
                List.of()
        ));

        h.service.refreshConversationMemory(conversationId);

        AppConversationMemoryEntry saved = captureSavedEntry(h.entryMapper);
        assertThat(saved.getEntryKey()).isEqualTo("relationship_romantic_confirmed");
        assertThat(saved.getMemoryType()).isEqualTo("relationship");
        assertThat(saved.isConstantInjection()).isTrue();
        assertThat(saved.getContent()).contains("lovers");
    }

    @Test
    void acceptance03_promiseMemory_shouldStorePromiseButNotConstantInjection() {
        long conversationId = 1303L;
        Harness h = newHarness(conversationId);
        when(h.entryMapper.countAllByConversationId(conversationId)).thenReturn(1);
        when(h.entryMapper.countEnabledByConversationId(conversationId)).thenReturn(1);
        when(h.memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                conversationId,
                "User asked to be reminded to review tomorrow.",
                1,
                1,
                1,
                "jg_memory_conv_1303_promise",
                "SUCCESS",
                ""
        ));
        givenStructured(h, conversationId, extraction(
                "User asked to be reminded to review tomorrow.",
                List.of(entry(
                        "promise_remind_review_tomorrow",
                        "promise",
                        "Review reminder",
                        "User asked the character to remind him to review tomorrow.",
                        List.of("review", "reminder"),
                        160,
                        true,
                        true,
                        "0.90",
                        List.of()
                )),
                List.of()
        ));

        h.service.refreshConversationMemory(conversationId);

        AppConversationMemoryEntry saved = captureSavedEntry(h.entryMapper);
        assertThat(saved.getEntryKey()).isEqualTo("promise_remind_review_tomorrow");
        assertThat(saved.getMemoryType()).isEqualTo("promise");
        assertThat(saved.isConstantInjection()).isFalse();
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void acceptance04_fillerOnly_shouldNotCreateLongTermEntry() {
        long conversationId = 1304L;
        Harness h = newHarness(conversationId);
        givenStructured(h, conversationId, extraction("", List.of(), List.of()));
        when(h.memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                conversationId,
                "",
                0,
                0,
                0,
                "",
                ConversationMemoryWorldbookSyncService.SYNC_SKIPPED,
                ""
        ));

        h.service.refreshConversationMemory(conversationId);

        verify(h.entryMapper, never()).upsert(org.mockito.ArgumentMatchers.any(AppConversationMemoryEntry.class));
        verify(h.syncService).syncWorldbook(conversationId);
    }

    @Test
    void acceptance05_conflictOverride_shouldDisableOldCallNameAndEnableNewCallName() {
        long conversationId = 1305L;
        Harness h = newHarness(conversationId);
        when(h.entryMapper.listAllByConversationId(conversationId)).thenReturn(List.of(existingEntry(
                conversationId,
                "identity_user_call_gege",
                "identity",
                "User wants the character to call him gege."
        )));
        when(h.entryMapper.countAllByConversationId(conversationId)).thenReturn(2);
        when(h.entryMapper.countEnabledByConversationId(conversationId)).thenReturn(1);
        when(h.memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                conversationId,
                "User wants the character to call him Ayao instead.",
                1,
                2,
                1,
                "jg_memory_conv_1305_conflict",
                "SUCCESS",
                ""
        ));
        givenStructured(h, conversationId, extraction(
                "User wants the character to call him Ayao instead.",
                List.of(entry(
                        "identity_user_call_ayao",
                        "identity",
                        "User call name",
                        "User wants the character to call him Ayao.",
                        List.of("Ayao", "call"),
                        200,
                        true,
                        true,
                        "0.96",
                        List.of("identity_user_call_gege")
                )),
                List.of("identity_user_call_gege")
        ));

        h.service.refreshConversationMemory(conversationId);

        verify(h.entryMapper).disableByKey(conversationId, "identity_user_call_gege");
        AppConversationMemoryEntry saved = captureSavedEntry(h.entryMapper);
        assertThat(saved.getEntryKey()).isEqualTo("identity_user_call_ayao");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void acceptance06_multiUserIsolation_shouldQueryAndUpdateOnlyCurrentConversation() {
        long conversationId = 1306L;
        long otherConversationId = 2306L;
        Harness h = newHarness(conversationId);
        when(h.entryMapper.listAllByConversationId(conversationId)).thenReturn(List.of());
        when(h.entryMapper.countAllByConversationId(conversationId)).thenReturn(1);
        when(h.entryMapper.countEnabledByConversationId(conversationId)).thenReturn(1);
        when(h.memoryMapper.findByConversationId(conversationId)).thenReturn(memory(
                conversationId,
                "User A memory.",
                1,
                1,
                1,
                "jg_memory_conv_1306_user_a",
                "SUCCESS",
                ""
        ));
        givenStructured(h, conversationId, extraction(
                "User A memory.",
                List.of(entry(
                        "identity_user_a_call_gege",
                        "identity",
                        "User A call name",
                        "User A wants the character to call him gege.",
                        List.of("gege", "userA"),
                        200,
                        true,
                        true,
                        "0.94",
                        List.of()
                )),
                List.of()
        ));

        h.service.refreshConversationMemory(conversationId);

        verify(h.entryMapper).listAllByConversationId(conversationId);
        verify(h.entryMapper, never()).listAllByConversationId(otherConversationId);
        verify(h.syncService).syncWorldbook(conversationId);
        AppConversationMemoryEntry saved = captureSavedEntry(h.entryMapper);
        assertThat(saved.getConversationId()).isEqualTo(conversationId);
    }

    @Test
    void acceptance07_worldbookContent_shouldContainLongTermMemoryForStInjection() {
        ConversationMemorySanitizer sanitizer = new ConversationMemorySanitizer(properties());
        AppConversationMemoryEntry source = sanitizer.toEntity(1307L, entry(
                "identity_user_call_gege",
                "identity",
                "User call name",
                "User wants the character to call him gege.",
                List.of("gege", "call"),
                200,
                true,
                true,
                "0.95",
                List.of()
        ), 1L, 2L);

        assertThat(source).isNotNull();
        String injected = "Long-term memory: " + source.getContent()
                + " Please use this memory naturally; do not repeat it mechanically.";

        assertThat(injected).contains("Long-term memory: User wants the character to call him gege.");
    }

    private static Harness newHarness(long conversationId) {
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryLlmService llmService = mock(ConversationMemoryLlmService.class);
        ConversationMemoryWorldbookSyncService syncService = mock(ConversationMemoryWorldbookSyncService.class);
        MemoryLlmProperties properties = properties();
        AppConversationMemoryService service = new AppConversationMemoryService(
                memoryMapper,
                entryMapper,
                messageMapper,
                llmService,
                new ConversationMemorySanitizer(properties),
                syncService,
                properties
        );
        when(entryMapper.listAllByConversationId(conversationId)).thenReturn(List.of());
        when(messageMapper.listRecentByConversationAsc(eq(conversationId), eq(properties.getMaxMessages())))
                .thenReturn(List.of(
                        message(1L, "user", "User says something memorable.", "SUCCESS"),
                        message(2L, "assistant", "Assistant replies.", "SUCCESS")
                ));
        return new Harness(memoryMapper, entryMapper, messageMapper, llmService, syncService, service);
    }

    private static void givenStructured(Harness h, long conversationId, StructuredMemoryExtraction extraction) {
        when(h.llmService.tryStructuredMemoryExtract(eq(conversationId), anyList()))
                .thenReturn(Optional.of(extraction));
    }

    private static AppConversationMemoryEntry captureSavedEntry(AppConversationMemoryEntryMapper entryMapper) {
        ArgumentCaptor<AppConversationMemoryEntry> captor = ArgumentCaptor.forClass(AppConversationMemoryEntry.class);
        verify(entryMapper).upsert(captor.capture());
        return captor.getValue();
    }

    private static StructuredMemoryExtraction extraction(
            String summaryPreview,
            List<ExtractedMemoryEntry> entries,
            List<String> disableEntryKeys
    ) {
        return new StructuredMemoryExtraction(summaryPreview, entries, disableEntryKeys);
    }

    private static ExtractedMemoryEntry entry(
            String entryKey,
            String memoryType,
            String title,
            String content,
            List<String> keywords,
            int priority,
            boolean constantInjection,
            boolean enabled,
            String confidence,
            List<String> replaces
    ) {
        return new ExtractedMemoryEntry(
                entryKey,
                memoryType,
                title,
                content,
                keywords,
                List.of(),
                priority,
                "before_char",
                constantInjection,
                false,
                enabled,
                new BigDecimal(confidence),
                replaces
        );
    }

    private static AppConversationMemory memory(
            long conversationId,
            String summaryPreview,
            int factsCount,
            int entryCount,
            int enabledEntryCount,
            String memoryWorldName,
            String syncStatus,
            String syncError
    ) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(conversationId);
        memory.setSummaryPreview(summaryPreview);
        memory.setFactsCount(factsCount);
        memory.setEntryCount(entryCount);
        memory.setEnabledEntryCount(enabledEntryCount);
        memory.setMemoryWorldName(memoryWorldName);
        memory.setSyncStatus(syncStatus);
        memory.setSyncError(syncError);
        memory.setUpdatedAt(LocalDateTime.of(2026, 5, 24, 23, 0));
        return memory;
    }

    private static AppConversationMemoryEntry existingEntry(
            long conversationId,
            String entryKey,
            String memoryType,
            String content
    ) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setConversationId(conversationId);
        entry.setEntryKey(entryKey);
        entry.setMemoryType(memoryType);
        entry.setTitle(entryKey);
        entry.setContent(content);
        entry.setKeywordsJson("[]");
        entry.setSecondaryKeywordsJson("[]");
        entry.setPriority(200);
        entry.setPosition("before_char");
        entry.setConstantInjection(true);
        entry.setEnabled(true);
        entry.setConfidence(new BigDecimal("0.95"));
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

    private static MemoryLlmProperties properties() {
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setMaxMessages(80);
        properties.setMaxEntryContentChars(300);
        properties.setMaxKeywords(8);
        return properties;
    }

    private record Harness(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppMessageMapper messageMapper,
            ConversationMemoryLlmService llmService,
            ConversationMemoryWorldbookSyncService syncService,
            AppConversationMemoryService service
    ) {
    }
}
