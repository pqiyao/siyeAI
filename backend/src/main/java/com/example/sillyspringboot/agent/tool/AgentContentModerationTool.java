package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only moderation/quality summary using persisted character review facts. */
@Component
public class AgentContentModerationTool {

    private final AppCharacterMapper characterMapper;

    public AgentContentModerationTool(AppCharacterMapper characterMapper) {
        this.characterMapper = characterMapper;
    }

    public Map<String, Object> review(Map<String, Object> step) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        long characterId = number(input.get("characterId"));
        if (characterId <= 0) throw new IllegalArgumentException("content.moderation.review 需要有效 characterId");
        AppCharacter character = characterMapper.findById(characterId);
        if (character == null) return Map.of("characterId", characterId, "exists", false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("characterId", characterId);
        result.put("exists", true);
        result.put("name", character.getName());
        result.put("ownerUserId", character.getOwnerUserId());
        result.put("reviewStatus", character.getReviewStatus());
        result.put("reviewReason", character.getReviewReason());
        result.put("healthScore", character.getHealthScore());
        result.put("healthIssuesJson", character.getHealthIssuesJson());
        result.put("clientVisible", character.getClientVisible());
        result.put("deleted", character.getDeletedAt() != null);
        String status = character.getReviewStatus() == null ? "UNKNOWN" : character.getReviewStatus();
        result.put("recommendation", "APPROVED".equalsIgnoreCase(status) && !Boolean.TRUE.equals(character.getPrivateCard())
                ? "KEEP_VISIBLE" : "MANUAL_REVIEW");
        return result;
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("content.moderation.review 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isNumber()
                    ? entry.getValue().numberValue() : entry.getValue().isTextual() ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("content.moderation.review 输入 JSON 无效", ex);
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
