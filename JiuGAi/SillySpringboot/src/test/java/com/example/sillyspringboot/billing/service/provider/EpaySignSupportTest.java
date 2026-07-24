package com.example.sillyspringboot.billing.service.provider;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EpaySignSupportTest {

    @Test
    void signsAndVerifiesStandardEpayPayload() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", "1001");
        params.put("type", "alipay");
        params.put("out_trade_no", "SPTESTORDER");
        params.put("notify_url", "https://example.test/notify");
        params.put("return_url", "https://example.test/return");
        params.put("name", "ʯ");
        params.put("money", "9.90");

        String sign = EpaySignSupport.sign(params, "secret-key");
        assertThat(sign).hasSize(32);
        assertThat(EpaySignSupport.verify(params, "secret-key", sign)).isTrue();
        assertThat(EpaySignSupport.verify(params, "wrong-key", sign)).isFalse();
    }

    @Test
    void moneyYuanAndCentsRoundTrip() {
        assertThat(EpaySignSupport.moneyYuan(990)).isEqualTo("9.90");
        assertThat(EpaySignSupport.moneyYuan(Integer.MAX_VALUE)).isEqualTo("21474836.47");
        assertThat(EpaySignSupport.parseMoneyToCents("9.90")).isEqualTo(990);
        assertThat(EpaySignSupport.parseMoneyToCents("10")).isEqualTo(1000);
        assertThat(EpaySignSupport.parseMoneyToCents(" 0.10 ")).isEqualTo(10);
        assertThat(EpaySignSupport.parseMoneyToCents("21474836.47")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void rejectsAmbiguousOrOutOfRangeMoney() {
        assertThat(EpaySignSupport.parseMoneyToCents(null)).isEqualTo(-1);
        assertThat(EpaySignSupport.parseMoneyToCents("")).isEqualTo(-1);
        assertThat(EpaySignSupport.parseMoneyToCents("9.901")).isEqualTo(-1);
        assertThat(EpaySignSupport.parseMoneyToCents("9.")).isEqualTo(-1);
        assertThat(EpaySignSupport.parseMoneyToCents("1e1")).isEqualTo(-1);
        assertThat(EpaySignSupport.parseMoneyToCents("NaN")).isEqualTo(-1);
        assertThat(EpaySignSupport.parseMoneyToCents("-1.00")).isEqualTo(-1);
        assertThat(EpaySignSupport.parseMoneyToCents("21474836.48")).isEqualTo(-1);
        assertThatThrownBy(() -> EpaySignSupport.moneyYuan(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
