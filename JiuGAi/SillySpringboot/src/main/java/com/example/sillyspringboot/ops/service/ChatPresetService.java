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
    private static final String API_OPENAI = "openai";
    private static final int MAX_PRIVATE_PRESETS = 20;

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
        int imported = 0;
        int skipped = 0;
        List<String> namesImported = new ArrayList<>();
        int count = Math.min(names != null && names.isArray() ? names.size() : 0,
                settings != null && settings.isArray() ? settings.size() : 0);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < count; i++) {
            String name = names.get(i).asText("").trim();
            String rawJson = settings.get(i).asText("");
            if (!StringUtils.hasText(name) || !StringUtils.hasText(rawJson)) {
                skipped++;
                continue;
            }
            try {
                JsonNode generation = objectMapper.readTree(rawJson);
                if (!generation.isObject()) {
                    skipped++;
                    continue;
                }
                AppChatPreset preset = new AppChatPreset();
                preset.setOwnerUserId(null);
                preset.setScope(SCOPE_PUBLIC);
                preset.setSourceType(SOURCE_ST_PLATFORM);
                preset.setApiType(API_OPENAI);
                preset.setSourceName(name);
                preset.setName(name);
                preset.setDescription(buildDescription(generation));
                preset.setBundleJson(buildBundleJson(generation));
                preset.setEnabled(true);
                preset.setSortOrder(100 + i);
                preset.setLastSyncedAt(now);
                presetMapper.upsertPlatformPreset(preset);
                imported++;
                namesImported.add(name);
            } catch (Exception ignored) {
                skipped++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("imported", imported);
        data.put("skipped", skipped);
        data.put("names", namesImported);
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
        if (presetMapper.listPrivateByOwner(userId).size() >= MAX_PRIVATE_PRESETS) {
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
        preset.setBundleJson(writePrivateBundle(generation));
        preset.setEnabled(true);
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
            int maxTokens,
            int maxContext,
            boolean enabled
    ) {
        AppChatPreset existing = presetMapper.findPrivateByIdForOwner(presetId, userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "private preset not found");
        }
        PrivateGeneration generation = validatePrivateGeneration(temperature, topP, maxTokens, maxContext);
        String safeName = normalizeName(name, existing.getName());
        if (presetMapper.updatePrivate(
                presetId,
                userId,
                safeName,
                privateDescription(generation),
                writePrivateBundle(generation),
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
        AppChatPreset existing = presetMapper.findPrivateByIdForOwner(presetId, userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "private preset not found");
        }
        bindingMapper.clearChatPresetId(presetId);
        return presetMapper.deletePrivate(presetId, userId) == 1;
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
        if (binding == null || binding.getChatPresetId() == null || binding.getChatPresetId() <= 0) {
            return null;
        }
        if (binding.getUserId() == null || binding.getUserId() <= 0) {
            return null;
        }
        AppChatPreset preset = presetMapper.findEnabledAvailableById(binding.getChatPresetId(), binding.getUserId());
        if (preset == null || !StringUtils.hasText(preset.getBundleJson())) {
            return null;
        }
        if (SCOPE_PRIVATE.equalsIgnoreCase(preset.getScope())) {
            try {
                return writePrivateBundle(readPrivateGeneration(preset.getBundleJson()));
            } catch (Exception ignored) {
                return null;
            }
        }
        return preset.getBundleJson();
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
        AppChatPreset preset = presetMapper.findById(id);
        if (preset == null) {
            return Map.of();
        }
        Map<String, Object> data = toAdminRow(preset);
        data.put("bundleJson", prettyJson(preset.getBundleJson()));
        return data;
    }

    @Transactional
    public boolean updateStatus(long id, boolean enabled) {
        return presetMapper.updateStatus(id, enabled) > 0;
    }

    @Transactional
    public boolean updateSortOrder(long id, int sortOrder) {
        return presetMapper.updateSortOrder(id, Math.max(0, sortOrder)) > 0;
    }

    @Transactional
    public boolean delete(long id) {
        return presetMapper.deleteById(id) > 0;
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
        row.put("editable", SCOPE_PRIVATE.equalsIgnoreCase(preset.getScope()));
        row.put("summary", summarizeBundle(preset.getBundleJson()));
        return row;
    }

    private PrivateGeneration readPrivateGeneration(String bundleJson) {
        try {
            JsonNode root = objectMapper.readTree(bundleJson);
            JsonNode generation = root != null && root.has("generation") ? root.path("generation") : root;
            double temperature = firstNumber(generation, 1.0d, "temperature", "temp_openai");
            double topP = firstNumber(generation, 1.0d, "top_p", "top_p_openai");
            int maxTokens = firstInt(generation, 512, "openai_max_tokens", "max_tokens");
            int maxContext = firstInt(generation, 8192, "openai_max_context", "max_context");
            return validatePrivateGeneration(temperature, topP, maxTokens, maxContext);
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
            int maxTokens = Math.max(64, Math.min(8192,
                    firstInt(generation, 512, "openai_max_tokens", "max_tokens")));
            int maxContext = Math.max(maxTokens + 512, Math.min(131072,
                    firstInt(generation, 8192, "openai_max_context", "max_context")));
            return validatePrivateGeneration(temperature, topP, maxTokens, maxContext);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "platform preset parameters invalid");
        }
    }

    private PrivateGeneration validatePrivateGeneration(double temperature, double topP, int maxTokens, int maxContext) {
        if (!Double.isFinite(temperature) || temperature < 0.0d || temperature > 2.0d) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "temperature must be between 0 and 2");
        }
        if (!Double.isFinite(topP) || topP < 0.01d || topP > 1.0d) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "topP must be between 0.01 and 1");
        }
        if (maxTokens < 64 || maxTokens > 8192) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "maxTokens must be between 64 and 8192");
        }
        if (maxContext < 2048 || maxContext > 131072 || maxContext < maxTokens + 512) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "maxContext invalid");
        }
        return new PrivateGeneration(temperature, topP, maxTokens, maxContext);
    }

    private String writePrivateBundle(PrivateGeneration generation) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("source_type", SOURCE_USER_COPY);
            ObjectNode values = root.putObject("generation");
            values.put("temperature", generation.temperature());
            values.put("top_p", generation.topP());
            values.put("openai_max_tokens", generation.maxTokens());
            values.put("openai_max_context", generation.maxContext());
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "preset serialization failed");
        }
    }

    private static String privateDescription(PrivateGeneration generation) {
        return "temp=" + trimNumber(generation.temperature())
                + " / top_p=" + trimNumber(generation.topP())
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

    private record PrivateGeneration(double temperature, double topP, int maxTokens, int maxContext) {
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
