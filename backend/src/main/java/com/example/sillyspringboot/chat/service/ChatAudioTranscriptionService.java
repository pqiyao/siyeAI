package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.ops.service.OperationalStatsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
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
import java.time.Duration;
import java.util.Locale;

@Service
public class ChatAudioTranscriptionService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long MAX_AUDIO_BYTES = 15L * 1024L * 1024L;

    public record AudioTranscriptionResult(String text, String modelName, String audioUrl) {
    }

    private final H5UserAiProviderService userAiProviderService;
    private final H5UploadService uploadService;
    private final OperationalStatsService operationalStatsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ChatAudioTranscriptionService(
            H5UserAiProviderService userAiProviderService,
            H5UploadService uploadService,
            OperationalStatsService operationalStatsService,
            ObjectMapper objectMapper
    ) {
        this.userAiProviderService = userAiProviderService;
        this.uploadService = uploadService;
        this.operationalStatsService = operationalStatsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
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

        UserModelOverride override = userAiProviderService.resolveActiveOverrideForUser(userId);
        if (override == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先配置可用的自定义 API");
        }
        String modelName = safe(override.sttModelOrFallback());
        if (!StringUtils.hasText(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先在 AI Provider 中配置语音识别模型");
        }
        String apiKey = safe(override.sttApiKeyOrFallback());
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前未配置可用的 API Key");
        }
        String baseUrl = resolveProviderBaseUrl(override);

        long startNanos = System.nanoTime();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音读取失败");
        }

        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", modelName);
        form.add("response_format", "json");
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        MediaType partContentType = resolveAudioMediaType(contentType, filename);
        if (partContentType != null) {
            partHeaders.setContentType(partContentType);
        }
        partHeaders.setContentDisposition(
                ContentDisposition.formData()
                        .name("file")
                        .filename(filename)
                        .build()
        );
        form.add("file", new HttpEntity<>(resource, partHeaders));

        try {
            RestClient client = buildRestClient(baseUrl, apiKey);
            String raw = client.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            String text = extractTranscriptText(raw);
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音识别结果为空，请重试");
            }
            String audioUrl = uploadService.saveAudioAndGetUrl(file);
            operationalStatsService.recordSttSuccess(
                    userId,
                    safe(override.sttProviderSourceOrFallback()),
                    modelName,
                    elapsedMs(startNanos),
                    bytes.length,
                    text.length()
            );
            return new AudioTranscriptionResult(text, modelName, audioUrl);
        } catch (BusinessException ex) {
            operationalStatsService.recordSttFailure(
                    userId,
                    safe(override.sttProviderSourceOrFallback()),
                    modelName,
                    elapsedMs(startNanos),
                    safeErrorCode(ex)
            );
            throw ex;
        } catch (RestClientResponseException ex) {
            operationalStatsService.recordSttFailure(
                    userId,
                    safe(override.sttProviderSourceOrFallback()),
                    modelName,
                    elapsedMs(startNanos),
                    "HTTP_" + ex.getStatusCode().value()
            );
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, safeProviderErrorMessage(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            operationalStatsService.recordSttFailure(
                    userId,
                    safe(override.sttProviderSourceOrFallback()),
                    modelName,
                    elapsedMs(startNanos),
                    "REST_CLIENT"
            );
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音识别服务暂时不可用");
        } catch (Exception ex) {
            operationalStatsService.recordSttFailure(
                    userId,
                    safe(override.sttProviderSourceOrFallback()),
                    modelName,
                    elapsedMs(startNanos),
                    "UNEXPECTED"
            );
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "语音识别服务暂时不可用");
        }
    }

    private RestClient buildRestClient(String baseUrl, String apiKey) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .baseUrl(baseUrl)
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

    private String safeProviderErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "" : responseBody);
            String message = safe(root.path("error").path("message").asText(""));
            if (!message.isBlank()) {
                return normalizeProviderTranscriptionMessage(message);
            }
            message = safe(root.path("message").asText(""));
            if (!message.isBlank()) {
                return normalizeProviderTranscriptionMessage(message);
            }
        } catch (Exception ignored) {
        }
        return "语音识别失败，请检查 STT 模型配置";
    }

    private String normalizeProviderTranscriptionMessage(String message) {
        String text = safe(message);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("failed to decode audio") || lower.contains("end of stream")) {
            return "当前这段录音没有被模型正确解码。H5 浏览器录音我已尽量转成更稳的格式，你可以重录 1 到 2 秒以上，或优先在 APP 里使用语音输入。";
        }
        if (lower.contains("unsupported") || lower.contains("not support")) {
            return "当前 STT 模型不支持这类录音格式，请到 AI 设置里更换语音识别模型。";
        }
        return text;
    }

    private String resolveProviderBaseUrl(UserModelOverride override) {
        String source = safe(override.sttProviderSourceOrFallback()).toLowerCase(Locale.ROOT);
        String customUrl = trimTrailingSlash(safe(override.sttCustomUrlOrFallback()));
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

    private static int elapsedMs(long startNanos) {
        long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, elapsed));
    }

    private static String safeErrorCode(BusinessException ex) {
        return ex == null || ex.getErrorCode() == null ? "" : ex.getErrorCode().name();
    }
}
