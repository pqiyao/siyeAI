package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.net.BoundedHttpBodyHandlers;
import com.example.sillyspringboot.shared.net.MediaPayloadValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * System NovelAI engine. The request shape follows the public ST integration
 * behavior, but this implementation is independent of ST runtime state.
 */
@Service
public class NovelAiImageGenerationService implements ImageGenerationEngine {

    private static final int MAX_PROMPT_CHARS = 4000;
    private static final int MAX_NEGATIVE_PROMPT_CHARS = 2000;
    private static final double REFERENCE_PIXEL_COUNT = 832.0d * 1216.0d;

    private final AppImageGenerationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NovelAiImageGenerationService(
            AppImageGenerationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String engineName() {
        return "novelai";
    }

    @Override
    public Map<String, Object> generate(String clientUid, Map<String, Object> payload) {
        AppImageGenerationProperties.NovelAi cfg = properties.getNovelAi();
        String prompt = trim(payload == null ? null : payload.get("prompt"), MAX_PROMPT_CHARS);
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写生图提示词");
        }
        String referencePolicy = safe(payload == null ? null : payload.get("referencePolicy"));
        if ("reference_only".equalsIgnoreCase(referencePolicy)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "NovelAI 强一致性参考图能力尚未验证，请先使用自由或平衡模式"
            );
        }

