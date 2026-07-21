package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppFeatureSettingsService {

    private static final String SETTING_KEY = "app_feature_settings";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppRuntimeSettingMapper runtimeSettingMapper;

    public AppFeatureSettingsService(AppRuntimeSettingMapper runtimeSettingMapper) {
        this.runtimeSettingMapper = runtimeSettingMapper;
    }

    @Transactional(readOnly = true)
    public AppFeatureSettings getSettings() {
        AppRuntimeSetting raw = runtimeSettingMapper.findByKey(SETTING_KEY);
        if (raw == null || raw.getSettingValue() == null || raw.getSettingValue().isBlank()) {
            return new AppFeatureSettings();
        }
        try {
            return objectMapper.readValue(raw.getSettingValue(), AppFeatureSettings.class);
        } catch (Exception ignored) {
            return new AppFeatureSettings();
        }
    }

    @Transactional
    public AppFeatureSettings saveSettings(Map<String, Object> body) {
        AppFeatureSettings settings = getSettings();
        if (body != null) {
            settings.setLoginEnabled(boolVal(body.get("loginEnabled"), settings.isLoginEnabled()));
            settings.setRegisterEnabled(boolVal(body.get("registerEnabled"), settings.isRegisterEnabled()));
            settings.setUserCharacterCreationEnabled(
                    boolVal(body.get("userCharacterCreationEnabled"), settings.isUserCharacterCreationEnabled())
            );
            settings.setUserByokEnabled(boolVal(body.get("userByokEnabled"), settings.isUserByokEnabled()));
            settings.setImageGenerationEnabled(
                    boolVal(body.get("imageGenerationEnabled"), settings.isImageGenerationEnabled())
            );
            settings.setVoiceFeatureEnabled(
                    boolVal(body.get("voiceFeatureEnabled"), settings.isVoiceFeatureEnabled())
            );
            settings.setUserByokVipMinLevel(
                    boundedIntVal(body.get("userByokVipMinLevel"), settings.getUserByokVipMinLevel(), 0, 2)
            );
            settings.setAnonymousTrialChatLimit(
                    nonNegativeIntVal(body.get("anonymousTrialChatLimit"), settings.getAnonymousTrialChatLimit())
            );
            settings.setAnonymousTrialConversationLimit(
                    nonNegativeIntVal(body.get("anonymousTrialConversationLimit"), settings.getAnonymousTrialConversationLimit())
            );
            settings.setAnonymousTrialCharacterCreationLimit(
                    nonNegativeIntVal(
                            body.get("anonymousTrialCharacterCreationLimit"),
                            settings.getAnonymousTrialCharacterCreationLimit()
                    )
            );
        }
        runtimeSettingMapper.upsert(SETTING_KEY, writeJson(settings));
        return settings;
    }

    public Map<String, Object> toMap(AppFeatureSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("loginEnabled", settings.isLoginEnabled());
        data.put("registerEnabled", settings.isRegisterEnabled());
        data.put("userCharacterCreationEnabled", settings.isUserCharacterCreationEnabled());
        data.put("userByokEnabled", settings.isUserByokEnabled());
        data.put("imageGenerationEnabled", settings.isImageGenerationEnabled());
        data.put("voiceFeatureEnabled", settings.isVoiceFeatureEnabled());
        data.put("userByokVipMinLevel", settings.getUserByokVipMinLevel());
        data.put("anonymousTrialChatLimit", settings.getAnonymousTrialChatLimit());
        data.put("anonymousTrialConversationLimit", settings.getAnonymousTrialConversationLimit());
        data.put("anonymousTrialCharacterCreationLimit", settings.getAnonymousTrialCharacterCreationLimit());
        return data;
    }

    public void ensureLoginEnabled() {
        if (!getSettings().isLoginEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭账号登录");
        }
    }

    public void ensureRegisterEnabled() {
        if (!getSettings().isRegisterEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭账号注册");
        }
    }

    public void ensureUserCharacterCreationEnabled() {
        if (!getSettings().isUserCharacterCreationEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭用户创建角色卡");
        }
    }

    public void ensureVoiceFeatureEnabled() {
        if (!getSettings().isVoiceFeatureEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭语音功能");
        }
    }

    private String writeJson(AppFeatureSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize app feature settings", e);
        }
    }

    private static boolean boolVal(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static int nonNegativeIntVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value).trim()));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int boundedIntVal(Object value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        int raw;
        if (value instanceof Number number) {
            raw = number.intValue();
        } else {
            try {
                raw = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
        return Math.max(min, Math.min(max, raw));
    }
}
