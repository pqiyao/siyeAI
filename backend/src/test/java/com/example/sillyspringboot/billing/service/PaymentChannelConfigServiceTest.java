package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.config.EpayPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.mapper.AppPaymentChannelConfigMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentChannelConfigServiceTest {

    private AppPaymentChannelConfigMapper mapper;
    private PaymentChannelSecretService secretService;
    private EpayPaymentProperties properties;
    private PaymentChannelConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AppPaymentChannelConfigMapper.class);
        secretService = mock(PaymentChannelSecretService.class);
        properties = new EpayPaymentProperties();
        service = new PaymentChannelConfigService(mapper, secretService, properties);
        when(mapper.listAll()).thenReturn(List.of());
    }

    @Test
    void rejectsVisibleEpayWithoutCompleteCredentials() {
        Map<String, Object> body = Map.of(
                "channelCode", "epay",
                "enabled", true,
                "clientVisible", true
        );

        assertThatThrownBy(() -> service.saveFromAdmin(body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PID");
    }

    @Test
    void rejectsPublicHttpCallbackWhenEnablingEpay() {
        Map<String, Object> body = validBody();
        body.put("notifyUrl", "http://shop.example.test/api/payment/epay/notify");

        assertThatThrownBy(() -> service.saveFromAdmin(body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void acceptsCompleteHttpsConfiguration() {
        AtomicReference<AppPaymentChannelConfig> saved = new AtomicReference<>();
        when(secretService.encryptConfig(any())).thenReturn("encrypted-config");
        when(secretService.decryptConfig("encrypted-config")).thenReturn(Map.of(
                "pid", "1001",
                "apiUrl", "https://pay.example.test",
                "notifyUrl", "https://shop.example.test/api/payment/epay/notify",
                "returnUrl", "https://shop.example.test/payment/result",
                "typeDefault", "alipay"
        ));
        doAnswer(invocation -> {
            AppPaymentChannelConfig row = invocation.getArgument(0);
            row.setId(1L);
            saved.set(row);
            return 1;
        }).when(mapper).insert(any());
        when(mapper.listAll()).thenAnswer(invocation -> saved.get() == null ? List.of() : List.of(saved.get()));

        Map<String, Object> result = service.saveFromAdmin(validBody());

        assertThat(result.get("channelCode")).isEqualTo("epay");
        assertThat(result.get("enabled")).isEqualTo(true);
    }

    private static Map<String, Object> validBody() {
        return new java.util.LinkedHashMap<>(Map.of(
                "channelCode", "epay",
                "enabled", true,
                "clientVisible", true,
                "pid", "1001",
                "key", "secret-key",
                "apiUrl", "https://pay.example.test",
                "notifyUrl", "https://shop.example.test/api/payment/epay/notify",
                "returnUrl", "https://shop.example.test/payment/result",
                "typeDefault", "alipay"
        ));
    }
}
