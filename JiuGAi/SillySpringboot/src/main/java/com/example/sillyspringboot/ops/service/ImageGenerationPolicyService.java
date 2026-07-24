package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import com.example.sillyspringboot.ops.entity.AppCharacterImagePolicy;
import com.example.sillyspringboot.ops.mapper.AppCharacterImagePolicyMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ImageGenerationPolicyService {

    private static final Set<String> MODES = Set.of("free", "balanced", "strong");
    private static final Set<String> REFERENCE_SOURCES = Set.of("latest_generated_first", "avatar_only");
    private static final int MAX_PROMPT_CHARS = 4000;

    private final AppImageGenerationSettingsService settingsService;
    private final AppCharacterImagePolicyMapper policyMapper;
    private final AppCharacterMapper characterMapper;
    private final ObjectMapper objectMapper;

    public ImageGenerationPolicyService(
            AppImageGenerationSettingsService settingsService,
            AppCharacterImagePolicyMapper policyMapper,
            AppCharacterMapper characterMapper,
            ObjectMapper objectMapper
    ) {
        this.settingsService = settingsService;
        this.policyMapper = policyMapper;
        this.characterMapper = characterMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Resolution resolve(long characterId, String engineName, Map<String, Object> payload) {
        AppImageGenerationSettings global = settingsService.getSettings();
        AppCharacter character = characterId > 0 ? characterMapper.findById(characterId) : null;
        AppCharacterImagePolicy override = characterId > 0 ? policyMapper.findByCharacterId(characterId) : null;
        EffectivePolicy policy = effectivePolicy(global, override);
        if (!policy.imageEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色已关闭聊天生图");
        }

        String requestedMode = normalizeMode(value(payload, "referenceMode"));
        if (requestedMode.isBlank()) {
            requestedMode = normalizeMode(value(payload, "consistencyMode"));
        }
        List<String> warnings = new ArrayList<>();
        String mode = requestedMode.isBlank() ? policy.defaultMode() : requestedMode;
        if (!policy.allowedModes().contains(mode)) {
            String fallback = policy.allowedModes().contains(policy.defaultMode())
                    ? policy.defaultMode() : policy.allowedModes().get(0);
            warnings.add("管理员未开放“" + modeLabel(mode) + "”模式，已改用“" + modeLabel(fallback) + "”");
            mode = fallback;
        }

        if (characterId <= 0 || character == null || character.getDeletedAt() != null) {
            if (!"free".equals(mode)) {
                warnings.add("当前会话没有可用角色资料，已按自由文生图处理");
                mode = "free";
            }
        }

        String requestedSource = normalizeReferenceSource(value(payload, "referenceSourceMode"));
        String referenceSource = requestedSource.isBlank() ? policy.defaultReferenceSourceMode() : requestedSource;
        if (!policy.allowedReferenceSourceModes().contains(referenceSource)) {
            referenceSource = policy.defaultReferenceSourceMode();
            warnings.add("参考图来源受管理员策略限制，已改用默认来源");
        }

        String userPrompt = trim(firstNonBlank(value(payload, "userPrompt"), value(payload, "prompt")), 600);
        if (!StringUtils.hasText(userPrompt)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写生图提示词");
        }
        String referenceImageUrl = safe(value(payload, "referenceImageUrl"));
        String referencePolicy = "prompt_first";
        if ("avatar_only".equals(referenceSource)) {
            referenceImageUrl = "";
        }

        if (!"free".equals(mode)) {
            if (!policy.referenceImagesEnabled()) {
                if ("strong".equals(mode)) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前角色策略未开放参考图，不能使用强一致性模式");
                }
                referenceImageUrl = "";
                warnings.add("参考图已被管理员关闭，本次仅使用角色文字设定保持一致性");
            }
            if ("strong".equals(mode)) {
                ensureStrongReferenceAvailable(character, referenceImageUrl, engineName, global);
                referencePolicy = "reference_only";
            } else {
                referencePolicy = policy.referenceImagesEnabled() ? "balanced" : "prompt_first";
            }
        } else {
            referenceImageUrl = "";
        }

        String prompt = buildPrompt(userPrompt, mode, character, payload, policy, global);
        Map<String, Object> resolvedPayload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        resolvedPayload.put("prompt", prompt);
        resolvedPayload.put("userPrompt", userPrompt);
        resolvedPayload.put("characterId", characterId > 0 ? characterId : 0L);
        resolvedPayload.put("characterName", character == null ? "" : safe(character.getName()));
        resolvedPayload.put("referenceImageUrl", referenceImageUrl);
        resolvedPayload.put("referenceMode", mode);
        resolvedPayload.put("referenceSourceMode", referenceSource);
        resolvedPayload.put("referencePolicy", referencePolicy);
        resolvedPayload.put("negativePrompt", policy.negativePrompt());

        return new Resolution(resolvedPayload, mode, referenceSource, referencePolicy, warnings);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> globalAdminSnapshot() {
        AppImageGenerationSettings settings = settingsService.getSettings();
        Map<String, Object> result = new LinkedHashMap<>(settingsService.toMap(settings));
        result.put("modeOptions", List.of(
                option("free", "自由文生图", "只使用用户描述，不注入角色资料或参考图"),
                option("balanced", "平衡一致性", "加入角色视觉设定；参考图不可用时允许降级"),
                option("strong", "强一致性", "必须使用可用参考图和支持参考图的引擎")
        ));
        result.put("referenceSourceOptions", List.of(
                option("latest_generated_first", "最近生成优先", "优先沿用当前角色最近生成的本地图片，再回退角色头像"),
                option("avatar_only", "仅角色头像", "始终使用角色卡头像或立绘作为身份参考")
        ));
        result.put("engineCapabilities", engineCapabilities(settings));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> characterAdminSnapshot(long characterId) {
        AppCharacter character = requireCharacter(characterId);
        AppCharacterImagePolicy override = policyMapper.findByCharacterId(characterId);
        EffectivePolicy effective = effectivePolicy(settingsService.getSettings(), override);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("characterId", characterId);
        data.put("characterName", safe(character.getName()));
        data.put("avatarUrl", firstNonBlank(character.getAvatarUrl(), character.getCoverUrl(), character.getStAvatarUrl()));
        data.put("hasOverride", override != null);
        data.put("override", overrideMap(override));
        data.put("effective", effectiveMap(effective));
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAdminCharacters(int pageNum, int pageSize, String keyword) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        String safeKeyword = trim(keyword, 120);
        long total = policyMapper.countAdminCharacters(safeKeyword);
        List<Map<String, Object>> rows = policyMapper.listAdminCharacters(
                safeKeyword, (safePage - 1) * safeSize, safeSize);
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            long characterId = longValue(firstValue(row, "characterId", "characterid", "CHARACTERID"));
            AppCharacterImagePolicy override = characterId > 0 ? policyMapper.findByCharacterId(characterId) : null;
            item.put("characterId", characterId);
            item.put("hasOverride", override != null);
            item.put("effective", effectiveMap(effectivePolicy(settingsService.getSettings(), override)));
            normalized.add(item);
        }
        return Map.of("total", total, "rows", normalized);
    }

    @Transactional
    public Map<String, Object> saveCharacterOverride(long characterId, Map<String, Object> body) {
        requireCharacter(characterId);
        AppCharacterImagePolicy row = new AppCharacterImagePolicy();
        row.setCharacterId(characterId);
        row.setImageEnabled(nullableBoolean(body, "imageEnabled"));
        row.setDefaultMode(nullableChoice(body, "defaultMode", MODES));
        List<String> allowedModes = nullableList(body, "allowedModes", MODES);
        row.setAllowedModesJson(allowedModes == null ? null : writeJson(allowedModes));
        row.setReferenceSourceMode(nullableChoice(body, "referenceSourceMode", REFERENCE_SOURCES));
        row.setReferenceImagesEnabled(nullableBoolean(body, "referenceImagesEnabled"));
        row.setNegativePrompt(body != null && body.containsKey("negativePrompt")
                ? trimPreserveEmpty(body.get("negativePrompt"), 2000) : null);
        policyMapper.upsert(row);
        return characterAdminSnapshot(characterId);
    }

    @Transactional
    public void deleteCharacterOverride(long characterId) {
        requireCharacter(characterId);
        policyMapper.deleteByCharacterId(characterId);
    }

    private EffectivePolicy effectivePolicy(AppImageGenerationSettings global, AppCharacterImagePolicy override) {
        List<String> globalModes = normalizeList(global.getAllowedConsistencyModes(), MODES, List.of("free", "balanced", "strong"));
        List<String> overrideModes = override == null ? null : readList(override.getAllowedModesJson(), MODES);
        List<String> allowedModes = overrideModes == null ? globalModes : intersection(globalModes, overrideModes);
        if (allowedModes.isEmpty()) {
            allowedModes = List.of(globalModes.get(0));
        }
        String defaultMode = override == null || !StringUtils.hasText(override.getDefaultMode())
                ? normalizeChoice(global.getDefaultConsistencyMode(), allowedModes, allowedModes.get(0))
                : normalizeChoice(override.getDefaultMode(), allowedModes, allowedModes.get(0));
        List<String> referenceSources = normalizeList(
                global.getAllowedReferenceSourceModes(), REFERENCE_SOURCES,
                List.of("latest_generated_first", "avatar_only"));
        String referenceSource = override == null || !StringUtils.hasText(override.getReferenceSourceMode())
                ? normalizeChoice(global.getDefaultReferenceSourceMode(), referenceSources, referenceSources.get(0))
                : normalizeChoice(override.getReferenceSourceMode(), referenceSources, referenceSources.get(0));
        boolean referenceEnabled = global.isReferenceImagesEnabled()
                && (override == null || override.getReferenceImagesEnabled() == null
                || Boolean.TRUE.equals(override.getReferenceImagesEnabled()));
        boolean imageEnabled = override == null || override.getImageEnabled() == null
                || Boolean.TRUE.equals(override.getImageEnabled());
        String negativePrompt = override != null && override.getNegativePrompt() != null
                ? trimPreserveEmpty(override.getNegativePrompt(), 2000)
                : trimPreserveEmpty(global.getNegativePrompt(), 2000);
        return new EffectivePolicy(
                imageEnabled, defaultMode, allowedModes, referenceSource,
                referenceSources, referenceEnabled, negativePrompt);
    }

    private String buildPrompt(
            String userPrompt,
            String mode,
            AppCharacter character,
            Map<String, Object> payload,
            EffectivePolicy policy,
            AppImageGenerationSettings global
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(userPrompt);
        if (!"free".equals(mode) && character != null) {
            lines.add("Character identity: " + trim(character.getName(), 120));
            addContext(lines, "Visual description", character.getDescription(), 900);
            addContext(lines, "Persona appearance cues", character.getPersona(), 700);
            addContext(lines, "Scene setting", character.getScenario(), 500);
            String tags = String.join(", ", mergeTags(character.getPublicTagsJson(), character.getTagsJson()));
            addContext(lines, "Visual tags", tags, 240);
            if (global.isRecentSceneContextEnabled()) {
                addContext(lines, "Recent scene continuity", value(payload, "recentSceneHint"), 420);
            }
            lines.add("Keep the same identity, facial features, hairstyle and signature appearance while following the requested scene.");
        }
        if (!"free".equals(mode) && StringUtils.hasText(policy.negativePrompt())) {
            lines.add("Avoid: " + policy.negativePrompt());
        }
        return trim(String.join("\n", lines), MAX_PROMPT_CHARS);
    }

    private void ensureStrongReferenceAvailable(
            AppCharacter character,
            String referenceImageUrl,
            String engineName,
            AppImageGenerationSettings settings
    ) {
        if (character == null || (!StringUtils.hasText(referenceImageUrl) && !hasCharacterReference(character))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "强一致性模式需要可用的角色头像或最近生成图片");
        }
        String engine = safe(engineName).toLowerCase(Locale.ROOT);
        if ("st_comfy".equals(engine) && !StringUtils.hasText(settings.getReferenceWorkflow())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前 Comfy 工作流未配置参考图能力，不能使用强一致性模式");
        }
        if ("st_sd_webui".equals(engine)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前生图引擎未开放强一致性参考图能力");
        }
    }

    private static boolean hasCharacterReference(AppCharacter character) {
        return character != null && StringUtils.hasText(firstNonBlank(
                character.getStAvatarUrl(), character.getAvatarUrl(), character.getCoverUrl()));
    }

    private List<Map<String, Object>> engineCapabilities(AppImageGenerationSettings settings) {
        return List.of(
                capability("user_openai_compatible", "用户自定义 API", true, "按用户所选模型自动尝试，强一致性不允许静默降级"),
                capability("managed_openai_compatible", "平台统一 API", true, "按后台路由模型自动尝试，强一致性不允许静默降级"),
                capability("st_comfy", "Comfy 工作流", StringUtils.hasText(settings.getReferenceWorkflow()),
                        StringUtils.hasText(settings.getReferenceWorkflow()) ? "已配置参考图工作流" : "缺少参考图工作流"),
                capability("st_sd_webui", "SD WebUI", false, "当前项目未接入可靠的参考图工作流")
        );
    }

    private static Map<String, Object> capability(String engine, String label, boolean reference, String note) {
        return Map.of("engine", engine, "label", label, "referenceSupported", reference, "note", note);
    }

    private static Map<String, Object> option(String value, String label, String description) {
        return Map.of("value", value, "label", label, "description", description);
    }

    private Map<String, Object> overrideMap(AppCharacterImagePolicy row) {
        if (row == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("imageEnabled", row.getImageEnabled());
        data.put("defaultMode", safe(row.getDefaultMode()));
        data.put("allowedModes", readList(row.getAllowedModesJson(), MODES));
        data.put("referenceSourceMode", safe(row.getReferenceSourceMode()));
        data.put("referenceImagesEnabled", row.getReferenceImagesEnabled());
        data.put("negativePrompt", row.getNegativePrompt());
        data.put("updatedAt", row.getUpdatedAt());
        return data;
    }

    private static Map<String, Object> effectiveMap(EffectivePolicy policy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("imageEnabled", policy.imageEnabled());
        data.put("defaultMode", policy.defaultMode());
        data.put("allowedModes", policy.allowedModes());
        data.put("referenceSourceMode", policy.defaultReferenceSourceMode());
        data.put("allowedReferenceSourceModes", policy.allowedReferenceSourceModes());
        data.put("referenceImagesEnabled", policy.referenceImagesEnabled());
        data.put("negativePrompt", policy.negativePrompt());
        return data;
    }

    private AppCharacter requireCharacter(long characterId) {
        AppCharacter character = characterId > 0 ? characterMapper.findById(characterId) : null;
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return character;
    }

    private static void addContext(List<String> lines, String label, Object value, int maxChars) {
        String text = trim(value, maxChars);
        if (StringUtils.hasText(text)) {
            lines.add(label + ": " + text);
        }
    }

    private List<String> mergeTags(String... values) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String value : values) {
            for (String tag : readStringArray(value)) {
                String normalized = trim(tag, 30);
                if (StringUtils.hasText(normalized)) {
                    tags.add(normalized);
                }
                if (tags.size() >= 8) {
                    return new ArrayList<>(tags);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private List<String> readStringArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> readList(String json, Set<String> supported) {
        if (json == null) {
            return null;
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {});
            return normalizeList(values, supported, List.of());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "角色生图策略保存失败");
        }
    }

    private static List<String> intersection(List<String> left, List<String> right) {
        List<String> result = new ArrayList<>();
        for (String value : left) {
            if (right.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<String> normalizeList(List<String> values, Set<String> supported, List<String> fallback) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String item = safe(value).toLowerCase(Locale.ROOT);
                if (supported.contains(item)) {
                    normalized.add(item);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(fallback);
        }
        return new ArrayList<>(normalized);
    }

    private static String normalizeChoice(String value, List<String> allowed, String fallback) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        if (allowed.contains(normalized)) {
            return normalized;
        }
        return allowed.contains(fallback) ? fallback : allowed.get(0);
    }

    private static String normalizeMode(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        if ("reference_only".equals(normalized)) return "strong";
        if ("prompt_first".equals(normalized)) return "free";
        return MODES.contains(normalized) ? normalized : "";
    }

    private static String normalizeReferenceSource(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return REFERENCE_SOURCES.contains(normalized) ? normalized : "";
    }

    private static String modeLabel(String mode) {
        return switch (safe(mode)) {
            case "strong" -> "强一致性";
            case "balanced" -> "平衡一致性";
            default -> "自由文生图";
        };
    }

    private static Boolean nullableBoolean(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null || "".equals(body.get(key))) {
            return null;
        }
        Object value = body.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String nullableChoice(Map<String, Object> body, String key, Set<String> supported) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return null;
        }
        String value = safe(body.get(key)).toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return null;
        }
        if (!supported.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色生图策略选项不合法");
        }
        return value;
    }

    private static List<String> nullableList(Map<String, Object> body, String key, Set<String> supported) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return null;
        }
        if (!(body.get(key) instanceof Iterable<?> values)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色生图可用模式格式不合法");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : values) {
            String value = safe(item).toLowerCase(Locale.ROOT);
            if (supported.contains(value)) {
                result.add(value);
            }
        }
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色至少需要开放一种生图模式");
        }
        return new ArrayList<>(result);
    }

    private static Object firstValue(Map<String, Object> source, String... keys) {
        if (source == null) return null;
        for (String key : keys) {
            if (source.containsKey(key)) return source.get(key);
        }
        return null;
    }

    private static String value(Map<String, Object> source, String key) {
        return source == null ? "" : safe(source.get(key));
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) return Math.max(0L, number.longValue());
        try {
            return Math.max(0L, Long.parseLong(safe(value)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = safe(value);
            if (StringUtils.hasText(text)) return text;
        }
        return "";
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String trim(Object value, int maxChars) {
        String normalized = safe(value).replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\s*\\R\\s*", " ")
                .trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private static String trimPreserveEmpty(Object value, int maxChars) {
        String normalized = safe(value).replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    public record Resolution(
            Map<String, Object> payload,
            String effectiveMode,
            String referenceSourceMode,
            String referencePolicy,
            List<String> warnings
    ) {}

    private record EffectivePolicy(
            boolean imageEnabled,
            String defaultMode,
            List<String> allowedModes,
            String defaultReferenceSourceMode,
            List<String> allowedReferenceSourceModes,
            boolean referenceImagesEnabled,
            String negativePrompt
    ) {}
}
