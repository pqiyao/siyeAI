package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.service.WalletLedgerService;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.checkin.mapper.AppCheckinActivityMapper;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class H5EntitlementCharacterCreationLockTest {

    @Test
    void locksUserBeforeCountingActiveCharacters() {
        H5MyCharacterMapper characterMapper = mock(H5MyCharacterMapper.class);
        AppH5UserProfileExtMapper profileMapper = mock(AppH5UserProfileExtMapper.class);
        EntitlementPolicyService policyService = mock(EntitlementPolicyService.class);
        AppFeatureSettingsService settingsService = mock(AppFeatureSettingsService.class);
        AppFeatureSettings settings = new AppFeatureSettings();
        settings.setUserCharacterCreationEnabled(true);
        when(settingsService.getSettings()).thenReturn(settings);

        AppUser user = new AppUser();
        user.setId(7L);
        AppH5UserProfileExt profile = new AppH5UserProfileExt();
        profile.setUserId(7L);
        profile.setCharacterCreateAllowed(1);
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(characterMapper.lockOwnerUser(7L)).thenReturn(7L);
        when(characterMapper.countMineActive(7L)).thenReturn(3);
        EntitlementPolicy policy = mock(EntitlementPolicy.class);
        when(policyService.getPolicy()).thenReturn(policy);
        when(policyService.characterCreateLimitFor(policy, 0)).thenReturn(20);

        H5EntitlementService service = new H5EntitlementService(
                mock(H5ClientUidAuthService.class),
                mock(AppTokenService.class),
                profileMapper,
                characterMapper,
                mock(AppCharacterMapper.class),
                policyService,
                mock(H5UserAiProviderService.class),
                settingsService,
                mock(EntitlementAuditLogService.class),
                mock(WalletLedgerService.class),
                mock(AppCheckinActivityMapper.class)
        );

        service.requireCharacterCreationAccess(user, 1);

        InOrder order = inOrder(characterMapper);
        order.verify(characterMapper).lockOwnerUser(7L);
        order.verify(characterMapper).countMineActive(7L);
    }
}
