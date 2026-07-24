package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.billing.config.EpayPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.service.PaymentChannelConfigService;
import com.example.sillyspringboot.billing.service.PaymentChannelSecretService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EpayStorePaymentProviderTest {

    @Test
    void usesAdminConfiguredPayTypeWhenRuntimeOverrideIsBlank() {
        Fixture fixture = fixture(secrets("wxpay"));

        EpayStorePaymentProvider.ResolvedCredentials credentials =
                fixture.provider().resolveCredentials(fixture.config());

        assertThat(credentials.ready()).isTrue();
        assertThat(credentials.payType()).isEqualTo("wxpay");

        AppPaymentOrder order = order();
        Map<String, Object> payment = fixture.provider().createPayment(
                "epay", order, null, null, null, null
        );
        assertThat(payment.get("ready")).isEqualTo(true);
        assertThat(payment.get("paymentUrl")).asString().contains("type=wxpay");
    }

    @Test
    void explicitRuntimePayTypeOverridesAdminConfiguration() {
        Fixture fixture = fixture(secrets("alipay"));
        fixture.properties().setDefaultPayType("WXPAY");

        EpayStorePaymentProvider.ResolvedCredentials credentials =
                fixture.provider().resolveCredentials(fixture.config());

        assertThat(credentials.ready()).isTrue();
        assertThat(credentials.payType()).isEqualTo("wxpay");
    }

    @Test
    void defaultsToAlipayOnlyWhenNeitherSourceConfiguresPayType() {
        Map<String, Object> values = secrets("");
        Fixture fixture = fixture(values);

        EpayStorePaymentProvider.ResolvedCredentials credentials =
                fixture.provider().resolveCredentials(fixture.config());

        assertThat(credentials.ready()).isTrue();
        assertThat(credentials.payType()).isEqualTo("alipay");
    }

    @Test
    void invalidPayTypeOrEndpointKeepsChannelUnready() {
        Fixture invalidType = fixture(secrets("bank"));
        assertThat(invalidType.provider().resolveCredentials(invalidType.config()).ready()).isFalse();

        Map<String, Object> invalidApi = secrets("alipay");
        invalidApi.put("apiUrl", "javascript:alert(1)");
        Fixture invalidApiFixture = fixture(invalidApi);
        assertThat(invalidApiFixture.provider().resolveCredentials(invalidApiFixture.config()).ready()).isFalse();

        Map<String, Object> relativeNotify = secrets("alipay");
        relativeNotify.put("notifyUrl", "/api/payment/epay/notify");
        Fixture relativeNotifyFixture = fixture(relativeNotify);
        assertThat(relativeNotifyFixture.provider().resolveCredentials(relativeNotifyFixture.config()).ready()).isFalse();

        Map<String, Object> apiWithQuery = secrets("alipay");
        apiWithQuery.put("apiUrl", "https://pay.example.test/gateway?next=submit.php");
        Fixture apiWithQueryFixture = fixture(apiWithQuery);
        assertThat(apiWithQueryFixture.provider().resolveCredentials(apiWithQueryFixture.config()).ready()).isFalse();

        Map<String, Object> insecurePublicCallback = secrets("alipay");
        insecurePublicCallback.put("notifyUrl", "http://shop.example.test/api/payment/epay/notify");
        Fixture insecurePublicCallbackFixture = fixture(insecurePublicCallback);
        assertThat(insecurePublicCallbackFixture.provider().resolveCredentials(insecurePublicCallbackFixture.config()).ready()).isFalse();
    }

    private static Fixture fixture(Map<String, Object> secrets) {
        PaymentChannelConfigService channelConfigService = mock(PaymentChannelConfigService.class);
        PaymentChannelSecretService secretService = mock(PaymentChannelSecretService.class);
        EpayPaymentProperties properties = new EpayPaymentProperties();
        AppPaymentChannelConfig config = new AppPaymentChannelConfig();
        config.setEnabled(true);
        config.setConfigCipher("cipher");
        when(channelConfigService.getRequired("epay")).thenReturn(config);
        when(secretService.decryptConfig("cipher")).thenReturn(secrets);
        EpayStorePaymentProvider provider = new EpayStorePaymentProvider(
                channelConfigService,
                secretService,
                properties
        );
        return new Fixture(provider, config, properties);
    }

    private static Map<String, Object> secrets(String payType) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("pid", "1001");
        values.put("key", "secret-key");
        values.put("apiUrl", "https://pay.example.test/gateway");
        values.put("notifyUrl", "https://shop.example.test/api/payment/epay/notify");
        values.put("returnUrl", "https://shop.example.test/payment/result?source=epay");
        if (payType != null && !payType.isBlank()) {
            values.put("typeDefault", payType);
        }
        return values;
    }

    private static AppPaymentOrder order() {
        AppPaymentOrder order = new AppPaymentOrder();
        order.setOrderNo("SPTESTORDER");
        order.setProductName("Test product");
        order.setAmountCents(990);
        return order;
    }

    private record Fixture(
            EpayStorePaymentProvider provider,
            AppPaymentChannelConfig config,
            EpayPaymentProperties properties
    ) {
    }
}
