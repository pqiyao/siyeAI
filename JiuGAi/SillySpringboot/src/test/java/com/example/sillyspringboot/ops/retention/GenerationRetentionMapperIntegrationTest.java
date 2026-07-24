package com.example.sillyspringboot.ops.retention;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GenerationRetentionMapperIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GenerationRetentionWriter writer;

    @Test
    void archivesExpiredDetailsWithoutDeletingRecentlyFinishedTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(30);
        LocalDateTime oldTime = now.minusDays(40);
        long userId = insertUser();
        long conversationId = insertConversation(userId);

        long expiredTaskId = insertTask(
                userId, conversationId, "retention-expired", oldTime, oldTime, oldTime, oldTime
        );
        long recentTaskId = insertTask(
                userId, conversationId, "retention-recent", oldTime, oldTime, now.minusHours(1), null
        );
        insertAttempt(expiredTaskId, conversationId, oldTime, "expired-provider");
        jdbc.update("""
                INSERT INTO app_generation_stat_event (
                    task_id, source_type, source_id, event_day, status, channel, model, created_at
                ) VALUES (?, 'TASK', ?, ?, 'SUCCESS', 'CHAT', 'retention-model', ?)
                """, expiredTaskId, expiredTaskId, oldTime.toLocalDate(), oldTime);

        assertThat(writer.archiveTaskBatch(cutoff, 100)).isEqualTo(1);

        assertThat(count("app_generation_task", "id", expiredTaskId)).isZero();
        assertThat(count("app_generation_task", "id", recentTaskId)).isEqualTo(1);
        assertThat(count("app_generation_attempt", "generation_task_id", expiredTaskId)).isZero();
        assertThat(count("app_generation_stat_event", "task_id", expiredTaskId)).isZero();
        assertThat(taskDailyCount(oldTime.toLocalDate(), "CHAT", "retention-model")).isEqualTo(1);
        assertThat(attemptDailyCount(oldTime.toLocalDate(), "expired-provider")).isEqualTo(1);

        assertThat(writer.archiveTaskBatch(cutoff, 100)).isZero();
        assertThat(taskDailyCount(oldTime.toLocalDate(), "CHAT", "retention-model")).isEqualTo(1);
    }

    @Test
    void archivesOrphanAttemptsAndLegacyEvents() {
        LocalDateTime oldTime = LocalDateTime.now().minusDays(40);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        long userId = insertUser();
        long conversationId = insertConversation(userId);
        insertAttempt(null, conversationId, oldTime, "orphan-provider");
        long sourceId = Math.abs(System.nanoTime());
        jdbc.update("""
                INSERT INTO app_generation_stat_event (
                    task_id, source_type, source_id, event_day, status, channel, model, created_at
                ) VALUES (NULL, 'RETENTION_TEST', ?, ?, 'FAILED', 'LEGACY', 'legacy-model', ?)
                """, sourceId, oldTime.toLocalDate(), oldTime);

        assertThat(writer.archiveOrphanAttemptBatch(cutoff, 100)).isEqualTo(1);
        assertThat(writer.archiveOrphanEventBatch(cutoff, 100)).isEqualTo(1);

        assertThat(attemptDailyCount(oldTime.toLocalDate(), "orphan-provider")).isEqualTo(1);
        assertThat(taskDailyCount(oldTime.toLocalDate(), "LEGACY", "legacy-model")).isEqualTo(1);
    }

    private long insertUser() {
        long telegramUserId = Math.abs(System.nanoTime());
        jdbc.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramUserId,
                "retention" + telegramUserId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramUserId
        );
    }

    private long insertConversation(long userId) {
        jdbc.update(
                "INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, 9101, 'Retention Test')",
                userId
        );
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM app_conversation WHERE user_id = ?",
                Long.class,
                userId
        );
    }

    private long insertTask(
            long userId,
            long conversationId,
            String clientMessageId,
            LocalDateTime queuedAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime deletedAt
    ) {
        jdbc.update("""
                INSERT INTO app_generation_task (
                    user_id, conversation_id, request_type, client_message_id, status,
                    queued_at, started_at, finished_at, channel, model, deleted_at
                ) VALUES (?, ?, 'generate', ?, 'SUCCESS', ?, ?, ?, 'CHAT', 'retention-model', ?)
                """, userId, conversationId, clientMessageId, queuedAt, startedAt, finishedAt, deletedAt);
        return jdbc.queryForObject(
                "SELECT id FROM app_generation_task WHERE conversation_id = ? AND client_message_id = ?",
                Long.class,
                conversationId,
                clientMessageId
        );
    }

    private void insertAttempt(
            Long generationTaskId,
            long conversationId,
            LocalDateTime time,
            String providerKey
    ) {
        jdbc.update("""
                INSERT INTO app_generation_attempt (
                    generation_task_id, conversation_id, character_id, attempt_no,
                    provider_key, route_key, model, byok, was_fallback,
                    started_at, finished_at, ttft_ms, duration_ms, http_status, status,
                    prompt_tokens, completion_tokens,
                    prompt_tokens_estimated, completion_tokens_estimated,
                    total_cost_usd, cost_estimated, cost_partial
                ) VALUES (?, ?, 9101, 1, ?, 'primary', 'retention-model', 0, 0,
                          ?, ?, 30, 120, 200, 'SUCCESS', 10, 20, 0, 0, 0.001, 0, 0)
                """, generationTaskId, conversationId, providerKey, time, time);
    }

    private int count(String table, String column, long id) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                id
        );
        return value == null ? 0 : value;
    }

    private long taskDailyCount(LocalDate day, String channel, String model) {
        Long value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(task_count), 0)
                FROM app_generation_task_daily_stat
                WHERE stat_day = ? AND channel = ? AND model = ?
                """, Long.class, day, channel, model);
        return value == null ? 0L : value;
    }

    private long attemptDailyCount(LocalDate day, String providerKey) {
        Long value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(attempt_count), 0)
                FROM app_generation_attempt_daily_stat
                WHERE stat_day = ? AND provider_key = ?
                """, Long.class, day, providerKey);
        return value == null ? 0L : value;
    }
}
