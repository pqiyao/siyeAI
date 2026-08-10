package com.example.sillyspringboot.ops.generation;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationObservabilityMigrationTest {

    @Test
    void h2MigrationsIncludeUnifiedAiLogTraceDetailsCharacterPromotionAndOfficialVoicePreference() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:generation_observability_v102;MODE=MySQL;"
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
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_generation_attempt'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_generation_model_pricing'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_generation_task_daily_stat'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_generation_attempt_daily_stat'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_conversation_branch' AND column_name = 'memory_source_revision'",
                Integer.class
        ));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_conversation_memory' "
                        + "AND column_name IN ('manual_revision', 'memory_revision', 'applied_source_revision')",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_inbox_ad'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_user_inbox_ad_read'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_checkin_activity'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_checkin_claim'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_character_image_policy'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_ai_provider_account' AND column_name = 'version_no'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_user_tts_voice'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'app_user_tts_voice_binding'",
                Integer.class
        ));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_generation_attempt' "
                        + "AND column_name IN ('request_id', 'trace_id', 'error_message')",
                Integer.class
        ));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes "
                        + "WHERE table_name = 'app_generation_attempt' "
                        + "AND index_name IN ('idx_generation_attempt_trace', 'idx_generation_attempt_request')",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name = 'app_character_system_promotion'",
                Integer.class
        ));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_h5_user_ai_provider' "
                        + "AND column_name IN ('official_tts_voice_name', 'official_tts_voice_template_code')",
                Integer.class
        ));
        assertEquals(6, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name IN ("
                        + "'app_ai_chat_model_settings', "
                        + "'app_ai_chat_offering', "
                        + "'app_ai_chat_offering_price', "
                        + "'app_h5_user_ai_chat_model', "
                        + "'app_chat_model_preference', "
                        + "'app_chat_generation_context')",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE LOWER(table_name) = 'app_character' "
                        + "AND LOWER(constraint_name) = 'ck_character_owner_private'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_payment_order' AND column_name = 'expires_at' "
                        + "AND is_nullable = 'NO'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes "
                        + "WHERE table_name = 'app_payment_order' "
                        + "AND index_name = 'idx_payment_order_pending_expiry'",
                Integer.class
        ));
        assertEquals(13, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE success = TRUE "
                        + "AND version IN ('106', '107', '108', '109', '110', '111', '112', '113', '114', '115', '117', '118', '119')",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name = 'app_conversation_memory_refresh_metric'",
                Integer.class
        ));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_character' "
                        + "AND column_name IN ('visual_prompt', 'visual_negative_prompt')",
                Integer.class
        ));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'app_character_member' "
                        + "AND column_name IN ('visual_prompt', 'visual_negative_prompt')",
                Integer.class
        ));
        assertEquals("119", jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class
        ));
    }
}
