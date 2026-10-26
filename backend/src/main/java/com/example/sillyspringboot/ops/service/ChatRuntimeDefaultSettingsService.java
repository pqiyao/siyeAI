package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application-owned overrides for the ST global/default chat preset.
 * ST-synced preset rows remain snapshots; this setting is the runtime policy.
 */
@Service
public class ChatRuntimeDefaultSettingsService {

    private static final String SETTING_KEY = "chat_runtime_default";
    private static final int LEGACY_MAX_TOKENS = 1000;
    private static final int LEGACY_MAX_CONTEXT = 65536;
    private static final int MAX_MAX_TOKENS = 8192;
    private static final int MAX_MAX_CONTEXT = 300000;

    private final AppRuntimeSettingMapper mapper;
    private final ObjectMapper objectMapper;

    public ChatRuntimeDefaultSettingsService(AppRuntimeSettingMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Settings current() {
        AppRuntimeSetting row = mapper.findByKey(SETTING_KEY);
        if (row == null || row.getSettingValue() == null || row.getSettingValue().isBlank()) {
            return new Settings(LEGACY_MAX_TOKENS, LEGACY_MAX_CONTEXT, "legacy-default");
        }
        try {
            JsonNode root = objectMapper.readTree(row.getSettingValue());
            int maxTokens = integer(root, "maxTokens", LEGACY_MAX_TOKENS);
            int maxContext = integer(root, "maxContext", LEGACY_MAX_CONTEXT);
            validate(maxTokens, maxContext);
            return new Settings(maxTokens, maxContext, "database");
        } catch (BusinessException ex) {
            return new Settings(LEGACY_MAX_TOKENS, LEGACY_MAX_CONTEXT, "legacy-default");
        } catch (Exception ex) {
            return new Settings(LEGACY_MAX_TOKENS, LEGACY_MAX_CONTEXT, "legacy-default");
        }
    }

    @Transactional
    public Settings save(Map<String, Object> body) {
        int maxTokens = integer(body == null ? null : body.get("maxTokens"), LEGACY_MAX_TOKENS);
        int maxContext = integer(body == null ? null : body.get("maxContext"), LEGACY_MAX_CONTEXT);
        validate(maxTokens, maxContext);
        try {
            mapper.upsert(SETTING_KEY, objectMapper.writeValueAsString(Map.of(
                    "maxTokens", maxTokens,
                    "maxContext", maxContext
            )));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "全局聊天默认参数保存失败");
        }
        return new Settings(maxTokens, maxContext, "database");
    }

    public Map<String, Object> toMap(Settings settings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxTokens", settings.maxTokens());
        result.put("maxContext", settings.maxContext());
        result.put("source", settings.source());
        result.put("settingKey", SETTING_KEY);
        return result;
    }

    private static int integer(JsonNode root, String field, int fallback) {
        return root != null && root.has(field) ? root.path(field).asInt(fallback) : fallback;
    }

    private static int integer(Object raw, int fallback) {
        if (raw instanceof Number number) return number.intValue();
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void validate(int maxTokens, int maxContext) {
        if (maxTokens < 64 || maxTokens > MAX_MAX_TOKENS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "maxTokens must be between 64 and " + MAX_MAX_TOKENS);
        }
        if (maxContext < 2048 || maxContext > MAX_MAX_CONTEXT || maxContext < maxTokens + 512) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "maxContext must be between 2048 and " + MAX_MAX_CONTEXT
                            + " and at least maxTokens + 512");
        }
    }

    public record Settings(int maxTokens, int maxContext, String source) {
    }
}
