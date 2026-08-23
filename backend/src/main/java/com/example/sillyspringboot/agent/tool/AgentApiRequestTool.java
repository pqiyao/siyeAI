package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sillyspringboot.shared.net.OutboundUrlGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Safe read-only HTTP connector for explicitly configured public hosts. */
@Component
public class AgentApiRequestTool {

    private static final int MAX_RESPONSE_BYTES = 256_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Set<String> allowedHosts;
    private final String authHeader;
    private final String authToken;

    public AgentApiRequestTool(
            ObjectMapper objectMapper,
            @Value("${app.agent.api.allowed-hosts:}") String allowedHosts,
            @Value("${app.agent.api.auth-header:Authorization}") String authHeader,
            @Value("${app.agent.api.auth-token:}") String authToken
    ) {
        this.objectMapper = objectMapper;
        this.authHeader = authHeader;
        this.authToken = authToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.allowedHosts = Arrays.stream((allowedHosts == null ? "" : allowedHosts).split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Map<String, Object> request(Map<String, Object> step) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        String rawUrl = text(input.get("url"));
        if (rawUrl.isBlank()) {
            throw new IllegalArgumentException("api.request 需要 url");
        }
        URI uri = OutboundUrlGuard.requirePublicHttpUrl(rawUrl, "api.request 外部地址不安全");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new IllegalArgumentException("api.request 域名未配置白名单");
        }
        String method = text(input.get("method"));
        if (!method.isBlank() && !"GET".equalsIgnoreCase(method)) {
            throw new IllegalArgumentException("api.request 目前只允许 GET");
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.1");
            AgentCredentialSupport.applyCredential(requestBuilder, authHeader, authToken);
            HttpRequest request = requestBuilder.GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream stream = response.body()) {
                body = readLimited(stream);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", AgentCredentialSupport.redactedUrl(uri));
            result.put("secretInUrlDetected", AgentCredentialSupport.looksSensitiveQuery(uri));
            result.put("host", host);
            result.put("statusCode", response.statusCode());
            result.put("contentType", response.headers().firstValue("content-type").orElse(""));
            result.put("bodyTruncated", body.length() > MAX_RESPONSE_BYTES);
            String safeBody = body.length() > MAX_RESPONSE_BYTES
                    ? body.substring(0, MAX_RESPONSE_BYTES)
                    : body;
            if (response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT).contains("json")) {
                try {
                    JsonNode json = objectMapper.readTree(safeBody);
                    result.put("body", json);
                } catch (Exception ignored) {
                    result.put("body", safeBody);
                }
            } else {
                result.put("body", safeBody);
            }
            result.put("status", response.statusCode() >= 200 && response.statusCode() < 300 ? "SUCCEEDED" : "HTTP_ERROR");
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("api.request 被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("api.request 调用失败: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) {
                throw new IllegalArgumentException("api.request 输入必须是 JSON 对象");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("api.request 输入 JSON 无效", ex);
        }
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            int remaining = MAX_RESPONSE_BYTES + 1 - total;
            if (remaining <= 0) {
                break;
            }
            int accepted = Math.min(read, remaining);
            output.write(buffer, 0, accepted);
            total += accepted;
            if (accepted < read) {
                break;
            }
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
