package com.example.sillyspringboot.migration;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnsembleChatMigrationTest {

    @Test
    void v114CreatesDefaultModeSegmentsAndLifecycleConstraints() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:ensemble_chat_v114;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration-common", "classpath:db/migration-h2")
                .load();
        flyway.migrate();
        flyway.validate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_message_segment'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_character' AND column_name = 'ensemble_chat_mode'",
                Integer.class
        )).isEqualTo(1);

        jdbc.update("INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)", 990114L, "ensemble-test");
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?", Long.class, 990114L);
        jdbc.update("INSERT INTO app_character (st_avatar_url, name) VALUES (?, ?)",
                "ensemble-migration.png", "组合角色");
        Long characterId = jdbc.queryForObject(
                "SELECT id FROM app_character WHERE st_avatar_url = ?", Long.class, "ensemble-migration.png");
        assertThat(jdbc.queryForObject(
                "SELECT ensemble_chat_mode FROM app_character WHERE id = ?", String.class, characterId
        )).isEqualTo("NATURAL");

        jdbc.update("INSERT INTO app_character_member "
                        + "(character_id, name, avatar_url, is_primary, sort_order, enabled) "
                        + "VALUES (?, ?, ?, TRUE, 0, TRUE)",
                characterId, "小夏", "/avatars/xia.png");
        Long memberId = jdbc.queryForObject(
                "SELECT id FROM app_character_member WHERE character_id = ?", Long.class, characterId);
        jdbc.update("INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, ?, ?)",
                userId, characterId, "迁移测试");
        Long conversationId = jdbc.queryForObject(
                "SELECT id FROM app_conversation WHERE user_id = ?", Long.class, userId);
        jdbc.update("INSERT INTO app_message (user_id, conversation_id, role, content, status) "
                        + "VALUES (?, ?, 'assistant', ?, 'SUCCESS')",
                userId, conversationId, "【小夏】\n你好");
        Long messageId = jdbc.queryForObject(
                "SELECT id FROM app_message WHERE conversation_id = ?", Long.class, conversationId);

        jdbc.update("INSERT INTO app_message_segment "
                        + "(message_id, segment_index, segment_type, speaker_member_id, "
                        + "speaker_name_snapshot, speaker_avatar_snapshot, content, status) "
                        + "VALUES (?, 0, 'CHARACTER', ?, ?, ?, ?, 'SUCCESS')",
                messageId, memberId, "小夏", "/avatars/xia.png", "你好");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO app_message_segment "
                        + "(message_id, segment_index, segment_type, content, status) "
                        + "VALUES (?, 0, 'NARRATOR', '重复', 'SUCCESS')", messageId))
                .isInstanceOf(DuplicateKeyException.class);

        jdbc.update("DELETE FROM app_character_member WHERE id = ?", memberId);
        assertThat(jdbc.queryForObject(
                "SELECT speaker_member_id FROM app_message_segment WHERE message_id = ?",
                Long.class,
                messageId
        )).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT speaker_name_snapshot FROM app_message_segment WHERE message_id = ?",
                String.class,
                messageId
        )).isEqualTo("小夏");
        assertThat(jdbc.queryForObject(
                "SELECT speaker_avatar_snapshot FROM app_message_segment WHERE message_id = ?",
                String.class,
                messageId
        )).isEqualTo("/avatars/xia.png");

        jdbc.update("DELETE FROM app_message WHERE id = ?", messageId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_message_segment WHERE message_id = ?",
                Integer.class,
                messageId
        )).isZero();
    }
}
