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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5EntitlementImageReservationTest {

    private final H5ClientUidAuthService auth = mock(H5ClientUidAuthService.class);
    private final AppTokenService tokenService = mock(AppTokenService.class);
    private final AppH5UserProfileExtMapper profileMapper = mock(AppH5UserProfileExtMapper.class);
    private final EntitlementPolicyService policyService = mock(EntitlementPolicyService.class);
    private final AppFeatureSettingsService featureService = mock(AppFeatureSettingsService.class);
    private final WalletLedgerService wallet = mock(WalletLedgerService.class);
    private H5EntitlementService service;
    private AppH5UserProfileExt profile;
    private EntitlementPolicy policy;

    @BeforeEach
    void setUp() {
        service = new H5EntitlementService(
                auth,
                tokenService,
                profileMapper,
                mock(H5MyCharacterMapper.class),
                mock(AppCharacterMapper.class),
                policyService,
                mock(H5UserAiProviderService.class),
                featureService,
                mock(EntitlementAuditLogService.class),
                wallet,
                mock(AppCheckinActivityMapper.class)
        );
        AppUser user = new AppUser();
        user.setId(7L);
        when(auth.requireAuthenticatedTokenForClientUid("client")).thenReturn("token");
        when(tokenService.validateAndLoadUser("token")).thenReturn(user);

        profile = new AppH5UserProfileExt();
        profile.setUserId(7L);
        profile.setDailyImageQuota(2);
        profile.setDailyImageUsed(0);
        profile.setDailyImageBonus(0);
        profile.setUsageResetDate(LocalDate.now());
        profile.setScore(100);
        profile.setGoldCoin(100);
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(profile);

        AppFeatureSettings settings = new AppFeatureSettings();
        settings.setImageGenerationEnabled(true);
        when(featureService.getSettings()).thenReturn(settings);
        policy = new EntitlementPolicy();
        when(policyService.getPolicy()).thenReturn(policy);
        when(policyService.refreshEffectiveQuota(any())).thenReturn(false);
    }

    @Test
    void freeQuotaIsReservedBeforeCallAndReleasedOnFailure() {
        H5EntitlementService.AccessTicket ticket = service.guardImage("client", 1, 0L, "image_request_123");
        assertThat(profile.getDailyImageUsed()).isEqualTo(1);
        assertThat(ticket.consumesQuota()).isTrue();

        service.releaseImageReservation(ticket);
        assertThat(profile.getDailyImageUsed()).isZero();
        verify(profileMapper, atLeastOnce()).findByUserIdForUpdate(7L);
    }

    @Test
    void paidRetryUsesNextBillingReferenceAfterRefund() {
        profile.setDailyImageQuota(0);
        policy.setImageScoreCost(5);
        when(wallet.consumeDiamonds(anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        H5EntitlementService.AccessTicket first = service.guardImage("client", 1, 0L, "image_request_456");
        service.releaseImageReservation(first);
        when(wallet.hasLedgerEntry(WalletLedgerService.BIZ_IMAGE_CONSUME, first.consumeBizRef())).thenReturn(true);
        when(wallet.hasLedgerEntry(WalletLedgerService.BIZ_IMAGE_REFUND, "refund:" + first.consumeBizRef())).thenReturn(true);

        H5EntitlementService.AccessTicket retry = service.guardImage("client", 1, 0L, "image_request_456");
        assertThat(retry.consumeBizRef()).isEqualTo(first.consumeBizRef() + ":r1");
        assertThat(retry.walletChargeCreated()).isTrue();
    }
}
