package com.example.sillyspringboot.compat.h5.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H5UserAiProviderServiceUrlTest {

    @Test
    void appendsV1ToOrigin() {
        assertEquals(
                "https://example.com/v1",
                H5UserAiProviderService.normalizeCustomOpenAiBaseUrl("https://example.com")
        );
    }

    @Test
    void keepsExistingV1AndRemovesTrailingSlash() {
        assertEquals(
                "https://example.com/v1",
                H5UserAiProviderService.normalizeCustomOpenAiBaseUrl("https://example.com/v1/")
        );
    }

    @Test
    void normalizesFullChatCompletionsEndpoint() {
        assertEquals(
                "https://example.com/v1",
                H5UserAiProviderService.normalizeCustomOpenAiBaseUrl(
                        "https://example.com/v1/chat/completions"
                )
        );
    }

    @Test
    void appendsV1ToCustomPath() {
        assertEquals(
                "https://example.com/openai/v1",
                H5UserAiProviderService.normalizeCustomOpenAiBaseUrl("https://example.com/openai")
        );
    }
}
