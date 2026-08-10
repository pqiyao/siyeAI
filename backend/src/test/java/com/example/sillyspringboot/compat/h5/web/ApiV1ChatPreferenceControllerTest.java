package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ChatPreferenceService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiV1ChatPreferenceControllerTest {

    @Test
    void derivesPreferenceOwnerFromAuthenticatedToken() {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        H5ChatPreferenceService preferenceService = mock(H5ChatPreferenceService.class);
        ApiV1ChatPreferenceController controller = new ApiV1ChatPreferenceController(
                h5Auth,
                tokenService,
                preferenceService
        );
        AppUser authenticated = new AppUser();
        authenticated.setId(42L);
        when(h5Auth.requireAuthenticatedTokenForClientUid("h5u_42")).thenReturn("real-token");
        when(tokenService.validateAndLoadUser("real-token")).thenReturn(authenticated);
        when(preferenceService.load(42L, 9L)).thenReturn(Map.of("owner", 42L));

        ApiV1Result<Map<String, Object>> result = controller.get("h5u_42", 9L);

        assertThat(result.code()).isEqualTo(1);
        assertThat(result.data()).containsEntry("owner", 42L);
        verify(preferenceService).load(42L, 9L);
    }
}
