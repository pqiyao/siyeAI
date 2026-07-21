package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConversationMemorySanitizerTest {

    private final ConversationMemorySanitizer sanitizer = new ConversationMemorySanitizer(properties());

    @Test
    void toEntity_shouldKeepHighPriorityIdentityConstantMemory() {
        AppConversationMemoryEntry entity = sanitizer.toEntity(10L, entry(
                "identity_user_call_gege",
                "identity",
                "用户称呼",
                "用户希望角色称呼他为哥哥。",
                List.of("你", "哥哥", "称呼", "今天"),
                999,
                "after_char",
                true,
                true,
                new BigDecimal("1.20")
        ), 100L, 120L);

        assertThat(entity).isNotNull();
        assertThat(entity.getConversationId()).isEqualTo(10L);
        assertThat(entity.getEntryKey()).isEqualTo("identity_user_call_gege");
        assertThat(entity.getMemoryType()).isEqualTo("identity");
        assertThat(entity.getContent()).isEqualTo("用户希望角色称呼他为哥哥。");
        assertThat(entity.getKeywordsJson()).contains("哥哥", "称呼").doesNotContain("\"你\"", "\"今天\"");
        assertThat(entity.getPriority()).isEqualTo(200);
        assertThat(entity.getPosition()).isEqualTo("before_char");
        assertThat(entity.isConstantInjection()).isTrue();
        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.getConfidence()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(entity.getSourceMessageFromId()).isEqualTo(100L);
        assertThat(entity.getSourceMessageToId()).isEqualTo(120L);
    }

    @Test
    void toEntity_shouldDropEmptyAndTrivialContent() {
        assertThat(sanitizer.toEntity(10L, entry(
                "empty",
                "event",
                "",
                "   ",
                List.of("记忆"),
                100,
                "before_char",
                false,
                true,
                new BigDecimal("0.90")
        ), null, null)).isNull();

        assertThat(sanitizer.toEntity(10L, entry(
                "filler",
                "event",
                "寒暄",
                "哈哈哈",
                List.of("哈哈"),
                100,
                "before_char",
                false,
                true,
                new BigDecimal("0.90")
        ), null, null)).isNull();
    }

    @Test
    void toEntity_shouldDisableLowConfidenceAndDisallowConstantForNonCoreTypes() {
        AppConversationMemoryEntry entity = sanitizer.toEntity(10L, entry(
                "promise_low_confidence",
                "promise",
                "承诺",
                "用户希望明天复习英语。",
                List.of("复习", "英语"),
                500,
                "after_char",
                true,
                true,
                new BigDecimal("0.40")
        ), null, null);

        assertThat(entity).isNotNull();
        assertThat(entity.getMemoryType()).isEqualTo("promise");
        assertThat(entity.isConstantInjection()).isFalse();
        assertThat(entity.isEnabled()).isFalse();
        assertThat(entity.getPriority()).isEqualTo(200);
        assertThat(entity.getPosition()).isEqualTo("before_char");
    }

    @Test
    void toEntity_shouldTrimLongContentAndLimitKeywords() {
        String longContent = "用户喜欢海边约会。".repeat(50);
        AppConversationMemoryEntry entity = sanitizer.toEntity(10L, entry(
                "",
                "unknown_type",
                "很长内容",
                longContent,
                List.of("海边", "约会", "喜欢", "偏好", "亲密", "浪漫"),
                10,
                "after_char",
                false,
                true,
                null
        ), null, null);

        assertThat(entity).isNotNull();
        assertThat(entity.getMemoryType()).isEqualTo("event");
        assertThat(entity.getEntryKey()).startsWith("event_");
        assertThat(entity.getContent().length()).isLessThanOrEqualTo(120);
        assertThat(sanitizer.readKeywords(entity.getKeywordsJson())).hasSize(3);
        assertThat(entity.getPriority()).isEqualTo(40);
        assertThat(entity.isEnabled()).isTrue();
    }

    @Test
    void sanitizeDisableKeys_shouldNormalizeAndDeduplicateKeys() {
        assertThat(sanitizer.sanitizeDisableKeys(List.of(
                " Identity User Call Gege ",
                "identity_user_call_gege",
                "别叫哥哥了!",
                ""
        ))).containsExactly("identity_user_call_gege", "别叫哥哥了");
    }

    private static ExtractedMemoryEntry entry(
            String entryKey,
            String memoryType,
            String title,
            String content,
            List<String> keywords,
            int priority,
            String position,
            boolean constantInjection,
            boolean enabled,
            BigDecimal confidence
    ) {
        return new ExtractedMemoryEntry(
                entryKey,
                memoryType,
                title,
                content,
                keywords,
                List.of(),
                priority,
                position,
                constantInjection,
                false,
                enabled,
                confidence,
                List.of()
        );
    }

    private static MemoryLlmProperties properties() {
        MemoryLlmProperties properties = new MemoryLlmProperties();
        properties.setMaxEntryContentChars(120);
        properties.setMaxKeywords(3);
        return properties;
    }
}
