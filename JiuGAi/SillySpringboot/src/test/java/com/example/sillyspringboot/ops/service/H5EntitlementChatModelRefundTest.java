package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ai.entity.ChatGenerationContext;
import com.example.sillyspringboot.ai.mapper.AiChatModelMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.service.WalletLedgerService;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.checkin.mapper.AppCheckinActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5EntitlementChatModelRefundTest {

    private final AiChatModelMapper chatModelMapper = mock(AiChatModelMapper.class);
    private final WalletLedgerService wallet = mock(WalletLedgerService.class);
    private H5EntitlementService service;

    @BeforeEach
    void setUp() {
        service = new H5EntitlementService(
                mock(H5ClientUidAuthService.class),
                mock(AppTokenService.class),
                mock(AppH5UserProfileExtMapper.class),
                mock(H5MyCharacterMapper.class),
                mock(AppCharacterMapper.class),
                mock(EntitlementPolicyService.class),
                mock(H5UserAiProviderService.class),
                mock(AppFeatureSettingsService.class),
                mock(EntitlementAuditLogService.class),
                wallet,
                mock(AppCheckinActivityMapper.class)
        );
        ReflectionTestUtils.setField(service, "chatModelMapper", chatModelMapper);
    }

    @Test
    void refundIsClaimedExactlyOnce() {
        when(chatModelMapper.claimGenerationRefund(41L)).thenReturn(1, 0);
        H5EntitlementService.AccessTicket ticket = ticket(false, false);

        service.refundFailedChat(ticket, false);
        service.refundFailedChat(ticket, false);

        verify(chatModelMapper, times(2)).claimGenerationRefund(41L);
        verify(chatModelMapper, times(1)).updateGenerationChargeStatus(41L, "REFUNDED", "chat-ref");
    }

    @Test
    void databaseFirstTokenGuardPreventsWalletRefund() {
        when(chatModelMapper.claimGenerationRefund(41L)).thenReturn(0);
        when(chatModelMapper.completeGenerationIfContentEmitted(41L)).thenReturn(1);
        H5EntitlementService.AccessTicket ticket = ticket(true, true);

        service.refundFailedChat(ticket, false);

        verify(wallet, never()).refundConsume(
                7L, 3, 2,
                WalletLedgerService.BIZ_CHAT_REFUND,
                "refund:chat-ref",
                "聊天模型首个内容前失败退回"
        );
        verify(chatModelMapper).completeGenerationIfContentEmitted(41L);
    }

    @Test
    void discardedPartialCanBeRefundedAfterFirstContent() {
        when(chatModelMapper.claimGenerationRefundDiscardingContent(41L)).thenReturn(1);
        H5EntitlementService.AccessTicket ticket = ticket(true, true);

        service.refundDiscardedChat(ticket);

        verify(chatModelMapper).claimGenerationRefundDiscardingContent(41L);
        verify(wallet).refundConsume(
                7L, 3, 2,
                WalletLedgerService.BIZ_CHAT_REFUND,
                "refund:chat-ref",
                "聊天内容未保存失败退回"
        );
        verify(chatModelMapper).updateGenerationChargeStatus(41L, "REFUNDED", "chat-ref");
    }

    @Test
    void refundReturnsOnlyTheCurrencyActuallySelected() {
        when(chatModelMapper.claimGenerationRefund(41L)).thenReturn(1);
        H5EntitlementService.AccessTicket ticket = new H5EntitlementService.AccessTicket(
                7L, "client", false, 0, H5EntitlementService.QuotaBucket.OFFICIAL_CHAT,
                3L, "GENERATE", true, 0, 100, "chat-ref", true,
                true, false, "request-1", 41L
        );

        service.refundFailedChat(ticket, false);

        verify(wallet).refundConsume(
                7L, 0, 100,
                WalletLedgerService.BIZ_CHAT_REFUND,
                "refund:chat-ref",
                "聊天模型首个内容前失败退回"
        );
    }

    @Test
    void blockingCancellationBeforeFirstContentRefundsReservation() {
        when(chatModelMapper.claimGenerationRefund(41L)).thenReturn(1);
        H5EntitlementService.AccessTicket ticket = ticket(false, false);

        service.settleBlockingChat(ticket, true, false, false);

        verify(chatModelMapper).claimGenerationRefund(41L);
        verify(chatModelMapper).updateGenerationChargeStatus(41L, "REFUNDED", "chat-ref");
        verify(chatModelMapper, never()).completeGenerationReservation(41L);
    }

    @Test
    void blockingCancellationAfterFirstContentCompletesReservation() {
        H5EntitlementService.AccessTicket ticket = ticket(false, false);

        service.settleBlockingChat(ticket, true, true, true);

        verify(chatModelMapper, never()).claimGenerationRefund(41L);
        verify(chatModelMapper).completeGenerationReservation(41L);
    }

    @Test
    void emptyStreamingResponseRefundsBeforeRequestCompletes() {
        when(chatModelMapper.claimGenerationRefund(41L)).thenReturn(1);
        H5EntitlementService.AccessTicket ticket = ticket(false, false);

        service.settleStreamingChat(ticket, false, false);

        verify(chatModelMapper).claimGenerationRefund(41L);
        verify(chatModelMapper).updateGenerationChargeStatus(41L, "REFUNDED", "chat-ref");
        verify(chatModelMapper, never()).completeGenerationReservation(41L);
    }

    @Test
    void streamingResponseWithContentCompletesReservation() {
        H5EntitlementService.AccessTicket ticket = ticket(false, false);

        service.settleStreamingChat(ticket, true, true);

        verify(chatModelMapper, never()).claimGenerationRefund(41L);
        verify(chatModelMapper).completeGenerationReservation(41L);
    }

    @Test
    void staleRefundingChargeIsClaimedBeforeIdempotentWalletRecovery() {
        ChatGenerationContext context = new ChatGenerationContext();
        context.setId(41L);
        context.setUserId(7L);
        context.setChargeStatus("REFUNDING");
        context.setFirstContentEmitted(false);
        context.setConsumeBizRef("chat-ref");
        context.setDiamondCost(3);
        context.setGoldCost(2);
        when(chatModelMapper.claimStaleGenerationRefund(eq(41L), any())).thenReturn(1);

        service.reconcileStaleChatCharge(context);

        verify(wallet).refundConsume(
                7L, 3, 2,
                WalletLedgerService.BIZ_CHAT_REFUND,
                "refund:chat-ref",
                "聊天模型超时任务自动退回"
        );
        verify(chatModelMapper).updateGenerationChargeStatus(41L, "REFUNDED", "chat-ref");
    }

    private static H5EntitlementService.AccessTicket ticket(boolean walletCharge, boolean usesWallet) {
        return new H5EntitlementService.AccessTicket(
                7L,
                "client",
                false,
                0,
                H5EntitlementService.QuotaBucket.OFFICIAL_CHAT,
                3L,
                "GENERATE",
                usesWallet,
                3,
                2,
                "chat-ref",
                walletCharge,
                true,
                false,
                "request-1",
                41L
        );
    }
}
