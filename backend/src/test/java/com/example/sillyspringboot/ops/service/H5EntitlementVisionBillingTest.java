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
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5EntitlementVisionBillingTest {

    private final AppH5UserProfileExtMapper profileMapper = mock(AppH5UserProfileExtMapper.class);
    private final EntitlementPolicyService policyService = mock(EntitlementPolicyService.class);
    private final WalletLedgerService wallet = mock(WalletLedgerService.class);
    private H5EntitlementService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new H5EntitlementService(
                mock(H5ClientUidAuthService.class),
                mock(AppTokenService.class),
                profileMapper,
                mock(H5MyCharacterMapper.class),
                mock(AppCharacterMapper.class),
                policyService,
                mock(H5UserAiProviderService.class),
                mock(AppFeatureSettingsService.class),
                mock(EntitlementAuditLogService.class),
                wallet,
                mock(AppCheckinActivityMapper.class)
        );
        user = new AppUser();
        user.setId(7L);
        AppH5UserProfileExt profile = new AppH5UserProfileExt();
        profile.setUserId(7L);
        profile.setScore(100);
        profile.setGoldCoin(100);
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(profile);
    }

    @Test
    void freeVisionDoesNotCreateWalletLedger() {
        when(policyService.getPolicy()).thenReturn(new EntitlementPolicy());

        H5EntitlementService.AccessTicket ticket = service.guardVision(user, "vision_request_001");

        assertThat(ticket.usesWallet()).isFalse();
        assertThat(service.reserveVisionCharge(ticket)).isFalse();
        verify(wallet, never()).consumeDiamonds(anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void duplicateRequestOnlyRefundsChargeCreatedByCurrentAttempt() {
        EntitlementPolicy policy = new EntitlementPolicy();
        policy.setVisionScoreCost(3);
        policy.setVisionGoldCost(2);
        when(policyService.getPolicy()).thenReturn(policy);
        when(wallet.consumeDiamonds(anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(true, false);

        H5EntitlementService.AccessTicket first = service.guardVision(user, "vision_request_002");
        boolean firstCreated = service.reserveVisionCharge(first);
        H5EntitlementService.AccessTicket duplicate = service.guardVision(user, "vision_request_002");
        boolean duplicateCreated = service.reserveVisionCharge(duplicate);

        assertThat(first.consumeBizRef()).isEqualTo(duplicate.consumeBizRef());
        assertThat(firstCreated).isTrue();
        assertThat(duplicateCreated).isFalse();
        service.refundVisionCharge(duplicate, duplicateCreated);
        verify(wallet, never()).refundConsume(anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString());

        service.refundVisionCharge(first, firstCreated);
        verify(wallet).refundConsume(
                7L, 3, 2,
                WalletLedgerService.BIZ_VISION_REFUND,
                "refund:" + first.consumeBizRef(),
                "官方识图失败退回"
        );
    }
}
