package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.billing.service.PaymentChannelConfigService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TelegramStarsStorePaymentProvider implements StorePaymentProvider {

    private final PaymentChannelConfigService channelConfigService;
    private final TelegramStarsPaymentProperties paymentProperties;
    private final TelegramStarsBotClient botClient;

    public TelegramStarsStorePaymentProvider(
            PaymentChannelConfigService channelConfigService,
            TelegramStarsPaymentProperties paymentProperties,
            TelegramStarsBotClient botClient
    ) {
        this.channelConfigService = channelConfigService;
        this.paymentProperties = paymentProperties;
        this.botClient = botClient;
    }

    @Override
    public boolean supportsChannel(String channel) {
        return "telegram_star".equals(channel) || "telegram_stars".equals(channel);
    }

    @Override
    public List<Map<String, Object>> describeChannels() {
        AppPaymentChannelConfig config = channelConfigService.getRequired("telegram_star");
        boolean ready = Boolean.TRUE.equals(config.getEnabled()) && paymentProperties.isEnabled() && botClient.hasBotToken();
        return List.of(
                Map.of(
                        "code", "telegram_star",
                        "name", config.getDisplayName(),
                        "desc", channelDescription(config, ready),
                        "provider", "telegram_stars",
                        "enabled", Boolean.TRUE.equals(config.getEnabled()),
                        "ready", ready,
                        "manualSettlement", false,
                        "clientVisible", Boolean.TRUE.equals(config.getClientVisible()),
                        "sortOrder", config.getSortOrder() == null ? 0 : config.getSortOrder(),
                        "note", blank(config.getNote())
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
        AppPaymentChannelConfig config = channelConfigService.getRequired("telegram_star");
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("provider", "telegram_stars");
            data.put("channel", channel);
            data.put("ready", false);
            data.put("manualSettlement", false);
            data.put("action", "await_provider_config");
            data.put("message", "Telegram Stars 通道尚未开启。");
            return data;
        }

        boolean enabled = paymentProperties.isEnabled();
        boolean hasBotToken = botClient.hasBotToken();
        int suggestedStars = Math.max(1, centsToSuggestedStars(order.getAmountCents()));
        String invoiceLink = "";
        String createError = "";
        if (enabled && hasBotToken) {
            try {
                invoiceLink = botClient.createInvoiceLink(
                        invoiceTitle(product, order),
                        invoiceDescription(product, order),
                        "store:" + order.getOrderNo(),
                        suggestedStars
                );
            } catch (Exception ex) {
                createError = ex.getMessage() == null ? "" : ex.getMessage().trim();
            }
        }
        boolean readyToOpen = enabled && hasBotToken && !invoiceLink.isBlank();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", user.getId());
        payload.put("productCode", order.getProductCode());
        payload.put("productType", order.getProductType());
        payload.put("provider", "telegram_stars");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "telegram_stars");
        data.put("channel", channel);
        data.put("ready", readyToOpen);
        data.put("manualSettlement", false);
        data.put("action", resolveAction(enabled, hasBotToken, readyToOpen));
        data.put("message", resolveMessage(enabled, hasBotToken, readyToOpen, createError));
        data.put("currency", "XTR");
        data.put("botUsername", botClient.botUsername());
        data.put("orderNo", order.getOrderNo());
        data.put("productCode", order.getProductCode());
        data.put("productName", order.getProductName());
        data.put("invoiceLink", invoiceLink);
        data.put("payload", payload);
        data.put("suggestedStars", suggestedStars);
        data.put("nextSteps", List.of(
                "后端调用 Telegram Bot API createInvoiceLink 生成 Stars 发票链接",
                "前端收到 invoiceLink 后，优先使用 Telegram.WebApp.openInvoice 拉起支付",
                "支付成功后通过 Telegram webhook 回调核销订单并发放权益"
        ));
        return data;
    }

    private String channelDescription(AppPaymentChannelConfig config, boolean ready) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return "后台已预留 Telegram Stars 通道，当前未对用户开放。";
        }
        if (ready) {
            return blank(config.getDescription());
        }
        return "Telegram Stars 通道已开启，但 Bot Token 或支付配置尚未补齐。";
    }

    private String resolveAction(boolean enabled, boolean hasBotToken, boolean readyToOpen) {
        if (!enabled) {
            return "await_provider_config";
        }
        if (!hasBotToken) {
            return "await_bot_token";
        }
        return readyToOpen ? "open_invoice" : "await_invoice_link";
    }

    private String resolveMessage(boolean enabled, boolean hasBotToken, boolean readyToOpen, String createError) {
        if (!enabled) {
            return "Telegram Stars 尚未启用，请先打开支付配置。";
        }
        if (!hasBotToken) {
            return "Telegram Bot Token 未配置，暂时无法生成 Stars 发票。";
        }
        if (readyToOpen) {
            return "Telegram Stars 发票链接已生成，可直接拉起支付。";
        }
        if (!createError.isBlank()) {
            return createError;
        }
        return "Telegram Stars 发票生成失败，请稍后重试。";
    }

    private String invoiceTitle(AppStoreProduct product, AppPaymentOrder order) {
        String title = product != null ? safe(product.getName()) : safe(order.getProductName());
        return title.isBlank() ? "会员权益订单" : title;
    }

    private String invoiceDescription(AppStoreProduct product, AppPaymentOrder order) {
        String description = product != null ? safe(product.getSubtitle()) : "";
        if (description.isBlank()) {
            description = "开通会员、购买钻石或角色权益商品";
        }
        if (order.getOrderNo() != null && !order.getOrderNo().isBlank()) {
            description += " · 订单号 " + order.getOrderNo();
        }
        return description;
    }

    private static int centsToSuggestedStars(Integer amountCents) {
        int cents = nvl(amountCents);
        return Math.max(1, (int) Math.ceil(cents / 100.0d));
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
