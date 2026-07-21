package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.auth.entity.AppUser;
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
public class CardCodeStorePaymentProvider implements StorePaymentProvider {

    private static final String CHANNEL_CODE = "card_code";

    private final PaymentChannelConfigService channelConfigService;

    public CardCodeStorePaymentProvider(PaymentChannelConfigService channelConfigService) {
        this.channelConfigService = channelConfigService;
    }

    @Override
    public boolean supportsChannel(String channel) {
        return CHANNEL_CODE.equals(channel);
    }

    @Override
    public List<Map<String, Object>> describeChannels() {
        AppPaymentChannelConfig config = channelConfigService.getRequired(CHANNEL_CODE);
        return List.of(
                Map.of(
                        "code", CHANNEL_CODE,
                        "name", config.getDisplayName(),
                        "desc", config.getDescription(),
                        "provider", "card_code",
                        "enabled", Boolean.TRUE.equals(config.getEnabled()),
                        "ready", false,
                        "manualSettlement", false,
                        "clientVisible", Boolean.TRUE.equals(config.getClientVisible()),
                        "sortOrder", config.getSortOrder() == null ? 0 : config.getSortOrder(),
                        "note", config.getNote() == null ? "" : config.getNote()
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
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "card_code");
        data.put("channel", channel);
        data.put("ready", false);
        data.put("manualSettlement", false);
        data.put("action", "await_card_code_flow");
        data.put("message", "卡密兑换通道已预留，后续可接兑换码核销逻辑。");
        data.put("orderNo", order.getOrderNo());
        data.put("productCode", order.getProductCode());
        data.put("productName", order.getProductName());
        return data;
    }
}
