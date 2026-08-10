package com.example.sillyspringboot.admin.security;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.config.ApiRateLimitProperties;
import com.example.sillyspringboot.config.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLoginAttemptServiceTest {

    @Test
    void blocksAfterConfiguredFailuresAndSuccessClearsState() {
        RuoYiAdminProperties properties = new RuoYiAdminProperties();
        properties.setLoginMaxAttempts(3);
        properties.setLoginWindowSeconds(300);
        properties.setLoginBlockSeconds(900);
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);
        AdminLoginAttemptService service = new AdminLoginAttemptService(properties, clock);

        assertThat(service.recordFailure("ip|admin").allowed()).isTrue();
        assertThat(service.recordFailure("ip|admin").allowed()).isTrue();
        AdminLoginAttemptService.AttemptDecision blocked = service.recordFailure("ip|admin");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(900);
        assertThat(service.check("ip|admin").allowed()).isFalse();

        service.recordSuccess("ip|admin");
        assertThat(service.check("ip|admin").allowed()).isTrue();
    }

    @Test
    void untrustedForwardingHeaderCannotRotateAdminLoginKey() {
        AdminLoginAttemptService service = new AdminLoginAttemptService(
                new RuoYiAdminProperties(),
                Clock.systemUTC()
        );
        MockHttpServletRequest first = request("203.0.113.8", "198.51.100.1");
        MockHttpServletRequest second = request("203.0.113.8", "198.51.100.2");

        assertThat(service.keyFor("Admin", first)).isEqualTo(service.keyFor("admin", second));
    }

    @Test
    void trustedProxyCanSupplyAdminLoginClientAddress() {
        ApiRateLimitProperties ipProperties = new ApiRateLimitProperties();
        ipProperties.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        AdminLoginAttemptService service = new AdminLoginAttemptService(
                new RuoYiAdminProperties(),
                Clock.systemUTC(),
                new ClientIpResolver(ipProperties)
        );
        MockHttpServletRequest request = request("10.0.0.8", "198.51.100.9");

        assertThat(service.keyFor("admin", request)).isEqualTo("198.51.100.9|user:admin");
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
