package com.example.sillyspringboot.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    @Test
    void ignoresForwardingHeadersFromUntrustedRemoteAddress() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = request("203.0.113.20");
        request.addHeader("X-Real-IP", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "192.0.2.55, 198.51.100.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.20");
    }

    @Test
    void takesRightmostUntrustedHopFromTrustedProxyChain() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8", "172.16.0.0/12"));
        MockHttpServletRequest request = request("10.0.0.9");
        request.addHeader("X-Real-IP", "198.51.100.8");
        request.addHeader("X-Forwarded-For", "192.0.2.77, 198.51.100.8, 172.18.0.4");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.8");
    }

    @Test
    void fallsBackToRealIpOnlyForTrustedProxyWithoutForwardedChain() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = request("10.0.0.9");
        request.addHeader("X-Real-IP", "198.51.100.44");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.44");
    }

    @Test
    void exactProxyAddressIsTrustedButAdjacentAddressIsNot() {
        ClientIpResolver resolver = resolver(List.of("172.18.0.10/32"));
        MockHttpServletRequest trusted = request("172.18.0.10");
        trusted.addHeader("X-Forwarded-For", "198.51.100.50");
        MockHttpServletRequest adjacent = request("172.18.0.11");
        adjacent.addHeader("X-Forwarded-For", "198.51.100.51");

        assertThat(resolver.resolve(trusted)).isEqualTo("198.51.100.50");
        assertThat(resolver.resolve(adjacent)).isEqualTo("172.18.0.11");
    }

    @Test
    void defaultConfigurationTrustsNoForwardingProxy() {
        ClientIpResolver resolver = resolver(List.of());
        MockHttpServletRequest request = request("172.18.0.10");
        request.addHeader("X-Forwarded-For", "198.51.100.60");

        assertThat(resolver.resolve(request)).isEqualTo("172.18.0.10");
    }

    @Test
    void invalidTrustedProxyConfigurationFailsClosedAtStartup() {
        assertThatThrownBy(() -> resolver(List.of("not-a-cidr")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid trusted proxy CIDR");
    }

    private static ClientIpResolver resolver(List<String> trustedProxyCidrs) {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setTrustedProxyCidrs(trustedProxyCidrs);
        return new ClientIpResolver(properties);
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
