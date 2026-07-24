package com.example.sillyspringboot.admin.security;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCaptchaServiceTest {

    @Test
    void challengeIsGifAndCanOnlyBeConsumedOnce() {
        AdminCaptchaService service = new AdminCaptchaService(new RuoYiAdminProperties());

        AdminCaptchaService.CaptchaChallenge challenge = service.createChallenge("A2B3C");
        byte[] image = Base64.getDecoder().decode(challenge.imageBase64());

        assertThat(new String(image, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("GIF8");
        assertThat(service.verifyAndConsume(challenge.uuid(), "a2b3c")).isTrue();
        assertThat(service.verifyAndConsume(challenge.uuid(), "A2B3C")).isFalse();
    }

    @Test
    void wrongAnswerConsumesChallenge() {
        AdminCaptchaService service = new AdminCaptchaService(new RuoYiAdminProperties());
        AdminCaptchaService.CaptchaChallenge challenge = service.createChallenge("ABCDE");

        assertThat(service.verifyAndConsume(challenge.uuid(), "WRONG")).isFalse();
        assertThat(service.verifyAndConsume(challenge.uuid(), "ABCDE")).isFalse();
    }
}
