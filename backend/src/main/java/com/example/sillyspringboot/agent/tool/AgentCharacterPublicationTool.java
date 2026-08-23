package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.service.CharacterPublicProfileService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Publishes Agent-owned drafts and optionally synchronizes them to SillyTavern. */
@Service
public class AgentCharacterPublicationTool {

    private final AppCharacterMapper characterMapper;
    private final CharacterPublicProfileService profileService;
    private final ObjectMapper objectMapper;
    private final StAdapter stAdapter;

    public AgentCharacterPublicationTool(
            AppCharacterMapper characterMapper,
            CharacterPublicProfileService profileService,
            ObjectMapper objectMapper,
            StAdapter stAdapter
    ) {
        this.characterMapper = characterMapper;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
        this.stAdapter = stAdapter;
    }

    @Transactional
    public Map<String, Object> publish(Map<String, Object> step, Map<String, Object> task) {
        List<Long> ids = resolveIds(step, task);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("缺少待发布的 Agent 角色编号");
        }
        List<Map<String, Object>> published = new ArrayList<>();
        for (Long id : ids) {
            AppCharacter row = requireAgentCharacter(id);
            row.setReviewStatus(CharacterReviewStatus.APPROVED);
            row.setReviewReason("Agent 任务发布，已通过人工审批");
            row.setReviewedBy("agent-approval");
            row.setReviewedAt(java.time.LocalDateTime.now());
            row.setPrivateCard(Boolean.FALSE);
            row.setClientVisible(Boolean.TRUE);
            row.setUnlockedDefault(Boolean.TRUE);
            profileService.apply(row);
            characterMapper.updateById(row);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("characterId", row.getId());
            item.put("name", row.getName());
            item.put("reviewStatus", row.getReviewStatus());
            item.put("clientVisible", row.getClientVisible());
            item.put("stSyncStatus", "NOT_REQUESTED");
            published.add(item);
        }
        return result("PUBLISHED", published);
    }

    @Transactional
    public Map<String, Object> syncToSt(Map<String, Object> step, Map<String, Object> task) {
        List<Long> ids = resolveIds(step, task);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("缺少待同步的 Agent 角色编号");
        }
        List<Map<String, Object>> synced = new ArrayList<>();
        for (Long id : ids) {
            AppCharacter row = requireAgentCharacter(id);
            if (!CharacterReviewStatus.APPROVED.equals(row.getReviewStatus()) || !Boolean.TRUE.equals(row.getClientVisible())) {
                throw new IllegalStateException("角色尚未审核发布：" + id);
            }
            String avatar = trim(row.getStAvatarUrl());
            if (avatar.isBlank()) {
                throw new IllegalStateException("角色缺少 SillyTavern 角色文件名：" + id);
            }
            StCharacterDetail detail = toStDetail(row);
            String syncedAvatar = stAdapter.syncCharacterCard(detail, avatar);
            if (!trim(syncedAvatar).isBlank() && !trim(syncedAvatar).equals(avatar)) {
                row.setStAvatarUrl(trim(syncedAvatar));
                characterMapper.updateById(row);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("characterId", row.getId());
            item.put("name", row.getName());
            item.put("stAvatarUrl", trim(syncedAvatar).isBlank() ? avatar : trim(syncedAvatar));
            item.put("stSyncStatus", "SYNCED");
            synced.add(item);
        }
        return result("SYNCED_TO_ST", synced);
    }

    private AppCharacter requireAgentCharacter(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("角色编号无效");
        }
        AppCharacter row = characterMapper.findById(id);
        if (row == null || row.getDeletedAt() != null) {
            throw new IllegalArgumentException("角色不存在：" + id);
        }
        if (row.getOwnerUserId() != null || !Boolean.FALSE.equals(row.getPrivateCard())) {
            throw new IllegalArgumentException("仅 Agent 系统角色草稿支持此操作：" + id);
        }
        return row;
    }

    private List<Long> resolveIds(Map<String, Object> step, Map<String, Object> task) {
        List<Long> ids = new ArrayList<>();
        addIds(ids, parse(step.get("inputJson")));
        if (ids.isEmpty()) addIds(ids, parse(task.get("constraintsJson")));
        if (ids.isEmpty()) {
            Object completed = task.get("completedSteps");
            if (completed instanceof List<?> rows) {
                for (Object item : rows) {
                    if (!(item instanceof Map<?, ?> row) || !"character.create".equals(String.valueOf(row.get("toolName")))) continue;
                    addIds(ids, parse(row.get("outputJson")));
                }
            }
        }
        return ids;
    }

    private void addIds(List<Long> ids, JsonNode node) {
        if (node == null || !node.isObject()) return;
        JsonNode raw = node.get("characterIds");
        if (raw != null && raw.isArray()) raw.forEach(item -> addId(ids, item.asLong(0)));
        addId(ids, node.path("characterId").asLong(0));
    }

    private static void addId(List<Long> ids, long id) {
        if (id > 0 && !ids.contains(id) && ids.size() < 4) ids.add(id);
    }

    private JsonNode parse(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return value instanceof JsonNode node ? node : objectMapper.readTree(String.valueOf(value)); }
        catch (Exception e) { throw new IllegalArgumentException("角色发布输入 JSON 无效", e); }
    }

    private StCharacterDetail toStDetail(AppCharacter row) {
        return new StCharacterDetail(
                trim(row.getName()), trim(row.getStAvatarUrl()), trim(row.getDescription()), trim(row.getScenario()),
                trim(row.getFirstMessage()), trim(row.getPersona()), strings(row.getTagsJson()),
                strings(row.getAlternateGreetingsJson()), trim(row.getMesExample()), trim(row.getSystemPrompt()),
                trim(row.getPostHistoryInstructions()), trim(row.getCreatorNotes()), trim(row.getCreatorName()),
                strings(row.getStWorldNamesJson()), trim(row.getStExtraJson()), trim(row.getStExtraJson())
        );
    }

    private List<String> strings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) return List.of();
            List<String> out = new ArrayList<>();
            node.forEach(item -> { if (item.isValueNode() && !item.asText().isBlank()) out.add(item.asText()); });
            return out;
        } catch (Exception ignored) { return List.of(); }
    }

    private Map<String, Object> result(String operation, List<Map<String, Object>> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operation", operation);
        out.put("characterCount", rows.size());
        out.put("characters", rows);
        out.put("characterIds", rows.stream().map(row -> row.get("characterId")).toList());
        if (!rows.isEmpty()) out.put("characterId", rows.get(0).get("characterId"));
        return out;
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
