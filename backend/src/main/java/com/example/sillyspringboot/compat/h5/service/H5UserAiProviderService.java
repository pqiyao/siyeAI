package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserAiProvider;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserAiProviderMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.TtsVoiceProvisionService;
import com.example.sillyspringboot.ops.service.TtsVoiceTemplateService;
import com.example.sillyspringboot.shared.crypto.SensitiveTextCrypto;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class H5UserAiProviderService {

    private static final Logger log = LoggerFactory.getLogger(H5UserAiProviderService.class);

    private static final Duration TEST_CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration TEST_REQUEST_TIMEOUT = Duration.ofSeconds(18);
    private static final long TEST_MIN_INTERVAL_MS = 3000L;
    private static final int MODEL_LIST_LIMIT = 120;
    private static final String TEST_REFERENCE_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7ZfQ0AAAAASUVORK5CYII=";
    private static final String TEST_REFERENCE_IMAGE_DATA_URL = "data:image/png;base64," + TEST_REFERENCE_IMAGE_BASE64;
    private static final byte[] TEST_REFERENCE_IMAGE_BYTES = Base64.getDecoder().decode(TEST_REFERENCE_IMAGE_BASE64);

    private static final String SILICONFLOW_SOURCE = "siliconflow";
    private static final List<ProviderOption> PROVIDER_OPTIONS = List.of(
            new ProviderOption(
                    "siliconflow",
                    "硅基流动",
                    "https://api.siliconflow.cn/v1",
                    false,
                    "deepseek-ai/DeepSeek-V3",
                    List.of("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen2.5-72B-Instruct"),
                    "国内访问相对友好，填写硅基流动控制台的 API Key。",
                    "在硅基流动控制台创建的 API Key"
            ),
            new ProviderOption(
                    "deepseek",
                    "DeepSeek",
                    "https://api.deepseek.com",
                    false,
                    "deepseek-chat",
                    List.of("deepseek-chat", "deepseek-reasoner"),
                    "DeepSeek 官方 OpenAI 兼容接口。",
                    "DeepSeek 官方 API Key"
            ),
            new ProviderOption(
                    "openrouter",
                    "OpenRouter",
                    "https://openrouter.ai/api/v1",
                    false,
                    "deepseek/deepseek-chat",
                    List.of("deepseek/deepseek-chat", "deepseek/deepseek-r1", "openai/gpt-4o-mini"),
                    "可聚合多家模型，模型 ID 请以 OpenRouter 页面显示为准。",
                    "OpenRouter API Key"
            ),
            new ProviderOption(
                    "openai",
                    "OpenAI",
                    "https://api.openai.com/v1",
                    false,
                    "gpt-4o-mini",
                    List.of("gpt-4o-mini", "gpt-4o"),
                    "OpenAI 官方兼容接口。",
                    "OpenAI API Key"
            ),
            new ProviderOption(
                    "groq",
                    "Groq",
                    "https://api.groq.com/openai/v1",
                    false,
                    "llama-3.3-70b-versatile",
                    List.of("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
                    "Groq OpenAI 兼容接口，适合低延迟模型。",
                    "Groq API Key"
            ),
            new ProviderOption(
                    "mistralai",
                    "Mistral",
                    "https://api.mistral.ai/v1",
                    false,
                    "mistral-small-latest",
                    List.of("mistral-small-latest", "mistral-large-latest"),
                    "Mistral OpenAI 兼容接口。",
                    "Mistral API Key"
            ),
            new ProviderOption(
                    "moonshot",
                    "Moonshot",
                    "https://api.moonshot.cn/v1",
                    false,
                    "moonshot-v1-8k",
                    List.of("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
                    "月之暗面 OpenAI 兼容接口。",
                    "Moonshot API Key"
            ),
            new ProviderOption(
                    "xai",
                    "xAI",
                    "https://api.x.ai/v1",
                    false,
                    "grok-2-latest",
                    List.of("grok-2-latest"),
                    "xAI OpenAI 兼容接口。",
                    "xAI API Key"
            ),
            new ProviderOption(
                    "fireworks",
                    "Fireworks",
                    "https://api.fireworks.ai/inference/v1",
                    false,
                    "accounts/fireworks/models/llama-v3p1-70b-instruct",
                    List.of("accounts/fireworks/models/llama-v3p1-70b-instruct"),
                    "Fireworks OpenAI 兼容接口。",
                    "Fireworks API Key"
            ),
            new ProviderOption(
                    "custom",
                    "自定义 OpenAI 兼容",
                    "",
                    true,
                    "",
                    List.of(),
                    "仅支持 OpenAI 兼容的 /chat/completions 接口，请填写到 /v1 的基础地址。",
                    "兼容服务商的 Bearer API Key"
            )
    );
    private static final Set<String> SUPPORTED_SOURCES = PROVIDER_OPTIONS.stream()
            .map(ProviderOption::value)
            .collect(Collectors.toUnmodifiableSet());
    private static final List<String> AVAILABLE_SOURCES = PROVIDER_OPTIONS.stream()
            .map(ProviderOption::value)
            .toList();

    public record ProviderOption(
            String value,
            String label,
            String defaultBaseUrl,
            boolean customUrlRequired,
            String defaultModel,
            List<String> modelPresets,
            String helpText,
            String apiKeyHint
    ) {
    }

    public record UserAiProviderView(
            boolean enabledGlobal,
            boolean canUse,
            String denyReason,
            String mode,
            String providerSource,
            String modelName,
            String visionModelName,
            String audioModelName,
            String sttModelName,
            boolean sttUseSeparateConfig,
            String sttProviderSource,
            boolean sttApiKeyConfigured,
            String sttApiKeyMask,
            String sttCustomUrl,
            String effectiveSttProviderSource,
            boolean effectiveSttApiKeyConfigured,
            String effectiveSttApiKeyMask,
            String effectiveSttCustomUrl,
            String ttsModelName,
            String ttsVoiceName,
            String ttsVoiceTemplateCode,
            String ttsVoiceTemplateLabel,
            String imageModelName,
            String imageCharacterConsistencyMode,
            String imageReferenceSourceMode,
            boolean apiKeyConfigured,
            String apiKeyMask,
            String customUrl,
            boolean ttsUseSeparateConfig,
            String ttsProviderSource,
            boolean ttsApiKeyConfigured,
            String ttsApiKeyMask,
            String ttsCustomUrl,
            String effectiveTtsProviderSource,
            boolean effectiveTtsApiKeyConfigured,
            String effectiveTtsApiKeyMask,
            String effectiveTtsCustomUrl,
            boolean imageUseSeparateConfig,
            String imageProviderSource,
            boolean imageApiKeyConfigured,
            String imageApiKeyMask,
            String imageCustomUrl,
            String effectiveImageProviderSource,
            boolean effectiveImageApiKeyConfigured,
            String effectiveImageApiKeyMask,
            String effectiveImageCustomUrl,
            boolean imageEnabledGlobal,
            boolean imageCanUse,
            String imageDenyReason,
            boolean voiceEnabledGlobal,
            boolean voiceCanUse,
            String voiceDenyReason,
            int currentVipLevel,
            int vipMinLevel,
            List<String> availableSources,
            List<ProviderOption> providerOptions,
            List<Map<String, Object>> ttsVoiceTemplates
    ) {
    }

    public record UserTtsSettings(
            String providerSource,
            String modelName,
            String voiceName,
            String voiceTemplateCode,
            String apiKey,
            String baseUrl
    ) {
    }

    public record UserAiProviderTestResult(
            boolean ok,
            String message,
            String providerSource,
            String modelName,
            long latencyMs,
            String normalizedCustomUrl
    ) {
    }

    public record UserAiProviderModelsResult(
            boolean ok,
            String message,
            String providerSource,
            List<String> models,
            List<UserAiProviderModelItem> modelItems,
            long latencyMs,
            String normalizedCustomUrl
    ) {
    }

    public record UserAiProviderModelItem(
            String id,
            String name,
            List<String> inputModalities,
            List<String> outputModalities,
            List<String> capabilityHints
    ) {
    }

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AppH5UserAiProviderMapper mapper;
    private final AppH5UserProfileExtMapper profileExtMapper;
    private final AppFeatureSettingsService featureSettingsService;
    private final EntitlementPolicyService entitlementPolicyService;
    private final TtsVoiceTemplateService ttsVoiceTemplateService;
    private final SensitiveTextCrypto sensitiveTextCrypto;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, Long> lastTestAtByScope = new ConcurrentHashMap<>();

    public H5UserAiProviderService(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppH5UserAiProviderMapper mapper,
            AppH5UserProfileExtMapper profileExtMapper,
            AppFeatureSettingsService featureSettingsService,
            EntitlementPolicyService entitlementPolicyService,
            TtsVoiceTemplateService ttsVoiceTemplateService,
            SensitiveTextCrypto sensitiveTextCrypto,
            ObjectMapper objectMapper
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.mapper = mapper;
        this.profileExtMapper = profileExtMapper;
        this.featureSettingsService = featureSettingsService;
        this.entitlementPolicyService = entitlementPolicyService;
        this.ttsVoiceTemplateService = ttsVoiceTemplateService;
        this.sensitiveTextCrypto = sensitiveTextCrypto;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TEST_CONNECT_TIMEOUT)
                .build();
    }

    @Transactional
    public UserAiProviderView getView(String clientUid) {
        AppUser user = resolveUser(clientUid);
        AppFeatureSettings settings = featureSettingsService.getSettings();
        AppH5UserAiProvider row = mapper.findByUserId(user.getId());
        int currentVipLevel = currentVipLevel(user.getId());
        boolean enabledGlobal = settings.isUserByokEnabled();
        boolean canUse = enabledGlobal && currentVipLevel >= settings.getUserByokVipMinLevel();
        boolean imageEnabledGlobal = settings.isImageGenerationEnabled();
        boolean imageCanUse = imageEnabledGlobal && canUse;
        boolean voiceEnabledGlobal = settings.isVoiceFeatureEnabled();
        boolean voiceCanUse = voiceEnabledGlobal && canUse;
        String mode = normalizeMode(row == null ? null : row.getProviderMode());
        String providerSource = normalizeSourceOrDefault(row == null ? null : row.getProviderSource(), "");
        boolean sttUseSeparateConfig = hasSeparateSttConfig(row);
        String sttProviderSource = sttUseSeparateConfig
                ? normalizeSourceOrDefault(row == null ? null : row.getSttProviderSource(), "")
                : "";
        boolean ttsUseSeparateConfig = hasSeparateTtsConfig(row);
        String ttsProviderSource = ttsUseSeparateConfig
                ? normalizeSourceOrDefault(row == null ? null : row.getTtsProviderSource(), "")
                : "";
        boolean imageUseSeparateConfig = hasSeparateImageConfig(row);
        String imageProviderSource = imageUseSeparateConfig
                ? normalizeSourceOrDefault(row == null ? null : row.getImageProviderSource(), "")
                : "";
        boolean apiKeyConfigured = hasStoredKey(row);
        String apiKeyMask = maskStoredKey(row);
        boolean sttApiKeyConfigured = hasStoredSttKey(row);
        String sttApiKeyMask = maskStoredSttKey(row);
        boolean ttsApiKeyConfigured = hasStoredTtsKey(row);
        String ttsApiKeyMask = maskStoredTtsKey(row);
        boolean imageApiKeyConfigured = hasStoredImageKey(row);
        String imageApiKeyMask = maskStoredImageKey(row);
        String sttCustomUrl = sttUseSeparateConfig ? safe(row == null ? null : row.getSttCustomUrl()) : "";
        String ttsCustomUrl = ttsUseSeparateConfig ? safe(row == null ? null : row.getTtsCustomUrl()) : "";
        String imageCustomUrl = imageUseSeparateConfig ? safe(row == null ? null : row.getImageCustomUrl()) : "";
        String effectiveSttProviderSource = sttUseSeparateConfig ? sttProviderSource : providerSource;
        boolean effectiveSttApiKeyConfigured = sttUseSeparateConfig ? sttApiKeyConfigured : apiKeyConfigured;
        String effectiveSttApiKeyMask = sttUseSeparateConfig ? sttApiKeyMask : apiKeyMask;
        String effectiveSttCustomUrl = sttUseSeparateConfig ? sttCustomUrl : safe(row == null ? null : row.getCustomUrl());
        String effectiveTtsProviderSource = ttsUseSeparateConfig ? ttsProviderSource : providerSource;
        boolean effectiveTtsApiKeyConfigured = ttsUseSeparateConfig ? ttsApiKeyConfigured : apiKeyConfigured;
        String effectiveTtsApiKeyMask = ttsUseSeparateConfig ? ttsApiKeyMask : apiKeyMask;
        String effectiveTtsCustomUrl = ttsUseSeparateConfig ? ttsCustomUrl : safe(row == null ? null : row.getCustomUrl());
        String effectiveImageProviderSource = imageUseSeparateConfig ? imageProviderSource : providerSource;
        boolean effectiveImageApiKeyConfigured = imageUseSeparateConfig ? imageApiKeyConfigured : apiKeyConfigured;
        String effectiveImageApiKeyMask = imageUseSeparateConfig ? imageApiKeyMask : apiKeyMask;
        String effectiveImageCustomUrl = imageUseSeparateConfig ? imageCustomUrl : safe(row == null ? null : row.getCustomUrl());
        String ttsVoiceTemplateCode = safe(row == null ? null : row.getTtsVoiceTemplateCode());
        String ttsVoiceTemplateLabel = ttsVoiceTemplateService.resolveDisplayName(ttsVoiceTemplateCode);
        String imageCharacterConsistencyMode = normalizeImageCharacterConsistencyMode(row == null ? null : row.getImageCharacterConsistencyMode());
        String imageReferenceSourceMode = normalizeImageReferenceSourceMode(row == null ? null : row.getImageReferenceSourceMode());
        List<Map<String, Object>> ttsVoiceTemplates = voiceEnabledGlobal
                ? ttsVoiceTemplateService.listUserOptions(
                        user.getId(),
                        buildTtsRuntimeContext(row, enabledGlobal, currentVipLevel, settings.getUserByokVipMinLevel()),
                        ttsVoiceTemplateCode
                )
                : List.of();
        return new UserAiProviderView(
                enabledGlobal,
                canUse,
                canUse ? "" : denyReason(enabledGlobal, currentVipLevel, settings.getUserByokVipMinLevel()),
                mode,
                providerSource,
                safe(row == null ? null : row.getModelName()),
                safe(row == null ? null : row.getVisionModelName()),
                safe(row == null ? null : row.getAudioModelName()),
                audioSttModel(row),
                sttUseSeparateConfig,
                sttProviderSource,
                sttApiKeyConfigured,
                sttApiKeyMask,
                sttCustomUrl,
                effectiveSttProviderSource,
                effectiveSttApiKeyConfigured,
                effectiveSttApiKeyMask,
                effectiveSttCustomUrl,
                audioTtsModel(row),
                audioTtsVoice(row),
                ttsVoiceTemplateCode,
                ttsVoiceTemplateLabel,
                safe(row == null ? null : row.getImageModelName()),
                imageCharacterConsistencyMode,
                imageReferenceSourceMode,
                apiKeyConfigured,
                apiKeyMask,
                safe(row == null ? null : row.getCustomUrl()),
                ttsUseSeparateConfig,
                ttsProviderSource,
                ttsApiKeyConfigured,
                ttsApiKeyMask,
                ttsCustomUrl,
                effectiveTtsProviderSource,
                effectiveTtsApiKeyConfigured,
                effectiveTtsApiKeyMask,
                effectiveTtsCustomUrl,
                imageUseSeparateConfig,
                imageProviderSource,
                imageApiKeyConfigured,
                imageApiKeyMask,
                imageCustomUrl,
                effectiveImageProviderSource,
                effectiveImageApiKeyConfigured,
                effectiveImageApiKeyMask,
                effectiveImageCustomUrl,
                imageEnabledGlobal,
                imageCanUse,
                imageCanUse ? "" : (!imageEnabledGlobal ? "当前已关闭生图功能" : denyReason(enabledGlobal, currentVipLevel, settings.getUserByokVipMinLevel())),
                voiceEnabledGlobal,
                voiceCanUse,
                voiceCanUse ? "" : (!voiceEnabledGlobal ? "当前已关闭语音功能" : denyReason(enabledGlobal, currentVipLevel, settings.getUserByokVipMinLevel())),
                currentVipLevel,
                settings.getUserByokVipMinLevel(),
                AVAILABLE_SOURCES,
                publicProviderOptions(),
                ttsVoiceTemplates
        );
    }

    @Transactional
    public UserAiProviderView save(String clientUid, Map<String, Object> body) {
        AppUser user = resolveUser(clientUid);
        AppFeatureSettings settings = featureSettingsService.getSettings();
        AppH5UserAiProvider row = mapper.findByUserId(user.getId());
        if (row == null) {
            row = defaultRow(user.getId());
        }

        String mode = normalizeMode(asString(body == null ? null : body.get("mode")));
        String providerSource = normalizeSourceOrDefault(asString(body == null ? null : body.get("providerSource")), "");
        String modelName = trim(asString(body == null ? null : body.get("modelName")), 255);
        boolean hasVisionModelName = body != null && body.containsKey("visionModelName");
        String visionModelName = trim(asString(body == null ? null : body.get("visionModelName")), 255);
        boolean hasAudioModelName = body != null && body.containsKey("audioModelName");
        String audioModelName = trim(asString(body == null ? null : body.get("audioModelName")), 255);
        boolean hasSttModelName = body != null && body.containsKey("sttModelName");
        String sttModelName = trim(asString(body == null ? null : body.get("sttModelName")), 255);
        boolean sttUseSeparateConfig = asBoolean(body == null ? null : body.get("sttUseSeparateConfig"));
        String sttProviderSource = normalizeSourceOrDefault(
                firstText(asString(body == null ? null : body.get("sttProviderSource")), providerSource),
                providerSource
        );
        String sttCustomUrl = trim(asString(body == null ? null : body.get("sttCustomUrl")), 512);
        String sttApiKey = trim(asString(body == null ? null : body.get("sttApiKey")), 2048);
        boolean clearStoredSttKey = asBoolean(body == null ? null : body.get("clearStoredSttKey"));
        boolean hasTtsModelName = body != null && body.containsKey("ttsModelName");
        String ttsModelName = trim(asString(body == null ? null : body.get("ttsModelName")), 255);
        boolean hasTtsVoiceName = body != null && body.containsKey("ttsVoiceName");
        String ttsVoiceName = trim(asString(body == null ? null : body.get("ttsVoiceName")), 255);
        boolean hasTtsVoiceTemplateCode = body != null && body.containsKey("ttsVoiceTemplateCode");
        String ttsVoiceTemplateCode = trim(asString(body == null ? null : body.get("ttsVoiceTemplateCode")), 64);
        boolean hasImageModelName = body != null && body.containsKey("imageModelName");
        String imageModelName = trim(asString(body == null ? null : body.get("imageModelName")), 255);
        boolean hasImageCharacterConsistencyMode = body != null && body.containsKey("imageCharacterConsistencyMode");
        String imageCharacterConsistencyMode = normalizeImageCharacterConsistencyMode(
                asString(body == null ? null : body.get("imageCharacterConsistencyMode"))
        );
        boolean hasImageReferenceSourceMode = body != null && body.containsKey("imageReferenceSourceMode");
        String imageReferenceSourceMode = normalizeImageReferenceSourceMode(
                asString(body == null ? null : body.get("imageReferenceSourceMode"))
        );
        boolean imageUseSeparateConfig = asBoolean(body == null ? null : body.get("imageUseSeparateConfig"));
        String imageProviderSource = normalizeSourceOrDefault(
                firstText(asString(body == null ? null : body.get("imageProviderSource")), providerSource),
                providerSource
        );
        String imageCustomUrl = trim(asString(body == null ? null : body.get("imageCustomUrl")), 512);
        String imageApiKey = trim(asString(body == null ? null : body.get("imageApiKey")), 2048);
        boolean clearStoredImageKey = asBoolean(body == null ? null : body.get("clearStoredImageKey"));
        String customUrl = trim(asString(body == null ? null : body.get("customUrl")), 512);
        String apiKey = trim(asString(body == null ? null : body.get("apiKey")), 2048);
        boolean clearStoredKey = asBoolean(body == null ? null : body.get("clearStoredKey"));
        boolean ttsUseSeparateConfig = asBoolean(body == null ? null : body.get("ttsUseSeparateConfig"));
        String ttsProviderSource = normalizeSourceOrDefault(
                firstText(asString(body == null ? null : body.get("ttsProviderSource")), providerSource),
                providerSource
        );
        String ttsCustomUrl = trim(asString(body == null ? null : body.get("ttsCustomUrl")), 512);
        String ttsApiKey = trim(asString(body == null ? null : body.get("ttsApiKey")), 2048);
        boolean clearStoredTtsKey = asBoolean(body == null ? null : body.get("clearStoredTtsKey"));
        String effectiveApiKey = StringUtils.hasText(apiKey)
                ? apiKey
                : (clearStoredKey || !storedKeyApplies(row, providerSource, customUrl) ? "" : decryptQuietly(row.getApiKeyCipher()));
        String effectiveSttApiKey = sttUseSeparateConfig
                ? (StringUtils.hasText(sttApiKey)
                ? sttApiKey
                : (clearStoredSttKey || !storedKeyApplies(row, sttProviderSource, sttCustomUrl, "stt") ? "" : decryptQuietly(row.getSttApiKeyCipher())))
                : "";
        String effectiveTtsApiKey = ttsUseSeparateConfig
                ? (StringUtils.hasText(ttsApiKey)
                ? ttsApiKey
                : (clearStoredTtsKey || !storedKeyApplies(row, ttsProviderSource, ttsCustomUrl, "tts") ? "" : decryptQuietly(row.getTtsApiKeyCipher())))
                : "";
        String effectiveImageApiKey = imageUseSeparateConfig
                ? (StringUtils.hasText(imageApiKey)
                ? imageApiKey
                : (clearStoredImageKey || !storedKeyApplies(row, imageProviderSource, imageCustomUrl, "image") ? "" : decryptQuietly(row.getImageApiKeyCipher())))
                : "";

        if ("custom".equals(mode)) {
            ensureCustomModeAllowed(user.getId(), settings);
            validateCustomConfigClean(providerSource, modelName, customUrl, effectiveApiKey);
            if (sttUseSeparateConfig) {
                validateSttConfigClean(sttProviderSource, sttCustomUrl, effectiveSttApiKey);
            }
            if (ttsUseSeparateConfig) {
                validateTtsConfigClean(ttsProviderSource, ttsCustomUrl, effectiveTtsApiKey);
            }
            if (imageUseSeparateConfig) {
                validateImageConfigClean(imageProviderSource, imageCustomUrl, effectiveImageApiKey);
            }
        }
        if (StringUtils.hasText(ttsVoiceTemplateCode) && !ttsVoiceTemplateService.hasEnabledTemplate(ttsVoiceTemplateCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "所选模板音色已失效，请重新选择");
        }

        row.setProviderMode(mode);
        if (StringUtils.hasText(providerSource)) {
            row.setProviderSource(providerSource);
        }
        if (StringUtils.hasText(modelName)) {
            row.setModelName(modelName);
        }
        if (hasVisionModelName) {
            row.setVisionModelName(visionModelName);
        }
        if (hasAudioModelName) {
            row.setAudioModelName(audioModelName);
        }
        if (hasSttModelName) {
            row.setSttModelName(sttModelName);
        }
        if (sttUseSeparateConfig) {
            row.setSttProviderSource(sttProviderSource);
            row.setSttCustomUrl(normalizeCustomUrlForStorage(sttProviderSource, sttCustomUrl));
            if (clearStoredSttKey) {
                row.setSttApiKeyCipher("");
            } else if (StringUtils.hasText(sttApiKey)) {
                row.setSttApiKeyCipher(sensitiveTextCrypto.encrypt(sttApiKey));
            }
        } else {
            row.setSttProviderSource("");
            row.setSttCustomUrl("");
            row.setSttApiKeyCipher("");
        }
        if (hasTtsModelName) {
            row.setTtsModelName(ttsModelName);
        }
        if (hasTtsVoiceName) {
            row.setTtsVoiceName(ttsVoiceName);
        }
        if (hasTtsVoiceTemplateCode) {
            row.setTtsVoiceTemplateCode(ttsVoiceTemplateCode);
        }
        if (hasImageModelName) {
            row.setImageModelName(imageModelName);
        }
        if (hasImageCharacterConsistencyMode) {
            row.setImageCharacterConsistencyMode(imageCharacterConsistencyMode);
        }
        if (hasImageReferenceSourceMode) {
            row.setImageReferenceSourceMode(imageReferenceSourceMode);
        }
        if (imageUseSeparateConfig) {
            row.setImageProviderSource(imageProviderSource);
            row.setImageCustomUrl(normalizeCustomUrlForStorage(imageProviderSource, imageCustomUrl));
            if (clearStoredImageKey) {
                row.setImageApiKeyCipher("");
            } else if (StringUtils.hasText(imageApiKey)) {
                row.setImageApiKeyCipher(sensitiveTextCrypto.encrypt(imageApiKey));
            }
        } else {
        row.setImageProviderSource("");
        row.setImageCustomUrl("");
        row.setImageApiKeyCipher("");
    }
        if (!hasImageCharacterConsistencyMode && !StringUtils.hasText(row.getImageCharacterConsistencyMode())) {
            row.setImageCharacterConsistencyMode(normalizeImageCharacterConsistencyMode(null));
        }
        if (!hasImageReferenceSourceMode && !StringUtils.hasText(row.getImageReferenceSourceMode())) {
            row.setImageReferenceSourceMode(normalizeImageReferenceSourceMode(null));
        }
        row.setCustomUrl("custom".equals(mode) ? normalizeCustomUrlForStorage(providerSource, customUrl) : customUrl);
        if (clearStoredKey) {
            row.setApiKeyCipher("");
        } else if (StringUtils.hasText(apiKey)) {
            row.setApiKeyCipher(sensitiveTextCrypto.encrypt(apiKey));
        }
        if (ttsUseSeparateConfig) {
            row.setTtsProviderSource(ttsProviderSource);
            row.setTtsCustomUrl(normalizeCustomUrlForStorage(ttsProviderSource, ttsCustomUrl));
            if (clearStoredTtsKey) {
                row.setTtsApiKeyCipher("");
            } else if (StringUtils.hasText(ttsApiKey)) {
                row.setTtsApiKeyCipher(sensitiveTextCrypto.encrypt(ttsApiKey));
            }
        } else {
            row.setTtsProviderSource("");
            row.setTtsCustomUrl("");
            row.setTtsApiKeyCipher("");
        }
        mapper.upsert(row);
        return getView(clientUid);
    }

    @Transactional
    public UserAiProviderTestResult testConnection(String clientUid, Map<String, Object> body) {
        AppUser user = resolveUser(clientUid);
        AppFeatureSettings settings = featureSettingsService.getSettings();
        ensureCustomModeAllowed(user.getId(), settings);

        AppH5UserAiProvider row = mapper.findByUserId(user.getId());
        String keyScope = normalizeKeyScope(asString(body == null ? null : body.get("keyScope")));
        String storedScope = resolveTestStoredScope(
                keyScope,
                asBoolean(body == null ? null : body.get("sttUseSeparateConfig")),
                asBoolean(body == null ? null : body.get("ttsUseSeparateConfig")),
                asBoolean(body == null ? null : body.get("imageUseSeparateConfig"))
        );
        String providerSource = normalizeSourceOrDefault(firstText(
                asString(body == null ? null : body.get("providerSource")),
                storedEffectiveProviderSource(row, storedScope)
        ), "");
        ProviderOption option = requireProviderOption(providerSource);
        String modelName = trim(firstText(
                asString(body == null ? null : body.get("modelName")),
                storedEffectiveModelName(row, storedScope),
                option.defaultModel()
        ), 255);
        String customUrl = trim(firstText(
                asString(body == null ? null : body.get("customUrl")),
                storedEffectiveCustomUrl(row, storedScope)
        ), 512);
        String apiKey = trim(asString(body == null ? null : body.get("apiKey")), 2048);
        if (!StringUtils.hasText(apiKey) && storedEffectiveKeyApplies(row, providerSource, customUrl, storedScope)) {
            apiKey = decryptQuietly(storedEffectiveApiKeyCipher(row, storedScope));
        }

        if ("image".equals(keyScope)) {
            if (!StringUtils.hasText(modelName)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u8bf7\u5148\u9009\u62e9\u751f\u56fe\u6a21\u578b");
            }
            validateImageConfigClean(providerSource, customUrl, apiKey);
        } else if ("stt".equals(keyScope)) {
            if (!StringUtils.hasText(modelName)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u8bf7\u5148\u9009\u62e9 STT \u6a21\u578b");
            }
            validateSttConfigClean(providerSource, customUrl, apiKey);
        } else if ("tts".equals(keyScope)) {
            if (!StringUtils.hasText(modelName)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u8bf7\u5148\u9009\u62e9 TTS \u6a21\u578b");
            }
            validateTtsConfigClean(providerSource, customUrl, apiKey);
        } else {
            validateCustomConfigClean(providerSource, modelName, customUrl, apiKey);
        }
        ensureTestRateAllowed(user.getId(), keyScope);
        String baseUrl = "image".equals(keyScope)
                ? normalizeImageTestBaseUrl(providerSource, resolveProviderBaseUrl(providerSource, customUrl))
                : resolveProviderBaseUrl(providerSource, customUrl);
        if ("image".equals(keyScope)) {
            return sendOpenAiCompatibleImageTest(providerSource, modelName, apiKey, baseUrl);
        }
        if ("stt".equals(keyScope)) {
            return sendScopedCapabilityCatalogTest(providerSource, modelName, apiKey, baseUrl, "stt", "STT");
        }
        if ("tts".equals(keyScope)) {
            return sendScopedCapabilityCatalogTest(providerSource, modelName, apiKey, baseUrl, "tts", "TTS");
        }
        return sendOpenAiCompatibleTest(providerSource, modelName, apiKey, baseUrl);
    }

    @Transactional
    public UserAiProviderModelsResult listModels(String clientUid, Map<String, Object> body) {
        AppUser user = resolveUser(clientUid);
        AppFeatureSettings settings = featureSettingsService.getSettings();
        ensureCustomModeAllowed(user.getId(), settings);

        AppH5UserAiProvider row = mapper.findByUserId(user.getId());
        String keyScope = normalizeKeyScope(asString(body == null ? null : body.get("keyScope")));
        String providerSource = normalizeSourceOrDefault(firstText(
                asString(body == null ? null : body.get("providerSource")),
                storedProviderSource(row, keyScope)
        ), "");
        requireProviderOption(providerSource);
        String customUrl = trim(firstText(
                asString(body == null ? null : body.get("customUrl")),
                storedCustomUrl(row, keyScope)
        ), 512);
        String apiKey = trim(asString(body == null ? null : body.get("apiKey")), 2048);
        if (!StringUtils.hasText(apiKey) && storedKeyApplies(row, providerSource, customUrl, keyScope)) {
            apiKey = decryptQuietly(storedApiKeyCipher(row, keyScope));
        }

        validateModelListConfig(providerSource, customUrl, apiKey);
        String baseUrl = resolveProviderBaseUrl(providerSource, customUrl);
        return sendOpenAiCompatibleModels(providerSource, apiKey, baseUrl, keyScope);
    }

    @Transactional(readOnly = true)
    public UserModelOverride resolveActiveOverrideForUser(long userId) {
        AppFeatureSettings settings = featureSettingsService.getSettings();
        if (!settings.isUserByokEnabled()) {
            return null;
        }
        if (currentVipLevel(userId) < settings.getUserByokVipMinLevel()) {
            return null;
        }
        AppH5UserAiProvider row = mapper.findByUserId(userId);
        if (row == null || !"custom".equals(normalizeMode(row.getProviderMode()))) {
            return null;
        }
        try {
            String providerSource = normalizeSourceOrDefault(row.getProviderSource(), "");
            String modelName = trim(row.getModelName(), 255);
            if (!StringUtils.hasText(providerSource) || !StringUtils.hasText(modelName)) {
                return null;
            }
            String apiKey = decryptQuietly(row.getApiKeyCipher());
            if (!StringUtils.hasText(apiKey)) {
                return null;
            }
            String customUrl = trim(row.getCustomUrl(), 512);
            String sttProviderSource = hasSeparateSttConfig(row)
                    ? normalizeSourceOrDefault(row.getSttProviderSource(), "")
                    : "";
            String sttCustomUrl = trim(row.getSttCustomUrl(), 512);
            String sttApiKey = decryptQuietly(row.getSttApiKeyCipher());
            String ttsProviderSource = hasSeparateTtsConfig(row)
                    ? normalizeSourceOrDefault(row.getTtsProviderSource(), "")
                    : "";
            String ttsCustomUrl = trim(row.getTtsCustomUrl(), 512);
            String ttsApiKey = decryptQuietly(row.getTtsApiKeyCipher());
            String imageProviderSource = hasSeparateImageConfig(row)
                    ? normalizeSourceOrDefault(row.getImageProviderSource(), "")
                    : "";
            String imageCustomUrl = trim(row.getImageCustomUrl(), 512);
            String imageApiKey = decryptQuietly(row.getImageApiKeyCipher());
            return new UserModelOverride(
                    providerSource,
                    modelName,
                    trim(row.getVisionModelName(), 255),
                    trim(row.getAudioModelName(), 255),
                    trim(row.getSttModelName(), 255),
                    sttProviderSource,
                    sttApiKey,
                    sttCustomUrl,
                    trim(row.getTtsModelName(), 255),
                    trim(row.getTtsVoiceName(), 255),
                    ttsProviderSource,
                    ttsApiKey,
                    ttsCustomUrl,
                    trim(row.getImageModelName(), 255),
                    imageProviderSource,
                    imageApiKey,
                    imageCustomUrl,
                    apiKey,
                    customUrl
            );
        } catch (BusinessException ex) {
            log.warn("skip invalid stored user ai provider config, userId={}", userId);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public UserTtsSettings resolveActiveTtsSettingsForUser(long userId) {
        AppFeatureSettings settings = featureSettingsService.getSettings();
        if (!settings.isUserByokEnabled()) {
            return null;
        }
        if (currentVipLevel(userId) < settings.getUserByokVipMinLevel()) {
            return null;
        }
        AppH5UserAiProvider row = mapper.findByUserId(userId);
        if (row == null || !"custom".equals(normalizeMode(row.getProviderMode()))) {
            return null;
        }
        try {
            String providerSource = hasSeparateTtsConfig(row)
                    ? normalizeSourceOrDefault(row.getTtsProviderSource(), "")
                    : normalizeSourceOrDefault(row.getProviderSource(), "");
            if (!StringUtils.hasText(providerSource)) {
                return null;
            }
            String modelName = trim(audioTtsModel(row), 255);
            String voiceName = trim(row.getTtsVoiceName(), 255);
            String voiceTemplateCode = trim(row.getTtsVoiceTemplateCode(), 64);
            String customUrl = hasSeparateTtsConfig(row)
                    ? trim(row.getTtsCustomUrl(), 512)
                    : trim(row.getCustomUrl(), 512);
            String apiKey = hasSeparateTtsConfig(row)
                    ? decryptQuietly(row.getTtsApiKeyCipher())
                    : decryptQuietly(row.getApiKeyCipher());
            String baseUrl = resolveProviderBaseUrl(providerSource, customUrl);
            return new UserTtsSettings(
                    providerSource,
                    modelName,
                    voiceName,
                    voiceTemplateCode,
                    apiKey,
                    baseUrl
            );
        } catch (BusinessException ex) {
            log.warn("skip invalid user tts override, userId={}, reason={}", userId, ex.getMessage());
            return null;
        }
    }

    public String resolveProviderBaseUrlForSource(String providerSource, String customUrl) {
        return resolveProviderBaseUrl(providerSource, customUrl);
    }

    private void ensureCustomModeAllowed(long userId, AppFeatureSettings settings) {
        if (!settings.isUserByokEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前暂未开放自定义 API");
        }
        if (currentVipLevel(userId) < settings.getUserByokVipMinLevel()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号等级暂不支持自定义 API");
        }
    }

    private void validateCustomConfig(String providerSource, String modelName, String customUrl, String apiKey) {
        if (!StringUtils.hasText(providerSource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择平台");
        }
        ProviderOption option = requireProviderOption(providerSource);
        if (!StringUtils.hasText(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模型不能为空");
        }
        if (option.customUrlRequired()) {
            normalizeBaseUrl(customUrl);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写 API Key");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "API Key 格式不正确");
        }
    }

    private void validateTtsConfig(String providerSource, String customUrl, String apiKey) {
        if (!StringUtils.hasText(providerSource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择 TTS 平台");
        }
        ProviderOption option = requireProviderOption(providerSource);
        if (option.customUrlRequired()) {
            normalizeBaseUrl(customUrl);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写 TTS API Key");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "TTS API Key 格式不正确");
        }
    }

    private void validateImageConfig(String providerSource, String customUrl, String apiKey) {
        if (!StringUtils.hasText(providerSource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择生图平台");
        }
        ProviderOption option = requireProviderOption(providerSource);
        if (option.customUrlRequired()) {
            normalizeBaseUrl(customUrl);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写生图 API Key");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图 API Key 格式不正确");
        }
    }

    private void validateCustomConfigClean(String providerSource, String modelName, String customUrl, String apiKey) {
        if (!StringUtils.hasText(providerSource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择平台");
        }
        ProviderOption option = requireProviderOption(providerSource);
        if (!StringUtils.hasText(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模型不能为空");
        }
        if (option.customUrlRequired()) {
            normalizeBaseUrl(customUrl);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写 API Key");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "API Key 格式不正确");
        }
    }

    private void validateTtsConfigClean(String providerSource, String customUrl, String apiKey) {
        if (!StringUtils.hasText(providerSource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择 TTS 平台");
        }
        ProviderOption option = requireProviderOption(providerSource);
        if (option.customUrlRequired()) {
            normalizeBaseUrl(customUrl);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写 TTS API Key");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "TTS API Key 格式不正确");
        }
    }

    private void validateSttConfigClean(String providerSource, String customUrl, String apiKey) {
        if (!StringUtils.hasText(providerSource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "璇烽€夋嫨 STT 骞冲彴");
        }
        ProviderOption option = requireProviderOption(providerSource);
        if (option.customUrlRequired()) {
            normalizeBaseUrl(customUrl);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "璇峰～鍐?STT API Key");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "STT API Key 鏍煎紡涓嶆纭?");
        }
    }

    private void validateImageConfigClean(String providerSource, String customUrl, String apiKey) {
        if (!StringUtils.hasText(providerSource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择生图平台");
        }
        ProviderOption option = requireProviderOption(providerSource);
        if (option.customUrlRequired()) {
            normalizeBaseUrl(customUrl);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写生图 API Key");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图 API Key 格式不正确");
        }
    }

    private void validateModelListConfig(String providerSource, String customUrl, String apiKey) {
        if (!StringUtils.hasText(providerSource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u8bf7\u9009\u62e9\u5e73\u53f0");
        }
        ProviderOption option = requireProviderOption(providerSource);
        if (option.customUrlRequired()) {
            normalizeBaseUrl(customUrl);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u8bf7\u586b\u5199 API Key");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "API Key \u683c\u5f0f\u4e0d\u6b63\u786e");
        }
    }

    /*
    private void ensureTestRateAllowed(long userId, String keyScope) {
        long now = System.currentTimeMillis();
        String scopeKey = userId + ":" + normalizeKeyScope(keyScope);
        Long previous = lastTestAtByScope.put(scopeKey, now);
        if (previous != null && now - previous < TEST_MIN_INTERVAL_MS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "测试太频繁，请稍后再试");
        }
    }

    */

    private void ensureTestRateAllowed(long userId, String keyScope) {
        long now = System.currentTimeMillis();
        String scopeKey = userId + ":" + normalizeKeyScope(keyScope);
        Long previous = lastTestAtByScope.put(scopeKey, now);
        if (previous != null && now - previous < TEST_MIN_INTERVAL_MS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "测试太频繁，请稍后再试");
        }
    }

    private UserAiProviderTestResult sendOpenAiCompatibleTest(
            String providerSource,
            String modelName,
            String apiKey,
            String baseUrl
    ) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(TEST_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(buildTestRequestBody(modelName)));
            if ("openrouter".equals(providerSource)) {
                builder.header("X-Title", "Clover Tavern");
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMs(startNanos);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new UserAiProviderTestResult(
                        true,
                        "连接成功，Key 与模型可用",
                        providerSource,
                        modelName,
                        latencyMs,
                        baseUrl
                );
            }
            return new UserAiProviderTestResult(
                    false,
                    buildProviderFailureMessage(response.statusCode(), extractProviderErrorMessage(response.body())),
                    providerSource,
                    modelName,
                    latencyMs,
                    baseUrl
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "接口地址格式不正确");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new UserAiProviderTestResult(false, "测试已中断，请重试", providerSource, modelName, elapsedMs(startNanos), baseUrl);
        } catch (IOException ex) {
            return new UserAiProviderTestResult(
                    false,
                    "无法连接到平台，请检查网络、平台地址或防火墙配置",
                    providerSource,
                    modelName,
                    elapsedMs(startNanos),
                    baseUrl
            );
        }
    }

    private UserAiProviderModelsResult sendOpenAiCompatibleModels(
            String providerSource,
            String apiKey,
            String baseUrl,
            String keyScope
    ) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(buildModelsUri(providerSource, baseUrl, keyScope))
                    .timeout(TEST_REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .GET();
            if ("openrouter".equals(providerSource)) {
                builder.header("X-Title", "Clover Tavern");
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMs(startNanos);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<UserAiProviderModelItem> modelItems = parseModelItems(response.body());
                List<String> models = modelItems.stream()
                        .map(UserAiProviderModelItem::id)
                        .filter(StringUtils::hasText)
                        .limit(MODEL_LIST_LIMIT)
                        .toList();
                if (models.isEmpty()) {
                    return new UserAiProviderModelsResult(
                            false,
                            "\u5e73\u53f0\u8fd4\u56de\u4e86\u7a7a\u6a21\u578b\u5217\u8868\uff0c\u8bf7\u624b\u52a8\u586b\u5199\u6a21\u578b ID",
                            providerSource,
                            List.of(),
                            List.of(),
                            latencyMs,
                            baseUrl
                    );
                }
                return new UserAiProviderModelsResult(
                        true,
                        "\u5df2\u4ece\u670d\u52a1\u5546\u62c9\u53d6\u6a21\u578b\u5217\u8868",
                        providerSource,
                        models,
                        modelItems,
                        latencyMs,
                        baseUrl
                );
            }
            return new UserAiProviderModelsResult(
                    false,
                    buildProviderFailureMessage(response.statusCode(), extractProviderErrorMessage(response.body())),
                    providerSource,
                    List.of(),
                    List.of(),
                    latencyMs,
                    baseUrl
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u63a5\u53e3\u5730\u5740\u683c\u5f0f\u4e0d\u6b63\u786e");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new UserAiProviderModelsResult(false, "\u6a21\u578b\u5217\u8868\u62c9\u53d6\u5df2\u4e2d\u65ad\uff0c\u8bf7\u91cd\u8bd5", providerSource, List.of(), List.of(), elapsedMs(startNanos), baseUrl);
        } catch (IOException ex) {
            return new UserAiProviderModelsResult(
                    false,
                    "\u65e0\u6cd5\u8fde\u63a5\u5230\u5e73\u53f0\u7684 /models\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u3001\u5e73\u53f0\u5730\u5740\u6216\u624b\u52a8\u586b\u5199\u6a21\u578b ID",
                    providerSource,
                    List.of(),
                    List.of(),
                    elapsedMs(startNanos),
                    baseUrl
            );
        }
    }

    private UserAiProviderTestResult sendScopedCapabilityCatalogTest(
            String providerSource,
            String modelName,
            String apiKey,
            String baseUrl,
            String capability,
            String capabilityLabel
    ) {
        UserAiProviderModelsResult modelsResult = sendOpenAiCompatibleModels(providerSource, apiKey, baseUrl, capability);
        if (!modelsResult.ok()) {
            return new UserAiProviderTestResult(
                    false,
                    modelsResult.message(),
                    providerSource,
                    modelName,
                    modelsResult.latencyMs(),
                    modelsResult.normalizedCustomUrl()
            );
        }
        List<String> matchedModels = new ArrayList<>();
        if (modelsResult.modelItems() != null && !modelsResult.modelItems().isEmpty()) {
            matchedModels = modelsResult.modelItems().stream()
                    .filter(item -> modelItemSupportsCapability(item, capability))
                    .map(UserAiProviderModelItem::id)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (matchedModels.isEmpty() && modelsResult.models() != null) {
            matchedModels = modelsResult.models().stream()
                    .filter(StringUtils::hasText)
                    .filter(item -> matchCapabilityByName(item, capability))
                    .toList();
        }
        boolean modelLooksLikeCapability = matchCapabilityByName(modelName, capability);
        boolean modelFound = matchedModels.stream().anyMatch(item -> item.equalsIgnoreCase(modelName));
        if (modelFound || (matchedModels.isEmpty() && modelLooksLikeCapability)) {
            return new UserAiProviderTestResult(
                    true,
                    capabilityLabel + " \u63a5\u53e3\u53ef\u7528\uff0cKey \u4e0e\u6a21\u578b\u53ef\u7528",
                    providerSource,
                    modelName,
                    modelsResult.latencyMs(),
                    modelsResult.normalizedCustomUrl()
            );
        }
        if (!modelLooksLikeCapability) {
            return new UserAiProviderTestResult(
                    false,
                    "\u5f53\u524d\u6a21\u578b\u4e0d\u50cf" + capabilityLabel + "\u6a21\u578b\uff0c\u8bf7\u5207\u6362\u5176\u4ed6" + capabilityLabel + "\u6a21\u578b",
                    providerSource,
                    modelName,
                    modelsResult.latencyMs(),
                    modelsResult.normalizedCustomUrl()
            );
        }
        return new UserAiProviderTestResult(
                true,
                capabilityLabel + " \u5df2\u8fde\u901a\uff0c\u8bf7\u4ee5\u5b9e\u9645" + capabilityLabel + "\u751f\u6210\u7ed3\u679c\u4e3a\u51c6",
                providerSource,
                modelName,
                modelsResult.latencyMs(),
                modelsResult.normalizedCustomUrl()
        );
    }

    private List<UserAiProviderModelItem> parseModelItems(String body) throws IOException {
        String text = safe(body);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(text);
        Map<String, UserAiProviderModelItem> items = new java.util.LinkedHashMap<>();
        collectModelItems(root.path("data"), items);
        collectModelItems(root.path("models"), items);
        if (root.isArray()) {
            collectModelItems(root, items);
        }
        return new ArrayList<>(items.values()).stream()
                .limit(MODEL_LIST_LIMIT)
                .toList();
    }

    private void collectModelItems(JsonNode node, Map<String, UserAiProviderModelItem> items) {
        if (node == null || node.isMissingNode() || node.isNull() || items.size() >= MODEL_LIST_LIMIT) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectModelItems(item, items);
                if (items.size() >= MODEL_LIST_LIMIT) {
                    break;
                }
            }
            return;
        }
        String id = "";
        if (node.isTextual()) {
            id = node.asText("");
        } else if (node.isObject()) {
            id = firstText(node.path("id").asText(""), node.path("name").asText(""));
        }
        id = safe(id);
        if (!StringUtils.hasText(id) || id.length() > 255 || id.contains("\n") || id.contains("\r")) {
            return;
        }
        if (items.containsKey(id)) {
            return;
        }
        List<String> inputModalities = extractInputModalities(node);
        List<String> outputModalities = extractOutputModalities(node);
        List<String> capabilityHints = inferCapabilityHints(id, inputModalities, outputModalities);
        items.put(id, new UserAiProviderModelItem(
                id,
                safe(node.path("name").asText("")),
                inputModalities,
                outputModalities,
                capabilityHints
        ));
    }

    private static List<String> extractInputModalities(JsonNode node) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectModalityValues(node.path("input_modalities"), values);
        collectModalityValues(node.path("modalities"), values);
        JsonNode architecture = node.path("architecture");
        collectModalityValues(architecture.path("input_modalities"), values);
        collectInputOutputFromModalityString(firstText(
                node.path("modality").asText(""),
                architecture.path("modality").asText("")
        ), values, null);
        return List.copyOf(values);
    }

    private static List<String> extractOutputModalities(JsonNode node) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectModalityValues(node.path("output_modalities"), values);
        JsonNode architecture = node.path("architecture");
        collectModalityValues(architecture.path("output_modalities"), values);
        collectInputOutputFromModalityString(firstText(
                node.path("modality").asText(""),
                architecture.path("modality").asText("")
        ), null, values);
        return List.copyOf(values);
    }

    private static void collectModalityValues(JsonNode node, LinkedHashSet<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectModalityValues(item, values);
            }
            return;
        }
        if (node.isTextual()) {
            addModalityTokens(node.asText(""), values);
        }
    }

    private static void collectInputOutputFromModalityString(String raw, LinkedHashSet<String> input, LinkedHashSet<String> output) {
        String text = safe(raw).toLowerCase();
        if (!StringUtils.hasText(text)) {
            return;
        }
        int arrow = text.indexOf("->");
        if (arrow >= 0) {
            if (input != null) {
                addModalityTokens(text.substring(0, arrow), input);
            }
            if (output != null) {
                addModalityTokens(text.substring(arrow + 2), output);
            }
            return;
        }
        if (input != null) {
            addModalityTokens(text, input);
        }
        if (output != null) {
            addModalityTokens(text, output);
        }
    }

    private static void addModalityTokens(String raw, LinkedHashSet<String> values) {
        String text = safe(raw).toLowerCase();
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (text.contains("image")) {
            values.add("image");
        }
        if (text.contains("audio")) {
            values.add("audio");
        }
        if (text.contains("speech")) {
            values.add("speech");
        }
        if (text.contains("text")) {
            values.add("text");
        }
        if (text.contains("video")) {
            values.add("video");
        }
    }

    private List<String> inferCapabilityHints(String modelId, List<String> inputModalities, List<String> outputModalities) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        boolean inputImage = containsAny(inputModalities, "image");
        boolean outputImage = containsAny(outputModalities, "image");
        boolean inputText = containsAny(inputModalities, "text");
        boolean outputText = containsAny(outputModalities, "text");
        boolean inputAudio = containsAny(inputModalities, "audio", "speech");
        boolean outputAudio = containsAny(outputModalities, "audio", "speech");
        if (outputImage) {
            hints.add("image");
        }
        if (inputImage && outputText) {
            hints.add("vision");
        }
        if (inputAudio && outputText) {
            hints.add("stt");
        }
        if (inputText && outputAudio) {
            hints.add("tts");
        }
        if (inputText && outputText) {
            hints.add("text");
        }
        if (hints.isEmpty()) {
            if (matchCapabilityByName(modelId, "text")) {
                hints.add("text");
            }
            if (matchCapabilityByName(modelId, "image")) {
                hints.add("image");
            }
            if (matchCapabilityByName(modelId, "vision")) {
                hints.add("vision");
            }
            if (matchCapabilityByName(modelId, "stt")) {
                hints.add("stt");
            }
            if (matchCapabilityByName(modelId, "tts")) {
                hints.add("tts");
            }
        }
        return List.copyOf(hints);
    }

    private static boolean containsAny(List<String> values, String... candidates) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            for (String candidate : candidates) {
                if (candidate.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean modelItemSupportsCapability(UserAiProviderModelItem item, String capability) {
        if (item == null) {
            return false;
        }
        if (containsAny(item.capabilityHints(), capability)) {
            return true;
        }
        return matchCapabilityByName(firstText(item.id(), item.name()), capability);
    }

    private static boolean matchCapabilityByName(String model, String capability) {
        String text = safe(model).toLowerCase();
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String noisyKeywords = "(embedding|reranker|ranker|thinking|reasoner|instruct|chat|captioner|coder)";
        String imageEditKeywords = "(image-?edit|img2img|image-to-image|image2image|inpaint|outpaint|controlnet|variation|variations|reference|remix|repaint|edit-only|paint-by-example|kontext)";
        if (("stt".equals(capability) || "tts".equals(capability)) && text.matches(".*" + noisyKeywords + ".*")) {
            return false;
        }
        return switch (capability) {
            case "text" -> !text.matches(".*(embedding|reranker|ranker|moderation|omni-moderation).*")
                    && !matchCapabilityByName(model, "vision")
                    && !matchCapabilityByName(model, "stt")
                    && !matchCapabilityByName(model, "tts")
                    && !matchCapabilityByName(model, "image");
            case "vision" -> !text.matches(".*(embedding|reranker|ranker).*") && text.matches(".*(vl|vision|multimodal|image-to-text|vision-language).*");
            case "stt" -> text.matches(".*(asr|stt|transcribe|transcription|speech2text|speech-to-text|speechrecognition|speech-recognition|whisper|sensevoice|paraformer).*");
            case "tts" -> !text.matches(".*(asr|stt|transcribe|transcription|speech2text|speech-to-text|speechrecognition|speech-recognition|whisper|sensevoice|paraformer|funasr).*")
                    && text.matches(".*(tts|text-to-speech|speech-synthesis|speechgeneration|speech-generation|cosyvoice|fish-speech|indextts|ttsd|voice-tts|voice_synth|voice-synth).*");
            case "image" -> text.matches(".*" + imageEditKeywords + ".*")
                    || text.matches(".*(flux|sdxl|stable[-_]?diffusion|dall[-_]?e|kolors|wanx|gpt-image|imagen|recraft|seedream|janus|text-to-image|text2image|t2i|image-generation|imagegeneration|generative-image).*");
            default -> false;
        };
    }

    private String buildTestRequestBody(String modelName) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);
        root.put("stream", false);
        root.put("max_tokens", 1);
        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", "user")
                .put("content", "ping");
        return objectMapper.writeValueAsString(root);
    }

    private UserAiProviderTestResult sendOpenAiCompatibleImageTest(
            String providerSource,
            String modelName,
            String apiKey,
            String baseUrl
    ) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest request = buildImageTestRequest(providerSource, modelName, apiKey, baseUrl);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMs(startNanos);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new UserAiProviderTestResult(
                        true,
                        "\u751f\u56fe\u63a5\u53e3\u53ef\u7528",
                        providerSource,
                        modelName,
                        latencyMs,
                        baseUrl
                );
            }
            return new UserAiProviderTestResult(
                    false,
                    buildProviderFailureMessage(response.statusCode(), extractProviderErrorMessage(response.body())),
                    providerSource,
                    modelName,
                    latencyMs,
                    baseUrl
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u63a5\u53e3\u5730\u5740\u683c\u5f0f\u4e0d\u6b63\u786e");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new UserAiProviderTestResult(false, "\u751f\u56fe\u6d4b\u8bd5\u5df2\u4e2d\u65ad\uff0c\u8bf7\u91cd\u8bd5", providerSource, modelName, elapsedMs(startNanos), baseUrl);
        } catch (IOException ex) {
            return new UserAiProviderTestResult(
                    false,
                    "\u65e0\u6cd5\u8fde\u63a5\u5230\u751f\u56fe\u63a5\u53e3\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u3001Key \u6216\u5e73\u53f0\u5730\u5740",
                    providerSource,
                    modelName,
                    elapsedMs(startNanos),
                    baseUrl
            );
        }
    }

    private HttpRequest buildImageTestRequest(
            String providerSource,
            String modelName,
            String apiKey,
            String baseUrl
    ) throws IOException {
        if ("siliconflow".equals(providerSource)) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", modelName);
            body.put("prompt", "test image auth");
            body.put("image_size", "512x512");
            body.put("image", TEST_REFERENCE_IMAGE_DATA_URL);
            return HttpRequest.newBuilder(URI.create(baseUrl + "/images/generations"))
                    .timeout(TEST_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        }
        if ("openrouter".equals(providerSource)) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", modelName);
            body.put("stream", false);
            ArrayNode modalities = body.putArray("modalities");
            modalities.add("image");
            modalities.add("text");
            body.putObject("image_config").put("aspect_ratio", "1:1");
            ArrayNode messages = body.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            ArrayNode content = message.putArray("content");
            content.addObject().put("type", "text").put("text", "test image auth");
            content.addObject().put("type", "image_url").putObject("image_url").put("url", TEST_REFERENCE_IMAGE_DATA_URL);
            return HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(TEST_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Title", "Clover Tavern")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        }
        if (isReferenceImageEditModel(modelName)) {
            String boundary = "----SillyTestBoundary" + System.currentTimeMillis();
            byte[] bodyBytes = buildImageEditTestBody(boundary, modelName);
            return HttpRequest.newBuilder(URI.create(baseUrl + "/images/edits"))
                    .timeout(TEST_REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("prompt", "test image auth");
        body.put("n", 1);
        body.put("size", "512x512");
        body.put("response_format", "b64_json");
        return HttpRequest.newBuilder(URI.create(baseUrl + "/images/generations"))
                .timeout(TEST_REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }

    private byte[] buildImageEditTestBody(String boundary, String modelName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeMultipartText(output, boundary, "model", modelName);
        writeMultipartText(output, boundary, "prompt", "test image auth");
        writeMultipartText(output, boundary, "n", "1");
        writeMultipartText(output, boundary, "size", "512x512");
        writeMultipartText(output, boundary, "response_format", "b64_json");
        writeMultipartFile(output, boundary, "image", "test.png", "image/png", TEST_REFERENCE_IMAGE_BYTES);
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private String extractProviderErrorMessage(String body) {
        String text = safe(body);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            JsonNode errorMessage = root.path("error").path("message");
            if (errorMessage.isTextual() && StringUtils.hasText(errorMessage.asText())) {
                return capMessage(errorMessage.asText());
            }
            JsonNode message = root.path("message");
            if (message.isTextual() && StringUtils.hasText(message.asText())) {
                return capMessage(message.asText());
            }
            JsonNode detail = root.path("detail");
            if (detail.isTextual() && StringUtils.hasText(detail.asText())) {
                return capMessage(detail.asText());
            }
        } catch (Exception ignored) {
            // Some providers return plain text or HTML on proxy errors.
        }
        if (text.startsWith("<")) {
            return "";
        }
        return capMessage(text.replaceAll("\\s+", " "));
    }

    private static String buildProviderFailureMessage(int statusCode, String detail) {
        String safeDetail = safe(detail).toLowerCase();
        if (safeDetail.contains("not available in your region")
                || safeDetail.contains("unsupported country")
                || safeDetail.contains("unsupported region")) {
            return "\u5f53\u524d\u6a21\u578b\u5728\u4f60\u6240\u5728\u5730\u533a\u4e0d\u53ef\u7528\uff0c\u8bf7\u5207\u6362\u5176\u4ed6\u751f\u56fe\u6a21\u578b";
        }
        String message = switch (statusCode) {
            case 400 -> "平台拒绝了请求，请检查模型 ID 是否正确";
            case 401, 403 -> "API Key 无效、权限不足或账户不可用";
            case 404 -> "接口地址或模型不存在";
            case 408 -> "平台响应超时，请稍后重试";
            case 429 -> "平台限流、余额不足或额度已用尽";
            default -> statusCode >= 500
                    ? "平台服务暂时不可用"
                    : "平台返回异常状态 " + statusCode;
        };
        if (StringUtils.hasText(detail)) {
            return message + "：" + detail;
        }
        return message;
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(1L, Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
    }

    private static String capMessage(String value) {
        String text = safe(value).replaceAll("(?i)(api[_-]?key|authorization|bearer|token)\\s*[:=]\\s*[^\\s,}]+", "$1=***");
        return text.length() > 180 ? text.substring(0, 180) + "..." : text;
    }

    private static ProviderOption requireProviderOption(String providerSource) {
        String source = safe(providerSource).toLowerCase();
        if (!SUPPORTED_SOURCES.contains(source)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "鏆備笉鏀寔璇ュ钩鍙?");
        }
        for (ProviderOption option : PROVIDER_OPTIONS) {
            if (option.value().equals(source)) {
                return option;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "暂不支持该平台");
    }

    private static List<ProviderOption> publicProviderOptions() {
        return PROVIDER_OPTIONS.stream()
                .map(option -> new ProviderOption(
                        option.value(),
                        option.label(),
                        option.defaultBaseUrl(),
                        option.customUrlRequired(),
                        option.defaultModel(),
                        option.modelPresets(),
                        option.helpText(),
                        option.apiKeyHint()
                ))
                .toList();
    }

    private static String resolveProviderBaseUrl(String providerSource, String customUrl) {
        ProviderOption option = requireProviderOption(providerSource);
        if (option.customUrlRequired()) {
            return normalizeBaseUrl(customUrl);
        }
        return normalizeBaseUrl(option.defaultBaseUrl());
    }

    private static String normalizeImageTestBaseUrl(String providerSource, String baseUrl) {
        String value = safe(baseUrl);
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value;
    }

    private static URI buildModelsUri(String providerSource, String baseUrl, String keyScope) {
        String value = safe(baseUrl);
        if ("siliconflow".equals(safe(providerSource)) && "image".equals(normalizeKeyScope(keyScope))) {
            return URI.create(value + "/models?type=image");
        }
        return URI.create(value + "/models");
    }

    private static String normalizeCustomUrlForStorage(String providerSource, String customUrl) {
        ProviderOption option = requireProviderOption(providerSource);
        return option.customUrlRequired() ? normalizeBaseUrl(customUrl) : "";
    }

    private static String normalizeBaseUrl(String raw) {
        String value = safe(raw);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "自定义接口地址不能为空");
        }
        value = stripTrailingSlash(value);
        String lower = value.toLowerCase();
        if (lower.endsWith("/chat/completions")) {
            value = value.substring(0, value.length() - "/chat/completions".length());
            value = stripTrailingSlash(value);
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("invalid url");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "接口地址必须是有效的 http/https 地址");
        }
        return value;
    }

    private static String stripTrailingSlash(String value) {
        String text = safe(value);
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String firstText(String... values) {
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

    private static boolean isReferenceImageEditModel(String modelName) {
        String text = safe(modelName).toLowerCase();
        return StringUtils.hasText(text) && text.matches(".*(image-?edit|img2img|image-to-image|image2image|inpaint|outpaint|controlnet|variation|variations|reference|remix|repaint|edit-only|paint-by-example|kontext).*");
    }

    private static void writeMultipartText(ByteArrayOutputStream output, String boundary, String name, String value) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(safe(value).getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeMultipartFile(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String fileName,
            String contentType,
            byte[] bytes
    ) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + safe(fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + firstText(contentType, "image/png") + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(bytes == null ? new byte[0] : bytes);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private AppUser resolveUser(String clientUid) {
        if (!StringUtils.hasText(clientUid)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 缺失");
        }
        String token = h5Auth.issueTokenForClientUid(clientUid.trim());
        return tokenService.validateAndLoadUser(token);
    }

    private int currentVipLevel(long userId) {
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(userId);
        return entitlementPolicyService.effectiveVipLevel(ext);
    }

    private boolean hasStoredKey(AppH5UserAiProvider row) {
        return hasStoredCipher(row == null ? null : row.getApiKeyCipher());
    }

    private boolean hasStoredSttKey(AppH5UserAiProvider row) {
        return hasStoredCipher(row == null ? null : row.getSttApiKeyCipher());
    }

    private boolean hasStoredTtsKey(AppH5UserAiProvider row) {
        return hasStoredCipher(row == null ? null : row.getTtsApiKeyCipher());
    }

    private boolean hasStoredImageKey(AppH5UserAiProvider row) {
        return hasStoredCipher(row == null ? null : row.getImageApiKeyCipher());
    }

    private boolean hasStoredCipher(String cipherText) {
        return StringUtils.hasText(cipherText) && StringUtils.hasText(decryptQuietly(cipherText));
    }

    private static boolean storedKeyApplies(AppH5UserAiProvider row, String providerSource, String customUrl) {
        return storedKeyApplies(row, providerSource, customUrl, "main");
    }

    private static boolean storedKeyApplies(AppH5UserAiProvider row, String providerSource, String customUrl, String keyScope) {
        String cipherText = storedApiKeyCipher(row, keyScope);
        if (!StringUtils.hasText(cipherText)) {
            return false;
        }
        String storedSource = normalizeSourceOrDefault(storedProviderSource(row, keyScope), "");
        String currentSource = safe(providerSource).toLowerCase();
        if (!StringUtils.hasText(storedSource) || !storedSource.equals(currentSource)) {
            return false;
        }
        if ("custom".equals(currentSource)) {
            String currentUrl;
            try {
                currentUrl = normalizeBaseUrl(customUrl);
            } catch (BusinessException ex) {
                return false;
            }
            return currentUrl.equals(safe(storedCustomUrl(row, keyScope)));
        }
        return true;
    }

    private String maskStoredKey(AppH5UserAiProvider row) {
        return maskStoredCipher(row == null ? null : row.getApiKeyCipher());
    }

    private String maskStoredSttKey(AppH5UserAiProvider row) {
        return maskStoredCipher(row == null ? null : row.getSttApiKeyCipher());
    }

    private String maskStoredTtsKey(AppH5UserAiProvider row) {
        return maskStoredCipher(row == null ? null : row.getTtsApiKeyCipher());
    }

    private String maskStoredImageKey(AppH5UserAiProvider row) {
        return maskStoredCipher(row == null ? null : row.getImageApiKeyCipher());
    }

    private String maskStoredCipher(String cipherText) {
        String apiKey = decryptQuietly(cipherText);
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String tail = apiKey.length() <= 4 ? apiKey : apiKey.substring(apiKey.length() - 4);
        return "****" + tail;
    }

    private static String storedProviderSource(AppH5UserAiProvider row, String keyScope) {
        if (row == null) {
            return "";
        }
        String scope = normalizeKeyScope(keyScope);
        if ("stt".equals(scope)) {
            return safe(row.getSttProviderSource());
        }
        if ("tts".equals(scope)) {
            return safe(row.getTtsProviderSource());
        }
        if ("image".equals(scope)) {
            return safe(row.getImageProviderSource());
        }
        return safe(row.getProviderSource());
    }

    private static String storedEffectiveProviderSource(AppH5UserAiProvider row, String keyScope) {
        String scope = normalizeKeyScope(keyScope);
        if ("image".equals(scope)) {
            return firstText(storedProviderSource(row, "image"), storedProviderSource(row, "main"));
        }
        if ("stt".equals(scope)) {
            return firstText(storedProviderSource(row, "stt"), storedProviderSource(row, "main"));
        }
        if ("tts".equals(scope)) {
            return firstText(storedProviderSource(row, "tts"), storedProviderSource(row, "main"));
        }
        return storedProviderSource(row, scope);
    }

    private static String storedCustomUrl(AppH5UserAiProvider row, String keyScope) {
        if (row == null) {
            return "";
        }
        String scope = normalizeKeyScope(keyScope);
        if ("stt".equals(scope)) {
            return safe(row.getSttCustomUrl());
        }
        if ("tts".equals(scope)) {
            return safe(row.getTtsCustomUrl());
        }
        if ("image".equals(scope)) {
            return safe(row.getImageCustomUrl());
        }
        return safe(row.getCustomUrl());
    }

    private static String storedEffectiveCustomUrl(AppH5UserAiProvider row, String keyScope) {
        String scope = normalizeKeyScope(keyScope);
        if ("image".equals(scope)) {
            return firstText(storedCustomUrl(row, "image"), storedCustomUrl(row, "main"));
        }
        if ("stt".equals(scope)) {
            return firstText(storedCustomUrl(row, "stt"), storedCustomUrl(row, "main"));
        }
        if ("tts".equals(scope)) {
            return firstText(storedCustomUrl(row, "tts"), storedCustomUrl(row, "main"));
        }
        return storedCustomUrl(row, scope);
    }

    private static String storedApiKeyCipher(AppH5UserAiProvider row, String keyScope) {
        if (row == null) {
            return "";
        }
        String scope = normalizeKeyScope(keyScope);
        if ("stt".equals(scope)) {
            return safe(row.getSttApiKeyCipher());
        }
        if ("tts".equals(scope)) {
            return safe(row.getTtsApiKeyCipher());
        }
        if ("image".equals(scope)) {
            return safe(row.getImageApiKeyCipher());
        }
        return safe(row.getApiKeyCipher());
    }

    private static String storedEffectiveApiKeyCipher(AppH5UserAiProvider row, String keyScope) {
        String scope = normalizeKeyScope(keyScope);
        if ("image".equals(scope)) {
            return firstText(storedApiKeyCipher(row, "image"), storedApiKeyCipher(row, "main"));
        }
        if ("stt".equals(scope)) {
            return firstText(storedApiKeyCipher(row, "stt"), storedApiKeyCipher(row, "main"));
        }
        if ("tts".equals(scope)) {
            return firstText(storedApiKeyCipher(row, "tts"), storedApiKeyCipher(row, "main"));
        }
        return storedApiKeyCipher(row, scope);
    }

    private static String storedEffectiveModelName(AppH5UserAiProvider row, String keyScope) {
        if (row == null) {
            return "";
        }
        String scope = normalizeKeyScope(keyScope);
        if ("image".equals(scope)) {
            return firstText(safe(row.getImageModelName()), safe(row.getModelName()));
        }
        if ("stt".equals(scope)) {
            return firstText(safe(row.getSttModelName()), safe(row.getAudioModelName()), safe(row.getModelName()));
        }
        if ("tts".equals(scope)) {
            return firstText(safe(row.getTtsModelName()), safe(row.getAudioModelName()), safe(row.getModelName()));
        }
        return safe(row.getModelName());
    }

    private static boolean storedEffectiveKeyApplies(AppH5UserAiProvider row, String providerSource, String customUrl, String keyScope) {
        String cipherText = storedEffectiveApiKeyCipher(row, keyScope);
        if (!StringUtils.hasText(cipherText)) {
            return false;
        }
        String storedSource = normalizeSourceOrDefault(storedEffectiveProviderSource(row, keyScope), "");
        String currentSource = safe(providerSource).toLowerCase();
        if (!StringUtils.hasText(storedSource) || !storedSource.equals(currentSource)) {
            return false;
        }
        if ("custom".equals(currentSource)) {
            String currentUrl;
            try {
                currentUrl = normalizeBaseUrl(customUrl);
            } catch (BusinessException ex) {
                return false;
            }
            return currentUrl.equals(safe(storedEffectiveCustomUrl(row, keyScope)));
        }
        return true;
    }

    private String decryptQuietly(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return "";
        }
        try {
            return sensitiveTextCrypto.decrypt(cipherText);
        } catch (Exception ex) {
            log.warn("cannot decrypt stored user ai provider key");
            return "";
        }
    }

    private static AppH5UserAiProvider defaultRow(long userId) {
        AppH5UserAiProvider row = new AppH5UserAiProvider();
        row.setUserId(userId);
        row.setProviderMode("system");
        row.setProviderSource("");
        row.setModelName("");
        row.setVisionModelName("");
        row.setAudioModelName("");
        row.setSttModelName("");
        row.setSttProviderSource("");
        row.setSttApiKeyCipher("");
        row.setSttCustomUrl("");
        row.setTtsModelName("");
        row.setTtsVoiceName("");
        row.setTtsVoiceTemplateCode("");
        row.setTtsProviderSource("");
        row.setTtsApiKeyCipher("");
        row.setTtsCustomUrl("");
        row.setImageModelName("");
        row.setImageProviderSource("");
        row.setImageApiKeyCipher("");
        row.setImageCustomUrl("");
        row.setImageCharacterConsistencyMode("balanced");
        row.setImageReferenceSourceMode("latest_generated_first");
        row.setApiKeyCipher("");
        row.setCustomUrl("");
        return row;
    }

    private TtsVoiceProvisionService.TtsRuntimeContext buildTtsRuntimeContext(
            AppH5UserAiProvider row,
            boolean enabledGlobal,
            int currentVipLevel,
            int vipMinLevel
    ) {
        if (!enabledGlobal || currentVipLevel < vipMinLevel || row == null || !"custom".equals(normalizeMode(row.getProviderMode()))) {
            return new TtsVoiceProvisionService.TtsRuntimeContext(false, "", "", "", "");
        }
        try {
            String providerSource = hasSeparateTtsConfig(row)
                    ? normalizeSourceOrDefault(row.getTtsProviderSource(), "")
                    : normalizeSourceOrDefault(row.getProviderSource(), "");
            String customUrl = hasSeparateTtsConfig(row)
                    ? trim(row.getTtsCustomUrl(), 512)
                    : trim(row.getCustomUrl(), 512);
            String modelName = trim(audioTtsModel(row), 255);
            String apiKey = hasSeparateTtsConfig(row)
                    ? decryptQuietly(row.getTtsApiKeyCipher())
                    : decryptQuietly(row.getApiKeyCipher());
            return new TtsVoiceProvisionService.TtsRuntimeContext(
                    true,
                    providerSource,
                    resolveProviderBaseUrl(providerSource, customUrl),
                    apiKey,
                    modelName
            );
        } catch (BusinessException ex) {
            return new TtsVoiceProvisionService.TtsRuntimeContext(false, "", "", "", "");
        }
    }

    private static String audioSttModel(AppH5UserAiProvider row) {
        String value = safe(row == null ? null : row.getSttModelName());
        if (!value.isBlank()) {
            return value;
        }
        return safe(row == null ? null : row.getAudioModelName());
    }

    private static String audioTtsModel(AppH5UserAiProvider row) {
        String value = safe(row == null ? null : row.getTtsModelName());
        if (!value.isBlank()) {
            return value;
        }
        return safe(row == null ? null : row.getAudioModelName());
    }

    private static String audioTtsVoice(AppH5UserAiProvider row) {
        return safe(row == null ? null : row.getTtsVoiceName());
    }

    private static String denyReason(boolean enabledGlobal, int currentVipLevel, int vipMinLevel) {
        if (!enabledGlobal) {
            return "当前暂未开放自定义 API";
        }
        if (currentVipLevel < vipMinLevel) {
            return "当前账号等级暂不支持自定义 API";
        }
        return "";
    }

    private static String normalizeMode(String raw) {
        return "custom".equalsIgnoreCase(safe(raw)) ? "custom" : "system";
    }

    private static String normalizeSource(String raw) {
        String value = safe(raw).toLowerCase();
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (!SUPPORTED_SOURCES.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的平台");
        }
        return value;
    }

    private static String normalizeSourceOrDefault(String raw, String fallback) {
        try {
            String value = normalizeSource(raw);
            return StringUtils.hasText(value) ? value : fallback;
        } catch (BusinessException ex) {
            return fallback;
        }
    }

    private static boolean hasSeparateTtsConfig(AppH5UserAiProvider row) {
        return row != null && StringUtils.hasText(normalizeSourceOrDefault(row.getTtsProviderSource(), ""));
    }

    private static boolean hasSeparateSttConfig(AppH5UserAiProvider row) {
        return row != null && StringUtils.hasText(normalizeSourceOrDefault(row.getSttProviderSource(), ""));
    }

    private static boolean hasSeparateImageConfig(AppH5UserAiProvider row) {
        return row != null && StringUtils.hasText(normalizeSourceOrDefault(row.getImageProviderSource(), ""));
    }

    private static String normalizeImageCharacterConsistencyMode(String raw) {
        String value = safe(raw).toLowerCase();
        if ("free".equals(value) || "prompt_first".equals(value) || "prompt-first".equals(value)) {
            return "free";
        }
        if ("strong".equals(value) || "reference_only".equals(value) || "reference-only".equals(value)) {
            return "strong";
        }
        return "balanced";
    }

    private static String normalizeImageReferenceSourceMode(String raw) {
        String value = safe(raw).toLowerCase();
        if ("avatar_only".equals(value) || "avatar-only".equals(value) || "card_only".equals(value) || "card-only".equals(value)) {
            return "avatar_only";
        }
        return "latest_generated_first";
    }

    private static String resolveTestStoredScope(String keyScope, boolean sttUseSeparateConfig, boolean ttsUseSeparateConfig, boolean imageUseSeparateConfig) {
        String scope = normalizeKeyScope(keyScope);
        if ("stt".equals(scope)) {
            return sttUseSeparateConfig ? "stt" : "main";
        }
        if ("tts".equals(scope)) {
            return ttsUseSeparateConfig ? "tts" : "main";
        }
        if ("image".equals(scope)) {
            return imageUseSeparateConfig ? "image" : "main";
        }
        return "main";
    }

    private static String normalizeKeyScope(String raw) {
        String scope = safe(raw);
        if ("stt".equalsIgnoreCase(scope)) {
            return "stt";
        }
        if ("tts".equalsIgnoreCase(scope)) {
            return "tts";
        }
        if ("image".equalsIgnoreCase(scope)) {
            return "image";
        }
        return "main";
    }

    private static String trim(String value, int max) {
        String text = safe(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max).trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }
}
