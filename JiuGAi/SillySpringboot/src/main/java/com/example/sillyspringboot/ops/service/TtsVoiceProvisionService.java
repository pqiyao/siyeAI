package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.example.sillyspringboot.ops.entity.AppTtsVoiceTemplate;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceInstance;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceInstanceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class TtsVoiceProvisionService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_REFERENCE_AUDIO_BYTES = 8 * 1024 * 1024;

    public record TtsRuntimeContext(
            boolean customModeActive,
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName
    ) {
        public boolean providerMatches(String expectedProviderSource) {
            return blank(providerSource).equalsIgnoreCase(blank(expectedProviderSource));
        }

        public boolean hasApiKey() {
            return StringUtils.hasText(blank(apiKey));
        }

        public String effectiveModelName(String fallbackModelName) {
            String model = blank(modelName);
            return StringUtils.hasText(model) ? model : blank(fallbackModelName);
        }
    }

    public record ResolvedVoice(
            String voiceUri,
            String modelName,
            String templateCode,
            String templateDisplayName
    ) {
    }

    private final TtsVoiceTemplateService templateService;
    private final AppUserTtsVoiceInstanceMapper instanceMapper;
    private final H5UploadService uploadService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TtsVoiceProvisionService(
            TtsVoiceTemplateService templateService,
            AppUserTtsVoiceInstanceMapper instanceMapper,
            H5UploadService uploadService,
            ObjectMapper objectMapper
    ) {
        this.templateService = templateService;
        this.instanceMapper = instanceMapper;
        this.uploadService = uploadService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public ResolvedVoice resolveVoiceForUser(long userId, String templateCode, TtsRuntimeContext runtimeContext) {
        AppTtsVoiceTemplate template = templateService.findEnabledTemplate(templateCode);
        if (template == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前选择的音色模板已失效");
        }
        if (runtimeContext == null || !runtimeContext.customModeActive()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先开启自定义 API，再使用模板音色");
        }
        if (!runtimeContext.providerMatches(blank(template.getProviderSource()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前模板仅支持硅基流动 TTS");
        }
        if (!runtimeContext.hasApiKey()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写当前 TTS 的 API Key");
        }
        String effectiveModelName = runtimeContext.effectiveModelName(template.getTtsModelName());
        if (!StringUtils.hasText(effectiveModelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写 TTS 模型，或在模板里配置推荐模型");
        }
        String fingerprint = buildConfigFingerprint(template, runtimeContext, effectiveModelName);
        AppUserTtsVoiceInstance instance = instanceMapper.findByUserIdAndTemplateCode(userId, blank(template.getTemplateCode()));
        if (instance != null
                && fingerprint.equals(blank(instance.getConfigFingerprint()))
                && "ready".equalsIgnoreCase(blank(instance.getStatus()))
                && StringUtils.hasText(instance.getVoiceUri())) {
            return new ResolvedVoice(
                    blank(instance.getVoiceUri()),
                    effectiveModelName,
                    blank(template.getTemplateCode()),
                    blank(template.getDisplayName())
            );
        }

        if (instance == null) {
            instance = new AppUserTtsVoiceInstance();
            instance.setUserId(userId);
            instance.setTemplateCode(blank(template.getTemplateCode()));
        }
        instance.setProviderSource(blank(runtimeContext.providerSource()));
        instance.setBaseUrl(blank(runtimeContext.baseUrl()));
        instance.setModelName(effectiveModelName);
        instance.setConfigFingerprint(fingerprint);
        instance.setStatus("pending");
        instance.setLastError("");

        try {
            ReferenceAudio referenceAudio = loadReferenceAudio(template);
            String voiceUri = uploadDynamicVoice(template, runtimeContext, effectiveModelName, referenceAudio);
            if (!StringUtils.hasText(voiceUri)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "平台没有返回可用的音色标识");
            }
            instance.setVoiceUri(voiceUri);
            instance.setStatus("ready");
            instance.setLastError("");
            persistInstance(instance);
            return new ResolvedVoice(
                    voiceUri,
                    effectiveModelName,
                    blank(template.getTemplateCode()),
                    blank(template.getDisplayName())
            );
        } catch (BusinessException ex) {
            instance.setVoiceUri("");
            instance.setStatus("failed");
            instance.setLastError(trim(ex.getMessage(), 255));
            persistInstance(instance);
            throw ex;
        }
    }

    public static String buildConfigFingerprint(
            AppTtsVoiceTemplate template,
            TtsRuntimeContext runtimeContext,
            String effectiveModelName
    ) {
        String joined = String.join("|",
                blank(runtimeContext == null ? null : runtimeContext.providerSource()),
                blank(runtimeContext == null ? null : runtimeContext.baseUrl()),
                blank(runtimeContext == null ? null : runtimeContext.apiKey()),
                blank(effectiveModelName),
                blank(template == null ? null : template.getTemplateCode()),
                blank(template == null ? null : template.getReferenceAudioUrl()),
                blank(template == null ? null : template.getSampleScript()),
                String.valueOf(template != null && template.getUpdatedAt() != null ? template.getUpdatedAt() : "")
        );
        return sha256Hex(joined);
    }

    private void persistInstance(AppUserTtsVoiceInstance instance) {
        if (instance.getId() == null) {
            instanceMapper.insert(instance);
            return;
        }
        instanceMapper.updateById(instance);
    }

    private String uploadDynamicVoice(
            AppTtsVoiceTemplate template,
            TtsRuntimeContext runtimeContext,
            String effectiveModelName,
            ReferenceAudio referenceAudio
    ) {
        RestClient client = buildRestClient(blank(runtimeContext.baseUrl()), blank(runtimeContext.apiKey()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", effectiveModelName);
        payload.put("customName", buildCustomVoiceName(template));
        payload.put("text", blank(template.getSampleScript()));
        payload.put("audio", "data:" + referenceAudio.mimeType() + ";base64," + Base64.getEncoder().encodeToString(referenceAudio.bytes()));
        try {
            String raw = client.post()
                    .uri("/uploads/audio/voice")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return extractVoiceUri(raw);
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, providerErrorMessage(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板音色生成服务暂时不可用");
        }
    }

    private RestClient buildRestClient(String baseUrl, String apiKey) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.httpClient);
        factory.setReadTimeout(Duration.ofSeconds(90));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String extractVoiceUri(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw == null ? "" : raw);
            String uri = blank(root.path("uri").asText(""));
            if (StringUtils.hasText(uri)) {
                return uri;
            }
            uri = blank(root.path("data").path("uri").asText(""));
            if (StringUtils.hasText(uri)) {
                return uri;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String providerErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "" : responseBody);
            String message = blank(root.path("error").path("message").asText(""));
            if (StringUtils.hasText(message)) {
                return normalizeProviderMessage(message);
            }
            message = blank(root.path("message").asText(""));
            if (StringUtils.hasText(message)) {
                return normalizeProviderMessage(message);
            }
        } catch (Exception ignored) {
        }
        return "模板音色生成失败，请检查模型和参考音频";
    }

    private String normalizeProviderMessage(String rawMessage) {
        String message = blank(rawMessage);
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("voice cloning") || lower.contains("voice") && lower.contains("unsupported")) {
            return "当前 TTS 模型不支持动态音色，请换成硅基流动支持音色克隆的模型";
        }
        if (lower.contains("audio")) {
            return "参考音频不符合要求，请换成 5 到 20 秒、清晰干净的人声音频";
        }
        if (lower.contains("api key") || lower.contains("unauthorized") || lower.contains("authentication")) {
            return "当前 TTS 的 API Key 不可用，请先检查硅基流动配置";
        }
        return message;
    }

    private String buildCustomVoiceName(AppTtsVoiceTemplate template) {
        String base = blank(template.getTemplateCode()).replaceAll("[^a-zA-Z0-9_-]+", "-");
        if (!StringUtils.hasText(base)) {
            base = "voice-template";
        }
        return trim("jg-" + base, 64);
    }

    private ReferenceAudio loadReferenceAudio(AppTtsVoiceTemplate template) {
        String url = blank(template.getReferenceAudioUrl());
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模板还没有配置参考音频");
        }
        if (url.startsWith("/uploads/h5/")) {
            byte[] bytes = uploadService.readUploadedFileBytes(url);
            validateReferenceAudioSize(bytes);
            return new ReferenceAudio(bytes, uploadService.detectUploadedFileContentType(url));
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频地址不可用");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "参考音频下载失败");
            }
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            validateReferenceAudioSize(bytes);
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            return new ReferenceAudio(bytes, StringUtils.hasText(contentType) ? contentType : "audio/mpeg");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "参考音频下载失败");
        }
    }

    private void validateReferenceAudioSize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频不能为空");
        }
        if (bytes.length > MAX_REFERENCE_AUDIO_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频太大，建议控制在 8MB 以内");
        }
    }

    private record ReferenceAudio(byte[] bytes, String mimeType) {
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(blank(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                sb.append(Character.forDigit((item >> 4) & 0xF, 16));
                sb.append(Character.forDigit(item & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
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
}
