package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.conversation.dto.CreateConversationRequest;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class AppConversationServiceIdempotencyTest {

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private AppTokenService appTokenService;

    @Autowired
    private AppConversationService conversationService;

    @Autowired
    private AppCharacterMapper appCharacterMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void migrateIfNeeded() {
        // 由于当前环境下 Flyway auto-config 在测试启动阶段未必执行到位，
        // 这里用显式 migrate 保证表结构就绪（只影响测试，不改变第 3 阶段代码主线）。
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        flyway.migrate();
    }

    @Test
    void createOrEnsure_sameIdempotencyKey_shouldBeIdempotent() {
        // given
        AppUser u = new AppUser();
        u.setTelegramUserId(999L);
        u.setUsername("u999");
        u.setFirstName("U");
        appUserMapper.insert(u);
        Long userId = u.getId();

        String token = appTokenService.issueToken(userId).token();

        AppCharacter ch = new AppCharacter();
        ch.setStAvatarUrl("test_10.png");
        ch.setName("C10");
        ch.setDescription("D");
        appCharacterMapper.insert(ch);

        CreateConversationRequest req = new CreateConversationRequest();
        req.setIdempotencyKey("idem-1");
        req.setCharacterId(ch.getId());

        // when
        ConversationDetailDto d1 = conversationService.createOrEnsure(req, token);
        ConversationDetailDto d2 = conversationService.createOrEnsure(req, token);

        // then
        assertThat(d1.conversationId()).isEqualTo(d2.conversationId());
        assertThat(d1.characterId()).isEqualTo(ch.getId());
    }

    @Test
    void createOrEnsure_sameIdempotencyKey_characterIdMismatch_shouldConflict() {
        // given
        AppUser u = new AppUser();
        u.setTelegramUserId(1000L);
        u.setUsername("u1000");
        u.setFirstName("U");
        appUserMapper.insert(u);
        Long userId = u.getId();

        String token = appTokenService.issueToken(userId).token();

        AppCharacter ch10 = new AppCharacter();
        ch10.setStAvatarUrl("test_10_mismatch.png");
        ch10.setName("C10");
        appCharacterMapper.insert(ch10);
        AppCharacter ch11 = new AppCharacter();
        ch11.setStAvatarUrl("test_11_mismatch.png");
        ch11.setName("C11");
        appCharacterMapper.insert(ch11);

        CreateConversationRequest req1 = new CreateConversationRequest();
        req1.setIdempotencyKey("idem-2");
        req1.setCharacterId(ch10.getId());
        conversationService.createOrEnsure(req1, token);

        CreateConversationRequest req2 = new CreateConversationRequest();
        req2.setIdempotencyKey("idem-2");
        req2.setCharacterId(ch11.getId());

        // then
        assertThatThrownBy(() -> conversationService.createOrEnsure(req2, token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话角色不一致")
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                });
    }
}

