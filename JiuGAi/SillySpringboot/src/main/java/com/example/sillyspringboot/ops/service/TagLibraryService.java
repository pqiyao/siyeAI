package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.web.CharacterJsonSupport;
import com.example.sillyspringboot.ops.entity.AppTagLibrary;
import com.example.sillyspringboot.ops.mapper.AppTagLibraryMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TagLibraryService {

    private static final Map<String, TagPreset> PRESET_MAP = buildPresetMap();

    private final AppTagLibraryMapper tagLibraryMapper;
    private final AppCharacterMapper characterMapper;

    public TagLibraryService(AppTagLibraryMapper tagLibraryMapper, AppCharacterMapper characterMapper) {
        this.tagLibraryMapper = tagLibraryMapper;
        this.characterMapper = characterMapper;
    }

    @Transactional(readOnly = true)
    public long countAdminList(String keyword, String category, Boolean enabled) {
        return tagLibraryMapper.countAdminList(blankToNull(keyword), blankToNull(category), enabled);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminPage(String keyword, String category, Boolean enabled, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return tagLibraryMapper.listAdminPage(
                        blankToNull(keyword),
                        blankToNull(category),
                        enabled,
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        AppTagLibrary row = tagLibraryMapper.findById(id);
        return row == null ? null : toMap(row);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEnabledOptions() {
        return tagLibraryMapper.listEnabled().stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDiscoverOptions() {
        return tagLibraryMapper.listEnabled().stream()
                .filter(this::discoverVisible)
                .sorted(
                        Comparator.comparing((AppTagLibrary row) -> !Boolean.TRUE.equals(row.getDiscoverRecommended()))
                                .thenComparing(row -> row.getSortOrder() == null ? 0 : row.getSortOrder())
                                .thenComparing(row -> blank(row.getName())))
                .map(this::toMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> buildDiscoverLabelArray(String tagsJson) {
        return buildLabelArray(CharacterJsonSupport.parseStringArrayJson(tagsJson), DisplaySurface.DISCOVER);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> buildDetailLabelArrayFromJson(String tagsJson) {
        return buildLabelArray(CharacterJsonSupport.parseStringArrayJson(tagsJson), DisplaySurface.DETAIL);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> buildDetailLabelArray(List<String> tags) {
        return buildLabelArray(tags, DisplaySurface.DETAIL);
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        AppTagLibrary row = toEntity(body);
        if (row.getCode() == null || row.getCode().isBlank()) {
            row.setCode(normalizeCode(row.getName()));
        }
        applyPresetIfBlank(row);
        if (row.getCode().isBlank() || row.getName() == null || row.getName().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tag code and tag name cannot be blank");
        }

        AppTagLibrary exists = tagLibraryMapper.findByCode(row.getCode());
        if (row.getId() == null) {
            if (exists != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "Tag code already exists");
            }
            tagLibraryMapper.insert(row);
            return toMap(tagLibraryMapper.findById(row.getId()));
        }

        if (exists != null && !exists.getId().equals(row.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Tag code already exists");
        }
        tagLibraryMapper.updateById(row);
        return toMap(tagLibraryMapper.findById(row.getId()));
    }

    @Transactional
    public void remove(long id) {
        tagLibraryMapper.deleteById(id);
    }

    @Transactional
    public int removeBatch(List<Long> ids) {
        List<Long> normalized = normalizeIds(ids);
        if (normalized.isEmpty()) {
            return 0;
        }
        return tagLibraryMapper.deleteByIds(normalized);
    }

    @Transactional
    public int syncExistingCharacterTags() {
        int created = 0;
        Set<String> unique = new LinkedHashSet<>();
        for (String tagsJson : characterMapper.listAllActiveTagsJson()) {
            unique.addAll(CharacterJsonSupport.parseStringArrayJson(tagsJson));
        }
        for (String raw : unique) {
            created += ensureTagExists(raw);
        }
        return created;
    }

    @Transactional
    public void ensureTagsExist(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String raw : tags) {
            ensureTagExists(raw);
        }
    }

    private int ensureTagExists(String raw) {
        String name = trim(raw);
        if (name.isEmpty()) {
            return 0;
        }
        String code = normalizeCode(name);
        if (tagLibraryMapper.findByCode(code) != null) {
            return 0;
        }

        AppTagLibrary row = new AppTagLibrary();
        row.setCode(code);
        row.setName(name);
        row.setCategory("");
        row.setColor("");
        row.setVipOnly(Boolean.FALSE);
        row.setAdultOnly(Boolean.FALSE);
        row.setEnabled(Boolean.TRUE);
        row.setSortOrder(0);
        applyPresetIfBlank(row);
        tagLibraryMapper.insert(row);
        return 1;
    }

    private AppTagLibrary toEntity(Map<String, Object> body) {
        AppTagLibrary row = new AppTagLibrary();
        if (body == null) {
            return row;
        }
        row.setId(longVal(body.get("id")));
        row.setCode(trim(str(body.get("code"))));
        row.setName(trim(str(body.get("name"))));
        row.setCategory(trim(str(body.get("category"))));
        row.setColor(trim(str(body.get("color"))));
        row.setVipOnly(boolVal(body.get("vipOnly")));
        row.setAdultOnly(boolVal(body.get("adultOnly")));
        row.setEnabled(body.get("enabled") == null || Boolean.TRUE.equals(boolVal(body.get("enabled"))));
        row.setDiscoverVisible(body.get("discoverVisible") == null ? null : boolVal(body.get("discoverVisible")));
        row.setDiscoverRecommended(body.get("discoverRecommended") == null ? null : boolVal(body.get("discoverRecommended")));
        row.setDetailVisible(body.get("detailVisible") == null ? null : boolVal(body.get("detailVisible")));
        row.setSortOrder(intVal(body.get("sortOrder"), 0));
        return row;
    }

    private Map<String, Object> toMap(AppTagLibrary row) {
        TagPreset preset = findPreset(row.getCode(), row.getName());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("code", blank(row.getCode()));
        data.put("name", blank(row.getName()));
        data.put("category", blank(row.getCategory()));
        data.put("color", blank(row.getColor()));
        data.put("vipOnly", Boolean.TRUE.equals(row.getVipOnly()));
        data.put("adultOnly", Boolean.TRUE.equals(row.getAdultOnly()));
        data.put("enabled", row.getEnabled() == null || row.getEnabled());
        data.put("discoverVisible", discoverVisible(row));
        data.put("discoverRecommended", Boolean.TRUE.equals(row.getDiscoverRecommended()));
        data.put("detailVisible", detailVisible(row));
        data.put("sortOrder", row.getSortOrder() == null ? 0 : row.getSortOrder());
        data.put("displayLabel", blank(row.getName()));
        data.put("recommended", Boolean.TRUE.equals(row.getDiscoverRecommended()));
        data.put("presetSuggested", preset != null);
        return data;
    }

    private List<Map<String, String>> buildLabelArray(List<String> rawTags, DisplaySurface surface) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        Map<String, AppTagLibrary> index = buildEnabledTagIndex();
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, String>> labels = new ArrayList<>();
        for (String raw : rawTags) {
            String tagName = trim(raw);
            if (tagName.isEmpty()) {
                continue;
            }
            String normalized = normalizePresetKey(tagName);
            if (!seen.add(normalized)) {
                continue;
            }
            AppTagLibrary row = index.get(normalized);
            if (row != null) {
                if (!surfaceVisible(row, surface)) {
                    continue;
                }
                labels.add(labelData(blankToDefault(row.getName(), tagName), blank(row.getColor()), blank(row.getCategory())));
                continue;
            }
            if (surface == DisplaySurface.DETAIL) {
                labels.add(labelData(tagName, "", ""));
            }
        }
        return List.copyOf(labels);
    }

    private Map<String, AppTagLibrary> buildEnabledTagIndex() {
        Map<String, AppTagLibrary> index = new LinkedHashMap<>();
        for (AppTagLibrary row : tagLibraryMapper.listEnabled()) {
            index.putIfAbsent(normalizePresetKey(row.getCode()), row);
            index.putIfAbsent(normalizePresetKey(row.getName()), row);
        }
        return index;
    }

    private Map<String, String> labelData(String code, String color, String category) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("color", color);
        item.put("category", category);
        return item;
    }

    private void applyPresetIfBlank(AppTagLibrary row) {
        if (row == null) {
            return;
        }
        TagPreset preset = findPreset(row.getCode(), row.getName());
        if (preset != null) {
            if (row.getCategory() == null || row.getCategory().isBlank()) {
                row.setCategory(preset.category());
            }
            if (row.getColor() == null || row.getColor().isBlank()) {
                row.setColor(preset.color());
            }
            if (row.getSortOrder() == null || row.getSortOrder() == 0) {
                row.setSortOrder(preset.sortOrder());
            }
            if (row.getVipOnly() == null) {
                row.setVipOnly(preset.vipOnly());
            }
            if (row.getAdultOnly() == null) {
                row.setAdultOnly(preset.adultOnly());
            }
            if (row.getDiscoverRecommended() == null) {
                row.setDiscoverRecommended(preset.discoverRecommended());
            }
        }
        if (row.getDiscoverVisible() == null) {
            row.setDiscoverVisible(Boolean.TRUE);
        }
        if (row.getDiscoverRecommended() == null) {
            row.setDiscoverRecommended(Boolean.FALSE);
        }
        if (row.getDetailVisible() == null) {
            row.setDetailVisible(Boolean.TRUE);
        }
        if (row.getEnabled() == null) {
            row.setEnabled(Boolean.TRUE);
        }
        if (row.getVipOnly() == null) {
            row.setVipOnly(Boolean.FALSE);
        }
        if (row.getAdultOnly() == null) {
            row.setAdultOnly(Boolean.FALSE);
        }
        if (row.getSortOrder() == null) {
            row.setSortOrder(0);
        }
        if (row.getCategory() == null) {
            row.setCategory("");
        }
        if (row.getColor() == null) {
            row.setColor("");
        }
    }

    private boolean discoverVisible(AppTagLibrary row) {
        return row != null && (row.getDiscoverVisible() == null || row.getDiscoverVisible());
    }

    private boolean detailVisible(AppTagLibrary row) {
        return row != null && (row.getDetailVisible() == null || row.getDetailVisible());
    }

    private boolean surfaceVisible(AppTagLibrary row, DisplaySurface surface) {
        return switch (surface) {
            case DISCOVER -> discoverVisible(row);
            case DETAIL -> detailVisible(row);
        };
    }

    private static TagPreset findPreset(String code, String name) {
        TagPreset preset = PRESET_MAP.get(normalizePresetKey(code));
        if (preset != null) {
            return preset;
        }
        return PRESET_MAP.get(normalizePresetKey(name));
    }

    private static Map<String, TagPreset> buildPresetMap() {
        Map<String, TagPreset> map = new LinkedHashMap<>();
        preset(map, "\u5947\u5e7b", "\u9898\u6750", "#7c3aed", 10, true);
        preset(map, "\u6821\u56ed", "\u573a\u666f", "#2563eb", 20, true);
        preset(map, "\u604b\u7231", "\u5173\u7cfb", "#ec4899", 30, true);
        preset(map, "\u5192\u9669", "\u73a9\u6cd5", "#f97316", 40, true);
        preset(map, "\u65e5\u5e38", "\u98ce\u683c", "#14b8a6", 50, true);
        preset(map, "\u60ac\u7591", "\u9898\u6750", "#475569", 60, true);
        preset(map, "\u79d1\u5e7b", "\u9898\u6750", "#0ea5e9", 70, true);
        preset(map, "\u53e4\u4ee3", "\u80cc\u666f", "#b45309", 80, false);
        preset(map, "\u73b0\u4ee3", "\u80cc\u666f", "#64748b", 90, false);
        preset(map, "\u90fd\u5e02", "\u573a\u666f", "#6366f1", 100, false);
        preset(map, "\u539f\u521b", "\u6765\u6e90", "#8b5cf6", 110, false);
        preset(map, "\u540c\u4eba", "\u6765\u6e90", "#d946ef", 120, false);
        preset(map, "\u6cbb\u6108", "\u98ce\u683c", "#22c55e", 130, false);
        preset(map, "\u7eaf\u7231", "\u5173\u7cfb", "#fb7185", 140, false);
        preset(map, "\u517b\u6210", "\u73a9\u6cd5", "#f59e0b", 150, false);
        preset(map, "\u7fa4\u804a", "\u73a9\u6cd5", "#06b6d4", 160, false);
        preset(map, "\u7fa4\u50cf", "\u73a9\u6cd5", "#10b981", 170, false);
        preset(map, "\u72b6\u6001\u680f", "\u73a9\u6cd5", "#a855f7", 180, false);
        preset(map, "\u8bb0\u5fc6\u4f53", "\u73a9\u6cd5", "#6366f1", 190, false);
        preset(map, "\u4eba\u59bb", "\u6210\u4eba\u5411", "#ef4444", 200, false, true, false);
        preset(map, "NTR", "\u6210\u4eba\u5411", "#dc2626", 210, false, true, false);
        preset(map, "\u5de8\u4e73", "\u6210\u4eba\u5411", "#f43f5e", 220, false, true, false);
        preset(map, "\u9b45\u9b54", "\u5947\u5e7b", "#9333ea", 230, false, true, false);
        return map;
    }

    private static void preset(Map<String, TagPreset> map, String name, String category, String color, int sortOrder, boolean discoverRecommended) {
        preset(map, name, category, color, sortOrder, false, false, discoverRecommended);
    }

    private static void preset(
            Map<String, TagPreset> map,
            String name,
            String category,
            String color,
            int sortOrder,
            boolean vipOnly,
            boolean adultOnly,
            boolean discoverRecommended
    ) {
        map.put(normalizePresetKey(name), new TagPreset(category, color, sortOrder, vipOnly, adultOnly, discoverRecommended));
    }

    private static String normalizeCode(String value) {
        String raw = trim(value).toLowerCase(Locale.ROOT);
        if (raw.length() <= 64) {
            return raw;
        }
        return raw.substring(0, 64);
    }

    private static String normalizePresetKey(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int intVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Boolean boolVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        if ("1".equals(raw) || "yes".equalsIgnoreCase(raw) || "y".equalsIgnoreCase(raw)) {
            return Boolean.TRUE;
        }
        if ("0".equals(raw) || "no".equalsIgnoreCase(raw) || "n".equalsIgnoreCase(raw)) {
            return Boolean.FALSE;
        }
        return Boolean.parseBoolean(raw);
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        return unique.isEmpty() ? List.of() : new ArrayList<>(unique);
    }

    private record TagPreset(
            String category,
            String color,
            int sortOrder,
            boolean vipOnly,
            boolean adultOnly,
            boolean discoverRecommended
    ) {
    }

    private enum DisplaySurface {
        DISCOVER,
        DETAIL
    }
}