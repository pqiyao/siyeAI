package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.ops.generation.service.GenerationTelemetryService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SillyTavernProperties.class)
public class SillyTavernIntegrationConfiguration {

    @Bean
    public StClient stClient(
            SillyTavernProperties properties,
            OpenRouterGenerationSettingsService generationSettingsService,
            StModelRoutingService modelRoutingService,
            StGenerateBodyCapture generateBodyCapture,
            StRuntimeChatWriteCapture runtimeChatWriteCapture,
            GenerationTelemetryService generationTelemetryService,
            AiRoutingService aiRoutingService
    ) {
        return new StClient(
                properties,
                generationSettingsService,
                modelRoutingService,
                generateBodyCapture,
                runtimeChatWriteCapture,
                generationTelemetryService,
                aiRoutingService
        );
    }
}
