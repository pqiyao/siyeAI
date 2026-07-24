package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5ClientUidAuthServiceTest {

    private static final long USER_ID = 42L;
    private static final String CLIENT_UID = "h5u_42";
    private static final String DEVICE_TOKEN = "dv_registered";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void anonymousClientUidCannotUseDeviceTokenAsIdentity() {
        Fixture fixture = fixture();
        bindRequest(null);

        assertThatThrownBy(() -> fixture.service.requireAuthenticatedTokenForClientUid(CLIENT_UID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(fixture.tokenService, never()).issueToken(USER_ID);
    }

    @Test
    void trustedDeviceTokenCannotBridgeARegisteredUserWithoutAnAppToken() {
        Fixture fixture = fixture();
        bindRequest(null);

        assertThatThrownBy(() -> fixture.service.requireAuthenticatedTokenForClientUid(CLIENT_UID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(fixture.tokenService, never()).issueToken(USER_ID);
    }

    @Test
    void authenticatedTokenCannotBePairedWithAnotherRegisteredUserClientUid() {
        Fixture fixture = fixture();
        when(fixture.tokenService.validateAndLoadUser("real-token")).thenReturn(user(7L));
        bindRequest("real-token");

        assertThatThrownBy(() -> fixture.service.requireAuthenticatedTokenForClientUid(CLIENT_UID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(fixture.tokenService, never()).issueToken(USER_ID);
    }

    private static void bindRequest(String appToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(H5VisitorDeviceService.DEVICE_TOKEN_HEADER, DEVICE_TOKEN);
        if (appToken != null) {
            request.addHeader("Authorization", "Bearer " + appToken);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Fixture fixture() {
        AppTokenService tokenService = mock(AppTokenService.class);
        return new Fixture(
                new H5ClientUidAuthService(tokenService),
                tokenService
        );
    }

    private static AppUser user(long id) {
        AppUser user = new AppUser();
        user.setId(id);
        return user;
    }

    private record Fixture(
            H5ClientUidAuthService service,
            AppTokenService tokenService
    ) {}
}
