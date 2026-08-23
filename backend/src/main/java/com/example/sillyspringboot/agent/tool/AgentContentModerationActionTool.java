package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.admin.service.AdminJiugaiCharacterService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Approved moderation action delegated to the existing character review service. */
@Component
public class AgentContentModerationActionTool {

    private final AdminJiugaiCharacterService characterService;

    public AgentContentModerationActionTool(AdminJiugaiCharacterService characterService) {
        this.characterService = characterService;
    }

    public Map<String, Object> review(Map<String, Object> step) {
        Map<String, Object> input = parse(step == null ? null : step.get("inputJson"));
        long characterId = number(input.get("characterId"));
        String status = text(input.get("reviewStatus")).toUpperCase();
        String reason = text(input.get("reviewReason"));
        if (characterId <= 0) throw new IllegalArgumentException("content.moderation.action 需要 characterId");
        if (!List.of("APPROVED", "REJECTED", "PENDING").contains(status)) {
            throw new IllegalArgumentException("审核状态必须为 APPROVED、REJECTED 或 PENDING");
        }
        List<AppCharacter> reviewed = characterService.reviewCharacters(
                List.of(characterId), status, reason, "Agent 审核员");
        return Map.of("operation", "MODERATION_UPDATED", "characterId", characterId,
                "reviewStatus", status, "reviewedCount", reviewed == null ? 0 : reviewed.size());
    }

    private static Map<String, Object> parse(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("content.moderation.action 输入必须是 JSON 对象");
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("content.moderation.action 输入 JSON 无效", ex);
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value).trim()); }
        catch (NumberFormatException ex) { return 0L; }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
