package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.billing.config.WechatH5PaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.billing.service.PaymentChannelConfigService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WechatH5StorePaymentProvider implements StorePaymentProvider {

    private static final String CHANNEL_CODE = "wechat_h5";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PaymentChannelConfigService channelConfigService;
    private final WechatH5PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WechatH5StorePaymentProvider(
            PaymentChannelConfigService channelConfigService,
            WechatH5PaymentProperties paymentProperties,
            ObjectMapper objectMapper
    ) {
        this.channelConfigService = channelConfigService;
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
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
                "provider", "wechat_h5",
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
            return unavailablePayload(config, "微信 H5 通道尚未开启，请联系客服。");
        }
        if (!credentialsReady()) {
            return unavailablePayload(config, "微信商户参数未配置完成，请先在后台补齐配置。");
        }
        if (context == null || context.getClientIp().isBlank()) {
            return unavailablePayload(config, "微信 H5 支付需要识别用户来源 IP，请在公网环境下发起支付。");
        }

        try {
            PrivateKey privateKey = PaymentPrivateKeySupport.loadPrivateKey(
                    paymentProperties.getPrivateKeyPem(),
                    paymentProperties.getPrivateKeyPath()
            );
            String requestPath = "/v3/pay/transactions/h5";
            String requestBody = buildRequestBody(order, product, context.getClientIp());
            String authorization = buildAuthorization(privateKey, requestPath, requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(paymentProperties.getApiBaseUrl()) + requestPath))
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header(HttpHeaders.USER_AGENT, safeUserAgent(context.getUserAgent()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return unavailablePayload(config, "微信 H5 下单失败：" + extractWechatError(response.body()));
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), MAP_TYPE);
            String paymentUrl = stringValue(body.get("h5_url"));
            if (paymentUrl.isBlank()) {
                return unavailablePayload(config, "微信 H5 下单成功，但未返回支付链接。");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("provider", "wechat_h5");
            data.put("channel", channel);
            data.put("ready", true);
            data.put("manualSettlement", false);
            data.put("action", "open_external_url");
            data.put("message", "已生成微信支付链接，请继续完成支付。");
            data.put("paymentUrl", paymentUrl);
            data.put("orderNo", order.getOrderNo());
            data.put("productCode", order.getProductCode());
            data.put("productName", order.getProductName());
            return data;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            return unavailablePayload(config, "微信支付请求失败，请稍后重试。");
        }
    }

    private String buildRequestBody(AppPaymentOrder order, AppStoreProduct product, String clientIp) throws IOException {
        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("total", nvl(order.getAmountCents()));

        Map<String, Object> h5Info = new LinkedHashMap<>();
        h5Info.put("type", safe(paymentProperties.getH5Type(), "Wap"));

        Map<String, Object> sceneInfo = new LinkedHashMap<>();
        sceneInfo.put("payer_client_ip", clientIp);
        sceneInfo.put("h5_info", h5Info);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appid", paymentProperties.getAppId().trim());
        payload.put("mchid", paymentProperties.getMerchantId().trim());
        payload.put("description", subject(product, order));
        payload.put("out_trade_no", order.getOrderNo());
        payload.put("notify_url", paymentProperties.getNotifyUrl().trim());
        payload.put("amount", amount);
        payload.put("scene_info", sceneInfo);
        return objectMapper.writeValueAsString(payload);
    }

    private String buildAuthorization(PrivateKey privateKey, String requestPath, String requestBody) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String message = "POST\n" + requestPath + "\n" + timestamp + "\n" + nonce + "\n" + requestBody + "\n";
        String signature = PaymentPrivateKeySupport.sign(message, privateKey, "SHA256withRSA");
        return "WECHATPAY2-SHA256-RSA2048 "
                + "mchid=\"" + paymentProperties.getMerchantId().trim() + "\","
                + "nonce_str=\"" + nonce + "\","
                + "timestamp=\"" + timestamp + "\","
                + "serial_no=\"" + paymentProperties.getMerchantSerialNumber().trim() + "\","
                + "signature=\"" + signature + "\"";
    }

    private boolean credentialsReady() {
        return paymentProperties.isEnabled()
                && !blank(paymentProperties.getAppId()).isBlank()
                && !blank(paymentProperties.getMerchantId()).isBlank()
                && !blank(paymentProperties.getMerchantSerialNumber()).isBlank()
                && !blank(paymentProperties.getNotifyUrl()).isBlank()
                && (!blank(paymentProperties.getPrivateKeyPem()).isBlank()
                || !blank(paymentProperties.getPrivateKeyPath()).isBlank());
    }

    private Map<String, Object> unavailablePayload(AppPaymentChannelConfig config, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "wechat_h5");
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
            return "后台已预留微信 H5 通道，当前未对用户开放。";
        }
        if (!credentialsReady) {
            return "微信 H5 通道已开启，但商户参数或证书尚未补齐。";
        }
        return blank(config.getDescription());
    }

    private String extractWechatError(String body) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, MAP_TYPE);
            String code = stringValue(parsed.get("code"));
            String message = stringValue(parsed.get("message"));
            if (!code.isBlank() || !message.isBlank()) {
                return (code + " " + message).trim();
            }
        } catch (Exception ignored) {
        }
        return body == null || body.isBlank() ? "未知错误" : body.trim();
    }

    private String safeUserAgent(String userAgent) {
        return userAgent == null || userAgent.isBlank() ? "JiuGuanSJ/1.0" : userAgent;
    }

    private String subject(AppStoreProduct product, AppPaymentOrder order) {
        String value = product == null ? order.getProductName() : product.getName();
        if (value == null || value.isBlank()) {
            return "会员权益订单";
        }
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
}
