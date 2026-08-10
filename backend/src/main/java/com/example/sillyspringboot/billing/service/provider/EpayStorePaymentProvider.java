package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.billing.config.EpayPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.billing.service.PaymentChannelConfigService;
import com.example.sillyspringboot.billing.service.PaymentChannelSecretService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class EpayStorePaymentProvider implements StorePaymentProvider {

    private static final String CHANNEL_CODE = "epay";
    private static final String DEFAULT_PAY_TYPE = "alipay";
    private static final Set<String> SUPPORTED_PAY_TYPES = Set.of("alipay", "wxpay");

    private final PaymentChannelConfigService channelConfigService;
    private final PaymentChannelSecretService secretService;
    private final EpayPaymentProperties paymentProperties;

    public EpayStorePaymentProvider(
            PaymentChannelConfigService channelConfigService,
            PaymentChannelSecretService secretService,
            EpayPaymentProperties paymentProperties
    ) {
        this.channelConfigService = channelConfigService;
        this.secretService = secretService;
        this.paymentProperties = paymentProperties;
    }

    @Override
    public boolean supportsChannel(String channel) {
        return CHANNEL_CODE.equals(channel);
    }

    @Override
    public List<Map<String, Object>> describeChannels() {
        AppPaymentChannelConfig config = channelConfigService.getRequired(CHANNEL_CODE);
        ResolvedCredentials credentials = resolveCredentials(config);
        boolean credentialsReady = credentials.ready();
        boolean ready = Boolean.TRUE.equals(config.getEnabled()) && credentialsReady;
        return List.of(Map.of(
                "code", CHANNEL_CODE,
                "name", config.getDisplayName(),
                "desc", channelDescription(config, credentialsReady),
                "provider", "epay",
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
            return unavailablePayload(config, "易支付渠道未启用");
        }
        ResolvedCredentials credentials = resolveCredentials(config);
        if (!credentials.ready()) {
            return unavailablePayload(config, "易支付渠道配置不完整或无效");
        }

        String payType = credentials.payType();
        String money = EpaySignSupport.moneyYuan(nvl(order.getAmountCents()));
        Map<String, String> params = new TreeMap<>();
        params.put("pid", credentials.pid());
        params.put("type", payType);
        params.put("out_trade_no", order.getOrderNo());
        params.put("notify_url", credentials.notifyUrl());
        params.put("return_url", credentials.returnUrl());
        params.put("name", blank(order.getProductName()).isBlank() ? "酒馆商品" : order.getProductName());
        params.put("money", money);

        String sign = EpaySignSupport.sign(params, credentials.key());
        params.put("sign_type", "MD5");
        params.put("sign", sign);

        String base = trimTrailingSlashes(credentials.apiUrl());
        String submitPath = base.toLowerCase(Locale.ROOT).endsWith("/submit.php")
                ? base
                : base + "/submit.php";
        String paymentUrl = submitPath + "?" + params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "epay");
        data.put("channel", channel);
        data.put("ready", true);
        data.put("action", "open_external_url");
        data.put("paymentUrl", paymentUrl);
        data.put("orderNo", order.getOrderNo());
        data.put("amountYuan", money);
        data.put("message", "请在易支付收银台完成支付");
        return data;
    }

    ResolvedCredentials resolveCredentials(AppPaymentChannelConfig config) {
        Map<String, Object> secrets = Map.of();
        if (config != null && config.getConfigCipher() != null && !config.getConfigCipher().isBlank()) {
            try {
                secrets = secretService.decryptConfig(config.getConfigCipher());
            } catch (Exception ignored) {
                secrets = Map.of();
            }
        }
        String pid = firstNonBlank(paymentProperties.getPid(), stringVal(secrets.get("pid")));
        String key = firstNonBlank(paymentProperties.getKey(), stringVal(secrets.get("key")));
        String apiUrl = firstNonBlank(paymentProperties.getApiUrl(), stringVal(secrets.get("apiUrl")));
        String notifyUrl = firstNonBlank(paymentProperties.getNotifyUrl(), stringVal(secrets.get("notifyUrl")));
        String returnUrl = firstNonBlank(paymentProperties.getReturnUrl(), stringVal(secrets.get("returnUrl")));
        String requestedPayType = firstNonBlank(
                paymentProperties.getDefaultPayType(),
                stringVal(secrets.get("typeDefault"))
        );
        String payType = requestedPayType.isBlank()
                ? DEFAULT_PAY_TYPE
                : normalizePayType(requestedPayType);
        boolean ready = !pid.isBlank()
                && !key.isBlank()
                && isValidHttpUrl(apiUrl, false)
                && isValidHttpUrl(notifyUrl, true)
                && isValidHttpUrl(returnUrl, true)
                && SUPPORTED_PAY_TYPES.contains(payType);
        return new ResolvedCredentials(pid, key, apiUrl, notifyUrl, returnUrl, payType, ready);
    }

    public ResolvedCredentials resolveCredentialsForNotify() {
        return resolveCredentials(channelConfigService.getRequired(CHANNEL_CODE));
    }

    private Map<String, Object> unavailablePayload(AppPaymentChannelConfig config, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "epay");
        data.put("channel", CHANNEL_CODE);
        data.put("ready", false);
        data.put("action", "await_provider_config");
        data.put("message", message);
        data.put("note", blank(config.getNote()));
        return data;
    }

    private static String channelDescription(AppPaymentChannelConfig config, boolean credentialsReady) {
        String base = blank(config.getDescription());
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return base + "（未启用）";
        }
        if (!credentialsReady) {
            return base + "（配置未完成或无效）";
        }
        return base;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String normalizePayType(String value) {
        String normalized = blank(value).trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_PAY_TYPES.contains(normalized) ? normalized : "";
    }

    private static boolean isValidHttpUrl(String value, boolean allowQuery) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = blank(uri.getScheme()).toLowerCase(Locale.ROOT);
            String host = blank(uri.getHost());
            boolean secureScheme = "https".equals(scheme)
                    || ("http".equals(scheme) && isLoopbackHost(host));
            return secureScheme
                    && uri.isAbsolute()
                    && !host.isBlank()
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null
                    && (allowQuery || uri.getRawQuery() == null);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static String trimTrailingSlashes(String value) {
        String result = blank(value).trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    public record ResolvedCredentials(
            String pid,
            String key,
            String apiUrl,
            String notifyUrl,
            String returnUrl,
            String payType,
            boolean ready
    ) {
        public boolean notifyReady() {
            return pid != null && !pid.isBlank() && key != null && !key.isBlank();
        }
    }
}
