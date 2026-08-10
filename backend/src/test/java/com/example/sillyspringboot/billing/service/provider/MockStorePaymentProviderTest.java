package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.billing.config.MockPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.service.PaymentChannelConfigService;
import com.example.sillyspringboot.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockStorePaymentProviderTest {

    @Test
    void productionCannotEnableManualSettlementThroughLegacyOverride() {
        PaymentChannelConfigService channelConfigService = mock(PaymentChannelConfigService.class);
        AppPaymentChannelConfig channel = new AppPaymentChannelConfig();
        channel.setEnabled(true);
        when(channelConfigService.getRequired("mock_wechat")).thenReturn(channel);

        MockPaymentProperties paymentProperties = new MockPaymentProperties();
        paymentProperties.setEnabled(true);
        paymentProperties.setAllowInProd(true);
        AppProperties appProperties = new AppProperties();
        appProperties.setEnvironment("production");

        MockStorePaymentProvider provider = new MockStorePaymentProvider(
                channelConfigService,
                paymentProperties,
                appProperties
        );

        assertThat(provider.supportsManualSettlement("mock_wechat")).isFalse();
    }
}
