package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.service.DeepSeekHarnessService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Executes a DSH prompt only as an approved Agent step. */
@Component
public class AgentDshPromptTool {
    private final DeepSeekHarnessService harnessService;
    private final ObjectMapper objectMapper;

    public AgentDshPromptTool(DeepSeekHarnessService harnessService, ObjectMapper objectMapper) {
        this.harnessService = harnessService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> prompt(Map<String, Object> step) {
        return prompt(step, Map.of());
    }

    public Map<String, Object> prompt(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> input = parseInput(step.get("inputJson"));
        String sessionId = text(input.get("sessionId"));
        String prompt = text(input.get("prompt"));
        if (sessionId.isBlank()) sessionId = sessionIdFromCompletedSteps(task.get("completedSteps"));
        if (prompt.isBlank()) prompt = text(task.get("objective"));
        if (prompt.isBlank()) prompt = text(task.get("taskObjective"));
        if (sessionId.isBlank()) throw new IllegalArgumentException("dsh.session.prompt 需要 sessionId");
        if (prompt.isBlank() || prompt.length() > 32_000) throw new IllegalArgumentException("dsh.session.prompt 内容长度无效");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", prompt);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("mode", "queue");
        payload.put("content", java.util.List.of(content));
        Map<String, Object> response = harnessService.writeRpc("session.prompt", payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", "dsh.session.prompt");
        result.put("status", "SUCCEEDED");
        result.put("sessionId", sessionId);
        result.put("dshResponse", response);
        return result;
    }

    private String sessionIdFromCompletedSteps(Object completedSteps) {
        if (!(completedSteps instanceof Iterable<?> rows)) return "";
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> values)) continue;
            if (!"dsh.session.create".equals(text(values.get("toolName")))) continue;
            String outputJson = text(values.get("outputJson"));
            if (outputJson.isBlank()) continue;
            try {
                String found = findSessionId(objectMapper.readTree(outputJson));
                if (!found.isBlank()) return found;
            } catch (Exception ignored) { }
        }
        return "";
    }

    private String findSessionId(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isObject()) {
            String direct = node.has("sessionId") ? node.get("sessionId").asText("").trim() : "";
            if (!direct.isBlank()) return direct;
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findSessionId(fields.next().getValue());
                if (!found.isBlank()) return found;
            }
        } else if (node.isArray()) {
            for (var child : node) {
                String found = findSessionId(child);
                if (!found.isBlank()) return found;
            }
        }
        return "";
    }

    private Map<String, Object> parseInput(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(text(raw), new TypeReference<>() { });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("dsh.session.prompt 输入 JSON 无效", ex);
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
