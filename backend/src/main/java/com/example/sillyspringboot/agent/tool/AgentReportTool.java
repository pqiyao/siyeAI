package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns the preceding read-only step output into a durable JSON report artifact. */
@Component
public class AgentReportTool {

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;

    public AgentReportTool(AgentAdminMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> render(Map<String, Object> step, Map<String, Object> task) {
        long taskId = number(task.get("taskId"));
        long stepId = number(step.get("stepId"));
        List<Map<String, Object>> artifacts = mapper.listArtifacts(taskId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("title", task.getOrDefault("taskTitle", "Agent 执行报告"));
        report.put("objective", task.get("taskObjective"));
        report.put("generatedAt", java.time.Instant.now().toString());
        report.put("sourceArtifacts", artifacts == null ? List.of() : artifacts.stream()
                .filter(row -> !"REPORT".equals(String.valueOf(row.get("artifactType")))
                        || !"agent-report".equals(String.valueOf(row.get("artifactKey"))))
                .map(row -> {
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("artifactKey", row.get("artifactKey"));
                    source.put("artifactType", row.get("artifactType"));
                    source.put("name", row.get("name"));
                    source.put("status", row.get("status"));
                    source.put("contentHash", row.get("contentHash"));
                    return source;
                }).toList());
        report.put("summary", summaryFor(task.get("taskType"), artifacts));
        String content = json(report);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", stepId);
        artifact.put("artifactKey", "agent-report");
        artifact.put("artifactType", "REPORT");
        artifact.put("name", "Agent 执行报告");
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", "agent://task/" + taskId + "/artifacts/agent-report.json");
        artifact.put("contentHash", sha256(content));
        artifact.put("sizeBytes", (long) content.getBytes(StandardCharsets.UTF_8).length);
        artifact.put("mimeType", "application/json");
        artifact.put("metadataJson", "{\"source\":\"artifact.render_report\"}");
        artifact.put("contentJson", content);
        if (mapper.upsertArtifact(artifact) != 1) throw new IllegalStateException("Agent 报告保存失败");
        mapper.refreshArtifactCount(taskId);
        Map<String, Object> persisted = mapper.findArtifact(taskId, "agent-report", 1);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("artifactKey", "agent-report");
        output.put("artifactId", persisted == null ? null : persisted.get("id"));
        output.put("summary", report.get("summary"));
        return output;
    }

    private Object summaryFor(Object taskType, List<Map<String, Object>> artifacts) {
        long ready = artifacts == null ? 0 : artifacts.stream().filter(row -> "READY".equals(String.valueOf(row.get("status")))).count();
        return Map.of("taskType", taskType == null ? "GENERAL" : taskType, "readyArtifactCount", ready);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Agent 报告格式化失败", e); }
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) out.append(String.format("%02x", item));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
