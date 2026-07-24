package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.mapper.AppWalletLedgerMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletLedgerServiceConsistencyTest {

    @Mock AppWalletLedgerMapper walletLedgerMapper;
    @Mock AppH5UserProfileExtMapper profileExtMapper;

    @InjectMocks
    WalletLedgerService walletLedgerService;

    @Test
    void refundDoesNotWritePositiveLedgerWhenWalletAccountIsMissing() {
        when(profileExtMapper.creditWallet(7L, 10, 2)).thenReturn(0);

        assertInternalError(() -> walletLedgerService.refundConsume(
                7L, 10, 2, "TTS_REFUND", "refund:tts-1", "failed tts"
        ), "退款账户不存在");

        verify(walletLedgerMapper, never()).insertFull(
                anyLong(), anyString(), any(), any(), anyString(), anyInt(), anyInt(), anyString()
        );
    }

    @Test
    void duplicateConsumeRequiresSuccessfulBalanceCompensation() {
        when(profileExtMapper.deductWallet(7L, 10, 2)).thenReturn(1);
        when(walletLedgerMapper.insertFull(
                anyLong(), anyString(), any(), any(), anyString(), anyInt(), anyInt(), anyString()
        )).thenThrow(new DuplicateKeyException("duplicate consume"));
        when(profileExtMapper.creditWallet(7L, 10, 2)).thenReturn(0);

        assertInternalError(() -> walletLedgerService.consumeDiamonds(
                7L, 10, 2, WalletLedgerService.BIZ_TTS_CONSUME, "tts-1", "tts"
        ), "重复消费补偿失败");
    }

    @Test
    void duplicateRefundRequiresSuccessfulCreditReversal() {
        when(profileExtMapper.creditWallet(7L, 10, 2)).thenReturn(1);
        when(walletLedgerMapper.insertFull(
                anyLong(), anyString(), any(), any(), anyString(), anyInt(), anyInt(), anyString()
        )).thenThrow(new DuplicateKeyException("duplicate refund"));
        when(profileExtMapper.deductWallet(7L, 10, 2)).thenReturn(0);

        assertInternalError(() -> walletLedgerService.refundConsume(
                7L, 10, 2, "TTS_REFUND", "refund:tts-1", "failed tts"
        ), "重复退款冲正失败");
    }

    @Test
    void paymentCreditRejectsNegativeAmountsBeforeWritingLedger() {
        assertThatThrownBy(() -> walletLedgerService.insertPaymentCredit(7L, "SP1", -1, 0, "invalid"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(walletLedgerMapper, never()).insertFull(
                anyLong(), anyString(), any(), any(), anyString(), anyInt(), anyInt(), anyString()
        );
    }

    @Test
    void consumeReportsWhetherThisCallCreatedTheCharge() {
        when(profileExtMapper.deductWallet(7L, 10, 2)).thenReturn(1);
        when(walletLedgerMapper.insertFull(
                anyLong(), anyString(), any(), any(), anyString(), anyInt(), anyInt(), anyString()
        )).thenReturn(1);

        assertThat(walletLedgerService.consumeDiamonds(
                7L, 10, 2, WalletLedgerService.BIZ_TTS_CONSUME, "tts-new", "tts"
        )).isTrue();
    }

    @Test
    void existingIdempotencyKeyReportsNoNewCharge() {
        when(walletLedgerMapper.findByIdempotencyKey("TTS_CONSUME:tts-existing"))
                .thenReturn(mock(com.example.sillyspringboot.billing.entity.AppWalletLedger.class));

        assertThat(walletLedgerService.consumeDiamonds(
                7L, 10, 2, WalletLedgerService.BIZ_TTS_CONSUME, "tts-existing", "tts"
        )).isFalse();
        verify(profileExtMapper, never()).deductWallet(anyLong(), anyInt(), anyInt());
    }

    private static void assertInternalError(Runnable action, String messagePart) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(messagePart)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
    }
}
