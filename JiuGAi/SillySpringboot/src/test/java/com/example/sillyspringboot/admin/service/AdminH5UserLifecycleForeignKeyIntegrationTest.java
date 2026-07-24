package com.example.sillyspringboot.admin.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminH5UserLifecycleForeignKeyIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(9_200_000L);

    @Autowired
    private AdminH5UserLifecycleService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deletesUserWithBranchesMemoryResetTokensAndPreferencesWhileAnonymizingSecurityEvents() {
        long userId = insertUser();
        String ownedUploadPath = UUID.randomUUID() + ".png";
        String ownedUploadUrl = "/uploads/h5/" + ownedUploadPath;
        jdbc.update(
                "UPDATE app_user SET photo_url = ? WHERE id = ?",
                ownedUploadUrl,
                userId
        );
        insertOwnedUpload(userId, ownedUploadUrl, ownedUploadPath);
        long characterId = insertPublicCharacter();
        long conversationId = insertConversation(userId, characterId);
        long branchId = insertBranch(userId, conversationId);
        jdbc.update("UPDATE app_conversation SET active_branch_id = ? WHERE id = ?", branchId, conversationId);
        insertMessage(userId, conversationId, branchId);
        insertMemory(conversationId, branchId);
        insertPasswordResetToken(userId);
        long deviceId = insertTrustedDevice(userId);
        String clientUid = insertClientUid(userId);
        String eventGroup = "delete-user-" + userId;
        jdbc.update(
                """
                INSERT INTO app_user_chat_preference
                    (user_id, character_id, bubble_json, reading_json, reply_format_json, revision)
                VALUES (?, ?, ?, NULL, NULL, 1)
                """,
                userId,
                characterId,
                "{\"fontSize\":28}"
        );
        jdbc.update(
                """
                INSERT INTO app_h5_security_event
                    (event_type, client_uid, user_id, ip_address, ua_hash, endpoint_group, detail)
                VALUES ('USER_CHANGED', ?, ?, '203.0.113.9', 'ua-hash', ?, 'private detail')
                """,
                clientUid,
                userId,
                eventGroup
        );
        jdbc.update(
                """
                INSERT INTO app_h5_security_event
                    (device_id, event_type, client_uid, user_id, ip_address, ua_hash, endpoint_group, detail)
                VALUES (?, 'DEVICE_BOUND', NULL, NULL, '203.0.113.10', 'device-ua', ?, 'device detail')
                """,
                deviceId,
                eventGroup
        );

        Map<String, Object> result = service.deleteUserById(userId);

        assertThat(result.get("deleted")).isEqualTo(true);
        assertThat(count("app_user", "id", userId)).isZero();
        assertThat(count("app_conversation", "user_id", userId)).isZero();
        assertThat(count("app_conversation_branch", "user_id", userId)).isZero();
        assertThat(count("app_conversation_memory_entry", "conversation_id", conversationId)).isZero();
        assertThat(count("app_password_reset_token", "user_id", userId)).isZero();
        assertThat(count("app_user_chat_preference", "user_id", userId)).isZero();
        assertThat(count("app_h5_visitor_device", "id", deviceId)).isZero();
        assertThat(count("app_h5_client_uid", "user_id", userId)).isZero();
        assertThat(count("app_h5_upload_asset", "owner_user_id", userId)).isZero();

        List<Map<String, Object>> cleanupTasks = jdbc.queryForList(
                """
                SELECT resource_type, status, attempt_count, source_user_id
                FROM app_external_cleanup_task
                WHERE source_user_id = ?
                """,
                userId
        );
        assertThat(cleanupTasks).singleElement().satisfies(task -> {
            assertThat(task.get("resource_type")).isEqualTo("LOCAL_UPLOAD");
            assertThat(task.get("status")).isEqualTo("COMPLETED");
            assertThat(((Number) task.get("attempt_count")).intValue()).isEqualTo(1);
            assertThat(((Number) task.get("source_user_id")).longValue()).isEqualTo(userId);
        });

        List<Map<String, Object>> securityEvents = jdbc.queryForList(
                """
                SELECT device_id, client_uid, user_id, ip_address, ua_hash, detail, event_type, endpoint_group
                FROM app_h5_security_event
                WHERE endpoint_group = ?
                ORDER BY id
                """,
                eventGroup
        );
        assertThat(securityEvents).hasSize(2);
        assertThat(securityEvents).extracting(row -> row.get("event_type"))
                .containsExactly("USER_CHANGED", "DEVICE_BOUND");
        for (Map<String, Object> securityEvent : securityEvents) {
            assertThat(securityEvent).containsEntry("endpoint_group", eventGroup);
            assertThat(securityEvent.get("device_id")).isNull();
            assertThat(securityEvent.get("client_uid")).isNull();
            assertThat(securityEvent.get("user_id")).isNull();
            assertThat(securityEvent.get("ip_address")).isNull();
            assertThat(securityEvent.get("ua_hash")).isNull();
            assertThat(securityEvent.get("detail")).isNull();
        }
    }

    @Test
    void editableUrlsCannotGrantDeletionRightsOverAnotherUsersUpload() {
        long victimUserId = insertUser();
        long deletingUserId = insertUser();
        String victimPath = UUID.randomUUID() + ".png";
        String victimUrl = "/uploads/h5/" + victimPath;
        insertOwnedUpload(victimUserId, victimUrl, victimPath);
        jdbc.update("UPDATE app_user SET photo_url = ? WHERE id = ?", victimUrl, deletingUserId);
        jdbc.update(
                "INSERT INTO app_character (owner_user_id, name, st_avatar_url, avatar_url) VALUES (?, ?, '', ?)",
                deletingUserId,
                "Referenced victim upload",
                victimUrl
        );

        Map<String, Object> result = service.deleteUserById(deletingUserId);

        assertThat(result.get("deleted")).isEqualTo(true);
        assertThat(count("app_user", "id", deletingUserId)).isZero();
        assertThat(count("app_user", "id", victimUserId)).isEqualTo(1);
        assertThat(count("app_h5_upload_asset", "owner_user_id", victimUserId)).isEqualTo(1);
        Integer cleanupCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_external_cleanup_task WHERE source_user_id = ? AND resource_type = 'LOCAL_UPLOAD'",
                Integer.class,
                deletingUserId
        );
        assertThat(cleanupCount).isZero();
    }

    private long insertUser() {
        long telegramId = IDS.incrementAndGet();
        jdbc.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramId,
                "delete-test-" + telegramId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramId
        );
    }

    private void insertOwnedUpload(long ownerUserId, String assetUrl, String relativePath) {
        jdbc.update(
                "INSERT INTO app_h5_upload_asset (asset_url, relative_path, owner_user_id) VALUES (?, ?, ?)",
                assetUrl,
                relativePath,
                ownerUserId
        );
    }

    private long insertPublicCharacter() {
        String avatar = "delete-test-" + IDS.incrementAndGet() + ".png";
        jdbc.update(
                "INSERT INTO app_character (st_avatar_url, name) VALUES (?, 'Delete Test Character')",
                avatar
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_character WHERE st_avatar_url = ?",
                Long.class,
                avatar
        );
    }

    private long insertConversation(long userId, long characterId) {
        jdbc.update(
                "INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, ?, 'Delete Test')",
                userId,
                characterId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_conversation WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                userId
        );
    }

    private long insertBranch(long userId, long conversationId) {
        jdbc.update(
                """
                INSERT INTO app_conversation_branch (conversation_id, user_id, title, is_default)
                VALUES (?, ?, 'Default', TRUE)
                """,
                conversationId,
                userId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_conversation_branch WHERE conversation_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                conversationId
        );
    }

    private void insertMessage(long userId, long conversationId, long branchId) {
        jdbc.update(
                """
                INSERT INTO app_message (user_id, conversation_id, branch_id, role, content, status)
                VALUES (?, ?, ?, 'assistant', 'hello', 'SUCCESS')
                """,
                userId,
                conversationId,
                branchId
        );
    }

    private void insertMemory(long conversationId, long branchId) {
        jdbc.update(
                """
                INSERT INTO app_conversation_memory (conversation_id, branch_id, summary_preview)
                VALUES (?, ?, 'summary')
                """,
                conversationId,
                branchId
        );
        jdbc.update(
                """
                INSERT INTO app_conversation_memory_entry
                    (conversation_id, branch_id, entry_key, memory_type, content, keywords_json)
                VALUES (?, ?, 'entry-1', 'fact', 'memory', '[]')
                """,
                conversationId,
                branchId
        );
    }

    private void insertPasswordResetToken(long userId) {
        String suffix = String.valueOf(IDS.incrementAndGet());
        jdbc.update(
                """
                INSERT INTO app_password_reset_token
                    (request_id, user_id, account_key, code_hash, expires_at)
                VALUES (?, ?, ?, ?, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """,
                "reset-" + suffix,
                userId,
                "a".repeat(64),
                "b".repeat(64)
        );
    }

    private long insertTrustedDevice(long userId) {
        String token = "dv_delete_" + IDS.incrementAndGet();
        jdbc.update(
                """
                INSERT INTO app_h5_visitor_device
                    (device_token, trusted_user_id, first_ip, latest_ip, ua_hash, user_agent)
                VALUES (?, ?, '203.0.113.10', '203.0.113.10', 'device-ua', 'delete-test-agent')
                """,
                token,
                userId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_h5_visitor_device WHERE device_token = ?",
                Long.class,
                token
        );
    }

    private String insertClientUid(long userId) {
        String clientUid = "delete_client_" + IDS.incrementAndGet();
        jdbc.update(
                "INSERT INTO app_h5_client_uid (client_uid, user_id) VALUES (?, ?)",
                clientUid,
                userId
        );
        return clientUid;
    }

    private int count(String table, String column, long value) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value
        );
    }
}
