package com.example.sillyspringboot.ops.config;

import com.example.sillyspringboot.ops.service.ImageGenerationReadinessService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class ImageGenerationProductionValidator implements InitializingBean {

    private final AppImageGenerationProperties properties;
    private final ImageGenerationReadinessService readinessService;

    public ImageGenerationProductionValidator(
            AppImageGenerationProperties properties,
            ImageGenerationReadinessService readinessService
    ) {
        this.properties = properties;
        this.readinessService = readinessService;
    }

    @Override
    public void afterPropertiesSet() {
        if (!readinessService.isProduction()) {
            return;
        }
        ImageGenerationReadinessService.Snapshot snapshot = readinessService.snapshot(properties.getEngine());
        if (!snapshot.ready()) {
            throw new IllegalStateException(
                    "Production image generation is not ready: " + snapshot.statusCode() + " - " + snapshot.message()
            );
        }
    }
}
