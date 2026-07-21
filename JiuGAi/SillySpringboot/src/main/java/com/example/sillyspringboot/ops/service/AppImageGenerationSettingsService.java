package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.crypto.SensitiveTextCrypto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppImageGenerationSettingsService {

    private static final String SETTING_KEY = "app_image_generation_settings";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppRuntimeSettingMapper runtimeSettingMapper;
    private final AppImageGenerationProperties properties;
    private final SensitiveTextCrypto sensitiveTextCrypto;

    public AppImageGenerationSettingsService(
            AppRuntimeSettingMapper runtimeSettingMapper,
            AppImageGenerationProperties properties,
            SensitiveTextCrypto sensitiveTextCrypto
    ) {
        this.runtimeSettingMapper = runtimeSettingMapper;
        this.properties = properties;
        this.sensitiveTextCrypto = sensitiveTextCrypto;
    }

    @Transactional(readOnly = true)
    public AppImageGenerationSettings getSettings() {
        AppImageGenerationSettings defaults = defaultsFromProperties();
        AppRuntimeSetting raw = runtimeSettingMapper.findByKey(SETTING_KEY);
        if (raw == null || raw.getSettingValue() == null || raw.getSettingValue().isBlank()) {
            return defaults;
        }
        try {
            AppImageGenerationSettings loaded = objectMapper.readValue(raw.getSettingValue(), AppImageGenerationSettings.class);
            return sanitize(loaded, defaults);
        } catch (Exception ignored) {
            return defaults;
        }
    }

    @Transactional
    public AppImageGenerationSettings saveSettings(Map<String, Object> body) {
        AppImageGenerationSettings settings = getSettings();
        if (body != null) {
            settings.setEngine(normalizeEngine(str(body.get("engine"), settings.getEngine())));
            settings.setGlobalConcurrentLimit(intVal(body.get("globalConcurrentLimit"), settings.getGlobalConcurrentLimit(), 1, 64));
            settings.setPerUserConcurrentLimit(intVal(body.get("perUserConcurrentLimit"), settings.getPerUserConcurrentLimit(), 1, 8));
            settings.setCounterTtlSeconds(intVal(body.get("counterTtlSeconds"), settings.getCounterTtlSeconds(), 10, 7200));
            settings.setManagedProviderSource(str(body.get("managedProviderSource"), settings.getManagedProviderSource()));
            settings.setManagedImageModelName(str(body.get("managedImageModelName"), settings.getManagedImageModelName()));
            settings.setManagedCustomUrl(str(body.get("managedCustomUrl"), settings.getManagedCustomUrl()));
            if (Boolean.TRUE.equals(body.get("managedApiKeyClear"))) {
                settings.setManagedApiKeyCipher("");
            } else if (body.containsKey("managedApiKey") && StringUtils.hasText(str(body.get("managedApiKey"), ""))) {
                settings.setManagedApiKeyCipher(sensitiveTextCrypto.encrypt(str(body.get("managedApiKey"), "")));
            }
            settings.setComfyUrl(str(body.get("comfyUrl"), settings.getComfyUrl()));
            settings.setWorkflow(str(body.get("workflow"), settings.getWorkflow()));
            settings.setReferenceWorkflow(str(body.get("referenceWorkflow"), settings.getReferenceWorkflow()));
            settings.setModel(str(body.get("model"), settings.getModel()));
            settings.setSampler(str(body.get("sampler"), settings.getSampler()));
            settings.setScheduler(str(body.get("scheduler"), settings.getScheduler()));
            settings.setNegativePrompt(str(body.get("negativePrompt"), settings.getNegativePrompt()));
            settings.setSteps(intVal(body.get("steps"), settings.getSteps(), 1, 150));
            settings.setScale(doubleVal(body.get("scale"), settings.getScale(), 1.0d, 30.0d));
            settings.setSeed(longVal(body.get("seed"), settings.getSeed(), -1L, Long.MAX_VALUE));
            settings.setDenoise(doubleVal(body.get("denoise"), settings.getDenoise(), 0.0d, 1.0d));
            settings.setRequestTimeoutSeconds(longVal(body.get("requestTimeoutSeconds"), settings.getRequestTimeoutSeconds(), 1L, 600L));
        }
        settings = sanitize(settings, defaultsFromProperties());
        runtimeSettingMapper.upsert(SETTING_KEY, writeJson(settings));
        return settings;
    }

    public Map<String, Object> toMap(AppImageGenerationSettings settings) {
        AppImageGenerationSettings safe = sanitize(settings, defaultsFromProperties());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engine", safe.getEngine());
        data.put("globalConcurrentLimit", safe.getGlobalConcurrentLimit());
        data.put("perUserConcurrentLimit", safe.getPerUserConcurrentLimit());
        data.put("counterTtlSeconds", safe.getCounterTtlSeconds());
        String managedApiKey = decryptManagedApiKey(safe);
        data.put("managedProviderSource", safe.getManagedProviderSource());
        data.put("managedImageModelName", safe.getManagedImageModelName());
        data.put("managedApiKey", "");
        data.put("managedApiKeyConfigured", StringUtils.hasText(managedApiKey));
        data.put("managedApiKeyMask", maskSecret(managedApiKey));
        data.put("managedCustomUrl", safe.getManagedCustomUrl());
        data.put("comfyUrl", safe.getComfyUrl());
        data.put("workflow", safe.getWorkflow());
        data.put("referenceWorkflow", safe.getReferenceWorkflow());
        data.put("model", safe.getModel());
        data.put("sampler", safe.getSampler());
        data.put("scheduler", safe.getScheduler());
        data.put("negativePrompt", safe.getNegativePrompt());
        data.put("steps", safe.getSteps());
        data.put("scale", safe.getScale());
        data.put("seed", safe.getSeed());
        data.put("denoise", safe.getDenoise());
        data.put("requestTimeoutSeconds", Math.max(1L, safe.getRequestTimeoutSeconds()));
        return data;
    }

    private AppImageGenerationSettings defaultsFromProperties() {
        AppImageGenerationProperties.StComfy comfy = properties.getStComfy();
        AppImageGenerationProperties.ManagedOpenAiCompatible managed = properties.getManagedOpenAiCompatible();
        AppImageGenerationSettings settings = new AppImageGenerationSettings();
        settings.setEngine(normalizeEngine(properties.getEngine()));
        settings.setGlobalConcurrentLimit(properties.getGlobalConcurrentLimit());
        settings.setPerUserConcurrentLimit(properties.getPerUserConcurrentLimit());
        settings.setCounterTtlSeconds(properties.getCounterTtlSeconds());
        settings.setManagedProviderSource(managed.getProviderSource());
        settings.setManagedImageModelName(managed.getImageModelName());
        settings.setManagedApiKeyCipher(encryptQuietly(managed.getApiKey()));
        settings.setManagedCustomUrl(managed.getCustomUrl());
        settings.setComfyUrl(comfy.getComfyUrl());
        settings.setWorkflow(comfy.getWorkflow());
        settings.setReferenceWorkflow(comfy.getReferenceWorkflow());
        settings.setModel(comfy.getModel());
        settings.setSampler(comfy.getSampler());
        settings.setScheduler(comfy.getScheduler());
        settings.setNegativePrompt(comfy.getNegativePrompt());
        settings.setSteps(comfy.getSteps());
        settings.setScale(comfy.getScale());
        settings.setSeed(comfy.getSeed());
        settings.setDenoise(comfy.getDenoise());
        settings.setRequestTimeout(comfy.getRequestTimeout());
        return sanitize(settings, new AppImageGenerationSettings());
    }

    private AppImageGenerationSettings sanitize(AppImageGenerationSettings settings, AppImageGenerationSettings defaults) {
        AppImageGenerationSettings safe = settings == null ? new AppImageGenerationSettings() : settings;
        AppImageGenerationSettings fallback = defaults == null ? new AppImageGenerationSettings() : defaults;
        safe.setEngine(normalizeEngine(firstNonBlank(safe.getEngine(), fallback.getEngine(), "openai_compatible")));
        safe.setGlobalConcurrentLimit(clamp(safe.getGlobalConcurrentLimit(), 1, 64, fallback.getGlobalConcurrentLimit()));
        safe.setPerUserConcurrentLimit(clamp(safe.getPerUserConcurrentLimit(), 1, 8, fallback.getPerUserConcurrentLimit()));
        safe.setCounterTtlSeconds(clamp(safe.getCounterTtlSeconds(), 10, 7200, fallback.getCounterTtlSeconds()));
        safe.setManagedProviderSource(firstNonBlank(safe.getManagedProviderSource(), fallback.getManagedProviderSource(), "siliconflow"));
        safe.setManagedImageModelName(firstNonBlank(safe.getManagedImageModelName(), fallback.getManagedImageModelName(), ""));
        safe.setManagedApiKeyCipher(firstNonBlank(safe.getManagedApiKeyCipher(), fallback.getManagedApiKeyCipher(), ""));
        safe.setManagedCustomUrl(firstNonBlank(safe.getManagedCustomUrl(), fallback.getManagedCustomUrl(), ""));
        safe.setComfyUrl(firstNonBlank(safe.getComfyUrl(), fallback.getComfyUrl(), "http://127.0.0.1:8188"));
        safe.setWorkflow(firstNonBlank(safe.getWorkflow(), fallback.getWorkflow(), "Default_Comfy_Workflow.json"));
        safe.setReferenceWorkflow(firstNonBlank(safe.getReferenceWorkflow(), fallback.getReferenceWorkflow(), "Char_Avatar_Comfy_Workflow.json"));
        safe.setSampler(firstNonBlank(safe.getSampler(), fallback.getSampler(), "euler"));
        safe.setScheduler(firstNonBlank(safe.getScheduler(), fallback.getScheduler(), "normal"));
        safe.setNegativePrompt(firstNonBlank(safe.getNegativePrompt(), fallback.getNegativePrompt(), ""));
        safe.setSteps(clamp(safe.getSteps(), 1, 150, fallback.getSteps()));
        safe.setScale(clampDouble(safe.getScale(), 1.0d, 30.0d, fallback.getScale()));
        safe.setDenoise(clampDouble(safe.getDenoise(), 0.0d, 1.0d, fallback.getDenoise()));
        safe.setRequestTimeoutSeconds(Math.max(1L, safe.getRequestTimeoutSeconds() > 0 ? safe.getRequestTimeoutSeconds() : fallback.getRequestTimeoutSeconds()));
        return safe;
    }

    public String decryptManagedApiKey(AppImageGenerationSettings settings) {
        if (settings == null || !StringUtils.hasText(settings.getManagedApiKeyCipher())) {
            return "";
        }
        try {
            return sensitiveTextCrypto.decrypt(settings.getManagedApiKeyCipher());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String writeJson(AppImageGenerationSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生图配置保存失败");
        }
    }

    private static String normalizeEngine(String value) {
        String text = firstNonBlank(value, "user_openai_compatible").toLowerCase()
                .replace('-', '_')
                .replace(' ', '_');
        if ("openai".equals(text)
                || "provider".equals(text)
                || "openai_compatible".equals(text)
                || "user".equals(text)
                || "user_openai".equals(text)
                || "user_openai_compatible".equals(text)) {
            return "user_openai_compatible";
        }
        if ("managed".equals(text)
                || "platform".equals(text)
                || "managed_openai".equals(text)
                || "platform_openai".equals(text)
                || "managed_openai_compatible".equals(text)
                || "platform_openai_compatible".equals(text)) {
            return "managed_openai_compatible";
        }
        if ("comfy".equals(text) || "st_comfyui".equals(text)) {
            return "st_comfy";
        }
        if ("sd_webui".equals(text) || "webui".equals(text)) {
            return "st_sd_webui";
        }
        if ("st_comfy".equals(text) || "st_sd_webui".equals(text)) {
            return text;
        }
        return "user_openai_compatible";
    }

    private static String str(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private static int intVal(Object value, int fallback, int min, int max) {
        if (value instanceof Number number) {
            return clamp(number.intValue(), min, max, fallback);
        }
        try {
            return clamp(Integer.parseInt(String.valueOf(value).trim()), min, max, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longVal(Object value, long fallback, long min, long max) {
        if (value instanceof Number number) {
            return Math.max(min, Math.min(max, number.longValue()));
        }
        try {
            return Math.max(min, Math.min(max, Long.parseLong(String.valueOf(value).trim())));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double doubleVal(Object value, double fallback, double min, double max) {
        if (value instanceof Number number) {
            return clampDouble(number.doubleValue(), min, max, fallback);
        }
        try {
            return clampDouble(Double.parseDouble(String.valueOf(value).trim()), min, max, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max, int fallback) {
        int raw = value <= 0 ? fallback : value;
        return Math.max(min, Math.min(max, raw));
    }

    private static double clampDouble(double value, double min, double max, double fallback) {
        double raw = Double.isFinite(value) ? value : fallback;
        return Math.max(min, Math.min(max, raw));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String encryptQuietly(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        try {
            return sensitiveTextCrypto.encrypt(plainText);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String maskSecret(String secret) {
        String value = secret == null ? "" : secret.trim();
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int keep = Math.min(4, value.length());
        return "****" + value.substring(value.length() - keep);
    }
}
