package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/** Constrained JSON-RPC MCP call; endpoint and methods are configured, never model-discovered. */
@Component
public class AgentMcpCallTool {

    private static final int MAX_RESPONSE_BYTES = 512_000;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final Set<String> allowedHosts;
    private final Set<String> allowedMethods;
    private final String authHeader;
    private final String authToken;

    public AgentMcpCallTool(
            ObjectMapper objectMapper,
            @Value("${app.agent.mcp.endpoint:}") String endpoint,
            @Value("${app.agent.mcp.allowed-hosts:}") String allowedHosts,
            @Value("${app.agent.mcp.allowed-methods:}") String allowedMethods,
            @Value("${app.agent.mcp.auth-header:Authorization}") String authHeader,
            @Value("${app.agent.mcp.auth-token:}") String authToken
    ) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.authHeader = authHeader;
        this.authToken = authToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.allowedHosts = csv(allowedHosts);
        this.allowedMethods = csv(allowedMethods);
    }

    public Map<String, Object> call(Map<String, Object> step) {
        if (endpoint.isBlank()) {
            throw new IllegalStateException("mcp.call 尚未配置 endpoint");
        }
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        String method = text(input.get("method"));
        if (method.isBlank() || method.length() > 200 || !allowedMethods.contains(method)) {
            throw new IllegalArgumentException("mcp.call 方法未配置白名单");
        }
        URI uri = OutboundUrlGuard.requirePublicHttpUrl(endpoint, "mcp.call 外部地址不安全");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new IllegalArgumentException("mcp.call 域名未配置白名单");
        }
        try {
            ObjectNode requestJson = objectMapper.createObjectNode();
            requestJson.put("jsonrpc", "2.0");
            requestJson.put("id", text(input.get("id")).isBlank() ? text(step == null ? null : step.get("stepId")) : text(input.get("id")));
            requestJson.put("method", method);
            Object params = input.get("params");
            requestJson.set("params", params instanceof JsonNode node ? node : objectMapper.createObjectNode());
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            AgentCredentialSupport.applyCredential(requestBuilder, authHeader, authToken);
            HttpRequest request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestJson), StandardCharsets.UTF_8)).build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream stream = response.body()) {
                body = readLimited(stream);
            }
            JsonNode parsed = parseJson(body);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("endpoint", AgentCredentialSupport.redactedUrl(uri));
            result.put("secretInUrlDetected", AgentCredentialSupport.looksSensitiveQuery(uri));
            result.put("method", method);
            result.put("statusCode", response.statusCode());
            result.put("bodyTruncated", body.length() > MAX_RESPONSE_BYTES);
            if (parsed != null && parsed.has("error")) {
                result.put("error", parsed.get("error"));
                result.put("status", "MCP_ERROR");
            } else if (parsed != null && parsed.has("result")) {
                result.put("result", parsed.get("result"));
                result.put("status", response.statusCode() >= 200 && response.statusCode() < 300 ? "SUCCEEDED" : "HTTP_ERROR");
            } else {
                result.put("response", body.length() > MAX_RESPONSE_BYTES ? body.substring(0, MAX_RESPONSE_BYTES) : body);
                result.put("status", "INVALID_MCP_RESPONSE");
            }
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mcp.call 被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("mcp.call 调用失败: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("mcp.call 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("mcp.call 输入 JSON 无效", ex);
        }
    }

    private static Set<String> csv(String value) {
        return Arrays.stream((value == null ? "" : value).split(","))
                .map(String::trim).filter(item -> !item.isBlank()).collect(Collectors.toUnmodifiableSet());
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

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
