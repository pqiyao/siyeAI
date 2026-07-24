package com.example.sillyspringboot.chat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AppGenerationTaskMapperSqlContractTest {

    @Test
    void terminalDurationUsesPortableIntegerMilliseconds() throws IOException {
        try (var input = getClass().getResourceAsStream("/mapper/chat/AppGenerationTaskMapper.xml")) {
            assertThat(input).isNotNull();
            String mapper = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(mapper).doesNotContain("AS BIGINT");
            assertThat(mapper).contains(
                    "TIMESTAMPDIFF(MICROSECOND, COALESCE(started_at, queued_at), CURRENT_TIMESTAMP) / 1000"
            );
            assertThat(mapper).contains("THEN FLOOR(");
        }
    }
}
