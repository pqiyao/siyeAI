package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.mapper.AppStoreProductMapper;
import com.example.sillyspringboot.billing.service.provider.StorePaymentProviderRegistry;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceOrderRemovalTest {

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

    private AppUser user;

    @BeforeEach
    void setUpUser() {
        user = new AppUser();
        user.setId(42L);
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(tokenService.validateAndLoadUser("token-1")).thenReturn(user);
    }

    @Test
    void hidesPendingOrderOwnedByCurrentUser() {
        AppPaymentOrder pending = order("SP-PENDING", "PENDING");
        when(orderMapper.findByOrderNoAndUserId("SP-PENDING", 42L)).thenReturn(pending);
        when(orderMapper.hideUnpaidByOrderNoAndUserId("SP-PENDING", 42L)).thenReturn(1);

        Map<String, Object> result = storeService.removeUnpaidOrder("client-1", "SP-PENDING");

        assertThat(result).containsEntry("removed", true).containsEntry("orderNo", "SP-PENDING");
        verify(orderMapper).hideUnpaidByOrderNoAndUserId("SP-PENDING", 42L);
    }

    @Test
    void rejectsPaidOrderRemoval() {
        AppPaymentOrder paid = order("SP-PAID", "PAID");
        when(orderMapper.findByOrderNoAndUserId("SP-PAID", 42L)).thenReturn(paid);

        assertThatThrownBy(() -> storeService.removeUnpaidOrder("client-1", "SP-PAID"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已支付订单不能删除");
        verify(orderMapper, never()).hideUnpaidByOrderNoAndUserId("SP-PAID", 42L);
    }

    @Test
    void rejectsRemovalWhenPaymentWinsTheRace() {
        AppPaymentOrder pending = order("SP-RACE", "PENDING");
        AppPaymentOrder paid = order("SP-RACE", "PAID");
        when(orderMapper.findByOrderNoAndUserId("SP-RACE", 42L)).thenReturn(pending, paid);
        when(orderMapper.hideUnpaidByOrderNoAndUserId("SP-RACE", 42L)).thenReturn(0);

        assertThatThrownBy(() -> storeService.removeUnpaidOrder("client-1", "SP-RACE"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单已支付，不能删除");
    }

    private AppPaymentOrder order(String orderNo, String status) {
        AppPaymentOrder order = new AppPaymentOrder();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setUserId(user.getId());
        order.setStatus(status);
        return order;
    }
}
