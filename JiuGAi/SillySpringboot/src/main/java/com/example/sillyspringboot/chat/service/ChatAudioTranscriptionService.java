package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiProviderCallException;
import com.example.sillyspringboot.ai.service.AiProviderFailurePolicy;
import com.example.sillyspringboot.ai.service.AiMediaAttemptTelemetry;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.net.BoundedHttpBodyHandlers;
import com.example.sillyspringboot.shared.net.OutboundUrlGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ChatAudioTranscriptionService {

    private static final long MAX_AUDIO_BYTES = 15L * 1024L * 1024L;
    private static final int MAX_TRANSCRIPTION_RESPONSE_BYTES = 2 * 1024 * 1024;

    public record AudioTranscriptionResult(String text, String modelName, String audioUrl) {
    }

    private record TranscriptionRuntime(
            String providerKey,
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            Long deploymentId,
            int connectTimeoutSeconds,
            int requestTimeoutSeconds
    ) {}

    private final H5UserAiProviderService userAiProviderService;
    private final AiRoutingService routingService;
    private final ObjectMapper objectMapper;
    private final AiMediaAttemptTelemetry attemptTelemetry;

    public ChatAudioTranscriptionService(
            H5UserAiProviderService userAiProviderService,
            ObjectMapper objectMapper,
            AiRoutingService routingService
    ) {
        this(userAiProviderService, objectMapper, routingService, null);
    }

    @Autowired
    public ChatAudioTranscriptionService(
            H5UserAiProviderService userAiProviderService,
            ObjectMapper objectMapper,
            AiRoutingService routingService,
            AiMediaAttemptTelemetry attemptTelemetry
    ) {
        this.userAiProviderService = userAiProviderService;
        this.routingService = routingService;
        this.objectMapper = objectMapper;
        this.attemptTelemetry = attemptTelemetry;
    }

    public AudioTranscriptionResult transcribeForUser(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音文件不能为空");
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音文件过大，请压缩后再试");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        if (!isAudioContentType(contentType) && !hasAudioExtension(filename)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅支持常见音频格式");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音读取失败");
        }

        BusinessException last = null;
        int attemptNo = 0;
        String telemetryRequestId = attemptTelemetry == null
                ? "" : attemptTelemetry.newRequestId(AiCapability.STT);
        for (TranscriptionRuntime runtime : resolveRuntimes(userId)) {
            attemptNo++;
            AiMediaAttemptTelemetry.Attempt attempt = startTelemetry(telemetryRequestId, runtime, attemptNo);
            try {
                AudioTranscriptionResult result = transcribeAttempt(bytes, filename, contentType, runtime);
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
            }
        }
        throw last == null
                ? new BusinessException(ErrorCode.FORBIDDEN, "语音识别服务尚未配置")
                : last;
    }

    private List<TranscriptionRuntime> resolveRuntimes(long userId) {
        UserModelOverride override = userAiProviderService.resolveActiveOverrideForUser(userId);
        if (override != null) {
            String modelName = safe(override.sttModelOrFallback());
            String apiKey = safe(override.apiKey());
            if (!StringUtils.hasText(modelName) || !StringUtils.hasText(apiKey)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户自定义 STT 配置不完整");
            }
            return List.of(new TranscriptionRuntime(
                    "user_byok", safe(override.providerSource()), resolveProviderBaseUrl(override),
                    apiKey, modelName, null, 10, 60));
        }
        if (userAiProviderService.isCustomModeSelectedForUser(userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "用户自定义 STT 配置不可用，请检查模型、地址和 API Key");
        }
        if (!routingService.isCapabilityEnabled(AiCapability.STT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先配置可用的自定义 API");
        }
        List<TranscriptionRuntime> runtimes = new ArrayList<>();
        for (AiRoutingService.ResolvedProvider provider : routingService.resolve(AiCapability.STT)) {
            runtimes.add(new TranscriptionRuntime(
                    provider.providerKey(), provider.vendor(), provider.baseUrl(), provider.apiKey(),
                    provider.modelName(), provider.deploymentId(),
                    provider.connectTimeoutSeconds(), provider.requestTimeoutSeconds()));
        }
        if (runtimes.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统语音识别供应商尚未配置");
        }
        return List.copyOf(runtimes);
    }

    private AudioTranscriptionResult transcribeAttempt(
            byte[] bytes,
            String filename,
            String contentType,
            TranscriptionRuntime runtime
    ) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", runtime.modelName());
        form.add("response_format", "json");
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        MediaType partContentType = resolveAudioMediaType(contentType, filename);
        if (partContentType != null) partHeaders.setContentType(partContentType);
        partHeaders.setContentDisposition(ContentDisposition.formData().name("file").filename(filename).build());
        form.add("file", new HttpEntity<>(resource, partHeaders));
        try {
            String raw = buildRestClient(
                    runtime.baseUrl(), runtime.apiKey(),
                    runtime.connectTimeoutSeconds(), runtime.requestTimeoutSeconds()).post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .exchange((request, response) -> {
                        byte[] body = BoundedHttpBodyHandlers.readBytes(
                                response.getBody(), MAX_TRANSCRIPTION_RESPONSE_BYTES);
                        String responseText = new String(body, StandardCharsets.UTF_8);
                        if (!response.getStatusCode().is2xxSuccessful()) {
                             throw AiProviderCallException.http(
                                     response.getStatusCode().value(),
                                     safeProviderErrorMessage(
                                             response.getStatusCode().value(),
                                             responseText,
                                             runtime.deploymentId() == null),
                                     null
                             );
                        }
                        return responseText;
                    });
            String text = extractTranscriptText(raw);
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音识别结果为空，请重试");
            }
            return new AudioTranscriptionResult(text, runtime.modelName(), null);
        } catch (BusinessException ex) {
            throw ex;
        } catch (BoundedHttpBodyHandlers.BodyTooLargeException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "语音识别响应过大，请稍后重试");
        } catch (RestClientResponseException ex) {
             throw AiProviderCallException.http(
                     ex.getStatusCode().value(),
                     safeProviderErrorMessage(
                             ex.getStatusCode().value(),
                             ex.getResponseBodyAsString(),
                             runtime.deploymentId() == null),
                     ex
             );
        } catch (RestClientException ex) {
            throw AiProviderCallException.transientFailure("语音识别服务暂时不可用", ex);
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
            TranscriptionRuntime runtime,
            int attemptNo
    ) {
        if (attemptTelemetry == null) return null;
        return attemptTelemetry.start(
                requestId, AiCapability.STT, attemptNo, runtime.providerKey(), runtime.providerSource(),
                runtime.modelName(), runtime.deploymentId() == null);
    }

    private void successTelemetry(AiMediaAttemptTelemetry.Attempt attempt) {
        if (attemptTelemetry != null) attemptTelemetry.success(attempt);
    }

    private void failureTelemetry(AiMediaAttemptTelemetry.Attempt attempt, Throwable error) {
        if (attemptTelemetry != null) attemptTelemetry.failure(attempt, error);
    }

    private RestClient buildRestClient(
            String baseUrl,
            String apiKey,
            int connectTimeoutSeconds,
            int requestTimeoutSeconds
    ) {
        String safeBaseUrl = OutboundUrlGuard.requirePublicHttpUrl(
                baseUrl, "STT 服务地址不安全，请使用可公开访问的 HTTP(S) 地址").toString();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(clamp(connectTimeoutSeconds, 1, 60, 10)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(clamp(requestTimeoutSeconds, 5, 600, 60)));
        return RestClient.builder()
                .baseUrl(safeBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String extractTranscriptText(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw == null ? "" : raw);
            String text = safe(root.path("text").asText(""));
            if (StringUtils.hasText(text)) {
                return text;
            }
            JsonNode segments = root.path("segments");
            if (segments.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : segments) {
                    String segmentText = safe(item.path("text").asText(""));
                    if (!segmentText.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(segmentText);
                    }
                }
                return sb.toString().trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    String safeProviderErrorMessage(int httpStatus, String responseBody, boolean customModeActive) {
        if (httpStatus == 401 || httpStatus == 403) {
            return customModeActive
                    ? "当前语音识别配置的 API Key 无效或无权限，请检查 BYOK 设置"
                    : "系统 STT 供应商鉴权失败，请联系管理员检查模型路由";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "" : responseBody);
            String message = safe(root.path("error").path("message").asText(""));
            if (!message.isBlank()) {
                return normalizeProviderTranscriptionMessage(message, customModeActive);
            }
            message = safe(root.path("message").asText(""));
            if (!message.isBlank()) {
                return normalizeProviderTranscriptionMessage(message, customModeActive);
            }
        } catch (Exception ignored) {
        }
        if (httpStatus == 404) {
            return customModeActive
                    ? "当前 STT 模型或接口不存在，请检查 BYOK 模型名称和 API 地址"
                    : "系统 STT 模型或接口不存在，请联系管理员检查模型路由";
        }
        if (httpStatus == 429) {
            return "语音识别服务请求过于频繁，请稍后重试";
        }
        if (httpStatus >= 500) {
            return "语音识别上游服务暂时不可用，请稍后重试";
        }
        return customModeActive
                ? "语音识别失败，请检查 BYOK 的 STT 模型配置"
                : "系统语音识别调用失败，请联系管理员检查模型路由";
    }

    static String normalizeProviderTranscriptionMessage(String message, boolean customModeActive) {
        String text = safe(message);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("failed to decode audio") || lower.contains("end of stream")) {
            return "当前这段录音没有被模型正确解码。H5 浏览器录音我已尽量转成更稳的格式，你可以重录 1 到 2 秒以上，或优先在 APP 里使用语音输入。";
        }
        if (lower.contains("unsupported") || lower.contains("not support")) {
            return customModeActive
                    ? "当前 STT 模型不支持这类录音格式，请到 AI 设置里更换语音识别模型。"
                    : "系统 STT 模型不支持这类录音格式，请联系管理员检查模型路由。";
        }
        if (lower.contains("model") && (lower.contains("not found") || lower.contains("does not exist"))) {
            return customModeActive
                    ? "当前 STT 模型不可用，请到 AI 设置里检查模型名称"
                    : "系统 STT 模型不可用，请联系管理员检查模型路由";
        }
        if (lower.contains("api key") || lower.contains("unauthorized")
                || lower.contains("authentication") || lower.contains("invalid token")) {
            return customModeActive
                    ? "当前语音识别配置的 API Key 无效或无权限，请检查 BYOK 设置"
                    : "系统 STT 供应商鉴权失败，请联系管理员检查模型路由";
        }
        return text;
    }

    private String resolveProviderBaseUrl(UserModelOverride override) {
        String source = safe(override.providerSource()).toLowerCase(Locale.ROOT);
        String customUrl = trimTrailingSlash(safe(override.customUrl()));
        if ("custom".equals(source)) {
            if (!StringUtils.hasText(customUrl)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先配置自定义 API 地址");
            }
            return customUrl;
        }
        return switch (source) {
            case "siliconflow" -> "https://api.siliconflow.cn/v1";
            case "deepseek" -> "https://api.deepseek.com";
            case "openrouter" -> "https://openrouter.ai/api/v1";
            case "openai" -> "https://api.openai.com/v1";
            case "groq" -> "https://api.groq.com/openai/v1";
            case "mistralai" -> "https://api.mistral.ai/v1";
            case "moonshot" -> "https://api.moonshot.cn/v1";
            case "xai" -> "https://api.x.ai/v1";
            case "fireworks" -> "https://api.fireworks.ai/inference/v1";
            default -> {
                if (StringUtils.hasText(customUrl)) {
                    yield customUrl;
                }
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前平台暂不支持语音识别");
            }
        };
    }

    private static boolean isAudioContentType(String contentType) {
        return contentType.startsWith("audio/") || "application/octet-stream".equals(contentType);
    }

    private static boolean hasAudioExtension(String filename) {
        String value = safe(filename).toLowerCase(Locale.ROOT);
        return value.endsWith(".mp3")
                || value.endsWith(".m4a")
                || value.endsWith(".wav")
                || value.endsWith(".webm")
                || value.endsWith(".mp4")
                || value.endsWith(".mpeg")
                || value.endsWith(".mpga")
                || value.endsWith(".ogg")
                || value.endsWith(".aac");
    }

    private static MediaType resolveAudioMediaType(String contentType, String filename) {
        String normalized = normalizeContentType(contentType);
        if (StringUtils.hasText(normalized) && !"application/octet-stream".equals(normalized)) {
            try {
                return MediaType.parseMediaType(normalized);
            } catch (Exception ignored) {
            }
        }
        String value = safe(filename).toLowerCase(Locale.ROOT);
        if (value.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        if (value.endsWith(".mp3") || value.endsWith(".mpeg") || value.endsWith(".mpga")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (value.endsWith(".m4a") || value.endsWith(".mp4")) {
            return MediaType.parseMediaType("audio/mp4");
        }
        if (value.endsWith(".ogg")) {
            return MediaType.parseMediaType("audio/ogg");
        }
        if (value.endsWith(".webm")) {
            return MediaType.parseMediaType("audio/webm");
        }
        if (value.endsWith(".aac")) {
            return MediaType.parseMediaType("audio/aac");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static String safeFilename(String filename) {
        String value = safe(filename);
        return value.isBlank() ? "voice.mp3" : value;
    }

    private static String normalizeContentType(String contentType) {
        return safe(contentType).toLowerCase(Locale.ROOT);
    }

    private static String trimTrailingSlash(String value) {
        String next = safe(value);
        while (next.endsWith("/")) {
            next = next.substring(0, next.length() - 1);
        }
        return next;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max, int fallback) {
        int candidate = value <= 0 ? fallback : value;
        return Math.max(min, Math.min(max, candidate));
    }
}
