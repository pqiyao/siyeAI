package com.example.sillyspringboot.billing.web;

import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.service.StoreService;
import com.example.sillyspringboot.billing.service.provider.EpaySignSupport;
import com.example.sillyspringboot.billing.service.provider.EpayStorePaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EpayNotifyControllerTest {

    private EpayStorePaymentProvider provider;
    private AppPaymentOrderMapper orderMapper;
    private StoreService storeService;
    private EpayNotifyController controller;

    @BeforeEach
    void setUp() {
        provider = mock(EpayStorePaymentProvider.class);
        orderMapper = mock(AppPaymentOrderMapper.class);
        storeService = mock(StoreService.class);
        controller = new EpayNotifyController(provider, orderMapper, storeService);
        when(provider.resolveCredentialsForNotify()).thenReturn(credentials());
    }

    @Test
    void acceptsSignedNotifyForConfiguredMerchantAndExactAmount() {
        AppPaymentOrder order = order(990);
        when(orderMapper.findByOrderNo("SPTESTORDER")).thenReturn(order);
        Map<String, String> params = signedParams("1001", "9.90");

        assertThat(controller.notifyPost(params)).isEqualTo("success");
        verify(storeService).confirmProviderPaid(
                eq("SPTESTORDER"),
                eq("epay"),
                eq(990),
                eq("EPAY-TRADE-1"),
                anyString()
        );
    }

    @Test
    void rejectsSignedNotifyForDifferentMerchant() {
        Map<String, String> params = signedParams("2002", "9.90");

        assertThat(controller.notifyPost(params)).isEqualTo("fail");
        verifyNoInteractions(orderMapper, storeService);
    }

    @Test
    void rejectsAmountWithExtraPrecisionInsteadOfRoundingIt() {
        when(orderMapper.findByOrderNo("SPTESTORDER")).thenReturn(order(990));
        Map<String, String> params = signedParams("1001", "9.901");

        assertThat(controller.notifyPost(params)).isEqualTo("fail");
        verify(storeService, never()).confirmProviderPaid(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsUnsupportedDeclaredSignType() {
        Map<String, String> params = signedParams("1001", "9.90");
        params.put("sign_type", "RSA2");

        assertThat(controller.notifyGet(params)).isEqualTo("fail");
        verifyNoInteractions(orderMapper, storeService);
    }

    @Test
    void rejectsSuccessfulNotifyWithoutProviderTradeNumber() {
        when(orderMapper.findByOrderNo("SPTESTORDER")).thenReturn(order(990));
        Map<String, String> params = signedParams("1001", "9.90");
        params.remove("trade_no");
        params.put("sign", EpaySignSupport.sign(params, "secret-key"));

        assertThat(controller.notifyPost(params)).isEqualTo("fail");
        verifyNoInteractions(storeService);
    }

    @Test
    void returnEndpointsNeverSettleAnOrder() {
        assertThat(controller.returnGet(Map.of("out_trade_no", "SPTESTORDER"))).isEqualTo("ok");
        assertThat(controller.returnPost(Map.of("out_trade_no", "SPTESTORDER"))).isEqualTo("ok");
        verifyNoInteractions(orderMapper, storeService);
    }

    private static EpayStorePaymentProvider.ResolvedCredentials credentials() {
        return new EpayStorePaymentProvider.ResolvedCredentials(
                "1001",
                "secret-key",
                "https://pay.example.test/gateway",
                "https://shop.example.test/api/payment/epay/notify",
                "https://shop.example.test/payment/result",
                "alipay",
                true
        );
    }

    private static Map<String, String> signedParams(String pid, String money) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", pid);
        params.put("trade_no", "EPAY-TRADE-1");
        params.put("out_trade_no", "SPTESTORDER");
        params.put("type", "alipay");
        params.put("name", "Test product");
        params.put("money", money);
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("sign_type", "MD5");
        params.put("sign", EpaySignSupport.sign(params, "secret-key"));
        return params;
    }

    private static AppPaymentOrder order(int amountCents) {
        AppPaymentOrder order = new AppPaymentOrder();
        order.setOrderNo("SPTESTORDER");
        order.setAmountCents(amountCents);
        order.setPaymentChannel("epay");
        return order;
    }
}
