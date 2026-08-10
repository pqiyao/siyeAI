package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class StorePaymentProviderRegistry {

    private final List<StorePaymentProvider> providers;

    public StorePaymentProviderRegistry(List<StorePaymentProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public StorePaymentProvider resolveRequired(String channel) {
        String normalized = normalizeChannel(channel);
        return providers.stream()
                .filter(provider -> provider.supportsChannel(normalized))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "支付渠道暂不支持"));
    }

    public List<Map<String, Object>> describeChannels() {
        List<Map<String, Object>> channels = new ArrayList<>();
        for (StorePaymentProvider provider : providers) {
            channels.addAll(provider.describeChannels());
        }
        channels.sort(Comparator.comparingInt(item -> {
            Object value = item.get("sortOrder");
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
                return Integer.MAX_VALUE;
            }
        }));
        return channels;
    }

    private static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }
}
