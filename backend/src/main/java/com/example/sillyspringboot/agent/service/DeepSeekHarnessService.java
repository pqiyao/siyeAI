package com.example.sillyspringboot.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only bridge to the project-local DSH runtime. Security and task facts remain in Spring Boot. */
@Service
public class DeepSeekHarnessService {
    private static final int WRITE_RPC_MIN_TIMEOUT_MS = 30_000;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String profile;
    private final boolean enabled;
    private final int timeoutMs;
    private final String workspaceRoot;
    private final boolean browserWorkerEnabled;
    private final ObjectMapper objectMapper;
    private static final Set<String> READ_METHODS = Set.of(
            "session.list", "session.history", "skill.list", "subagent.list",
            "subagent.history", "llm.providers", "llm.models", "workspace.list",
            "agentPreset.list"
    );
    private static final Set<String> WRITE_METHODS = Set.of("session.create", "session.prompt");

    public DeepSeekHarnessService(
            @Value("${app.agent.dsh.base-url:http://127.0.0.1:8091}") String baseUrl,
            @Value("${app.agent.dsh.profile:web}") String profile,
            @Value("${app.agent.dsh.enabled:false}") boolean enabled,
            @Value("${app.agent.dsh.timeout-ms:1500}") int timeoutMs,
            @Value("${app.agent.dsh.workspace-root:}") String workspaceRoot,
            @Value("${app.agent.dsh.browser-worker.enabled:false}") boolean browserWorkerEnabled,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.profile = profile;
        this.enabled = enabled;
        this.timeoutMs = Math.max(250, Math.min(timeoutMs, 10000));
        this.workspaceRoot = workspaceRoot == null ? "" : workspaceRoot.trim();
        this.browserWorkerEnabled = browserWorkerEnabled;
        this.objectMapper = objectMapper;
        // DSH's local web host is HTTP/1.1; avoid protocol negotiation resets on write RPCs.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(this.timeoutMs))
                .build();
    }

    public Map<String, Object> readRpc(String method, Map<String, Object> payload) {
        if (!READ_METHODS.contains(method)) {
            throw new IllegalArgumentException("DSH RPC 只允许只读方法: " + method);
        }
        if (!enabled) {
            throw new IllegalStateException("DSH 运行时未启用");
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", "client-request");
            envelope.put("rpcId", "jiugai-" + UUID.randomUUID());
            envelope.put("method", method);
            envelope.put("payload", payload == null ? Map.of() : payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/" + method))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(envelope)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("DSH RPC HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), Map.class);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DSH RPC 调用失败: " + method, ex);
        }
    }

    public HttpResponse<byte[]> exportSession(String sessionId) {
        if (!enabled) throw new IllegalStateException("DSH 运行时未启用");
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException("DSH 会话 ID 格式无效");
        }
        try {
            URI uri = URI.create(baseUrl + "/api/session.export?sessionId="
                    + URLEncoder.encode(sessionId, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(Math.max(timeoutMs, 5000)))
                    .GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) throw new IllegalStateException("DSH 导出失败: HTTP " + response.statusCode());
            return response;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DSH 会话导出失败", ex);
        }
    }

    public Map<String, Object> writeRpc(String method, Map<String, Object> payload) {
        if (!WRITE_METHODS.contains(method)) throw new IllegalArgumentException("DSH RPC 写方法不允许: " + method);
        if (!enabled) throw new IllegalStateException("DSH 运行时未启用");
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", "client-request");
            envelope.put("rpcId", "jiugai-" + UUID.randomUUID());
            envelope.put("method", method);
            envelope.put("payload", payload == null ? Map.of() : payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/" + method))
                    .timeout(Duration.ofMillis(Math.max(timeoutMs, WRITE_RPC_MIN_TIMEOUT_MS)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(envelope)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException("DSH RPC HTTP " + response.statusCode());
            return objectMapper.readValue(response.body(), Map.class);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DSH RPC 写调用失败: " + method, ex);
        }
    }

    public Map<String, Object> createSession(String requestedPreset) {
        if (workspaceRoot.isBlank()) throw new IllegalStateException("DSH workspace 根目录未配置");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cwd", workspaceRoot);
        if (requestedPreset != null && !requestedPreset.isBlank()) payload.put("agentPreset", requestedPreset.trim());
        return writeRpc("session.create", payload);
    }

    public String workspaceRoot() { return workspaceRoot; }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("baseUrl", baseUrl);
        result.put("profile", profile);
        result.put("capabilities", Map.ofEntries(
                Map.entry("webApp", enabled),
                Map.entry("planner", enabled),
                Map.entry("skills", enabled),
                Map.entry("mcp", enabled),
                Map.entry("subagents", enabled),
                Map.entry("filesystem", enabled),
                Map.entry("terminal", enabled),
                Map.entry("tokenMeter", enabled),
                Map.entry("browserBridge", enabled),
                Map.entry("browserInteractive", enabled && browserWorkerEnabled),
                Map.entry("devopsInspect", enabled),
                Map.entry("devopsBuild", enabled),
                Map.entry("devopsRelease", enabled),
                Map.entry("monitorSnapshot", enabled),
                Map.entry("backupInspect", enabled),
                Map.entry("evaluate", enabled),
                Map.entry("secretStatus", enabled)
        ));
        if (!enabled) {
            result.put("state", "DISABLED");
            result.put("message", "DSH 运行时未启用");
            return result;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            result.put("state", response.statusCode() < 500 ? "READY" : "UNAVAILABLE");
            result.put("httpStatus", response.statusCode());
        } catch (Exception ex) {
            result.put("state", "UNAVAILABLE");
            result.put("message", "DSH Web 未连接");
        }
        return result;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://127.0.0.1:8091";
        return value.replaceFirst("/+$", "");
    }
}
