package com.example.sillyspringboot.ops.config;

import com.example.sillyspringboot.ops.service.ImageGenerationReadinessService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageGenerationProductionValidatorTest {

    @Test
    void productionRefusesToStartWhenSystemImageRuntimeIsNotReady() {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        ImageGenerationReadinessService readiness = mock(ImageGenerationReadinessService.class);
        when(readiness.isProduction()).thenReturn(true);
        when(readiness.snapshot("novelai")).thenReturn(snapshot(false, "REDIS_UNAVAILABLE", "Redis unavailable"));

        ImageGenerationProductionValidator validator =
                new ImageGenerationProductionValidator(properties, readiness);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_UNAVAILABLE");
    }

    @Test
    void developmentDoesNotFailFastOnExternalRuntimeDependencies() {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        ImageGenerationReadinessService readiness = mock(ImageGenerationReadinessService.class);
        when(readiness.isProduction()).thenReturn(false);

        assertThatCode(new ImageGenerationProductionValidator(properties, readiness)::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    private static ImageGenerationReadinessService.Snapshot snapshot(
            boolean ready,
            String code,
            String message
    ) {
        return new ImageGenerationReadinessService.Snapshot(
                ready, code, message, "novelai", true, true, ready, true, true
        );
    }
}
