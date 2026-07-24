package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.config.AiRoutingProperties;
import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AiRoutingRuntimeSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AiRoutingRuntimeSettingsService.class);
    private static final String SETTING_KEY = "ai_routing_runtime_settings";
    private static final long CACHE_MILLIS = 2_000L;

    public record Settings(
            boolean enabled,
            boolean shadowEnabled,
            int chatCanaryPercent,
            boolean imageEnabled,
            boolean ttsEnabled,
            boolean sttEnabled,
            String source
    ) {}

    private final AppRuntimeSettingMapper mapper;
    private final AiRoutingProperties defaults;
    private final ObjectMapper objectMapper;
    private volatile Settings cached;
    private volatile long cacheExpiresAt;

    public AiRoutingRuntimeSettingsService(
            AppRuntimeSettingMapper mapper,
            AiRoutingProperties defaults,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.defaults = defaults;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Settings current() {
        long now = System.currentTimeMillis();
        Settings current = cached;
        if (current != null && now < cacheExpiresAt) {
            return current;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (cached != null && now < cacheExpiresAt) return cached;
            Settings loaded = loadOrDefault();
            cached = loaded;
            cacheExpiresAt = now + CACHE_MILLIS;
            return loaded;
        }
    }

    @Transactional
    public Settings save(Map<String, Object> body) {
        if (!bool(body == null ? null : body.get("confirmed"), false)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "修改运行开关前必须明确确认");
        }
        Settings current = current();
        Settings next = new Settings(
                bool(body.get("enabled"), current.enabled()),
                bool(body.get("shadowEnabled"), current.shadowEnabled()),
                integer(body.get("chatCanaryPercent"), current.chatCanaryPercent(), 0, 100),
                bool(body.get("imageEnabled"), current.imageEnabled()),
                bool(body.get("ttsEnabled"), current.ttsEnabled()),
                bool(body.get("sttEnabled"), current.sttEnabled()),
                "database"
        );
        try {
            mapper.upsert(SETTING_KEY, objectMapper.writeValueAsString(toMap(next)));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 路由运行开关保存失败");
        }
        cached = next;
        cacheExpiresAt = System.currentTimeMillis() + CACHE_MILLIS;
        return next;
    }

    @Transactional
    public Settings resetToEnvironment() {
        mapper.deleteByKey(SETTING_KEY);
        Settings fallback = environmentDefaults();
        cached = fallback;
        cacheExpiresAt = System.currentTimeMillis() + CACHE_MILLIS;
        return fallback;
    }

    public Map<String, Object> toMap(Settings value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", value.enabled());
        result.put("shadowEnabled", value.shadowEnabled());
        result.put("chatCanaryPercent", value.chatCanaryPercent());
        result.put("imageEnabled", value.imageEnabled());
        result.put("ttsEnabled", value.ttsEnabled());
        result.put("sttEnabled", value.sttEnabled());
        result.put("source", value.source());
        result.put("byokFallbackToOfficial", false);
        return result;
    }

    private Settings loadOrDefault() {
        Settings fallback = environmentDefaults();
        try {
            AppRuntimeSetting row = mapper.findByKey(SETTING_KEY);
            if (row == null || row.getSettingValue() == null || row.getSettingValue().isBlank()) return fallback;
            JsonNode root = objectMapper.readTree(row.getSettingValue());
            return new Settings(
                    root.path("enabled").asBoolean(fallback.enabled()),
                    root.path("shadowEnabled").asBoolean(fallback.shadowEnabled()),
                    Math.max(0, Math.min(100, root.path("chatCanaryPercent").asInt(fallback.chatCanaryPercent()))),
                    root.path("imageEnabled").asBoolean(fallback.imageEnabled()),
                    root.path("ttsEnabled").asBoolean(fallback.ttsEnabled()),
                    root.path("sttEnabled").asBoolean(fallback.sttEnabled()),
                    "database"
            );
        } catch (Exception ex) {
            log.warn("cannot load AI routing runtime settings; using last safe defaults reason={}", ex.getClass().getSimpleName());
            return cached == null ? fallback : cached;
        }
    }

    private Settings environmentDefaults() {
        return new Settings(
                defaults.isEnabled(), defaults.isShadowEnabled(), defaults.getChatCanaryPercent(),
                defaults.isImageEnabled(), defaults.isTtsEnabled(), defaults.isSttEnabled(), "environment");
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(Object value, int fallback, int min, int max) {
        int parsed = fallback;
        if (value instanceof Number number) parsed = number.intValue();
        else if (value != null) {
            try { parsed = Integer.parseInt(String.valueOf(value).trim()); } catch (NumberFormatException ignored) {}
        }
        return Math.max(min, Math.min(max, parsed));
    }
}
