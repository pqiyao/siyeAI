package com.example.sillyspringboot.admin.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminChatRuntimeMapperIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminChatRuntimeMapper mapper;

    @Test
    void stopsOnlyTheActiveAssistantMessageOwnedByTheTask() {
        long unique = Math.abs(System.nanoTime());
        long telegramUserId = 8_000_000_000L + unique % 1_000_000_000L;
        String targetClientMessageId = "cancel-target-" + unique;
        String otherClientMessageId = "cancel-other-" + unique;

        jdbc.update("INSERT INTO app_user (telegram_user_id) VALUES (?)", telegramUserId);
        long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramUserId
        );
        jdbc.update(
                "INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, ?, ?)",
                userId,
                1L,
                "runtime cancellation test"
        );
        long conversationId = jdbc.queryForObject(
                "SELECT MAX(id) FROM app_conversation WHERE user_id = ?",
                Long.class,
                userId
        );

        jdbc.update("""
                INSERT INTO app_generation_task (
                    user_id, conversation_id, request_type, client_message_id, status
                ) VALUES (?, ?, 'chat_stream', ?, 'GENERATING')
                """, userId, conversationId, targetClientMessageId);
        long taskId = jdbc.queryForObject(
                "SELECT id FROM app_generation_task WHERE conversation_id = ? AND client_message_id = ?",
                Long.class,
                conversationId,
                targetClientMessageId
        );

        insertMessage(userId, conversationId, "user", targetClientMessageId, "SUCCESS");
        long targetAssistantId = insertMessage(
                userId,
                conversationId,
                "assistant",
                targetClientMessageId,
                "GENERATING"
        );
        long otherAssistantId = insertMessage(
                userId,
                conversationId,
                "assistant",
                otherClientMessageId,
                "GENERATING"
        );

        assertThat(mapper.stopAssistantMessageForTask(taskId)).isEqualTo(1);
        assertThat(messageStatus(targetAssistantId)).isEqualTo("STOPPED");
        assertThat(messageErrorCode(targetAssistantId)).isEqualTo("ADMIN_CANCELLED");
        assertThat(messageStatus(otherAssistantId)).isEqualTo("GENERATING");
    }

    private long insertMessage(
            long userId,
            long conversationId,
            String role,
            String clientMessageId,
            String status
    ) {
        jdbc.update("""
                INSERT INTO app_message (
                    user_id, conversation_id, role, client_message_id, content, status
                ) VALUES (?, ?, ?, ?, NULL, ?)
                """, userId, conversationId, role, clientMessageId, status);
        return jdbc.queryForObject(
                """
                        SELECT MAX(id)
                        FROM app_message
                        WHERE conversation_id = ? AND role = ? AND client_message_id = ?
                        """,
                Long.class,
                conversationId,
                role,
                clientMessageId
        );
    }

    private String messageStatus(long messageId) {
        return jdbc.queryForObject("SELECT status FROM app_message WHERE id = ?", String.class, messageId);
    }

    private String messageErrorCode(long messageId) {
        return jdbc.queryForObject("SELECT error_code FROM app_message WHERE id = ?", String.class, messageId);
    }
}
