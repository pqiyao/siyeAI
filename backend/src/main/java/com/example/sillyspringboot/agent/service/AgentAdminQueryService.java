package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.ops.service.ImageGenerationReadinessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.net.URI;
import com.example.sillyspringboot.agent.tool.AgentCredentialSupport;
import com.example.sillyspringboot.agent.tool.AgentSystemInspectionTool;

@Service
public class AgentAdminQueryService {

    private static final Logger log = LoggerFactory.getLogger(AgentAdminQueryService.class);

    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EVENT_PAGE_SIZE = 500;
    private static final Set<String> TASK_STATUSES = Set.of(
            "DRAFT", "PLANNING", "WAITING_APPROVAL", "READY", "RUNNING", "PAUSED",
            "WAITING_CONFIRMATION", "CANCEL_REQUESTED", "SUCCEEDED", "PARTIAL_SUCCESS",
            "FAILED", "CANCELLED"
    );
    private static final Set<String> RISK_LEVELS = Set.of(
            "READ_ONLY", "WRITE_REVERSIBLE", "EXTERNAL_SIDE_EFFECT", "DESTRUCTIVE", "PRODUCTION_OPERATION"
    );
    private static final Set<String> APPROVAL_STATUSES = Set.of(
            "PENDING", "APPROVED", "REJECTED", "EXPIRED", "CANCELLED"
    );

    private final AgentAdminMapper mapper;
    private final AiRoutingService aiRoutingService;
    private final ImageGenerationReadinessService imageReadinessService;
    private final ObjectMapper objectMapper;
    private final AgentSystemInspectionTool systemInspectionTool;

    @Value("${app.agent.api.allowed-hosts:}")
    private String apiAllowedHosts;
    @Value("${app.agent.api.auth-token:}")
    private String apiAuthToken;
    @Value("${app.agent.api.auth-header:Authorization}")
    private String apiAuthHeader;
    @Value("${app.agent.web-search.endpoint:}")
    private String webSearchEndpoint;
    @Value("${app.agent.web-search.allowed-hosts:}")
    private String webSearchAllowedHosts;
    @Value("${app.agent.web-search.auth-token:}")
    private String webSearchAuthToken;
    @Value("${app.agent.web-search.auth-header:Authorization}")
    private String webSearchAuthHeader;
    @Value("${app.agent.browser.allowed-hosts:}")
    private String browserAllowedHosts;
    @Value("${app.agent.mcp.endpoint:}")
    private String mcpEndpoint;
    @Value("${app.agent.mcp.allowed-hosts:}")
    private String mcpAllowedHosts;
    @Value("${app.agent.mcp.auth-token:}")
    private String mcpAuthToken;
    @Value("${app.agent.mcp.auth-header:Authorization}")
    private String mcpAuthHeader;
    @Value("${app.agent.code-runner.image:}")
    private String codeRunnerImage;
    @Value("${app.agent.code-runner.user:65532:65532}")
    private String codeRunnerUser;
    @Value("${app.agent.code-runner.require-digest:false}")
    private boolean codeRunnerRequireDigest;
    @Value("${app.agent.alerts.pending-approval-threshold:10}")
    private long pendingApprovalAlertThreshold;
    @Value("${app.agent.alerts.failed-task-threshold:1}")
    private long failedTaskAlertThreshold;
    @Value("${app.agent.platform-max-steps:32}")
    private int platformMaxSteps;
    @Value("${app.agent.platform-max-tokens:100000000}")
    private long platformMaxTokens;
    @Value("${app.agent.platform-max-cost:1000000}")
    private java.math.BigDecimal platformMaxCost;
    @Value("${app.agent.daily-max-tokens:0}")
    private long dailyMaxTokens;
    @Value("${app.agent.daily-max-cost:0}")
    private java.math.BigDecimal dailyMaxCost;
    @Value("${app.agent.daily-max-tasks:0}")
    private long dailyMaxTasks;
    @Value("${app.agent.workflow-template-enabled:false}")
    private boolean workflowTemplateEnabled;
    @Value("${app.agent.planner-model-enabled:false}")
    private boolean plannerModelEnabled;
    @Value("${app.agent.runtime-planner-model-enabled:false}")
    private boolean runtimePlannerModelEnabled;

    public AgentAdminQueryService(
            AgentAdminMapper mapper,
            AiRoutingService aiRoutingService,
            ImageGenerationReadinessService imageReadinessService
    ) {
        this(mapper, aiRoutingService, imageReadinessService, new ObjectMapper(), null);
    }

