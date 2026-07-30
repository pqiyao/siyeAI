package com.example.sillyspringboot.config;

import com.example.sillyspringboot.compat.h5.mapper.AppH5VisitorDeviceMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRateLimitFilterTest {

    @Test
    void rotatingUnverifiedDeviceTokensCannotResetStableNetworkLimit() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(2);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties, null);

        MockHttpServletResponse first = run(filter, "dv_one");
        MockHttpServletResponse second = run(filter, "dv_two");
        MockHttpServletResponse third = run(filter, "dv_three");

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(third.getContentAsString()).contains("请求过于频繁");
    }

    @Test
    void verifiedDeviceAttributesAreNotCountedByNetworkFilter() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(1);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties, null);

        MockHttpServletResponse first = runVerified(filter, "dv_verified", 1L);
        MockHttpServletRequest secondRequest = baseRequest("different-agent");
        secondRequest.setRemoteAddr("203.0.113.10");
        secondRequest.setAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_TOKEN, "dv_verified");
        secondRequest.setAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_ID, 1L);
        MockHttpServletResponse second = execute(filter, secondRequest);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
    }

    @Test
    void untrustedRemoteCannotRotateSpoofedForwardingHeaders() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(1);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties, null);
        MockHttpServletRequest firstRequest = baseRequest();
        firstRequest.addHeader("X-Real-IP", "198.51.100.10");
        firstRequest.addHeader("X-Forwarded-For", "192.0.2.1, 198.51.100.10");
        MockHttpServletRequest secondRequest = baseRequest();
        secondRequest.addHeader("X-Real-IP", "198.51.100.11");
        secondRequest.addHeader("X-Forwarded-For", "192.0.2.2, 198.51.100.10");

        MockHttpServletResponse first = execute(filter, firstRequest);
        MockHttpServletResponse second = execute(filter, secondRequest);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void stableLimitRunsBeforeAnyVisitorDeviceLookup() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(1);
        properties.setMaxRequestsPerIpWindow(1);
        ClientIpResolver clientIpResolver = new ClientIpResolver(properties);
        ApiRateLimitFilter rateLimitFilter = new ApiRateLimitFilter(
                properties,
                new ApiRateLimitCounterStore(null),
                null,
                clientIpResolver
        );

        AppH5VisitorDeviceMapper deviceMapper = mock(AppH5VisitorDeviceMapper.class);
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        H5VisitorDeviceFilter deviceFilter = new H5VisitorDeviceFilter(new H5VisitorDeviceService(
                deviceMapper,
                h5Auth,
                null,
                clientIpResolver
        ));

        MockHttpServletResponse first = executeInConfiguredOrder(rateLimitFilter, deviceFilter, requestWithToken("dv_first"));
        MockHttpServletResponse second = executeInConfiguredOrder(rateLimitFilter, deviceFilter, requestWithToken("dv_second"));

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        verify(deviceMapper, times(1)).findByDeviceToken(anyString());
    }

    @Test
    void rotatingUserAgentsCannotResetIpLimit() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(100);
        properties.setMaxRequestsPerIpWindow(2);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties, null);

        MockHttpServletRequest firstRequest = baseRequest("agent-one");
        MockHttpServletRequest secondRequest = baseRequest("agent-two");
        MockHttpServletRequest thirdRequest = baseRequest("agent-three");

        assertThat(execute(filter, firstRequest).getStatus()).isEqualTo(200);
        assertThat(execute(filter, secondRequest).getStatus()).isEqualTo(200);
        assertThat(execute(filter, thirdRequest).getStatus()).isEqualTo(429);
    }

    @Test
    void networkLimitWritesOneClearlyScopedEventWithoutDeviceIdentity() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(1);
        properties.setMaxRequestsPerIpWindow(100);
        AppH5SecurityEventMapper securityEvents = mock(AppH5SecurityEventMapper.class);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                properties,
                new ApiRateLimitCounterStore(null),
                securityEvents,
                new ClientIpResolver(properties)
        );

        assertThat(execute(filter, baseRequest()).getStatus()).isEqualTo(200);
        assertThat(execute(filter, baseRequest()).getStatus()).isEqualTo(429);
        assertThat(execute(filter, baseRequest()).getStatus()).isEqualTo(429);

        verify(securityEvents, times(1)).insert(
                isNull(),
                eq("RATE_LIMIT_HIT"),
                eq(""),
                isNull(),
                eq("203.0.113.9"),
                anyString(),
                eq("/api/v1/app/me/stats"),
                eq("scope=ip+ua;method=GET")
        );
    }

    @Test
    void networkLimitLinksARecognizedDeviceWithoutRunningTheFullDeviceFilter() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(1);
        properties.setMaxRequestsPerIpWindow(100);
        AppH5SecurityEventMapper securityEvents = mock(AppH5SecurityEventMapper.class);
        H5VisitorDeviceService visitorDeviceService = mock(H5VisitorDeviceService.class);
        when(visitorDeviceService.resolvePersistedDeviceId(org.mockito.ArgumentMatchers.any(HttpServletRequest.class)))
                .thenReturn(77L);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                properties,
                new ApiRateLimitCounterStore(null),
                securityEvents,
                new ClientIpResolver(properties),
                visitorDeviceService
        );

        assertThat(execute(filter, requestWithToken("dv_known")).getStatus()).isEqualTo(200);
        assertThat(execute(filter, requestWithToken("dv_known")).getStatus()).isEqualTo(429);

        verify(securityEvents).insert(
                eq(77L),
                eq("RATE_LIMIT_HIT"),
                eq(""),
                isNull(),
                eq("203.0.113.9"),
                anyString(),
                eq("/api/v1/app/me/stats"),
                eq("scope=ip+ua;method=GET")
        );
    }

    @Test
    void legacyLoginIsCoveredByStableNetworkLimit() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(1);
        properties.setMaxRequestsPerIpWindow(100);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties, null);

        MockHttpServletRequest firstRequest = legacyRequest("/api/index/emslogin");
        MockHttpServletRequest secondRequest = legacyRequest("/api/index/emslogin");

        assertThat(execute(filter, firstRequest).getStatus()).isEqualTo(200);
        assertThat(execute(filter, secondRequest).getStatus()).isEqualTo(429);
    }

    @Test
    void persistedLegacyDeviceIsCoveredByDeviceLimit() throws Exception {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(1);
        properties.setMaxRequestsPerIpWindow(100);
        ApiRateLimitCounterStore counterStore = new ApiRateLimitCounterStore(null);
        H5VisitorDeviceService visitorDeviceService = mock(H5VisitorDeviceService.class);
        when(visitorDeviceService.resolveOrIssue(org.mockito.ArgumentMatchers.any(HttpServletRequest.class)))
                .thenReturn(new H5VisitorDeviceService.DeviceTouchContext(9L, "dv_verified", false));
        H5VisitorDeviceFilter deviceFilter = new H5VisitorDeviceFilter(visitorDeviceService);
        VerifiedDeviceRateLimitFilter verifiedFilter = new VerifiedDeviceRateLimitFilter(
                properties,
                counterStore,
                null,
                new ClientIpResolver(properties)
        );

        assertThat(executeDeviceThenVerified(deviceFilter, verifiedFilter, legacyRequest("/api/user/user_info")).getStatus())
                .isEqualTo(200);
        assertThat(executeDeviceThenVerified(deviceFilter, verifiedFilter, legacyRequest("/api/user/user_info")).getStatus())
                .isEqualTo(429);
    }

    private static MockHttpServletResponse run(ApiRateLimitFilter filter, String rawDeviceToken) throws Exception {
        return execute(filter, requestWithToken(rawDeviceToken));
    }

    private static MockHttpServletResponse runVerified(
            ApiRateLimitFilter filter,
            String deviceToken,
            long deviceId
    ) throws Exception {
        MockHttpServletRequest request = baseRequest();
        request.setAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_TOKEN, deviceToken);
        request.setAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_ID, deviceId);
        return execute(filter, request);
    }

    private static MockHttpServletRequest baseRequest() {
        return baseRequest("stable-test-agent");
    }

    private static MockHttpServletRequest baseRequest(String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/app/me/stats");
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("User-Agent", userAgent);
        return request;
    }

    private static MockHttpServletRequest requestWithToken(String rawDeviceToken) {
        MockHttpServletRequest request = baseRequest();
        request.addHeader(H5VisitorDeviceService.DEVICE_TOKEN_HEADER, rawDeviceToken);
        return request;
    }

    private static MockHttpServletRequest legacyRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.20");
        request.addHeader("User-Agent", "legacy-test-agent");
        return request;
    }

    private static MockHttpServletResponse executeInConfiguredOrder(
            ApiRateLimitFilter rateLimitFilter,
            H5VisitorDeviceFilter deviceFilter,
            MockHttpServletRequest request
    ) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                deviceFilter.doFilter(servletRequest, servletResponse, new MockFilterChain())
        );
        return response;
    }

    private static MockHttpServletResponse execute(
            ApiRateLimitFilter filter,
            MockHttpServletRequest request
    ) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletResponse executeDeviceThenVerified(
            H5VisitorDeviceFilter deviceFilter,
            VerifiedDeviceRateLimitFilter verifiedFilter,
            MockHttpServletRequest request
    ) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        deviceFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                verifiedFilter.doFilter(servletRequest, servletResponse, new MockFilterChain())
        );
        return response;
    }
}
