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

@Service
public class ChatPresetService {

    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SOURCE_ST_PLATFORM = "ST_PLATFORM";
    private static final String API_OPENAI = "openai";

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
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentPresetId", currentPresetId);
        data.put("platformPresets", presetMapper.listPublicEnabled().stream().map(this::toH5Row).toList());
        return data;
    }

    @Transactional
    public Map<String, Object> bindConversationPreset(long userId, long conversationId, Long presetId) {
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        if (presetId != null && presetId > 0) {
            AppChatPreset preset = presetMapper.findEnabledPublicById(presetId);
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
        AppChatPreset preset = presetMapper.findEnabledPublicById(binding.getChatPresetId());
        if (preset == null || !StringUtils.hasText(preset.getBundleJson())) {
            return null;
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
        row.put("summary", summarizeBundle(preset.getBundleJson()));
        return row;
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
