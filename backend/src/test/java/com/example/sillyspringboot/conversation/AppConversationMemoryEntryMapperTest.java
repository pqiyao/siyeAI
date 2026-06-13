package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AppConversationMemoryEntryMapperTest {

    private SqlSession sqlSession;
    private AppConversationMemoryEntryMapper entryMapper;

    @BeforeEach
    void setUpMapper() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:memory_mapper_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE app_conversation (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY
                )
                """);
        jdbc.execute("""
                CREATE TABLE app_conversation_memory_entry (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    conversation_id BIGINT NOT NULL,
                    entry_key VARCHAR(128) NOT NULL,
                    memory_type VARCHAR(32) NOT NULL,
                    title VARCHAR(255) NULL,
                    content TEXT NOT NULL,
                    keywords_json TEXT NOT NULL,
                    secondary_keywords_json TEXT NULL,
                    priority INT NOT NULL DEFAULT 100,
                    position VARCHAR(32) NOT NULL DEFAULT 'before_char',
                    constant_injection BOOLEAN NOT NULL DEFAULT FALSE,
                    selective BOOLEAN NOT NULL DEFAULT FALSE,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    confidence DECIMAL(5,2) NULL,
                    source_message_from_id BIGINT NULL,
                    source_message_to_id BIGINT NULL,
                    last_activated_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted_at TIMESTAMP NULL DEFAULT NULL,
                    CONSTRAINT fk_memory_entry_conv FOREIGN KEY (conversation_id) REFERENCES app_conversation(id),
                    CONSTRAINT uk_memory_entry_key UNIQUE (conversation_id, entry_key)
                )
                """);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/conversation/AppConversationMemoryEntryMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        assertThat(factory).isNotNull();

        sqlSession = factory.openSession(true);
        entryMapper = sqlSession.getMapper(AppConversationMemoryEntryMapper.class);

        jdbc.update("INSERT INTO app_conversation(id) VALUES (?), (?)", 1001L, 1002L);
    }

    @Test
    void memoryEntryMapper_shouldUpsertQueryDisableAndSoftDeletePerConversation() {
        long conversationId = 1001L;
        long otherConversationId = 1002L;

        entryMapper.upsert(entry(conversationId, "identity_user_call_gege", "identity",
                "User call name", "User wants to be called gege.", "[\"gege\",\"call\"]", 200));
        entryMapper.upsert(entry(conversationId, "relationship_close", "relationship",
                "Close relationship", "User and character are close.", "[\"relationship\",\"close\"]", 160));
        entryMapper.upsert(entry(otherConversationId, "identity_other", "identity",
                "Other conversation", "Other conversation has isolated memory.", "[\"isolated\"]", 120));

        assertThat(entryMapper.countAllByConversationId(conversationId)).isEqualTo(2);
        assertThat(entryMapper.countEnabledByConversationId(conversationId)).isEqualTo(2);
        assertThat(entryMapper.countAllByConversationId(otherConversationId)).isEqualTo(1);

        AppConversationMemoryEntry updated = entry(conversationId, "identity_user_call_gege", "identity",
                "User call name", "User wants the character to call him gege.", "[\"gege\",\"call\"]", 180);
        entryMapper.upsert(updated);

        List<AppConversationMemoryEntry> all = entryMapper.listAllByConversationId(conversationId);
        assertThat(all)
                .filteredOn(entry -> "identity_user_call_gege".equals(entry.getEntryKey()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getContent()).contains("call him gege");
                    assertThat(entry.getPriority()).isEqualTo(180);
                });

        entryMapper.disableByKey(conversationId, "identity_user_call_gege");
        assertThat(entryMapper.countEnabledByConversationId(conversationId)).isEqualTo(1);
        assertThat(entryMapper.listEnabledByConversationId(conversationId))
                .extracting(AppConversationMemoryEntry::getEntryKey)
                .containsExactly("relationship_close");

        AppConversationMemoryEntry remaining = entryMapper.listEnabledByConversationId(conversationId).get(0);
        entryMapper.disableById(remaining.getId());
        assertThat(entryMapper.countEnabledByConversationId(conversationId)).isZero();
        assertThat(entryMapper.countAllByConversationId(conversationId)).isEqualTo(2);

        entryMapper.softDeleteByConversationId(conversationId);
        assertThat(entryMapper.countAllByConversationId(conversationId)).isZero();
        assertThat(entryMapper.countEnabledByConversationId(conversationId)).isZero();
        assertThat(entryMapper.countAllByConversationId(otherConversationId)).isEqualTo(1);
    }

    private AppConversationMemoryEntry entry(
            long conversationId,
            String entryKey,
            String memoryType,
            String title,
            String content,
            String keywordsJson,
            int priority
    ) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setConversationId(conversationId);
        entry.setEntryKey(entryKey);
        entry.setMemoryType(memoryType);
        entry.setTitle(title);
        entry.setContent(content);
        entry.setKeywordsJson(keywordsJson);
        entry.setPriority(priority);
        entry.setPosition("before_char");
        entry.setConstantInjection(true);
        entry.setSelective(false);
        entry.setEnabled(true);
        entry.setConfidence(new BigDecimal("0.95"));
        return entry;
    }
}
