package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persists a bounded, auditable handoff between configured Agent roles. */
@Component
public class AgentCollaborationTool {

    private static final Set<String> ROLES = Set.of(
            "Planner", "World Builder", "Character Designer", "Plotter", "Writer", "Reviewer",
            "Media Producer", "Delivery Agent", "Data Analyst", "DevOps Inspector", "Researcher",
            "Developer", "Support Agent"
    );
    private final AgentAdminMapper mapper;

    public AgentCollaborationTool(AgentAdminMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> handoff(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        String fromRole = text(input.get("fromRole"));
        String toRole = text(input.get("toRole"));
        String type = text(input.getOrDefault("messageType", "HANDOFF"));
        String summary = text(input.get("summary"));
        long taskId = number(task == null ? null : task.get("taskId"));
        long stepId = number(step == null ? null : step.get("stepId"));
        if (!ROLES.contains(fromRole) || !ROLES.contains(toRole) || fromRole.equals(toRole)) {
            throw new IllegalArgumentException("协作角色不在白名单或不能相同");
        }
        if (!Set.of("HANDOFF", "REQUEST", "RESULT").contains(type)) {
            throw new IllegalArgumentException("协作消息类型无效");
        }
        if (taskId <= 0 || summary.isBlank() || summary.length() > 4_000) {
            throw new IllegalArgumentException("协作消息参数无效");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", taskId);
        row.put("stepId", stepId > 0 ? stepId : null);
        row.put("fromRole", fromRole);
        row.put("toRole", toRole);
        row.put("messageType", type);
        row.put("summary", summary);
        row.put("payloadJson", null);
        row.put("status", "RECORDED");
        if (mapper.insertCollaboration(row) != 1) {
            throw new IllegalStateException("Agent 协作消息保存失败");
        }
        return Map.of(
                "messageId", row.get("id"),
                "taskId", taskId,
                "fromRole", fromRole,
                "toRole", toRole,
                "messageType", type,
                "status", "RECORDED"
        );
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("agent.handoff 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("agent.handoff 输入 JSON 无效", ex);
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
