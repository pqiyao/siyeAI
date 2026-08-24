package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.service.DeepSeekHarnessService;
import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates a DSH session inside the configured workspace after Agent approval. */
@Component
public class AgentDshSessionTool {
    private final DeepSeekHarnessService harnessService;
    private final ObjectMapper objectMapper;
    private final AgentAdminMapper mapper;

    public AgentDshSessionTool(DeepSeekHarnessService harnessService, ObjectMapper objectMapper, AgentAdminMapper mapper) {
        this.harnessService = harnessService;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    public Map<String, Object> create(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> input = new LinkedHashMap<>();
        Object raw = step.get("inputJson");
        try {
            if (raw instanceof Map<?, ?> map) map.forEach((key, value) -> input.put(String.valueOf(key), value));
            else if (raw != null && !String.valueOf(raw).isBlank()) {
                Map<?, ?> parsed = objectMapper.readValue(String.valueOf(raw), Map.class);
                parsed.forEach((key, value) -> input.put(String.valueOf(key), value));
            }
        } catch (Exception ex) { throw new IllegalArgumentException("dsh.session.create 输入 JSON 无效", ex); }
        String preset = input.get("agentPreset") == null ? "" : String.valueOf(input.get("agentPreset")).trim();
        if (preset.length() > 160 || (preset.contains("/") || preset.contains("\\"))) {
            throw new IllegalArgumentException("dsh.session.create agentPreset 格式无效");
        }
        Map<String, Object> response = harnessService.createSession(preset);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", "dsh.session.create");
        result.put("status", "SUCCEEDED");
        result.put("dshResponse", response);
        String sessionId = findSessionId(response);
        if (!sessionId.isBlank()) result.put("sessionId", sessionId);
        if (!sessionId.isBlank() && task != null) {
            Map<String, Object> binding = new LinkedHashMap<>();
            binding.put("taskId", number(task.get("taskId")));
            binding.put("sessionId", sessionId);
            binding.put("tenantKey", text(task.getOrDefault("tenantKey", "platform")));
            binding.put("workspace", harnessService.workspaceRoot());
            binding.put("preset", preset);
            binding.put("status", "ACTIVE");
            mapper.insertDshBinding(binding);
        }
        return result;
    }

    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private String findSessionId(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object direct = map.get("sessionId");
            if (direct != null && !String.valueOf(direct).isBlank()) return String.valueOf(direct);
            for (Object child : map.values()) {
                String found = findSessionId(child);
                if (!found.isBlank()) return found;
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object child : values) {
                String found = findSessionId(child);
                if (!found.isBlank()) return found;
            }
        }
        return "";
    }
}
