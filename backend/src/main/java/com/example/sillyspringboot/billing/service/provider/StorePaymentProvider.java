package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;

import java.util.List;
import java.util.Map;

public interface StorePaymentProvider {

    boolean supportsChannel(String channel);

    List<Map<String, Object>> describeChannels();

    Map<String, Object> createPayment(
            String channel,
            AppPaymentOrder order,
            AppStoreProduct product,
            AppUser user,
            AppH5UserProfileExt profile,
            StorePaymentContext context
    );

    default boolean supportsManualSettlement(String channel) {
        return false;
    }

    default Map<String, Object> manualSettlementResult(String channel, AppPaymentOrder order) {
        return Map.of(
                "channel", channel == null ? "" : channel,
                "ready", false,
                "manualSettlement", false
        );
    }
}
