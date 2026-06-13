package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ManagedOpenAiCompatibleImageGenerationService implements ImageGenerationEngine {

    private final AppImageGenerationSettingsService settingsService;
    private final OpenAiCompatibleImageGenerationService delegate;

    public ManagedOpenAiCompatibleImageGenerationService(
            AppImageGenerationSettingsService settingsService,
            OpenAiCompatibleImageGenerationService delegate
    ) {
        this.settingsService = settingsService;
        this.delegate = delegate;
    }

    @Override
    public String engineName() {
        return "managed_openai_compatible";
    }

    @Override
    public Map<String, Object> generate(String clientUid, Map<String, Object> payload) {
        AppImageGenerationSettings settings = settingsService.getSettings();
        return delegate.generateManaged(
                clientUid,
                payload,
                settings.getManagedProviderSource(),
                settings.getManagedImageModelName(),
                settingsService.decryptManagedApiKey(settings),
                settings.getManagedCustomUrl()
        );
    }
}
