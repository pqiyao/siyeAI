package com.example.sillyspringboot.auth.service;

import com.example.sillyspringboot.auth.dto.AppAuthSessionResponse;
import com.example.sillyspringboot.auth.dto.H5AccountRegisterRequest;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.entity.AppUserIdentity;
import com.example.sillyspringboot.auth.mapper.AppUserIdentityMapper;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.telegram.TelegramWebAppInitDataValidator;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5ClientUid;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppAuthServiceRegistrationSecurityTest {

    @Test
    @SuppressWarnings("deprecation")
    void untrustedLegacyClientUidCannotSelectOrUpgradeExistingGuestUser() {
        TelegramWebAppInitDataValidator telegramValidator = mock(TelegramWebAppInitDataValidator.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppUserIdentityMapper identityMapper = mock(AppUserIdentityMapper.class);
        AppH5ClientUidMapper clientUidMapper = mock(AppH5ClientUidMapper.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        H5EntitlementService entitlementService = mock(H5EntitlementService.class);
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        AppAuthService service = new AppAuthService(
                telegramValidator,
                userMapper,
                identityMapper,
                clientUidMapper,
                tokenService,
                passwordEncoder,
                entitlementService,
                featureSettingsService
        );

        AppH5ClientUid victimMapping = new AppH5ClientUid();
        victimMapping.setClientUid("guest-victim");
        victimMapping.setUserId(7L);
        when(clientUidMapper.findByClientUid("guest-victim")).thenReturn(victimMapping);
        when(identityMapper.findByTypeAndKey("h5_account", "new-account")).thenReturn(null);
        when(passwordEncoder.encode("strong-pass")).thenReturn("{noop}strong-pass");
        doAnswer(invocation -> {
            AppUser inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        }).when(userMapper).insert(any(AppUser.class));
        when(entitlementService.ensureProfileExt(any(AppUser.class))).thenReturn(new AppH5UserProfileExt());
        when(tokenService.issueToken(99L)).thenReturn(new AppTokenService.TokenIssueResult("new-token", 123456L));

        H5AccountRegisterRequest request = new H5AccountRegisterRequest();
        request.setAccount("new-account");
        request.setPassword("strong-pass");
        AppAuthSessionResponse response = service.registerWithH5Account(request, "guest-victim");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getId()).isEqualTo(99L);
        assertThat(response.user().appUserId()).isEqualTo(99L);

        ArgumentCaptor<AppUserIdentity> identityCaptor = ArgumentCaptor.forClass(AppUserIdentity.class);
        verify(identityMapper).insert(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getUserId()).isEqualTo(99L);
        verify(clientUidMapper, never()).findByClientUid(anyString());
        verify(clientUidMapper, never()).updateUserIdByClientUid(anyString(), anyLong());
        verify(userMapper, never()).findById(7L);
        verify(userMapper, never()).updateById(any(AppUser.class));
    }
}
