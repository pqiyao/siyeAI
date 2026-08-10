package com.example.sillyspringboot.admin.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminOperationalHardDeleteIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminChatRuntimeController chatRuntimeController;

    @Autowired
    private AdminVisitorRiskController visitorRiskController;

    @Test
    void deletingRuntimeTaskDoesNotDeleteChatMessage() {
        long userId = insertUser("runtime-delete");
        long conversationId = insertConversation(userId, 9001L);
        jdbc.update("""
                INSERT INTO app_message (user_id, conversation_id, role, content, status)
                VALUES (?, ?, 'assistant', 'keep this chat message', 'SUCCESS')
                """, userId, conversationId);
        jdbc.update("""
                INSERT INTO app_generation_task (
                    user_id, conversation_id, request_type, client_message_id, status
                ) VALUES (?, ?, 'generate', 'runtime-delete-message', 'SUCCESS')
                """, userId, conversationId);
        long taskId = jdbc.queryForObject(
                "SELECT id FROM app_generation_task WHERE client_message_id = 'runtime-delete-message'",
                Long.class
        );
        jdbc.update("""
                INSERT INTO app_generation_stat_event (
                    task_id, source_type, source_id, event_day, status
                ) VALUES (?, 'TASK', ?, CURRENT_DATE, 'SUCCESS')
                """, taskId, taskId);
        jdbc.update("""
                INSERT INTO app_generation_attempt (
                    generation_task_id, conversation_id, character_id, attempt_no,
                    provider_key, route_key, byok, was_fallback,
                    started_at, finished_at, duration_ms, status,
                    prompt_tokens_estimated, completion_tokens_estimated,
                    cost_estimated, cost_partial
                ) VALUES (?, ?, 9001, 1, 'test', 'primary', 0, 0,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 10, 'SUCCESS', 0, 0, 0, 0)
                """, taskId, conversationId);

        var result = chatRuntimeController.hardDelete(
                new AdminChatRuntimeController.DeleteRequest(List.of(taskId))
        );

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(count("app_generation_task", "id", taskId)).isZero();
        assertThat(count("app_generation_attempt", "generation_task_id", taskId)).isZero();
        assertThat(count("app_generation_stat_event", "task_id", taskId)).isZero();
        assertThat(count("app_message", "conversation_id", conversationId)).isEqualTo(1);
    }

    @Test
    void deletingVisitorDeviceRemovesEventsButKeepsUserAndMessages() {
        long userId = insertUser("visitor-delete");
        long conversationId = insertConversation(userId, 9002L);
        jdbc.update("""
                INSERT INTO app_message (user_id, conversation_id, role, content, status)
                VALUES (?, ?, 'user', 'visitor chat remains', 'SUCCESS')
                """, userId, conversationId);
        String token = "device-delete-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO app_h5_visitor_device (
                    device_token, latest_user_id, trusted_user_id,
                    anonymous_chat_attempt_count
                ) VALUES (?, ?, ?, 7)
                """, token, userId, userId);
        long deviceId = jdbc.queryForObject(
                "SELECT id FROM app_h5_visitor_device WHERE device_token = ?",
                Long.class,
                token
        );
        jdbc.update("""
                INSERT INTO app_h5_security_event (device_id, event_type, user_id, detail)
                VALUES (?, 'RATE_LIMIT_HIT', ?, 'integration test')
                """, deviceId, userId);

        var result = visitorRiskController.hardDelete(
                new AdminVisitorRiskController.DeleteRequest(List.of(deviceId))
        );

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(count("app_h5_security_event", "device_id", deviceId)).isZero();
        assertThat(count("app_h5_visitor_device", "id", deviceId)).isZero();
        assertThat(count("app_user", "id", userId)).isEqualTo(1);
        assertThat(count("app_message", "conversation_id", conversationId)).isEqualTo(1);
    }

    private long insertUser(String prefix) {
        long telegramUserId = Math.abs(System.nanoTime());
        jdbc.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramUserId,
                prefix + telegramUserId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramUserId
        );
    }

    private long insertConversation(long userId, long characterId) {
        jdbc.update(
                "INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, ?, 'Delete Guard Test')",
                userId,
                characterId
        );
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM app_conversation WHERE user_id = ?",
                Long.class,
                userId
        );
    }

    private int count(String table, String column, long id) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                id
        );
        return value == null ? 0 : value;
    }
}
