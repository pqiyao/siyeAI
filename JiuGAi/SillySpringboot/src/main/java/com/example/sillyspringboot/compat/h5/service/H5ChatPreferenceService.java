package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.entity.AppUserChatPreference;
import com.example.sillyspringboot.compat.h5.mapper.AppUserChatPreferenceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class H5ChatPreferenceService {
    static final int MAX_CHARACTER_PREFERENCES_PER_USER = 200;
    private static final int MAX_SECTION_JSON_BYTES = 12_000;
    private static final int MAX_TOTAL_JSON_BYTES = 32_000;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Logger log = LoggerFactory.getLogger(H5ChatPreferenceService.class);

    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "clientSchemaVersion", "expectedRevision", "bubble", "reading", "replyFormat"
    );
    private static final Set<String> BUBBLE_KEYS = Set.of(
            "schemaVersion", "presetVersion", "bubbleCustomized", "customized", "preset", "textColorOverrides",
            "fontSize", "lineHeight", "baseFontWeight", "userFontWeight", "speechFontWeight",
            "actionFontWeight", "thoughtFontWeight", "narrationFontWeight", "thoughtItalic",
            "radius", "opacity", "bubblePaddingY", "bubblePaddingX",
            "charMaxWidth", "userMaxWidth", "imagePadding", "backdropStrength", "surfaceMode",
            "surfaceBorderOpacity", "sideBorderWidth", "sideBorderOpacity", "shadowStrength",
            "blurRadius", "contentTone",
            "charBubbleColor", "userBubbleColor", "charBorderColor", "userBorderColor",
            "baseTextColor", "userTextColor", "speechColor", "actionColor", "thoughtColor", "narrationColor"
    );
    private static final Set<String> V3_BUBBLE_KEYS = Set.of(
            "schemaVersion", "presetVersion", "baseFontWeight", "userFontWeight", "speechFontWeight",
            "actionFontWeight", "thoughtFontWeight", "narrationFontWeight", "thoughtItalic", "surfaceMode",
            "surfaceBorderOpacity", "sideBorderWidth", "sideBorderOpacity", "shadowStrength", "blurRadius",
            "contentTone"
    );
    private static final Set<String> TEXT_COLOR_KEYS = Set.of(
            "baseTextColor", "userTextColor", "speechColor", "actionColor", "thoughtColor", "narrationColor"
    );
    private static final Set<String> COLOR_KEYS = Set.of(
            "charBubbleColor", "userBubbleColor", "charBorderColor", "userBorderColor",
            "baseTextColor", "userTextColor", "speechColor", "actionColor", "thoughtColor", "narrationColor"
    );
    private static final Set<String> PRESETS = Set.of(
            "system", "classic", "fengyue", "night", "soft", "novel", "contrast", "clear", "custom"
    );
    private static final Set<String> SURFACE_MODES = Set.of("flat", "softGradient", "legacyGlass");
    private static final Set<String> CONTENT_TONES = Set.of("auto", "light", "dark");
    private static final Set<String> READING_KEYS = Set.of("readMode", "showSegmentLabels");
    private static final Set<String> READ_MODES = Set.of(
            "original", "novel", "speechOnly", "hideThought", "softAction"
    );
    private static final Set<String> REPLY_FORMAT_KEYS = Set.of("replySplitMode");
    private static final Set<String> REPLY_SPLIT_MODES = Set.of("none", "bubble");

    private final AppUserChatPreferenceMapper mapper;
    private final AppCharacterMapper characterMapper;
    private final ObjectMapper json;

    public H5ChatPreferenceService(
            AppUserChatPreferenceMapper mapper,
            AppCharacterMapper characterMapper,
            ObjectMapper json
    ) {
        this.mapper = mapper;
        this.characterMapper = characterMapper;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> load(long userId, Long requestedCharacterId) {
        long characterId = normalizeCharacterId(requestedCharacterId);
        if (characterId > 0) {
            requireAccessibleCharacter(userId, characterId);
        }
        AppUserChatPreference global = mapper.find(userId, 0L);
        AppUserChatPreference character = characterId == 0 ? null : mapper.find(userId, characterId);
        return response(global, character);
    }

    @Transactional
    public Map<String, Object> save(long userId, Long requestedCharacterId, Map<String, Object> body) {
        long characterId = normalizeCharacterId(requestedCharacterId);
        ValidatedSave request = validate(body);
        if (characterId > 0) {
            requireAccessibleCharacter(userId, characterId);
        }

        Long lockedUserId = mapper.lockUser(userId);
        if (lockedUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }

        AppUserChatPreference current = mapper.find(userId, characterId);
        int currentRevision = current == null || current.getRevision() == null ? 0 : current.getRevision();
        if (request.expectedRevision() != currentRevision) {
            throw revisionConflict();
        }

        boolean empty = request.bubbleJson() == null
                && request.readingJson() == null
                && request.replyFormatJson() == null;
        if (empty) {
            if (current != null
                    && mapper.deleteIfRevision(userId, characterId, request.expectedRevision()) != 1) {
                throw revisionConflict();
            }
            return loadWithinTransaction(userId, characterId);
        }

        String bubbleJson = request.bubbleJson();
        if (request.clientSchemaVersion() < 3 && current != null && bubbleJson != null) {
            bubbleJson = preserveV3BubbleFields(current.getBubbleJson(), bubbleJson);
        }

        AppUserChatPreference row = new AppUserChatPreference();
        row.setUserId(userId);
        row.setCharacterId(characterId);
        row.setBubbleJson(bubbleJson);
        row.setReadingJson(request.readingJson());
        row.setReplyFormatJson(request.replyFormatJson());

        if (current == null) {
            if (characterId > 0
                    && mapper.countCharacterPreferences(userId) >= MAX_CHARACTER_PREFERENCES_PER_USER) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "角色专属聊天设置数量已达上限"
                );
            }
            try {
                if (mapper.insert(row) != 1) {
                    throw revisionConflict();
                }
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "设置已在其他设备创建，请重新加载后再保存",
                        ex
                );
            }
        } else if (mapper.updateIfRevision(row, request.expectedRevision()) != 1) {
            throw revisionConflict();
        }
        return loadWithinTransaction(userId, characterId);
    }

    private Map<String, Object> loadWithinTransaction(long userId, long characterId) {
        AppUserChatPreference global = mapper.find(userId, 0L);
        AppUserChatPreference character = characterId == 0 ? null : mapper.find(userId, characterId);
        return response(global, character);
    }

    private Map<String, Object> response(
            AppUserChatPreference global,
            AppUserChatPreference character
    ) {
        Map<String, Object> globalMap = toMap(global);
        Map<String, Object> characterMap = toMap(character);
        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put("bubble", mergeSection(characterMap, globalMap, "bubble"));
        effective.put("reading", mergeSection(characterMap, globalMap, "reading"));
        effective.put("replyFormat", mergeSection(characterMap, globalMap, "replyFormat"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("global", globalMap);
        result.put("character", characterMap);
        result.put("effective", effective);
        return result;
    }

    private ValidatedSave validate(Map<String, Object> body) {
        if (body == null) {
            throw validation("设置内容不能为空");
        }
        rejectUnknownKeys(body, TOP_LEVEL_KEYS, "设置");
        if (!body.containsKey("expectedRevision")) {
            throw validation("expectedRevision 缺失");
        }
        requireJsonSize(body, MAX_TOTAL_JSON_BYTES, "设置内容过大");
        int expectedRevision = requireInteger(
                body.get("expectedRevision"),
                "expectedRevision",
                0,
                Integer.MAX_VALUE - 1
        );
        int clientSchemaVersion = body.containsKey("clientSchemaVersion")
                ? requireInteger(body.get("clientSchemaVersion"), "clientSchemaVersion", 1, 100)
                : 2;
        return new ValidatedSave(
                clientSchemaVersion,
                expectedRevision,
                sectionJson(body.get("bubble"), Section.BUBBLE),
                sectionJson(body.get("reading"), Section.READING),
                sectionJson(body.get("replyFormat"), Section.REPLY_FORMAT)
        );
    }

    private String sectionJson(Object value, Section section) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw validation(section.label + "格式不正确");
        }
        if (raw.isEmpty()) {
            throw validation(section.label + "不能为空；跟随全局请传 null");
        }
        rejectUnknownKeys(raw, section.allowedKeys, section.label);
        switch (section) {
            case BUBBLE -> validateBubble(raw);
            case READING -> validateReading(raw);
            case REPLY_FORMAT -> validateReplyFormat(raw);
        }
        requireJsonSize(raw, MAX_SECTION_JSON_BYTES, section.label + "内容过大");
        try {
            return json.writeValueAsString(raw);
        } catch (Exception ex) {
            throw validation(section.label + "格式不正确");
        }
    }

    private void validateBubble(Map<?, ?> raw) {
        optionalInteger(raw, "schemaVersion", 1, 100);
        optionalInteger(raw, "presetVersion", 1, 100);
        optionalBoolean(raw, "bubbleCustomized");
        optionalBoolean(raw, "customized");
        if (raw.containsKey("bubbleCustomized") && raw.containsKey("customized")
                && !raw.get("bubbleCustomized").equals(raw.get("customized"))) {
            throw validation("bubbleCustomized 与 customized 必须一致");
        }
        optionalEnum(raw, "preset", PRESETS);
        optionalInteger(raw, "fontSize", 24, 36);
        optionalDecimal(raw, "lineHeight", 1.35, 2.1);
        optionalFontWeight(raw, "baseFontWeight");
        optionalFontWeight(raw, "userFontWeight");
        optionalFontWeight(raw, "speechFontWeight");
        optionalFontWeight(raw, "actionFontWeight");
        optionalFontWeight(raw, "thoughtFontWeight");
        optionalFontWeight(raw, "narrationFontWeight");
        optionalBoolean(raw, "thoughtItalic");
        optionalInteger(raw, "radius", 8, 32);
        optionalInteger(raw, "opacity", 30, 96);
        optionalInteger(raw, "bubblePaddingY", 8, 26);
        optionalInteger(raw, "bubblePaddingX", 12, 34);
        optionalInteger(raw, "charMaxWidth", 62, 92);
        optionalInteger(raw, "userMaxWidth", 58, 88);
        optionalInteger(raw, "imagePadding", 0, 18);
        optionalInteger(raw, "backdropStrength", 0, 55);
        optionalEnum(raw, "surfaceMode", SURFACE_MODES);
        optionalInteger(raw, "surfaceBorderOpacity", 0, 60);
        optionalInteger(raw, "sideBorderWidth", 0, 6);
        optionalInteger(raw, "sideBorderOpacity", 0, 100);
        optionalInteger(raw, "shadowStrength", 0, 100);
        optionalInteger(raw, "blurRadius", 0, 16);
        optionalEnum(raw, "contentTone", CONTENT_TONES);
        for (String key : COLOR_KEYS) {
            optionalColor(raw, key);
        }
        if (raw.containsKey("textColorOverrides")) {
            Object value = raw.get("textColorOverrides");
            if (!(value instanceof Map<?, ?> overrides)) {
                throw validation("textColorOverrides 格式不正确");
            }
            rejectUnknownKeys(overrides, TEXT_COLOR_KEYS, "textColorOverrides");
            for (Object key : overrides.keySet()) {
                requireColor(overrides.get(key), "textColorOverrides." + key);
            }
        }
    }

    private void validateReading(Map<?, ?> raw) {
        optionalEnum(raw, "readMode", READ_MODES);
        optionalBoolean(raw, "showSegmentLabels");
    }

    private void validateReplyFormat(Map<?, ?> raw) {
        optionalEnum(raw, "replySplitMode", REPLY_SPLIT_MODES);
    }

    private void rejectUnknownKeys(Map<?, ?> raw, Set<String> allowed, String label) {
        for (Object key : raw.keySet()) {
            if (!(key instanceof String text) || !allowed.contains(text)) {
                throw validation(label + "包含未知字段: " + String.valueOf(key));
            }
        }
    }

    private void optionalBoolean(Map<?, ?> raw, String key) {
        if (raw.containsKey(key) && !(raw.get(key) instanceof Boolean)) {
            throw validation(key + "必须是布尔值");
        }
    }

    private void optionalEnum(Map<?, ?> raw, String key, Set<String> allowed) {
        if (!raw.containsKey(key)) {
            return;
        }
        Object value = raw.get(key);
        if (!(value instanceof String text) || !allowed.contains(text)) {
            throw validation(key + "取值不正确");
        }
    }

    private void optionalInteger(Map<?, ?> raw, String key, int min, int max) {
        if (raw.containsKey(key)) {
            requireInteger(raw.get(key), key, min, max);
        }
    }

    private void optionalFontWeight(Map<?, ?> raw, String key) {
        if (!raw.containsKey(key)) {
            return;
        }
        int value = requireInteger(raw.get(key), key, 300, 700);
        if (value != 540 && value != 560 && value % 100 != 0) {
            throw validation(key + "必须使用标准字重");
        }
    }

    private void optionalDecimal(Map<?, ?> raw, String key, double min, double max) {
        if (!raw.containsKey(key)) {
            return;
        }
        BigDecimal value = requireNumber(raw.get(key), key);
        if (value.compareTo(BigDecimal.valueOf(min)) < 0
                || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw validation(key + "超出允许范围");
        }
    }

    private int requireInteger(Object raw, String key, int min, int max) {
        BigDecimal value = requireNumber(raw, key);
        try {
            int result = value.intValueExact();
            if (result < min || result > max) {
                throw validation(key + "超出允许范围");
            }
            return result;
        } catch (ArithmeticException ex) {
            throw validation(key + "必须是整数");
        }
    }

    private BigDecimal requireNumber(Object raw, String key) {
        if (!(raw instanceof Number)) {
            throw validation(key + "必须是数字");
        }
        try {
            return new BigDecimal(raw.toString());
        } catch (NumberFormatException ex) {
            throw validation(key + "必须是有限数字");
        }
    }

    private void optionalColor(Map<?, ?> raw, String key) {
        if (raw.containsKey(key)) {
            requireColor(raw.get(key), key);
        }
    }

    private void requireColor(Object raw, String key) {
        if (!(raw instanceof String text) || !text.matches("^#[0-9a-fA-F]{6}$")) {
            throw validation(key + "必须是 #RRGGBB 颜色");
        }
    }

    private void requireJsonSize(Object value, int maximum, String message) {
        try {
            if (json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8).length > maximum) {
                throw validation(message);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw validation("设置格式不正确");
        }
    }

    private String preserveV3BubbleFields(String currentJson, String incomingJson) {
        if (currentJson == null || currentJson.isBlank() || incomingJson == null || incomingJson.isBlank()) {
            return incomingJson;
        }
        try {
            Map<String, Object> current = json.readValue(currentJson, MAP_TYPE);
            Map<String, Object> incoming = new LinkedHashMap<>(json.readValue(incomingJson, MAP_TYPE));
            for (String key : V3_BUBBLE_KEYS) {
                if (!incoming.containsKey(key) && current.containsKey(key)) {
                    incoming.put(key, current.get(key));
                }
            }
            requireJsonSize(incoming, MAX_SECTION_JSON_BYTES, "气泡设置内容过大");
            return json.writeValueAsString(incoming);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw validation("气泡设置格式不正确");
        }
    }

    private long normalizeCharacterId(Long characterId) {
        if (characterId == null) {
            return 0L;
        }
        if (characterId < 0) {
            throw validation("characterId 不能为负数");
        }
        return characterId;
    }

    private void requireAccessibleCharacter(long userId, long characterId) {
        AppCharacter character = characterMapper.findById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        Long ownerId = character.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(character.getPrivateCard())) {
            if (ownerId == null || ownerId.longValue() != userId) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
            }
            return;
        }
        if (Boolean.FALSE.equals(character.getClientVisible())
                || !CharacterReviewStatus.APPROVED.equals(
                        CharacterReviewStatus.normalize(character.getReviewStatus()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
    }

    private Map<String, Object> toMap(AppUserChatPreference row) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (row == null) {
            out.put("revision", 0);
            return out;
        }
        putSection(out, "bubble", row.getBubbleJson(), row);
        putSection(out, "reading", row.getReadingJson(), row);
        putSection(out, "replyFormat", row.getReplyFormatJson(), row);
        out.put("revision", row.getRevision() == null ? 0 : row.getRevision());
        return out;
    }

    private void putSection(
            Map<String, Object> target,
            String key,
            String raw,
            AppUserChatPreference row
    ) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            target.put(key, json.readValue(raw, MAP_TYPE));
        } catch (Exception ex) {
            log.warn(
                    "ignore invalid stored chat preference userId={} characterId={} section={}",
                    row.getUserId(),
                    row.getCharacterId(),
                    key
            );
        }
    }

    private Map<String, Object> mergeSection(
            Map<String, Object> character,
            Map<String, Object> global,
            String key
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Object globalValue = global.get(key);
        if (globalValue instanceof Map<?, ?> globalSection) {
            copyStringKeys(merged, globalSection);
        }
        Object characterValue = character.get(key);
        if (characterValue instanceof Map<?, ?> characterSection) {
            copyStringKeys(merged, characterSection);
        }
        return merged;
    }

    private void copyStringKeys(Map<String, Object> target, Map<?, ?> source) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) {
                target.put(key, entry.getValue());
            }
        }
    }

    private BusinessException revisionConflict() {
        return new BusinessException(
                ErrorCode.CONFLICT,
                "设置已在其他设备更新，请重新加载后再保存"
        );
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    private enum Section {
        BUBBLE("气泡设置", BUBBLE_KEYS),
        READING("阅读设置", READING_KEYS),
        REPLY_FORMAT("回复格式设置", REPLY_FORMAT_KEYS);

        private final String label;
        private final Set<String> allowedKeys;

        Section(String label, Set<String> allowedKeys) {
            this.label = label;
            this.allowedKeys = allowedKeys;
        }
    }

    private record ValidatedSave(
            int clientSchemaVersion,
            int expectedRevision,
            String bubbleJson,
            String readingJson,
            String replyFormatJson
    ) {}
}
