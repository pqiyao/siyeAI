package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.admin.service.AdminH5UserLifecycleService;
import com.example.sillyspringboot.auth.dto.AppAuthSessionResponse;
import com.example.sillyspringboot.auth.dto.AppUserDto;
import com.example.sillyspringboot.auth.dto.H5AccountRegisterRequest;
import com.example.sillyspringboot.auth.service.AppAuthService;
import com.example.sillyspringboot.compat.h5.service.H5LegacyUserCompatibilityService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5LegacyIndexControllerRegistrationSecurityTest {

    @Test
    @SuppressWarnings("deprecation")
    void emsRegisterNeverPassesAnonymousClientUidIntoRegistrationIdentitySelection() {
        AppAuthService authService = mock(AppAuthService.class);
        H5LegacyUserCompatibilityService legacyUserService = mock(H5LegacyUserCompatibilityService.class);
        AdminH5UserLifecycleService lifecycleService = mock(AdminH5UserLifecycleService.class);
        H5LegacyIndexController controller = new H5LegacyIndexController(
                authService,
                legacyUserService,
                lifecycleService
        );
        AppAuthSessionResponse session = new AppAuthSessionResponse(
                "new-token",
                123456L,
                new AppUserDto(99L, null, "new-account", "new-account", "", false)
        );
        when(authService.registerWithH5Account(any(H5AccountRegisterRequest.class))).thenReturn(session);
        when(legacyUserService.buildLegacyUserInfoByToken("new-token"))
                .thenReturn(Map.of("id", 99L, "client_uid", "h5u_99"));

        Map<String, Object> result = controller.emsRegister("new-account", "strong-pass", "guest-victim");

        assertThat(result.get("code")).isEqualTo(1);
        verify(authService).registerWithH5Account(any(H5AccountRegisterRequest.class));
        verify(authService, never()).registerWithH5Account(any(H5AccountRegisterRequest.class), anyString());
    }
}
