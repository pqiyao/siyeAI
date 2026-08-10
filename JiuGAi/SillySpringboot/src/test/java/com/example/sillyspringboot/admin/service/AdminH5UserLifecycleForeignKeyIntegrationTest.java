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
        insertPrivateChatPreset(userId);
        insertMessage(userId, conversationId, branchId);
        long generationTaskId = insertGenerationData(userId, conversationId, characterId);
        insertMemory(conversationId, branchId);
        jdbc.update(
                """
                INSERT INTO app_conversation_memory_refresh_metric (
                    request_id, conversation_id, branch_id, refresh_mode, extraction_mode, outcome,
                    input_message_count, visible_message_count, existing_entry_count,
                    model_output_entry_count, accepted_entry_count, rejected_entry_count,
                    conflict_count, disable_requested_count, duration_ms
                ) VALUES (?, ?, ?, 'AUTO', 'FULL', 'STRUCTURED_APPLIED', 8, 8, 2, 1, 1, 0, 0, 0, 25)
                """,
                "memory-metric-" + userId,
                conversationId,
                branchId
        );
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
        assertThat(count("app_conversation_memory_refresh_metric", "conversation_id", conversationId)).isZero();
        assertThat(count("app_generation_task", "id", generationTaskId)).isZero();
        assertThat(count("app_generation_attempt", "generation_task_id", generationTaskId)).isZero();
        assertThat(count("app_generation_stat_event", "task_id", generationTaskId)).isZero();
        assertThat(count("app_password_reset_token", "user_id", userId)).isZero();
        assertThat(count("app_user_chat_preference", "user_id", userId)).isZero();
        assertThat(count("app_h5_visitor_device", "id", deviceId)).isZero();
        assertThat(count("app_h5_client_uid", "user_id", userId)).isZero();
        assertThat(count("app_h5_upload_asset", "owner_user_id", userId)).isZero();
        assertThat(count("app_chat_preset", "owner_user_id", userId)).isZero();

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
        assertNoDirectUserReferencesRemain(userId);
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
                "INSERT INTO app_character (owner_user_id, private_card, name, st_avatar_url, avatar_url) VALUES (?, TRUE, ?, '', ?)",
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

    @Test
    void deletesPromotedSourceCharacterWithoutDeletingIndependentSystemCharacter() {
        long userId = insertUser();
        long sourceCharacterId = insertOwnedCharacter(userId);
        long targetCharacterId = insertPublicCharacter();
        jdbc.update(
                """
                INSERT INTO app_character_system_promotion
                    (source_character_id, source_user_id, target_character_id, promoted_by)
                VALUES (?, ?, ?, 'integration-test')
                """,
                sourceCharacterId,
                userId,
                targetCharacterId
        );

        Map<String, Object> result = service.deleteUserById(userId);

        assertThat(result.get("deleted")).isEqualTo(true);
        assertThat(count("app_user", "id", userId)).isZero();
        assertThat(count("app_character", "id", sourceCharacterId)).isZero();
        assertThat(count("app_character", "id", targetCharacterId)).isEqualTo(1);
        assertThat(count("app_character_system_promotion", "source_user_id", userId)).isZero();
    }

    @Test
    void hardDeletesAllAuxiliaryUserDataWithoutTouchingAnotherUser() {
        long deletingUserId = insertUser();
        long survivingUserId = insertUser();
        insertAuxiliaryUserData(deletingUserId, "delete-" + IDS.incrementAndGet());
        insertAuxiliaryUserData(survivingUserId, "keep-" + IDS.incrementAndGet());

        Map<String, Object> result = service.deleteUserById(deletingUserId);

        assertThat(result.get("deleted")).isEqualTo(true);
        assertAuxiliaryUserDataCount(deletingUserId, 0);
        assertAuxiliaryUserDataCount(survivingUserId, 1);
        assertThat(count("app_user", "id", survivingUserId)).isEqualTo(1);
        assertNoDirectUserReferencesRemain(deletingUserId);
    }

    private void insertAuxiliaryUserData(long userId, String suffix) {
        jdbc.update(
                "INSERT INTO app_user_tts_voice (user_id, request_id, display_name) VALUES (?, ?, ?)",
                userId,
                "voice-request-" + suffix,
                "Voice " + suffix
        );
        long voiceId = jdbc.queryForObject(
                "SELECT id FROM app_user_tts_voice WHERE user_id = ? AND request_id = ?",
                Long.class,
                userId,
                "voice-request-" + suffix
        );
        jdbc.update(
                """
                INSERT INTO app_user_tts_voice_binding
                    (user_id, scope_type, character_id, member_id, voice_id)
                VALUES (?, 'GLOBAL', 0, 0, ?)
                """,
                userId,
                voiceId
        );
        jdbc.update(
                "INSERT INTO app_user_tts_voice_instance (user_id, template_code) VALUES (?, ?)",
                userId,
                "template-" + suffix
        );
        jdbc.update(
                "INSERT INTO app_h5_user_ai_chat_model (user_id, model_name) VALUES (?, ?)",
                userId,
                "model-" + suffix
        );
        jdbc.update(
                """
                INSERT INTO app_chat_model_preference
                    (user_id, conversation_id, branch_id, source_type)
                VALUES (?, 0, 0, 'USER')
                """,
                userId
        );
        jdbc.update(
                """
                INSERT INTO app_chat_generation_context
                    (user_id, conversation_id, generation_request_id, action_type, source_type)
                VALUES (?, 0, ?, 'GENERATE', 'USER')
                """,
                userId,
                "generation-" + suffix
        );
        long activityId = jdbc.queryForObject(
                "SELECT id FROM app_checkin_activity WHERE code = 'daily_checkin'",
                Long.class
        );
        jdbc.update(
                "INSERT INTO app_checkin_claim (user_id, activity_id, biz_date) VALUES (?, ?, CURRENT_DATE)",
                userId,
                activityId
        );
        jdbc.update(
                "INSERT INTO app_user_inbox_ad_read (user_id, ad_id) VALUES (?, ?)",
                userId,
                IDS.incrementAndGet()
        );
        jdbc.update(
                """
                INSERT INTO app_illustration_work
                    (title, slug, cover_url, image_url, source, submitter_user_id)
                VALUES (?, ?, ?, ?, 'USER', ?)
                """,
                "Illustration " + suffix,
                "illustration-" + suffix,
                "/uploads/h5/cover-" + suffix + ".png",
                "/uploads/h5/image-" + suffix + ".png",
                userId
        );
    }

    private void assertAuxiliaryUserDataCount(long userId, int expected) {
        assertThat(count("app_user_tts_voice_binding", "user_id", userId)).isEqualTo(expected);
        assertThat(count("app_user_tts_voice", "user_id", userId)).isEqualTo(expected);
        assertThat(count("app_user_tts_voice_instance", "user_id", userId)).isEqualTo(expected);
        assertThat(count("app_h5_user_ai_chat_model", "user_id", userId)).isEqualTo(expected);
        assertThat(count("app_chat_model_preference", "user_id", userId)).isEqualTo(expected);
        assertThat(count("app_chat_generation_context", "user_id", userId)).isEqualTo(expected);
        assertThat(count("app_checkin_claim", "user_id", userId)).isEqualTo(expected);
        assertThat(count("app_user_inbox_ad_read", "user_id", userId)).isEqualTo(expected);
        assertThat(count("app_illustration_work", "submitter_user_id", userId)).isEqualTo(expected);
    }

    private void assertNoDirectUserReferencesRemain(long userId) {
        List<Map<String, Object>> userReferenceColumns = jdbc.queryForList(
                """
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND column_name IN (
                      'USER_ID', 'OWNER_USER_ID', 'TARGET_USER_ID', 'SUBMITTER_USER_ID',
                      'SOURCE_USER_ID', 'FIRST_USER_ID', 'LATEST_USER_ID', 'TRUSTED_USER_ID'
                  )
                ORDER BY table_name, column_name
                """
        );
        for (Map<String, Object> reference : userReferenceColumns) {
            String table = String.valueOf(reference.get("table_name"));
            String column = String.valueOf(reference.get("column_name"));
            if ("APP_EXTERNAL_CLEANUP_TASK".equals(table) && "SOURCE_USER_ID".equals(column)) {
                continue;
            }
            Integer remaining = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                    Integer.class,
                    userId
            );
            assertThat(remaining)
                    .as("remaining user reference in %s.%s", table, column)
                    .isZero();
        }
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

    private long insertOwnedCharacter(long userId) {
        String name = "Owned Delete Test " + IDS.incrementAndGet();
        jdbc.update(
                "INSERT INTO app_character (owner_user_id, private_card, name, st_avatar_url) VALUES (?, TRUE, ?, '')",
                userId,
                name
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_character WHERE owner_user_id = ? AND name = ?",
                Long.class,
                userId,
                name
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

    private void insertPrivateChatPreset(long userId) {
        String sourceName = "delete-preset-" + IDS.incrementAndGet();
        jdbc.update(
                """
                INSERT INTO app_chat_preset
                    (owner_user_id, scope, source_type, api_type, source_name, name, bundle_json)
                VALUES (?, 'PRIVATE', 'USER_COPY', 'openai', ?, 'Delete Test Preset', '{}')
                """,
                userId,
                sourceName
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

    private long insertGenerationData(long userId, long conversationId, long characterId) {
        String clientMessageId = "delete-generation-" + IDS.incrementAndGet();
        jdbc.update(
                """
                INSERT INTO app_generation_task
                    (user_id, conversation_id, request_type, client_message_id, status)
                VALUES (?, ?, 'generate', ?, 'SUCCESS')
                """,
                userId,
                conversationId,
                clientMessageId
        );
        long taskId = jdbc.queryForObject(
                "SELECT id FROM app_generation_task WHERE conversation_id = ? AND client_message_id = ?",
                Long.class,
                conversationId,
                clientMessageId
        );
        jdbc.update(
                """
                INSERT INTO app_generation_attempt
                    (generation_task_id, conversation_id, character_id, attempt_no,
                     provider_key, route_key, was_fallback, started_at, finished_at,
                     duration_ms, status)
                VALUES (?, ?, ?, 1, 'test', 'test', FALSE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 'SUCCESS')
                """,
                taskId,
                conversationId,
                characterId
        );
        jdbc.update(
                """
                INSERT INTO app_generation_stat_event
                    (task_id, source_type, source_id, event_day, status)
                VALUES (?, 'TASK', ?, CURRENT_DATE, 'SUCCESS')
                """,
                taskId,
                taskId
        );
        return taskId;
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
