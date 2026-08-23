package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.support.service.SupportTicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Approved Agent action that delegates ticket replies to the existing support service. */
@Component
public class AgentUserTicketReplyTool {

    private final SupportTicketService supportTicketService;

    public AgentUserTicketReplyTool(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    public Map<String, Object> reply(Map<String, Object> step) {
        Map<String, Object> input = parse(step == null ? null : step.get("inputJson"));
        String ticketNo = text(input.get("ticketNo"));
        String content = text(input.get("content"));
        if (ticketNo.isBlank()) throw new IllegalArgumentException("user.ticket.reply 需要 ticketNo");
        if (content.isBlank() || content.length() > 20_000) {
            throw new IllegalArgumentException("user.ticket.reply 的 content 必须为 1 到 20000 个字符");
        }
        String adminName = text(input.get("adminName"));
        String nextStatus = text(input.get("nextStatus"));
        String nextPriority = text(input.get("nextPriority"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "TICKET_REPLIED");
        result.put("ticketNo", ticketNo);
        result.put("ticket", supportTicketService.replyAsAdmin(
                ticketNo, adminName.isBlank() ? "Agent 客服" : adminName,
                content, nextStatus, nextPriority));
        return result;
    }

    private static Map<String, Object> parse(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("user.ticket.reply 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("user.ticket.reply 输入 JSON 无效", ex);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
