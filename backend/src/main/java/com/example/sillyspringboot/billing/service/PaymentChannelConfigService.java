package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.mapper.AppPaymentChannelConfigMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentChannelConfigService {

    private final AppPaymentChannelConfigMapper mapper;

    public PaymentChannelConfigService(AppPaymentChannelConfigMapper mapper) {
        this.mapper = mapper;
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

        if (existing == null) {
            mapper.insert(target);
        } else {
            target.setId(existing.getId());
            mapper.updateById(target);
        }
        return getRequired(normalized);
    }

    private Map<String, AppPaymentChannelConfig> defaultConfigs() {
        LinkedHashMap<String, AppPaymentChannelConfig> map = new LinkedHashMap<>();
        map.put("wechat_h5", defaultRow("wechat_h5", "微信 H5", "微信公众号/H5 页面拉起微信支付。需要商户号、API v3 密钥与商户证书。", 10, false, false, "生产渠道，建议在商户配置齐全后启用"));
        map.put("alipay_wap", defaultRow("alipay_wap", "支付宝", "手机网站支付，适合 H5 页面直接跳转支付宝收银台。", 20, false, false, "生产渠道，建议在应用审核通过后启用"));
        map.put("telegram_star", defaultRow("telegram_star", "Telegram Stars", "Telegram WebApp/机器人场景数字商品支付。", 30, false, false, "适用于 Telegram 端"));
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
        copy.setCreatedAt(row.getCreatedAt());
        copy.setUpdatedAt(row.getUpdatedAt());
        return copy;
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
}
