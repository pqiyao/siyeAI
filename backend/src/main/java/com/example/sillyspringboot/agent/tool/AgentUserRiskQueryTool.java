package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.admin.mapper.AdminVisitorRiskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only anomaly/risk summary for a user based on existing visitor-risk facts. */
@Component
public class AgentUserRiskQueryTool {

    private final AdminVisitorRiskMapper riskMapper;

    public AgentUserRiskQueryTool(AdminVisitorRiskMapper riskMapper) {
        this.riskMapper = riskMapper;
    }

    public Map<String, Object> query(Map<String, Object> step) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        long userId = number(input.get("userId"));
        if (userId <= 0) throw new IllegalArgumentException("user.risk.query 需要有效 userId");
        List<Map<String, Object>> rows = riskMapper.list(String.valueOf(userId), false, 0, 50);
        List<Map<String, Object>> matches = rows == null ? List.of() : rows.stream()
                .filter(row -> userId == number(row.get("userId")))
                .map(this::safeSummary)
                .toList();
        long risky = matches.stream().filter(row -> number(row.get("riskScore")) > 0).count();
        return Map.of("userId", userId, "deviceCount", matches.size(), "riskyDeviceCount", risky, "devices", matches);
    }

    private Map<String, Object> safeSummary(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("deviceId", "userId", "riskScore", "userChanges", "clientUidChanges",
                "rateLimitHits", "ipChanges", "anonymousBlocksDay", "lastRiskAt", "lastSeenAt")) {
            if (row.containsKey(key)) result.put(key, row.get(key));
        }
        return result;
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("user.risk.query 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isNumber()
                    ? entry.getValue().numberValue() : entry.getValue().isTextual() ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("user.risk.query 输入 JSON 无效", ex);
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
