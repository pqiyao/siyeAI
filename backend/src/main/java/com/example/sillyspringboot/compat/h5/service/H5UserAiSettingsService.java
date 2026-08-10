package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.ai.service.AiChatModelService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class H5UserAiSettingsService {

    private final H5UserAiProviderService providerService;
    private final AiChatModelService chatModelService;
    private final H5EntitlementService entitlementService;

    public H5UserAiSettingsService(
            H5UserAiProviderService providerService,
            AiChatModelService chatModelService,
            H5EntitlementService entitlementService
    ) {
        this.providerService = providerService;
        this.chatModelService = chatModelService;
        this.entitlementService = entitlementService;
    }

    @Transactional
    public Map<String, Object> save(String clientUid, Map<String, Object> body) {
        Map<String, Object> provider = childMap(body, "provider");
        Map<String, Object> chatModels = childMap(body, "chatModels");
        synchronizeDefaultModel(provider, chatModels);

        long userId = entitlementService.resolveUser(clientUid).getId();
        H5UserAiProviderService.UserAiProviderView providerView = providerService.save(clientUid, provider);
        List<Map<String, Object>> modelViews = chatModelService.saveUserModels(userId, chatModels);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", providerView);
        result.put("chatModels", modelViews);
        return result;
    }

    private static void synchronizeDefaultModel(
            Map<String, Object> provider,
            Map<String, Object> chatModels
    ) {
        String mode = text(provider.get("mode"));
        String providerModel = text(provider.get("modelName"));
        if (!"custom".equalsIgnoreCase(mode) || providerModel.isBlank()) return;

        Object rawModels = chatModels.get("models");
        List<?> models = rawModels instanceof List<?> list ? list : List.of();
        boolean presentAndEnabled = models.stream().anyMatch(value -> {
            if (!(value instanceof Map<?, ?> model)) return false;
            return providerModel.equalsIgnoreCase(text(model.get("modelName")))
                    && !Boolean.FALSE.equals(model.get("enabled"));
        });
        if (!presentAndEnabled) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "当前聊天模型必须保留在已启用的模型库中"
            );
        }
        chatModels.put("defaultModelId", null);
        chatModels.put("defaultModelName", providerModel);
    }

    private static Map<String, Object> childMap(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (!(value instanceof Map<?, ?> raw)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, key + " 不能为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((itemKey, itemValue) -> {
            if (itemKey != null) result.put(String.valueOf(itemKey), itemValue);
        });
        return result;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
