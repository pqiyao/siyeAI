package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.service.OperationalStatsService;
import com.example.sillyspringboot.ops.service.TtsVoiceProvisionService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ChatAudioSpeechService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_INPUT_CHARS = 1200;
    private static final String DEFAULT_VOICE_NAME = "alloy";
    private static final String DEFAULT_SILICONFLOW_VOICE_NAME = "alex";
    private static final String DEFAULT_RESPONSE_FORMAT = "mp3";
    private static final Set<String> OPENAI_VOICE_NAMES = Set.of("alloy", "nova", "shimmer", "echo", "fable", "onyx");
    private static final Set<String> SILICONFLOW_VOICE_NAMES = Set.of("alex", "benjamin", "charles", "david", "anna", "bella", "claire", "diana");

    public record AudioSpeechResult(byte[] audioBytes, String mimeType, String modelName, String voiceName) {
    }

    private final H5UserAiProviderService userAiProviderService;
    private final TtsVoiceProvisionService ttsVoiceProvisionService;
    private final OperationalStatsService operationalStatsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ChatAudioSpeechService(
            H5UserAiProviderService userAiProviderService,
            TtsVoiceProvisionService ttsVoiceProvisionService,
            OperationalStatsService operationalStatsService,
            ObjectMapper objectMapper
    ) {
        this.userAiProviderService = userAiProviderService;
        this.ttsVoiceProvisionService = ttsVoiceProvisionService;
        this.operationalStatsService = operationalStatsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public AudioSpeechResult synthesizeForUser(long userId, String text) {
        return synthesizeForUser(userId, text, "", "", "");
    }

    public AudioSpeechResult synthesizeForUser(
            long userId,
            String text,
            String ttsModelNameOverride,
            String ttsVoiceNameOverride,
            String ttsVoiceTemplateCodeOverride
    ) {
        String safeText = normalizeSpeechText(text);
        if (!StringUtils.hasText(safeText)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音内容不能为空");
        }

        H5UserAiProviderService.UserTtsSettings settings = userAiProviderService.resolveActiveTtsSettingsForUser(userId);
        if (settings == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先配置可用的自定义 API");
        }

        String modelName = safe(ttsModelNameOverride);
        if (!StringUtils.hasText(modelName)) {
            modelName = safe(settings.modelName());
        }
        String configuredVoice = safe(ttsVoiceNameOverride);
        if (!StringUtils.hasText(configuredVoice)) {
            configuredVoice = safe(settings.voiceName());
        }
        String configuredTemplateCode = safe(ttsVoiceTemplateCodeOverride);
        if (!StringUtils.hasText(configuredTemplateCode)) {
            configuredTemplateCode = safe(settings.voiceTemplateCode());
        }
        String apiKey = safe(settings.apiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前未配置可用的 API Key");
        }
        String providerSource = safe(settings.providerSource());
        String baseUrl = safe(settings.baseUrl());

        if (StringUtils.hasText(configuredTemplateCode)) {
            TtsVoiceProvisionService.ResolvedVoice resolvedVoice = ttsVoiceProvisionService.resolveVoiceForUser(
                    userId,
                    configuredTemplateCode,
                    new TtsVoiceProvisionService.TtsRuntimeContext(true, providerSource, baseUrl, apiKey, modelName)
            );
            configuredVoice = safe(resolvedVoice.voiceUri());
            modelName = safe(resolvedVoice.modelName());
        }

        if (!StringUtils.hasText(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先在 AI Provider 里配置语音合成模型");
        }
        String voiceName = resolveVoiceName(modelName, configuredVoice);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("input", safeText);
        if (StringUtils.hasText(voiceName)) {
            payload.put("voice", voiceName);
        }
        payload.put("response_format", DEFAULT_RESPONSE_FORMAT);

        long startNanos = System.nanoTime();
        try {
            RestClient client = buildRestClient(baseUrl, apiKey);
            ResponseEntity<byte[]> entity = client.post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(
                            MediaType.parseMediaType("audio/mpeg"),
                            MediaType.parseMediaType("audio/mp3"),
                            MediaType.APPLICATION_OCTET_STREAM
                    )
                    .body(payload)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] body = entity.getBody();
            if (body == null || body.length == 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音合成结果为空");
            }
            MediaType contentType = entity.getHeaders().getContentType();
            String mimeType = contentType != null ? contentType.toString() : "audio/mpeg";
            operationalStatsService.recordTtsSuccess(
                    userId,
                    providerSource,
                    modelName,
                    voiceName,
                    configuredTemplateCode,
                    safeText,
                    elapsedMs(startNanos),
                    body.length,
                    false
            );
            return new AudioSpeechResult(body, mimeType, modelName, voiceName);
        } catch (BusinessException ex) {
            operationalStatsService.recordTtsFailure(
                    userId,
                    providerSource,
                    modelName,
                    voiceName,
                    configuredTemplateCode,
                    safeText,
                    elapsedMs(startNanos),
                    safeErrorCode(ex)
            );
            throw ex;
        } catch (RestClientResponseException ex) {
            operationalStatsService.recordTtsFailure(
                    userId,
                    providerSource,
                    modelName,
                    voiceName,
                    configuredTemplateCode,
                    safeText,
                    elapsedMs(startNanos),
                    "HTTP_" + ex.getStatusCode().value()
            );
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, safeProviderErrorMessage(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            operationalStatsService.recordTtsFailure(
                    userId,
                    providerSource,
                    modelName,
                    voiceName,
                    configuredTemplateCode,
                    safeText,
                    elapsedMs(startNanos),
                    "REST_CLIENT"
            );
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音合成服务暂时不可用");
        } catch (Exception ex) {
            operationalStatsService.recordTtsFailure(
                    userId,
                    providerSource,
                    modelName,
                    voiceName,
                    configuredTemplateCode,
                    safeText,
                    elapsedMs(startNanos),
                    "UNEXPECTED"
            );
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音合成服务暂时不可用");
        }
    }

    private RestClient buildRestClient(String baseUrl, String apiKey) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.httpClient);
        factory.setReadTimeout(Duration.ofSeconds(90));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    private String safeProviderErrorMessage(String responseBody) {
        String message = "";
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "" : responseBody);
            message = safe(root.path("error").path("message").asText(""));
            if (!message.isBlank()) {
                return normalizeProviderSpeechMessage(message);
            }
            message = safe(root.path("message").asText(""));
            if (!message.isBlank()) {
                return normalizeProviderSpeechMessage(message);
            }
        } catch (Exception ignored) {
        }
        return "语音合成失败，请检查 TTS 模型和音色配置";
    }

    private String normalizeProviderSpeechMessage(String message) {
        String text = safe(message);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("illegal operation") || lower.contains("not support") || lower.contains("unsupported")) {
            return "当前 TTS 模型不支持语音合成，请到 AI 设置里单独填写可用的 TTS 模型";
        }
        if (lower.contains("voice") && lower.contains("invalid")) {
            return "当前 TTS 音色不可用，请到 AI 设置里更换音色";
        }
        if (lower.contains("voice or reference audio should be set")) {
            return "当前 TTS 模型需要音色或参考音频，请先在 AI 设置里选择可用音色";
        }
        if (lower.contains("model") && (lower.contains("not found") || lower.contains("does not exist"))) {
            return "当前 TTS 模型不可用，请到 AI 设置里检查模型名称";
        }
        if (lower.contains("api key") || lower.contains("unauthorized") || lower.contains("authentication")) {
            return "当前语音配置的 API Key 不可用，请检查 BYOK 设置";
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

    private boolean supportsOpenAiVoicePreset(String modelName) {
        String text = safe(modelName).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("gpt-4o-mini-tts")
                || text.contains("tts-1")
                || text.contains("/tts")
                || text.matches(".*openai/.+tts.*");
    }

    private boolean supportsSiliconFlowVoicePreset(String modelName) {
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
            return value.substring(0, MAX_INPUT_CHARS);
        }
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int elapsedMs(long startNanos) {
        long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, elapsed));
    }

    private static String safeErrorCode(BusinessException ex) {
        return ex == null || ex.getErrorCode() == null ? "" : ex.getErrorCode().name();
    }
}
