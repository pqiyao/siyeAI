package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentTaskService {

    private static final Set<String> RISK_LEVELS = Set.of(
            "READ_ONLY", "WRITE_REVERSIBLE", "EXTERNAL_SIDE_EFFECT", "DESTRUCTIVE", "PRODUCTION_OPERATION"
    );
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "DRAFT", "PLANNING", "WAITING_APPROVAL", "READY", "RUNNING", "PAUSED", "WAITING_CONFIRMATION", "CANCEL_REQUESTED"
    );
    private static final Set<String> RETRYABLE_STATUSES = Set.of("FAILED", "PARTIAL_SUCCESS");

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;

    public AgentTaskService(AgentAdminMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, Long adminId) {
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
        task.put("taskKey", "agent-" + UUID.randomUUID().toString().replace("-", ""));
        task.put("taskType", taskType);
        task.put("title", title);
        task.put("objective", objective);
        task.put("constraintsJson", constraintsJson(body.get("constraints"), body.get("constraintsJson")));
        task.put("status", "DRAFT");
        task.put("riskLevel", riskLevel);
        task.put("createdBy", adminId);
        task.put("priority", boundedInt(body.get("priority"), 0, -100, 100));
        task.put("maxSteps", boundedInt(body.get("maxSteps"), 50, 1, 500));
        task.put("maxRetries", boundedInt(body.get("maxRetries"), 3, 0, 10));
        task.put("maxRuntimeSeconds", boundedInt(body.get("maxRuntimeSeconds"), 3600, 60, 86_400));
        task.put("maxTokens", boundedLong(body.get("maxTokens"), 0L, 0L, 100_000_000L));
        task.put("maxCost", boundedDecimal(body.get("maxCost"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000000")));

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
        if ("RETRY".equalsIgnoreCase(action)) {
            mapper.resetFailedSteps(id);
        }
        insertEvent(id, transition.eventType(), transition.summary(), null);
        return mapper.findTask(id);
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
            case "RETRY" -> {
                require(currentStatus, RETRYABLE_STATUSES, "当前状态不能重试");
                yield new Transition("READY", true, 1, false, "TASK_RETRY_REQUESTED", "Agent 任务已进入重试队列");
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
