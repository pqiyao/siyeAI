package com.example.sillyspringboot.config;

import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifiedDeviceRateLimitFilterTest {

    @Test
    void verifiedDeviceIsLimitedAfterDeviceResolutionAcrossChangingNetworks() throws Exception {
        ApiRateLimitProperties properties = properties(1);
        ApiRateLimitCounterStore counters = new ApiRateLimitCounterStore(null);
        ClientIpResolver clientIpResolver = new ClientIpResolver(properties);
        AppH5SecurityEventMapper securityEvents = mock(AppH5SecurityEventMapper.class);
        H5VisitorDeviceService deviceService = mock(H5VisitorDeviceService.class);
        when(deviceService.resolveOrIssue(any())).thenReturn(
                new H5VisitorDeviceService.DeviceTouchContext(42L, "dv_verified", false)
        );

        ApiRateLimitFilter networkFilter = new ApiRateLimitFilter(
                properties,
                counters,
                securityEvents,
                clientIpResolver
        );
        H5VisitorDeviceFilter deviceFilter = new H5VisitorDeviceFilter(deviceService);
        VerifiedDeviceRateLimitFilter verifiedDeviceFilter = new VerifiedDeviceRateLimitFilter(
                properties,
                counters,
                securityEvents,
                clientIpResolver
        );

        MockHttpServletResponse first = executeChain(
                networkFilter,
                deviceFilter,
                verifiedDeviceFilter,
                request("203.0.113.10", "agent-one")
        );
        MockHttpServletResponse second = executeChain(
                networkFilter,
                deviceFilter,
                verifiedDeviceFilter,
                request("203.0.113.11", "agent-two")
        );
        MockHttpServletResponse third = executeChain(
                networkFilter,
                deviceFilter,
                verifiedDeviceFilter,
                request("203.0.113.11", "agent-two")
        );

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(second.getContentAsString()).contains("请求过于频繁");
        verify(securityEvents, times(1)).insert(
                eq(42L),
                eq("RATE_LIMIT_HIT"),
                eq(""),
                isNull(),
                eq("203.0.113.11"),
                anyString(),
                eq("/api/v1/app/me/stats"),
                eq("scope=device;method=GET")
        );
    }

    @Test
    void anonymousIssuedTokenIsNotTreatedAsVerifiedDevice() throws Exception {
        ApiRateLimitProperties properties = properties(1);
        ApiRateLimitCounterStore counters = new ApiRateLimitCounterStore(null);
        ClientIpResolver clientIpResolver = new ClientIpResolver(properties);
        AppH5SecurityEventMapper securityEvents = mock(AppH5SecurityEventMapper.class);
        H5VisitorDeviceService deviceService = mock(H5VisitorDeviceService.class);
        when(deviceService.resolveOrIssue(any())).thenReturn(
                new H5VisitorDeviceService.DeviceTouchContext(null, "dv_bootstrap", false)
        );

        H5VisitorDeviceFilter deviceFilter = new H5VisitorDeviceFilter(deviceService);
        VerifiedDeviceRateLimitFilter verifiedDeviceFilter = new VerifiedDeviceRateLimitFilter(
                properties,
                counters,
                securityEvents,
                clientIpResolver
        );

        MockHttpServletRequest firstRequest = request("203.0.113.20", "agent-one");
        MockHttpServletResponse first = executeDeviceChain(deviceFilter, verifiedDeviceFilter, firstRequest);
        MockHttpServletRequest secondRequest = request("203.0.113.21", "agent-two");
        MockHttpServletResponse second = executeDeviceChain(deviceFilter, verifiedDeviceFilter, secondRequest);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(first.getHeader(H5VisitorDeviceService.DEVICE_TOKEN_HEADER)).isEqualTo("dv_bootstrap");
        assertThat(firstRequest.getAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_ID)).isNull();
        assertThat(firstRequest.getAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_TOKEN)).isNull();
        verify(securityEvents, never()).insert(
                any(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString()
        );
    }

    private static ApiRateLimitProperties properties(int maxRequestsPerWindow) {
        ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setMaxRequestsPerWindow(maxRequestsPerWindow);
        properties.setMaxRequestsPerIpWindow(100);
        return properties;
    }

    private static MockHttpServletRequest request(String remoteAddress, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/app/me/stats");
        request.setRemoteAddr(remoteAddress);
        request.addHeader("User-Agent", userAgent);
        return request;
    }

    private static MockHttpServletResponse executeChain(
            ApiRateLimitFilter networkFilter,
            H5VisitorDeviceFilter deviceFilter,
            VerifiedDeviceRateLimitFilter verifiedDeviceFilter,
            MockHttpServletRequest request
    ) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        networkFilter.doFilter(request, response, (networkRequest, networkResponse) ->
                deviceFilter.doFilter(networkRequest, networkResponse, (deviceRequest, deviceResponse) ->
                        verifiedDeviceFilter.doFilter(deviceRequest, deviceResponse, new MockFilterChain())
                )
        );
        return response;
    }

    private static MockHttpServletResponse executeDeviceChain(
            H5VisitorDeviceFilter deviceFilter,
            VerifiedDeviceRateLimitFilter verifiedDeviceFilter,
            MockHttpServletRequest request
    ) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        deviceFilter.doFilter(request, response, (deviceRequest, deviceResponse) ->
                verifiedDeviceFilter.doFilter(deviceRequest, deviceResponse, new MockFilterChain())
        );
        return response;
    }
}
