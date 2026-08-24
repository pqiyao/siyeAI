package com.example.sillyspringboot.agent.executor;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.security.AgentParameterHasher;
import com.example.sillyspringboot.agent.tool.AgentBuiltinToolExecutor;
import com.example.sillyspringboot.agent.tool.AgentEvaluationTool;
import com.example.sillyspringboot.agent.service.AgentPlannerModelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AgentWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AgentWorkerService.class);
    private static final int BATCH_SIZE = 5;
    private static final int MAX_RUNTIME_REPLAN_STEPS = 8;
    private static final Set<String> RUNTIME_REPLAN_ALLOWED_TOOLS = Set.of(
            "planner.analyze", "worldbook.create", "character.create", "character.validate",
            "character.publish", "character.sync_st", "story.create_outline", "story.write_chapter",
            "story.check_continuity", "story.revise", "image.generate", "tts.generate",
            "artifact.render_html", "artifact.package_zip", "analytics.query", "analytics.review",
            "artifact.render_report", "artifact.evaluate", "content.moderation.review",
            "content.moderation.action", "user.profile.query", "user.ticket.query", "user.ticket.reply",
            "user.account.query", "user.account.credit", "user.risk.query", "agent.handoff",
            "system.health_check", "api.request", "web.search", "browser.open", "mcp.call", "code.run"
    );
    private static final int LEASE_SECONDS = 60;
    private static final String WORKER_ID = "agent-worker-" + UUID.randomUUID().toString().replace("-", "");
    private static final ScheduledExecutorService LEASE_EXECUTOR = Executors.newScheduledThreadPool(
            1,
            runnable -> {
                Thread thread = new Thread(runnable, "agent-lease-renewer");
                thread.setDaemon(true);
                return thread;
            }
    );

    private final AgentAdminMapper mapper;
    private final AgentBuiltinToolExecutor toolExecutor;
    private final AgentEvaluationTool evaluationTool;
    private final ObjectMapper objectMapper;
    private final AgentPlannerModelService plannerModelService;
    @Value("${app.agent.daily-max-tokens:0}")
    private long platformDailyMaxTokens;
    @Value("${app.agent.daily-max-cost:0}")
    private BigDecimal platformDailyMaxCost;

    public AgentWorkerService(AgentAdminMapper mapper, AgentBuiltinToolExecutor toolExecutor) {
        this(mapper, toolExecutor, new ObjectMapper(), null, null);
    }

    public AgentWorkerService(AgentAdminMapper mapper, AgentBuiltinToolExecutor toolExecutor, ObjectMapper objectMapper) {
        this(mapper, toolExecutor, objectMapper, null, null);
    }

    public AgentWorkerService(AgentAdminMapper mapper, AgentBuiltinToolExecutor toolExecutor,
                              ObjectMapper objectMapper, AgentEvaluationTool evaluationTool) {
        this(mapper, toolExecutor, objectMapper, evaluationTool, null);
    }

    @Autowired
    public AgentWorkerService(AgentAdminMapper mapper, AgentBuiltinToolExecutor toolExecutor,
                              ObjectMapper objectMapper, AgentEvaluationTool evaluationTool,
                              AgentPlannerModelService plannerModelService) {
        this.mapper = mapper;
        this.toolExecutor = toolExecutor;
        this.evaluationTool = evaluationTool;
        this.objectMapper = objectMapper;
        this.plannerModelService = plannerModelService;
    }

    @Scheduled(
            initialDelayString = "${app.agent.worker-initial-delay-ms:5000}",
            fixedDelayString = "${app.agent.worker-interval-ms:5000}"
    )
    public void dispatchReadySteps() {
        try {
            LocalDateTime reconcileAt = LocalDateTime.now();
            List<Map<String, Object>> recoverable = mapper.listRecoverableExpiredSteps(reconcileAt);
            int recovered = mapper.recoverExpiredSteps(reconcileAt);
            if (recovered > 0 && recoverable != null) {
                for (Map<String, Object> row : recoverable) {
                    long taskId = number(row.get("taskId"));
                    long stepId = number(row.get("stepId"));
                    if (taskId > 0 && stepId > 0) {
                        insertEvent(taskId, stepId, "STEP_LEASE_RECOVERED", "Worker 租约已过期，步骤重新排队");
                    }
                }
            }
            int exhausted = mapper.failExhaustedSteps(LocalDateTime.now());
            int failedTasks = mapper.failTasksWithFailedSteps();
            int cancelledOrphanSteps = mapper.cancelStepsForFailedTasks();
            int expiredSteps = mapper.failExpiredApprovalSteps();
            int expiredTasks = mapper.failExpiredApprovalTasks();
            int expiredPlans = mapper.rejectExpiredPlans();
            int expiredApprovals = mapper.expirePendingApprovals();
            if (recovered > 0) {
                log.warn("Agent worker recovered expired steps count={}", recovered);
            }
            if (exhausted > 0 || failedTasks > 0 || cancelledOrphanSteps > 0 || expiredSteps > 0 || expiredTasks > 0 || expiredPlans > 0 || expiredApprovals > 0) {
                log.warn("Agent worker reconciled leases/approvals exhaustedSteps={} failedTasks={} cancelledOrphanSteps={} expiredSteps={} expiredTasks={} expiredPlans={} expiredApprovals={}",
                        exhausted, failedTasks, cancelledOrphanSteps, expiredSteps, expiredTasks, expiredPlans, expiredApprovals);
            }
            List<Map<String, Object>> candidates = mapper.listReadySteps(BATCH_SIZE);
            if (candidates == null || candidates.isEmpty()) {
                return;
            }
            for (Map<String, Object> candidate : candidates) {
                processCandidate(candidate);
            }
        } catch (RuntimeException ex) {
            log.warn("Agent worker dispatch failed: {}", rootCauseMessage(ex));
        }
    }

    private void processCandidate(Map<String, Object> candidate) {
        long stepId = number(candidate.get("stepId"));
        long taskId = number(candidate.get("taskId"));
        if (stepId <= 0 || taskId <= 0) {
            return;
        }
        String owner = WORKER_ID + ":" + stepId;
        if (mapper.claimStep(stepId, owner, LocalDateTime.now().plusSeconds(LEASE_SECONDS)) != 1) {
            return;
        }

        ScheduledFuture<?> leaseRenewal = LEASE_EXECUTOR.scheduleAtFixedRate(
                () -> renewLease(stepId, owner),
                15L,
                15L,
                TimeUnit.SECONDS
        );

        int attempt = (int) Math.max(1L, number(candidate.get("attemptCount")) + 1L);
        Map<String, Object> toolCall = new LinkedHashMap<>();
        long toolCallId = 0L;
        long startedNanos = System.nanoTime();
        long startingTokens = whole(candidate.get("tokenUsage"));
        BigDecimal startingCost = decimal(candidate.get("costAmount"));
        try {
            markTaskRunning(taskId);
            if (mapper.taskRuntimeExceeded(taskId) == 1) {
                stopForRuntime(taskId, stepId, owner);
                return;
            }
            if (mapper.taskBudgetExceeded(taskId) == 1) {
                stopForBudget(taskId, stepId, owner);
                return;
            }
            if (mapper.tenantDailyBudgetExceeded(tenantKey(candidate)) == 1) {
                stopForTenantBudget(taskId, stepId, owner);
                return;
            }
            if (!tenantActive(tenantKey(candidate))) {
                stopForTenantStatus(taskId, stepId, owner);
                return;
            }
            if (platformBudgetExceeded()) {
                stopForPlatformBudget(taskId, stepId, owner);
                return;
            }
            Map<String, Object> toolDefinition = mapper.findToolByKey(String.valueOf(candidate.getOrDefault("toolName", "")));
            if (toolDefinition == null || !Boolean.TRUE.equals(asBoolean(toolDefinition.get("enabled")))) {
                throw new IllegalStateException("工具未注册或已禁用: " + candidate.get("toolName"));
            }
            requireAllowedRole(toolDefinition, candidate);
            if (Boolean.TRUE.equals(asBoolean(toolDefinition.get("approvalRequired")))) {
                requestToolApproval(taskId, stepId, toolDefinition, candidate);
                return;
            }
            toolCall.put("taskId", taskId);
            toolCall.put("stepId", stepId);
            toolCall.put("toolDefinitionId", null);
            toolCall.put("attempt", attempt);
            toolCall.put("idempotencyKey", taskId + ":" + stepId + ":" + attempt);
            toolCall.put("status", "RUNNING");
            toolCall.put("inputHash", null);
            toolCall.put("inputSummary", clip(String.valueOf(candidate.getOrDefault("taskObjective", "")), 2_000));
            if (mapper.insertToolCall(toolCall) != 1) {
                throw new IllegalStateException("工具调用记录创建失败");
            }
            toolCallId = number(toolCall.get("id"));
            Map<String, Object> taskContext = new LinkedHashMap<>(candidate);
            taskContext.put("completedSteps", mapper.listSteps(taskId));
            AgentBuiltinToolExecutor.ToolResult result = toolExecutor.execute(candidate, taskContext);
            long durationMs = elapsedMs(startedNanos);
            UsageDelta usage = refreshUsage(taskId, startingTokens, startingCost);
            mapper.finishToolCall(toolCallId, "SUCCEEDED", result.outputSummary(), null, null, durationMs,
                    usage.tokenUsage(), usage.costAmount(), null, null);
            recordUsageLedger(taskId, stepId, candidate, attempt, usage, "SUCCEEDED");
            if (mapper.completeStep(stepId, owner, clip(result.outputJson(), 60_000), result.outputSummary()) != 1) {
                throw new IllegalStateException("步骤状态已变化，无法写入成功结果");
            }
            if (!tenantActive(tenantKey(candidate))) {
                stopForTenantStatus(taskId, stepId, null);
                return;
            }
            mapper.updateTaskProgress(taskId);
            int runtimeReplanSteps = maybeAutoReplanDegraded(taskId, result.outputJson());
            if (runtimeReplanSteps > 0) {
                insertEvent(taskId, stepId, "TASK_AUTO_REPLAN",
                        "检测到降级结果，已自动重置降级步骤及其下游：" + runtimeReplanSteps + " 个步骤");
            }
            int explicitRuntimeReplanSteps = applyRuntimeReplan(taskId, stepId, result.outputJson());
            if (explicitRuntimeReplanSteps != 0) {
                runtimeReplanSteps = explicitRuntimeReplanSteps;
            }
            if (runtimeReplanSteps == 0 && plannerModelService != null) {
                Map<String, Object> runtimeTask = mapper.findTask(taskId);
                Map<String, Object> completed = mapper.listSteps(taskId).stream()
                        .filter(item -> number(item.get("id")) == stepId).findFirst().orElse(candidate);
                String proposal = plannerModelService.proposeRuntime(runtimeTask, completed, result.outputJson());
                if (proposal != null) {
                    runtimeReplanSteps = applyRuntimeReplan(taskId, stepId, proposal);
                    if (runtimeReplanSteps > 0) {
                        insertEvent(taskId, stepId, "TASK_RUNTIME_MODEL_REPLAN", "模型候选追加了 " + runtimeReplanSteps + " 个受控步骤");
                    }
                }
            }
            if (runtimeReplanSteps > 0) {
                insertEvent(taskId, stepId, "TASK_RUNTIME_REPLAN", "工具结果追加了 " + runtimeReplanSteps + " 个受控步骤");
            }
            insertEvent(taskId, stepId, "TOOL_CALL_FINISHED", "工具调用已完成");
            insertEvent(taskId, stepId, "STEP_SUCCEEDED", "Agent 步骤已完成");
            if (mapper.finalizeCancelledTask(taskId) == 1) {
                mapper.cancelPendingSteps(taskId);
                insertEvent(taskId, null, "TASK_CANCELLED", "Agent 任务已取消");
                saveEvaluation(taskId, stepId);
                return;
            }
            if (mapper.taskBudgetExceeded(taskId) == 1) {
                mapper.failTask(taskId, "BUDGET_EXCEEDED", "Agent 任务已达到 Token 或费用预算");
                insertEvent(taskId, null, "TASK_BUDGET_EXCEEDED", "Agent 任务已达到预算上限");
                return;
            }
            if (mapper.tenantDailyBudgetExceeded(tenantKey(candidate)) == 1) {
                mapper.cancelPendingSteps(taskId);
                if (mapper.failTask(taskId, "TENANT_BUDGET_EXCEEDED", "租户当日 Token 或费用配额已用尽") == 1) {
                    insertEvent(taskId, null, "TENANT_BUDGET_EXCEEDED", "租户当日 Token 或费用配额已用尽");
                }
                saveEvaluation(taskId, stepId);
                return;
            }
            if (platformBudgetExceeded()) {
                mapper.cancelPendingSteps(taskId);
                if (mapper.failTask(taskId, "PLATFORM_BUDGET_EXCEEDED", "Agent 平台当日 Token 或费用配额已用尽") == 1) {
                    insertEvent(taskId, null, "PLATFORM_BUDGET_EXCEEDED", "Agent 平台当日 Token 或费用配额已用尽");
                }
                saveEvaluation(taskId, stepId);
                return;
            }
            if (mapper.taskRuntimeExceeded(taskId) == 1) {
                mapper.cancelPendingSteps(taskId);
                if (mapper.failTask(taskId, "RUNTIME_EXCEEDED", "Agent 任务已超过最大运行时长") == 1) {
                    insertEvent(taskId, null, "TASK_RUNTIME_EXCEEDED", "Agent 任务已超过最大运行时长");
                }
                saveEvaluation(taskId, stepId);
                return;
            }
            promoteNextStep(taskId);
            if (evaluationTool != null) {
                Map<String, Object> terminalTask = mapper.findTask(taskId);
                List<Map<String, Object>> terminalSteps = mapper.listSteps(taskId);
                if (allStepsTerminal(terminalSteps)) {
                    Map<String, Object> terminalEvaluation = evaluationTool.evaluate(terminalTask);
                    Object missing = terminalEvaluation.get("missingRequiredArtifacts");
                    if (missing instanceof List<?> missingList && !missingList.isEmpty()) {
                        String missingSummary = clip(String.join(", ", missingList.stream()
                                .map(String::valueOf).toList()), 1_000);
                        if (mapper.failTask(taskId, "REQUIRED_ARTIFACTS_MISSING",
                                "关键交付产物缺失: " + missingSummary) == 1) {
                            insertEvent(taskId, null, "TASK_RESULT_REJECTED",
                                    "终态结果检查未通过，缺少关键产物: " + missingSummary);
                        }
                        saveEvaluation(taskId, stepId);
                        return;
                    }
                    Integer minimumScore = minimumEvaluationScore(terminalTask);
                    if (minimumScore != null && number(terminalEvaluation.get("score")) < minimumScore) {
                        String message = "评估分数 " + terminalEvaluation.get("score") + " 低于任务最低门槛 " + minimumScore;
                        if (mapper.failTask(taskId, "EVALUATION_GATE_FAILED", message) == 1) {
                            insertEvent(taskId, null, "TASK_EVALUATION_GATE_FAILED", message);
                        }
                        saveEvaluation(taskId, stepId);
                        return;
                    }
                }
            }
            if (mapper.tryCompleteTask(taskId) == 1) {
                insertEvent(taskId, null, "TASK_SUCCEEDED", "Agent 任务已完成");
                saveEvaluation(taskId, stepId);
            } else if (mapper.tryCompletePartialTask(taskId) == 1) {
                insertEvent(taskId, null, "TASK_PARTIAL_SUCCESS", "Agent 任务完成，但部分可选产物已降级或跳过");
                saveEvaluation(taskId, stepId);
            }
        } catch (Exception ex) {
            String message = clip(rootCauseMessage(ex), 4_000);
            if (toolCallId > 0L) {
                UsageDelta usage = refreshUsage(taskId, startingTokens, startingCost);
                mapper.finishToolCall(toolCallId, "FAILED", null, "TOOL_EXECUTION_FAILED", message,
                        elapsedMs(startedNanos), usage.tokenUsage(), usage.costAmount(), null, null);
                recordUsageLedger(taskId, stepId, candidate, attempt, usage, "FAILED");
            }
            if (mapper.tenantDailyBudgetExceeded(tenantKey(candidate)) == 1) {
                mapper.cancelPendingSteps(taskId);
                if (mapper.failTask(taskId, "TENANT_BUDGET_EXCEEDED", "租户当日 Token 或费用配额已用尽") == 1) {
                    insertEvent(taskId, null, "TENANT_BUDGET_EXCEEDED", "租户当日 Token 或费用配额已用尽");
                }
                saveEvaluation(taskId, stepId);
                return;
            }
            String failurePolicy = failurePolicy(candidate);
            boolean continuingDegraded = "CONTINUE_DEGRADED".equals(failurePolicy)
                    && mapper.skipStep(stepId, owner, "TOOL_EXECUTION_FAILED", message) == 1;
            boolean retrying = !continuingDegraded && "RETRY".equals(failurePolicy)
                    && mapper.retryStep(stepId, owner, "TOOL_EXECUTION_FAILED", message) == 1;
            mapper.updateTaskProgress(taskId);
            if (mapper.finalizeCancelledTask(taskId) == 1) {
                mapper.cancelPendingSteps(taskId);
                insertEvent(taskId, null, "TASK_CANCELLED", "Agent 任务已取消");
                return;
            }
            if (continuingDegraded) {
                promoteNextStep(taskId);
                insertEvent(taskId, stepId, "TOOL_CALL_FINISHED", "工具调用失败，按 CONTINUE_DEGRADED 策略跳过并继续");
                insertEvent(taskId, stepId, "STEP_SKIPPED", message);
                return;
            }
            if (retrying) {
                insertEvent(taskId, stepId, "TOOL_CALL_FINISHED", "工具调用失败，已按 maxAttempts 重新排队");
                insertEvent(taskId, stepId, "STEP_RETRY_WAITING", message);
                log.warn("Agent step scheduled for retry taskId={} stepId={} tool={} cause={}",
                        taskId, stepId, candidate.get("toolName"), message);
                return;
            }
            mapper.failStep(stepId, owner, "TOOL_EXECUTION_FAILED", message);
            mapper.failTask(taskId, "TOOL_EXECUTION_FAILED", message);
            insertEvent(taskId, stepId, "TOOL_CALL_FINISHED", "工具调用失败");
            insertEvent(taskId, stepId, "STEP_FAILED", message);
            insertEvent(taskId, null, "TASK_FAILED", "Agent 任务执行失败");
            saveEvaluation(taskId, stepId);
            log.warn("Agent step failed taskId={} stepId={} tool={} cause={}",
                    taskId, stepId, candidate.get("toolName"), message);
        } finally {
            leaseRenewal.cancel(false);
        }
    }

    private String failurePolicy(Map<String, Object> candidate) {
        Object raw = candidate == null ? null : candidate.get("planJson");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return "RETRY";
        }
        try {
            JsonNode root = objectMapper.readTree(String.valueOf(raw));
            String policy = root == null ? "" : root.path("failurePolicy").asText("").trim().toUpperCase();
            return Set.of("STOP", "RETRY", "CONTINUE_DEGRADED").contains(policy) ? policy : "RETRY";
        } catch (Exception ignored) {
            return "RETRY";
        }
    }

    private void renewLease(long stepId, String owner) {
        try {
            if (mapper.renewStepLease(stepId, owner, LocalDateTime.now().plusSeconds(LEASE_SECONDS)) != 1) {
                log.warn("Agent step lease renewal lost taskId stepId={} owner={}", stepId, owner);
            }
        } catch (RuntimeException ex) {
            log.warn("Agent step lease renewal failed stepId={} cause={}", stepId, rootCauseMessage(ex));
        }
    }

    private void stopForRuntime(long taskId, long stepId, String owner) {
        mapper.failStep(stepId, owner, "RUNTIME_EXCEEDED", "Agent 任务已超过最大运行时长");
        mapper.cancelPendingSteps(taskId);
        if (mapper.failTask(taskId, "RUNTIME_EXCEEDED", "Agent 任务已超过最大运行时长") == 1) {
            insertEvent(taskId, null, "TASK_RUNTIME_EXCEEDED", "Agent 任务已超过最大运行时长");
        } else if (mapper.finalizeCancelledTask(taskId) == 1) {
            insertEvent(taskId, null, "TASK_CANCELLED", "Agent 任务已取消");
        }
        saveEvaluation(taskId, stepId);
    }

    private void stopForBudget(long taskId, long stepId, String owner) {
        mapper.failStep(stepId, owner, "BUDGET_EXCEEDED", "Agent 任务已达到 Token 或费用预算");
        mapper.cancelPendingSteps(taskId);
        if (mapper.failTask(taskId, "BUDGET_EXCEEDED", "Agent 任务已达到 Token 或费用预算") == 1) {
            insertEvent(taskId, null, "TASK_BUDGET_EXCEEDED", "Agent 任务已达到预算上限");
        }
        saveEvaluation(taskId, stepId);
    }

    private void stopForPlatformBudget(long taskId, long stepId, String owner) {
        mapper.failStep(stepId, owner, "PLATFORM_BUDGET_EXCEEDED", "Agent 平台当日 Token 或费用配额已用尽");
        mapper.cancelPendingSteps(taskId);
        if (mapper.failTask(taskId, "PLATFORM_BUDGET_EXCEEDED", "Agent 平台当日 Token 或费用配额已用尽") == 1) {
            insertEvent(taskId, null, "PLATFORM_BUDGET_EXCEEDED", "Agent 平台当日 Token 或费用配额已用尽");
        }
        saveEvaluation(taskId, stepId);
    }

    private boolean platformBudgetExceeded() {
        long tokens = Math.max(0L, platformDailyMaxTokens);
        BigDecimal cost = platformDailyMaxCost == null || platformDailyMaxCost.signum() < 0
                ? BigDecimal.ZERO : platformDailyMaxCost;
        return (tokens > 0L || cost.signum() > 0)
                && mapper.platformBudgetExceeded(tokens, cost) == 1;
    }

    private void saveEvaluation(long taskId, long stepId) {
        if (evaluationTool == null) return;
        try {
            Map<String, Object> task = mapper.findTask(taskId);
            if (task != null) evaluationTool.saveArtifact(taskId, stepId, task);
        } catch (RuntimeException ex) {
            log.warn("Agent evaluation artifact save failed taskId={} cause={}", taskId, rootCauseMessage(ex));
        }
    }

    private static boolean allStepsTerminal(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) return false;
        for (Map<String, Object> step : steps) {
            String status = String.valueOf(step.getOrDefault("status", ""));
            if (!("SUCCEEDED".equals(status) || "SKIPPED".equals(status)
                    || "FAILED".equals(status) || "CANCELLED".equals(status))) {
                return false;
            }
        }
        return true;
    }

    private Integer minimumEvaluationScore(Map<String, Object> task) {
        if (task == null || task.get("constraintsJson") == null) return null;
        try {
            JsonNode constraints = objectMapper.readTree(String.valueOf(task.get("constraintsJson")));
            JsonNode value = constraints == null ? null : constraints.get("minEvaluationScore");
            if (value == null || !value.isNumber()) return null;
            int score = value.intValue();
            return score >= 0 && score <= 100 ? score : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private UsageDelta refreshUsage(long taskId, long startingTokens, BigDecimal startingCost) {
        try {
            mapper.refreshTaskUsage(taskId);
            Map<String, Object> task = mapper.findTask(taskId);
            long currentTokens = whole(task == null ? null : task.get("tokenUsage"));
            BigDecimal currentCost = decimal(task == null ? null : task.get("costAmount"));
            return new UsageDelta(
                    Math.max(0L, currentTokens - startingTokens),
                    currentCost.subtract(startingCost).max(BigDecimal.ZERO)
            );
        } catch (RuntimeException ex) {
            log.warn("Agent usage refresh failed taskId={} cause={}", taskId, rootCauseMessage(ex));
            return new UsageDelta(0L, BigDecimal.ZERO);
        }
    }

    private void recordUsageLedger(long taskId, long stepId, Map<String, Object> candidate,
                                   int attempt, UsageDelta usage, String status) {
        if (usage == null || (usage.tokenUsage() <= 0 && usage.costAmount().signum() == 0)) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", taskId);
        row.put("stepId", stepId);
        row.put("tenantKey", String.valueOf(candidate.getOrDefault("tenantKey", "platform")));
        row.put("idempotencyKey", taskId + ":" + stepId + ":" + attempt);
        row.put("toolName", candidate.get("toolName"));
        row.put("attempt", attempt);
        row.put("tokenUsage", usage.tokenUsage());
        row.put("costAmount", usage.costAmount());
        row.put("status", status);
        try {
            mapper.insertUsageLedger(row);
        } catch (RuntimeException ex) {
            log.warn("Agent usage ledger write failed taskId={} stepId={} cause={}", taskId, stepId, rootCauseMessage(ex));
        }
    }

    private void markTaskRunning(long taskId) {
        if (mapper.updateTaskStatus(taskId, List.of("READY"), "RUNNING", false, 0, false) == 1) {
            insertEvent(taskId, null, "TASK_STARTED", "Agent 任务开始执行");
        }
    }

    private void stopForTenantBudget(long taskId, long stepId, String owner) {
        mapper.cancelPendingSteps(taskId);
        mapper.failStep(stepId, owner, "TENANT_BUDGET_EXCEEDED", "租户当日 Token 或费用配额已用尽");
        if (mapper.failTask(taskId, "TENANT_BUDGET_EXCEEDED", "租户当日 Token 或费用配额已用尽") == 1) {
            insertEvent(taskId, null, "TENANT_BUDGET_EXCEEDED", "租户当日 Token 或费用配额已用尽");
        }
    }

    private void stopForTenantStatus(long taskId, long stepId, String owner) {
        mapper.cancelPendingSteps(taskId);
        if (owner != null) {
            mapper.failStep(stepId, owner, "TENANT_NOT_ACTIVE", "租户已暂停或停用，Agent 任务停止执行");
        }
        if (mapper.failTask(taskId, "TENANT_NOT_ACTIVE", "租户已暂停或停用，Agent 任务停止执行") == 1) {
            insertEvent(taskId, null, "TENANT_NOT_ACTIVE", "租户已暂停或停用，Agent 任务停止执行");
        }
    }

    private boolean tenantActive(String tenantKey) {
        Map<String, Object> tenant = mapper.findTenant(tenantKey);
        // Keep legacy platform tasks runnable when the tenant directory was introduced
        // after the task was created; all explicit tenants still require ACTIVE state.
        if ((tenant == null || tenant.isEmpty()) && "platform".equals(tenantKey)) {
            return true;
        }
        return tenant != null && "ACTIVE".equalsIgnoreCase(String.valueOf(tenant.getOrDefault("status", "DISABLED")));
    }

    private static String tenantKey(Map<String, Object> candidate) {
        Object value = candidate == null ? null : candidate.get("tenantKey");
        return value == null || String.valueOf(value).isBlank() ? "platform" : String.valueOf(value);
    }

    private void promoteNextStep(long taskId) {
        List<Map<String, Object>> nextSteps = mapper.findReadyBlockedSteps(taskId);
        if (nextSteps == null || nextSteps.isEmpty()) {
            return;
        }
        for (Map<String, Object> next : nextSteps) {
            long nextStepId = number(next.get("stepId"));
            if (nextStepId > 0 && mapper.promoteStep(nextStepId) == 1) {
                insertEvent(taskId, nextStepId, "STEP_READY", "下一步已就绪");
            }
        }
    }

    /**
     * Accepts only an explicit, bounded extension declared by a tool result.
     * The extension cannot raise the task risk level or bypass the normal tool approval path.
     */
    private int applyRuntimeReplan(long taskId, long completedStepId, String outputJson) {
        if (outputJson == null || outputJson.isBlank()) {
            return 0;
        }
        List<Long> insertedStepIds = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(outputJson);
            JsonNode extension = root == null ? null : root.get("runtimeReplan");
            JsonNode requestedSteps = extension == null ? null : extension.get("steps");
            if (requestedSteps == null || !requestedSteps.isArray() || requestedSteps.isEmpty()) {
                return 0;
            }
            if (requestedSteps.size() > MAX_RUNTIME_REPLAN_STEPS) {
                return rejectRuntimeReplan(taskId, "运行时重规划单次最多追加 " + MAX_RUNTIME_REPLAN_STEPS + " 个步骤");
            }

            Map<String, Object> task = mapper.findTask(taskId);
            List<Map<String, Object>> existingSteps = mapper.listSteps(taskId);
            if (task == null || existingSteps == null || existingSteps.isEmpty()) {
                return rejectRuntimeReplan(taskId, "任务或现有步骤不存在");
            }
            int maxSteps = Math.max(1, (int) number(task.get("maxSteps")));
            if (existingSteps.size() + requestedSteps.size() > Math.min(maxSteps, 32)) {
                return rejectRuntimeReplan(taskId, "运行时重规划超过任务 maxSteps 或平台上限");
            }

            Map<String, Object> completedStep = existingSteps.stream()
                    .filter(step -> number(step.get("id")) == completedStepId)
                    .findFirst()
                    .orElse(null);
            if (completedStep == null) {
                return rejectRuntimeReplan(taskId, "找不到产生重规划结果的步骤");
            }
            String parentKey = String.valueOf(completedStep.getOrDefault("stepKey", "")).trim();
            long planId = number(completedStep.get("planId"));
            if (parentKey.isBlank() || planId <= 0) {
                return rejectRuntimeReplan(taskId, "重规划父步骤信息不完整");
            }

            Map<String, String> statusByKey = new LinkedHashMap<>();
            Set<String> knownKeys = new HashSet<>();
            int lastSortOrder = 0;
            for (Map<String, Object> step : existingSteps) {
                String key = String.valueOf(step.getOrDefault("stepKey", "")).trim();
                if (!key.isBlank()) {
                    knownKeys.add(key);
                    statusByKey.put(key, String.valueOf(step.getOrDefault("status", "")));
                }
                lastSortOrder = Math.max(lastSortOrder, (int) number(step.get("sortOrder")));
            }
            String taskRisk = normalizedRisk(task.get("riskLevel"));
            List<Map<String, Object>> additions = new ArrayList<>();
            for (JsonNode requested : requestedSteps) {
                if (!requested.isObject()) {
                    return rejectRuntimeReplan(taskId, "运行时重规划步骤必须是对象");
                }
                String key = text(requested.get("key"));
                String toolName = text(requested.get("toolName"));
                String name = text(requested.get("name"));
                String role = text(requested.get("agentRole"));
                String stepType = text(requested.get("stepType"));
                if (!key.matches("[A-Za-z0-9_-]{1,100}") || !knownKeys.add(key)) {
                    return rejectRuntimeReplan(taskId, "运行时重规划包含重复或无效步骤 key");
                }
                if (toolName.isBlank() || name.isBlank() || role.isBlank() || stepType.isBlank()) {
                    return rejectRuntimeReplan(taskId, "运行时重规划步骤缺少名称、角色、类型或工具");
                }
                if (!RUNTIME_REPLAN_ALLOWED_TOOLS.contains(toolName)) {
                    return rejectRuntimeReplan(taskId, "运行时重规划工具不在 Agent 白名单内: " + toolName);
                }
                JsonNode dependsNode = requested.get("dependsOn");
                if (dependsNode == null || !dependsNode.isArray() || dependsNode.isEmpty()) {
                    return rejectRuntimeReplan(taskId, "运行时重规划步骤必须声明依赖");
                }
                List<String> dependencies = new ArrayList<>();
                boolean connected = false;
                for (JsonNode dependency : dependsNode) {
                    String dependencyKey = text(dependency);
                    if (dependencyKey.isBlank() || !statusByKey.containsKey(dependencyKey)) {
                        return rejectRuntimeReplan(taskId, "运行时重规划只能依赖已存在或已声明的前置步骤");
                    }
                    dependencies.add(dependencyKey);
                    connected = connected || parentKey.equals(dependencyKey);
                }
                if (!connected && additions.isEmpty()) {
                    return rejectRuntimeReplan(taskId, "首个运行时步骤必须直接依赖产生结果的步骤");
                }
                Map<String, Object> tool = mapper.findToolByKey(toolName);
                if (tool == null || !Boolean.TRUE.equals(asBoolean(tool.get("enabled")))) {
                    return rejectRuntimeReplan(taskId, "运行时重规划工具未注册或已禁用: " + toolName);
                }
                Map<String, Object> candidate = new LinkedHashMap<>();
                candidate.put("toolName", toolName);
                candidate.put("agentRole", role);
                requireAllowedRole(tool, candidate);
                String toolRisk = normalizedRisk(tool.get("riskLevel"));
                if (riskRank(toolRisk) > riskRank(taskRisk)) {
                    return rejectRuntimeReplan(taskId, "运行时重规划不能提升任务风险等级");
                }
                Map<String, Object> addition = new LinkedHashMap<>();
                addition.put("taskId", taskId);
                addition.put("planId", planId);
                addition.put("stepKey", key);
                addition.put("stepName", clip(name, 255));
                addition.put("agentRole", clip(role, 80));
                addition.put("stepType", clip(stepType, 64));
                addition.put("status", "BLOCKED");
                addition.put("dependsOnJson", objectMapper.writeValueAsString(dependencies));
                addition.put("toolName", clip(toolName, 160));
                JsonNode inputNode = requested.get("inputJson");
                addition.put("inputJson", inputNode == null || inputNode.isNull()
                        ? null
                        : inputNode.isTextual() ? clip(inputNode.textValue(), 60_000)
                        : clip(objectMapper.writeValueAsString(inputNode), 60_000));
                addition.put("maxAttempts", Math.max(1, (int) number(task.get("maxRetries"))));
                addition.put("riskLevel", taskRisk);
                addition.put("sortOrder", ++lastSortOrder);
                additions.add(addition);
                statusByKey.put(key, "BLOCKED");
            }
            for (Map<String, Object> addition : additions) {
                if (mapper.insertStep(addition) != 1) {
                    cleanupRuntimeReplanSteps(taskId, insertedStepIds);
                    return rejectRuntimeReplan(taskId, "运行时重规划步骤保存失败");
                }
                insertedStepIds.add(number(addition.get("id")));
            }
            if (mapper.incrementTaskTotalSteps(taskId, additions.size()) != 1) {
                cleanupRuntimeReplanSteps(taskId, insertedStepIds);
                return rejectRuntimeReplan(taskId, "运行时重规划任务步骤总数更新失败");
            }
            return additions.size();
        } catch (Exception ex) {
            cleanupRuntimeReplanSteps(taskId, insertedStepIds);
            return rejectRuntimeReplan(taskId, "运行时重规划结果格式无效: " + clip(rootCauseMessage(ex), 300));
        }
    }

    private void cleanupRuntimeReplanSteps(long taskId, List<Long> insertedStepIds) {
        if (insertedStepIds == null || insertedStepIds.isEmpty()) {
            return;
        }
        try {
            mapper.deleteRuntimeReplanSteps(taskId, insertedStepIds);
        } catch (RuntimeException cleanupError) {
            log.error("Agent runtime replan compensation failed taskId={} stepIds={}",
                    taskId, insertedStepIds, cleanupError);
        }
    }

    private int maybeAutoReplanDegraded(long taskId, String outputJson) {
        if (outputJson == null || outputJson.isBlank()) return 0;
        try {
            JsonNode result = objectMapper.readTree(outputJson);
            if (result == null || !result.path("degraded").asBoolean(false)) return 0;
            Map<String, Object> task = mapper.findTask(taskId);
            if (task == null || !autoReplanEnabled(task.get("constraintsJson"))) return 0;
            int retryCount = Math.max(0, (int) number(task.get("retryCount")));
            int maxRetries = Math.max(0, (int) number(task.get("maxRetries")));
            if (retryCount >= maxRetries) {
                insertEvent(taskId, null, "TASK_AUTO_REPLAN_LIMIT_REACHED",
                        "降级结果未自动重试：已达到 maxRetries=" + maxRetries);
                return 0;
            }
            if (mapper.updateTaskStatus(taskId, List.of("RUNNING"), "RUNNING", true, 1, false) != 1) {
                return 0;
            }
            int reset = mapper.resetDegradedSteps(taskId);
            return Math.max(0, reset);
        } catch (Exception ex) {
            log.warn("Agent automatic degraded replan rejected taskId={} cause={}", taskId, rootCauseMessage(ex));
            insertEvent(taskId, null, "TASK_AUTO_REPLAN_REJECTED", "降级结果自动重规划失败");
            return 0;
        }
    }

    private boolean autoReplanEnabled(Object constraintsJson) {
        if (constraintsJson == null || String.valueOf(constraintsJson).isBlank()) return false;
        try {
            JsonNode constraints = objectMapper.readTree(String.valueOf(constraintsJson));
            return constraints != null && constraints.path("autoReplan").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private int rejectRuntimeReplan(long taskId, String reason) {
        insertEvent(taskId, null, "TASK_RUNTIME_REPLAN_REJECTED", reason);
        log.warn("Agent runtime replan rejected taskId={} reason={}", taskId, reason);
        return -1;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.isTextual() ? node.textValue().trim() : node.asText("").trim();
    }

    private static String normalizedRisk(Object value) {
        String risk = value == null ? "READ_ONLY" : String.valueOf(value).trim().toUpperCase();
        return riskRank(risk) < 0 ? "READ_ONLY" : risk;
    }

    private static int riskRank(String risk) {
        return switch (risk == null ? "" : risk) {
            case "READ_ONLY" -> 0;
            case "WRITE_REVERSIBLE" -> 1;
            case "EXTERNAL_SIDE_EFFECT" -> 2;
            case "DESTRUCTIVE" -> 3;
            case "PRODUCTION_OPERATION" -> 4;
            default -> -1;
        };
    }

    private void insertEvent(long taskId, Long stepId, String eventType, String summary) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("taskId", taskId);
        event.put("stepId", stepId);
        event.put("eventType", eventType);
        event.put("visibility", "PUBLIC");
        event.put("summary", summary);
        event.put("payloadJson", null);
        if (mapper.insertEvent(event) != 1) {
            log.warn("Agent event write failed taskId={} eventType={}", taskId, eventType);
        }
    }

    private void requestToolApproval(long taskId, long stepId, Map<String, Object> tool,
                                     Map<String, Object> candidate) {
        if (mapper.requestToolApproval(stepId) != 1) {
            throw new IllegalStateException("工具步骤状态已变化，无法进入审批");
        }
        try {
            Map<String, Object> approval = new LinkedHashMap<>();
            approval.put("taskId", taskId);
            approval.put("stepId", stepId);
            approval.put("approvalType", "TOOL");
            approval.put("status", "PENDING");
            approval.put("requestedBy", candidate.get("createdBy"));
            approval.put("reason", "工具定义要求执行前人工确认");
            approval.put("targetSummary", clip(String.valueOf(candidate.getOrDefault("taskObjective", "")), 4_000));
            approval.put("parameterHash", AgentParameterHasher.hash(
                    objectMapper,
                    String.valueOf(candidate.getOrDefault("toolName", "")),
                    String.valueOf(candidate.getOrDefault("agentRole", "")),
                    String.valueOf(candidate.getOrDefault("inputJson", "")),
                    String.valueOf(candidate.getOrDefault("taskObjective", "")),
                    String.valueOf(candidate.getOrDefault("constraintsJson", ""))
            ));
            approval.put("expiresAt", LocalDateTime.now().plusMinutes(15));
            if (mapper.insertApproval(approval) != 1) {
                throw new IllegalStateException("工具审批记录创建失败");
            }
            if (mapper.updateTaskStatus(taskId, List.of("RUNNING"), "WAITING_CONFIRMATION", false, 0, false) != 1) {
                throw new IllegalStateException("任务进入工具审批状态失败");
            }
            insertEvent(taskId, stepId, "APPROVAL_REQUIRED", "工具执行需要人工审批: " + tool.get("toolKey"));
        } catch (RuntimeException ex) {
            String message = clip(rootCauseMessage(ex), 4_000);
            mapper.rejectPendingToolApproval(stepId, "APPROVAL_REQUEST_FAILED", message);
            mapper.failWaitingApprovalStep(stepId, "APPROVAL_REQUEST_FAILED", message);
            throw ex;
        }
    }

    private void requireAllowedRole(Map<String, Object> tool, Map<String, Object> candidate) {
        Object rawRoles = tool.get("allowedRolesJson");
        if (rawRoles == null || String.valueOf(rawRoles).isBlank()) {
            return;
        }
        String role = String.valueOf(candidate.getOrDefault("agentRole", "")).trim();
        try {
            JsonNode roles = objectMapper.readTree(String.valueOf(rawRoles));
            if (!roles.isArray() || role.isBlank() || !containsText(roles, role)) {
                throw new IllegalStateException("Agent 角色无权执行工具: " + candidate.get("toolName"));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("工具角色白名单格式无效", ex);
        }
    }

    private static boolean containsText(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (value.isTextual() && expected.equals(value.textValue())) {
                return true;
            }
        }
        return false;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static long whole(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0L; }
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception ignored) { return BigDecimal.ZERO; }
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static String clip(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor != null && cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor == null || cursor.getMessage() == null ? error.getClass().getSimpleName() : cursor.getMessage();
    }

    private record UsageDelta(long tokenUsage, BigDecimal costAmount) {}
}
