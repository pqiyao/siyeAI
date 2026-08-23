package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.support.entity.AppSupportTicket;
import com.example.sillyspringboot.support.mapper.AppSupportTicketMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only, privacy-limited user ticket summary for operations agents. */
@Component
public class AgentUserTicketQueryTool {

    private final AppSupportTicketMapper ticketMapper;

    public AgentUserTicketQueryTool(AppSupportTicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    public Map<String, Object> query(Map<String, Object> step) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        long userId = number(input.get("userId"));
        if (userId <= 0) throw new IllegalArgumentException("user.ticket.query 需要有效 userId");
        String status = text(input.get("status"));
        if (status.length() > 32) throw new IllegalArgumentException("工单状态参数过长");
        int limit = Math.max(1, Math.min(50, (int) number(input.getOrDefault("limit", 20))));
        List<AppSupportTicket> tickets = ticketMapper.listByUserId(userId, status.isBlank() ? null : status, limit);
        List<Map<String, Object>> summaries = tickets == null ? List.of() : tickets.stream().map(this::summary).toList();
        return Map.of("userId", userId, "statusFilter", status, "count", summaries.size(), "tickets", summaries);
    }

    private Map<String, Object> summary(AppSupportTicket ticket) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ticketNo", ticket.getTicketNo());
        row.put("ticketType", ticket.getTicketType());
        row.put("subject", ticket.getSubject());
        row.put("status", ticket.getStatus());
        row.put("priority", ticket.getPriority());
        row.put("messageCount", ticket.getMessageCount());
        row.put("orderNo", ticket.getOrderNo());
        row.put("lastMessageAt", ticket.getLastMessageAt());
        row.put("closedAt", ticket.getClosedAt());
        return row;
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("user.ticket.query 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue().isNumber() ? entry.getValue().numberValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("user.ticket.query 输入 JSON 无效", ex);
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
