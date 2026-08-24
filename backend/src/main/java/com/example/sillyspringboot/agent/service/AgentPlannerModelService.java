package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import com.example.sillyspringboot.ops.generation.service.GenerationTelemetryService;
import com.example.sillyspringboot.shared.net.OutboundUrlGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Optional model-backed planner. It only proposes JSON; AgentPlanService performs all authorization. */
@Service
public class AgentPlannerModelService {

    private static final Logger log = LoggerFactory.getLogger(AgentPlannerModelService.class);
    private static final int MAX_RESPONSE_CHARS = 256_000;
    private static final String ALLOWED_TOOLS = "planner.analyze,analytics.query,analytics.review,system.health_check," +
            "web.search,browser.open,dsh.browser.open,dsh.browser.interact,dsh.devops.inspect,dsh.devops.build,dsh.monitor.snapshot,dsh.backup.inspect,dsh.secret.status,dsh.evaluate.run,api.request,mcp.call,code.run,dsh.session.create,dsh.session.prompt,artifact.render_report,artifact.evaluate," +
            "character.create,character.validate,story.create_outline,story.write_chapter,story.check_continuity," +
            "story.revise,image.generate,tts.generate,artifact.render_html,artifact.package_zip";

    private final AiRoutingService routingService;
    private final ObjectMapper objectMapper;
    private final GenerationTelemetryService telemetryService;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final boolean runtimeEnabled;

