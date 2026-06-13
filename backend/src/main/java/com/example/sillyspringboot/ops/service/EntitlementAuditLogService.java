package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.ops.entity.AppEntitlementAuditLog;
import com.example.sillyspringboot.ops.mapper.AppEntitlementAuditLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EntitlementAuditLogService {

    public static final String SCOPE_POLICY = "POLICY";
    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_ORDER = "ORDER";
    public static final String SCOPE_IMAGE = "IMAGE";
    public static final String SCOPE_USAGE = "USAGE";

    public static final String ACTION_POLICY_UPDATED = "POLICY_UPDATED";
    public static final String ACTION_USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    public static final String ACTION_USER_SECURITY_UPDATED = "USER_SECURITY_UPDATED";
    public static final String ACTION_PAYMENT_APPLIED = "PAYMENT_APPLIED";
    public static final String ACTION_IMAGE_GENERATED = "IMAGE_GENERATED";
    public static final String ACTION_CHAT_QUOTA_CONSUMED = "CHAT_QUOTA_CONSUMED";
    public static final String ACTION_IMAGE_QUOTA_CONSUMED = "IMAGE_QUOTA_CONSUMED";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppEntitlementAuditLogMapper auditLogMapper;
    private final AppH5ClientUidMapper clientUidMapper;

    public EntitlementAuditLogService(
            AppEntitlementAuditLogMapper auditLogMapper,
            AppH5ClientUidMapper clientUidMapper
    ) {
        this.auditLogMapper = auditLogMapper;
        this.clientUidMapper = clientUidMapper;
    }

    @Transactional
    public void recordPolicyUpdate(Map<String, Object> before, Map<String, Object> after, String operatorName) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", safeMap(before));
        detail.put("after", safeMap(after));
        detail.put("changedKeys", changedKeys(before, after, List.of(
                "guestDailyChatQuota",
                "vipDailyChatQuota",
                "svipDailyChatQuota",
                "guestDailyImageQuota",
                "vipDailyImageQuota",
                "svipDailyImageQuota",
                "guestCharacterCreateLimit",
                "vipCharacterCreateLimit",
                "svipCharacterCreateLimit",
                "guestCanAccessVipCharacters",
                "vipCanAccessVipCharacters",
                "svipCanAccessVipCharacters",
                "continueConsumesQuota",
                "regenerateConsumesQuota"
        )));
        write(
                SCOPE_POLICY,
                ACTION_POLICY_UPDATED,
                "ADMIN",
                blank(operatorName, "admin"),
                null,
                null,
                null,
                "更新全局权益配置",
                detail
        );
    }

    @Transactional
    public void recordUserProfileUpdate(long userId, Map<String, Object> before, Map<String, Object> after, String operatorName) {
        List<String> watched = List.of(
                "nickname",
                "status",
                "vipType",
                "vipExpiresAt",
                "score",
                "goldCoin",
                "dailyChatQuota",
                "chatQuotaOverride",
                "dailyChatUsed",
                "dailyImageQuota",
                "imageQuotaOverride",
                "dailyImageUsed",
                "needEdit"
        );
        List<String> changed = changedKeys(before, after, watched);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", safeMap(before));
        detail.put("after", safeMap(after));
        detail.put("changedKeys", changed);
        write(
                SCOPE_USER,
                ACTION_USER_PROFILE_UPDATED,
                "ADMIN",
                blank(operatorName, "admin"),
                userId,
                resolveClientUid(userId),
                null,
                changed.isEmpty() ? "保存用户权益资料" : "调整用户权益: " + String.join(", ", changed),
                detail
        );
    }

    @Transactional
    public void recordUserSecurityUpdate(long userId, String action, Map<String, Object> detail, String operatorName) {
        write(
                SCOPE_USER,
                ACTION_USER_SECURITY_UPDATED,
                "ADMIN",
                blank(operatorName, "admin"),
                userId,
                resolveClientUid(userId),
                null,
                "账号安全: " + blank(action, "updated"),
                safeMap(detail)
        );
    }

    @Transactional
    public void recordPaymentApplied(
            long userId,
            String orderNo,
            Map<String, Object> beforeProfile,
            Map<String, Object> afterProfile,
            Map<String, Object> benefit
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("beforeProfile", safeMap(beforeProfile));
        detail.put("afterProfile", safeMap(afterProfile));
        detail.put("benefit", safeMap(benefit));
        write(
                SCOPE_ORDER,
                ACTION_PAYMENT_APPLIED,
                "SYSTEM",
                "payment",
                userId,
                resolveClientUid(userId),
                orderNo,
                buildPaymentSummary(benefit),
                detail
        );
    }

    @Transactional
    public void recordImageGenerated(
            long userId,
            String clientUid,
            String prompt,
            int imageCount,
            int remainingQuota,
            String imageUrl
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("promptLength", blank(prompt, "").length());
        detail.put("imageCount", Math.max(1, imageCount));
        detail.put("remainingQuota", Math.max(0, remainingQuota));
        write(
                SCOPE_IMAGE,
                ACTION_IMAGE_GENERATED,
                "USER",
                "h5",
                userId,
                blank(clientUid, resolveClientUid(userId)),
                null,
                "生成图片 " + Math.max(1, imageCount) + " 张，剩余额度 " + Math.max(0, remainingQuota),
                detail
        );
    }

    @Transactional
    public void recordQuotaConsumed(
            long userId,
            String clientUid,
            String quotaBucket,
            int consumeAmount,
            int quota,
            int beforeUsed,
            int afterUsed,
            Long characterId,
            String businessAction
    ) {
        int safeAmount = Math.max(1, consumeAmount);
        int safeQuota = Math.max(0, quota);
        int safeAfterUsed = Math.max(0, afterUsed);
        int remaining = Math.max(0, safeQuota - safeAfterUsed);
        String bucket = blank(quotaBucket, "");
        boolean image = "IMAGE".equalsIgnoreCase(bucket);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("quotaBucket", bucket);
        detail.put("businessAction", blank(businessAction, ""));
        detail.put("consumeAmount", safeAmount);
        detail.put("quota", safeQuota);
        detail.put("beforeUsed", Math.max(0, beforeUsed));
        detail.put("afterUsed", safeAfterUsed);
        detail.put("remaining", remaining);
        detail.put("characterId", characterId);
        detail.put("traceId", traceId());
        write(
                SCOPE_USAGE,
                image ? ACTION_IMAGE_QUOTA_CONSUMED : ACTION_CHAT_QUOTA_CONSUMED,
                "USER",
                "h5",
                userId,
                blank(clientUid, resolveClientUid(userId)),
                null,
                (image ? "生图" : "聊天") + "消耗 " + safeAmount + " 次，剩余 " + remaining,
                detail
        );
    }

    @Transactional(readOnly = true)
    public long countList(String scopeType, String actionType, String keyword, Long targetUserId) {
        return auditLogMapper.countList(
                trimToNull(scopeType),
                trimToNull(actionType),
                trimToNull(keyword),
                positiveId(targetUserId)
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPage(String scopeType, String actionType, String keyword, Long targetUserId, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return auditLogMapper.listPage(
                trimToNull(scopeType),
                trimToNull(actionType),
                trimToNull(keyword),
                positiveId(targetUserId),
                (safePage - 1) * safeSize,
                safeSize
        );
    }

    @Transactional
    public int deleteByIds(String ids) {
        List<Long> parsed = parseIds(ids);
        return parsed.isEmpty() ? 0 : auditLogMapper.deleteByIds(parsed);
    }

    private void write(
            String scopeType,
            String actionType,
            String operatorType,
            String operatorName,
            Long targetUserId,
            String clientUid,
            String orderNo,
            String summary,
            Object detail
    ) {
        AppEntitlementAuditLog row = new AppEntitlementAuditLog();
        row.setScopeType(scopeType);
        row.setActionType(actionType);
        row.setOperatorType(blank(operatorType, "SYSTEM"));
        row.setOperatorName(blank(operatorName, "system"));
        row.setTargetUserId(targetUserId);
        row.setClientUid(trimToNull(clientUid));
        row.setOrderNo(trimToNull(orderNo));
        row.setSummary(blank(summary, ""));
        row.setDetailJson(writeJson(detail));
        auditLogMapper.insert(row);
    }

    private String resolveClientUid(long userId) {
        String clientUid = clientUidMapper.findAnyClientUidByUserId(userId);
        return clientUid == null ? "" : clientUid;
    }

    private String buildPaymentSummary(Map<String, Object> benefit) {
        int score = intVal(benefit == null ? null : benefit.get("scoreAmount"));
        int gold = intVal(benefit == null ? null : benefit.get("goldCoinAmount"));
        int vipDays = intVal(benefit == null ? null : benefit.get("vipDays"));
        return "支付到账: +" + score + " 钻石 / +" + gold + " 金币 / +" + vipDays + " 天 VIP";
    }

    private String trimPrompt(String prompt) {
        String text = blank(prompt, "");
        if (text.length() <= 120) {
            return text;
        }
        return text.substring(0, 120) + "...";
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"json_serialize_failed\"}";
        }
    }

    private List<String> changedKeys(Map<String, Object> before, Map<String, Object> after, List<String> keys) {
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            Object beforeValue = before == null ? null : before.get(key);
            Object afterValue = after == null ? null : after.get(key);
            if (!Objects.equals(normalizeValue(beforeValue), normalizeValue(afterValue))) {
                changed.add(key);
            }
        }
        return changed;
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> safeMap(Map<String, Object> map) {
        return map == null ? Map.of() : new LinkedHashMap<>(map);
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id == null || id.isBlank() ? "" : id;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long positiveId(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private static int intVal(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
