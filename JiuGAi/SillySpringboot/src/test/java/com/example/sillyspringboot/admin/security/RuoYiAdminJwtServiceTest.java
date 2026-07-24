package com.example.sillyspringboot.admin.security;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuoYiAdminJwtServiceTest {

    @Test
    void strongSecretCreatesVerifiableToken() {
        RuoYiAdminProperties properties = new RuoYiAdminProperties();
        properties.setJwtSecret("B2@jwt-3913b7d6d25c48d481e6f4a5450e0f70");
        properties.setJwtExpireHours(8);
        RuoYiAdminJwtService service = new RuoYiAdminJwtService(properties);

        String token = service.createToken("admin");

        assertThat(service.parseUsername(token)).isEqualTo("admin");
    }

    @Test
    void shortSecretIsRejectedInsteadOfBeingZeroPadded() {
        RuoYiAdminProperties properties = new RuoYiAdminProperties();
        properties.setJwtSecret("short");
        RuoYiAdminJwtService service = new RuoYiAdminJwtService(properties);

        assertThatThrownBy(() -> service.createToken("admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }
}
