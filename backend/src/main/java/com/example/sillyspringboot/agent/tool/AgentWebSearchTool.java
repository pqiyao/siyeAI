package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sillyspringboot.shared.net.OutboundUrlGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only web search adapter backed by one explicitly configured search endpoint. */
@Component
public class AgentWebSearchTool {

    private static final int MAX_RESPONSE_BYTES = 512_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpointTemplate;
    private final Set<String> allowedHosts;
    private final String authHeader;
    private final String authToken;

    public AgentWebSearchTool(
            ObjectMapper objectMapper,
            @Value("${app.agent.web-search.endpoint:}") String endpointTemplate,
            @Value("${app.agent.web-search.allowed-hosts:${app.agent.api.allowed-hosts:}}") String allowedHosts,
            @Value("${app.agent.web-search.auth-header:Authorization}") String authHeader,
            @Value("${app.agent.web-search.auth-token:}") String authToken
    ) {
        this.objectMapper = objectMapper;
        this.endpointTemplate = endpointTemplate == null ? "" : endpointTemplate.trim();
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

    public Map<String, Object> search(Map<String, Object> step) {
        String query = text(parseInput(step == null ? null : step.get("inputJson")).get("query"));
        if (query.isBlank() || query.length() > 500) {
            throw new IllegalArgumentException("web.search 的 query 必须为 1 到 500 个字符");
        }
        if (endpointTemplate.isBlank() || !endpointTemplate.contains("{query}")) {
            throw new IllegalStateException("web.search 尚未配置 endpoint 模板");
        }
        String rawUrl = endpointTemplate.replace("{query}", URLEncoder.encode(query, StandardCharsets.UTF_8)
                .replace("+", "%20"));
        URI uri = OutboundUrlGuard.requirePublicHttpUrl(rawUrl, "web.search 外部地址不安全");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new IllegalArgumentException("web.search 域名未配置白名单");
        }
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    ;
            AgentCredentialSupport.applyCredential(requestBuilder, authHeader, authToken);
            HttpRequest request = requestBuilder.GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream stream = response.body()) {
                body = readLimited(stream);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("source", AgentCredentialSupport.redactedUrl(uri));
            result.put("secretInUrlDetected", AgentCredentialSupport.looksSensitiveQuery(uri));
            result.put("fetchedAt", Instant.now().toString());
            result.put("statusCode", response.statusCode());
            result.put("bodyTruncated", body.length() > MAX_RESPONSE_BYTES);
            String safeBody = body.length() > MAX_RESPONSE_BYTES ? body.substring(0, MAX_RESPONSE_BYTES) : body;
            JsonNode parsed = parseJson(safeBody);
            if (parsed != null && parsed.isObject() && parsed.has("results")) {
                result.put("results", parsed.get("results"));
            } else if (parsed != null && parsed.isArray()) {
                result.put("results", parsed);
            } else {
                result.put("results", safeBody);
            }
            result.put("status", response.statusCode() >= 200 && response.statusCode() < 300 ? "SUCCEEDED" : "HTTP_ERROR");
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("web.search 被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("web.search 调用失败: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) {
                throw new IllegalArgumentException("web.search 输入必须是 JSON 对象");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("web.search 输入 JSON 无效", ex);
        }
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            int remaining = MAX_RESPONSE_BYTES + 1 - total;
            if (remaining <= 0) break;
            int accepted = Math.min(read, remaining);
            output.write(buffer, 0, accepted);
            total += accepted;
            if (accepted < read) break;
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
