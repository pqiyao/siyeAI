package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5VisitorTrialGuardServiceTest {

    @Test
    void spoofedRegisteredClientUidIsStillAnonymousWithoutAppToken() {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        H5VisitorDeviceService visitorDeviceService = mock(H5VisitorDeviceService.class);
        when(h5Auth.hasAuthenticatedRequestUser()).thenReturn(false);
        H5VisitorTrialGuardService service = new H5VisitorTrialGuardService(h5Auth, visitorDeviceService);

        assertThatThrownBy(() -> service.guardAnonymousCharacterCreation("h5u_42"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(visitorDeviceService).recordAnonymousAttempt(
                null,
                H5VisitorDeviceService.AnonymousAction.CHARACTER,
                "h5u_42"
        );
    }
}
