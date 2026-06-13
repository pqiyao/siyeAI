package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.service.ConversationMemoryLlmService;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConversationMemoryLlmServiceTest {

    @Test
    void structuredExtract_shouldReturnIdentityConstantEntryForUserCallName() {
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        when(messageMapper.listRecentByConversationAsc(eq(101L), any(Integer.class)))
                .thenReturn(List.of(user("以后叫我哥哥")));

        StClient stClient = mock(StClient.class);
        streamResponse(stClient, """
                {
                  "summaryPreview": "用户希望被称呼为哥哥。",
                  "entries": [
                    {
                      "entryKey": "identity_user_call_gege",
                      "memoryType": "identity",
                      "title": "用户称呼",
                      "content": "用户希望角色称呼他为哥哥。",
                      "keywords": ["哥哥", "称呼"],
                      "secondaryKeywords": [],
                      "priority": 200,
                      "position": "before_char",
                      "constantInjection": true,
                      "selective": false,
                      "enabled": true,
                      "confidence": 0.95,
                      "replaces": []
                    }
                  ],
                  "disableEntryKeys": []
                }
                """);

        ConversationMemoryLlmService service = new ConversationMemoryLlmService(
                stClient,
                messageMapper,
                properties()
        );

        Optional<StructuredMemoryExtraction> result = service.tryStructuredMemoryExtract(101L, List.of());

        assertThat(result).isPresent();
        StructuredMemoryExtraction extraction = result.get();
        assertThat(extraction.summaryPreview()).contains("哥哥");
        assertThat(extraction.disableEntryKeys()).isEmpty();
        assertThat(extraction.entries()).hasSize(1);

        ExtractedMemoryEntry entry = extraction.entries().get(0);
        assertThat(entry.entryKey()).isEqualTo("identity_user_call_gege");
        assertThat(entry.memoryType()).isEqualTo("identity");
        assertThat(entry.content()).isEqualTo("用户希望角色称呼他为哥哥。");
        assertThat(entry.keywords()).containsExactly("哥哥", "称呼");
        assertThat(entry.priority()).isEqualTo(200);
        assertThat(entry.position()).isEqualTo("before_char");
        assertThat(entry.constantInjection()).isTrue();
        assertThat(entry.enabled()).isTrue();
        assertThat(entry.confidence()).isEqualByComparingTo(new BigDecimal("0.95"));
    }

    @Test
    void structuredExtract_shouldNotCreateEntriesForFillerOnlyTranscript() {
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        when(messageMapper.listRecentByConversationAsc(eq(102L), any(Integer.class)))
                .thenReturn(List.of(user("哈哈"), assistant("嗯嗯")));

        StClient stClient = mock(StClient.class);
        streamResponse(stClient, """
                {
                  "summaryPreview": "",
                  "entries": [],
                  "disableEntryKeys": []
                }
                """);

        ConversationMemoryLlmService service = new ConversationMemoryLlmService(
                stClient,
                messageMapper,
                properties()
        );

        Optional<StructuredMemoryExtraction> result = service.tryStructuredMemoryExtract(102L, List.of());

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void streamResponse(StClient stClient, String text) {
        doAnswer(invocation -> {
            Consumer<ChatGenerateChunk> consumer = invocation.getArgument(1);
            consumer.accept(new ChatGenerateChunk(1L, "memory", 0, text, true, null, null));
            return null;
        }).when(stClient).streamChatCompletionsGenerate(any(), any(Consumer.class), any());
    }

    private static MemoryLlmProperties properties() {
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setLlmEnabled(true);
        properties.setMaxMessages(20);
        properties.setMaxTranscriptChars(4000);
        properties.setMaxEntryContentChars(300);
        return properties;
    }

    private static AppMessage user(String content) {
        return message("user", content, "SUCCESS");
    }

    private static AppMessage assistant(String content) {
        return message("assistant", content, "SUCCESS");
    }

    private static AppMessage message(String role, String content, String status) {
        AppMessage message = new AppMessage();
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        return message;
    }
}
