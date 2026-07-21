package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.billing.config.MockPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.billing.service.PaymentChannelConfigService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MockStorePaymentProvider implements StorePaymentProvider {

    private final PaymentChannelConfigService channelConfigService;
    private final MockPaymentProperties paymentProperties;
    private final AppProperties appProperties;

    public MockStorePaymentProvider(
            PaymentChannelConfigService channelConfigService,
            MockPaymentProperties paymentProperties,
            AppProperties appProperties
    ) {
        this.channelConfigService = channelConfigService;
        this.paymentProperties = paymentProperties;
        this.appProperties = appProperties;
    }

    @Override
    public boolean supportsChannel(String channel) {
        return "mock_wechat".equals(channel) || "mock_alipay".equals(channel);
    }

    @Override
    public List<Map<String, Object>> describeChannels() {
        boolean runtimeReady = runtimeReady();
        AppPaymentChannelConfig wechat = channelConfigService.getRequired("mock_wechat");
        AppPaymentChannelConfig alipay = channelConfigService.getRequired("mock_alipay");
        return List.of(
                Map.of(
                        "code", "mock_wechat",
                        "name", wechat.getDisplayName(),
                        "desc", channelDesc(wechat, runtimeReady),
                        "provider", "mock",
                        "enabled", Boolean.TRUE.equals(wechat.getEnabled()),
                        "ready", Boolean.TRUE.equals(wechat.getEnabled()) && runtimeReady,
                        "manualSettlement", true,
                        "clientVisible", Boolean.TRUE.equals(wechat.getClientVisible()),
                        "sortOrder", safeSort(wechat),
                        "note", blank(wechat.getNote())
                ),
                Map.of(
                        "code", "mock_alipay",
                        "name", alipay.getDisplayName(),
                        "desc", channelDesc(alipay, runtimeReady),
                        "provider", "mock",
                        "enabled", Boolean.TRUE.equals(alipay.getEnabled()),
                        "ready", Boolean.TRUE.equals(alipay.getEnabled()) && runtimeReady,
                        "manualSettlement", true,
                        "clientVisible", Boolean.TRUE.equals(alipay.getClientVisible()),
                        "sortOrder", safeSort(alipay),
                        "note", blank(alipay.getNote())
                )
        );
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
        AppPaymentChannelConfig config = channelConfigService.getRequired(channel);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return Map.of(
                    "provider", "mock",
                    "channel", channel,
                    "ready", false,
                    "manualSettlement", true,
                    "action", "await_provider_config",
                    "message", "模拟支付通道尚未开启。"
            );
        }
        if (!runtimeReady()) {
            return Map.of(
                    "provider", "mock",
                    "channel", channel,
                    "ready", false,
                    "manualSettlement", true,
                    "action", "await_provider_config",
                    "message", "当前环境已禁用模拟支付，请改用真实支付通道。"
            );
        }
        return Map.of(
                "provider", "mock",
                "channel", channel,
                "ready", true,
                "manualSettlement", true,
                "action", "mock_pay",
                "buttonText", "确认模拟支付",
                "message", "当前为开发/测试模拟支付，确认后会立即发放权益。",
                "orderNo", order.getOrderNo()
        );
    }

    @Override
    public boolean supportsManualSettlement(String channel) {
        if (!supportsChannel(channel)) {
            return false;
        }
        AppPaymentChannelConfig config = channelConfigService.getRequired(channel);
        return Boolean.TRUE.equals(config.getEnabled()) && runtimeReady();
    }

    @Override
    public Map<String, Object> manualSettlementResult(String channel, AppPaymentOrder order) {
        return Map.of(
                "provider", "mock",
                "channel", channel,
                "ready", true,
                "manualSettlement", true,
                "action", "mock_pay",
                "message", "模拟支付已完成。",
                "orderNo", order.getOrderNo()
        );
    }

    private boolean runtimeReady() {
        if (!paymentProperties.isEnabled()) {
            return false;
        }
        String env = appProperties.getEnvironment() == null ? "" : appProperties.getEnvironment().trim().toLowerCase();
        boolean prodLike = "prod".equals(env) || "production".equals(env);
        return !prodLike || paymentProperties.isAllowInProd();
    }

    private String channelDesc(AppPaymentChannelConfig config, boolean runtimeReady) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return "后台已保留该模拟通道，但当前未对用户开放。";
        }
        if (!runtimeReady) {
            return "当前环境已禁用模拟支付，请改用真实支付通道。";
        }
        return blank(config.getDescription());
    }

    private static int safeSort(AppPaymentChannelConfig config) {
        return config.getSortOrder() == null ? 0 : config.getSortOrder();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
