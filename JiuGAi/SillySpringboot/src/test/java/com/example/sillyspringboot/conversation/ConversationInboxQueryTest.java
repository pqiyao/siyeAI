package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.dto.ConversationInboxItemDto;
import com.example.sillyspringboot.conversation.dto.CreateConversationRequest;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ConversationInboxQueryTest {

    @Autowired private DataSource dataSource;
    @Autowired private AppUserMapper userMapper;
    @Autowired private AppTokenService tokenService;
    @Autowired private AppCharacterMapper characterMapper;
    @Autowired private AppConversationService conversationService;
    @Autowired private AppConversationMapper conversationMapper;
    @Autowired private AppMessageMapper messageMapper;

    @BeforeEach
    void migrate() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();
    }

    @Test
    void inbox_shouldIncludeLastMessageFields() {
        AppUser u = new AppUser();
        u.setTelegramUserId(3000L);
        u.setUsername("u3000");
        u.setFirstName("U");
        userMapper.insert(u);
        String token = tokenService.issueToken(u.getId()).token();

        AppCharacter ch = new AppCharacter();
        ch.setStAvatarUrl("inbox.png");
        ch.setName("InboxChar");
        characterMapper.insert(ch);

        CreateConversationRequest req = new CreateConversationRequest();
        req.setIdempotencyKey("idem-inbox");
        req.setCharacterId(ch.getId());
        long convId = conversationService.createOrEnsure(req, token).conversationId();

        AppMessage m = new AppMessage();
        m.setUserId(u.getId());
        m.setConversationId(convId);
        m.setRole("assistant");
        m.setClientMessageId("m1");
        m.setContent("hello");
        m.setStatus("SUCCESS");
        m.setTraceId("t1");
        messageMapper.insert(m);

        List<ConversationInboxItemDto> items = conversationMapper.listInboxByUser(u.getId(), 10);
        assertThat(items).isNotEmpty();
        ConversationInboxItemDto one = items.get(0);
        assertThat(one.conversationId()).isEqualTo(convId);
        assertThat(one.lastMessageRole()).isEqualTo("assistant");
        assertThat(one.lastMessageContent()).isEqualTo("hello");
        assertThat(one.lastMessageAt()).isNotNull();
    }
}

