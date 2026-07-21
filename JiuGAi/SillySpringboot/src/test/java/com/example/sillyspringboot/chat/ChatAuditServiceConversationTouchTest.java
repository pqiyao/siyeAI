package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.conversation.dto.CreateConversationRequest;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ChatAuditServiceConversationTouchTest {

    @Autowired private DataSource dataSource;
    @Autowired private AppUserMapper appUserMapper;
    @Autowired private AppTokenService tokenService;
    @Autowired private AppCharacterMapper characterMapper;
    @Autowired private AppConversationService conversationService;
    @Autowired private AppConversationMapper conversationMapper;
    @Autowired private ChatAuditService auditService;

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
    void onQueued_shouldTouchUpdatedAt_andSetTitleIfNull() {
        AppUser u = new AppUser();
        u.setTelegramUserId(2000L);
        u.setUsername("u2000");
        u.setFirstName("U");
        appUserMapper.insert(u);
        String token = tokenService.issueToken(u.getId()).token();

        AppCharacter ch = new AppCharacter();
        ch.setStAvatarUrl("touch.png");
        ch.setName("Touch");
        characterMapper.insert(ch);

        CreateConversationRequest req = new CreateConversationRequest();
        req.setIdempotencyKey("idem-touch");
        req.setCharacterId(ch.getId());
        long convId = conversationService.createOrEnsure(req, token).conversationId();

        AppConversation before = conversationMapper.findByIdForUser(convId, u.getId());
        assertThat(before.getTitle()).isNull();

        auditService.onQueued(convId, "你好  世界   123456789012345678901234567890XYZ", "m1", token, "t1");
        AppConversation after = conversationMapper.findByIdForUser(convId, u.getId());
        assertThat(after.getTitle()).isNotBlank();
        assertThat(after.getTitle().length()).isLessThanOrEqualTo(30);
    }
}

