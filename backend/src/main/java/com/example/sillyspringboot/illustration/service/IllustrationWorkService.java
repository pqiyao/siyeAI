package com.example.sillyspringboot.illustration.service;

import com.example.sillyspringboot.compat.h5.web.CharacterJsonSupport;
import com.example.sillyspringboot.illustration.entity.AppIllustrationWork;
import com.example.sillyspringboot.illustration.mapper.AppIllustrationWorkMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class IllustrationWorkService {

    public static final String LEVEL_NORMAL = "NORMAL";
    public static final String LEVEL_R15 = "R15";
    public static final String LEVEL_R18 = "R18";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_HIDDEN = "HIDDEN";

    private static final Set<String> LEVELS = Set.of(LEVEL_NORMAL, LEVEL_R15, LEVEL_R18);
    private static final Set<String> STATUSES = Set.of(STATUS_DRAFT, STATUS_PENDING, STATUS_PUBLISHED, STATUS_REJECTED, STATUS_HIDDEN);
    private static final Set<String> SOURCES = Set.of("ADMIN", "USER");

    private final AppIllustrationWorkMapper workMapper;
    private final ObjectMapper objectMapper;

    public IllustrationWorkService(AppIllustrationWorkMapper workMapper, ObjectMapper objectMapper) {
        this.workMapper = workMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildMeta() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contentLevels", List.of(
                option(LEVEL_NORMAL, "全年龄"),
                option(LEVEL_R15, "15+"),
                option(LEVEL_R18, "18+")
        ));
        data.put("statuses", List.of(
                option(STATUS_DRAFT, "草稿"),
                option(STATUS_PENDING, "待审核"),
                option(STATUS_PUBLISHED, "已发布"),
                option(STATUS_REJECTED, "已驳回"),
                option(STATUS_HIDDEN, "已隐藏")
        ));
        data.put("sources", List.of(
                option("ADMIN", "后台录入"),
                option("USER", "用户上传")
        ));
        data.put("categories", List.of("全部", "动画", "漫画", "原创", "插画", "壁纸"));
        return data;
    }

    @Transactional(readOnly = true)
    public long countAdminWorks(String keyword, String category, String contentLevel, String status, String source) {
        return workMapper.countAdminList(
                trimToNull(keyword),
                normalizeCategoryFilter(category),
                normalizeLevelOptional(contentLevel),
                normalizeStatusOptional(status),
                normalizeSourceOptional(source)
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminWorks(
            String keyword,
            String category,
            String contentLevel,
            String status,
            String source,
            int pageNum,
            int pageSize
    ) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return workMapper.listAdminPage(
                        trimToNull(keyword),
                        normalizeCategoryFilter(category),
                        normalizeLevelOptional(contentLevel),
                        normalizeStatusOptional(status),
                        normalizeSourceOptional(source),
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::toAdminMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminWork(long id) {
        AppIllustrationWork row = workMapper.findById(id);
        return row == null ? null : toAdminMap(row);
    }

    @Transactional
    public Map<String, Object> submitUserWork(
            Long userId,
            String title,
            String nickname,
            String category,
            Object tags,
            String description,
            String imageUrl
    ) {
        AppIllustrationWork row = new AppIllustrationWork();
        row.setTitle(limit(requiredText(title, "作品标题不能为空"), 160));
        row.setSlug(uniqueSlug(slugOrGenerate(null, row.getTitle())));
        row.setCategory(limit(blankToDefault(category, "插画"), 64));
        row.setTagsJson(serializeTagsValue(tags));
        row.setDescription(limit(trimToNull(description), 5000));
        row.setCoverUrl(limit(requiredText(imageUrl, "图片地址不能为空"), 1024));
        row.setImageUrl(limit(requiredText(imageUrl, "图片地址不能为空"), 1024));
        row.setContentLevel(LEVEL_NORMAL);
        row.setStatus(STATUS_PENDING);
        row.setSource("USER");
        row.setSubmitterUserId(userId);
        row.setSubmitterName(limit(trimToNull(nickname), 120));
        row.setAuditNote(null);
        row.setRecommended(Boolean.FALSE);
        row.setSortOrder(0);
        row.setViewCount(0L);
        row.setLikeCount(0L);
        row.setReviewedAt(null);
        workMapper.insert(row);
        return toAdminMap(workMapper.findById(row.getId()));
    }

    @Transactional
    public Map<String, Object> saveAdminWork(Map<String, Object> body) {
        AppIllustrationWork row = toEntity(body);
        validateForSave(row);

        AppIllustrationWork exists = workMapper.findBySlug(row.getSlug());
        if (row.getId() == null) {
            if (exists != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "作品访问标识已存在");
            }
            workMapper.insert(row);
            return toAdminMap(workMapper.findById(row.getId()));
        }

        if (exists != null && !exists.getId().equals(row.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "作品访问标识已存在");
        }
        AppIllustrationWork old = workMapper.findById(row.getId());
        if (old == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作品不存在");
        }
        workMapper.updateById(row);
        return toAdminMap(workMapper.findById(row.getId()));
    }

    @Transactional
    public int removeAdminWork(long id) {
        return workMapper.hardDeleteById(id);
    }

    @Transactional
    public Map<String, Object> updateAdminStatus(long id, String status, String auditNote) {
        String safeStatus = normalizeStatus(status);
        boolean reviewed = STATUS_PUBLISHED.equals(safeStatus) || STATUS_REJECTED.equals(safeStatus) || STATUS_HIDDEN.equals(safeStatus);
        workMapper.updateStatus(id, safeStatus, limit(trimToNull(auditNote), 500), reviewed);
        AppIllustrationWork row = workMapper.findById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作品不存在");
        }
        return toAdminMap(row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listPublicWorks(String keyword, String category, String tag, boolean allowR18, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(200, pageSize));
        String safeCategory = normalizeCategoryFilter(category);
        String safeTag = trimToNull(tag);
        long total = workMapper.countPublicList(trimToNull(keyword), safeCategory, safeTag, allowR18);
        List<Map<String, Object>> rows = workMapper.listPublicPage(
                        trimToNull(keyword),
                        safeCategory,
                        safeTag,
                        allowR18,
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::toPublicMap)
                .toList();
        return Map.of("total", total, "rows", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicWork(String slug, boolean allowR18) {
        AppIllustrationWork row = workMapper.findActiveBySlug(trimToNull(slug), allowR18);
        return row == null ? null : toPublicMap(row);
    }

    private AppIllustrationWork toEntity(Map<String, Object> body) {
        AppIllustrationWork row = new AppIllustrationWork();
        if (body == null) {
            return row;
        }
        row.setId(longVal(body.get("id")));
        row.setTitle(limit(requiredText(body.get("title"), "作品标题不能为空"), 160));
        row.setSlug(limit(slugOrGenerate(str(body.get("slug")), row.getTitle()), 180));
        row.setCategory(limit(blankToDefault(str(body.get("category")), "原创"), 64));
        row.setTagsJson(serializeTags(body));
        row.setDescription(limit(trimToNull(str(body.get("description"))), 5000));
        row.setCoverUrl(limit(requiredText(body.get("coverUrl"), "封面地址不能为空"), 1024));
        row.setImageUrl(limit(requiredText(body.get("imageUrl"), "原图地址不能为空"), 1024));
        row.setContentLevel(normalizeLevel(str(body.get("contentLevel"))));
        row.setStatus(normalizeStatus(blankToDefault(str(body.get("status")), STATUS_PUBLISHED)));
        row.setSource(normalizeSource(blankToDefault(str(body.get("source")), "ADMIN")));
        row.setSubmitterUserId(longVal(body.get("submitterUserId")));
        row.setSubmitterName(limit(trimToNull(str(body.get("submitterName"))), 120));
        row.setAuditNote(limit(trimToNull(str(body.get("auditNote"))), 500));
        row.setRecommended(Boolean.TRUE.equals(boolVal(body.get("recommended"))));
        row.setSortOrder(intVal(body.get("sortOrder"), 0));
        row.setViewCount(longValDefault(body.get("viewCount"), 0L));
        row.setLikeCount(longValDefault(body.get("likeCount"), 0L));
        row.setReviewedAt(isReviewedStatus(row.getStatus()) ? LocalDateTime.now() : null);
        return row;
    }

    private void validateForSave(AppIllustrationWork row) {
        if (row.getTitle() == null || row.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "作品标题不能为空");
        }
        if (row.getSlug() == null || row.getSlug().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "作品访问标识不能为空");
        }
        if (row.getCoverUrl() == null || row.getCoverUrl().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "封面地址不能为空");
        }
        if (row.getImageUrl() == null || row.getImageUrl().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "原图地址不能为空");
        }
    }

    private Map<String, Object> toAdminMap(AppIllustrationWork row) {
        Map<String, Object> data = toPublicMap(row);
        data.put("status", blankToDefault(row.getStatus(), STATUS_DRAFT));
        data.put("source", blankToDefault(row.getSource(), "ADMIN"));
        data.put("submitterUserId", row.getSubmitterUserId());
        data.put("submitterName", blank(row.getSubmitterName()));
        data.put("auditNote", blank(row.getAuditNote()));
        data.put("viewCount", row.getViewCount() == null ? 0L : row.getViewCount());
        data.put("likeCount", row.getLikeCount() == null ? 0L : row.getLikeCount());
        data.put("reviewedAt", row.getReviewedAt());
        data.put("updatedAt", row.getUpdatedAt());
        return data;
    }

    private Map<String, Object> toPublicMap(AppIllustrationWork row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("title", blank(row.getTitle()));
        data.put("slug", blank(row.getSlug()));
        data.put("category", blank(row.getCategory()));
        data.put("tags", CharacterJsonSupport.parseStringArrayJson(row.getTagsJson()));
        data.put("tagsJson", row.getTagsJson());
        data.put("description", blank(row.getDescription()));
        data.put("coverUrl", blank(row.getCoverUrl()));
        data.put("imageUrl", blank(row.getImageUrl()));
        data.put("contentLevel", blankToDefault(row.getContentLevel(), LEVEL_NORMAL));
        data.put("recommended", Boolean.TRUE.equals(row.getRecommended()));
        data.put("sortOrder", row.getSortOrder() == null ? 0 : row.getSortOrder());
        data.put("createdAt", row.getCreatedAt());
        return data;
    }

    private String serializeTags(Map<String, Object> body) {
        List<String> tags = normalizeTags(body == null ? null : body.get("tags"));
        if (tags.isEmpty()) {
            tags = CharacterJsonSupport.parseStringArrayJson(str(body == null ? null : body.get("tagsJson")));
        }
        return serializeTagList(tags);
    }

    private String serializeTagsValue(Object raw) {
        return serializeTagList(normalizeTags(raw));
    }

    private String serializeTagList(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> normalizeTags(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addTag(out, str(item));
            }
        } else if (raw != null) {
            String value = str(raw);
            if (value != null) {
                for (String part : value.split("[,，\\s]+")) {
                    addTag(out, part);
                }
            }
        }
        return List.copyOf(new LinkedHashSet<>(out));
    }

    private static void addTag(List<String> out, String raw) {
        String value = trimToNull(raw);
        if (value != null && value.length() <= 40) {
            out.add(value);
        }
    }

    private static Map<String, String> option(String value, String label) {
        return Map.of("value", value, "label", label);
    }

    private static boolean isReviewedStatus(String status) {
        return STATUS_PUBLISHED.equals(status) || STATUS_REJECTED.equals(status) || STATUS_HIDDEN.equals(status);
    }

    private static String normalizeLevelOptional(String value) {
        return value == null || value.isBlank() ? null : normalizeLevel(value);
    }

    private static String normalizeLevel(String value) {
        String normalized = blankToDefault(value, LEVEL_NORMAL).trim().toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "作品分级不正确");
        }
        return normalized;
    }

    private static String normalizeStatusOptional(String value) {
        return value == null || value.isBlank() ? null : normalizeStatus(value);
    }

    private static String normalizeStatus(String value) {
        String normalized = blankToDefault(value, STATUS_DRAFT).trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "作品状态不正确");
        }
        return normalized;
    }

    private static String normalizeSourceOptional(String value) {
        return value == null || value.isBlank() ? null : normalizeSource(value);
    }

    private static String normalizeSource(String value) {
        String normalized = blankToDefault(value, "ADMIN").trim().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "作品来源不正确");
        }
        return normalized;
    }

    private static String normalizeCategoryFilter(String value) {
        String safe = trimToNull(value);
        return safe == null || "全部".equals(safe) ? null : safe;
    }

    private static String slugOrGenerate(String raw, String title) {
        String slug = normalizeSlug(raw);
        if (slug != null) {
            return slug;
        }
        slug = normalizeSlug(title);
        if (slug != null) {
            return slug;
        }
        return "work-" + System.currentTimeMillis();
    }

    private String uniqueSlug(String baseSlug) {
        String base = blankToDefault(baseSlug, "work");
        String candidate = base;
        int index = 2;
        while (workMapper.findBySlug(candidate) != null) {
            candidate = base + "-" + index;
            index += 1;
        }
        return candidate;
    }

    private static String normalizeSlug(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? null : normalized;
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

    private static Long longValDefault(Object value, long defaultValue) {
        Long parsed = longVal(value);
        return parsed == null ? defaultValue : parsed;
    }
}
