package com.example.sillyspringboot.agent.executor;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.tool.AgentBuiltinToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AgentWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AgentWorkerService.class);
    private static final int BATCH_SIZE = 5;
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

    public AgentWorkerService(AgentAdminMapper mapper, AgentBuiltinToolExecutor toolExecutor) {
        this.mapper = mapper;
        this.toolExecutor = toolExecutor;
    }

    @Scheduled(
            initialDelayString = "${app.agent.worker-initial-delay-ms:5000}",
            fixedDelayString = "${app.agent.worker-interval-ms:5000}"
    )
    public void dispatchReadySteps() {
        try {
            int recovered = mapper.recoverExpiredSteps(LocalDateTime.now());
            int exhausted = mapper.failExhaustedSteps(LocalDateTime.now());
            int failedTasks = mapper.failTasksWithFailedSteps();
            if (recovered > 0) {
                log.warn("Agent worker recovered expired steps count={}", recovered);
            }
            if (exhausted > 0 || failedTasks > 0) {
                log.warn("Agent worker exhausted leases steps={} tasks={}", exhausted, failedTasks);
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
            Map<String, Object> toolDefinition = mapper.findToolByKey(String.valueOf(candidate.getOrDefault("toolName", "")));
            if (toolDefinition == null || !Boolean.TRUE.equals(asBoolean(toolDefinition.get("enabled")))) {
                throw new IllegalStateException("工具未注册或已禁用: " + candidate.get("toolName"));
            }
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
                mapper.failStep(stepId, owner, "TOOL_CALL_CREATE_FAILED", "工具调用记录创建失败");
                mapper.failTask(taskId, "TOOL_CALL_CREATE_FAILED", "工具调用记录创建失败");
                return;
            }
            toolCallId = number(toolCall.get("id"));
            Map<String, Object> taskContext = new LinkedHashMap<>(candidate);
            taskContext.put("completedSteps", mapper.listSteps(taskId));
            AgentBuiltinToolExecutor.ToolResult result = toolExecutor.execute(candidate, taskContext);
            long durationMs = elapsedMs(startedNanos);
            UsageDelta usage = refreshUsage(taskId, startingTokens, startingCost);
            mapper.finishToolCall(toolCallId, "SUCCEEDED", result.outputSummary(), null, null, durationMs,
                    usage.tokenUsage(), usage.costAmount(), null, null);
            if (mapper.completeStep(stepId, owner, clip(result.outputJson(), 60_000), result.outputSummary()) != 1) {
                throw new IllegalStateException("步骤状态已变化，无法写入成功结果");
            }
            mapper.updateTaskProgress(taskId);
            insertEvent(taskId, stepId, "TOOL_CALL_FINISHED", "工具调用已完成");
            insertEvent(taskId, stepId, "STEP_SUCCEEDED", "Agent 步骤已完成");
            if (mapper.finalizeCancelledTask(taskId) == 1) {
                mapper.cancelPendingSteps(taskId);
                insertEvent(taskId, null, "TASK_CANCELLED", "Agent 任务已取消");
                return;
            }
            if (mapper.taskBudgetExceeded(taskId) == 1) {
                mapper.failTask(taskId, "BUDGET_EXCEEDED", "Agent 任务已达到 Token 或费用预算");
                insertEvent(taskId, null, "TASK_BUDGET_EXCEEDED", "Agent 任务已达到预算上限");
                return;
            }
            if (mapper.taskRuntimeExceeded(taskId) == 1) {
                mapper.cancelPendingSteps(taskId);
                if (mapper.failTask(taskId, "RUNTIME_EXCEEDED", "Agent 任务已超过最大运行时长") == 1) {
                    insertEvent(taskId, null, "TASK_RUNTIME_EXCEEDED", "Agent 任务已超过最大运行时长");
                }
                return;
            }
            promoteNextStep(taskId);
            if (mapper.tryCompleteTask(taskId) == 1) {
                insertEvent(taskId, null, "TASK_SUCCEEDED", "Agent 任务已完成");
            } else if (mapper.tryCompletePartialTask(taskId) == 1) {
                insertEvent(taskId, null, "TASK_PARTIAL_SUCCESS", "Agent 任务完成，但部分可选产物已降级或跳过");
            }
        } catch (Exception ex) {
            String message = clip(rootCauseMessage(ex), 4_000);
            if (toolCallId > 0L) {
                UsageDelta usage = refreshUsage(taskId, startingTokens, startingCost);
                mapper.finishToolCall(toolCallId, "FAILED", null, "TOOL_EXECUTION_FAILED", message,
                        elapsedMs(startedNanos), usage.tokenUsage(), usage.costAmount(), null, null);
            }
            mapper.failStep(stepId, owner, "TOOL_EXECUTION_FAILED", message);
            mapper.updateTaskProgress(taskId);
            if (mapper.finalizeCancelledTask(taskId) == 1) {
                mapper.cancelPendingSteps(taskId);
                insertEvent(taskId, null, "TASK_CANCELLED", "Agent 任务已取消");
                return;
            }
            mapper.failTask(taskId, "TOOL_EXECUTION_FAILED", message);
            insertEvent(taskId, stepId, "TOOL_CALL_FINISHED", "工具调用失败");
            insertEvent(taskId, stepId, "STEP_FAILED", message);
            insertEvent(taskId, null, "TASK_FAILED", "Agent 任务执行失败");
            log.warn("Agent step failed taskId={} stepId={} tool={} cause={}",
                    taskId, stepId, candidate.get("toolName"), message);
        } finally {
            leaseRenewal.cancel(false);
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

    private void markTaskRunning(long taskId) {
        if (mapper.updateTaskStatus(taskId, List.of("READY"), "RUNNING", false, 0, false) == 1) {
            insertEvent(taskId, null, "TASK_STARTED", "Agent 任务开始执行");
        }
    }

    private void promoteNextStep(long taskId) {
        Map<String, Object> next = mapper.findNextBlockedStep(taskId);
        if (next == null) {
            return;
        }
        long nextStepId = number(next.get("stepId"));
        if (nextStepId > 0 && mapper.promoteStep(nextStepId) == 1) {
            insertEvent(taskId, nextStepId, "STEP_READY", "下一步已就绪");
        }
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
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("taskId", taskId);
        approval.put("stepId", stepId);
        approval.put("approvalType", "TOOL");
        approval.put("status", "PENDING");
        approval.put("requestedBy", candidate.get("createdBy"));
        approval.put("reason", "工具定义要求执行前人工确认");
        approval.put("targetSummary", clip(String.valueOf(candidate.getOrDefault("taskObjective", "")), 4_000));
        approval.put("parameterHash", null);
        approval.put("expiresAt", null);
        if (mapper.insertApproval(approval) != 1) {
            throw new IllegalStateException("工具审批记录创建失败");
        }
        if (mapper.updateTaskStatus(taskId, List.of("RUNNING"), "WAITING_CONFIRMATION", false, 0, false) != 1) {
            throw new IllegalStateException("任务进入工具审批状态失败");
        }
        insertEvent(taskId, stepId, "APPROVAL_REQUIRED", "工具执行需要人工审批: " + tool.get("toolKey"));
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
