package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.tool.AgentEvaluationTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentTaskService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskService.class);

    private static final Set<String> RISK_LEVELS = Set.of(
            "READ_ONLY", "WRITE_REVERSIBLE", "EXTERNAL_SIDE_EFFECT", "DESTRUCTIVE", "PRODUCTION_OPERATION"
    );
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "DRAFT", "PLANNING", "WAITING_APPROVAL", "READY", "RUNNING", "PAUSED", "WAITING_CONFIRMATION", "CANCEL_REQUESTED"
    );
    private static final Set<String> RETRYABLE_STATUSES = Set.of("FAILED", "PARTIAL_SUCCESS");

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;
    private final AgentEvaluationTool evaluationTool;
    private final int platformMaxSteps;
    private final long platformMaxTokens;
    private final BigDecimal platformMaxCost;
    @Value("${app.agent.daily-max-tokens:0}")
    private long dailyMaxTokens;
    @Value("${app.agent.daily-max-cost:0}")
    private BigDecimal dailyMaxCost;
    @Value("${app.agent.daily-max-tasks:0}")
    private long dailyMaxTasks;

    public AgentTaskService(AgentAdminMapper mapper, ObjectMapper objectMapper) {
        this(mapper, objectMapper, null, 32, 100_000_000L, new BigDecimal("1000000"));
    }

    public AgentTaskService(AgentAdminMapper mapper, ObjectMapper objectMapper, AgentEvaluationTool evaluationTool) {
        this(mapper, objectMapper, evaluationTool, 32, 100_000_000L, new BigDecimal("1000000"));
    }

    @Autowired
    public AgentTaskService(AgentAdminMapper mapper, ObjectMapper objectMapper, AgentEvaluationTool evaluationTool,
                            @Value("${app.agent.platform-max-steps:32}") int platformMaxSteps,
                            @Value("${app.agent.platform-max-tokens:100000000}") long platformMaxTokens,
                            @Value("${app.agent.platform-max-cost:1000000}") BigDecimal platformMaxCost) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.evaluationTool = evaluationTool;
        this.platformMaxSteps = Math.max(1, Math.min(platformMaxSteps, 500));
        this.platformMaxTokens = Math.max(0L, platformMaxTokens);
        this.platformMaxCost = platformMaxCost == null || platformMaxCost.signum() < 0
                ? BigDecimal.ZERO : platformMaxCost;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, Long adminId) {
        return createInternal(body, adminId, false);
    }

    /** Backend administrators may create platform or authorized tenant tasks. */
    @Transactional
    public Map<String, Object> createAdminTask(Map<String, Object> body, Long adminId) {
        return createInternal(body, adminId, true);
    }

    private Map<String, Object> createInternal(Map<String, Object> body, Long adminId, boolean adminScope) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String objective = text(body.get("objective"), 20_000);
        if (objective == null) {
            throw new IllegalArgumentException("任务目标不能为空");
        }
        String title = text(body.get("title"), 255);
        if (title == null) {
            title = objective.length() <= 80 ? objective : objective.substring(0, 80);
        }
        String taskType = defaultText(body.get("taskType"), "GENERAL", 64);
        String riskLevel = defaultText(body.get("riskLevel"), "READ_ONLY", 32).toUpperCase();
        if (!RISK_LEVELS.contains(riskLevel)) {
            throw new IllegalArgumentException("风险等级无效");
        }

        Map<String, Object> task = new LinkedHashMap<>();
        String idempotencyKey = text(body.get("idempotencyKey"), 128);
        String taskKey = idempotencyKey == null
                ? "agent-" + UUID.randomUUID().toString().replace("-", "")
                : "agent-idem-" + sha256((adminId == null ? 0L : adminId) + ":" + idempotencyKey).substring(0, 48);
        if (idempotencyKey != null) {
            Map<String, Object> existing = mapper.findTaskByKey(taskKey);
            if (existing != null && sameOwner(existing.get("createdBy"), adminId)) return existing;
        }
        task.put("taskKey", taskKey);
        task.put("taskType", taskType);
        task.put("title", title);
        task.put("objective", objective);
        task.put("constraintsJson", constraintsJson(body.get("constraints"), body.get("constraintsJson")));
        task.put("status", "DRAFT");
        task.put("riskLevel", riskLevel);
        task.put("createdBy", adminId);
        task.put("tenantKey", tenantKey(body.get("tenantKey"), adminId, adminScope));
        task.put("priority", boundedInt(body.get("priority"), 0, -100, 100));
        task.put("maxSteps", boundedInt(body.get("maxSteps"), Math.min(50, platformMaxSteps), 1, platformMaxSteps));
        task.put("maxRetries", boundedInt(body.get("maxRetries"), 3, 0, 10));
        task.put("maxRuntimeSeconds", boundedInt(body.get("maxRuntimeSeconds"), 3600, 60, 86_400));
        task.put("maxTokens", boundedLong(body.get("maxTokens"), 0L, 0L, platformMaxTokens));
        task.put("maxCost", boundedDecimal(body.get("maxCost"), BigDecimal.ZERO, BigDecimal.ZERO, platformMaxCost));

        reserveTenantTaskQuota(String.valueOf(task.get("tenantKey")));
        if (dailyBudgetExceeded()) {
            throw new IllegalStateException("Agent 平台当日 Token 或费用配额已用尽，请稍后再试");
        }

        if (mapper.insertTask(task) != 1) {
            throw new IllegalStateException("Agent 任务创建失败");
        }
        long id = ((Number) task.get("id")).longValue();
        insertEvent(id, "TASK_CREATED", "Agent 任务已创建", null);
        Map<String, Object> persisted = mapper.findTask(id);
        if (persisted == null) {
            throw new IllegalStateException("Agent 任务创建后读取失败");
        }
        return persisted;
    }

    private boolean dailyBudgetExceeded() {
        long tokens = Math.max(0L, dailyMaxTokens);
        BigDecimal cost = dailyMaxCost == null || dailyMaxCost.signum() < 0 ? BigDecimal.ZERO : dailyMaxCost;
        if ((tokens > 0L || cost.signum() > 0) && mapper.platformBudgetExceeded(tokens, cost) == 1) return true;
        if (dailyMaxTasks > 0) {
            Map<String, Object> usage = mapper.dailyBudgetUsage();
            if (usage != null && number(usage.get("taskCount")) >= dailyMaxTasks) return true;
        }
        return false;
    }

    private static boolean sameOwner(Object owner, Long userId) {
        if (userId == null || owner == null) return userId == null && owner == null;
        try { return Long.parseLong(String.valueOf(owner)) == userId; }
        catch (Exception ignored) { return false; }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    @Transactional
    public Map<String, Object> control(long id, String action) {
        if (id <= 0) {
            throw new IllegalArgumentException("任务编号无效");
        }
        Map<String, Object> current = mapper.findTask(id);
        if (current == null || current.isEmpty()) {
            throw new IllegalArgumentException("Agent 任务不存在");
        }
        String currentStatus = String.valueOf(current.get("status"));
        Transition transition = transition(currentStatus, action);
        int updated = mapper.updateTaskStatus(
                id,
                List.of(currentStatus),
                transition.toStatus(),
                transition.clearError(),
                transition.retryIncrement(),
                transition.terminal()
        );
        if (updated != 1) {
            throw new IllegalStateException("任务状态已变化，请刷新后重试");
        }
        if ("CANCELLED".equals(transition.toStatus())) {
            mapper.cancelPendingSteps(id);
        }
        int resetFailedSteps = 0;
        int resetDegradedSteps = 0;
        if ("RETRY".equalsIgnoreCase(action) || "REPLAN".equalsIgnoreCase(action)) {
            resetFailedSteps = mapper.resetFailedSteps(id);
        }
        if ("REPLAN".equalsIgnoreCase(action)) {
            resetDegradedSteps = mapper.resetDegradedSteps(id);
        }
        insertEvent(id, transition.eventType(), transition.summary(), null);
        if ("REPLAN".equalsIgnoreCase(action)) {
            insertEvent(id, "TASK_REPLAN_STEPS_RESET",
                    "有限重规划已重置步骤：失败/取消 " + resetFailedSteps + "，降级及下游 " + resetDegradedSteps,
                    null);
        }
        if (transition.terminal()) {
            saveEvaluation(id);
        }
        return mapper.findTask(id);
    }

    private void saveEvaluation(long taskId) {
        if (evaluationTool == null) return;
        try {
            Map<String, Object> task = mapper.findTask(taskId);
            if (task != null) evaluationTool.saveArtifact(taskId, 0L, task);
        } catch (RuntimeException ex) {
            log.warn("Agent cancellation evaluation save failed taskId={} cause={}", taskId, ex.getMessage());
        }
    }

    private Transition transition(String currentStatus, String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase();
        return switch (normalized) {
            case "PAUSE" -> {
                require(currentStatus, Set.of("READY", "RUNNING"), "当前状态不能暂停");
                yield new Transition("PAUSED", false, 0, false, "TASK_PAUSED", "Agent 任务已暂停");
            }
            case "RESUME" -> {
                require(currentStatus, Set.of("PAUSED"), "当前状态不能继续");
                yield new Transition("READY", true, 0, false, "TASK_RESUMED", "Agent 任务已恢复");
            }
            case "CANCEL" -> {
                require(currentStatus, ACTIVE_STATUSES, "当前状态不能取消");
                if ("RUNNING".equals(currentStatus)) {
                    yield new Transition("CANCEL_REQUESTED", false, 0, false, "TASK_CANCEL_REQUESTED", "已请求取消 Agent 任务");
                }
                yield new Transition("CANCELLED", false, 0, true, "TASK_CANCELLED", "Agent 任务已取消");
            }
            case "RETRY", "REPLAN" -> {
                require(currentStatus, RETRYABLE_STATUSES, "当前状态不能重试");
                String eventType = "REPLAN".equals(normalized) ? "TASK_REPLAN_REQUESTED" : "TASK_RETRY_REQUESTED";
                String summary = "REPLAN".equals(normalized)
                        ? "Agent 任务已按现有计划有限重规划并进入执行队列"
                        : "Agent 任务已进入重试队列";
                yield new Transition("READY", true, 1, false, eventType, summary);
            }
            default -> throw new IllegalArgumentException("任务操作无效");
        };
    }

    private void insertEvent(long taskId, String eventType, String summary, Long stepId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("taskId", taskId);
        event.put("stepId", stepId);
        event.put("eventType", eventType);
        event.put("visibility", "PUBLIC");
        event.put("summary", summary);
        event.put("payloadJson", null);
        if (mapper.insertEvent(event) != 1) {
            throw new IllegalStateException("Agent 任务事件写入失败");
        }
    }

    private static void require(String currentStatus, Set<String> allowed, String message) {
        if (!allowed.contains(currentStatus)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String constraintsJson(Object constraints, Object rawJson) {
        if (rawJson != null) {
            String text = text(rawJson, 20_000);
            if (text != null) {
                return text;
            }
        }
        if (constraints == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("约束条件格式无效");
        }
    }

    private void reserveTenantTaskQuota(String tenantKey) {
        Map<String, Object> tenant = mapper.findTenant(tenantKey);
        if (tenant == null && tenantKey.startsWith("user:")) {
            Long ownerId = parseUserTenantId(tenantKey);
            mapper.ensureTenant(tenantKey, "User " + ownerId, ownerId);
            tenant = mapper.findTenant(tenantKey);
        }
        if (tenant == null) throw new IllegalStateException("Agent 租户不存在");
        if (!"ACTIVE".equalsIgnoreCase(String.valueOf(tenant.getOrDefault("status", "DISABLED")))) {
            throw new IllegalStateException("Agent 租户当前未启用，不能创建任务");
        }
        Map<String, Object> budget = mapper.findTenantBudget(tenantKey);
        if (budget == null || !booleanValue(budget.get("enabled"))) return;
        Map<String, Object> usage = mapper.tenantDailyBudgetUsage(tenantKey);
        long maxTokens = valueOrZero(number(budget.get("maxDailyTokens")));
        long maxTasks = valueOrZero(number(budget.get("maxDailyTasks")));
        BigDecimal maxCost = decimalValue(budget.get("maxDailyCost"));
        if (maxTokens > 0 && valueOrZero(number(usage.get("tokenUsage"))) >= maxTokens) throw new IllegalStateException("租户当日 Token 配额已用尽");
        if (maxCost.signum() > 0 && decimalValue(usage.get("costUsage")).compareTo(maxCost) >= 0) throw new IllegalStateException("租户当日费用配额已用尽");
        if (maxTasks <= 0) return;
        mapper.resetTenantDailyTaskReservation(tenantKey);
        if (mapper.reserveTenantDailyTask(tenantKey) != 1) {
            throw new IllegalStateException("租户当日任务配额已用尽");
        }
    }

    private static Long parseUserTenantId(String tenantKey) {
        try {
            long id = Long.parseLong(tenantKey.substring("user:".length()));
            if (id > 0) return id;
        } catch (Exception ignored) { }
        throw new IllegalArgumentException("用户租户标识无效");
    }

    private static boolean booleanValue(Object value) { return value instanceof Boolean b ? b : "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value)); }
    private static long valueOrZero(Long value) { return value == null ? 0L : Math.max(0L, value); }
    private static BigDecimal decimalValue(Object value) { try { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); } catch (Exception ignored) { return BigDecimal.ZERO; } }

    private static String tenantKey(Object raw, Long ownerId, boolean adminScope) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.isBlank()) {
            return "platform";
        }
        if (value.length() > 120 || !value.matches("[a-z0-9][a-z0-9:_-]{0,119}")) {
            throw new IllegalArgumentException("tenantKey 格式无效");
        }
        if (!adminScope && ownerId != null && ownerId > 0 && !value.equals("user:" + ownerId)) {
            throw new IllegalArgumentException("用户任务不能指定其他 tenantKey");
        }
        return value;
    }

    private static String defaultText(Object value, String fallback, int maxLength) {
        String text = text(value, maxLength);
        return text == null ? fallback : text;
    }

    private static String text(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static int boundedInt(Object value, int fallback, int min, int max) {
        Long number = number(value);
        if (number == null) {
            return fallback;
        }
        if (number < min || number > max) {
            throw new IllegalArgumentException("任务参数超出允许范围");
        }
        return number.intValue();
    }

    private static long boundedLong(Object value, long fallback, long min, long max) {
        Long number = number(value);
        if (number == null) {
            return fallback;
        }
        if (number < min || number > max) {
            throw new IllegalArgumentException("任务参数超出允许范围");
        }
        return number;
    }

    private static BigDecimal boundedDecimal(Object value, BigDecimal fallback, BigDecimal min, BigDecimal max) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            BigDecimal number = new BigDecimal(String.valueOf(value));
            if (number.compareTo(min) < 0 || number.compareTo(max) > 0) {
                throw new IllegalArgumentException("任务参数超出允许范围");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("费用预算格式无效");
        }
    }

    private static Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("任务参数格式无效");
            }
        }
        return null;
    }

    private record Transition(
            String toStatus,
            boolean clearError,
            int retryIncrement,
            boolean terminal,
            String eventType,
            String summary
    ) {}
}
