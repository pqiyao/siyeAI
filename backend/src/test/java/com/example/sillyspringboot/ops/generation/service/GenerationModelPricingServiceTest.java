package com.example.sillyspringboot.ops.generation.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationModelPricingServiceTest {

    @Test
    void modelPatternSupportsExactAndStarGlobMatching() {
        assertTrue(GenerationModelPricingService.globMatches("gpt-4.1", "gpt-4.1"));
        assertTrue(GenerationModelPricingService.globMatches("openai/gpt-*", "OpenAI/GPT-4.1-mini"));
        assertTrue(GenerationModelPricingService.globMatches("*claude-3-5*", "anthropic/claude-3-5-sonnet"));
        assertFalse(GenerationModelPricingService.globMatches("gpt-4.1", "gpt-4.1-mini"));
        assertFalse(GenerationModelPricingService.globMatches("gpt-*", "claude-3"));
    }
}
