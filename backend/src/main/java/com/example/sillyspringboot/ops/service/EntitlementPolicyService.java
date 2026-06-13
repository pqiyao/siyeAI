package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EntitlementPolicyService {

    public enum ChatQuotaAction {
        GENERATE,
        CONTINUE,
        REGENERATE
    }

    private static final String SETTING_KEY = "entitlement_policy";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppRuntimeSettingMapper runtimeSettingMapper;

    public EntitlementPolicyService(AppRuntimeSettingMapper runtimeSettingMapper) {
        this.runtimeSettingMapper = runtimeSettingMapper;
    }

    @Transactional(readOnly = true)
    public EntitlementPolicy getPolicy() {
        AppRuntimeSetting raw = runtimeSettingMapper.findByKey(SETTING_KEY);
        if (raw == null || raw.getSettingValue() == null || raw.getSettingValue().isBlank()) {
            return new EntitlementPolicy();
        }
        try {
            return objectMapper.readValue(raw.getSettingValue(), EntitlementPolicy.class);
        } catch (Exception ignored) {
            return new EntitlementPolicy();
        }
    }

    @Transactional
    public EntitlementPolicy savePolicy(Map<String, Object> body) {
        EntitlementPolicy current = getPolicy();
        if (body != null) {
            current.setGuestDailyChatQuota(intVal(body.get("guestDailyChatQuota"), current.getGuestDailyChatQuota()));
            current.setVipDailyChatQuota(intVal(body.get("vipDailyChatQuota"), current.getVipDailyChatQuota()));
            current.setSvipDailyChatQuota(intVal(body.get("svipDailyChatQuota"), current.getSvipDailyChatQuota()));
            current.setGuestDailyByokChatQuota(intVal(body.get("guestDailyByokChatQuota"), current.getGuestDailyByokChatQuota()));
            current.setVipDailyByokChatQuota(intVal(body.get("vipDailyByokChatQuota"), current.getVipDailyByokChatQuota()));
            current.setSvipDailyByokChatQuota(intVal(body.get("svipDailyByokChatQuota"), current.getSvipDailyByokChatQuota()));
            current.setGuestDailyImageQuota(intVal(body.get("guestDailyImageQuota"), current.getGuestDailyImageQuota()));
            current.setVipDailyImageQuota(intVal(body.get("vipDailyImageQuota"), current.getVipDailyImageQuota()));
            current.setSvipDailyImageQuota(intVal(body.get("svipDailyImageQuota"), current.getSvipDailyImageQuota()));
            current.setGuestCharacterCreateLimit(intVal(body.get("guestCharacterCreateLimit"), current.getGuestCharacterCreateLimit()));
            current.setVipCharacterCreateLimit(intVal(body.get("vipCharacterCreateLimit"), current.getVipCharacterCreateLimit()));
            current.setSvipCharacterCreateLimit(intVal(body.get("svipCharacterCreateLimit"), current.getSvipCharacterCreateLimit()));
            current.setGuestCanAccessVipCharacters(boolVal(body.get("guestCanAccessVipCharacters"), current.isGuestCanAccessVipCharacters()));
            current.setVipCanAccessVipCharacters(boolVal(body.get("vipCanAccessVipCharacters"), current.isVipCanAccessVipCharacters()));
            current.setSvipCanAccessVipCharacters(boolVal(body.get("svipCanAccessVipCharacters"), current.isSvipCanAccessVipCharacters()));
            current.setContinueConsumesQuota(boolVal(body.get("continueConsumesQuota"), current.isContinueConsumesQuota()));
            current.setRegenerateConsumesQuota(boolVal(body.get("regenerateConsumesQuota"), current.isRegenerateConsumesQuota()));
            current.setByokContinueConsumesQuota(boolVal(body.get("byokContinueConsumesQuota"), current.isByokContinueConsumesQuota()));
            current.setByokRegenerateConsumesQuota(boolVal(body.get("byokRegenerateConsumesQuota"), current.isByokRegenerateConsumesQuota()));
        }
        runtimeSettingMapper.upsert(SETTING_KEY, writeJson(current));
        return current;
    }

    public Map<String, Object> toMap(EntitlementPolicy policy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("guestDailyChatQuota", policy.getGuestDailyChatQuota());
        data.put("vipDailyChatQuota", policy.getVipDailyChatQuota());
        data.put("svipDailyChatQuota", policy.getSvipDailyChatQuota());
        data.put("guestDailyByokChatQuota", policy.getGuestDailyByokChatQuota());
        data.put("vipDailyByokChatQuota", policy.getVipDailyByokChatQuota());
        data.put("svipDailyByokChatQuota", policy.getSvipDailyByokChatQuota());
        data.put("guestDailyImageQuota", policy.getGuestDailyImageQuota());
        data.put("vipDailyImageQuota", policy.getVipDailyImageQuota());
        data.put("svipDailyImageQuota", policy.getSvipDailyImageQuota());
        data.put("guestCharacterCreateLimit", policy.getGuestCharacterCreateLimit());
        data.put("vipCharacterCreateLimit", policy.getVipCharacterCreateLimit());
        data.put("svipCharacterCreateLimit", policy.getSvipCharacterCreateLimit());
        data.put("guestCanAccessVipCharacters", policy.isGuestCanAccessVipCharacters());
        data.put("vipCanAccessVipCharacters", policy.isVipCanAccessVipCharacters());
        data.put("svipCanAccessVipCharacters", policy.isSvipCanAccessVipCharacters());
        data.put("continueConsumesQuota", policy.isContinueConsumesQuota());
        data.put("regenerateConsumesQuota", policy.isRegenerateConsumesQuota());
        data.put("byokContinueConsumesQuota", policy.isByokContinueConsumesQuota());
        data.put("byokRegenerateConsumesQuota", policy.isByokRegenerateConsumesQuota());
        return data;
    }

    public boolean refreshEffectiveQuota(AppH5UserProfileExt ext) {
        if (ext == null) {
            return false;
        }
        EntitlementPolicy policy = getPolicy();
        int vipLevel = effectiveVipLevel(ext);
        int chatQuota = ext.getChatQuotaOverride() != null ? Math.max(0, ext.getChatQuotaOverride()) : chatQuotaFor(policy, vipLevel);
        int imageQuota = ext.getImageQuotaOverride() != null ? Math.max(0, ext.getImageQuotaOverride()) : imageQuotaFor(policy, vipLevel);
        boolean changed = false;
        if (!equalsInt(ext.getDailyChatQuota(), chatQuota)) {
            ext.setDailyChatQuota(chatQuota);
            changed = true;
        }
        if (!equalsInt(ext.getDailyImageQuota(), imageQuota)) {
            ext.setDailyImageQuota(imageQuota);
            changed = true;
        }
        return changed;
    }

    public int effectiveVipLevel(AppH5UserProfileExt ext) {
        if (ext == null || ext.getVipType() == null || ext.getVipType() <= 0) {
            return 0;
        }
        LocalDateTime expiresAt = ext.getVipExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            return 0;
        }
        return Math.max(0, ext.getVipType());
    }

    public boolean canAccessVipCharacter(AppH5UserProfileExt ext) {
        EntitlementPolicy policy = getPolicy();
        int vipLevel = effectiveVipLevel(ext);
        if (vipLevel >= 2) {
            return policy.isSvipCanAccessVipCharacters();
        }
        if (vipLevel >= 1) {
            return policy.isVipCanAccessVipCharacters();
        }
        return policy.isGuestCanAccessVipCharacters();
    }

    public boolean consumesChatQuota(ChatQuotaAction action) {
        EntitlementPolicy policy = getPolicy();
        return switch (action) {
            case GENERATE -> true;
            case CONTINUE -> policy.isContinueConsumesQuota();
            case REGENERATE -> policy.isRegenerateConsumesQuota();
        };
    }

    public boolean consumesByokChatQuota(ChatQuotaAction action) {
        EntitlementPolicy policy = getPolicy();
        return switch (action) {
            case GENERATE -> true;
            case CONTINUE -> policy.isByokContinueConsumesQuota();
            case REGENERATE -> policy.isByokRegenerateConsumesQuota();
        };
    }

    public int byokChatQuotaFor(EntitlementPolicy policy, int vipLevel) {
        if (vipLevel >= 2) {
            return Math.max(0, policy.getSvipDailyByokChatQuota());
        }
        if (vipLevel >= 1) {
            return Math.max(0, policy.getVipDailyByokChatQuota());
        }
        return Math.max(0, policy.getGuestDailyByokChatQuota());
    }

    public int characterCreateLimitFor(EntitlementPolicy policy, int vipLevel) {
        if (vipLevel >= 2) {
            return Math.max(0, policy.getSvipCharacterCreateLimit());
        }
        if (vipLevel >= 1) {
            return Math.max(0, policy.getVipCharacterCreateLimit());
        }
        return Math.max(0, policy.getGuestCharacterCreateLimit());
    }

    private int chatQuotaFor(EntitlementPolicy policy, int vipLevel) {
        if (vipLevel >= 2) {
            return Math.max(0, policy.getSvipDailyChatQuota());
        }
        if (vipLevel >= 1) {
            return Math.max(0, policy.getVipDailyChatQuota());
        }
        return Math.max(0, policy.getGuestDailyChatQuota());
    }

    private int imageQuotaFor(EntitlementPolicy policy, int vipLevel) {
        if (vipLevel >= 2) {
            return Math.max(0, policy.getSvipDailyImageQuota());
        }
        if (vipLevel >= 1) {
            return Math.max(0, policy.getVipDailyImageQuota());
        }
        return Math.max(0, policy.getGuestDailyImageQuota());
    }

    private String writeJson(EntitlementPolicy policy) {
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize entitlement policy", e);
        }
    }

    private static boolean equalsInt(Integer actual, int expect) {
        return actual != null && actual == expect;
    }

    private static int intVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value).trim()));
        } catch (Exception ignored) {
            return fallback;
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
}
