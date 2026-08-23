package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.admin.mapper.AdminH5UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only, privacy-limited user profile summary for operations agents. */
@Component
public class AgentUserProfileQueryTool {

    private final AdminH5UserMapper userMapper;

    public AgentUserProfileQueryTool(AdminH5UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Map<String, Object> query(Map<String, Object> step) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        long userId = number(input.get("userId"));
        if (userId <= 0) throw new IllegalArgumentException("user.profile.query 需要有效 userId");
        Map<String, Object> raw = userMapper.findDetail(userId);
        if (raw == null || raw.isEmpty()) return Map.of("userId", userId, "exists", false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("exists", true);
        for (String key : List.of("nickname", "status", "vipType", "vipExpiresAt", "score", "goldCoin",
                "needEdit", "createdAt", "lastLoginAt", "bindingStatus")) {
            if (raw.containsKey(key)) result.put(key, raw.get(key));
        }
        return result;
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("user.profile.query 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue().isNumber() ? entry.getValue().numberValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("user.profile.query 输入 JSON 无效", ex);
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
