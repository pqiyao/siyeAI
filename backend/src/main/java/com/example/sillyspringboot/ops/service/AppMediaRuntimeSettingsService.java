package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.chat.config.AppMediaProperties;
import com.example.sillyspringboot.ops.dto.AppMediaRuntimeSettings;
import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppMediaRuntimeSettingsService {

    private static final String SETTING_KEY = "app_media_runtime_settings";

    private final AppRuntimeSettingMapper runtimeSettingMapper;
    private final AppMediaProperties properties;
    private final ObjectMapper objectMapper;

    public AppMediaRuntimeSettingsService(
            AppRuntimeSettingMapper runtimeSettingMapper,
            AppMediaProperties properties,
            ObjectMapper objectMapper
    ) {
        this.runtimeSettingMapper = runtimeSettingMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AppMediaRuntimeSettings getSettings() {
        AppMediaRuntimeSettings defaults = defaults();
        AppRuntimeSetting raw = runtimeSettingMapper.findByKey(SETTING_KEY);
        if (raw == null || raw.getSettingValue() == null || raw.getSettingValue().isBlank()) {
            return defaults;
        }
        try {
            return sanitize(objectMapper.readValue(raw.getSettingValue(), AppMediaRuntimeSettings.class), defaults);
        } catch (Exception ignored) {
            return defaults;
        }
    }

    @Transactional
    public AppMediaRuntimeSettings saveSettings(Map<String, Object> body) {
        AppMediaRuntimeSettings settings = getSettings();
        if (body != null) {
            settings.setCounterTtlSeconds(intValue(body.get("counterTtlSeconds"), settings.getCounterTtlSeconds(), 10, 7200));
            settings.setRateWindowSeconds(intValue(body.get("rateWindowSeconds"), settings.getRateWindowSeconds(), 10, 3600));
            applyLimits(settings.getTts(), nested(body.get("tts")));
            applyLimits(settings.getStt(), nested(body.get("stt")));
            applyLimits(settings.getVoiceClone(), nested(body.get("voiceClone")));
        }
        settings = sanitize(settings, defaults());
        try {
            runtimeSettingMapper.upsert(SETTING_KEY, objectMapper.writeValueAsString(settings));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音运行策略保存失败");
        }
        return settings;
    }

    public Map<String, Object> toMap(AppMediaRuntimeSettings settings) {
        AppMediaRuntimeSettings safe = sanitize(settings, defaults());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("counterTtlSeconds", safe.getCounterTtlSeconds());
        data.put("rateWindowSeconds", safe.getRateWindowSeconds());
        data.put("tts", limitsMap(safe.getTts()));
        data.put("stt", limitsMap(safe.getStt()));
        data.put("voiceClone", limitsMap(safe.getVoiceClone()));
        return data;
    }

    private AppMediaRuntimeSettings defaults() {
        AppMediaRuntimeSettings settings = new AppMediaRuntimeSettings();
        settings.setCounterTtlSeconds(properties.getCounterTtlSeconds());
        settings.setRateWindowSeconds(properties.getRateWindowSeconds());
        settings.setTts(copy(properties.getTts()));
        settings.setStt(copy(properties.getStt()));
        settings.setVoiceClone(new AppMediaRuntimeSettings.Limits(3, 1, 3));
        return settings;
    }

    private static AppMediaRuntimeSettings.Limits copy(AppMediaProperties.Limits limits) {
        return new AppMediaRuntimeSettings.Limits(
                limits.getGlobalConcurrentLimit(),
                limits.getPerUserConcurrentLimit(),
                limits.getPerUserRequestsPerWindow());
    }

    private static AppMediaRuntimeSettings sanitize(
            AppMediaRuntimeSettings settings,
            AppMediaRuntimeSettings fallback
    ) {
        AppMediaRuntimeSettings safe = settings == null ? fallback : settings;
        safe.setCounterTtlSeconds(clamp(safe.getCounterTtlSeconds(), 10, 7200, fallback.getCounterTtlSeconds()));
        safe.setRateWindowSeconds(clamp(safe.getRateWindowSeconds(), 10, 3600, fallback.getRateWindowSeconds()));
        safe.setTts(sanitizeLimits(safe.getTts(), fallback.getTts()));
        safe.setStt(sanitizeLimits(safe.getStt(), fallback.getStt()));
        safe.setVoiceClone(sanitizeLimits(safe.getVoiceClone(), fallback.getVoiceClone()));
        return safe;
    }

    private static AppMediaRuntimeSettings.Limits sanitizeLimits(
            AppMediaRuntimeSettings.Limits limits,
            AppMediaRuntimeSettings.Limits fallback
    ) {
        AppMediaRuntimeSettings.Limits safe = limits == null ? fallback : limits;
        safe.setGlobalConcurrentLimit(clamp(safe.getGlobalConcurrentLimit(), 1, 128, fallback.getGlobalConcurrentLimit()));
        safe.setPerUserConcurrentLimit(clamp(safe.getPerUserConcurrentLimit(), 1, 8, fallback.getPerUserConcurrentLimit()));
        safe.setPerUserRequestsPerWindow(clamp(safe.getPerUserRequestsPerWindow(), 1, 300, fallback.getPerUserRequestsPerWindow()));
        return safe;
    }

    private static void applyLimits(AppMediaRuntimeSettings.Limits target, Map<String, Object> body) {
        if (body == null) return;
        target.setGlobalConcurrentLimit(intValue(body.get("globalConcurrentLimit"), target.getGlobalConcurrentLimit(), 1, 128));
        target.setPerUserConcurrentLimit(intValue(body.get("perUserConcurrentLimit"), target.getPerUserConcurrentLimit(), 1, 8));
        target.setPerUserRequestsPerWindow(intValue(body.get("perUserRequestsPerWindow"), target.getPerUserRequestsPerWindow(), 1, 300));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static Map<String, Object> limitsMap(AppMediaRuntimeSettings.Limits limits) {
        return Map.of(
                "globalConcurrentLimit", limits.getGlobalConcurrentLimit(),
                "perUserConcurrentLimit", limits.getPerUserConcurrentLimit(),
                "perUserRequestsPerWindow", limits.getPerUserRequestsPerWindow());
    }

    private static int intValue(Object value, int fallback, int min, int max) {
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private static int clamp(int value, int min, int max, int fallback) {
        int raw = value <= 0 ? fallback : value;
        return Math.max(min, Math.min(max, raw));
    }
}
