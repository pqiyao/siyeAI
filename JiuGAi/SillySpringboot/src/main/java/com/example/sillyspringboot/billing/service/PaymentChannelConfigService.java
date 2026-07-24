package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.config.EpayPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.mapper.AppPaymentChannelConfigMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentChannelConfigService {

    private final AppPaymentChannelConfigMapper mapper;
    private final PaymentChannelSecretService secretService;
    private final EpayPaymentProperties epayProperties;

    public PaymentChannelConfigService(
            AppPaymentChannelConfigMapper mapper,
            PaymentChannelSecretService secretService,
            EpayPaymentProperties epayProperties
    ) {
        this.mapper = mapper;
        this.secretService = secretService;
        this.epayProperties = epayProperties;
    }

    @Transactional(readOnly = true)
    public List<AppPaymentChannelConfig> listAll() {
        Map<String, AppPaymentChannelConfig> dbMap = mapper.listAll().stream()
                .collect(Collectors.toMap(
                        row -> normalizeChannelCode(row.getChannelCode()),
                        this::copy,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
        return defaultConfigs().values().stream()
                .map(defaultRow -> merge(defaultRow, dbMap.get(defaultRow.getChannelCode())))
                .sorted((left, right) -> Integer.compare(nvl(left.getSortOrder()), nvl(right.getSortOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminWithMaskedSecrets() {
        return listAdminWithMaskedSecrets(List.of());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminWithMaskedSecrets(List<Map<String, Object>> runtimeChannels) {
        Map<String, Map<String, Object>> runtimeByCode = new LinkedHashMap<>();
        if (runtimeChannels != null) {
            for (Map<String, Object> item : runtimeChannels) {
                if (item == null) {
                    continue;
                }
                String code = stringVal(item.get("code"));
                if (!code.isBlank()) {
                    runtimeByCode.put(code.toLowerCase(Locale.ROOT), item);
                }
            }
        }
        return listAll().stream().map(row -> {
            Map<String, Object> data = toAdminMap(row);
            Map<String, Object> runtime = runtimeByCode.get(normalizeChannelCode(row.getChannelCode()));
            if (runtime != null) {
                data.put("ready", Boolean.TRUE.equals(runtime.get("ready")));
                data.put("provider", stringVal(runtime.get("provider")));
                if (!data.containsKey("manualSettlement")) {
                    data.put("manualSettlement", Boolean.TRUE.equals(runtime.get("manualSettlement")));
                }
            } else {
                data.putIfAbsent("ready", false);
                data.putIfAbsent("provider", "");
            }
            return data;
        }).toList();
    }

    @Transactional(readOnly = true)
    public AppPaymentChannelConfig getRequired(String channelCode) {
        String normalized = normalizeChannelCode(channelCode);
        AppPaymentChannelConfig found = listAll().stream()
                .filter(item -> normalized.equals(item.getChannelCode()))
                .findFirst()
                .orElse(null);
        if (found == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "支付渠道不存在");
        }
        return found;
    }

    @Transactional
    public AppPaymentChannelConfig saveFromAdmin(AppPaymentChannelConfig body) {
        return saveFromAdmin(body, null);
    }

    /** Admin API body 可附 secrets；key 为空字符串时保留旧密钥。 */
    @Transactional
    public Map<String, Object> saveFromAdmin(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少支付渠道配置");
        }
        AppPaymentChannelConfig config = new AppPaymentChannelConfig();
        config.setChannelCode(stringVal(body.get("channelCode")));
        config.setDisplayName(stringVal(body.get("displayName")));
        config.setDescription(stringVal(body.get("description")));
        config.setSortOrder(intVal(body.get("sortOrder")));
        config.setEnabled(boolVal(body.get("enabled")));
        config.setClientVisible(boolVal(body.get("clientVisible")));
        config.setNote(stringVal(body.get("note")));

        @SuppressWarnings("unchecked")
        Map<String, Object> secrets = body.get("secrets") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : extractFlatSecrets(body);

        AppPaymentChannelConfig saved = saveFromAdmin(config, secrets);
        return toAdminMap(saved);
    }

    @Transactional
    public AppPaymentChannelConfig saveFromAdmin(AppPaymentChannelConfig body, Map<String, Object> secretFields) {
        if (body == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少支付渠道配置");
        }
        String normalized = normalizeChannelCode(body.getChannelCode());
        AppPaymentChannelConfig defaults = defaultConfigs().get(normalized);
        if (defaults == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "未知支付渠道");
        }

        AppPaymentChannelConfig existing = mapper.findByChannelCode(normalized);
        AppPaymentChannelConfig target = existing == null ? copy(defaults) : copy(existing);
        target.setChannelCode(normalized);
        target.setDisplayName(trimToDefault(body.getDisplayName(), defaults.getDisplayName()));
        target.setDescription(trimToDefault(body.getDescription(), defaults.getDescription()));
        target.setSortOrder(body.getSortOrder() == null ? defaults.getSortOrder() : body.getSortOrder());
        target.setEnabled(body.getEnabled() != null ? body.getEnabled() : defaults.getEnabled());
        target.setClientVisible(body.getClientVisible() != null ? body.getClientVisible() : defaults.getClientVisible());
        target.setNote(trimToDefault(body.getNote(), defaults.getNote()));

        Map<String, Object> mergedSecrets = new LinkedHashMap<>();
        if (existing != null && existing.getConfigCipher() != null && !existing.getConfigCipher().isBlank()) {
            mergedSecrets.putAll(secretService.decryptConfig(existing.getConfigCipher()));
        }
        if (secretFields != null && !secretFields.isEmpty()) {
            mergeSecretField(mergedSecrets, secretFields, "pid");
            mergeSecretField(mergedSecrets, secretFields, "apiUrl");
            mergeSecretField(mergedSecrets, secretFields, "notifyUrl");
            mergeSecretField(mergedSecrets, secretFields, "returnUrl");
            mergeSecretField(mergedSecrets, secretFields, "typeDefault");
            Object newKey = secretFields.get("key");
            if (newKey != null && !String.valueOf(newKey).isBlank()) {
                mergedSecrets.put("key", String.valueOf(newKey).trim());
            }
            target.setConfigCipher(secretService.encryptConfig(mergedSecrets));
            target.setConfigUpdatedAt(LocalDateTime.now());
        } else if (existing != null) {
            target.setConfigCipher(existing.getConfigCipher());
            target.setConfigUpdatedAt(existing.getConfigUpdatedAt());
        }

        if ("epay".equals(normalized)
                && (Boolean.TRUE.equals(target.getEnabled()) || Boolean.TRUE.equals(target.getClientVisible()))) {
            validateEpayReady(mergedSecrets);
        }

        if (existing == null) {
            mapper.insert(target);
        } else {
            target.setId(existing.getId());
            mapper.updateById(target);
        }
        return getRequired(normalized);
    }

    private Map<String, Object> toAdminMap(AppPaymentChannelConfig row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("channelCode", row.getChannelCode());
        data.put("displayName", row.getDisplayName());
        data.put("description", row.getDescription());
        data.put("sortOrder", nvl(row.getSortOrder()));
        data.put("enabled", Boolean.TRUE.equals(row.getEnabled()));
        data.put("clientVisible", Boolean.TRUE.equals(row.getClientVisible()));
        data.put("note", blank(row.getNote()));
        data.put("configUpdatedAt", row.getConfigUpdatedAt());
        data.put("createdAt", row.getCreatedAt());
        data.put("updatedAt", row.getUpdatedAt());
        Map<String, Object> secrets = Map.of();
        if (row.getConfigCipher() != null && !row.getConfigCipher().isBlank()) {
            try {
                secrets = secretService.decryptConfig(row.getConfigCipher());
            } catch (Exception ignored) {
                secrets = Map.of();
            }
        }
        data.put("secrets", secretService.maskSecrets(secrets));
        data.put("hasSecrets", !secrets.isEmpty());
        data.put("secretConfigured", !secrets.isEmpty());
        data.put("code", blank(row.getChannelCode()));
        data.put("name", blank(row.getDisplayName()));
        data.put("desc", blank(row.getDescription()));
        data.put("apiUrl", stringVal(secrets.get("apiUrl")));
        data.put("notifyUrl", stringVal(secrets.get("notifyUrl")));
        data.put("returnUrl", stringVal(secrets.get("returnUrl")));
        data.put("typeDefault", stringVal(secrets.get("typeDefault")));
        data.put("runtimeOverrideActive", hasEpayRuntimeOverride());
        Object pid = secrets.get("pid");
        data.put("pidMasked", pid == null || String.valueOf(pid).isBlank() ? "" : maskPid(String.valueOf(pid)));
        return data;
    }

    private Map<String, AppPaymentChannelConfig> defaultConfigs() {
        LinkedHashMap<String, AppPaymentChannelConfig> map = new LinkedHashMap<>();
        map.put("wechat_h5", defaultRow("wechat_h5", "微信 H5", "微信公众号/H5 页面拉起微信支付。需要商户号、API v3 密钥与商户证书。", 10, false, false, "生产渠道，建议在商户配置齐全后启用"));
        map.put("alipay_wap", defaultRow("alipay_wap", "支付宝", "手机网站支付，适合 H5 页面直接跳转支付宝收银台。", 20, false, false, "生产渠道，建议在应用审核通过后启用"));
        map.put("telegram_star", defaultRow("telegram_star", "Telegram Stars", "Telegram WebApp/机器人场景数字商品支付。", 30, false, false, "适用于 Telegram 端"));
        map.put("epay", defaultRow("epay", "易支付", "易支付/码支付聚合收银台（支付宝/微信等）。需配置商户 PID 与通信密钥。", 40, false, false, "默认关闭；配置 PID/KEY 并开启后对客户端可见"));
        map.put("mock_wechat", defaultRow("mock_wechat", "模拟微信支付", "开发/测试渠道，确认后直接发放权益。", 90, true, true, "默认仅测试环境可用"));
        map.put("mock_alipay", defaultRow("mock_alipay", "模拟支付宝", "开发/测试渠道，确认后直接发放权益。", 91, true, true, "默认仅测试环境可用"));
        map.put("card_code", defaultRow("card_code", "卡密兑换", "预留的卡密/兑换码支付通道。", 120, false, false, "暂未开放"));
        return map;
    }

    private AppPaymentChannelConfig defaultRow(
            String channelCode,
            String displayName,
            String description,
            int sortOrder,
            boolean enabled,
            boolean clientVisible,
            String note
    ) {
        AppPaymentChannelConfig row = new AppPaymentChannelConfig();
        row.setChannelCode(channelCode);
        row.setDisplayName(displayName);
        row.setDescription(description);
        row.setSortOrder(sortOrder);
        row.setEnabled(enabled);
        row.setClientVisible(clientVisible);
        row.setNote(note);
        return row;
    }

    private AppPaymentChannelConfig merge(AppPaymentChannelConfig defaults, AppPaymentChannelConfig overrides) {
        if (overrides == null) {
            return copy(defaults);
        }
        AppPaymentChannelConfig row = copy(defaults);
        row.setId(overrides.getId());
        row.setDisplayName(trimToDefault(overrides.getDisplayName(), defaults.getDisplayName()));
        row.setDescription(trimToDefault(overrides.getDescription(), defaults.getDescription()));
        row.setSortOrder(overrides.getSortOrder() == null ? defaults.getSortOrder() : overrides.getSortOrder());
        row.setEnabled(overrides.getEnabled() != null ? overrides.getEnabled() : defaults.getEnabled());
        row.setClientVisible(overrides.getClientVisible() != null ? overrides.getClientVisible() : defaults.getClientVisible());
        row.setNote(trimToDefault(overrides.getNote(), defaults.getNote()));
        row.setConfigCipher(overrides.getConfigCipher());
        row.setConfigUpdatedAt(overrides.getConfigUpdatedAt());
        row.setCreatedAt(overrides.getCreatedAt());
        row.setUpdatedAt(overrides.getUpdatedAt());
        return row;
    }

    private AppPaymentChannelConfig copy(AppPaymentChannelConfig row) {
        AppPaymentChannelConfig copy = new AppPaymentChannelConfig();
        copy.setId(row.getId());
        copy.setChannelCode(row.getChannelCode());
        copy.setDisplayName(row.getDisplayName());
        copy.setDescription(row.getDescription());
        copy.setSortOrder(row.getSortOrder());
        copy.setEnabled(row.getEnabled());
        copy.setClientVisible(row.getClientVisible());
        copy.setNote(row.getNote());
        copy.setConfigCipher(row.getConfigCipher());
        copy.setConfigUpdatedAt(row.getConfigUpdatedAt());
        copy.setCreatedAt(row.getCreatedAt());
        copy.setUpdatedAt(row.getUpdatedAt());
        return copy;
    }

    private static void mergeSecretField(Map<String, Object> target, Map<String, Object> incoming, String field) {
        Object value = incoming.get(field);
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            target.put(field, text);
        }
    }

    private void validateEpayReady(Map<String, Object> secrets) {
        String pid = firstNonBlank(epayProperties.getPid(), stringVal(secrets.get("pid")));
        String key = firstNonBlank(epayProperties.getKey(), stringVal(secrets.get("key")));
        String apiUrl = firstNonBlank(epayProperties.getApiUrl(), stringVal(secrets.get("apiUrl")));
        String notifyUrl = firstNonBlank(epayProperties.getNotifyUrl(), stringVal(secrets.get("notifyUrl")));
        String returnUrl = firstNonBlank(epayProperties.getReturnUrl(), stringVal(secrets.get("returnUrl")));
        String payType = firstNonBlank(epayProperties.getDefaultPayType(), stringVal(secrets.get("typeDefault")));
        if (payType.isBlank()) {
            payType = "alipay";
        }
        if (pid.isBlank() || key.isBlank()) {
            throw validation("启用或展示易支付前，必须完整配置商户 PID 和通信密钥");
        }
        if (!isSafePaymentUrl(apiUrl)) {
            throw validation("易支付网关地址无效；正式地址必须使用 HTTPS");
        }
        if (!isSafePaymentUrl(notifyUrl)) {
            throw validation("异步通知地址无效；正式地址必须使用 HTTPS");
        }
        if (!isSafePaymentUrl(returnUrl)) {
            throw validation("同步跳转地址无效；正式地址必须使用 HTTPS");
        }
        String normalizedType = payType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("alipay", "wxpay").contains(normalizedType)) {
            throw validation("易支付默认支付类型仅支持 alipay 或 wxpay");
        }
    }

    private boolean hasEpayRuntimeOverride() {
        return !stringVal(epayProperties.getPid()).isBlank()
                || !stringVal(epayProperties.getKey()).isBlank()
                || !stringVal(epayProperties.getApiUrl()).isBlank()
                || !stringVal(epayProperties.getNotifyUrl()).isBlank()
                || !stringVal(epayProperties.getReturnUrl()).isBlank()
                || !stringVal(epayProperties.getDefaultPayType()).isBlank();
    }

    private static boolean isSafePaymentUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (!uri.isAbsolute() || host == null || host.isBlank()
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                return false;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return true;
            }
            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static Map<String, Object> extractFlatSecrets(Map<String, Object> body) {
        Map<String, Object> secrets = new LinkedHashMap<>();
        for (String key : List.of("pid", "key", "apiUrl", "notifyUrl", "returnUrl", "typeDefault")) {
            if (body.containsKey(key)) {
                secrets.put(key, body.get(key));
            }
        }
        return secrets;
    }

    private static String normalizeChannelCode(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            return "";
        }
        return channelCode.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String maskPid(String pid) {
        String value = pid == null ? "" : pid.trim();
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Integer intVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Boolean boolVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }
}
