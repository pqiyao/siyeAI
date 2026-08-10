package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AppConversationMemoryMapperGuardTest {

    @Test
    void manualRefreshGuard_shouldAcquireAtomicallyAndRespectCooldown() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:memory_guard_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE app_conversation_memory (
                    conversation_id BIGINT NOT NULL,
                    branch_id BIGINT NOT NULL,
                    summary_preview TEXT NULL,
                    facts_count INT NOT NULL DEFAULT 0,
                    memory_world_name VARCHAR(255) NULL,
                    entry_count INT NOT NULL DEFAULT 0,
                    enabled_entry_count INT NOT NULL DEFAULT 0,
                    manual_revision BIGINT NOT NULL DEFAULT 0,
                    memory_revision BIGINT NOT NULL DEFAULT 0,
                    applied_source_revision BIGINT NOT NULL DEFAULT 0,
                    last_source_message_id BIGINT NULL,
                    last_refreshed_message_count INT NOT NULL DEFAULT 0,
                    last_manual_refresh_at TIMESTAMP NULL,
                    manual_refresh_started_at TIMESTAMP NULL,
                    manual_refresh_token VARCHAR(64) NULL,
                    last_synced_at TIMESTAMP NULL,
                    sync_status VARCHAR(32) NULL,
                    sync_error VARCHAR(512) NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (conversation_id, branch_id)
                )
                """);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/conversation/AppConversationMemoryMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        assertThat(factory).isNotNull();

        try (SqlSession session = factory.openSession(true)) {
            AppConversationMemoryMapper mapper = session.getMapper(AppConversationMemoryMapper.class);
            mapper.ensureForBranch(10L, 20L);

            LocalDateTime now = LocalDateTime.now();
            assertThat(mapper.tryAcquireManualRefresh(10L, 20L, "token-1", now.minusSeconds(60), now.minusSeconds(300))).isEqualTo(1);
            assertThat(mapper.tryAcquireManualRefresh(10L, 20L, "token-2", now.minusSeconds(60), now.minusSeconds(300))).isZero();
            assertThat(mapper.releaseManualRefresh(10L, 20L, "wrong-token")).isZero();
            assertThat(mapper.releaseManualRefresh(10L, 20L, "token-1")).isEqualTo(1);
            assertThat(mapper.tryAcquireManualRefresh(10L, 20L, "token-3", now.minusSeconds(60), now.minusSeconds(300))).isZero();

            jdbc.update("""
                    INSERT INTO app_conversation_memory
                        (conversation_id, branch_id, enabled_entry_count, sync_status, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, 11L, 0L, 1, "FAILED", now.minusSeconds(60));
            jdbc.update("""
                    INSERT INTO app_conversation_memory
                        (conversation_id, branch_id, enabled_entry_count, sync_status, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, 12L, 0L, 1, "FAILED", now);
            jdbc.update("""
                    INSERT INTO app_conversation_memory
                        (conversation_id, branch_id, enabled_entry_count, sync_status, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, 13L, 2L, 1, "SYNCING", now.minusSeconds(300));

            var retryCandidates = mapper.listWorldbookSyncRetryCandidates(
                    now.minusSeconds(30),
                    now.minusSeconds(120),
                    10
            );
            assertThat(retryCandidates)
                    .extracting(memory -> memory.getConversationId())
                    .containsExactly(13L, 11L);
            assertThat(mapper.tryClaimWorldbookSync(
                    11L,
                    0L,
                    now.minusSeconds(30),
                    now.minusSeconds(120)
            )).isEqualTo(1);
            assertThat(mapper.tryClaimWorldbookSync(
                    11L,
                    0L,
                    now.minusSeconds(30),
                    now.minusSeconds(120)
            )).isZero();
            assertThat(mapper.updateWorldbookSyncStatusWithRevision(
                    11L,
                    0L,
                    "memory-world",
                    2,
                    2,
                    "SUCCESS",
                    null,
                    1L
            )).isZero();
            assertThat(mapper.updateWorldbookSyncStatusWithRevision(
                    11L,
                    0L,
                    "memory-world",
                    2,
                    2,
                    "SUCCESS",
                    null,
                    0L
            )).isEqualTo(1);
        }
    }
}
