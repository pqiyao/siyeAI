package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ops.service.ChatPresetService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiV1TavernChatPresetControllerTest {

    @Test
    void malformedPresetIdIsRejectedInsteadOfSilentlyUnbindingConversation() {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        ChatPresetService presetService = mock(ChatPresetService.class);
        AppUser user = new AppUser();
        user.setId(10L);
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(tokenService.validateAndLoadUser("token-1")).thenReturn(user);
        ApiV1TavernChatPresetController controller = new ApiV1TavernChatPresetController(
                h5Auth, tokenService, presetService);

        assertThatThrownBy(() -> controller.bind(20L, Map.of(
                "clientUid", "client-1",
                "presetId", "not-a-number"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("presetId invalid");

        verify(presetService, never()).bindConversationPreset(10L, 20L, null);
    }

    @Test
    void explicitNullPresetIdStillUnbindsConversation() {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        ChatPresetService presetService = mock(ChatPresetService.class);
        AppUser user = new AppUser();
        user.setId(10L);
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(tokenService.validateAndLoadUser("token-1")).thenReturn(user);
        ApiV1TavernChatPresetController controller = new ApiV1TavernChatPresetController(
                h5Auth, tokenService, presetService);
        Map<String, Object> body = new HashMap<>();
        body.put("clientUid", "client-1");
        body.put("presetId", null);

        controller.bind(20L, body);

        verify(presetService).bindConversationPreset(10L, 20L, null);
    }

    @Test
    void missingPresetIdIsRejectedInsteadOfSilentlyUnbindingConversation() {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        ChatPresetService presetService = mock(ChatPresetService.class);
        AppUser user = new AppUser();
        user.setId(10L);
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(tokenService.validateAndLoadUser("token-1")).thenReturn(user);
        ApiV1TavernChatPresetController controller = new ApiV1TavernChatPresetController(
                h5Auth, tokenService, presetService);

        assertThatThrownBy(() -> controller.bind(20L, Map.of("clientUid", "client-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("presetId missing");

        verify(presetService, never()).bindConversationPreset(10L, 20L, null);
    }
}
