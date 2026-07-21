package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.conversation.dto.CreateConversationRequest;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
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
class ChatAuditServiceFailureLoggingTest {

    @Autowired private DataSource dataSource;
    @Autowired private AppUserMapper appUserMapper;
    @Autowired private AppTokenService tokenService;
    @Autowired private AppCharacterMapper characterMapper;
    @Autowired private AppConversationService conversationService;
    @Autowired private ChatAuditService auditService;
    @Autowired private AppGenerationTaskMapper taskMapper;

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
    void onFailed_shouldPersistDetailedReasonAndTraceId() {
        String token = issueToken(2100L, "u2100");
        long conversationId = createConversation(token, "Trace");
        ChatAuditService.AuditContext audit = auditService.onQueued(conversationId, "hello", "m-failure-1", token, "trace-ai-1");

        BusinessException exception = new BusinessException(
                ErrorCode.UPSTREAM_ERROR,
                "服务暂时不可用，请稍后重试",
                new IllegalStateException("st runtime generate http 503: {\"error\":\"model not found\",\"authorization\":\"Bearer secret-token\"}")
        );

        auditService.onFailed(audit.assistantMessageId(), audit.taskId(), exception, "trace-ai-1");

        AppGenerationTask task = taskMapper.findByConversationAndClientMessageId(conversationId, "m-failure-1");
        assertThat(task).isNotNull();
        assertThat(task.getErrorCode()).isEqualTo("UPSTREAM_ERROR");
        assertThat(task.getHttpStatus()).isEqualTo(503);
        assertThat(task.getTraceId()).isEqualTo("trace-ai-1");
        assertThat(task.getErrorMessage()).contains("UPSTREAM_ERROR");
        assertThat(task.getErrorMessage()).contains("st runtime generate http 503");
        assertThat(task.getErrorMessage()).contains("traceId=trace-ai-1");
        assertThat(task.getErrorMessage()).doesNotContain("secret-token");
    }

    @Test
    void onFailed_shouldMapValidationReasonToExistingLogColumn() {
        String token = issueToken(2200L, "u2200");
        long conversationId = createConversation(token, "Validate");
        ChatAuditService.AuditContext audit = auditService.onQueued(conversationId, "hello", "m-failure-2", token, "trace-ai-2");

        auditService.onFailed(
                audit.assistantMessageId(),
                audit.taskId(),
                ErrorCode.VALIDATION_FAILED,
                "trace-ai-2",
                "重新生成结果为空"
        );

        AppGenerationTask task = taskMapper.findByConversationAndClientMessageId(conversationId, "m-failure-2");
        assertThat(task).isNotNull();
        assertThat(task.getErrorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(task.getHttpStatus()).isEqualTo(400);
        assertThat(task.getErrorMessage()).contains("VALIDATION_FAILED");
        assertThat(task.getErrorMessage()).contains("重新生成结果为空");
        assertThat(task.getErrorMessage()).contains("traceId=trace-ai-2");
    }

    private String issueToken(long telegramUserId, String username) {
        AppUser user = new AppUser();
        user.setTelegramUserId(telegramUserId);
        user.setUsername(username);
        user.setFirstName("U");
        appUserMapper.insert(user);
        return tokenService.issueToken(user.getId()).token();
    }

    private long createConversation(String token, String characterName) {
        AppCharacter character = new AppCharacter();
        character.setStAvatarUrl(characterName.toLowerCase() + ".png");
        character.setName(characterName);
        characterMapper.insert(character);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setIdempotencyKey("idem-" + characterName);
        request.setCharacterId(character.getId());
        return conversationService.createOrEnsure(request, token).conversationId();
    }
}
