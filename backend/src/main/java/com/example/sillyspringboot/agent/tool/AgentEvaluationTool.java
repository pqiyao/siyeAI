package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Deterministic task evaluation based on persisted execution facts. */
@Component
public class AgentEvaluationTool {

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;

    public AgentEvaluationTool(AgentAdminMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> evaluate(Map<String, Object> task) {
        return evaluate(task, 0L);
    }

    public Map<String, Object> evaluate(Map<String, Object> task, long assumedSuccessfulStepId) {
        long taskId = number(task == null ? null : task.get("taskId"));
        if (taskId <= 0) throw new IllegalArgumentException("评估任务编号无效");
        List<Map<String, Object>> steps = mapper.listSteps(taskId);
        List<Map<String, Object>> artifacts = mapper.listArtifacts(taskId);
        Map<String, Object> usage = mapper.summarizeToolUsage(taskId);
        int totalSteps = steps == null ? 0 : steps.size();
        long succeededSteps = count(steps, "SUCCEEDED");
        if (assumedSuccessfulStepId > 0 && steps != null && steps.stream().anyMatch(row ->
                assumedSuccessfulStepId == number(row.get("id"))
                        && "RUNNING".equals(String.valueOf(row.get("status"))))) {
            succeededSteps++;
        }
        long failedSteps = count(steps, "FAILED");
        int totalArtifacts = artifacts == null ? 0 : artifacts.size();
        long readyArtifacts = count(artifacts, "READY");
        if (assumedSuccessfulStepId > 0) {
            totalArtifacts++;
            readyArtifacts++;
        }
        long degradedArtifacts = artifacts == null ? 0 : artifacts.stream()
                .filter(row -> "READY".equals(String.valueOf(row.get("status")))
                        && isDegraded(row.get("contentJson")))
                .count();
        long degradedSteps = steps == null ? 0 : steps.stream()
                .filter(row -> "SUCCEEDED".equals(String.valueOf(row.get("status")))
                        && isDegraded(row.get("outputJson")))
                .count();
        List<String> missingRequiredArtifacts = missingRequiredArtifacts(task, artifacts);
        double stepRate = totalSteps == 0 ? 0D : (double) succeededSteps / totalSteps;
        double artifactRate = totalArtifacts == 0 ? 1D : (double) readyArtifacts / totalArtifacts;
        long unpriced = usage == null ? 0L : number(usage.get("unpricedCalls"));
        int score = (int) Math.round(stepRate * 60D + artifactRate * 30D + (failedSteps == 0 ? 10D : 0D));
        long degradedCount = degradedArtifacts + degradedSteps;
        if (degradedCount > 0) score = Math.max(0, score - Math.min(25, (int) degradedCount * 5));
        String verdict = score >= 85 && failedSteps == 0 ? "PASS" : score >= 60 ? "WARN" : "FAIL";
        if (!missingRequiredArtifacts.isEmpty()) {
            verdict = "FAIL";
            score = Math.min(score, 59);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("taskId", taskId);
        result.put("taskStatus", task == null ? null : task.get("status"));
        result.put("score", score);
        result.put("verdict", verdict);
        Integer minimumScore = minimumEvaluationScore(task);
        result.put("minimumEvaluationScore", minimumScore);
        result.put("evaluationGatePassed", minimumScore == null || score >= minimumScore);
        result.put("stepSuccessRate", stepRate);
        result.put("artifactReadyRate", artifactRate);
        result.put("totalSteps", totalSteps);
        result.put("succeededSteps", succeededSteps);
        result.put("failedSteps", failedSteps);
        result.put("totalArtifacts", totalArtifacts);
        result.put("readyArtifacts", readyArtifacts);
        result.put("degradedArtifacts", degradedArtifacts);
        result.put("degradedSteps", degradedSteps);
        result.put("unpricedCalls", unpriced);
        result.put("requiredArtifacts", requiredArtifacts(task));
        result.put("missingRequiredArtifacts", missingRequiredArtifacts);
        result.put("recommendations", recommendations(failedSteps, degradedCount, unpriced, missingRequiredArtifacts));
        return result;
    }

    private Integer minimumEvaluationScore(Map<String, Object> task) {
        if (task == null || task.get("constraintsJson") == null) return null;
        try {
            JsonNode constraints = objectMapper.readTree(String.valueOf(task.get("constraintsJson")));
            JsonNode value = constraints == null ? null : constraints.get("minEvaluationScore");
            if (value == null || !value.isNumber()) return null;
            int score = value.intValue();
            return score >= 0 && score <= 100 ? score : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> requiredArtifacts(Map<String, Object> task) {
        String taskType = task == null ? "" : String.valueOf(task.getOrDefault("taskType", ""));
        if ("CONTENT_CREATION".equalsIgnoreCase(taskType)) {
            return List.of("worldbook", "story-outline", "story-chapters", "continuity-report",
                    "interactive-html", "task-package");
        }
        return List.of();
    }

    private List<String> missingRequiredArtifacts(Map<String, Object> task, List<Map<String, Object>> artifacts) {
        List<String> required = requiredArtifacts(task);
        if (required.isEmpty()) return List.of();
        Set<String> readyKeys = new HashSet<>();
        if (artifacts != null) {
            for (Map<String, Object> artifact : artifacts) {
                if (artifact != null && "READY".equals(String.valueOf(artifact.get("status")))) {
                    readyKeys.add(String.valueOf(artifact.get("artifactKey")));
                }
            }
        }
        return required.stream().filter(key -> !readyKeys.contains(key)).toList();
    }

    private boolean isDegraded(Object contentJson) {
        if (contentJson == null || String.valueOf(contentJson).isBlank()) return false;
        try {
            JsonNode node = objectMapper.readTree(String.valueOf(contentJson));
            return node.path("degraded").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    public void saveArtifact(long taskId, long stepId, Map<String, Object> task) {
        Map<String, Object> evaluation = evaluate(task);
        String json;
        try {
            json = objectMapper.writeValueAsString(evaluation);
        } catch (Exception e) {
            throw new IllegalStateException("Agent 评估结果格式化失败", e);
        }
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", stepId > 0 ? stepId : null);
        artifact.put("artifactKey", "agent-evaluation");
        artifact.put("artifactType", "EVALUATION");
        artifact.put("name", "Agent 任务评估");
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", "agent://task/" + taskId + "/artifacts/agent-evaluation.json");
        artifact.put("contentHash", sha256(json));
        artifact.put("sizeBytes", (long) json.getBytes(StandardCharsets.UTF_8).length);
        artifact.put("mimeType", "application/json");
        artifact.put("metadataJson", "{\"source\":\"artifact.evaluate\"}");
        artifact.put("contentJson", json);
        if (mapper.upsertArtifact(artifact) != 1) {
            throw new IllegalStateException("Agent 评估结果保存失败");
        }
        mapper.refreshArtifactCount(taskId);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static List<String> recommendations(long failedSteps, long degradedArtifacts, long unpricedCalls,
                                                List<String> missingRequiredArtifacts) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (failedSteps > 0) result.add("处理失败步骤后再交付");
        if (degradedArtifacts > 0) result.add("复核降级产物并决定是否重新生成");
        if (unpricedCalls > 0) result.add("补齐模型或工具调用的 Token/费用计价");
        if (missingRequiredArtifacts != null && !missingRequiredArtifacts.isEmpty()) {
            result.add("补齐关键交付产物：" + String.join(", ", missingRequiredArtifacts));
        }
        if (result.isEmpty()) result.add("当前执行事实满足基础交付检查");
        return result;
    }

    private static long count(List<Map<String, Object>> rows, String status) {
        return rows == null ? 0 : rows.stream().filter(row -> status.equals(String.valueOf(row.get("status")))).count();
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
