package com.example.sillyspringboot.illustration.service;

import com.example.sillyspringboot.compat.h5.web.CharacterJsonSupport;
import com.example.sillyspringboot.illustration.entity.AppIllustrationNotice;
import com.example.sillyspringboot.illustration.mapper.AppIllustrationNoticeMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IllustrationNoticeService {

    private static final Set<String> CATEGORIES = Set.of("review", "update", "rule");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AppIllustrationNoticeMapper noticeMapper;
    private final ObjectMapper objectMapper;

    public IllustrationNoticeService(AppIllustrationNoticeMapper noticeMapper, ObjectMapper objectMapper) {
        this.noticeMapper = noticeMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public long countAdminNotices(String keyword, String category, Boolean enabled) {
        return noticeMapper.countAdminList(trimToNull(keyword), normalizeCategoryOptional(category), enabled);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminNotices(String keyword, String category, Boolean enabled, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return noticeMapper.listAdminPage(
                        trimToNull(keyword),
                        normalizeCategoryOptional(category),
                        enabled,
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::toAdminMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPublicNotices() {
        return noticeMapper.listPublic().stream()
                .map(this::toPublicMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminNotice(long id) {
        AppIllustrationNotice row = noticeMapper.findById(id);
        return row == null ? null : toAdminMap(row);
    }

    @Transactional
    public Map<String, Object> saveAdminNotice(Map<String, Object> body) {
        AppIllustrationNotice row = toEntity(body);
        validate(row);
        if (row.getId() == null) {
            noticeMapper.insert(row);
        } else if (noticeMapper.findById(row.getId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        } else {
            noticeMapper.updateById(row);
        }
        return toAdminMap(noticeMapper.findById(row.getId()));
    }

    @Transactional
    public int removeAdminNotice(long id) {
        return noticeMapper.hardDeleteById(id);
    }

    private AppIllustrationNotice toEntity(Map<String, Object> body) {
        AppIllustrationNotice row = new AppIllustrationNotice();
        if (body == null) {
            return row;
        }
        row.setId(longVal(body.get("id")));
        row.setCategory(normalizeCategory(blankToDefault(str(body.get("category")), "update")));
        row.setTypeLabel(limit(blankToDefault(str(body.get("typeLabel")), defaultTypeLabel(row.getCategory())), 40));
        row.setTitle(limit(requiredText(body.get("title"), "通知标题不能为空"), 160));
        row.setContent(requiredText(body.get("content"), "通知内容不能为空"));
        row.setPointsJson(serializePoints(body.get("points")));
        row.setImportant(Boolean.TRUE.equals(boolVal(body.get("important"))));
        row.setEnabled(!Boolean.FALSE.equals(boolVal(body.get("enabled"))));
        row.setSortOrder(intVal(body.get("sortOrder"), 0));
        return row;
    }

    private void validate(AppIllustrationNotice row) {
        if (row.getCategory() == null || row.getCategory().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "通知分类不能为空");
        }
        if (row.getTitle() == null || row.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "通知标题不能为空");
        }
        if (row.getContent() == null || row.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "通知内容不能为空");
        }
    }

    private Map<String, Object> toAdminMap(AppIllustrationNotice row) {
        Map<String, Object> data = toPublicMap(row);
        data.put("enabled", Boolean.TRUE.equals(row.getEnabled()));
        data.put("pointsJson", row.getPointsJson());
        data.put("updatedAt", row.getUpdatedAt());
        return data;
    }

    private Map<String, Object> toPublicMap(AppIllustrationNotice row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("category", blankToDefault(row.getCategory(), "update"));
        data.put("type", blankToDefault(row.getTypeLabel(), defaultTypeLabel(row.getCategory())));
        data.put("typeLabel", blankToDefault(row.getTypeLabel(), defaultTypeLabel(row.getCategory())));
        data.put("title", blank(row.getTitle()));
        data.put("content", blank(row.getContent()));
        data.put("points", CharacterJsonSupport.parseStringArrayJson(row.getPointsJson()));
        data.put("important", Boolean.TRUE.equals(row.getImportant()));
        data.put("sortOrder", row.getSortOrder() == null ? 0 : row.getSortOrder());
        data.put("createdAt", row.getCreatedAt());
        data.put("date", row.getCreatedAt() == null ? "" : row.getCreatedAt().toLocalDate().format(DATE_FORMATTER));
        return data;
    }

    private String serializePoints(Object raw) {
        List<String> points = normalizePoints(raw);
        try {
            return objectMapper.writeValueAsString(points);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> normalizePoints(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addPoint(out, str(item));
            }
        } else if (raw != null) {
            String value = str(raw);
            if (value != null) {
                for (String line : value.split("\\r?\\n")) {
                    addPoint(out, line);
                }
            }
        }
        return List.copyOf(new LinkedHashSet<>(out));
    }

    private static void addPoint(List<String> out, String raw) {
        String value = trimToNull(raw);
        if (value != null && value.length() <= 160) {
            out.add(value);
        }
    }

    private static String normalizeCategoryOptional(String value) {
        return value == null || value.isBlank() ? null : normalizeCategory(value);
    }

    private static String normalizeCategory(String value) {
        String normalized = blankToDefault(value, "update").trim();
        if (!CATEGORIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "通知分类不正确");
        }
        return normalized;
    }

    private static String defaultTypeLabel(String category) {
        return switch (blankToDefault(category, "update")) {
            case "review" -> "审核";
            case "rule" -> "规则";
            default -> "更新";
        };
    }

    private static String requiredText(Object raw, String message) {
        String value = trimToNull(str(raw));
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToDefault(String value, String defaultValue) {
        String safe = trimToNull(value);
        return safe == null ? defaultValue : safe;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String limit(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean boolVal(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    private static Integer intVal(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Long longVal(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = value == null ? null : String.valueOf(value).trim();
            return s == null || s.isEmpty() ? null : Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }
}
