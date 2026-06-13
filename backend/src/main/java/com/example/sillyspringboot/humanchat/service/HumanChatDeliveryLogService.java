package com.example.sillyspringboot.humanchat.service;

import com.example.sillyspringboot.humanchat.entity.HumanChatDeliveryLog;
import com.example.sillyspringboot.humanchat.mapper.HumanChatDeliveryLogMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HumanChatDeliveryLogService {

    private final HumanChatDeliveryLogMapper mapper;
    private final ObjectMapper objectMapper;

    public HumanChatDeliveryLogService(HumanChatDeliveryLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(
            Long messageId,
            String conversationKey,
            Long targetUserId,
            String channel,
            String eventType,
            String status,
            Object requestPayload,
            Object responsePayload
    ) {
        HumanChatDeliveryLog log = new HumanChatDeliveryLog();
        log.setMessageId(messageId);
        log.setConversationKey(blankToNull(conversationKey));
        log.setTargetUserId(targetUserId);
        log.setChannel(blankToDefault(channel, "local_ws"));
        log.setEventType(blankToDefault(eventType, "unknown"));
        log.setStatus(blankToDefault(status, "unknown"));
        log.setRequestPayloadJson(writeJson(requestPayload));
        log.setResponsePayloadJson(writeJson(responsePayload));
        mapper.insert(log);
    }

    @Transactional(readOnly = true)
    public long countAdminDeliveryLogs(String keyword, String eventType, String status, Long targetUserId, Long messageId) {
        return mapper.countAdminDeliveryLogs(
                blankToNull(keyword),
                blankToNull(eventType),
                normalizeStatusOptional(status),
                targetUserId,
                messageId
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminDeliveryLogs(
            String keyword,
            String eventType,
            String status,
            Long targetUserId,
            Long messageId,
            int pageNum,
            int pageSize
    ) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return mapper.listAdminDeliveryLogs(
                        blankToNull(keyword),
                        blankToNull(eventType),
                        normalizeStatusOptional(status),
                        targetUserId,
                        messageId,
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::normalizeRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminDeliveryLog(long id) {
        Map<String, Object> row = mapper.findAdminDeliveryLog(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "投递日志不存在");
        }
        return normalizeRow(row);
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("requestPayload", parseJson(row.get("requestPayload")));
        out.put("responsePayload", parseJson(row.get("responsePayload")));
        normalizeTimeFields(out, "createdAt");
        return out;
    }

    private Object parseJson(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> || raw instanceof List<?>) {
            return raw;
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "投递日志序列化失败", e);
        }
    }

    private static void normalizeTimeFields(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof LocalDateTime localDateTime) {
                map.put(key, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime));
            } else if (value instanceof OffsetDateTime offsetDateTime) {
                map.put(key, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offsetDateTime));
            } else if (value instanceof java.util.Date date) {
                map.put(key, date.toInstant().toString());
            }
        }
    }

    private static String normalizeStatusOptional(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("success".equals(normalized) || "offline".equals(normalized) || "failed".equals(normalized) || "partial".equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "投递日志状态不支持");
    }

    private static String blankToDefault(String value, String defaultValue) {
        String safe = blankToNull(value);
        return safe == null ? defaultValue : safe;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