    @Autowired
    public AgentPlannerModelService(
            AiRoutingService routingService,
            ObjectMapper objectMapper,
            GenerationTelemetryService telemetryService,
            @Value("${app.agent.planner-model-enabled:false}") boolean enabled,
            @Value("${app.agent.runtime-planner-model-enabled:false}") boolean runtimeEnabled
    ) {
        this.routingService = routingService;
        this.objectMapper = objectMapper;
        this.telemetryService = telemetryService;
        this.enabled = enabled;
        this.runtimeEnabled = runtimeEnabled;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public AgentPlannerModelService(AiRoutingService routingService, ObjectMapper objectMapper) {
        this(routingService, objectMapper, null, false, false);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String proposeRuntime(Map<String, Object> task, Map<String, Object> completedStep, String outputJson) {
        if (!runtimeEnabled || task == null || completedStep == null) return null;
        List<AiRoutingService.ResolvedProvider> providers;
        try {
            providers = routingService.resolve(AiCapability.CHAT);
        } catch (RuntimeException ex) {
            log.warn("Agent 运行时 Planner 路由解析失败: {}", ex.getMessage());
            return null;
        }
        int attemptNo = 0;
        for (AiRoutingService.ResolvedProvider provider : providers) {
            attemptNo++;
            String requestId = "agent-runtime-planner-" + task.getOrDefault("taskId", UUID.randomUUID());
            LocalDateTime startedAt = LocalDateTime.now();
            Integer responseStatus = null;
            try {
                URI endpoint = OutboundUrlGuard.requirePublicHttpUrl(
                        trimTrailingSlash(provider.baseUrl()) + "/chat/completions",
                        "运行时 Planner 模型地址不安全");
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(Math.max(5, provider.requestTimeoutSeconds())))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + provider.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(runtimePayload(
                                task, completedStep, outputJson, provider.modelName()))))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                responseStatus = response.statusCode();
                if (response.body() != null && response.body().length() > MAX_RESPONSE_CHARS) {
                    throw new IllegalStateException("运行时 Planner 模型响应超过大小限制");
                }
                JsonNode root = objectMapper.readTree(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("运行时 Planner 模型 HTTP " + response.statusCode());
                }
                String proposal = normalizeRuntimeProposal(content(root));
                record(requestId, attemptNo, provider, startedAt, response.statusCode(), root, "success", null);
                return proposal;
            } catch (Exception ex) {
                record(requestId, attemptNo, provider, startedAt, responseStatus, null, "failed", ex);
                log.warn("Agent 运行时 Planner 调用失败 provider={} cause={}", provider.providerKey(), ex.getMessage());
            }
        }
        return null;
    }

    private Map<String, Object> runtimePayload(Map<String, Object> task, Map<String, Object> step,
                                                String outputJson, String modelName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("stream", false);
        payload.put("temperature", 0.1);
        payload.put("max_tokens", 1600);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "你是受控运行时 Agent Planner。只输出 JSON 对象，必须包含 runtimeReplan.steps 数组；每个步骤需包含 key、name、agentRole、stepType、toolName、dependsOn 和 inputJson。没有必要追加步骤时返回空 steps。只能使用已授权 Agent 工具，不得提升风险或生成删除、扣款、封禁、权限修改、任意 Shell。"),
                Map.of("role", "user", "content", "taskType=" + safe(task.get("taskType")) + "\ntaskObjective=" + safe(task.get("objective")) +
                        "\ncompletedStep=" + safe(step.get("stepKey")) + "\nresult=" + clip(outputJson, 12_000))
        ));
        return payload;
    }

    private String normalizeRuntimeProposal(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```") && text.endsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        JsonNode node = objectMapper.readTree(text);
        if (node == null || !node.isObject() || !node.path("runtimeReplan").isObject()
                || !node.path("runtimeReplan").path("steps").isArray()) {
            throw new IllegalArgumentException("运行时 Planner 模型未返回 runtimeReplan.steps");
        }
        return node.toString();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String propose(Map<String, Object> task) {
        if (!enabled || task == null) return null;
        List<AiRoutingService.ResolvedProvider> providers;
        try {
            providers = routingService.resolve(AiCapability.CHAT);
        } catch (RuntimeException ex) {
            log.warn("Agent Planner 模型路由解析失败: {}", ex.getMessage());
            return null;
        }
        int attemptNo = 0;
        for (AiRoutingService.ResolvedProvider provider : providers) {
            attemptNo++;
            String requestId = "agent-planner-" + task.getOrDefault("taskId", UUID.randomUUID());
            LocalDateTime startedAt = LocalDateTime.now();
            Integer responseStatus = null;
            try {
                String baseUrl = trimTrailingSlash(provider.baseUrl());
                URI endpoint = URI.create(baseUrl + "/chat/completions");
                String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase();
                if (!("http".equals(scheme) || "https".equals(scheme)) || endpoint.getHost() == null
                        || endpoint.getUserInfo() != null) {
                    throw new IllegalArgumentException("Planner 模型地址必须是无用户信息的 HTTP(S) 地址");
                }
                endpoint = OutboundUrlGuard.requirePublicHttpUrl(endpoint.toString(), "Planner 模型地址不安全");
                HttpRequest request = HttpRequest.newBuilder(
                                endpoint)
                        .timeout(Duration.ofSeconds(Math.max(5, provider.requestTimeoutSeconds())))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + provider.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload(task, provider.modelName()))))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                responseStatus = response.statusCode();
                if (response.body() != null && response.body().length() > MAX_RESPONSE_CHARS) {
                    throw new IllegalStateException("Planner 模型响应超过大小限制");
                }
                JsonNode root = objectMapper.readTree(response.body());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Planner 模型 HTTP " + response.statusCode());
                }
                String content = content(root);
                String proposal = normalizeProposal(content);
                record(requestId, attemptNo, provider, startedAt, response.statusCode(), root, "success", null);
                return proposal;
            } catch (Exception ex) {
                record(requestId, attemptNo, provider, startedAt, responseStatus, null, "failed", ex);
                log.warn("Agent Planner 模型调用失败 provider={} cause={}", provider.providerKey(), ex.getMessage());
            }
        }
        return null;
    }

    private Map<String, Object> payload(Map<String, Object> task, String modelName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("stream", false);
        payload.put("temperature", 0.1);
        payload.put("max_tokens", 1600);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        messages.add(Map.of("role", "user", "content", userPrompt(task)));
        payload.put("messages", messages);
        return payload;
    }

    private String systemPrompt() {
        return "你是受控 Agent Planner。只输出 JSON，不要 Markdown，不要解释。" +
                "输出格式必须为 {\"plannerProposal\":{\"steps\":[{" +
                "\"key\":\"ascii_key\",\"name\":\"步骤\",\"agentRole\":\"角色\",\"stepType\":\"类型\",\"toolName\":\"工具\",\"dependsOn\":[],\"inputJson\":{}}]}}。" +
                "只能使用这些工具：" + ALLOWED_TOOLS + "。不得生成循环依赖，不得生成删除、扣款、封禁、权限修改或任意 Shell 工具。";
    }

    private static String userPrompt(Map<String, Object> task) {
        return "taskType=" + safe(task.get("taskType")) + "\n" +
                "title=" + safe(task.get("title")) + "\n" +
                "objective=" + safe(task.get("objective")) + "\n" +
                "riskLevel=" + safe(task.get("riskLevel"));
    }

    private String content(JsonNode root) {
        JsonNode value = root == null ? null : root.at("/choices/0/message/content");
        if (value == null || value.isMissingNode()) throw new IllegalArgumentException("Planner 模型未返回 message.content");
        if (value.isTextual()) return value.textValue();
        if (value.isArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonNode item : value) {
                JsonNode text = item.get("text");
                if (text != null && text.isTextual()) result.append(text.textValue());
            }
            return result.toString();
        }
        throw new IllegalArgumentException("Planner 模型返回内容格式无效");
    }

    private String normalizeProposal(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```") && text.endsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        JsonNode node = objectMapper.readTree(text);
        if (node == null || !node.isObject() || node.get("plannerProposal") == null) {
            throw new IllegalArgumentException("Planner 模型未返回 plannerProposal");
        }
        return node.toString();
    }

    private void record(String requestId, int attemptNo, AiRoutingService.ResolvedProvider provider,
                        LocalDateTime startedAt, Integer httpStatus, JsonNode response,
                        String status, Throwable error) {
        if (telemetryService == null) return;
        try {
            JsonNode usage = response == null ? null : response.get("usage");
            Integer prompt = integer(usage == null ? null : usage.get("prompt_tokens"));
            Integer completion = integer(usage == null ? null : usage.get("completion_tokens"));
            telemetryService.recordAsync(new GenerationAttemptEvent(
                    null, requestId, null, attemptNo, provider.providerKey(), AiCapability.CHAT.defaultRouteKey(),
                    provider.vendor(), provider.modelName(), false, attemptNo > 1, startedAt, null, LocalDateTime.now(),
                    httpStatus, status, null, error == null ? null : safe(error.getMessage()), prompt, prompt == null,
                    completion, completion == null));
        } catch (RuntimeException ignored) {
            log.debug("Agent Planner telemetry write failed", ignored);
        }
    }

    private static Integer integer(JsonNode node) { return node != null && node.isNumber() ? node.intValue() : null; }
    private static String safe(Object value) { return value == null ? "" : String.valueOf(value).replace("\n", " ").trim(); }
    private static String clip(String value, int max) {
        String text = safe(value);
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
    private static String trimTrailingSlash(String value) { return value == null ? "" : value.replaceAll("/+$", ""); }
}
