package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppUserTtsVoice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppUserTtsVoiceRecoveryMapperIntegrationTest {

    @Autowired
    private AppUserTtsVoiceMapper voiceMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleProvisioningIsFailedAndCannotBeCompletedAfterRecovery() {
        long userId = 9_900_001L;
        LocalDateTime now = LocalDateTime.now();
        insertVoice(userId, "stale-request", "PROVISIONING", now.minusMinutes(5));
        insertVoice(userId, "active-request", "PROVISIONING", now.minusMinutes(1));

        int recovered = voiceMapper.failAllStaleProvisioning(now.minusMinutes(3), "创建任务已超时");

        assertThat(recovered).isEqualTo(1);
        AppUserTtsVoice stale = voiceMapper.findByUserIdAndRequestId(userId, "stale-request");
        AppUserTtsVoice active = voiceMapper.findByUserIdAndRequestId(userId, "active-request");
        assertThat(stale.getStatus()).isEqualTo("FAILED");
        assertThat(stale.getLastError()).isEqualTo("创建任务已超时");
        assertThat(active.getStatus()).isEqualTo("PROVISIONING");

        stale.setVoiceUri("speech:late-result");
        stale.setModelName("late-model");
        stale.setConfigFingerprint("late-fingerprint");
        assertThat(voiceMapper.completeProvisioning(stale)).isZero();
        assertThat(voiceMapper.findById(stale.getId()).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void adminFinishOnlyChangesActiveProvisioningTask() {
        long userId = 9_900_002L;
        insertVoice(userId, "manual-request", "PENDING", LocalDateTime.now());
        AppUserTtsVoice voice = voiceMapper.findByUserIdAndRequestId(userId, "manual-request");

        assertThat(voiceMapper.failProvisioningById(voice.getId(), "管理员结束")).isEqualTo(1);
        assertThat(voiceMapper.failProvisioningById(voice.getId(), "重复结束")).isZero();
        assertThat(voiceMapper.findById(voice.getId()).getStatus()).isEqualTo("FAILED");
    }

    private void insertVoice(long userId, String requestId, String status, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
                        INSERT INTO app_user_tts_voice
                        (user_id, request_id, display_name, provider_source, model_name, config_fingerprint,
                         voice_uri, status, last_error, disabled, created_at, updated_at)
                        VALUES (?, ?, ?, 'siliconflow', '', '', '', ?, '', FALSE, ?, ?)
                        """,
                userId, requestId, requestId, status,
                Timestamp.valueOf(updatedAt), Timestamp.valueOf(updatedAt));
    }
}
