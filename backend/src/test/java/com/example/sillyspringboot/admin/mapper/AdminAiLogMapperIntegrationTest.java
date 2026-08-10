package com.example.sillyspringboot.admin.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAiLogMapperIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminAiLogMapper mapper;

    @Test
    void groupsStandaloneFallbackAttemptsIntoOneFinalRequest() {
        String requestId = "vision-test-" + Math.abs(System.nanoTime());
        LocalDateTime started = LocalDateTime.now().minusSeconds(2);
        insertAttempt(requestId, 1, "vision-primary", "vision-model-a", false,
                started, 503, "FAILED", "UPSTREAM_ERROR");
        insertAttempt(requestId, 2, "vision-fallback", "vision-model-b", true,
                started.plusSeconds(1), 200, "SUCCESS", null);
        insertOrphanChatAttempt(started);

        assertThat(mapper.countStandaloneRequests("VISION", null, null, null, null, null,
                null, null, null)).isEqualTo(1);
        assertThat(mapper.countStandaloneRequests("VISION", "FAILED", null, null, null, null,
                null, null, null)).isZero();
        assertThat(mapper.countStandaloneRequests("VISION", "SUCCESS", null, null, "vision-primary", null,
                503, null, null)).isEqualTo(1);

        List<Map<String, Object>> rows = mapper.listStandaloneRequests(
                "VISION", null, null, requestId, null, null, null, null, null, 0, 20);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(string(row, "requestId")).isEqualTo(requestId);
        assertThat(string(row, "providerKey")).isEqualTo("vision-fallback");
        assertThat(string(row, "status")).isEqualTo("SUCCESS");
        assertThat(number(row, "attemptCount")).isEqualTo(2L);
        assertThat(Boolean.valueOf(string(row, "wasFallback"))).isTrue();

        List<Map<String, Object>> attempts = mapper.listStandaloneAttemptsByRequestId(requestId);
        assertThat(attempts).hasSize(2);
        assertThat(number(attempts.get(0), "attemptNo")).isEqualTo(1L);
        assertThat(number(attempts.get(1), "attemptNo")).isEqualTo(2L);
    }

    private void insertAttempt(String requestId, int attemptNo, String provider, String model,
                               boolean fallback, LocalDateTime startedAt, int httpStatus,
                               String status, String errorCode) {
        jdbc.update("""
                INSERT INTO app_generation_attempt (
                    generation_task_id, request_id, trace_id, attempt_no, provider_key, route_key,
                    provider_source, model, byok, was_fallback, started_at, finished_at,
                    duration_ms, http_status, status, error_code, error_message,
                    prompt_tokens, completion_tokens, prompt_tokens_estimated,
                    completion_tokens_estimated, total_cost_usd, cost_estimated, cost_partial
                ) VALUES (NULL, ?, 'trace-vision-test', ?, ?, 'vision.default',
                          'openai', ?, FALSE, ?, ?, ?, 100, ?, ?, ?, ?,
                          10, 20, FALSE, FALSE, 0.001, FALSE, FALSE)
                """, requestId, attemptNo, provider, model, fallback, startedAt,
                startedAt.plusNanos(100_000_000), httpStatus, status, errorCode,
                errorCode == null ? null : "upstream failed");
    }

    private void insertOrphanChatAttempt(LocalDateTime startedAt) {
        jdbc.update("""
                INSERT INTO app_generation_attempt (
                    generation_task_id, request_id, attempt_no, provider_key, route_key, model,
                    byok, was_fallback, started_at, finished_at, duration_ms, status,
                    prompt_tokens_estimated, completion_tokens_estimated, cost_estimated, cost_partial
                ) VALUES (NULL, 'chat-orphan-test', 1, 'chat-provider', 'default_chat', 'chat-model',
                          FALSE, FALSE, ?, ?, 10, 'SUCCESS', FALSE, FALSE, FALSE, FALSE)
                """, startedAt, startedAt.plusNanos(10_000_000));
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static Object value(Map<String, Object> row, String key) {
        return row.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
