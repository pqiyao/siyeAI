package com.example.sillyspringboot.admin.mapper;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminDashboardGenerationOpsMapperTest {

    @Autowired
    private AdminDashboardMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Test
    void generationOperationsQueriesWorkOnAnEmptyMigratedSchema() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(14);
        assertNotNull(mapper.generationOpsSummary(startAt));
        assertNotNull(mapper.generationLatencyTrend(startAt));
        assertNotNull(mapper.generationProviderStats(startAt));
        assertNotNull(mapper.generationModelStats(startAt));
        assertNotNull(mapper.generationCharacterStats(startAt));
        assertNotNull(mapper.generationErrorStats(startAt));
        assertNotNull(mapper.generationRouteHealth(startAt));
    }

    @Test
    void totalMessagesUsesTheGreaterOfTheCumulativeCounterAndStoredRows() {
        long unique = Math.abs(System.nanoTime());
        long telegramUserId = 7_000_000_000L + unique % 1_000_000_000L;
        jdbc.update("INSERT INTO app_user (telegram_user_id) VALUES (?)", telegramUserId);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramUserId
        );
        jdbc.update(
                "INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, ?, ?)",
                userId,
                1L,
                "dashboard message counter test"
        );
        Long conversationId = jdbc.queryForObject(
                "SELECT MAX(id) FROM app_conversation WHERE user_id = ?",
                Long.class,
                userId
        );
        jdbc.update(
                "INSERT INTO app_message (user_id, conversation_id, role, content, status) VALUES (?, ?, 'user', 'hello', 'SUCCESS')",
                userId,
                conversationId
        );

        long storedRows = jdbc.queryForObject("SELECT COUNT(*) FROM app_message", Long.class);
        setMessageCounter(0L);
        assertThat(mapper.totalMessages()).isEqualTo(storedRows);

        setMessageCounter(storedRows + 7L);
        assertThat(mapper.totalMessages()).isEqualTo(storedRows + 7L);
    }

    @Test
    void successfulAiResponsesReadsOnlyItsDedicatedCounter() {
        jdbc.update(
                "UPDATE app_stats_counter SET counter_value = ?, updated_at = CURRENT_TIMESTAMP WHERE counter_key = 'successful_ai_responses'",
                17L
        );
        sqlSessionTemplate.clearCache();
        assertThat(mapper.successfulAiResponses()).isEqualTo(17L);
    }

    private void setMessageCounter(long value) {
        jdbc.update(
                "UPDATE app_stats_counter SET counter_value = ?, updated_at = CURRENT_TIMESTAMP WHERE counter_key = 'total_messages'",
                value
        );
        sqlSessionTemplate.clearCache();
    }
}
