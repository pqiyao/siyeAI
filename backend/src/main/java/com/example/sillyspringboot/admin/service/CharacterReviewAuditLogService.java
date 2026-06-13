package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.AppCharacterReviewLog;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterReviewLogMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CharacterReviewAuditLogService {

    private static final String EVENT_MANUAL_REVIEW = "MANUAL_REVIEW";
    private static final String EVENT_AUTO_SCREEN = "AUTO_SCREEN";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppCharacterReviewLogMapper reviewLogMapper;
    private final AppH5ClientUidMapper clientUidMapper;

    public CharacterReviewAuditLogService(
            AppCharacterReviewLogMapper reviewLogMapper,
            AppH5ClientUidMapper clientUidMapper
    ) {
        this.reviewLogMapper = reviewLogMapper;
        this.clientUidMapper = clientUidMapper;
    }

    @Transactional
    public void recordReview(
            AppCharacter character,
            String reviewStatus,
            String reviewReason,
            String operatorName,
            String batchNo
    ) {
        if (character == null || character.getId() == null) {
            return;
        }
        String ownerClientUid = resolveOwnerClientUid(character.getOwnerUserId());
        AppCharacterReviewLog row = new AppCharacterReviewLog();
        row.setCharacterId(character.getId());
        row.setCharacterName(blank(character.getName()));
        row.setOwnerUserId(character.getOwnerUserId());
        row.setOwnerClientUid(ownerClientUid);
        row.setReviewStatus(CharacterReviewStatus.normalize(reviewStatus));
        row.setReviewReason(trimToNull(reviewReason));
        row.setOperatorName(blank(operatorName, "admin"));
        row.setBatchNo(trimToNull(batchNo));
        row.setEventType(EVENT_MANUAL_REVIEW);
        row.setScreeningLevel("NONE");
        row.setScreeningFlags("");
        row.setScreeningHits(0);
        row.setSummary(buildReviewSummary(character, row.getReviewStatus(), ownerClientUid));
        row.setDetailJson(writeJson(buildDetail(character, ownerClientUid, row)));
        reviewLogMapper.insert(row);
    }

    @Transactional
    public void recordAutoScreen(
            AppCharacter character,
            CharacterContentScreeningService.ScreeningResult screening,
            String operatorName
    ) {
        if (character == null || character.getId() == null || screening == null) {
            return;
        }
        String ownerClientUid = resolveOwnerClientUid(character.getOwnerUserId());
        AppCharacterReviewLog row = new AppCharacterReviewLog();
        row.setCharacterId(character.getId());
        row.setCharacterName(blank(character.getName()));
        row.setOwnerUserId(character.getOwnerUserId());
        row.setOwnerClientUid(ownerClientUid);
        row.setReviewStatus(blank(character.getReviewStatus(), CharacterReviewStatus.PENDING));
        row.setReviewReason(null);
        row.setOperatorName(blank(operatorName, "system"));
        row.setBatchNo(null);
        row.setEventType(EVENT_AUTO_SCREEN);
        row.setScreeningLevel(blank(screening.level(), "NONE"));
        row.setScreeningFlags(String.join(",", screening.flags()));
        row.setScreeningHits(screening.hitCount());
        row.setSummary(buildScreeningSummary(character, screening, ownerClientUid));
        row.setDetailJson(writeJson(buildScreeningDetail(character, screening, ownerClientUid, row)));
        reviewLogMapper.insert(row);
    }

    @Transactional(readOnly = true)
    public long countList(String reviewStatus, String keyword) {
        return reviewLogMapper.countList(normalizeFilter(reviewStatus), trimToNull(keyword));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPage(String reviewStatus, String keyword, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return reviewLogMapper.listPage(
                normalizeFilter(reviewStatus),
                trimToNull(keyword),
                (safePage - 1) * safeSize,
                safeSize
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", reviewLogMapper.countAll());
        data.put("pending", reviewLogMapper.countByReviewStatus(CharacterReviewStatus.PENDING));
        data.put("approved", reviewLogMapper.countByReviewStatus(CharacterReviewStatus.APPROVED));
        data.put("rejected", reviewLogMapper.countByReviewStatus(CharacterReviewStatus.REJECTED));
        data.put("autoScreened", reviewLogMapper.countByEventType(EVENT_AUTO_SCREEN));
        data.put("manualReviewed", reviewLogMapper.countByEventType(EVENT_MANUAL_REVIEW));
        data.put("mediumRisk", reviewLogMapper.countByScreeningLevel("MEDIUM"));
        data.put("highRisk", reviewLogMapper.countByScreeningLevel("HIGH"));
        data.put("topFlags", collectTopFlags(reviewLogMapper.listRecentScreeningFlags(500)));
        return data;
    }

    @Transactional
    public int deleteByIds(String ids) {
        List<Long> parsed = parseIds(ids);
        return parsed.isEmpty() ? 0 : reviewLogMapper.deleteByIds(parsed);
    }

    private List<Map<String, Object>> collectTopFlags(List<Map<String, Object>> rows) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String raw = row == null || row.get("screeningFlags") == null ? "" : String.valueOf(row.get("screeningFlags"));
                for (String item : raw.split(",")) {
                    String flag = item == null ? "" : item.trim();
                    if (flag.isEmpty()) {
                        continue;
                    }
                    counter.merge(flag, 1, Integer::sum);
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        counter.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("flag", entry.getKey());
                    row.put("count", entry.getValue());
                    out.add(row);
                });
        return out;
    }

    private Map<String, Object> buildDetail(AppCharacter character, String ownerClientUid, AppCharacterReviewLog row) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("characterId", character.getId());
        detail.put("characterName", blank(character.getName()));
        detail.put("ownerUserId", character.getOwnerUserId());
        detail.put("ownerClientUid", blank(ownerClientUid));
        detail.put("reviewStatus", row.getReviewStatus());
        detail.put("reviewReason", blank(row.getReviewReason()));
        detail.put("operatorName", row.getOperatorName());
        detail.put("batchNo", blank(row.getBatchNo()));
        detail.put("eventType", row.getEventType());
        detail.put("vipOnly", Boolean.TRUE.equals(character.getVipOnly()));
        detail.put("privateCard", Boolean.TRUE.equals(character.getPrivateCard()));
        detail.put("reviewedAt", character.getReviewedAt() == null ? null : character.getReviewedAt().toString());
        detail.put("tagline", blank(character.getTagline()));
        detail.put("tagsJson", blank(character.getTagsJson()));
        return detail;
    }

    private Map<String, Object> buildScreeningDetail(
            AppCharacter character,
            CharacterContentScreeningService.ScreeningResult screening,
            String ownerClientUid,
            AppCharacterReviewLog row
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("characterId", character.getId());
        detail.put("characterName", blank(character.getName()));
        detail.put("ownerUserId", character.getOwnerUserId());
        detail.put("ownerClientUid", blank(ownerClientUid));
        detail.put("reviewStatus", row.getReviewStatus());
        detail.put("eventType", row.getEventType());
        detail.put("screeningLevel", row.getScreeningLevel());
        detail.put("screeningFlags", screening.flags());
        detail.put("screeningHits", screening.hitCount());
        detail.put("screeningSummary", screening.summary());
        detail.put("tagline", blank(character.getTagline()));
        detail.put("tagsJson", blank(character.getTagsJson()));
        return detail;
    }

    private String buildReviewSummary(AppCharacter character, String reviewStatus, String ownerClientUid) {
        String prefix = switch (reviewStatus) {
            case CharacterReviewStatus.REJECTED -> "驳回角色审核";
            case CharacterReviewStatus.PENDING -> "重新设为待审核";
            default -> "通过角色审核";
        };
        String name = blank(character.getName(), "未命名角色卡");
        if (ownerClientUid == null || ownerClientUid.isBlank()) {
            return prefix + "：" + name;
        }
        return prefix + "：" + name + " / " + ownerClientUid;
    }

    private String buildScreeningSummary(
            AppCharacter character,
            CharacterContentScreeningService.ScreeningResult screening,
            String ownerClientUid
    ) {
        String name = blank(character.getName(), "未命名角色卡");
        String prefix = screening.hasRisk() ? "自动初筛命中风险" : "自动初筛通过";
        String base = prefix + "：" + name;
        if (ownerClientUid != null && !ownerClientUid.isBlank()) {
            base += " / " + ownerClientUid;
        }
        if (!screening.summary().isBlank()) {
            base += " / " + screening.summary();
        }
        return base;
    }

    private String resolveOwnerClientUid(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            return "";
        }
        String clientUid = clientUidMapper.findAnyClientUidByUserId(ownerUserId);
        return clientUid == null ? "" : clientUid;
    }

    private String writeJson(Object detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"review_log_serialize_failed\"}";
        }
    }

    private static String normalizeFilter(String reviewStatus) {
        if (reviewStatus == null || reviewStatus.isBlank()) {
            return null;
        }
        String normalized = CharacterReviewStatus.normalize(reviewStatus);
        return CharacterReviewStatus.isValid(normalized) ? normalized : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        List<Long> parsed = new ArrayList<>();
        for (String item : ids.split(",")) {
            try {
                long id = Long.parseLong(item.trim());
                if (id > 0) {
                    parsed.add(id);
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid ids from the path and delete only valid positive ids.
            }
        }
        return parsed;
    }
}
