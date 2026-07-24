package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.mapper.AppStoreProductMapper;
import com.example.sillyspringboot.billing.service.provider.StorePaymentProviderRegistry;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceConfirmPaidTest {

    @Mock H5ClientUidAuthService h5Auth;
    @Mock AppTokenService tokenService;
    @Mock AppUserMapper userMapper;
    @Mock AppH5ClientUidMapper h5ClientUidMapper;
    @Mock AppH5UserProfileExtMapper profileExtMapper;
    @Mock AppStoreProductMapper productMapper;
    @Mock AppPaymentOrderMapper orderMapper;
    @Mock WalletLedgerService walletLedgerService;
    @Mock H5EntitlementService h5EntitlementService;
    @Mock EntitlementAuditLogService entitlementAuditLogService;
    @Mock EntitlementPolicyService entitlementPolicyService;
    @Mock StorePaymentProviderRegistry paymentProviderRegistry;

    @InjectMocks
    StoreService storeService;

    @Test
    void confirmProviderPaidAppliesBenefitsOnlyWhenMarkPaidAffectsOneRow() {
        AppPaymentOrder pending = order("SP1", "PENDING", 990);
        AppPaymentOrder paid = order("SP1", "PAID", 990);
        AppUser user = new AppUser();
        user.setId(7L);
        AppH5UserProfileExt ext = new AppH5UserProfileExt();
        ext.setUserId(7L);
        ext.setScore(0);
        ext.setGoldCoin(0);
        ext.setVipType(0);

        when(orderMapper.findByOrderNo("SP1")).thenReturn(pending, paid, paid);
        when(userMapper.findById(7L)).thenReturn(user);
        when(orderMapper.markPaid(eq(1L), any(), any(), any())).thenReturn(1);
        when(h5EntitlementService.ensureProfileExt(user)).thenReturn(ext);
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(ext);
        when(profileExtMapper.creditWallet(7L, 10, 0)).thenReturn(1);
        AppH5UserProfileExt applied = new AppH5UserProfileExt();
        applied.setUserId(7L);
        applied.setScore(10);
        applied.setGoldCoin(0);
        applied.setVipType(0);
        when(profileExtMapper.findByUserId(7L)).thenReturn(applied);
        when(walletLedgerService.insertPaymentCredit(anyLong(), anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(true);

        storeService.confirmProviderPaid("SP1", "epay", 990, "T-1", "hash");
        storeService.confirmProviderPaid("SP1", "epay", 990, "T-1", "hash");

        verify(orderMapper, times(1)).markPaid(eq(1L), eq("T-1"), eq(990), eq("hash"));
        verify(walletLedgerService, times(1)).insertPaymentCredit(eq(7L), eq("SP1"), anyInt(), anyInt(), anyString());
        verify(profileExtMapper, times(1)).creditWallet(7L, 10, 0);
        verify(profileExtMapper, never()).upsert(any());
    }

    @Test
    void confirmProviderPaidSkipsBenefitsWhenAlreadyPaidRace() {
        AppPaymentOrder pending = order("SP2", "PENDING", 100);
        AppUser user = new AppUser();
        user.setId(7L);
        AppH5UserProfileExt ext = new AppH5UserProfileExt();
        ext.setUserId(7L);

        when(orderMapper.findByOrderNo("SP2")).thenReturn(pending);
        when(userMapper.findById(7L)).thenReturn(user);
        when(orderMapper.markPaid(anyLong(), isNull(), any(), isNull())).thenReturn(0);
        when(h5EntitlementService.ensureProfileExt(user)).thenReturn(ext);

        storeService.confirmProviderPaid("SP2", "epay");

        verify(walletLedgerService, never()).insertPaymentCredit(anyLong(), anyString(), anyInt(), anyInt(), anyString());
        verify(profileExtMapper, never()).upsert(any());
        verify(profileExtMapper, never()).findByUserIdForUpdate(anyLong());
    }

    @Test
    void confirmProviderPaidRollsBackPathWhenPaymentLedgerAlreadyExists() {
        AppPaymentOrder pending = order("SP3", "PENDING", 100);
        AppPaymentOrder paid = order("SP3", "PAID", 100);
        AppUser user = new AppUser();
        user.setId(7L);
        AppH5UserProfileExt ext = new AppH5UserProfileExt();
        ext.setUserId(7L);

        when(orderMapper.findByOrderNo("SP3")).thenReturn(pending, paid);
        when(userMapper.findById(7L)).thenReturn(user);
        when(orderMapper.markPaid(anyLong(), isNull(), any(), isNull())).thenReturn(1);
        when(h5EntitlementService.ensureProfileExt(user)).thenReturn(ext);
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(ext);
        when(walletLedgerService.insertPaymentCredit(anyLong(), anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(false);

        assertThatThrownBy(() -> storeService.confirmProviderPaid("SP3", "epay"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("支付流水已存在");

        verify(profileExtMapper, never()).creditWallet(anyLong(), anyInt(), anyInt());
        verify(profileExtMapper, never()).updateMembershipAndEffectiveQuotas(
                anyLong(), anyInt(), any(), anyInt(), any(), anyInt(), any()
        );
    }

    @Test
    void vipBenefitUsesLockedCurrentExpiryInsteadOfStaleProfile() {
        AppPaymentOrder pending = order("SP4", "PENDING", 2800);
        pending.setVipType(1);
        pending.setVipDays(7);
        AppPaymentOrder paid = order("SP4", "PAID", 2800);
        paid.setVipType(1);
        paid.setVipDays(7);
        AppUser user = new AppUser();
        user.setId(7L);
        AppH5UserProfileExt stale = new AppH5UserProfileExt();
        stale.setUserId(7L);
        stale.setVipType(0);
        LocalDateTime lockedExpiry = LocalDateTime.now().plusDays(30).withNano(0);
        AppH5UserProfileExt locked = new AppH5UserProfileExt();
        locked.setUserId(7L);
        locked.setVipType(2);
        locked.setVipExpiresAt(lockedExpiry);

        when(orderMapper.findByOrderNo("SP4")).thenReturn(pending, paid, paid);
        when(userMapper.findById(7L)).thenReturn(user);
        when(orderMapper.markPaid(anyLong(), isNull(), any(), isNull())).thenReturn(1);
        when(h5EntitlementService.ensureProfileExt(user)).thenReturn(stale);
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(locked);
        when(walletLedgerService.insertPaymentCredit(anyLong(), anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(true);
        when(profileExtMapper.creditWallet(7L, 10, 0)).thenReturn(1);
        when(profileExtMapper.updateMembershipAndEffectiveQuotas(
                eq(7L), eq(2), eq(lockedExpiry.plusDays(7)), anyInt(), any(), anyInt(), any()
        )).thenReturn(1);
        when(profileExtMapper.findByUserId(7L)).thenReturn(locked);

        storeService.confirmProviderPaid("SP4", "epay");

        verify(profileExtMapper).updateMembershipAndEffectiveQuotas(
                eq(7L), eq(2), eq(lockedExpiry.plusDays(7)), anyInt(), any(), anyInt(), any()
        );
    }

    @Test
    void confirmProviderPaidRejectsMalformedFrozenBenefitsBeforeLedgerWrite() {
        AppPaymentOrder pending = order("SP5", "PENDING", 100);
        AppPaymentOrder paid = order("SP5", "PAID", 100);
        paid.setScoreAmount(0);
        paid.setVipType(1);
        paid.setVipDays(0);
        AppUser user = new AppUser();
        user.setId(7L);
        AppH5UserProfileExt ext = new AppH5UserProfileExt();
        ext.setUserId(7L);

        when(orderMapper.findByOrderNo("SP5")).thenReturn(pending, paid);
        when(userMapper.findById(7L)).thenReturn(user);
        when(orderMapper.markPaid(anyLong(), isNull(), any(), isNull())).thenReturn(1);
        when(h5EntitlementService.ensureProfileExt(user)).thenReturn(ext);
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(ext);

        assertThatThrownBy(() -> storeService.confirmProviderPaid("SP5", "epay"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权益配置不完整");

        verify(walletLedgerService, never()).insertPaymentCredit(
                anyLong(), anyString(), anyInt(), anyInt(), anyString()
        );
        verify(profileExtMapper, never()).creditWallet(anyLong(), anyInt(), anyInt());
    }

    private static AppPaymentOrder order(String orderNo, String status, int amountCents) {
        AppPaymentOrder order = new AppPaymentOrder();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setUserId(7L);
        order.setStatus(status);
        order.setAmountCents(amountCents);
        order.setScoreAmount(10);
        order.setGoldCoinAmount(0);
        order.setVipType(0);
        order.setVipDays(0);
        order.setPaymentChannel("epay");
        order.setProductName("???????");
        return order;
    }
}
