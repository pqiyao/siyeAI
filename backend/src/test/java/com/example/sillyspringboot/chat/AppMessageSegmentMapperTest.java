package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.entity.AppMessageSegment;
import com.example.sillyspringboot.chat.mapper.AppMessageSegmentMapper;
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

class AppMessageSegmentMapperTest {

    private SqlSession sqlSession;
    private AppMessageSegmentMapper mapper;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpMapper() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:message_segments_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE app_message_segment (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    message_id BIGINT NOT NULL,
                    segment_index INT NOT NULL,
                    segment_type VARCHAR(16) NOT NULL,
                    speaker_member_id BIGINT NULL,
                    speaker_name_snapshot VARCHAR(64) NULL,
                    speaker_avatar_snapshot VARCHAR(512) NULL,
                    content TEXT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/chat/AppMessageSegmentMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        assertThat(factory).isNotNull();
        sqlSession = factory.openSession(true);
        mapper = sqlSession.getMapper(AppMessageSegmentMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void batchReadGroupsMessagesAndKeepsSegmentOrderStable() {
        insert(20L, 1, "NARRATOR", null, "旁白", "第二条消息的第二段");
        insert(10L, 1, "CHARACTER", 102L, "顾言", "第一条消息的第二段");
        insert(20L, 0, "CHARACTER", 201L, "苏禾", "第二条消息的第一段");
        insert(10L, 0, "CHARACTER", 101L, "林夏", "第一条消息的第一段");

        List<AppMessageSegment> rows = mapper.listByMessageIds(List.of(20L, 10L));

        assertThat(rows).extracting(AppMessageSegment::getMessageId)
                .containsExactly(10L, 10L, 20L, 20L);
        assertThat(rows).extracting(AppMessageSegment::getSegmentIndex)
                .containsExactly(0, 1, 0, 1);
        assertThat(rows).extracting(AppMessageSegment::getContent)
                .containsExactly(
                        "第一条消息的第一段",
                        "第一条消息的第二段",
                        "第二条消息的第一段",
                        "第二条消息的第二段"
                );
    }

    private void insert(long messageId, int index, String type, Long memberId, String name, String content) {
        jdbc.update("""
                        INSERT INTO app_message_segment (
                            message_id, segment_index, segment_type, speaker_member_id,
                            speaker_name_snapshot, speaker_avatar_snapshot, content, status
                        ) VALUES (?, ?, ?, ?, ?, '', ?, 'SUCCESS')
                        """,
                messageId, index, type, memberId, name, content);
    }
}
