package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Approved bridge to the loopback-only Playwright Browser Worker. */
@Component
public class AgentDshBrowserInteractTool {
    private static final Set<String> ACTIONS = Set.of("open", "click", "input", "extract", "close");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\\\"']+");
    private final boolean enabled;
    private final URI endpoint;
    private final String token;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentDshBrowserInteractTool(
            @Value("${app.agent.dsh.browser-worker.enabled:false}") boolean enabled,
            @Value("${app.agent.dsh.browser-worker.endpoint:http://127.0.0.1:8092/api/browser}") String endpoint,
            @Value("${app.agent.dsh.browser-worker.token:}") String token,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.endpoint = URI.create(endpoint);
        String host = this.endpoint.getHost() == null ? "" : this.endpoint.getHost().toLowerCase();
        if (!("127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host))) {
            throw new IllegalArgumentException("DSH Browser Worker 只允许本机地址");
        }
        this.token = token == null ? "" : token.trim();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public Map<String, Object> interact(Map<String, Object> step) {
        return interact(step, Map.of());
    }

    public Map<String, Object> interact(Map<String, Object> step, Map<String, Object> task) {
        if (!enabled) throw new IllegalStateException("DSH Browser Worker 未启用");
        Map<String, Object> input = parse(step.get("inputJson"));
        String action = String.valueOf(input.getOrDefault("action", "")).trim().toLowerCase();
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("DSH Browser action 无效");
        mergeBrowserConstraints(input, task, action);
        if ("open".equals(action) && text(input.get("url")).isBlank()) {
            input.put("url", urlFromTask(task));
        }
        if (!"open".equals(action) && text(input.get("sessionId")).isBlank()) {
            input.put("sessionId", sessionIdFromCompletedSteps(task.get("completedSteps")));
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(input)));
            if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException("DSH Browser Worker HTTP " + response.statusCode());
            Map<String, Object> workerResponse = objectMapper.readValue(
                    response.body(), new TypeReference<Map<String, Object>>() { });
            if (!Boolean.TRUE.equals(workerResponse.get("ok"))) {
                throw new IllegalStateException("DSH Browser Worker 返回失败");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("operationId", "dsh-op-" + UUID.randomUUID());
            result.put("capability", "dsh.browser.interact");
            result.put("status", "SUCCEEDED");
            String sessionId = findSessionId(workerResponse);
            if (!sessionId.isBlank()) result.put("sessionId", sessionId);
            result.put("browserResponse", workerResponse);
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DSH Browser Worker 调用被中断", ex);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DSH Browser Worker 调用失败", ex);
        }
    }

    private String urlFromTask(Map<String, Object> task) {
        Map<String, Object> constraints = parseOptional(task.get("constraintsJson"));
        String configured = text(constraints.get("url"));
        if (!configured.isBlank()) return configured;
        Object browser = constraints.get("browser");
        if (browser instanceof Map<?, ?> values) {
            configured = text(values.get("url"));
            if (!configured.isBlank()) return configured;
        }
        Matcher matcher = URL_PATTERN.matcher(text(task.get("objective")));
        if (!matcher.find()) throw new IllegalArgumentException("DSH Browser open 需要 url，或在任务目标中包含 HTTP/HTTPS 地址");
        return matcher.group().replaceAll("[),.;\\]}，。；）]+$", "");
    }

    private void mergeBrowserConstraints(Map<String, Object> input, Map<String, Object> task, String action) {
        Map<String, Object> constraints = parseOptional(task.get("constraintsJson"));
        Map<String, Object> browser = new LinkedHashMap<>();
        Object nested = constraints.get("browser");
        if (nested instanceof Map<?, ?> values) {
            values.forEach((key, value) -> browser.put(String.valueOf(key), value));
        }
        constraints.forEach((key, value) -> browser.putIfAbsent(String.valueOf(key), value));
        if ("input".equals(action)) {
            if (text(input.get("selector")).isBlank()) input.put("selector", browser.get("inputSelector"));
            if (text(input.get("value")).isBlank()) input.put("value", browser.get("inputValue"));
        } else if ("click".equals(action) && text(input.get("selector")).isBlank()) {
            input.put("selector", browser.get("clickSelector"));
        }
    }

    private String sessionIdFromCompletedSteps(Object completedSteps) {
        String latest = "";
        if (!(completedSteps instanceof Iterable<?> rows)) return latest;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> values)) continue;
            if (!"dsh.browser.interact".equals(text(values.get("toolName")))) continue;
            String outputJson = text(values.get("outputJson"));
            if (outputJson.isBlank()) continue;
            try {
                String found = findSessionId(objectMapper.readValue(outputJson, Object.class));
                if (!found.isBlank()) latest = found;
            } catch (Exception ignored) { }
        }
        if (latest.isBlank()) throw new IllegalArgumentException("DSH Browser action 需要前置 open 会话或显式 sessionId");
        return latest;
    }

    private String findSessionId(Object value) {
        if (value instanceof Map<?, ?> values) {
            String direct = text(values.get("sessionId"));
            if (!direct.isBlank()) return direct;
            for (Object child : values.values()) {
                String found = findSessionId(child);
                if (!found.isBlank()) return found;
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object child : values) {
                String found = findSessionId(child);
                if (!found.isBlank()) return found;
            }
        }
        return "";
    }

    private Map<String, Object> parseOptional(Object raw) {
        if (raw == null || text(raw).isBlank()) return Map.of();
        try {
            if (raw instanceof Map<?, ?> values) {
                Map<String, Object> result = new LinkedHashMap<>();
                values.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
            return objectMapper.readValue(text(raw), new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            throw new IllegalArgumentException("任务 constraints JSON 无效", ex);
        }
    }

    private Map<String, Object> parse(Object raw) {
        try {
            if (raw instanceof Map<?, ?> values) {
                Map<String, Object> result = new LinkedHashMap<>();
                values.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            throw new IllegalArgumentException("dsh.browser.interact 输入 JSON 无效", ex);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
