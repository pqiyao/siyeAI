package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleImageGenerationServiceFallbackTest {

    private final BusinessException unsupportedReference = new BusinessException(
            ErrorCode.VALIDATION_FAILED,
            "upstream model does not support image edit"
    );

    @Test
    void balancedMayFallbackWhenReferenceEditingIsUnsupported() {
        assertThat(OpenAiCompatibleImageGenerationService.shouldFallbackToWeakConsistency(
                "balanced", unsupportedReference)).isTrue();
    }

    @Test
    void strongReferenceOnlyNeverFallsBackToTextToImage() {
        assertThat(OpenAiCompatibleImageGenerationService.shouldFallbackToWeakConsistency(
                "reference_only", unsupportedReference)).isFalse();
    }

    @Test
    void freePromptFirstNeverUsesReferenceFallback() {
        assertThat(OpenAiCompatibleImageGenerationService.shouldFallbackToWeakConsistency(
                "prompt_first", unsupportedReference)).isFalse();
    }
}
