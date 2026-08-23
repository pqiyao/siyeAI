package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.service.AgentArtifactStorageService;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.chat.service.ChatAudioSpeechService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Generates bounded Agent voiceover artifacts through the existing TTS routing. */
@Service
public class AgentTtsTool {

    private static final int MAX_SEGMENTS = 4;
    private static final int MAX_TEXT_CHARS = 1_200;

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;
    private final AgentArtifactStorageService storageService;
    private final ChatAudioSpeechService speechService;
    private final AiRoutingService aiRoutingService;

    public AgentTtsTool(
            AgentAdminMapper mapper,
            ObjectMapper objectMapper,
            AgentArtifactStorageService storageService,
            ChatAudioSpeechService speechService,
            AiRoutingService aiRoutingService
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.speechService = speechService;
        this.aiRoutingService = aiRoutingService;
    }

    @Transactional
    public Map<String, Object> generate(Map<String, Object> step, Map<String, Object> task) {
        long taskId = number(task.get("taskId"));
        long stepId = number(step.get("stepId"));
        JsonNode input = mergedInput(step.get("inputJson"), task.get("constraintsJson"));
        if (!requested(input, text(task.get("taskObjective")))) {
            return Map.of(
                    "operation", "SKIPPED",
                    "degraded", false,
                    "reason", "任务没有明确要求语音产物"
            );
        }
        if (mediaOptional(input)) {
            Map<String, Object> readiness = aiRoutingService.capabilitySummary(AiCapability.TTS);
            if (!Boolean.TRUE.equals(readiness.get("ready"))) {
                return Map.of(
                        "operation", "SKIPPED",
                        "degraded", true,
                        "reason", "媒体为可选项，当前 TTS 路由未就绪",
                        "status", readiness.getOrDefault("status", "unavailable")
                );
            }
        }
        List<JsonNode> segments = segments(input, task);
        List<Map<String, Object>> audios = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            JsonNode segment = segments.get(index);
            String content = clip(firstText(segment, "text", "content", "script"), MAX_TEXT_CHARS);
            if (content.isBlank()) {
                throw new IllegalArgumentException("第" + (index + 1) + "段语音内容为空");
            }
            String model = firstText(segment, "modelName", "model");
            String voice = firstText(segment, "voiceName", "voice");
            String template = firstText(segment, "voiceTemplateCode", "templateCode");
            ChatAudioSpeechService.AudioSpeechResult result = speechService.synthesizeForUser(
                    0L, content, model, voice, template, null, "");
            String audioKey = "audio-" + (index + 1);
            AgentArtifactStorageService.StoredFile stored = storageService.storeAudio(
                    taskId, audioKey, result.audioBytes(), result.mimeType());
            Map<String, Object> audio = new LinkedHashMap<>();
            audio.put("artifactKey", audioKey);
            audio.put("uri", stored.uri());
            audio.put("contentHash", stored.contentHash());
            audio.put("sizeBytes", stored.sizeBytes());
            audio.put("mimeType", result.mimeType());
            audio.put("modelName", result.modelName());
            audio.put("voiceName", result.voiceName());
            audio.put("text", content);
            saveArtifact(taskId, stepId, audioKey, audio);
            audios.add(audio);
        }
        mapper.refreshArtifactCount(taskId);
        return Map.of(
                "operation", "GENERATED",
                "audioCount", audios.size(),
                "audios", audios
        );
    }

    private void saveArtifact(long taskId, long stepId, String key, Map<String, Object> audio) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", stepId);
        artifact.put("artifactKey", key);
        artifact.put("artifactType", "AUDIO");
        artifact.put("name", key + "." + extension(audio.get("mimeType")));
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", audio.get("uri"));
        artifact.put("contentHash", audio.get("contentHash"));
        artifact.put("sizeBytes", audio.get("sizeBytes"));
        artifact.put("mimeType", audio.get("mimeType"));
        artifact.put("metadataJson", json(Map.of(
                "generatedBy", "AGENT_TTS_TOOL",
                "modelName", audio.get("modelName"),
                "voiceName", audio.get("voiceName"),
                "textLength", text(audio.get("text")).length()
        )));
        artifact.put("contentJson", json(audio));
        if (mapper.upsertArtifact(artifact) < 1) {
            throw new IllegalStateException("音频产物记录保存失败: " + key);
        }
    }

    private List<JsonNode> segments(JsonNode input, Map<String, Object> task) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode tts = input.get("tts");
        if (tts != null && tts.isObject()) {
            JsonNode rawSegments = tts.get("segments");
            if (rawSegments != null && rawSegments.isArray()) {
                rawSegments.forEach(item -> {
                    if (result.size() < MAX_SEGMENTS && item != null && item.isObject()) result.add(item);
                });
            }
            if (result.isEmpty()) result.add(tts);
        }
        if (result.isEmpty()) {
            JsonNode rawSegments = input.get("audioSegments");
            if (rawSegments != null && rawSegments.isArray()) {
                rawSegments.forEach(item -> {
                    if (result.size() < MAX_SEGMENTS && item != null && item.isObject()) result.add(item);
                });
            }
        }
        if (result.isEmpty()) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("text", firstText(input, "voiceoverText", "audioText", "text"));
            if (fallback.path("text").asText("").isBlank()) fallback.put("text", text(task.get("taskObjective")));
            result.add(fallback);
        }
        return result.subList(0, Math.min(result.size(), MAX_SEGMENTS));
    }

    private static boolean requested(JsonNode input, String objective) {
        if (input.has("tts") || input.has("audioSegments") || input.path("ttsEnabled").asBoolean(false)) return true;
        String value = (objective == null ? "" : objective).toLowerCase(Locale.ROOT);
        return value.contains("配音") || value.contains("语音") || value.contains("音频")
                || value.contains("tts") || value.contains("voiceover");
    }

    private static boolean mediaOptional(JsonNode input) {
        return input != null && (input.path("mediaOptional").asBoolean(false)
                || input.path("ttsOptional").asBoolean(false)
                || input.path("skipTts").asBoolean(false));
    }

    private JsonNode mergedInput(Object rawStep, Object rawConstraints) {
        ObjectNode merged = objectMapper.createObjectNode();
        JsonNode constraints = parseObject(rawConstraints);
        JsonNode step = parseObject(rawStep);
        if (constraints != null) merged.setAll((ObjectNode) constraints);
        if (step != null) merged.setAll((ObjectNode) step);
        return merged;
    }

    private JsonNode parseObject(Object raw) {
        if (raw == null || text(raw).isBlank()) return null;
        try {
            JsonNode node = raw instanceof JsonNode jsonNode ? jsonNode : objectMapper.readTree(text(raw));
            if (node != null && !node.isObject()) throw new IllegalArgumentException("TTS 工具输入必须是 JSON 对象");
            return node;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("TTS 工具输入 JSON 无效", ex);
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

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("音频产物 JSON 格式化失败", ex); }
    }

    private static String extension(Object mimeType) {
        String value = text(mimeType).toLowerCase(Locale.ROOT);
        if (value.contains("wav")) return "wav";
        if (value.contains("ogg")) return "ogg";
        if (value.contains("flac")) return "flac";
        if (value.contains("mp4")) return "m4a";
        if (value.contains("webm")) return "webm";
        return "mp3";
    }

    private static String clip(String value, int maxLength) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(text(value)); } catch (NumberFormatException ex) { return 0L; }
    }
}
