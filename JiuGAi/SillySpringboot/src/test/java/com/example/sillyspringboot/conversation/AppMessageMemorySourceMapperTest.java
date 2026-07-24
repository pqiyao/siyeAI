package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppMessageMemorySourceMapperTest {

    private SqlSession sqlSession;
    private AppMessageMapper messageMapper;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpMapper() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:message_memory_source_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE app_message (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NULL,
                    conversation_id BIGINT NOT NULL,
                    branch_id BIGINT NOT NULL,
                    parent_message_id BIGINT NULL,
                    role VARCHAR(32) NOT NULL,
                    message_kind VARCHAR(32) NULL,
                    continue_from_message_id BIGINT NULL,
                    client_message_id VARCHAR(128) NULL,
                    content TEXT NULL,
                    voice_url VARCHAR(512) NULL,
                    voice_duration_ms INT NULL,
                    st_message_ref VARCHAR(128) NULL,
                    swipe_index INT NULL,
                    status VARCHAR(32) NULL,
                    error_code VARCHAR(64) NULL,
                    trace_id VARCHAR(128) NULL,
                    created_at TIMESTAMP NULL,
                    updated_at TIMESTAMP NULL,
                    deleted_at TIMESTAMP NULL
                )
                """);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/chat/AppMessageMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        assertThat(factory).isNotNull();
        sqlSession = factory.openSession(true);
        messageMapper = sqlSession.getMapper(AppMessageMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void recentMemorySourceWindow_shouldFilterHiddenSwipesAndBlankStoppedBeforeLimit() {
        insert(1L, 10L, 20L, "user", "第一条主线", "SUCCESS", null, null);
        insert(2L, 10L, 20L, "assistant", "第一条回复", "SUCCESS", "root:2", 0);
        insert(3L, 10L, 20L, "user", "第二条主线", "SUCCESS", null, null);
        insert(4L, 10L, 20L, "assistant", "第二条回复", "STOPPED", null, 0);

        for (long id = 5L; id <= 104L; id++) {
            insert(id, 10L, 20L, "assistant", "隐藏候选 " + id, "SUCCESS", "root:2", (int) id);
        }
        insert(105L, 10L, 20L, "assistant", "   ", "STOPPED", null, 0);
        insert(106L, 10L, 20L, "assistant", null, "STOPPED", null, 0);
        insert(107L, 10L, 20L, "assistant", "可见的中止回复", "STOPPED", "root:107", 0);

        List<AppMessage> rows = messageMapper.listRecentMemorySourceByConversationBranchAsc(10L, 20L, 4);

        assertThat(rows).extracting(AppMessage::getId).containsExactly(2L, 3L, 4L, 107L);
        assertThat(messageMapper.countMemorySourceByConversationBranchId(10L, 20L)).isEqualTo(5);
        assertThat(messageMapper.findLatestMemorySourceMessageIdByBranch(10L, 20L)).isEqualTo(107L);
    }

    @Test
    void conversationMemorySourceWindow_shouldRemainUnscopedWhileBranchWindowStaysIsolated() {
        insert(1L, 10L, 20L, "user", "分支一", "SUCCESS", null, null);
        insert(2L, 10L, 21L, "user", "分支二", "SUCCESS", null, null);
        insert(3L, 10L, 21L, "assistant", "分支二回复", "SUCCESS", "root:3", 0);
        insert(4L, 11L, 20L, "user", "其他会话", "SUCCESS", null, null);

        assertThat(messageMapper.listRecentMemorySourceByConversationAsc(10L, 10))
                .extracting(AppMessage::getId)
                .containsExactly(1L, 2L, 3L);
        assertThat(messageMapper.listRecentMemorySourceByConversationBranchAsc(10L, 20L, 10))
                .extracting(AppMessage::getId)
                .containsExactly(1L);
        assertThat(messageMapper.countMemorySourceByConversationId(10L)).isEqualTo(3);
        assertThat(messageMapper.findLatestMemorySourceMessageId(10L)).isEqualTo(3L);
    }

    private void insert(
            long id,
            long conversationId,
            long branchId,
            String role,
            String content,
            String status,
            String stMessageRef,
            Integer swipeIndex
    ) {
        jdbc.update("""
                        INSERT INTO app_message (
                            id, conversation_id, branch_id, role, content,
                            status, st_message_ref, swipe_index
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                conversationId,
                branchId,
                role,
                content,
                status,
                stMessageRef,
                swipeIndex
        );
    }
}
