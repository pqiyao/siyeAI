package com.example.sillyspringboot.integration.sillytavern.dto;

public record UserModelOverride(
        String providerSource,
        String modelName,
        String visionModelName,
        String audioModelName,
        String sttModelName,
        String sttProviderSource,
        String sttApiKey,
        String sttCustomUrl,
        String ttsModelName,
        String ttsVoiceName,
        String ttsProviderSource,
        String ttsApiKey,
        String ttsCustomUrl,
        String imageModelName,
        String imageProviderSource,
        String imageApiKey,
        String imageCustomUrl,
        String apiKey,
        String customUrl
) {
    public String textModelOrFallback() {
        return safe(modelName);
    }

    public String visionModelOrFallback() {
        String value = safe(visionModelName);
        return !value.isBlank() ? value : textModelOrFallback();
    }

    public String audioModelOrFallback() {
        String value = safe(audioModelName);
        return !value.isBlank() ? value : textModelOrFallback();
    }

    public String sttModelOrFallback() {
        String value = safe(sttModelName);
        if (!value.isBlank()) {
            return value;
        }
        value = safe(audioModelName);
        return !value.isBlank() ? value : textModelOrFallback();
    }

    public String sttProviderSourceOrFallback() {
        return hasDedicatedSttProvider() ? safe(sttProviderSource) : safe(providerSource);
    }

    public String sttApiKeyOrFallback() {
        return hasDedicatedSttProvider() ? safe(sttApiKey) : safe(apiKey);
    }

    public String sttCustomUrlOrFallback() {
        return hasDedicatedSttProvider() ? safe(sttCustomUrl) : safe(customUrl);
    }

    public String ttsModelOrFallback() {
        String value = safe(ttsModelName);
        if (!value.isBlank()) {
            return value;
        }
        value = safe(audioModelName);
        return !value.isBlank() ? value : textModelOrFallback();
    }

    public String ttsVoiceOrFallback() {
        return safe(ttsVoiceName);
    }

    public String ttsProviderSourceOrFallback() {
        return hasDedicatedTtsProvider() ? safe(ttsProviderSource) : safe(providerSource);
    }

    public String ttsApiKeyOrFallback() {
        return hasDedicatedTtsProvider() ? safe(ttsApiKey) : safe(apiKey);
    }

    public String ttsCustomUrlOrFallback() {
        return hasDedicatedTtsProvider() ? safe(ttsCustomUrl) : safe(customUrl);
    }

    public String imageModelOrFallback() {
        String value = safe(imageModelName);
        return !value.isBlank() ? value : textModelOrFallback();
    }

    public String imageProviderSourceOrFallback() {
        return hasDedicatedImageProvider() ? safe(imageProviderSource) : safe(providerSource);
    }

    public String imageApiKeyOrFallback() {
        return hasDedicatedImageProvider() ? safe(imageApiKey) : safe(apiKey);
    }

    public String imageCustomUrlOrFallback() {
        return hasDedicatedImageProvider() ? safe(imageCustomUrl) : safe(customUrl);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasDedicatedTtsProvider() {
        return !safe(ttsProviderSource).isBlank();
    }

    private boolean hasDedicatedSttProvider() {
        return !safe(sttProviderSource).isBlank();
    }

    private boolean hasDedicatedImageProvider() {
        return !safe(imageProviderSource).isBlank();
    }
}
