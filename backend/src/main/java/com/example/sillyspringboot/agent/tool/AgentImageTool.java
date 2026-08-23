package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.ops.service.ImageGenerationAssetStorageService;
import com.example.sillyspringboot.ops.service.ImageGenerationEngine;
import com.example.sillyspringboot.ops.service.ImageGenerationReadinessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Generates bounded Agent images through the configured system image engine. */
@Service
public class AgentImageTool {

    private static final int MAX_IMAGES = 4;
    private static final int MAX_PROMPT_CHARS = 4_000;
    private static final int MAX_NEGATIVE_PROMPT_CHARS = 2_000;

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;
    private final ImageGenerationReadinessService readinessService;
    private final ImageGenerationAssetStorageService assetStorageService;
    private final Map<String, ImageGenerationEngine> engines;

    public AgentImageTool(
            AgentAdminMapper mapper,
            ObjectMapper objectMapper,
            ImageGenerationReadinessService readinessService,
            ImageGenerationAssetStorageService assetStorageService,
            List<ImageGenerationEngine> engines
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.readinessService = readinessService;
        this.assetStorageService = assetStorageService;
        this.engines = engines.stream().collect(Collectors.toMap(
                engine -> normalize(engine.engineName()),
                engine -> engine,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    @Transactional
    public Map<String, Object> generate(Map<String, Object> step, Map<String, Object> task) {
        long taskId = number(task.get("taskId"));
        long stepId = number(step.get("stepId"));
        if (taskId <= 0 || stepId <= 0) {
            throw new IllegalArgumentException("图片工具缺少有效的任务或步骤编号");
        }

        JsonNode input = mergedInput(step.get("inputJson"), task.get("constraintsJson"));
        ImageGenerationReadinessService.Snapshot snapshot = readinessService.systemSnapshot();
        if (!snapshot.ready() && mediaOptional(input)) {
            return skipped(snapshot.statusCode(), snapshot.message());
        }
        readinessService.guardReady(snapshot.engine());
        ImageGenerationEngine engine = engines.get(normalize(snapshot.engine()));
        if (engine == null) {
            if (mediaOptional(input)) {
                return skipped("ENGINE_UNAVAILABLE", "当前系统生图引擎未注册");
            }
            throw new IllegalStateException("当前系统生图引擎未注册: " + snapshot.engine());
        }

        List<JsonNode> specifications = imageSpecifications(input);
        List<Map<String, Object>> images = new ArrayList<>();
        for (int index = 0; index < specifications.size(); index++) {
            String imageKey = "image-" + (index + 1);
            JsonNode specification = specifications.get(index);
            String prompt = prompt(specification, input, task);
            String requestId = "agent-image-" + taskId + "-" + (index + 1);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("prompt", prompt);
            payload.put("userPrompt", prompt);
            payload.put("negativePrompt", negativePrompt(specification, input));
            payload.put("aspectRatio", aspectRatio(specification, input));
            payload.put("count", 1);
            payload.put("characterId", 0L);
            payload.put("referenceMode", "free");
            payload.put("referencePolicy", "prompt_first");
            payload.put("imageRequestId", requestId);

            Map<String, Object> generated = engine.generate("agent:" + taskId, payload);
            // Use a negative synthetic scope so Agent files cannot share a user media directory.
            Map<String, Object> stored = assetStorageService.externalize(-taskId, requestId, generated);
            Map<String, Object> image = firstImage(stored, imageKey);
            saveArtifact(taskId, stepId, imageKey, image, prompt, snapshot.engine());
            images.add(image);
        }
        mapper.refreshArtifactCount(taskId);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("operation", "GENERATED");
        output.put("engine", snapshot.engine());
        output.put("imageCount", images.size());
        output.put("images", images);
        return output;
    }

    private static Map<String, Object> skipped(String statusCode, String message) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("operation", "SKIPPED");
        output.put("degraded", true);
        output.put("reason", "媒体为可选项，当前生图未就绪");
        output.put("statusCode", statusCode);
        output.put("message", message == null ? "" : message);
        return output;
    }

    private static boolean mediaOptional(JsonNode input) {
        return input != null && (input.path("mediaOptional").asBoolean(false)
                || input.path("imageOptional").asBoolean(false)
                || input.path("skipImages").asBoolean(false));
    }

    private void saveArtifact(long taskId, long stepId, String imageKey, Map<String, Object> image,
                              String prompt, String engineName) {
        String contentJson = json(image);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", stepId);
        artifact.put("artifactKey", imageKey);
        artifact.put("artifactType", "IMAGE");
        artifact.put("name", imageKey + "." + extension(image.get("mimeType")));
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", text(image.get("url")));
        artifact.put("contentHash", text(image.get("contentHash")));
        artifact.put("sizeBytes", number(image.get("byteSize")));
        artifact.put("mimeType", text(image.get("mimeType")));
        artifact.put("metadataJson", json(Map.of(
                "generatedBy", "AGENT_IMAGE_TOOL",
                "engine", engineName,
                "taskId", taskId,
                "stepId", stepId,
                "promptLength", prompt.length()
        )));
        artifact.put("contentJson", contentJson);
        if (mapper.upsertArtifact(artifact) < 1) {
            throw new IllegalStateException("图片产物记录保存失败: " + imageKey);
        }
    }

    private Map<String, Object> firstImage(Map<String, Object> result, String imageKey) {
        Object rawImages = result == null ? null : result.get("images");
        if (!(rawImages instanceof List<?> values) || values.isEmpty() || !(values.get(0) instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("生图引擎没有返回可用图片: " + imageKey);
        }
        Map<String, Object> image = new LinkedHashMap<>();
        raw.forEach((key, value) -> image.put(String.valueOf(key), value));
        if (!StringUtils.hasText(text(image.get("url")))) {
            throw new IllegalStateException("生图引擎返回的图片地址为空: " + imageKey);
        }
        return image;
    }

    private List<JsonNode> imageSpecifications(JsonNode input) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode rawImages = input.get("images");
        if (rawImages != null && rawImages.isArray()) {
            rawImages.forEach(item -> {
                if (result.size() < MAX_IMAGES && item != null && item.isObject()) {
                    result.add(item);
                }
            });
        }
        if (result.isEmpty() && input.get("image") != null && input.get("image").isObject()) {
            result.add(input.get("image"));
        }
        int count = clamp(input.get("imageCount"), 1, MAX_IMAGES);
        while (result.size() < count) {
            result.add(input);
        }
        return result.subList(0, Math.min(result.size(), MAX_IMAGES));
    }

    private String prompt(JsonNode specification, JsonNode input, Map<String, Object> task) {
        String value = firstText(specification, "prompt", "scene", "description");
        if (value.isBlank()) value = firstText(input, "prompt", "scene", "imagePrompt");
        if (value.isBlank()) value = text(task.get("taskObjective"));
        return clip(value.isBlank() ? "一幅与任务主题一致的高质量插图" : value, MAX_PROMPT_CHARS);
    }

    private String negativePrompt(JsonNode specification, JsonNode input) {
        String value = firstText(specification, "negativePrompt");
        if (value.isBlank()) value = firstText(input, "negativePrompt");
        return clip(value, MAX_NEGATIVE_PROMPT_CHARS);
    }

    private String aspectRatio(JsonNode specification, JsonNode input) {
        String value = firstText(specification, "aspectRatio");
        if (value.isBlank()) value = firstText(input, "aspectRatio");
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "square", "landscape", "wide" -> value.toLowerCase(Locale.ROOT);
            default -> "portrait";
        };
    }

    private JsonNode mergedInput(Object rawStep, Object rawConstraints) {
        ObjectNode merged = objectMapper.createObjectNode();
        JsonNode constraints = parseObject(rawConstraints);
        JsonNode step = parseObject(rawStep);
        if (constraints != null) merged.setAll((ObjectNode) constraints);
        if (step != null) merged.setAll((ObjectNode) step);
        JsonNode nestedImage = merged.get("imageGeneration");
        if (nestedImage != null && nestedImage.isObject()) merged.setAll((ObjectNode) nestedImage);
        return merged;
    }

    private JsonNode parseObject(Object raw) {
        if (raw == null || text(raw).isBlank()) return null;
        try {
            JsonNode node = raw instanceof JsonNode jsonNode ? jsonNode : objectMapper.readTree(text(raw));
            if (node != null && !node.isObject()) throw new IllegalArgumentException("图片工具输入必须是 JSON 对象");
            return node;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("图片工具输入 JSON 无效", ex);
        }
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return "";
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isValueNode() && !value.asText("").isBlank()) return value.asText("").trim();
        }
        return "";
    }

    private static int clamp(JsonNode value, int min, int max) {
        int raw = value == null || !value.isValueNode() ? min : value.asInt(min);
        return Math.max(min, Math.min(max, raw));
    }

    private static String extension(Object mimeType) {
        String value = text(mimeType).toLowerCase(Locale.ROOT);
        if (value.contains("jpeg") || value.contains("jpg")) return "jpg";
        if (value.contains("webp")) return "webp";
        if (value.contains("gif")) return "gif";
        return "png";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("图片产物 JSON 格式化失败", ex);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String clip(String value, int maxLength) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(text(value)); } catch (NumberFormatException ex) { return 0L; }
    }
}
