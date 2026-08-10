package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ai.entity.ChatGenerationContext;
import com.example.sillyspringboot.ai.mapper.AiChatModelMapper;
import com.example.sillyspringboot.ai.service.AiChatModelService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.service.WalletLedgerService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5EntitlementChatEitherCurrencyTest {

    private final H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
    private final AppTokenService tokenService = mock(AppTokenService.class);
    private final AppH5UserProfileExtMapper profileMapper = mock(AppH5UserProfileExtMapper.class);
    private final AppCharacterMapper characterMapper = mock(AppCharacterMapper.class);
    private final EntitlementPolicyService policyService = mock(EntitlementPolicyService.class);
    private final H5UserAiProviderService providerService = mock(H5UserAiProviderService.class);
    private final WalletLedgerService wallet = mock(WalletLedgerService.class);
    private final AiChatModelMapper chatModelMapper = mock(AiChatModelMapper.class);
    private H5EntitlementService service;
    private AppH5UserProfileExt ext;

    @BeforeEach
    void setUp() {
        service = new H5EntitlementService(
                h5Auth,
                tokenService,
                profileMapper,
                mock(H5MyCharacterMapper.class),
                characterMapper,
                policyService,
                providerService,
                mock(AppFeatureSettingsService.class),
                mock(EntitlementAuditLogService.class),
                wallet,
                mock(AppCheckinActivityMapper.class)
        );
        ReflectionTestUtils.setField(service, "chatModelMapper", chatModelMapper);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(7L);
        when(h5Auth.requireAuthenticatedTokenForClientUid("client")).thenReturn("token");
        when(tokenService.validateAndLoadUser("token")).thenReturn(user);

        AppCharacter character = mock(AppCharacter.class);
        when(characterMapper.findById(3L)).thenReturn(character);
        when(character.getOwnerUserId()).thenReturn(null);
        when(character.getPrivateCard()).thenReturn(false);
        when(character.getClientVisible()).thenReturn(true);
        when(character.getReviewStatus()).thenReturn(CharacterReviewStatus.APPROVED);

        ext = new AppH5UserProfileExt();
        ext.setUserId(7L);
        ext.setNickname("user");
        ext.setCharacterCreateAllowed(0);
        ext.setUsageResetDate(LocalDate.now());
        ext.setDailyChatQuota(0);
        ext.setDailyChatBonus(0);
        ext.setDailyChatUsed(0);
        ext.setDailyByokChatUsed(0);
        when(profileMapper.findByUserId(7L)).thenReturn(ext);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(ext);
        when(policyService.refreshEffectiveQuota(ext)).thenReturn(false);
        when(policyService.canAccessVipCharacter(ext)).thenReturn(true);
        when(policyService.consumesChatQuota(EntitlementPolicyService.ChatQuotaAction.GENERATE)).thenReturn(true);
        when(policyService.consumesByokChatQuota(EntitlementPolicyService.ChatQuotaAction.GENERATE)).thenReturn(true);
        doAnswer(invocation -> {
            ChatGenerationContext context = invocation.getArgument(0);
            context.setId(41L);
            return 1;
        }).when(chatModelMapper).insertGenerationContext(any(ChatGenerationContext.class));
        when(chatModelMapper.updateGenerationReservation(
                anyLong(), anyString(), anyString(), anyInt(), anyInt())).thenReturn(1);
    }

    @Test
    void platformEitherCurrencyStoresOnlyActualGoldCharge() {
        AiChatModelService.ResolvedChatModel selection = model(
                AiChatModelService.SOURCE_SYSTEM, "QUOTA_THEN_DIAMOND_OR_GOLD");
        when(wallet.consumeDiamondOrGold(
                anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString()
        )).thenReturn(new WalletLedgerService.WalletChargeResult(true, 0, 100, "GOLD"));

        H5EntitlementService.AccessTicket ticket = service.guardChatModel(
                "client", 3L, EntitlementPolicyService.ChatQuotaAction.GENERATE,
                selection, "request-1", 9L);

        assertThat(ticket.scoreCost()).isZero();
        assertThat(ticket.goldCost()).isEqualTo(100);
        verify(chatModelMapper).updateGenerationReservation(41L, "RESERVED", ticket.consumeBizRef(), 0, 100);
        verify(wallet, never()).consumeDiamonds(
                anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void existingMixedModeStillChargesBothCurrencies() {
        AiChatModelService.ResolvedChatModel selection = model(
                AiChatModelService.SOURCE_SYSTEM, "QUOTA_THEN_MIXED");
        when(wallet.consumeDiamonds(anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        H5EntitlementService.AccessTicket ticket = service.guardChatModel(
                "client", 3L, EntitlementPolicyService.ChatQuotaAction.GENERATE,
                selection, "request-2", 9L);

        assertThat(ticket.scoreCost()).isEqualTo(10);
        assertThat(ticket.goldCost()).isEqualTo(100);
        verify(chatModelMapper).updateGenerationReservation(41L, "RESERVED", ticket.consumeBizRef(), 10, 100);
    }

    @Test
    void byokUsesSameEitherCurrencyReservation() {
        EntitlementPolicy policy = new EntitlementPolicy();
        policy.setOverQuotaBillingEnabled(true);
        policy.setChatWalletMode("DIAMOND_OR_GOLD");
        policy.setChatScoreCost(10);
        policy.setChatGoldCost(100);
        when(policyService.getPolicy()).thenReturn(policy);
        when(policyService.byokChatQuotaFor(policy, 0)).thenReturn(0);
        when(policyService.effectiveVipLevel(ext)).thenReturn(0);
        when(wallet.consumeDiamondOrGold(anyLong(), anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(new WalletLedgerService.WalletChargeResult(true, 10, 0, "DIAMOND"));

        H5EntitlementService.AccessTicket ticket = service.guardChat(
                "client", 3L, EntitlementPolicyService.ChatQuotaAction.GENERATE,
                "request-3", 9L, model(AiChatModelService.SOURCE_BYOK, "FREE"));

        assertThat(ticket.quotaBucket()).isEqualTo(H5EntitlementService.QuotaBucket.BYOK_CHAT);
        assertThat(ticket.scoreCost()).isEqualTo(10);
        assertThat(ticket.goldCost()).isZero();
        verify(chatModelMapper).updateGenerationReservation(41L, "RESERVED", ticket.consumeBizRef(), 10, 0);
    }

    private static AiChatModelService.ResolvedChatModel model(String source, String billingMode) {
        return new AiChatModelService.ResolvedChatModel(
                source, AiChatModelService.SOURCE_SYSTEM.equals(source) ? 2L : null,
                "test", "测试模型", AiChatModelService.SOURCE_BYOK.equals(source) ? 5L : null,
                "model", "chat", billingMode, 1, 10, 100, 1L
        );
    }
}