        String token = safe(cfg.getToken());
        String baseUrl = normalizeBaseUrl(cfg.getBaseUrl());
        String model = firstNonBlank(cfg.getModel(), "nai-diffusion-4-5-full");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "系统 NovelAI 尚未配置 Token");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "系统 NovelAI 地址配置不正确");
        }

        String aspectRatio = normalizeAspectRatio(payload == null ? null : payload.get("aspectRatio"));
        int[] dimensions = dimensions(aspectRatio);
        String negativePrompt = trim(payload == null ? null : payload.get("negativePrompt"), MAX_NEGATIVE_PROMPT_CHARS);
        long seed = cfg.getSeed() >= 0
                ? cfg.getSeed()
                : ThreadLocalRandom.current().nextLong(0L, 10_000_000_000L);

        ObjectNode body = buildRequestBody(cfg, model, prompt, negativePrompt, dimensions[0], dimensions[1], seed);
        byte[] imageBytes = requestImage(baseUrl, token, body, cfg.getRequestTimeout(), cfg.getMaxResponseBytes());
        String mimeType = MediaPayloadValidator.requireImage(imageBytes, "");
        String imageUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", imageUrl);
        image.put("prompt", prompt);
        image.put("rawPrompt", safe(payload == null ? null : payload.get("userPrompt")));
        image.put("negativePrompt", negativePrompt);
        image.put("width", dimensions[0]);
        image.put("height", dimensions[1]);
        image.put("seed", seed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "provider");
        result.put("providerSource", "novelai");
        result.put("modelName", model);
        result.put("promptEnhanced", false);
        result.put("referenceApplied", false);
        result.put("referencePolicy", referencePolicy);
        result.put("images", List.of(image));
        if ("balanced".equalsIgnoreCase(referencePolicy)
                && StringUtils.hasText(safe(payload == null ? null : payload.get("referenceImageUrl")))) {
            result.put("warning", "当前 NovelAI 系统链路使用角色视觉 Prompt，参考图能力尚未启用");
        }
        result.put("message", "ok");
        return result;
    }

    private ObjectNode buildRequestBody(
            AppImageGenerationProperties.NovelAi cfg,
            String model,
            String prompt,
            String negativePrompt,
            int width,
            int height,
            long seed
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("action", "generate");
        body.put("input", prompt);
        body.put("model", model);

        ObjectNode parameters = body.putObject("parameters");
        parameters.put("params_version", 3);
        parameters.put("prefer_brownian", true);
        parameters.put("negative_prompt", negativePrompt);
        parameters.put("height", height);
        parameters.put("width", width);
        parameters.put("scale", clampDouble(cfg.getScale(), 1.0d, 30.0d, 9.0d));
        parameters.put("seed", seed);
        parameters.put("sampler", firstNonBlank(cfg.getSampler(), "k_dpmpp_2m"));
        parameters.put("noise_schedule", firstNonBlank(cfg.getScheduler(), "karras"));
        parameters.put("steps", clampInt(cfg.getSteps(), 1, 50, 28));
        parameters.put("n_samples", 1);
        parameters.put("ucPreset", 0);
        parameters.put("qualityToggle", false);
        parameters.put("add_original_image", false);
        parameters.put("controlnet_strength", 1);
        parameters.put("deliberate_euler_ancestral_bug", false);
        parameters.put("dynamic_thresholding", cfg.isDecrisper());
        parameters.put("legacy", false);
        parameters.put("legacy_v3_extend", false);
        parameters.put("sm", cfg.isSm());
        parameters.put("sm_dyn", cfg.isSmDyn());
        parameters.put("uncond_scale", 1);
        if (cfg.isVarietyBoost()) {
            parameters.put("skip_cfg_above_sigma", calculateSkipCfgAboveSigma(width, height, model));
        } else {
            parameters.putNull("skip_cfg_above_sigma");
        }
        parameters.put("use_coords", false);
        parameters.put("characterPrompts", objectMapper.createArrayNode());
        parameters.put("reference_image_multiple", objectMapper.createArrayNode());
        parameters.put("reference_information_extracted_multiple", objectMapper.createArrayNode());
        parameters.put("reference_strength_multiple", objectMapper.createArrayNode());

        ObjectNode v4Negative = parameters.putObject("v4_negative_prompt");
        ObjectNode v4NegativeCaption = v4Negative.putObject("caption");
        v4NegativeCaption.put("base_caption", negativePrompt);
        v4NegativeCaption.put("char_captions", objectMapper.createArrayNode());

        ObjectNode v4Prompt = parameters.putObject("v4_prompt");
        ObjectNode v4Caption = v4Prompt.putObject("caption");
        v4Caption.put("base_caption", prompt);
        v4Caption.put("char_captions", objectMapper.createArrayNode());
        v4Prompt.put("use_coords", false);
        v4Prompt.put("use_order", true);
        return body;
    }

    private byte[] requestImage(
            String baseUrl,
            String token,
            ObjectNode body,
            Duration requestTimeout,
            int maxResponseBytes
    ) {
        Duration timeout = requestTimeout == null ? Duration.ofSeconds(120) : requestTimeout;
        int maxBytes = Math.max(1_048_576, Math.min(maxResponseBytes, 64 * 1024 * 1024));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/ai/generate-image"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, BoundedHttpBodyHandlers.ofByteArray(maxBytes));
            if (response.statusCode() / 100 != 2) {
                throw providerError(response.body(), response.statusCode());
            }
            byte[] image = extractPng(response.body(), maxBytes);
            if (image.length == 0) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "NovelAI 没有返回图片");
            }
            return image;
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "NovelAI 生图请求被中断");
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "NovelAI 生图请求失败，请稍后重试", ex);
        }
    }

    private byte[] extractPng(byte[] archive, int maxBytes) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName() == null ? "" : entry.getName().toLowerCase();
                if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")) {
                    continue;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    total += read;
                    if (total > maxBytes) {
                        throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "NovelAI 返回的图片过大");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
            return new byte[0];
        }
    }

    private BusinessException providerError(byte[] body, int status) {
        String text = body == null ? "" : new String(body, StandardCharsets.UTF_8).trim();
        if (status == 401 || status == 403) {
            return new BusinessException(ErrorCode.FORBIDDEN, "系统 NovelAI Token 无效，请检查后台配置");
        }
        if (status == 429) {
            return new BusinessException(ErrorCode.RATE_LIMITED, "NovelAI 当前繁忙，请稍后重试");
        }
        String detail = text.length() > 300 ? text.substring(0, 300) : text;
        return new BusinessException(ErrorCode.UPSTREAM_ERROR,
                StringUtils.hasText(detail) ? "NovelAI 请求失败：" + detail : "NovelAI 请求失败，请稍后重试");
    }

    private static String normalizeBaseUrl(String value) {
        String text = safe(value);
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private static String normalizeAspectRatio(Object value) {
        String text = safe(value);
        return "square".equals(text) || "landscape".equals(text) || "wide".equals(text)
                ? text : "portrait";
    }

    private static int[] dimensions(String aspectRatio) {
        return switch (aspectRatio) {
            case "square" -> new int[] {1024, 1024};
            case "landscape", "wide" -> new int[] {1216, 832};
            default -> new int[] {832, 1216};
        };
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "NovelAI 请求组装失败", ex);
        }
    }

    private static String trim(Object value, int maxChars) {
        String text = safe(value);
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return "";
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        int safe = value <= 0 ? fallback : value;
        return Math.max(min, Math.min(max, safe));
    }

    private static double clampDouble(double value, double min, double max, double fallback) {
        double safe = Double.isFinite(value) ? value : fallback;
        return Math.max(min, Math.min(max, safe));
    }

    private static double calculateSkipCfgAboveSigma(int width, int height, String model) {
        double magic = safe(model).contains("nai-diffusion-4-5") ? 58.0d : 19.0d;
        double ratio = ((double) width * (double) height) / REFERENCE_PIXEL_COUNT;
        return Math.sqrt(ratio) * magic;
    }
}