    @Autowired
    public AgentAdminQueryService(
            AgentAdminMapper mapper,
            AiRoutingService aiRoutingService,
            ImageGenerationReadinessService imageReadinessService,
            ObjectMapper objectMapper,
            AgentSystemInspectionTool systemInspectionTool
    ) {
        this.mapper = mapper;
        this.aiRoutingService = aiRoutingService;
        this.imageReadinessService = imageReadinessService;
        this.objectMapper = objectMapper;
        this.systemInspectionTool = systemInspectionTool;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> overview = mapper.overview();
        if (overview != null) {
            result.putAll(overview);
        }
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("chat", safeCapability(AiCapability.CHAT));
        capabilities.put("image", safeImageReadiness());
        capabilities.put("tts", safeCapability(AiCapability.TTS));
        result.put("capabilities", capabilities);
        Map<String, Object> toolState = toolReadiness();
        Map<String, Object> externalState = externalReadiness();
        result.put("toolReadiness", toolState);
        result.put("externalReadiness", externalState);
        result.put("alerts", alerts(overview, toolState, externalState));
        return result;
    }

    public Map<String, Object> security() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readOnly", true);
        result.put("toolReadiness", toolReadiness());
        result.put("externalReadiness", externalReadiness());
        result.put("alerts", alerts(mapper.overview(), toolReadiness(), externalReadiness()));
        result.put("executionPolicies", Map.ofEntries(
                Map.entry("workflowTemplateEnabled", workflowTemplateEnabled),
                Map.entry("plannerModelEnabled", plannerModelEnabled),
                Map.entry("runtimePlannerModelEnabled", runtimePlannerModelEnabled),
                Map.entry("platformMaxSteps", Math.max(1, Math.min(32, platformMaxSteps))),
                Map.entry("platformMaxTokens", Math.max(1L, platformMaxTokens)),
                Map.entry("platformMaxCost", platformMaxCost == null ? java.math.BigDecimal.ZERO : platformMaxCost),
                Map.entry("dailyMaxTokens", Math.max(0L, dailyMaxTokens)),
                Map.entry("dailyMaxCost", dailyMaxCost == null ? java.math.BigDecimal.ZERO : dailyMaxCost),
                Map.entry("dailyMaxTasks", Math.max(0L, dailyMaxTasks)),
                Map.entry("dailyBudgetEnforced", dailyMaxTokens > 0
                        || (dailyMaxCost != null && dailyMaxCost.signum() > 0)
                        || dailyMaxTasks > 0),
                Map.entry("arbitraryShell", false),
                Map.entry("arbitrarySql", false),
                Map.entry("arbitrarySsh", false),
                Map.entry("productionAutoDeploy", false)
        ));
        result.put("approvalRequiredFor", List.of("EXTERNAL_SIDE_EFFECT", "DESTRUCTIVE", "PRODUCTION_OPERATION"));
        return result;
    }

    public PageResult artifacts(int pageNum, int pageSize, String artifactType, String status, String keyword, String tenantKey) {
        String safeType = clip(artifactType, 64);
        String safeStatus = clip(status, 32);
        String safeKeyword = clip(keyword, 128);
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countArtifactCenter(safeType, safeStatus, safeKeyword, safeTenantKey(tenantKey)),
                mapper.listArtifactCenter(safeType, safeStatus, safeKeyword, safeTenantKey(tenantKey), page.offset(), page.size())
        );
    }

    private List<Map<String, Object>> alerts(Map<String, Object> overview,
                                             Map<String, Object> tools,
                                             Map<String, Object> external) {
        List<Map<String, Object>> alerts = new java.util.ArrayList<>();
        long failed = number(overview == null ? null : overview.get("failedTasks"));
        long pending = number(overview == null ? null : overview.get("pendingApprovals"));
        long failedThreshold = Math.max(1L, failedTaskAlertThreshold);
        if (failed >= failedThreshold) {
            alerts.add(alert("WARNING", "FAILED_TASKS", "失败 Agent 任务达到：" + failed + "（阈值 " + failedThreshold + "）"));
        }
        long approvalThreshold = Math.max(1L, pendingApprovalAlertThreshold);
        if (pending >= approvalThreshold) {
            alerts.add(alert("WARNING", "PENDING_APPROVALS", "待处理审批达到：" + pending + "（阈值 " + approvalThreshold + "）"));
        }
        if (number(tools.get("enabledTools")) == 0) {
            alerts.add(alert("CRITICAL", "NO_ENABLED_TOOLS", "当前没有启用的 Agent 工具"));
        }
        Object enabledKeys = tools.get("enabledToolKeys");
        if (enabledKeys instanceof List<?> keys) {
            addConnectorAlert(alerts, keys, "api.request", external, "apiRequest", "API 请求工具已启用但公网域名白名单未配置");
            addConnectorAlert(alerts, keys, "web.search", external, "webSearch", "Web Search 工具已启用但搜索端点或域名白名单未配置");
            addConnectorAlert(alerts, keys, "browser.open", external, "browser", "Browser 工具已启用但网页域名白名单未配置");
            addConnectorAlert(alerts, keys, "mcp.call", external, "mcp", "MCP 工具已启用但端点或方法白名单未配置");
            addConnectorAlert(alerts, keys, "code.run", external, "codeRunner", "Code Runner 工具已启用但沙盒镜像或非 root UID/GID 未正确配置");
        }
        addDailyBudgetAlert(alerts, external);
        addTenantBudgetAlerts(alerts);
        Object secretFlags = external.get("secretInUrlDetected");
        if (secretFlags instanceof Map<?, ?> flags && flags.values().stream().anyMatch(value -> Boolean.TRUE.equals(value))) {
            alerts.add(alert("CRITICAL", "SECRET_IN_URL", "外部连接端点包含疑似密钥查询参数，已禁止在结果中回显；请改用环境变量凭据"));
        }
        if (alerts.isEmpty()) alerts.add(alert("INFO", "NO_ACTIVE_ALERT", "当前概览未发现达到阈值的 Agent 告警"));
        persistAlerts(alerts);
        return alerts;
    }

    public List<Map<String, Object>> listPersistentAlerts(String status, int limit) {
        String safeStatus = status == null ? "" : status.trim().toUpperCase();
        if (!safeStatus.isBlank() && !Set.of("OPEN", "ACKNOWLEDGED").contains(safeStatus)) throw new IllegalArgumentException("告警状态无效");
        return mapper.listAlerts(safeStatus, Math.max(1, Math.min(200, limit)));
    }

    public void acknowledgeAlert(long id, long adminId) {
        if (id <= 0 || adminId <= 0) throw new IllegalArgumentException("告警编号无效");
        if (mapper.acknowledgeAlert(id, adminId) != 1) throw new IllegalStateException("告警不存在或已确认");
    }

    private void persistAlerts(List<Map<String, Object>> alerts) {
        if (alerts == null) return;
        for (Map<String, Object> alert : alerts) {
            String code = String.valueOf(alert.getOrDefault("code", "UNKNOWN"));
            String message = String.valueOf(alert.getOrDefault("message", ""));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenantKey", "platform");
            row.put("fingerprint", Integer.toHexString((code + "|" + message).hashCode()));
            row.put("severity", alert.getOrDefault("severity", "INFO"));
            row.put("code", code);
            row.put("message", message);
            try { mapper.upsertAlert(row); } catch (RuntimeException ex) { log.warn("Agent alert persistence failed: {}", ex.getMessage()); }
        }
    }

    private void addDailyBudgetAlert(List<Map<String, Object>> alerts, Map<String, Object> external) {
        Object rawUsage = external.get("dailyBudgetUsage");
        if (!(rawUsage instanceof Map<?, ?> usage)) return;
        long tokenLimit = Math.max(0L, dailyMaxTokens);
        long tokenUsage = number(usage.get("tokenUsage"));
        if (tokenLimit > 0 && tokenUsage >= Math.max(1L, Math.round(tokenLimit * 0.8D))) {
            alerts.add(alert(tokenUsage >= tokenLimit ? "CRITICAL" : "WARNING", "DAILY_TOKEN_BUDGET_PRESSURE",
                    "Agent 当日 Token 配额已使用 " + tokenUsage + "/" + tokenLimit));
        }
        java.math.BigDecimal costLimit = dailyMaxCost == null ? java.math.BigDecimal.ZERO : dailyMaxCost.max(java.math.BigDecimal.ZERO);
        java.math.BigDecimal costUsage = decimalValue(usage.get("costUsage"));
        if (costLimit.signum() > 0 && costUsage.compareTo(costLimit.multiply(new java.math.BigDecimal("0.8"))) >= 0) {
            alerts.add(alert(costUsage.compareTo(costLimit) >= 0 ? "CRITICAL" : "WARNING", "DAILY_COST_BUDGET_PRESSURE",
                    "Agent 当日费用配额已使用 " + costUsage + "/" + costLimit));
        }
        long taskLimit = Math.max(0L, dailyMaxTasks);
        long taskUsage = number(usage.get("taskCount"));
        if (taskLimit > 0 && taskUsage >= Math.max(1L, Math.round(taskLimit * 0.8D))) {
            alerts.add(alert(taskUsage >= taskLimit ? "CRITICAL" : "WARNING", "DAILY_TASK_BUDGET_PRESSURE",
                    "Agent 当日任务配额已使用 " + taskUsage + "/" + taskLimit));
        }
    }

    private void addTenantBudgetAlerts(List<Map<String, Object>> alerts) {
        List<Map<String, Object>> rows;
        try {
            rows = mapper.listTenantBudgetAlerts();
        } catch (RuntimeException ex) {
            log.warn("租户预算告警查询失败: {}", ex.getMessage());
            return;
        }
        if (rows == null) return;
        for (Map<String, Object> row : rows) {
            String tenantKey = String.valueOf(row.getOrDefault("tenantKey", "unknown"));
            addTenantBudgetAlert(alerts, tenantKey, "TOKEN", number(row.get("tokenUsage")),
                    number(row.get("maxDailyTokens")));
            addTenantBudgetAlert(alerts, tenantKey, "TASK", number(row.get("taskCount")),
                    number(row.get("maxDailyTasks")));
            java.math.BigDecimal cost = decimalValue(row.get("costUsage"));
            java.math.BigDecimal costLimit = decimalValue(row.get("maxDailyCost"));
            if (costLimit.signum() > 0 && cost.compareTo(costLimit.multiply(new java.math.BigDecimal("0.8"))) >= 0) {
                alerts.add(alert(cost.compareTo(costLimit) >= 0 ? "CRITICAL" : "WARNING",
                        "TENANT_COST_BUDGET_PRESSURE",
                        "租户 " + tenantKey + " 当日费用配额已使用 " + cost + "/" + costLimit));
            }
        }
    }

    private static void addTenantBudgetAlert(List<Map<String, Object>> alerts, String tenantKey,
                                             String kind, long usage, long limit) {
        if (limit <= 0 || usage < Math.max(1L, Math.round(limit * 0.8D))) return;
        alerts.add(alert(usage >= limit ? "CRITICAL" : "WARNING",
                "TENANT_" + kind + "_BUDGET_PRESSURE",
                "租户 " + tenantKey + " 当日" + ("TOKEN".equals(kind) ? " Token" : "任务")
                        + "配额已使用 " + usage + "/" + limit));
    }

    private static java.math.BigDecimal decimalValue(Object value) {
        try { return value == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(String.valueOf(value)); }
        catch (Exception ignored) { return java.math.BigDecimal.ZERO; }
    }

    private static void addConnectorAlert(List<Map<String, Object>> alerts, List<?> enabledKeys,
                                          String toolKey, Map<String, Object> external,
                                          String readinessKey, String message) {
        boolean enabled = enabledKeys.stream().anyMatch(key -> toolKey.equals(String.valueOf(key)));
        if (enabled && !Boolean.TRUE.equals(external.get(readinessKey))) {
            alerts.add(alert("WARNING", "CONNECTOR_NOT_READY", message));
        }
    }

    private static Map<String, Object> alert(String severity, String code, String message) {
        return Map.of("severity", severity, "code", code, "message", message, "readOnly", true);
    }

    private static long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0L; }
    }

    private Map<String, Object> externalReadiness() {
        boolean apiConfigured = configured(apiAllowedHosts);
        boolean webConfigured = configured(webSearchEndpoint) && configured(webSearchAllowedHosts);
        boolean browserConfigured = configured(browserAllowedHosts);
        boolean mcpConfigured = configured(mcpEndpoint) && configured(mcpAllowedHosts);
        boolean codeRunnerConfigured = validCodeRunnerImage(codeRunnerImage);
        boolean codeRunnerImagePinned = codeRunnerConfigured && codeRunnerImage.matches(".+@sha256:[0-9a-fA-F]{64}");
        boolean codeRunnerSandboxReady = codeRunnerConfigured && nonRootUser(codeRunnerUser)
                && (!codeRunnerRequireDigest || codeRunnerImagePinned);
        boolean webSecretInUrl = sensitiveUrl(webSearchEndpoint);
        boolean mcpSecretInUrl = sensitiveUrl(mcpEndpoint);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiRequest", apiConfigured);
        result.put("webSearch", webConfigured);
        result.put("browser", browserConfigured);
        result.put("mcp", mcpConfigured);
        result.put("codeRunner", codeRunnerSandboxReady);
        result.put("codeRunnerImageConfigured", codeRunnerConfigured);
        result.put("codeRunnerSandboxReady", codeRunnerSandboxReady);
        result.put("codeRunnerRunAsUser", codeRunnerSandboxReady ? codeRunnerUser : "INVALID");
        result.put("codeRunnerImagePinned", codeRunnerImagePinned);
        result.put("codeRunnerDigestRequired", codeRunnerRequireDigest);
        result.put("credentialsRequired", Map.of("apiRequest", false, "webSearch", false, "browser", false, "mcp", false, "codeRunner", false));
        result.put("credentialsConfigured", Map.of("apiRequest", configured(apiAuthToken), "webSearch", configured(webSearchAuthToken), "browser", false, "mcp", configured(mcpAuthToken), "codeRunner", false));
        result.put("credentialModes", Map.of("apiRequest", AgentCredentialSupport.credentialMode(apiAuthHeader, apiAuthToken), "webSearch", AgentCredentialSupport.credentialMode(webSearchAuthHeader, webSearchAuthToken), "mcp", AgentCredentialSupport.credentialMode(mcpAuthHeader, mcpAuthToken)));
        result.put("secretInUrlDetected", Map.of("webSearch", webSecretInUrl, "mcp", mcpSecretInUrl));
        result.put("dailyBudgetUsage", dailyBudgetUsage());
        result.put("secretsExposed", false);
        result.put("note", "仅报告连接和凭据状态，不显示端点、密钥或凭据；当前凭据通过环境变量注入，真实调用仍需工具白名单和审批");
        return result;
    }

    private static boolean nonRootUser(String value) {
        if (value == null || !value.matches("[0-9]{1,10}:[0-9]{1,10}")) return false;
        String[] ids = value.split(":", -1);
        try {
            return Long.parseLong(ids[0]) > 0 && Long.parseLong(ids[1]) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean validCodeRunnerImage(String value) {
        return configured(value) && !value.contains(" ") && !value.contains("\"") && !value.contains("'");
    }

    private Map<String, Object> dailyBudgetUsage() {
        try {
            Map<String, Object> usage = mapper.dailyBudgetUsage();
            return usage == null ? Map.of("tokenUsage", 0, "costUsage", java.math.BigDecimal.ZERO, "taskCount", 0) : usage;
        } catch (RuntimeException ex) {
            return Map.of("tokenUsage", 0, "costUsage", java.math.BigDecimal.ZERO, "taskCount", 0, "status", "unavailable");
        }
    }

    private static boolean sensitiveUrl(String value) {
        if (!configured(value)) return false;
        try { return AgentCredentialSupport.looksSensitiveQuery(URI.create(value)); }
        catch (Exception ignored) { return false; }
    }

    private static boolean configured(String value) {
        return value != null && !value.trim().isBlank();
    }

    private Map<String, Object> toolReadiness() {
        List<Map<String, Object>> rows = mapper.listTools(null, null, null, 0, MAX_PAGE_SIZE);
        long total = mapper.countTools(null, null, null);
        long enabled = rows == null ? 0 : rows.stream().filter(row -> bool(row.get("enabled"))).count();
        long approvalRequired = rows == null ? 0 : rows.stream()
                .filter(row -> bool(row.get("approvalRequired"))).count();
        Map<String, Long> riskCounts = new LinkedHashMap<>();
        List<String> enabledToolKeys = new java.util.ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String risk = String.valueOf(row.getOrDefault("riskLevel", "UNKNOWN"));
                riskCounts.put(risk, riskCounts.getOrDefault(risk, 0L) + 1L);
                if (bool(row.get("enabled")) && row.get("toolKey") != null && enabledToolKeys.size() < MAX_PAGE_SIZE) {
                    enabledToolKeys.add(String.valueOf(row.get("toolKey")));
                }
            }
        }
        return Map.of(
                "sampledTools", rows == null ? 0 : rows.size(),
                "totalTools", total,
                "truncated", total > MAX_PAGE_SIZE,
                "enabledTools", enabled,
                "enabledToolKeys", List.copyOf(enabledToolKeys),
                "approvalRequiredTools", approvalRequired,
                "riskCounts", riskCounts,
                "readOnly", true,
                "note", "工具启用、风险和审批信息仅用于查看；执行仍由 Worker 白名单和审批链裁决"
        );
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    public Map<String, Object> runtime() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", Instant.now().toString());
        result.put("service", "SillySpringboot");
        result.put("javaVersion", System.getProperty("java.version", ""));
        result.put("worker", Map.of(
                "enabled", true,
                "mode", "database-lease",
                "intervalSeconds", 5,
                "leaseSeconds", 60
        ));
        Map<String, Object> overview = overview();
        result.put("overview", overview);
        result.put("queue", Map.of(
                "runningTasks", mapper.listTasks("RUNNING", null, null, null, 0, 50),
                "readyTasks", mapper.listTasks("READY", null, null, null, 0, 50),
                "waitingApprovalTasks", mapper.listTasks("WAITING_APPROVAL", null, null, null, 0, 50),
                "pendingApprovals", mapper.listApprovals("PENDING", null, null, 0, 50)
        ));
        result.put("toolReadiness", overview.get("toolReadiness"));
        result.put("externalReadiness", overview.get("externalReadiness"));
        result.put("alerts", overview.get("alerts"));
        result.put("readOnly", true);
        result.put("note", "运行中心是只读快照；任务控制、审批和高风险操作必须通过各自受权限保护的接口执行");
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> releaseReadiness() {
        if (systemInspectionTool == null) return Map.of("status", "UNAVAILABLE", "readOnly", true);
        Map<String, Object> snapshot = systemInspectionTool.inspect(Map.of(), Map.of());
        Object readiness = snapshot.get("releaseReadiness");
        if (readiness instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            result.put("checkedAt", snapshot.get("checkedAt"));
            result.put("readOnly", true);
            return result;
        }
        return Map.of("status", "UNKNOWN", "readOnly", true);
    }

    private Map<String, Object> safeCapability(AiCapability capability) {
        try {
            return aiRoutingService.capabilitySummary(capability);
        } catch (RuntimeException ex) {
            return Map.of("capability", capability.name(), "ready", false, "status", "unavailable");
        }
    }

    private Map<String, Object> safeImageReadiness() {
        try {
            return imageReadinessService.toMap(imageReadinessService.systemSnapshot());
        } catch (RuntimeException ex) {
            return Map.of("ready", false, "statusCode", "unavailable");
        }
    }

    public PageResult listTasks(int pageNum, int pageSize, String status, String riskLevel, String tenantKey, String keyword) {
        String safeStatus = optionalEnum(status, TASK_STATUSES, "任务状态");
        String safeRiskLevel = optionalEnum(riskLevel, RISK_LEVELS, "风险等级");
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countTasks(safeStatus, safeRiskLevel, safeTenantKey(tenantKey), clip(keyword, 128)),
                mapper.listTasks(safeStatus, safeRiskLevel, safeTenantKey(tenantKey), clip(keyword, 128), page.offset(), page.size())
        );
    }

    public TaskDetail findTask(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("任务编号无效");
        }
        Map<String, Object> task = mapper.findTask(id);
        if (task == null || task.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> artifacts = mapper.listArtifacts(id);
        return new TaskDetail(task, mapper.findLatestPlan(id), mapper.listSteps(id), mapper.listToolCalls(id),
                mapper.listExternalOperations(id), artifacts, evaluationArtifact(artifacts));
    }

    public List<Map<String, Object>> listEvents(long taskId, long afterSeq, int limit) {
        validateId(taskId, "任务编号");
        long safeAfterSeq = Math.max(0, afterSeq);
        int safeLimit = Math.max(1, Math.min(MAX_EVENT_PAGE_SIZE, limit));
        return mapper.listEvents(taskId, safeAfterSeq, safeLimit);
    }

    public List<Map<String, Object>> listArtifacts(long taskId) {
        validateId(taskId, "任务编号");
        return mapper.listArtifacts(taskId);
    }

    public Map<String, Object> findArtifact(long taskId, String artifactKey) {
        validateId(taskId, "任务编号");
        String key = artifactKey == null ? "" : artifactKey.trim();
        if (key.isBlank() || key.length() > 160) {
            throw new IllegalArgumentException("产物编号无效");
        }
        return mapper.findArtifact(taskId, key, 1);
    }

    private Map<String, Object> evaluationArtifact(List<Map<String, Object>> artifacts) {
        if (artifacts == null) return null;
        for (Map<String, Object> artifact : artifacts) {
            if (!"agent-evaluation".equals(String.valueOf(artifact.get("artifactKey")))) continue;
            Object content = artifact.get("contentJson");
            if (content == null || String.valueOf(content).isBlank()) return null;
            try {
                JsonNode node = objectMapper.readTree(String.valueOf(content));
                if (node == null || !node.isObject()) return null;
                Map<String, Object> result = new LinkedHashMap<>();
                node.fields().forEachRemaining(entry -> result.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
                return result;
            } catch (Exception ex) {
                log.warn("Agent evaluation artifact parse failed: {}", ex.getMessage());
                return null;
            }
        }
        return null;
    }

    public Map<String, Object> findApproval(long approvalId) {
        validateId(approvalId, "审批编号");
        return mapper.findApproval(approvalId);
    }

    public PageResult listApprovals(int pageNum, int pageSize, String status, String keyword, String tenantKey) {
        String safeStatus = optionalEnum(status, APPROVAL_STATUSES, "审批状态");
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countApprovals(safeStatus, clip(keyword, 128), safeTenantKey(tenantKey)),
                mapper.listApprovals(safeStatus, clip(keyword, 128), safeTenantKey(tenantKey), page.offset(), page.size())
        );
    }

    public PageResult listTools(int pageNum, int pageSize, String category, Boolean enabled, String keyword) {
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countTools(clip(category, 64), enabled, clip(keyword, 128)),
                mapper.listTools(clip(category, 64), enabled, clip(keyword, 128), page.offset(), page.size())
        );
    }

    public Map<String, Object> findTool(long id) {
        validateId(id, "工具编号");
        return mapper.findTool(id);
    }

    public PageResult listAgentRoles(int pageNum, int pageSize, Boolean enabled, String keyword) {
        PageWindow page = page(pageNum, pageSize);
        String safeKeyword = clip(keyword, 128);
        return new PageResult(
                mapper.countAgentRoles(enabled, safeKeyword),
                mapper.listAgentRoles(enabled, safeKeyword, page.offset(), page.size())
        );
    }

    public Map<String, Object> findAgentRole(long id) {
        validateId(id, "Agent 角色编号");
        return mapper.findAgentRole(id);
    }

    /**
     * Internal lookup for a future planner/executor. The system prompt is never
     * returned by the administrator-facing list/detail queries above.
     */
    public Map<String, Object> findEnabledAgentRole(String roleKey, int version) {
        String safeRoleKey = clip(roleKey, 80);
        if (safeRoleKey == null || version <= 0) {
            throw new IllegalArgumentException("Agent 角色版本无效");
        }
        return mapper.findAgentRoleForExecution(safeRoleKey, version);
    }

    public PageResult listWorkflowTemplates(int pageNum,
                                            int pageSize,
                                            Boolean enabled,
                                            String taskType,
                                            String keyword) {
        PageWindow page = page(pageNum, pageSize);
        String safeTaskType = optionalTaskType(taskType);
        String safeKeyword = clip(keyword, 128);
        return new PageResult(
                mapper.countWorkflowTemplates(enabled, safeTaskType, safeKeyword),
                mapper.listWorkflowTemplates(enabled, safeTaskType, safeKeyword, page.offset(), page.size())
        );
    }

    public Map<String, Object> findWorkflowTemplate(long id) {
        validateId(id, "Agent 工作流编号");
        return mapper.findWorkflowTemplate(id);
    }

    public PageResult listTenants(int pageNum, int pageSize, String status, String keyword) {
        String safeStatus = optionalEnum(status, Set.of("ACTIVE", "SUSPENDED", "DISABLED"), "租户状态");
        String safeKeyword = clip(keyword, 128);
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countTenants(safeStatus, safeKeyword),
                mapper.listTenants(safeStatus, safeKeyword, page.offset(), page.size())
        );
    }

    public Map<String, Object> findTenant(String tenantKey) {
        return mapper.findTenant(safeTenantKey(tenantKey));
    }

    public PageResult listTenantMembers(int pageNum, int pageSize, String tenantKey, String status) {
        String safeTenant = safeTenantKey(tenantKey);
        if (safeTenant == null) throw new IllegalArgumentException("tenantKey 不能为空");
        String safeStatus = optionalEnum(status, Set.of("ACTIVE", "SUSPENDED", "REMOVED"), "成员状态");
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countTenantMembers(safeTenant, safeStatus),
                mapper.listTenantMembers(safeTenant, safeStatus, page.offset(), page.size())
        );
    }

    public PageResult listUsageLedger(int pageNum, int pageSize, String tenantKey, Long taskId) {
        Long safeTask = taskId == null || taskId <= 0 ? null : taskId;
        String safeTenant = safeTenantKey(tenantKey);
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countUsageLedger(safeTenant, safeTask),
                mapper.listUsageLedger(safeTenant, safeTask, page.offset(), page.size())
        );
    }

    public Map<String, Object> tenantUsageSummary(String tenantKey) {
        String safeTenant = safeTenantKey(tenantKey);
        if (safeTenant == null) throw new IllegalArgumentException("tenantKey 不能为空");
        if (mapper.findTenant(safeTenant) == null) throw new IllegalArgumentException("Agent 租户不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", mapper.tenantUsageSummary(safeTenant));
        result.put("tenant", mapper.findTenant(safeTenant));
        result.put("readOnly", true);
        return result;
    }

    public PageResult listConfigAudits(int pageNum,
                                       int pageSize,
                                       String configType,
                                       String configKey,
                                       String action,
                                       Long operatorId,
                                       String keyword) {
        Set<String> types = Set.of("ROLE", "WORKFLOW");
        Set<String> actions = Set.of("CREATE", "ACTIVATE");
        String safeType = optionalEnum(configType, types, "配置类型");
        String safeAction = optionalEnum(action, actions, "配置动作");
        String safeKey = clip(configKey, 80);
        String safeKeyword = clip(keyword, 128);
        Long safeOperator = operatorId == null || operatorId <= 0 ? null : operatorId;
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countConfigAudits(safeType, safeKey, safeAction, safeOperator, safeKeyword),
                mapper.listConfigAudits(safeType, safeKey, safeAction, safeOperator, safeKeyword, page.offset(), page.size())
        );
    }

    public Map<String, Object> budget(int pageNum,
                                      int pageSize,
                                      String taskType,
                                      String status,
                                      Boolean overBudget,
                                      String tenantKey) {
        String safeTaskType = clip(taskType, 64);
        if (safeTaskType != null) safeTaskType = safeTaskType.toUpperCase();
        String safeStatus = optionalEnum(status, TASK_STATUSES, "任务状态");
        PageWindow page = page(pageNum, pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        String safeTenant = safeTenantKey(tenantKey);
        result.put("summary", mapper.budgetSummary(safeTaskType, safeStatus, safeTenant));
        result.put("dailyBudgetUsage", dailyBudgetUsage());
        result.put("dailyBudgetLimits", Map.of(
                "maxTokens", Math.max(0L, dailyMaxTokens),
                "maxCost", dailyMaxCost == null ? java.math.BigDecimal.ZERO : dailyMaxCost.max(java.math.BigDecimal.ZERO),
                "maxTasks", Math.max(0L, dailyMaxTasks),
                "enforced", dailyMaxTokens > 0 || (dailyMaxCost != null && dailyMaxCost.signum() > 0) || dailyMaxTasks > 0
        ));
        result.put("total", mapper.countBudgetTasks(safeTaskType, safeStatus, overBudget, safeTenant));
        result.put("rows", mapper.listBudgetTasks(safeTaskType, safeStatus, overBudget, safeTenant, page.offset(), page.size()));
        return result;
    }

    public Map<String, Object> evaluation(int pageNum, int pageSize, String taskType,
                                          String verdict, Integer minimumScore, Boolean gatePassed, String tenantKey) {
        String safeTaskType = clip(taskType, 64);
        if (safeTaskType != null) safeTaskType = safeTaskType.toUpperCase();
        String safeVerdict = verdict == null ? null : verdict.trim().toUpperCase();
        if (safeVerdict != null && !Set.of("PASS", "WARN", "FAIL", "INVALID").contains(safeVerdict)) {
            throw new IllegalArgumentException("评估结论无效");
        }
        Integer safeMinimumScore = minimumScore == null ? null : Math.max(0, Math.min(100, minimumScore));
        PageWindow page = page(pageNum, pageSize);
        List<Map<String, Object>> source = mapper.listEvaluationArtifacts(
                safeTaskType, safeVerdict, safeMinimumScore, gatePassed, safeTenantKey(tenantKey), page.offset(), page.size());
        java.util.ArrayList<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Map<String, Object> item : source) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            Object raw = row.remove("contentJson");
            if (raw != null) {
                try {
                    JsonNode evaluation = objectMapper.readTree(String.valueOf(raw));
                    if (evaluation != null && evaluation.isObject()) {
                        evaluation.fields().forEachRemaining(field -> {
                            String name = field.getKey();
                            if (Set.of("score", "verdict", "stepSuccessRate", "artifactReadyRate", "totalSteps",
                                    "succeededSteps", "failedSteps", "totalArtifacts", "readyArtifacts",
                                    "degradedArtifacts", "degradedSteps", "unpricedCalls", "recommendations",
                                    "minimumEvaluationScore", "evaluationGatePassed").contains(name)) {
                                row.put(name, objectMapper.convertValue(field.getValue(), Object.class));
                            }
                        });
                    }
                } catch (Exception ignored) {
                    row.put("verdict", "INVALID");
                }
            }
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filters", Map.of("taskType", safeTaskType == null ? "" : safeTaskType,
                "verdict", safeVerdict == null ? "" : safeVerdict,
                "minimumScore", safeMinimumScore == null ? "" : safeMinimumScore,
                "gatePassed", gatePassed == null ? "" : gatePassed));
        String safeTenant = safeTenantKey(tenantKey);
        result.put("summary", mapper.evaluationSummary(safeTaskType, safeVerdict, safeMinimumScore, gatePassed, safeTenant));
        result.put("total", mapper.countEvaluationArtifacts(safeTaskType, safeVerdict, safeMinimumScore, gatePassed, safeTenant));
        result.put("rows", rows);
        return result;
    }

    private static String safeTenantKey(String value) {
        if (value == null || value.isBlank()) return null;
        String key = value.trim();
        if (key.length() > 120 || !key.matches("[a-z0-9][a-z0-9:_-]{0,119}")) {
            throw new IllegalArgumentException("tenantKey 格式无效");
        }
        return key;
    }

    private static String optionalTaskType(String value) {
        String normalized = clip(value, 64);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private static PageWindow page(int pageNum, int pageSize) {
        int safePage = Math.max(1, Math.min(MAX_PAGE, pageNum));
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        long offset = (long) (safePage - 1) * safeSize;
        if (offset > Integer.MAX_VALUE) {
            offset = Integer.MAX_VALUE;
        }
        return new PageWindow((int) offset, safeSize);
    }

    private static String optionalEnum(String value, Set<String> allowed, String label) {
        String normalized = clip(value, 64);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase();
        if (!allowed.contains(upper)) {
            throw new IllegalArgumentException(label + "无效");
        }
        return upper;
    }

    private static String clip(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static void validateId(long id, String label) {
        if (id <= 0) {
            throw new IllegalArgumentException(label + "无效");
        }
    }

    public record PageResult(long total, List<Map<String, Object>> rows) {}

    public record TaskDetail(Map<String, Object> task,
                             Map<String, Object> plan,
                             List<Map<String, Object>> steps,
                             List<Map<String, Object>> toolCalls,
                             List<Map<String, Object>> externalOperations,
                             List<Map<String, Object>> artifacts,
                             Map<String, Object> evaluation) {}

    private record PageWindow(int offset, int size) {}
}
