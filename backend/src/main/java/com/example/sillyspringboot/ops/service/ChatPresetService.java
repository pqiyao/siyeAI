package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.ops.entity.AppChatPreset;
import com.example.sillyspringboot.ops.mapper.AppChatPresetMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatPresetService {

    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_PRIVATE = "PRIVATE";
    private static final String SOURCE_ST_PLATFORM = "ST_PLATFORM";
    private static final String SOURCE_USER_COPY = "USER_COPY";
    private static final String SOURCE_USER_CUSTOM = "USER_CUSTOM";
    private static final String API_OPENAI = "openai";
    private static final int MAX_PRIVATE_PRESETS = 20;
    private static final int LEGACY_MIN_MAX_TOKENS = 64;
    private static final int MIN_EDITABLE_MAX_TOKENS = 800;
    private static final int MAX_MAX_TOKENS = 8192;
    private static final int DEFAULT_PRIVATE_MAX_CONTEXT = 8192;

    private final AppChatPresetMapper presetMapper;
    private final AppConversationMapper conversationMapper;
    private final AppConversationStBindingMapper bindingMapper;
    private final StClient stClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatPresetService(
            AppChatPresetMapper presetMapper,
            AppConversationMapper conversationMapper,
            AppConversationStBindingMapper bindingMapper,
            StClient stClient
    ) {
        this.presetMapper = presetMapper;
        this.conversationMapper = conversationMapper;
        this.bindingMapper = bindingMapper;
        this.stClient = stClient;
    }

    @Transactional
    public Map<String, Object> syncOpenAiPlatformPresetsFromSt() {
        JsonNode envelope = stClient.readStSettingsEnvelope();
        JsonNode names = envelope == null ? null : envelope.path("openai_setting_names");
        JsonNode settings = envelope == null ? null : envelope.path("openai_settings");
        if (names == null || !names.isArray() || settings == null || !settings.isArray()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "ST chat preset payload invalid");
        }
        int imported = 0;
        int skipped = Math.abs(names.size() - settings.size());
        List<String> namesImported = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (names.size() != settings.size()) {
            warnings.add("ST preset names/settings count mismatch: " + names.size() + "/" + settings.size());
        }
        int count = Math.min(names.size(), settings.size());
        LocalDateTime now = LocalDateTime.now();
        presetMapper.markAllPlatformPresetsSourceUnavailable(API_OPENAI);
        for (int i = 0; i < count; i++) {
            String name = names.get(i).asText("").trim();
            String rawJson = settings.get(i).asText("");
            if (!StringUtils.hasText(name) || !StringUtils.hasText(rawJson)) {
                skipped++;
                addSyncWarning(warnings, "ST preset at index " + i + " is missing name or settings");
                continue;
            }
            try {
                JsonNode generation = objectMapper.readTree(rawJson);
                if (!generation.isObject()) {
                    skipped++;
                    addSyncWarning(warnings, "ST preset '" + name + "' settings must be a JSON object");
                    continue;
                }
                AppChatPreset preset = new AppChatPreset();
                preset.setOwnerUserId(null);
                preset.setScope(SCOPE_PUBLIC);
                preset.setSourceType(SOURCE_ST_PLATFORM);
                preset.setApiType(API_OPENAI);
                preset.setSourceName(name);
                preset.setName(name);
                JsonNode effectiveGeneration = "default".equalsIgnoreCase(name.trim())
                        ? normalizeGlobalDefaultGeneration(generation)
                        : generation;
                preset.setDescription(buildDescription(effectiveGeneration));
                preset.setBundleJson(buildBundleJson(effectiveGeneration));
                preset.setEnabled(true);
                preset.setSourceAvailable(true);
                preset.setGlobalDefault("default".equalsIgnoreCase(name.trim()));
                preset.setSortOrder(100 + i);
                preset.setLastSyncedAt(now);
                presetMapper.upsertPlatformPreset(preset);
                imported++;
                namesImported.add(name);
            } catch (Exception ignored) {
                skipped++;
                addSyncWarning(warnings, "ST preset '" + name + "' contains invalid JSON");
            }
        }

        List<Long> unavailableIds = presetMapper.listUnavailablePlatformPresetIds(API_OPENAI);
        for (Long presetId : unavailableIds) {
            if (presetId != null && presetId > 0) {
                bindingMapper.clearChatPresetId(presetId);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("imported", imported);
        data.put("skipped", skipped);
        data.put("unavailable", unavailableIds.size());
        data.put("names", namesImported);
        data.put("warnings", warnings);
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listForH5(long userId, Long conversationId) {
        Long currentPresetId = null;
        if (conversationId != null && conversationId > 0) {
            AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
            if (conversation == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
            }
            AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
            currentPresetId = binding == null ? null : binding.getChatPresetId();
            if (currentPresetId != null && presetMapper.findEnabledAvailableById(currentPresetId, userId) == null) {
                currentPresetId = null;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentPresetId", currentPresetId);
        data.put("platformPresets", presetMapper.listPublicEnabled().stream().map(this::toH5Row).toList());
        data.put("myPresets", presetMapper.listPrivateByOwner(userId).stream().map(this::toH5Row).toList());
        return data;
    }

    @Transactional
    public Map<String, Object> copyPlatformPreset(long userId, long sourcePresetId, String requestedName) {
        AppChatPreset source = presetMapper.findEnabledPublicById(sourcePresetId);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "platform preset not found");
        }
        lockOwnerOrThrow(userId);
        if (presetMapper.countPrivateByOwner(userId) >= MAX_PRIVATE_PRESETS) {
            throw new BusinessException(ErrorCode.CONFLICT, "最多可保存 20 个我的预设");
        }
        PrivateGeneration generation = readSourceGeneration(source.getBundleJson());
        AppChatPreset preset = new AppChatPreset();
        preset.setOwnerUserId(userId);
        preset.setScope(SCOPE_PRIVATE);
        preset.setSourceType(SOURCE_USER_COPY);
        preset.setApiType(API_OPENAI);
        preset.setSourceName("user:" + userId + ":" + UUID.randomUUID());
        preset.setName(normalizeName(requestedName, source.getName() + " 副本"));
        preset.setDescription(privateDescription(generation));
        preset.setBundleJson(writePrivateBundle(generation, SOURCE_USER_COPY));
        preset.setEnabled(true);
        preset.setSourceAvailable(true);
        presetMapper.insertPrivate(preset);
        return toH5Row(preset);
    }

    @Transactional
    public Map<String, Object> createPrivatePreset(long userId, String requestedName) {
        lockOwnerOrThrow(userId);
        if (presetMapper.countPrivateByOwner(userId) >= MAX_PRIVATE_PRESETS) {
            throw new BusinessException(ErrorCode.CONFLICT, "最多可保存 20 个我的预设");
        }
        PrivateGeneration generation = validateEditablePrivateGeneration(
                1.0d, 1.0d, 0.0d, 0.0d, MIN_EDITABLE_MAX_TOKENS, DEFAULT_PRIVATE_MAX_CONTEXT);
        AppChatPreset preset = new AppChatPreset();
        preset.setOwnerUserId(userId);
        preset.setScope(SCOPE_PRIVATE);
        preset.setSourceType(SOURCE_USER_CUSTOM);
        preset.setApiType(API_OPENAI);
        preset.setSourceName("user:" + userId + ":" + UUID.randomUUID());
        preset.setName(normalizeName(requestedName, "我的预设"));
        preset.setDescription(privateDescription(generation));
        preset.setBundleJson(writePrivateBundle(generation, SOURCE_USER_CUSTOM));
        preset.setEnabled(true);
        preset.setSourceAvailable(true);
        presetMapper.insertPrivate(preset);
        return toH5Row(preset);
    }

    @Transactional
    public Map<String, Object> updatePrivatePreset(
            long userId,
            long presetId,
            String name,
            double temperature,
            double topP,
            Double frequencyPenalty,
            Double presencePenalty,
            int maxTokens,
            boolean enabled
    ) {
        lockOwnerOrThrow(userId);
        AppChatPreset existing = presetMapper.findPrivateByIdForOwner(presetId, userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "private preset not found");
        }
        PrivateGeneration storedGeneration = readPrivateGeneration(existing.getBundleJson());
        double effectiveFrequencyPenalty = frequencyPenalty == null
                ? storedGeneration.frequencyPenalty()
                : frequencyPenalty;
        double effectivePresencePenalty = presencePenalty == null
                ? storedGeneration.presencePenalty()
                : presencePenalty;
        int internalMaxContext = normalizedInternalMaxContext(storedGeneration.maxContext(), maxTokens);
        PrivateGeneration generation = validateEditablePrivateGeneration(
                temperature,
                topP,
                effectiveFrequencyPenalty,
                effectivePresencePenalty,
                maxTokens,
                internalMaxContext
        );
        String safeName = normalizeName(name, existing.getName());
        if (presetMapper.updatePrivate(
                presetId,
                userId,
                safeName,
                privateDescription(generation),
                writePrivateBundle(generation, privateSourceType(existing.getSourceType())),
                enabled
        ) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "private preset changed, please retry");
        }
        if (!enabled) {
            bindingMapper.clearChatPresetId(presetId);
        }
        AppChatPreset updated = presetMapper.findPrivateByIdForOwner(presetId, userId);
        return toH5Row(updated);
    }

    @Transactional
    public boolean deletePrivatePreset(long userId, long presetId) {
        lockOwnerOrThrow(userId);
        AppChatPreset existing = presetMapper.findPrivateByIdForOwner(presetId, userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "private preset not found");
        }
        bindingMapper.clearChatPresetId(presetId);
        return presetMapper.deletePrivate(presetId, userId) == 1;
    }

    private void lockOwnerOrThrow(long userId) {
        if (presetMapper.lockOwnerUser(userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
    }

    @Transactional
    public Map<String, Object> bindConversationPreset(long userId, long conversationId, Long presetId) {
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        if (presetId != null && presetId > 0) {
            AppChatPreset preset = presetMapper.findEnabledAvailableById(presetId, userId);
            if (preset == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "preset not available");
            }
        } else {
            presetId = null;
        }
        bindingMapper.updateChatPresetId(conversationId, presetId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId);
        data.put("chatPresetId", presetId);
        return data;
    }

    @Transactional(readOnly = true)
    public String resolveRuntimePresetBundle(AppConversationStBinding binding) {
        if (binding == null || binding.getUserId() == null || binding.getUserId() <= 0) {
            return null;
        }
        AppChatPreset preset = null;
        if (binding.getChatPresetId() != null && binding.getChatPresetId() > 0) {
            preset = presetMapper.findEnabledAvailableById(binding.getChatPresetId(), binding.getUserId());
        }
        if (preset == null) {
            preset = presetMapper.findEnabledGlobalDefault();
        }
        if (preset == null || !StringUtils.hasText(preset.getBundleJson())) {
            return null;
        }
        if (SCOPE_PRIVATE.equalsIgnoreCase(preset.getScope())) {
            try {
                return withRuntimeMetadata(preset, writePrivateBundle(
                        readPrivateGeneration(preset.getBundleJson()),
                        privateSourceType(preset.getSourceType())
                ));
            } catch (Exception ignored) {
                return null;
            }
        }
        return withRuntimeMetadata(preset, preset.getBundleJson());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminList(int pageNum, int pageSize, String keyword, String apiType, Boolean enabled) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safeSize;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String safeApiType = apiType == null ? "" : apiType.trim().toLowerCase(Locale.ROOT);
        long total = presetMapper.countAdmin(safeKeyword, safeApiType, enabled);
        List<Map<String, Object>> rows = presetMapper.listAdmin(safeKeyword, safeApiType, enabled, offset, safeSize)
                .stream()
                .map(this::toAdminRow)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("rows", rows);
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminDetail(long id) {
        AppChatPreset preset = presetMapper.findPublicById(id);
        if (preset == null) {
            return Map.of();
        }
        Map<String, Object> data = toAdminRow(preset);
        data.put("bundleJson", prettyJson(preset.getBundleJson()));
        return data;
    }

    @Transactional
    public boolean updateStatus(long id, boolean enabled) {
        AppChatPreset preset = presetMapper.findPublicById(id);
        if (preset == null) {
            return false;
        }
        if (enabled && Boolean.FALSE.equals(preset.getSourceAvailable())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ST preset source unavailable");
        }
        if (presetMapper.updateStatus(id, enabled) != 1) {
            return false;
        }
        if (!enabled) {
            bindingMapper.clearChatPresetId(id);
        }
        return true;
    }

    @Transactional
    public boolean updateSortOrder(long id, int sortOrder) {
        return presetMapper.updateSortOrder(id, Math.max(0, sortOrder)) > 0;
    }

    @Transactional
    public boolean delete(long id) {
        if (presetMapper.findPublicById(id) == null) {
            return false;
        }
        bindingMapper.clearChatPresetId(id);
        return presetMapper.deleteById(id) == 1;
    }

    private Map<String, Object> toH5Row(AppChatPreset preset) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", preset.getId());
        row.put("name", preset.getName());
        row.put("description", preset.getDescription());
        row.put("apiType", preset.getApiType());
        row.put("sourceType", preset.getSourceType());
        row.put("sortOrder", preset.getSortOrder());
        row.put("scope", preset.getScope());
        row.put("enabled", Boolean.TRUE.equals(preset.getEnabled()));
        row.put("sourceAvailable", !Boolean.FALSE.equals(preset.getSourceAvailable()));
        row.put("globalDefault", Boolean.TRUE.equals(preset.getGlobalDefault()));
        row.put("editable", SCOPE_PRIVATE.equalsIgnoreCase(preset.getScope()));
        row.put("summary", summarizeBundle(preset.getBundleJson()));
        return row;
    }

    private String withRuntimeMetadata(AppChatPreset preset, String bundleJson) {
        try {
            JsonNode parsed = objectMapper.readTree(bundleJson);
            ObjectNode root = parsed != null && parsed.isObject()
                    ? (ObjectNode) parsed
                    : objectMapper.createObjectNode();
            root.put("_effective_preset_id", preset.getId() == null ? 0L : preset.getId());
            root.put("_effective_preset_scope", preset.getScope() == null ? "" : preset.getScope());
            root.put("_effective_preset_name", preset.getName() == null ? "" : preset.getName());
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "preset runtime metadata invalid");
        }
    }

    private PrivateGeneration readPrivateGeneration(String bundleJson) {
        try {
            JsonNode root = objectMapper.readTree(bundleJson);
            JsonNode generation = root != null && root.has("generation") ? root.path("generation") : root;
            double temperature = firstNumber(generation, 1.0d, "temperature", "temp_openai");
            double topP = firstNumber(generation, 1.0d, "top_p", "top_p_openai");
            double frequencyPenalty = firstNumber(generation, 0.0d, "frequency_penalty", "freq_pen_openai");
            double presencePenalty = firstNumber(generation, 0.0d, "presence_penalty", "pres_pen_openai");
            int maxTokens = firstInt(generation, 512, "openai_max_tokens", "max_tokens");
            int maxContext = firstInt(generation, 8192, "openai_max_context", "max_context");
            return validatePrivateGeneration(
                    temperature,
                    topP,
                    frequencyPenalty,
                    presencePenalty,
                    maxTokens,
                    maxContext,
                    LEGACY_MIN_MAX_TOKENS
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "preset parameters invalid");
        }
    }

    private PrivateGeneration readSourceGeneration(String bundleJson) {
        try {
            JsonNode root = objectMapper.readTree(bundleJson);
            JsonNode generation = root != null && root.has("generation") ? root.path("generation") : root;
            double temperature = Math.max(0.0d, Math.min(2.0d,
                    firstNumber(generation, 1.0d, "temperature", "temp_openai")));
            double topP = Math.max(0.01d, Math.min(1.0d,
                    firstNumber(generation, 1.0d, "top_p", "top_p_openai")));
            double frequencyPenalty = Math.max(-2.0d, Math.min(2.0d,
                    firstNumber(generation, 0.0d, "frequency_penalty", "freq_pen_openai")));
            double presencePenalty = Math.max(-2.0d, Math.min(2.0d,
                    firstNumber(generation, 0.0d, "presence_penalty", "pres_pen_openai")));
            int maxTokens = Math.max(MIN_EDITABLE_MAX_TOKENS, Math.min(MAX_MAX_TOKENS,
                    firstInt(generation, MIN_EDITABLE_MAX_TOKENS, "openai_max_tokens", "max_tokens")));
            int maxContext = Math.max(maxTokens + 512, Math.min(131072,
                    firstInt(generation, 8192, "openai_max_context", "max_context")));
            return validateEditablePrivateGeneration(
                    temperature, topP, frequencyPenalty, presencePenalty, maxTokens, maxContext);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "platform preset parameters invalid");
        }
    }

    private PrivateGeneration validateEditablePrivateGeneration(
            double temperature,
            double topP,
            double frequencyPenalty,
            double presencePenalty,
            int maxTokens,
            int maxContext
    ) {
        return validatePrivateGeneration(
                temperature,
                topP,
                frequencyPenalty,
                presencePenalty,
                maxTokens,
                maxContext,
                MIN_EDITABLE_MAX_TOKENS
        );
    }

    private PrivateGeneration validatePrivateGeneration(
            double temperature,
            double topP,
            double frequencyPenalty,
            double presencePenalty,
            int maxTokens,
            int maxContext,
            int minMaxTokens
    ) {
        if (!Double.isFinite(temperature) || temperature < 0.0d || temperature > 2.0d) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "temperature must be between 0 and 2");
        }
        if (!Double.isFinite(topP) || topP < 0.01d || topP > 1.0d) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "topP must be between 0.01 and 1");
        }
        if (!Double.isFinite(frequencyPenalty) || frequencyPenalty < -2.0d || frequencyPenalty > 2.0d) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "frequencyPenalty must be between -2 and 2");
        }
        if (!Double.isFinite(presencePenalty) || presencePenalty < -2.0d || presencePenalty > 2.0d) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "presencePenalty must be between -2 and 2");
        }
        if (maxTokens < minMaxTokens || maxTokens > MAX_MAX_TOKENS) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "maxTokens must be between " + minMaxTokens + " and " + MAX_MAX_TOKENS
            );
        }
        if (maxContext < 2048 || maxContext > 131072 || maxContext < maxTokens + 512) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "maxContext invalid");
        }
        return new PrivateGeneration(
                temperature, topP, frequencyPenalty, presencePenalty, maxTokens, maxContext);
    }

    private static int normalizedInternalMaxContext(int currentMaxContext, int maxTokens) {
        return Math.min(131072, Math.max(Math.max(2048, currentMaxContext), maxTokens + 512));
    }

    private String writePrivateBundle(PrivateGeneration generation, String sourceType) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("source_type", privateSourceType(sourceType));
            ObjectNode values = root.putObject("generation");
            values.put("temperature", generation.temperature());
            values.put("top_p", generation.topP());
            values.put("frequency_penalty", generation.frequencyPenalty());
            values.put("presence_penalty", generation.presencePenalty());
            values.put("openai_max_tokens", generation.maxTokens());
            values.put("openai_max_context", generation.maxContext());
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "preset serialization failed");
        }
    }

    private static String privateSourceType(String sourceType) {
        return SOURCE_USER_CUSTOM.equalsIgnoreCase(sourceType == null ? "" : sourceType)
                ? SOURCE_USER_CUSTOM
                : SOURCE_USER_COPY;
    }

    private static String privateDescription(PrivateGeneration generation) {
        return "temp=" + trimNumber(generation.temperature())
                + " / top_p=" + trimNumber(generation.topP())
                + " / frequency=" + trimNumber(generation.frequencyPenalty())
                + " / presence=" + trimNumber(generation.presencePenalty())
                + " / tokens=" + generation.maxTokens()
                + " / context=" + generation.maxContext();
    }

    private static String normalizeName(String raw, String fallback) {
        String value = StringUtils.hasText(raw) ? raw.trim() : (fallback == null ? "我的预设" : fallback.trim());
        if (value.isBlank()) {
            value = "我的预设";
        }
        return value.length() <= 40 ? value : value.substring(0, 40).trim();
    }

    private static double firstNumber(JsonNode node, double fallback, String... fields) {
        if (node != null) {
            for (String field : fields) {
                JsonNode value = node.get(field);
                if (value != null && !value.isNull()) {
                    double number = value.asDouble(Double.NaN);
                    if (Double.isFinite(number)) return number;
                }
            }
        }
        return fallback;
    }

    private static int firstInt(JsonNode node, int fallback, String... fields) {
        double value = firstNumber(node, fallback, fields);
        return Double.isFinite(value) ? (int) Math.round(value) : fallback;
    }

    private static String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private record PrivateGeneration(
            double temperature,
            double topP,
            double frequencyPenalty,
            double presencePenalty,
            int maxTokens,
            int maxContext
    ) {
    }

    private Map<String, Object> toAdminRow(AppChatPreset preset) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", preset.getId());
        row.put("name", preset.getName());
        row.put("description", preset.getDescription());
        row.put("scope", preset.getScope());
        row.put("sourceType", preset.getSourceType());
        row.put("apiType", preset.getApiType());
        row.put("sourceName", preset.getSourceName());
        row.put("enabled", Boolean.TRUE.equals(preset.getEnabled()));
        row.put("sourceAvailable", !Boolean.FALSE.equals(preset.getSourceAvailable()));
        row.put("sortOrder", preset.getSortOrder());
        row.put("lastSyncedAt", preset.getLastSyncedAt());
        row.put("createdAt", preset.getCreatedAt());
        row.put("updatedAt", preset.getUpdatedAt());
        row.put("summary", summarizeBundle(preset.getBundleJson()));
        return row;
    }

    private String buildBundleJson(JsonNode generation) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("generation", generation);
        root.put("api_type", API_OPENAI);
        root.put("source_type", SOURCE_ST_PLATFORM);
        return objectMapper.writeValueAsString(root);
    }

    private JsonNode normalizeGlobalDefaultGeneration(JsonNode generation) {
        ObjectNode normalized = generation != null && generation.isObject()
                ? ((ObjectNode) generation).deepCopy()
                : objectMapper.createObjectNode();
        normalized.put("openai_max_tokens", 1000);
        normalized.put("openai_max_context", 65536);
        return normalized;
    }

    private String buildDescription(JsonNode generation) {
        String source = text(generation, "chat_completion_source");
        String model = firstNonBlank(
                text(generation, modelFieldForSource(source)),
                text(generation, "openai_model"),
                text(generation, "model")
        );
        String temp = firstNonBlank(text(generation, "temperature"), text(generation, "temp_openai"));
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(source)) {
            parts.add("source=" + source);
        }
        if (StringUtils.hasText(model)) {
            parts.add("model=" + model);
        }
        if (StringUtils.hasText(temp)) {
            parts.add("temp=" + temp);
        }
        return String.join(" / ", parts);
    }

    private Map<String, Object> summarizeBundle(String bundleJson) {
        Map<String, Object> summary = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(bundleJson);
            JsonNode generation = root.path("generation");
            String source = text(generation, "chat_completion_source");
            summary.put("source", source);
            summary.put("model", firstNonBlank(
                    text(generation, modelFieldForSource(source)),
                    text(generation, "openai_model"),
                    text(generation, "model")
            ));
            summary.put("temperature", firstNonBlank(text(generation, "temperature"), text(generation, "temp_openai")));
            summary.put("topP", firstNonBlank(text(generation, "top_p"), text(generation, "top_p_openai")));
            summary.put("frequencyPenalty", firstNonBlank(
                    text(generation, "frequency_penalty"), text(generation, "freq_pen_openai")));
            summary.put("presencePenalty", firstNonBlank(
                    text(generation, "presence_penalty"), text(generation, "pres_pen_openai")));
            summary.put("maxTokens", firstNonBlank(text(generation, "openai_max_tokens"), text(generation, "max_tokens")));
            summary.put("maxContext", firstNonBlank(text(generation, "openai_max_context"), text(generation, "max_context")));
        } catch (Exception ignored) {
            // keep summary empty
        }
        return summary;
    }

    private String prettyJson(String json) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json));
        } catch (Exception ignored) {
            return json;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return "";
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.isTextual() ? value.asText("").trim() : value.asText("").trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static void addSyncWarning(List<String> warnings, String warning) {
        if (warnings.size() < 20 && StringUtils.hasText(warning)) {
            warnings.add(warning);
        }
    }

    private static String modelFieldForSource(String source) {
        return switch (source == null ? "" : source.trim().toLowerCase(Locale.ROOT)) {
            case "claude" -> "claude_model";
            case "openrouter" -> "openrouter_model";
            case "mistralai" -> "mistralai_model";
            case "cohere" -> "cohere_model";
            case "perplexity" -> "perplexity_model";
            case "groq" -> "groq_model";
            case "chutes" -> "chutes_model";
            case "electronhub" -> "electronhub_model";
            case "nanogpt" -> "nanogpt_model";
            case "deepseek" -> "deepseek_model";
            case "aimlapi" -> "aimlapi_model";
            case "xai" -> "xai_model";
            case "pollinations" -> "pollinations_model";
            case "moonshot" -> "moonshot_model";
            case "fireworks" -> "fireworks_model";
            case "cometapi" -> "cometapi_model";
            case "custom" -> "custom_model";
            case "makersuite" -> "google_model";
            case "vertexai" -> "vertexai_model";
            case "zai" -> "zai_model";
            case "siliconflow" -> "siliconflow_model";
            default -> "openai_model";
        };
    }
}
