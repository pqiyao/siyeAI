package com.example.sillyspringboot.shared.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLogSanitizerTest {

    @Test
    void removesCommonCredentialShapesAndNormalizesLines() {
        String raw = "Authorization: Bearer top-secret\n"
                + "{\"api_key\":\"sk-1234567890abcdef\"} "
                + "url=https://user:password@example.com/v1 token=plain-token";

        String value = SensitiveLogSanitizer.sanitize(raw, 512);

        assertThat(value).doesNotContain("top-secret", "1234567890abcdef", "password", "plain-token");
        assertThat(value).contains("***").doesNotContain("\n");
    }

    @Test
    void appliesDatabaseColumnLimitAfterRedaction() {
        String value = SensitiveLogSanitizer.sanitize("apiKey=sk-1234567890 " + "x".repeat(100), 32);

        assertThat(value).hasSize(32).endsWith("...").doesNotContain("1234567890");
    }
}
