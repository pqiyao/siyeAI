package com.example.sillyspringboot.app.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AppPingControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    @Test
    void pingReturnsOkAndTraceHeader() {
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                baseUrl() + "/api/app/ping",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getFirst("X-Trace-Id")).isNotBlank();
        assertThat(res.getBody()).isNotNull();
        assertThat(((Number) res.getBody().get("code")).intValue()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data).containsEntry("pong", true);
    }

    @Test
    void respectsIncomingTraceId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Trace-Id", "test-trace-123");
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                baseUrl() + "/api/app/ping",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getFirst("X-Trace-Id")).isEqualTo("test-trace-123");
    }
}
