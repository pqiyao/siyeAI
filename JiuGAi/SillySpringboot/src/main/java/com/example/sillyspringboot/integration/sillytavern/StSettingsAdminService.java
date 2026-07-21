package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.OpenRouterGenerationAdminDto;
import org.springframework.stereotype.Service;

@Service
public class StSettingsAdminService {

    private final StClient stClient;
    private final OpenRouterGenerationSettingsService fallbackSettingsService;
    private final SillyTavernProperties properties;

    public StSettingsAdminService(
            StClient stClient,
            OpenRouterGenerationSettingsService fallbackSettingsService,
            SillyTavernProperties properties
    ) {
        this.stClient = stClient;
        this.fallbackSettingsService = fallbackSettingsService;
        this.properties = properties;
    }

    public OpenRouterGenerationAdminDto getForAdmin() {
        OpenRouterGenerationAdminDto fallback = fallbackSettingsService.getForAdmin();
        if (isBlank(fallback.getChatCompletionSource())) {
            fallback.setChatCompletionSource(trimmed(properties.getChatCompletionSource()));
        }
        fallback.setStLinked(Boolean.FALSE);
        fallback.setStError("未能读取 ST settings，当前展示的是本地兜底参数。");
        try {
            OpenRouterGenerationAdminDto fromSt = stClient.getGenerationSettingsForAdmin();
            if (isBlank(fromSt.getStopSequences())) {
                fromSt.setStopSequences(fallback.getStopSequences());
            }
            if (isBlank(fromSt.getChatCompletionSource())) {
                fromSt.setChatCompletionSource(fallback.getChatCompletionSource());
            }
            fromSt.setStLinked(Boolean.TRUE);
            fromSt.setStError("");
            return fromSt;
        } catch (StUnavailableException e) {
            fallback.setStError(resolveStError(e));
            return fallback;
        }
    }

    public void updateFromAdmin(OpenRouterGenerationAdminDto body) {
        stClient.saveGenerationSettingsFromAdmin(body);
        try {
            fallbackSettingsService.updateFromAdmin(body);
        } catch (RuntimeException ignored) {
            // ST settings is the source of truth for the main chat path.
            // Local fallback persistence should not block a successful ST save.
        }
    }

    private static String resolveStError(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            String message = trimmed(cursor.getMessage());
            if (!message.isBlank() && !message.contains("服务暂时不可用")) {
                return "ST settings 读取失败：" + message;
            }
            cursor = cursor.getCause();
        }
        return "未能读取 ST settings，当前展示的是本地兜底参数。";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
