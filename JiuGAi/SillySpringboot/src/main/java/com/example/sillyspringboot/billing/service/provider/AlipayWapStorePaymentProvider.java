package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.billing.config.AlipayWapPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.billing.service.PaymentChannelConfigService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class AlipayWapStorePaymentProvider implements StorePaymentProvider {

    private static final String CHANNEL_CODE = "alipay_wap";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentChannelConfigService channelConfigService;
    private final AlipayWapPaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    public AlipayWapStorePaymentProvider(
            PaymentChannelConfigService channelConfigService,
            AlipayWapPaymentProperties paymentProperties,
            ObjectMapper objectMapper
    ) {
        this.channelConfigService = channelConfigService;
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supportsChannel(String channel) {
        return CHANNEL_CODE.equals(channel);
    }

    @Override
    public List<Map<String, Object>> describeChannels() {
        AppPaymentChannelConfig config = channelConfigService.getRequired(CHANNEL_CODE);
        boolean credentialsReady = credentialsReady();
        boolean ready = Boolean.TRUE.equals(config.getEnabled()) && credentialsReady;
        return List.of(Map.of(
                "code", CHANNEL_CODE,
                "name", config.getDisplayName(),
                "desc", channelDescription(config, credentialsReady),
                "provider", "alipay_wap",
                "enabled", Boolean.TRUE.equals(config.getEnabled()),
                "ready", ready,
                "manualSettlement", false,
                "clientVisible", Boolean.TRUE.equals(config.getClientVisible()),
                "sortOrder", nvl(config.getSortOrder()),
                "note", blank(config.getNote())
        ));
    }

    @Override
    public Map<String, Object> createPayment(
            String channel,
            AppPaymentOrder order,
            AppStoreProduct product,
            AppUser user,
            AppH5UserProfileExt profile,
            StorePaymentContext context
    ) {
        AppPaymentChannelConfig config = channelConfigService.getRequired(CHANNEL_CODE);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return unavailablePayload(config, "支付宝通道尚未开启，请联系客服。");
        }
        if (!credentialsReady()) {
            return unavailablePayload(config, "支付宝商户参数未配置完成，请先在后台补齐配置。");
        }

        PrivateKey privateKey = PaymentPrivateKeySupport.loadPrivateKey(
                paymentProperties.getPrivateKeyPem(),
                paymentProperties.getPrivateKeyPath()
        );
        String bizContent = buildBizContent(order, product);

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", paymentProperties.getAppId().trim());
        params.put("method", "alipay.trade.wap.pay");
        params.put("format", "JSON");
        params.put("charset", safe(paymentProperties.getCharset(), "UTF-8"));
        params.put("sign_type", safe(paymentProperties.getSignType(), "RSA2"));
        params.put("timestamp", TIMESTAMP_FORMATTER.format(LocalDateTime.now()));
        params.put("version", "1.0");
        params.put("notify_url", paymentProperties.getNotifyUrl().trim());
        params.put("return_url", paymentProperties.getReturnUrl().trim());
        params.put("biz_content", bizContent);

        String signContent = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        String sign = PaymentPrivateKeySupport.sign(signContent, privateKey, "SHA256withRSA");
        params.put("sign", sign);

        String paymentUrl = safe(paymentProperties.getGatewayUrl(), "https://openapi.alipay.com/gateway.do")
                + "?"
                + params.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "alipay_wap");
        data.put("channel", channel);
        data.put("ready", true);
        data.put("manualSettlement", false);
        data.put("action", "open_external_url");
        data.put("message", "已生成支付宝支付链接，请继续完成支付。");
        data.put("paymentUrl", paymentUrl);
        data.put("orderNo", order.getOrderNo());
        data.put("productCode", order.getProductCode());
        data.put("productName", order.getProductName());
        return data;
    }

    private String buildBizContent(AppPaymentOrder order, AppStoreProduct product) {
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", order.getOrderNo());
        biz.put("total_amount", String.format(Locale.ROOT, "%.2f", nvl(order.getAmountCents()) / 100.0d));
        biz.put("subject", truncate(subject(product, order), 128));
        biz.put("product_code", "QUICK_WAP_WAY");
        try {
            return objectMapper.writeValueAsString(biz);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize alipay biz_content", ex);
        }
    }

    private boolean credentialsReady() {
        return paymentProperties.isEnabled()
                && !blank(paymentProperties.getAppId()).isBlank()
                && !blank(paymentProperties.getNotifyUrl()).isBlank()
                && !blank(paymentProperties.getReturnUrl()).isBlank()
                && (!blank(paymentProperties.getPrivateKeyPem()).isBlank()
                || !blank(paymentProperties.getPrivateKeyPath()).isBlank());
    }

    private Map<String, Object> unavailablePayload(AppPaymentChannelConfig config, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "alipay_wap");
        data.put("channel", CHANNEL_CODE);
        data.put("ready", false);
        data.put("manualSettlement", false);
        data.put("action", "await_provider_config");
        data.put("message", message);
        data.put("displayName", config.getDisplayName());
        return data;
    }

    private String channelDescription(AppPaymentChannelConfig config, boolean credentialsReady) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return "后台已预留支付宝通道，当前未对用户开放。";
        }
        if (!credentialsReady) {
            return "支付宝通道已开启，但商户参数尚未补齐。";
        }
        return blank(config.getDescription());
    }

    private String subject(AppStoreProduct product, AppPaymentOrder order) {
        String value = product == null ? order.getProductName() : product.getName();
        if (value == null || value.isBlank()) {
            return "会员权益订单";
        }
        return value.trim();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static int nvl(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
