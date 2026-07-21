package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.entity.AppTtsVoiceTemplate;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceInstance;
import com.example.sillyspringboot.ops.mapper.AppTtsVoiceTemplateMapper;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceInstanceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TtsVoiceTemplateService {

    private final AppTtsVoiceTemplateMapper templateMapper;
    private final AppUserTtsVoiceInstanceMapper instanceMapper;

    public TtsVoiceTemplateService(
            AppTtsVoiceTemplateMapper templateMapper,
            AppUserTtsVoiceInstanceMapper instanceMapper
    ) {
        this.templateMapper = templateMapper;
        this.instanceMapper = instanceMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdmin(String keyword, Boolean enabled) {
        return templateMapper.listAdminPage(blankToNull(keyword), enabled, 0, 200)
                .stream()
                .map(this::toAdminMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        AppTtsVoiceTemplate row = templateMapper.findById(id);
        return row == null ? null : toAdminMap(row);
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        AppTtsVoiceTemplate row = toEntity(body);
        if (!StringUtils.hasText(row.getTemplateCode())) {
            row.setTemplateCode(normalizeCode(row.getDisplayName()));
        }
        row.setProviderSource(normalizeProviderSource(row.getProviderSource()));
        row.setDisplayName(trim(row.getDisplayName(), 64));
        row.setTemplateCode(trim(row.getTemplateCode(), 64).toLowerCase(Locale.ROOT));
        row.setTtsModelName(trim(row.getTtsModelName(), 255));
        row.setDescription(trim(row.getDescription(), 255));
        row.setReferenceAudioUrl(trim(row.getReferenceAudioUrl(), 512));
        row.setCoverImageUrl(trim(row.getCoverImageUrl(), 512));
        row.setSampleScript(trim(row.getSampleScript(), 255));
        row.setEnabled(row.getEnabled() == null || row.getEnabled());
        row.setSortOrder(row.getSortOrder() == null ? 100 : Math.max(0, row.getSortOrder()));
        if (!StringUtils.hasText(row.getTemplateCode()) || !StringUtils.hasText(row.getDisplayName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模板编码和模板名称不能为空");
        }
        if (!StringUtils.hasText(row.getReferenceAudioUrl())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先上传或填写参考音频");
        }
        if (!StringUtils.hasText(row.getSampleScript())) {
            row.setSampleScript("请用温柔自然的语气说话。");
        }

        AppTtsVoiceTemplate exists = templateMapper.findByCode(row.getTemplateCode());
        if (row.getId() == null) {
            if (exists != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "模板编码已存在");
            }
            templateMapper.insert(row);
            return toAdminMap(templateMapper.findById(row.getId()));
        }

        AppTtsVoiceTemplate before = templateMapper.findById(row.getId());
        if (before == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        if (!before.getTemplateCode().equals(row.getTemplateCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模板编码创建后不可修改");
        }
        templateMapper.updateById(row);
        return toAdminMap(templateMapper.findById(row.getId()));
    }

    @Transactional
    public void remove(long id) {
        AppTtsVoiceTemplate row = templateMapper.findById(id);
        if (row == null) {
            return;
        }
        templateMapper.deleteById(id);
        instanceMapper.deleteByTemplateCode(row.getTemplateCode());
    }

    @Transactional(readOnly = true)
    public boolean hasEnabledTemplate(String templateCode) {
        AppTtsVoiceTemplate row = findEnabledTemplate(templateCode);
        return row != null;
    }

    @Transactional(readOnly = true)
    public String resolveDisplayName(String templateCode) {
        AppTtsVoiceTemplate row = templateMapper.findByCode(trim(templateCode, 64));
        return row == null ? "" : blank(row.getDisplayName());
    }

    @Transactional(readOnly = true)
    public AppTtsVoiceTemplate findEnabledTemplate(String templateCode) {
        String code = trim(templateCode, 64);
        if (!StringUtils.hasText(code)) {
            return null;
        }
        AppTtsVoiceTemplate row = templateMapper.findByCode(code);
        return row != null && Boolean.TRUE.equals(row.getEnabled()) ? row : null;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUserOptions(
            long userId,
            TtsVoiceProvisionService.TtsRuntimeContext runtimeContext,
            String selectedTemplateCode
    ) {
        List<AppTtsVoiceTemplate> templates = templateMapper.listEnabled();
        Map<String, AppUserTtsVoiceInstance> instanceMap = instanceMapper.listByUserId(userId)
                .stream()
                .collect(Collectors.toMap(AppUserTtsVoiceInstance::getTemplateCode, item -> item, (left, right) -> right, LinkedHashMap::new));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AppTtsVoiceTemplate template : templates) {
            Map<String, Object> row = new LinkedHashMap<>();
            String effectiveModelName = runtimeContext == null
                    ? templateModelName(template)
                    : runtimeContext.effectiveModelName(templateModelName(template));
            String fingerprint = runtimeContext == null
                    ? ""
                    : TtsVoiceProvisionService.buildConfigFingerprint(template, runtimeContext, effectiveModelName);
            AppUserTtsVoiceInstance instance = instanceMap.get(template.getTemplateCode());
            boolean ready = instance != null
                    && fingerprint.equals(blank(instance.getConfigFingerprint()))
                    && "ready".equalsIgnoreCase(blank(instance.getStatus()))
                    && StringUtils.hasText(instance.getVoiceUri());
            String statusCode;
            String statusText;
            if (runtimeContext == null || !runtimeContext.customModeActive()) {
                statusCode = "requires_byok";
                statusText = "先开启自定义 API 后才能生成专属音色";
            } else if (!runtimeContext.providerMatches(blank(template.getProviderSource()))) {
                statusCode = "requires_provider";
                statusText = "当前模板仅支持硅基流动 TTS";
            } else if (!runtimeContext.hasApiKey()) {
                statusCode = "requires_api_key";
                statusText = "先填写当前 TTS 的 API Key";
            } else if (!StringUtils.hasText(effectiveModelName)) {
                statusCode = "requires_model";
                statusText = "先填写 TTS 模型，或给模板配置推荐模型";
            } else if (ready) {
                statusCode = "ready";
                statusText = "已为当前账号准备好专属音色";
            } else if (instance != null
                    && fingerprint.equals(blank(instance.getConfigFingerprint()))
                    && "failed".equalsIgnoreCase(blank(instance.getStatus()))
                    && StringUtils.hasText(instance.getLastError())) {
                statusCode = "failed";
                statusText = "上次生成失败，实际播放时会自动重试";
            } else {
                statusCode = "pending";
                statusText = "首次使用会自动生成专属音色";
            }
            row.put("code", blank(template.getTemplateCode()));
            row.put("displayName", blank(template.getDisplayName()));
            row.put("description", blank(template.getDescription()));
            row.put("providerSource", blank(template.getProviderSource()));
            row.put("recommendedModelName", blank(template.getTtsModelName()));
            row.put("coverImageUrl", blank(template.getCoverImageUrl()));
            row.put("referenceAudioUrl", blank(template.getReferenceAudioUrl()));
            row.put("sampleScript", blank(template.getSampleScript()));
            row.put("ready", ready);
            row.put("selected", blank(template.getTemplateCode()).equals(blank(selectedTemplateCode)));
            row.put("statusCode", statusCode);
            row.put("statusText", statusText);
            row.put("lastError", instance == null ? "" : blank(instance.getLastError()));
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private Map<String, Object> toAdminMap(AppTtsVoiceTemplate row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("templateCode", blank(row.getTemplateCode()));
        data.put("displayName", blank(row.getDisplayName()));
        data.put("providerSource", blank(row.getProviderSource()));
        data.put("ttsModelName", blank(row.getTtsModelName()));
        data.put("description", blank(row.getDescription()));
        data.put("referenceAudioUrl", blank(row.getReferenceAudioUrl()));
        data.put("coverImageUrl", blank(row.getCoverImageUrl()));
        data.put("sampleScript", blank(row.getSampleScript()));
        data.put("enabled", row.getEnabled() == null || row.getEnabled());
        data.put("sortOrder", row.getSortOrder() == null ? 100 : row.getSortOrder());
        data.put("createdAt", row.getCreatedAt());
        data.put("updatedAt", row.getUpdatedAt());
        return data;
    }

    private AppTtsVoiceTemplate toEntity(Map<String, Object> body) {
        AppTtsVoiceTemplate row = new AppTtsVoiceTemplate();
        if (body == null) {
            return row;
        }
        row.setId(longVal(body.get("id")));
        row.setTemplateCode(str(body.get("templateCode")));
        row.setDisplayName(str(body.get("displayName")));
        row.setProviderSource(str(body.get("providerSource")));
        row.setTtsModelName(str(body.get("ttsModelName")));
        row.setDescription(str(body.get("description")));
        row.setReferenceAudioUrl(str(body.get("referenceAudioUrl")));
        row.setCoverImageUrl(str(body.get("coverImageUrl")));
        row.setSampleScript(str(body.get("sampleScript")));
        row.setEnabled(boolVal(body.get("enabled")));
        row.setSortOrder(intVal(body.get("sortOrder"), 100));
        return row;
    }

    private static String templateModelName(AppTtsVoiceTemplate template) {
        return template == null ? "" : blank(template.getTtsModelName());
    }

    private static String normalizeProviderSource(String value) {
        String source = trim(value, 32).toLowerCase(Locale.ROOT);
        return StringUtils.hasText(source) ? source : "siliconflow";
    }

    private static String normalizeCode(String value) {
        String raw = blank(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (!StringUtils.hasText(raw)) {
            return "voice-" + Integer.toHexString(blank(value).hashCode());
        }
        return trim(raw, 64);
    }

    private static String trim(String value, int maxLength) {
        String text = blank(value).trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        String text = blank(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Boolean boolVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text) || "on".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text) || "off".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Integer intVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
