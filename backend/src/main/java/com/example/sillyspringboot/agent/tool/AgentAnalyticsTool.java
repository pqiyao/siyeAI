package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.admin.mapper.AdminDashboardMapper;
import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parameterized, read-only business metrics for Agent tasks. */
@Component
public class AgentAnalyticsTool {

    private final AdminDashboardMapper dashboardMapper;
    private final AgentAdminMapper agentMapper;
    private final ObjectMapper objectMapper;

    public AgentAnalyticsTool(
            AdminDashboardMapper dashboardMapper,
            AgentAdminMapper agentMapper,
            ObjectMapper objectMapper
    ) {
        this.dashboardMapper = dashboardMapper;
        this.agentMapper = agentMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> query(Map<String, Object> step, Map<String, Object> task) {
        int days = resolveDays(task.get("constraintsJson"));
        LocalDateTime startAt = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("metricSource", "AdminDashboardMapper.parameterized_metrics");
        result.put("rangeDays", days);
        result.put("startAt", startAt.toString());
        result.put("endAt", LocalDateTime.now().toString());
        result.put("overview", safeMap(agentMapper.overview()));
        result.put("generationTrend", safe(dashboardMapper.generationTrend(days)));
        result.put("generationSummary", safeMap(dashboardMapper.generationOpsSummary(startAt)));
        result.put("providerStats", safe(dashboardMapper.generationProviderStats(startAt)));
        result.put("modelStats", safe(dashboardMapper.generationModelStats(startAt)));
        result.put("characterStats", safe(dashboardMapper.generationCharacterStats(startAt)));
        result.put("errorStats", safe(dashboardMapper.generationErrorStats(startAt)));
        result.put("hotCharacters", safe(dashboardMapper.hotCharacters(10)));
        return result;
    }

    public Map<String, Object> review(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> source = latestArtifact(task, "analytics-data");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("reviewedArtifactId", source.get("id"));
        result.put("passed", true);
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
        Map<String, Object> summary = parseMap(source.get("contentJson"));
        Map<String, Object> generation = map(summary.get("generationSummary"));
        double successRate = decimal(generation.get("successRate"));
        long unpriced = whole(generation.get("unpricedAttempts"));
        if (successRate > 0 && successRate < 0.95d) {
            warnings.add("模型生成成功率低于 95%：" + successRate);
        }
        if (unpriced > 0) {
            warnings.add("存在未计价生成尝试：" + unpriced);
        }
        result.put("warnings", warnings);
        result.put("recommendations", List.of(
                "优先查看供应商失败率和错误码分布",
                "对未计价模型补充模型价格配置",
                "将报告中的时间范围与线上发布记录一起核对"
        ));
        result.put("passed", warnings.isEmpty());
        return result;
    }

    public void saveArtifact(long taskId, long stepId, String key, String name, Map<String, Object> content) {
        String json = json(content);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", stepId);
        artifact.put("artifactKey", key);
        artifact.put("artifactType", "REPORT");
        artifact.put("name", name);
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", "agent://task/" + taskId + "/artifacts/" + key + ".json");
        artifact.put("contentHash", sha256(json));
        artifact.put("sizeBytes", (long) json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        artifact.put("mimeType", "application/json");
        artifact.put("metadataJson", "{\"source\":\"agent-analytics\"}");
        artifact.put("contentJson", json);
        if (agentMapper.upsertArtifact(artifact) != 1) {
            throw new IllegalStateException("Agent 分析报告保存失败");
        }
        agentMapper.refreshArtifactCount(taskId);
    }

    private Map<String, Object> latestArtifact(Map<String, Object> task, String key) {
        long taskId = whole(task.get("taskId"));
        Map<String, Object> artifact = agentMapper.findArtifact(taskId, key, 1);
        if (artifact == null) {
            throw new IllegalStateException("缺少分析数据产物：" + key);
        }
        return artifact;
    }

    private Map<String, Object> parseMap(Object value) {
        try {
            JsonNode node = objectMapper.readTree(String.valueOf(value));
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("分析数据产物格式无效", e);
        }
    }

    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> safeMap(Map<String, Object> value) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(key, jsonValue(item)));
        return result;
    }

    private static List<Map<String, Object>> safe(List<Map<String, Object>> value) {
        return value == null ? List.of() : value.stream().map(AgentAnalyticsTool::safeMap).toList();
    }

    private static Object jsonValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof TemporalAccessor temporal) {
            return temporal.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), jsonValue(item)));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(item -> result.add(jsonValue(item)));
            return result;
        }
        return value;
    }

    private static long whole(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0L; }
    }

    private static double decimal(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return 0d; }
    }

    private int resolveDays(Object raw) {
        if (raw != null) {
            try {
                JsonNode node = objectMapper.readTree(String.valueOf(raw));
                int days = node.path("days").asInt(14);
                return Math.max(1, Math.min(days, 365));
            } catch (Exception ignored) {
                // Invalid optional constraints use the documented default.
            }
        }
        return 14;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Agent 报告格式化失败", e); }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) out.append(String.format("%02x", item));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
