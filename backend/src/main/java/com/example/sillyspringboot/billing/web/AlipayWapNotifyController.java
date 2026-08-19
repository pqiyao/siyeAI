package com.example.sillyspringboot.billing.web;

import com.example.sillyspringboot.billing.config.AlipayWapPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.service.StoreService;
import com.example.sillyspringboot.billing.service.provider.PaymentPublicKeySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/payment/alipay-wap")
public class AlipayWapNotifyController {
    private static final Logger log = LoggerFactory.getLogger(AlipayWapNotifyController.class);

    private final AlipayWapPaymentProperties properties;
    private final AppPaymentOrderMapper orderMapper;
    private final StoreService storeService;

    public AlipayWapNotifyController(AlipayWapPaymentProperties properties,
                                     AppPaymentOrderMapper orderMapper,
                                     StoreService storeService) {
        this.properties = properties;
        this.orderMapper = orderMapper;
        this.storeService = storeService;
    }

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestParam Map<String, String> raw) {
        try {
            Map<String, String> params = new TreeMap<>(raw == null ? Map.of() : raw);
            String signature = params.remove("sign");
            params.remove("sign_type");
            String content = params.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((left, right) -> left + "&" + right).orElse("");
            PublicKey publicKey = PaymentPublicKeySupport.loadPublicKey(properties.getAlipayPublicKey(), "");
            if (signature == null || !PaymentPublicKeySupport.verify(content, signature, publicKey)) return "failure";
            if (!secureEquals(properties.getAppId(), params.get("app_id"))) return "failure";
            String status = value(params.get("trade_status")).toUpperCase(Locale.ROOT);
            if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) return "failure";
            String orderNo = value(params.get("out_trade_no"));
            AppPaymentOrder order = orderMapper.findByOrderNo(orderNo);
            if (order == null) return "failure";
            int paidCents = new BigDecimal(value(params.get("total_amount"))).movePointRight(2).intValueExact();
            String tradeNo = value(params.get("trade_no"));
            if (tradeNo.isBlank()) return "failure";
            storeService.confirmProviderPaid(orderNo, "alipay_wap", paidCents, tradeNo, sha256(raw.toString()));
            return "success";
        } catch (Exception ex) {
            log.warn("alipay notify rejected: {}", ex.getMessage());
            return "failure";
        }
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }

    private static boolean secureEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.trim().getBytes(StandardCharsets.UTF_8), actual.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b));
            return result.toString();
        } catch (Exception ex) { return ""; }
    }
}
