package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.OpenRouterGenerationAdminDto;
import com.example.sillyspringboot.integration.sillytavern.entity.OpenRouterGenerationSettings;
import com.example.sillyspringboot.integration.sillytavern.mapper.OpenRouterGenerationSettingsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class OpenRouterGenerationSettingsService {

    private static final long SINGLETON_ID = 1L;

    private final OpenRouterGenerationSettingsMapper mapper;
    private final SillyTavernProperties properties;

    public OpenRouterGenerationSettingsService(
            OpenRouterGenerationSettingsMapper mapper,
            SillyTavernProperties properties
    ) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public OpenRouterGenerationAdminDto getForAdmin() {
        OpenRouterGenerationSettings row = mapper.findSingleton();
        if (row == null) {
            return defaultsFromProperties();
        }
        return toDto(row);
    }

    public String currentModel() {
        String model = getForAdmin().getDefaultModel();
        return model == null ? "" : model.trim();
    }

    public ResolvedSettings resolveForRuntime() {
        OpenRouterGenerationAdminDto dto = getForAdmin();
        return new ResolvedSettings(
                trimmed(dto.getDefaultModel()),
                nullSafe(dto.getDefaultTemperature(), 0.85d),
                nullSafeInt(dto.getDefaultMaxOutputTokens(), 2048),
                nullSafe(dto.getTopP(), -1d),
                nullSafe(dto.getFrequencyPenalty(), -999d),
                nullSafe(dto.getPresencePenalty(), -999d),
                splitStopSequences(dto.getStopSequences())
        );
    }

    @Transactional
    public void updateFromAdmin(OpenRouterGenerationAdminDto body) {
        OpenRouterGenerationAdminDto normalized = normalize(body);
        OpenRouterGenerationSettings row = mapper.findSingleton();
        if (row == null) {
            row = new OpenRouterGenerationSettings();
            row.setId(SINGLETON_ID);
            apply(row, normalized);
            mapper.insert(row);
            return;
        }
        apply(row, normalized);
        mapper.updateById(row);
    }

    private OpenRouterGenerationAdminDto defaultsFromProperties() {
        OpenRouterGenerationAdminDto dto = new OpenRouterGenerationAdminDto();
        dto.setChatCompletionSource(trimmed(properties.getChatCompletionSource()));
        dto.setDefaultModel(trimmed(properties.getDefaultModel()));
        dto.setDefaultTemperature(0.85d);
        dto.setDefaultMaxOutputTokens(2048);
        dto.setTopP(-1d);
        dto.setFrequencyPenalty(-999d);
        dto.setPresencePenalty(-999d);
        dto.setStopSequences("");
        return dto;
    }

    private static OpenRouterGenerationAdminDto toDto(OpenRouterGenerationSettings row) {
        OpenRouterGenerationAdminDto dto = new OpenRouterGenerationAdminDto();
        dto.setDefaultModel(row.getDefaultModel());
        dto.setDefaultTemperature(row.getDefaultTemperature());
        dto.setDefaultMaxOutputTokens(row.getDefaultMaxOutputTokens());
        dto.setTopP(row.getTopP());
        dto.setFrequencyPenalty(row.getFrequencyPenalty());
        dto.setPresencePenalty(row.getPresencePenalty());
        dto.setStopSequences(row.getStopSequences());
        return dto;
    }

    private static void apply(OpenRouterGenerationSettings row, OpenRouterGenerationAdminDto dto) {
        row.setDefaultModel(dto.getDefaultModel());
        row.setDefaultTemperature(dto.getDefaultTemperature());
        row.setDefaultMaxOutputTokens(dto.getDefaultMaxOutputTokens());
        row.setTopP(dto.getTopP());
        row.setFrequencyPenalty(dto.getFrequencyPenalty());
        row.setPresencePenalty(dto.getPresencePenalty());
        row.setStopSequences(dto.getStopSequences());
    }

    private OpenRouterGenerationAdminDto normalize(OpenRouterGenerationAdminDto body) {
        OpenRouterGenerationAdminDto source = body == null ? new OpenRouterGenerationAdminDto() : body;
        OpenRouterGenerationAdminDto dto = new OpenRouterGenerationAdminDto();
        String chatSource = trimmed(source.getChatCompletionSource());
        if (chatSource.isBlank()) {
            chatSource = trimmed(properties.getChatCompletionSource());
        }
        dto.setChatCompletionSource(chatSource);
        String model = trimmed(source.getDefaultModel());
        if (model.isBlank()) {
            model = trimmed(properties.getDefaultModel());
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("defaultModel cannot be blank");
        }
        if ("openrouter".equalsIgnoreCase(chatSource)
                && (!model.contains("/") || model.contains(":"))) {
            throw new IllegalArgumentException("OpenRouter defaultModel 必须是 provider/model 形式，例如 deepseek/deepseek-chat");
        }
        dto.setDefaultModel(model);

        double temperature = clamp(nullSafe(source.getDefaultTemperature(), 0.85d), 0d, 2d);
        int maxTokens = Math.max(0, Math.min(128000, nullSafeInt(source.getDefaultMaxOutputTokens(), 2048)));
        double topP = normalizeOptionalRange(source.getTopP(), -1d, 0d, 1d);
        double frequencyPenalty = normalizeOptionalRange(source.getFrequencyPenalty(), -999d, -2d, 2d);
        double presencePenalty = normalizeOptionalRange(source.getPresencePenalty(), -999d, -2d, 2d);

        dto.setDefaultTemperature(temperature);
        dto.setDefaultMaxOutputTokens(maxTokens);
        dto.setTopP(topP);
        dto.setFrequencyPenalty(frequencyPenalty);
        dto.setPresencePenalty(presencePenalty);
        dto.setStopSequences(trimmed(source.getStopSequences()));
        return dto;
    }

    private static double normalizeOptionalRange(Double value, double sentinel, double min, double max) {
        if (value == null) {
            return sentinel;
        }
        if (Double.compare(value, sentinel) == 0) {
            return sentinel;
        }
        return clamp(value, min, max);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double nullSafe(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static int nullSafeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> splitStopSequences(String raw) {
        String value = trimmed(raw);
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    public record ResolvedSettings(
            String defaultModel,
            double defaultTemperature,
            int defaultMaxOutputTokens,
            double topP,
            double frequencyPenalty,
            double presencePenalty,
            List<String> stopSequences
    ) {}
}
