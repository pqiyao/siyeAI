package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.net.MediaPayloadValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

@Service
public class AiProviderProbeService {

    private static final int MAX_ERROR_BODY = 2_000;
    private static final int MAX_MODELS = 2_000;
    private static final int MAX_RESPONSE_BODY = 16 * 1024 * 1024;
    private static final byte[] STT_PROBE_WAV = loadSttProbeWav();
    private static final String VISION_PROBE_IMAGE =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z4D8AAAAASUVORK5CYII=";

    record ProbeResponse(int statusCode, HttpHeaders headers, byte[] body) {}

    private final AiRoutingService routingService;
    private final ObjectMapper objectMapper;

    public AiProviderProbeService(AiRoutingService routingService, ObjectMapper objectMapper) {
        this.routingService = routingService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> discoverModels(Map<String, Object> body) {
        AiRoutingService.DraftCredential draft = routingService.resolveDraft(body);
        HttpRequest request = requestBuilder(draft, "/models").GET().build();
        ProbeResponse response = send(draft, request, "获取模型");
        JsonNode root = parseJson(response.body(), "模型列表响应不是有效 JSON");
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            data = root.path("models");
        }
        if (!data.isArray()) {
            throw validation("上游响应中没有 data/models 模型数组");
        }

        List<Map<String, Object>> models = new ArrayList<>();
        for (JsonNode item : data) {
            if (models.size() >= MAX_MODELS) {
                break;
            }
            String id = item.isTextual() ? item.asText("") : item.path("id").asText("");
            if (!StringUtils.hasText(id)) {
                id = item.path("name").asText("");
            }
            id = id.trim();
            if (id.isBlank()) {
                continue;
            }
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("id", id);
            model.put("label", firstNonBlank(item.path("display_name").asText(""), item.path("name").asText(""), id));
            model.put("ownedBy", item.path("owned_by").asText(""));
            model.put("capabilityMatch", capabilityMatches(id, item, draft.capability()));
            models.add(model);
        }
        models.sort(Comparator
                .comparing((Map<String, Object> item) -> !Boolean.TRUE.equals(item.get("capabilityMatch")))
                .thenComparing(item -> String.valueOf(item.get("id")), String.CASE_INSENSITIVE_ORDER));

        long matched = models.stream().filter(item -> Boolean.TRUE.equals(item.get("capabilityMatch"))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", draft.baseUrl());
        result.put("capability", draft.capability().name());
        result.put("models", List.copyOf(models));
        result.put("matchedCount", matched);
        result.put("totalCount", models.size());
        result.put("warning", models.isEmpty() ? "上游返回了空模型列表" : "");
        return result;
    }

    public Map<String, Object> probe(Map<String, Object> body) {
        AiRoutingService.DraftCredential draft = routingService.resolveDraft(body);
        if (!StringUtils.hasText(draft.modelName())) {
            throw validation("请先选择模型");
        }
        try {
            long startedAt = System.nanoTime();
            HttpRequest request = switch (draft.capability()) {
                case CHAT -> chatRequest(draft);
                case VISION -> visionRequest(draft);
                case IMAGE -> imageRequest(draft);
                case TTS -> ttsRequest(draft);
                case STT -> sttRequest(draft);
            };
            ProbeResponse response = send(draft, request, capabilityLabel(draft.capability()) + "测试");
            validateCapabilityResponse(draft, response);
            if (draft.deploymentId() != null) {
                routingService.recordSuccess(draft.deploymentId());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("capability", draft.capability().name());
            result.put("modelName", draft.modelName());
            result.put("baseUrl", draft.baseUrl());
            result.put("httpStatus", response.statusCode());
            result.put("latencyMs", Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            result.put("message", capabilityLabel(draft.capability()) + "模型真实调用成功");
            return result;
        } catch (BusinessException ex) {
            if (draft.deploymentId() != null) {
                routingService.recordConfigurationError(draft.deploymentId(), ex.getMessage());
            }
            throw ex;
        }
    }

    private HttpRequest chatRequest(AiRoutingService.DraftCredential draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", draft.modelName());
        payload.put("messages", List.of(Map.of("role", "user", "content", "Reply with OK.")));
        payload.put("max_tokens", 2);
        payload.put("stream", false);
        return jsonPost(draft, "/chat/completions", payload);
    }

    private HttpRequest visionRequest(AiRoutingService.DraftCredential draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", draft.modelName());
        payload.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", "Describe the image briefly. Return plain text only."),
                        Map.of("type", "image_url", "image_url", Map.of("url", VISION_PROBE_IMAGE))
                )
        )));
        payload.put("max_tokens", 32);
        payload.put("stream", false);
        return jsonPost(draft, "/chat/completions", payload);
    }

    private HttpRequest imageRequest(AiRoutingService.DraftCredential draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", draft.modelName());
        if ("openrouter".equalsIgnoreCase(draft.vendor())) {
            payload.put("stream", false);
            payload.put("modalities", List.of("image", "text"));
            payload.put("image_config", Map.of("aspect_ratio", "1:1"));
            payload.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "A small black circle centered on a plain white background."
                    ))
            )));
            return jsonPost(draft, "/chat/completions", payload);
        }
        payload.put("prompt", "A small black circle centered on a plain white background.");
        payload.put("n", 1);
        return jsonPost(draft, "/images/generations", payload);
    }

    private HttpRequest ttsRequest(AiRoutingService.DraftCredential draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", draft.modelName());
        payload.put("voice", firstNonBlank(draft.voiceName(), defaultProbeVoice(draft.modelName())));
        payload.put("input", "test");
        payload.put("response_format", "mp3");
        return jsonPost(draft, "/audio/speech", payload);
    }

    private HttpRequest sttRequest(AiRoutingService.DraftCredential draft) {
        String boundary = "----AiRoutingProbe" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeUtf8(out, "--" + boundary + "\r\n");
            writeUtf8(out, "Content-Disposition: form-data; name=\"model\"\r\n\r\n");
            writeUtf8(out, draft.modelName() + "\r\n");
            writeUtf8(out, "--" + boundary + "\r\n");
            writeUtf8(out, "Content-Disposition: form-data; name=\"file\"; filename=\"probe.wav\"\r\n");
            writeUtf8(out, "Content-Type: audio/wav\r\n\r\n");
            out.write(sttProbeWav());
            writeUtf8(out, "\r\n--" + boundary + "--\r\n");
        } catch (IOException ex) {
            throw new IllegalStateException("cannot build STT probe", ex);
        }
        return requestBuilder(draft, "/audio/transcriptions")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
    }

    private HttpRequest jsonPost(AiRoutingService.DraftCredential draft, String path, Object payload) {
        try {
            return requestBuilder(draft, path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException ex) {
            throw new IllegalStateException("cannot encode provider probe", ex);
        }
    }

    private HttpRequest.Builder requestBuilder(AiRoutingService.DraftCredential draft, String path) {
        URI target = URI.create(trimTrailingSlash(draft.baseUrl()) + path);
        validatePublicTarget(target);
        return HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(draft.requestTimeoutSeconds()))
                .header("Authorization", "Bearer " + draft.apiKey())
                .header("Accept", "application/json");
    }

    private ProbeResponse send(AiRoutingService.DraftCredential draft, HttpRequest request, String operation) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(draft.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        try {
            validatePublicTarget(request.uri());
            HttpResponse<InputStream> raw = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream input = raw.body()) {
                body = readBounded(input, MAX_RESPONSE_BODY);
            }
            ProbeResponse response = new ProbeResponse(raw.statusCode(), raw.headers(), body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw validation(operation + "失败：HTTP " + response.statusCode() + "，" + providerError(response.body()));
            }
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw validation(operation + "已中断");
        } catch (IOException ex) {
            throw validation(operation + "失败：" + safeMessage(ex));
        }
    }

    void validateCapabilityResponse(AiRoutingService.DraftCredential draft, ProbeResponse response) {
        AiCapability capability = draft.capability();
        byte[] body = response.body() == null ? new byte[0] : response.body();
        if (body.length == 0) {
            throw validation(capabilityLabel(capability) + "接口返回空内容");
        }
        if (capability == AiCapability.TTS) {
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            MediaPayloadValidator.requireAudio(body, contentType);
            return;
        }
        JsonNode root = parseJson(body, capabilityLabel(capability) + "响应不是有效 JSON");
        boolean valid = switch (capability) {
            case CHAT -> hasUsableChatContent(root);
            case VISION -> hasUsableChatContent(root);
            case IMAGE -> hasUsableImage(root);
            case STT -> StringUtils.hasText(root.path("text").asText(""));
            case TTS -> true;
        };
        if (!valid) {
            throw validation(capabilityLabel(capability) + "接口成功但响应结构无有效内容");
        }
    }

    private static boolean hasUsableChatContent(JsonNode root) {
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) {
            return StringUtils.hasText(content.asText(""));
        }
        if (content.isArray()) {
            for (JsonNode item : content) {
                if (StringUtils.hasText(item.path("text").asText(""))) return true;
            }
        }
        return false;
    }

    private static boolean hasUsableImage(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (hasImageValue(item.path("url")) || hasImageValue(item.path("b64_json"))) return true;
            }
        }
        JsonNode message = root.path("choices").path(0).path("message");
        JsonNode images = message.path("images");
        if (images.isArray()) {
            for (JsonNode item : images) {
                if (hasImageValue(item.path("url"))
                        || hasImageValue(item.path("image_url").path("url"))) return true;
            }
        }
        JsonNode content = message.path("content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                if (hasImageValue(item.path("image_url").path("url"))
                        || hasImageValue(item.path("url"))) return true;
            }
        }
        return false;
    }

    private static boolean hasImageValue(JsonNode value) {
        return value != null && value.isTextual() && StringUtils.hasText(value.asText(""));
    }

    private static String defaultProbeVoice(String modelName) {
        String model = firstNonBlank(modelName).toLowerCase(Locale.ROOT);
        if (model.contains("cosyvoice") || model.contains("fish-speech") || model.contains("gpt-sovits")) {
            return firstNonBlank(modelName) + ":alex";
        }
        return "alloy";
    }

    static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        int limit = Math.max(1, maxBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        while (true) {
            int read = input.read(buffer, 0, Math.min(buffer.length, limit + 1 - total));
            if (read < 0) {
                return output.toByteArray();
            }
            if (read == 0) {
                int single = input.read();
                if (single < 0) {
                    return output.toByteArray();
                }
                total++;
                if (total > limit) {
                    throw validation("上游响应超过 16 MiB 安全限制");
                }
                output.write(single);
                continue;
            }
            total += read;
            if (total > limit) {
                throw validation("上游响应超过 16 MiB 安全限制");
            }
            output.write(buffer, 0, read);
        }
    }

    static void validatePublicTarget(URI target) {
        if (target == null
                || target.getScheme() == null
                || !("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()))
                || target.getHost() == null
                || target.getHost().isBlank()
                || target.getUserInfo() != null) {
            throw validation("探测地址必须是有效的 HTTP/HTTPS 公网地址");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(target.getHost());
            if (addresses.length == 0) {
                throw validation("探测地址无法解析");
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw validation("探测地址指向内网或保留网络，已拒绝访问");
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw validation("探测地址无法解析");
        }
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            int c = bytes[2] & 0xff;
            return a != 0
                    && a != 10
                    && a != 127
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && b == 0 && c == 0)
                    && !(a == 192 && b == 0 && c == 2)
                    && !(a == 192 && b == 88 && c == 99)
                    && !(a == 192 && b == 168)
                    && !(a == 198 && (b == 18 || b == 19))
                    && !(a == 198 && b == 51 && c == 100)
                    && !(a == 203 && b == 0 && c == 113)
                    && a < 224;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = bytes[0] & 0xff;
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean documentation = first == 0x20
                    && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d
                    && (bytes[3] & 0xff) == 0xb8;
            return !uniqueLocal && !documentation;
        }
        return false;
    }

    private JsonNode parseJson(byte[] body, String message) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw validation(message);
        }
    }

    private String providerError(byte[] body) {
        if (body == null || body.length == 0) {
            return "上游未返回错误详情";
        }
        String raw = new String(body, StandardCharsets.UTF_8).trim();
        if (raw.length() > MAX_ERROR_BODY) {
            raw = raw.substring(0, MAX_ERROR_BODY);
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String message = firstNonBlank(
                    root.path("error").path("message").asText(""),
                    root.path("message").asText(""),
                    root.path("error").asText("")
            );
            return StringUtils.hasText(message) ? message : raw;
        } catch (IOException ignored) {
            return raw;
        }
    }

    static boolean capabilityMatches(String modelId, JsonNode item, AiCapability capability) {
        String text = modelMetadataText(modelId, item,
                "display_name", "name", "owned_by", "type", "task", "modality", "modalities",
                "architecture", "capabilities", "features", "tags", "input_modalities", "output_modalities",
                "inputModalities", "outputModalities");
        String inputText = modelMetadataText("", item, "input_modalities", "inputModalities", "input", "inputs");
        String outputText = modelMetadataText("", item, "output_modalities", "outputModalities", "output", "outputs");

        boolean vision = containsAny(inputText, "image", "vision", "visual") || containsAny(text,
                "vision", "visual", "multimodal", "multi-modal", "qwen-vl", "qwen2-vl", "qwen2.5-vl",
                "qwen3-vl", "qwen3vl", "qwen2vl", "qwen-omni", "qwen2.5-omni", "qwen3-omni",
                "llava", "pixtral", "gpt-4o", "gpt-4.1", "gemini", "claude", "grok-vision",
                "internvl", "minicpm-v", "minicpmv", "glm-4v", "glm4v", "deepseek-vl", "deepseekvl",
                "llama-vision", "phi-vision", "molmo", "paligemma", "florence", "idefics", "cogvlm");
        boolean image = containsAny(outputText, "image") || containsAny(text,
                "image-generation", "text-to-image", "dall-e", "flux", "stable-diffusion", "kolors",
                "sdxl", "sd-3", "sd3", "cogview", "hunyuan-image", "ideogram", "recraft", "seedream",
                "imagen", "playground-v") || (!vision && containsAny(text, "image model", "image-model"));
        boolean stt = containsAny(text,
                "whisper", "transcri", "speech-to-text", "speech2text", "speech-recognition",
                "audio-transcription", "-stt", "stt-", "/stt", "-asr", "asr-", "/asr",
                "sensevoice", "paraformer", "funasr");
        boolean tts = !stt && containsAny(text,
                "text-to-speech", "speech-synth", "speech-generation", "audio-generation", "voice-clone",
                "-tts", "tts-", "/tts", "cosyvoice", "fish-speech", "chattts", "chat-tts", "indextts",
                "index-tts", "f5-tts", "melotts", "melo-tts", "gpt-sovits", "sovits", "bark", "spark-tts");
        boolean video = containsAny(text,
                "text-to-video", "image-to-video", "video-generation", "cogvideo", "hunyuanvideo",
                "hunyuan-video", "ltx-video", "wan2.1", "wan2.2", "kling-video");
        boolean nonGenerative = containsAny(text,
                "rerank", "reranker", "embedding", "text-embedding", "jina-embeddings",
                "bge-", "gte-", "e5-", "moderation", "reward-model", "clip-model");
        return switch (capability) {
            case IMAGE -> image;
            case VISION -> vision;
            case TTS -> tts;
            case STT -> stt;
            case CHAT -> !image && !tts && !stt && !video && !nonGenerative;
        };
    }

    private static String modelMetadataText(String modelId, JsonNode item, String... fields) {
        StringBuilder haystack = new StringBuilder(modelId == null ? "" : modelId);
        if (item != null) {
            for (String field : fields) {
                JsonNode value = item.path(field);
                if (!value.isMissingNode() && !value.isNull()) {
                    haystack.append(' ').append(value);
                }
            }
        }
        return haystack.toString().toLowerCase(Locale.ROOT);
    }

    static byte[] sttProbeWav() {
        return STT_PROBE_WAV.clone();
    }

    private static byte[] loadSttProbeWav() {
        try (InputStream encoded = AiProviderProbeService.class.getResourceAsStream("/ai/stt-probe.wav.gz.b64")) {
            if (encoded == null) {
                throw new IllegalStateException("missing STT probe audio resource");
            }
            byte[] compressed = Base64.getMimeDecoder().decode(encoded.readAllBytes());
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                return gzip.readAllBytes();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void writeUtf8(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String capabilityLabel(AiCapability capability) {
        return switch (capability) {
            case CHAT -> "聊天";
            case VISION -> "识图";
            case IMAGE -> "生图";
            case TTS -> "语音合成";
            case STT -> "语音识别";
        };
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        return StringUtils.hasText(message) ? message.trim() : error.getClass().getSimpleName();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
