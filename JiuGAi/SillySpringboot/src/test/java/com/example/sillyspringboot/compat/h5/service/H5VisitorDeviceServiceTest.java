package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.compat.h5.entity.AppH5VisitorDevice;
import com.example.sillyspringboot.compat.h5.mapper.AppH5VisitorDeviceMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import com.example.sillyspringboot.config.ApiRateLimitProperties;
import com.example.sillyspringboot.config.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5VisitorDeviceServiceTest {

    @Test
    void missingDeviceTokenIssuesTransientTokenWithoutInsertingOrTrustingClientUid() {
        Fixture fixture = fixture(null);
        MockHttpServletRequest request = request("h5u_42", null);

        H5VisitorDeviceService.DeviceTouchContext context = fixture.service.resolveOrIssue(request);

        assertThat(context.deviceId()).isNull();
        assertThat(context.deviceToken()).startsWith("dv_");
        assertThat(context.created()).isFalse();
        verify(fixture.deviceMapper, never()).insert(any());
        verify(fixture.deviceMapper, never()).findByDeviceToken(any());
    }

    @Test
    void unknownAnonymousDeviceTokenDoesNotInsertDeviceRow() {
        Fixture fixture = fixture(null);
        MockHttpServletRequest request = request("legacy-client", "dv_attacker_chosen");

        H5VisitorDeviceService.DeviceTouchContext context = fixture.service.resolveOrIssue(request);

        assertThat(context.deviceId()).isNull();
        assertThat(context.deviceToken()).startsWith("dv_").isNotEqualTo("dv_attacker_chosen");
        verify(fixture.deviceMapper).findByDeviceToken("dv_attacker_chosen");
        verify(fixture.deviceMapper, never()).insert(any());
    }

    @Test
    void authenticatedRequestPersistsDeviceWithTrustedUserBinding() {
        AppUser authenticatedUser = new AppUser();
        authenticatedUser.setId(42L);
        Fixture fixture = fixture(authenticatedUser);
        MockHttpServletRequest request = request("h5u_42", "dv_from_client");
        AppH5VisitorDevice persisted = new AppH5VisitorDevice();
        persisted.setId(9L);
        persisted.setDeviceToken("dv_from_client");
        persisted.setTrustedUserId(42L);
        when(fixture.deviceMapper.findByDeviceToken("dv_from_client"))
                .thenReturn(null, persisted);

        H5VisitorDeviceService.DeviceTouchContext context = fixture.service.resolveOrIssue(request);

        ArgumentCaptor<AppH5VisitorDevice> rowCaptor = ArgumentCaptor.forClass(AppH5VisitorDevice.class);
        verify(fixture.deviceMapper).insert(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getTrustedUserId()).isEqualTo(42L);
        assertThat(rowCaptor.getValue().getFirstUserId()).isEqualTo(42L);
        assertThat(context.deviceId()).isEqualTo(9L);
        assertThat(context.created()).isTrue();
    }

    @Test
    void anonymousChatAttemptIncrementsTheDeviceAndWritesAnAuditEvent() {
        Fixture fixture = fixture(null);
        MockHttpServletRequest request = request("legacy-client", "dv_known");
        request.setRequestURI("/api/v1/tavern/chat/stream");
        AppH5VisitorDevice persisted = new AppH5VisitorDevice();
        persisted.setId(19L);
        persisted.setDeviceToken("dv_known");
        when(fixture.deviceMapper.findByDeviceToken("dv_known")).thenReturn(persisted);

        fixture.service.recordAnonymousAttempt(
                request,
                H5VisitorDeviceService.AnonymousAction.CHAT,
                "legacy-client"
        );

        verify(fixture.deviceMapper).incrementAnonymousChatAttemptCount(19L);
        verify(fixture.securityEvents).insert(
                eq(19L),
                eq("ANONYMOUS_CHAT_BLOCKED"),
                eq("legacy-client"),
                isNull(),
                eq("203.0.113.8"),
                anyString(),
                eq("/api/v1/tavern/chat/stream"),
                eq("anonymous chat blocked")
        );
    }

    private static Fixture fixture(AppUser authenticatedUser) {
        AppH5VisitorDeviceMapper deviceMapper = mock(AppH5VisitorDeviceMapper.class);
        AppH5SecurityEventMapper securityEvents = mock(AppH5SecurityEventMapper.class);
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        when(h5Auth.resolveAuthenticatedRequestUser(any(MockHttpServletRequest.class))).thenReturn(authenticatedUser);
        return new Fixture(
                new H5VisitorDeviceService(
                        deviceMapper,
                        h5Auth,
                        securityEvents,
                        new ClientIpResolver(new ApiRateLimitProperties())
                ),
                deviceMapper,
                securityEvents
        );
    }

    private static MockHttpServletRequest request(String clientUid, String deviceToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("clientUid", clientUid);
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("User-Agent", "test-agent");
        if (deviceToken != null) {
            request.addHeader(H5VisitorDeviceService.DEVICE_TOKEN_HEADER, deviceToken);
        }
        return request;
    }

    private record Fixture(
            H5VisitorDeviceService service,
            AppH5VisitorDeviceMapper deviceMapper,
            AppH5SecurityEventMapper securityEvents
    ) {}
}
