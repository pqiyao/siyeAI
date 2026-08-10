package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiProviderCallException;
import com.example.sillyspringboot.ai.service.AiProviderFailurePolicy;
import com.example.sillyspringboot.ai.service.AiMediaAttemptTelemetry;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.service.TtsVoiceProvisionService;
import com.example.sillyspringboot.ops.service.UserTtsVoiceService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.net.BoundedHttpBodyHandlers;
import com.example.sillyspringboot.shared.net.MediaPayloadValidator;
import com.example.sillyspringboot.shared.net.OutboundUrlGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ChatAudioSpeechService {

    private static final int MAX_INPUT_CHARS = 1200;
    private static final int MAX_AUDIO_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final String DEFAULT_VOICE_NAME = "alloy";
    private static final String DEFAULT_SILICONFLOW_VOICE_NAME = "alex";
    private static final String DEFAULT_RESPONSE_FORMAT = "mp3";
    private static final int TTS_TOTAL_TIMEOUT_SECONDS = 105;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final Set<String> OPENAI_VOICE_NAMES = Set.of("alloy", "nova", "shimmer", "echo", "fable", "onyx");
    private static final Set<String> SILICONFLOW_VOICE_NAMES = Set.of("alex", "benjamin", "charles", "david", "anna", "bella", "claire", "diana");

    public record AudioSpeechResult(byte[] audioBytes, String mimeType, String modelName, String voiceName) {
    }

    private record RawSpeechResponse(byte[] bytes, String contentType) {}

    record SpeechSelection(String modelName, String voiceName, String voiceTemplateCode) {}

    static Long privateVoiceIdForRuntime(boolean customModeActive, Long userVoiceId) {
        return customModeActive && userVoiceId != null && userVoiceId > 0 ? userVoiceId : null;
    }

    private record SpeechRuntime(
            String providerKey,
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String voiceName,
            String voiceTemplateCode,
            Long deploymentId,
            boolean customModeActive,
            int connectTimeoutSeconds,
            int requestTimeoutSeconds
    ) {}

    private final H5UserAiProviderService userAiProviderService;
    private final TtsVoiceProvisionService ttsVoiceProvisionService;
    private final UserTtsVoiceService userTtsVoiceService;
    private final AiRoutingService routingService;
    private final ObjectMapper objectMapper;
    private final AiMediaAttemptTelemetry attemptTelemetry;

    public ChatAudioSpeechService(
            H5UserAiProviderService userAiProviderService,
            TtsVoiceProvisionService ttsVoiceProvisionService,
            ObjectMapper objectMapper,
            AiRoutingService routingService
    ) {
        this(userAiProviderService, ttsVoiceProvisionService, null, objectMapper, routingService, null);
    }

    @Autowired
    public ChatAudioSpeechService(
            H5UserAiProviderService userAiProviderService,
            TtsVoiceProvisionService ttsVoiceProvisionService,
            UserTtsVoiceService userTtsVoiceService,
            ObjectMapper objectMapper,
            AiRoutingService routingService,
            AiMediaAttemptTelemetry attemptTelemetry
    ) {
        this.userAiProviderService = userAiProviderService;
        this.ttsVoiceProvisionService = ttsVoiceProvisionService;
        this.userTtsVoiceService = userTtsVoiceService;
        this.routingService = routingService;
        this.objectMapper = objectMapper;
        this.attemptTelemetry = attemptTelemetry;
    }

    public AudioSpeechResult synthesizeForUser(long userId, String text) {
        return synthesizeForUser(userId, text, "", "", "", null, "");
    }

    public AudioSpeechResult synthesizeForUser(
            long userId,
            String text,
            String ttsModelNameOverride,
            String ttsVoiceNameOverride,
            String ttsVoiceTemplateCodeOverride
    ) {
        return synthesizeForUser(
                userId, text, ttsModelNameOverride, ttsVoiceNameOverride,
                ttsVoiceTemplateCodeOverride, null, "");
    }

    public AudioSpeechResult synthesizeForUser(
            long userId,
            String text,
            String ttsModelNameOverride,
            String ttsVoiceNameOverride,
            String ttsVoiceTemplateCodeOverride,
            Long ttsUserVoiceId
    ) {
        return synthesizeForUser(
                userId, text, ttsModelNameOverride, ttsVoiceNameOverride,
                ttsVoiceTemplateCodeOverride, ttsUserVoiceId, "");
    }

    public AudioSpeechResult synthesizeForUser(
            long userId,
            String text,
            String ttsModelNameOverride,
            String ttsVoiceNameOverride,
            String ttsVoiceTemplateCodeOverride,
            Long ttsUserVoiceId,
            String ttsOverrideProviderSource
    ) {
        String safeText = normalizeSpeechText(text);
        if (!StringUtils.hasText(safeText)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音内容不能为空");
        }

        List<SpeechRuntime> runtimes = prioritizeTemplateCompatibleRuntimes(
                resolveRuntimes(userId), ttsVoiceTemplateCodeOverride, ttsOverrideProviderSource);
        long deadlineNanos = System.nanoTime() + TTS_TOTAL_TIMEOUT_SECONDS * NANOS_PER_SECOND;
        BusinessException last = null;
        int attemptNo = 0;
        String telemetryRequestId = attemptTelemetry == null
                ? "" : attemptTelemetry.newRequestId(AiCapability.TTS);
        for (SpeechRuntime runtime : runtimes) {
            if (remainingDeadlineSeconds(deadlineNanos) <= 0) {
                throw ttsDeadlineExceeded();
            }
            attemptNo++;
            AiMediaAttemptTelemetry.Attempt attempt = startTelemetry(telemetryRequestId, runtime, attemptNo);
            try {
                boolean applyScopedOverride = providerScopeMatchesRuntime(
                        ttsOverrideProviderSource, runtime.providerSource());
                AudioSpeechResult result = synthesizeAttempt(
                        userId,
                        safeText,
                        applyScopedOverride ? ttsModelNameOverride : "",
                        applyScopedOverride ? ttsVoiceNameOverride : "",
                        applyScopedOverride ? ttsVoiceTemplateCodeOverride : "",
                        ttsUserVoiceId,
                        runtime,
                        deadlineNanos
                );
                if (remainingDeadlineSeconds(deadlineNanos) <= 0) {
                    throw ttsDeadlineExceeded();
                }
                recordSuccessQuietly(runtime.deploymentId());
                successTelemetry(attempt);
                return result;
            } catch (BusinessException ex) {
                failureTelemetry(attempt, ex);
                last = ex;
                if (!AiProviderFailurePolicy.shouldFallback(ex)) {
                    recordConfigurationErrorQuietly(runtime.deploymentId(), ex.getMessage());
                    throw ex;
                }
                if (AiProviderFailurePolicy.shouldCountCircuitFailure(ex)) {
                    recordFailureQuietly(runtime.deploymentId(), ex.getMessage());
                }
                if (remainingDeadlineSeconds(deadlineNanos) <= 0) {
                    throw ttsDeadlineExceeded();
                }
            }
        }
        throw last == null
                ? new BusinessException(ErrorCode.FORBIDDEN, "语音合成服务尚未配置")
                : last;
    }

    public AudioSpeechResult synthesizePrivateUserVoice(long userId, String text, long userVoiceId) {
        String safeText = normalizeSpeechText(text);
        if (!StringUtils.hasText(safeText)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "试听文字不能为空");
        }
        if (userVoiceId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择要试听的自建音色");
        }
        List<SpeechRuntime> runtimes = resolveRuntimes(userId);
        if (runtimes.size() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "自建音色试听必须使用你自己的硅基流动 API Key");
        }
        SpeechRuntime runtime = runtimes.get(0);
        if (!runtime.customModeActive()
                || !"siliconflow".equalsIgnoreCase(safe(runtime.providerSource()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "自建音色试听必须使用你自己的硅基流动 API Key");
        }
        long deadlineNanos = System.nanoTime() + TTS_TOTAL_TIMEOUT_SECONDS * NANOS_PER_SECOND;
        return synthesizeAttempt(
                userId, safeText, "", "", "", userVoiceId, runtime, deadlineNanos);
    }

    private List<SpeechRuntime> prioritizeTemplateCompatibleRuntimes(
            List<SpeechRuntime> runtimes,
            String templateOverride,
            String overrideProviderSource
    ) {
        if (runtimes == null || runtimes.size() < 2) {
            return runtimes == null ? List.of() : runtimes;
        }
        List<SpeechRuntime> compatible = new ArrayList<>();
        List<SpeechRuntime> fallback = new ArrayList<>();
        for (SpeechRuntime runtime : runtimes) {
            if (runtime.customModeActive()) {
                fallback.add(runtime);
                continue;
            }
            String requestedTemplate = providerScopeMatchesRuntime(overrideProviderSource, runtime.providerSource())
                    ? firstNonBlank(templateOverride, runtime.voiceTemplateCode())
                    : runtime.voiceTemplateCode();
            TtsVoiceProvisionService.TtsRuntimeContext context = new TtsVoiceProvisionService.TtsRuntimeContext(
                    false, runtime.providerSource(), runtime.baseUrl(), runtime.apiKey(), runtime.modelName());
            if (StringUtils.hasText(requestedTemplate)
                    && ttsVoiceProvisionService.isTemplateCompatible(requestedTemplate, context)) {
                compatible.add(runtime);
            } else {
                fallback.add(runtime);
            }
        }
        if (compatible.isEmpty()) {
            return runtimes;
        }
        compatible.addAll(fallback);
        return List.copyOf(compatible);
    }

    private List<SpeechRuntime> resolveRuntimes(long userId) {
        H5UserAiProviderService.UserTtsSettings settings = userAiProviderService.resolveActiveTtsSettingsForUser(userId);
        if (settings != null) {
            return List.of(new SpeechRuntime(
                    "user_byok", safe(settings.providerSource()), safe(settings.baseUrl()), safe(settings.apiKey()),
                    safe(settings.modelName()), safe(settings.voiceName()), safe(settings.voiceTemplateCode()),
                    null, true, 10, 90
            ));
        }
        if (userAiProviderService.isCustomModeSelectedForUser(userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户自定义 TTS 配置不可用，请检查模型、地址和 API Key");
        }
        if (!routingService.isCapabilityEnabled(AiCapability.TTS)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先配置可用的自定义 API");
        }
        H5UserAiProviderService.OfficialTtsVoicePreference preference =
                userAiProviderService.resolveOfficialTtsVoicePreferenceForUser(userId);
        List<SpeechRuntime> runtimes = new ArrayList<>();
        for (AiRoutingService.ResolvedProvider provider : routingService.resolve(AiCapability.TTS)) {
            String preferredVoiceName = isOfficialBuiltInVoiceAllowed(provider.modelName(), preference.voiceName())
                    ? preference.voiceName()
                    : provider.voiceName();
            runtimes.add(new SpeechRuntime(
                    provider.providerKey(), provider.vendor(), provider.baseUrl(), provider.apiKey(), provider.modelName(),
                    preferredVoiceName, preference.templateCode(), provider.deploymentId(), false,
                    provider.connectTimeoutSeconds(), provider.requestTimeoutSeconds()
            ));
        }
        if (runtimes.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统语音合成供应商尚未配置");
        }
        return List.copyOf(runtimes);
    }

    private AudioSpeechResult synthesizeAttempt(
            long userId,
            String safeText,
            String modelOverride,
            String voiceOverride,
            String templateOverride,
            Long userVoiceId,
            SpeechRuntime runtime,
            long deadlineNanos
    ) {
        requireRemainingDeadline(deadlineNanos);
        SpeechSelection selection = selectSpeechSettings(
                runtime.customModeActive(), runtime.modelName(), runtime.voiceName(), runtime.voiceTemplateCode(),
                modelOverride, voiceOverride, templateOverride);
        String modelName = selection.modelName();
        String configuredVoice = selection.voiceName();
        String configuredTemplateCode = selection.voiceTemplateCode();
        if (!StringUtils.hasText(runtime.apiKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前未配置可用的 API Key");
        }
        Long effectiveUserVoiceId = privateVoiceIdForRuntime(runtime.customModeActive(), userVoiceId);
        if (effectiveUserVoiceId != null) {
            if (userTtsVoiceService == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "自建音色服务尚未加载");
            }
            UserTtsVoiceService.RuntimeVoice resolvedVoice = userTtsVoiceService.resolveForRuntime(
                    userId,
                    effectiveUserVoiceId,
                    new TtsVoiceProvisionService.TtsRuntimeContext(
                            runtime.customModeActive(), runtime.providerSource(), runtime.baseUrl(), runtime.apiKey(), runtime.modelName()));
            configuredVoice = safe(resolvedVoice.voiceUri());
            modelName = safe(resolvedVoice.modelName());
            configuredTemplateCode = "";
        } else if (StringUtils.hasText(configuredTemplateCode)) {
            TtsVoiceProvisionService.TtsRuntimeContext runtimeContext = new TtsVoiceProvisionService.TtsRuntimeContext(
                    runtime.customModeActive(), runtime.providerSource(), runtime.baseUrl(), runtime.apiKey(), modelName);
            // An official route may change after a user saved a preference. Keep speech available by
            // falling back to the deployment voice when that old template no longer matches the route.
            if (runtime.customModeActive()
                    || ttsVoiceProvisionService.isTemplateCompatible(configuredTemplateCode, runtimeContext)) {
                TtsVoiceProvisionService.ResolvedVoice resolvedVoice = ttsVoiceProvisionService.resolveVoiceForUser(
                        userId, configuredTemplateCode, runtimeContext, deadlineNanos);
                configuredVoice = safe(resolvedVoice.voiceUri());
                modelName = safe(resolvedVoice.modelName());
            } else {
                configuredTemplateCode = "";
            }
        }
        if (!StringUtils.hasText(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先配置语音合成模型");
        }
        String voiceName = resolveVoiceName(modelName, configuredVoice);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("input", safeText);
        if (StringUtils.hasText(voiceName)) payload.put("voice", voiceName);
        payload.put("response_format", DEFAULT_RESPONSE_FORMAT);
        try {
            int remainingSeconds = requireRemainingDeadline(deadlineNanos);
            RawSpeechResponse raw = buildRestClient(
                    runtime.baseUrl(), runtime.apiKey(),
                    boundedAttemptTimeoutSeconds(runtime.connectTimeoutSeconds(), remainingSeconds),
                    boundedAttemptTimeoutSeconds(runtime.requestTimeoutSeconds(), remainingSeconds)).post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("audio/mpeg"), MediaType.parseMediaType("audio/mp3"), MediaType.APPLICATION_OCTET_STREAM)
                    .body(payload)
                    .exchange((request, response) -> {
                        byte[] bytes = BoundedHttpBodyHandlers.readBytes(response.getBody(), MAX_AUDIO_RESPONSE_BYTES);
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw AiProviderCallException.http(
                                     response.getStatusCode().value(),
                                    safeProviderErrorMessage(
                                            new String(bytes, StandardCharsets.UTF_8), runtime.customModeActive()),
                                    null
                            );
                        }
                        MediaType contentType = response.getHeaders().getContentType();
                        return new RawSpeechResponse(bytes, contentType == null ? "" : contentType.toString());
                    });
            byte[] body = raw == null ? null : raw.bytes();
            if (body == null || body.length == 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音合成结果为空");
            }
            requireRemainingDeadline(deadlineNanos);
            String contentType = MediaPayloadValidator.requireAudio(body, raw.contentType());
            return new AudioSpeechResult(body, contentType, modelName, voiceName);
        } catch (BusinessException ex) {
            throw ex;
        } catch (BoundedHttpBodyHandlers.BodyTooLargeException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "语音合成结果过大，请缩短文本后重试");
        } catch (RestClientResponseException ex) {
            throw AiProviderCallException.http(
                    ex.getStatusCode().value(),
                    safeProviderErrorMessage(ex.getResponseBodyAsString(), runtime.customModeActive()),
                    ex
            );
        } catch (RestClientException ex) {
            throw AiProviderCallException.transientFailure("语音合成服务暂时不可用", ex);
        }
    }

    private void recordSuccessQuietly(Long deploymentId) {
        if (deploymentId == null) return;
        try { routingService.recordSuccess(deploymentId); } catch (RuntimeException ignored) {}
    }

    private void recordFailureQuietly(Long deploymentId, String message) {
        if (deploymentId == null) return;
        try { routingService.recordFailure(deploymentId, message); } catch (RuntimeException ignored) {}
    }

    private void recordConfigurationErrorQuietly(Long deploymentId, String message) {
        if (deploymentId == null) return;
        try { routingService.recordConfigurationError(deploymentId, message); } catch (RuntimeException ignored) {}
    }

    private AiMediaAttemptTelemetry.Attempt startTelemetry(
            String requestId,
            SpeechRuntime runtime,
            int attemptNo
    ) {
        if (attemptTelemetry == null) return null;
        return attemptTelemetry.start(
                requestId, AiCapability.TTS, attemptNo, runtime.providerKey(), runtime.providerSource(),
                runtime.modelName(), runtime.deploymentId() == null);
    }

    private void successTelemetry(AiMediaAttemptTelemetry.Attempt attempt) {
        if (attemptTelemetry != null) attemptTelemetry.success(attempt);
    }

    private void failureTelemetry(AiMediaAttemptTelemetry.Attempt attempt, Throwable error) {
        if (attemptTelemetry != null) attemptTelemetry.failure(attempt, error);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return "";
    }

    static SpeechSelection selectSpeechSettings(
            boolean customModeActive,
            String runtimeModel,
            String runtimeVoice,
            String runtimeTemplate,
            String modelOverride,
            String voiceOverride,
            String templateOverride
    ) {
        if (!customModeActive) {
            boolean allowedVoiceOverride = isOfficialBuiltInVoiceAllowed(runtimeModel, voiceOverride);
            String selectedVoice = allowedVoiceOverride
                    ? safe(voiceOverride)
                    : safe(runtimeVoice);
            String selectedTemplate = StringUtils.hasText(templateOverride)
                    ? safe(templateOverride)
                    : (allowedVoiceOverride ? "" : safe(runtimeTemplate));
            return new SpeechSelection(
                    safe(runtimeModel),
                    selectedVoice,
                    selectedTemplate);
        }
        boolean hasVoiceOverride = StringUtils.hasText(voiceOverride);
        return new SpeechSelection(
                firstNonBlank(modelOverride, runtimeModel),
                firstNonBlank(voiceOverride, runtimeVoice),
                StringUtils.hasText(templateOverride)
                        ? safe(templateOverride)
                        : (hasVoiceOverride ? "" : safe(runtimeTemplate)));
    }

    static boolean providerScopeMatchesRuntime(String overrideProviderSource, String runtimeProviderSource) {
        if (!StringUtils.hasText(overrideProviderSource)) return true;
        return StringUtils.hasText(runtimeProviderSource)
                && overrideProviderSource.trim().equalsIgnoreCase(runtimeProviderSource.trim());
    }

    static boolean isOfficialBuiltInVoiceAllowed(String modelName, String voiceName) {
        String normalizedVoice = safe(voiceName).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedVoice) || normalizedVoice.contains(":")) {
            return false;
        }
        if (supportsOpenAiVoicePreset(modelName)) {
            return OPENAI_VOICE_NAMES.contains(normalizedVoice);
        }
        if (supportsSiliconFlowVoicePreset(modelName)) {
            return SILICONFLOW_VOICE_NAMES.contains(normalizedVoice);
        }
        return false;
    }

    private RestClient buildRestClient(
            String baseUrl,
            String apiKey,
            int connectTimeoutSeconds,
            int requestTimeoutSeconds
    ) {
        String safeBaseUrl = OutboundUrlGuard.requirePublicHttpUrl(
                baseUrl, "TTS 服务地址不安全，请使用可公开访问的 HTTP(S) 地址").toString();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(clamp(connectTimeoutSeconds, 1, TTS_TOTAL_TIMEOUT_SECONDS, 10)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(clamp(requestTimeoutSeconds, 1, TTS_TOTAL_TIMEOUT_SECONDS, 90)));
        return RestClient.builder()
                .baseUrl(safeBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    static int totalTimeoutSeconds() {
        return TTS_TOTAL_TIMEOUT_SECONDS;
    }

    static int boundedAttemptTimeoutSeconds(int configuredSeconds, int remainingSeconds) {
        int remaining = Math.max(1, Math.min(TTS_TOTAL_TIMEOUT_SECONDS, remainingSeconds));
        int configured = configuredSeconds > 0 ? configuredSeconds : 90;
        return Math.max(1, Math.min(configured, remaining));
    }

    private static int remainingDeadlineSeconds(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) return 0;
        long roundedUp = (remainingNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
        return (int) Math.max(1, Math.min(TTS_TOTAL_TIMEOUT_SECONDS, roundedUp));
    }

    private static int requireRemainingDeadline(long deadlineNanos) {
        int remainingSeconds = remainingDeadlineSeconds(deadlineNanos);
        if (remainingSeconds <= 0) throw ttsDeadlineExceeded();
        return remainingSeconds;
    }

    private static BusinessException ttsDeadlineExceeded() {
        return new BusinessException(ErrorCode.UPSTREAM_ERROR, "语音合成等待超时，请重试");
    }

    private String safeProviderErrorMessage(String responseBody, boolean customModeActive) {
        String message = "";
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "" : responseBody);
            message = safe(root.path("error").path("message").asText(""));
            if (!message.isBlank()) {
                return normalizeProviderSpeechMessage(message, customModeActive);
            }
            message = safe(root.path("message").asText(""));
            if (!message.isBlank()) {
                return normalizeProviderSpeechMessage(message, customModeActive);
            }
        } catch (Exception ignored) {
        }
        return "语音合成失败，请检查 TTS 模型和音色配置";
    }

    private String normalizeProviderSpeechMessage(String message, boolean customModeActive) {
        String text = safe(message);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("illegal operation") || lower.contains("not support") || lower.contains("unsupported")) {
            return customModeActive
                    ? "当前 TTS 模型不支持语音合成，请到 AI 设置里单独填写可用的 TTS 模型"
                    : "系统 TTS 模型不支持语音合成，请联系管理员检查模型路由";
        }
        if (lower.contains("voice") && lower.contains("invalid")) {
            return customModeActive
                    ? "当前 TTS 音色不可用，请到 AI 设置里更换音色"
                    : "系统 TTS 音色不可用，请联系管理员检查模型路由";
        }
        if (lower.contains("voice or reference audio should be set")) {
            return "当前 TTS 模型需要音色或参考音频，请先在 AI 设置里选择可用音色";
        }
        if (lower.contains("model") && (lower.contains("not found") || lower.contains("does not exist"))) {
            return customModeActive
                    ? "当前 TTS 模型不可用，请到 AI 设置里检查模型名称"
                    : "系统 TTS 模型不可用，请联系管理员检查模型路由";
        }
        if (lower.contains("api key") || lower.contains("unauthorized") || lower.contains("authentication")) {
            return customModeActive
                    ? "当前语音配置的 API Key 不可用，请检查 BYOK 设置"
                    : "系统 TTS 供应商鉴权失败，请联系管理员检查模型路由";
        }
        return text;
    }

    private String resolveVoiceName(String modelName, String configuredVoice) {
        String voiceName = safe(configuredVoice);
        if (supportsOpenAiVoicePreset(modelName)) {
            return StringUtils.hasText(voiceName) ? voiceName : DEFAULT_VOICE_NAME;
        }
        if (supportsSiliconFlowVoicePreset(modelName)) {
            return normalizeSiliconFlowVoiceName(modelName, voiceName);
        }
        if (OPENAI_VOICE_NAMES.contains(voiceName.toLowerCase(Locale.ROOT))) {
            return "";
        }
        return voiceName;
    }

    private static boolean supportsOpenAiVoicePreset(String modelName) {
        String text = safe(modelName).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("gpt-4o-mini-tts")
                || text.contains("tts-1")
                || text.contains("/tts")
                || text.matches(".*openai/.+tts.*");
    }

    private static boolean supportsSiliconFlowVoicePreset(String modelName) {
        String text = safe(modelName).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("cosyvoice")
                || text.contains("fish-speech")
                || text.contains("gpt-sovits");
    }

    private String normalizeSiliconFlowVoiceName(String modelName, String configuredVoice) {
        String voiceName = safe(configuredVoice);
        if (!StringUtils.hasText(voiceName) || OPENAI_VOICE_NAMES.contains(voiceName.toLowerCase(Locale.ROOT))) {
            return modelName + ":" + DEFAULT_SILICONFLOW_VOICE_NAME;
        }
        if (voiceName.startsWith("speech:")) {
            return voiceName;
        }
        if (voiceName.contains(":")) {
            return voiceName;
        }
        String lowerVoice = voiceName.toLowerCase(Locale.ROOT);
        if (SILICONFLOW_VOICE_NAMES.contains(lowerVoice)) {
            return modelName + ":" + lowerVoice;
        }
        return voiceName;
    }

    private static String normalizeSpeechText(String text) {
        String value = safe(text).replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() > MAX_INPUT_CHARS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "单段语音内容过长，请分段后重试");
        }
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max, int fallback) {
        int candidate = value <= 0 ? fallback : value;
        return Math.max(min, Math.min(max, candidate));
    }
}
