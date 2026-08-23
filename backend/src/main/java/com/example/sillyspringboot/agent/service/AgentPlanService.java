package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentPlanService {

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;

    public AgentPlanService(AgentAdminMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> generate(long taskId, Long requesterId) {
        Map<String, Object> task = requireTask(taskId);
        requireStatus(task, "DRAFT", "只有草稿任务可以生成计划");
        Map<String, Object> previousPlan = mapper.findLatestPlan(taskId);
        if (previousPlan != null && !"REJECTED".equals(String.valueOf(previousPlan.get("status")))) {
            throw new IllegalArgumentException("任务已经存在计划");
        }
        int planVersion = previousPlan == null ? 1 : number(previousPlan.get("planVersion"), 1) + 1;
        if (previousPlan != null) {
            mapper.deletePlanSteps(idOf(previousPlan, "上一版计划"));
        }

        transitionTask(taskId, "DRAFT", "PLANNING");
        List<StepDefinition> definitions = templateFor(String.valueOf(task.get("taskType")));
        String taskRisk = normalizedRisk(task.get("riskLevel"));
        String planRisk = definitions.stream()
                .map(definition -> riskForTool(definition.toolName()))
                .reduce(taskRisk, AgentPlanService::maxRisk);
        if (!planRisk.equals(taskRisk) && mapper.updateTaskRiskLevel(taskId, planRisk) != 1) {
            throw new IllegalStateException("Agent 任务风险等级更新失败");
        }
        int maxAttempts = Math.max(1, number(task.get("maxRetries"), 3));

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("taskId", taskId);
        plan.put("planVersion", planVersion);
        plan.put("status", "WAITING_APPROVAL");
        plan.put("generatedBy", "SYSTEM_TEMPLATE");
        plan.put("inputHash", null);
        plan.put("planJson", planJson(task, definitions));
        if (mapper.insertPlan(plan) != 1) {
            throw new IllegalStateException("Agent 计划创建失败");
        }
        long planId = idOf(plan, "计划");

        long firstStepId = 0;
        List<Map<String, Object>> stepRows = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            StepDefinition definition = definitions.get(index);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("taskId", taskId);
            step.put("planId", planId);
            step.put("stepKey", definition.key());
            step.put("stepName", definition.name());
            step.put("agentRole", definition.agentRole());
            step.put("stepType", definition.stepType());
            step.put("status", index == 0 ? "READY" : "BLOCKED");
            step.put("dependsOnJson", json(definition.dependsOn()));
            step.put("toolName", definition.toolName());
            step.put("inputJson", null);
            step.put("maxAttempts", maxAttempts);
            step.put("riskLevel", maxRisk(taskRisk, riskForTool(definition.toolName())));
            step.put("sortOrder", index + 1);
            if (mapper.insertStep(step) != 1) {
                throw new IllegalStateException("Agent 步骤创建失败");
            }
            long stepId = idOf(step, "步骤");
            if (firstStepId == 0) {
                firstStepId = stepId;
            }
            stepRows.add(mapperStep(step, stepId));
        }

        if (mapper.updateTaskPlanSummary(taskId, definitions.size(), firstStepId) != 1) {
            throw new IllegalStateException("Agent 任务计划摘要更新失败");
        }
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("taskId", taskId);
        approval.put("stepId", null);
        approval.put("approvalType", "PLAN");
        approval.put("status", "PENDING");
        approval.put("requestedBy", requesterId);
        approval.put("reason", "Agent 计划需要人工审批");
        approval.put("targetSummary", shortText(task.get("title"), task.get("objective"), 4_000));
        approval.put("parameterHash", null);
        approval.put("expiresAt", null);
        if (mapper.insertApproval(approval) != 1) {
            throw new IllegalStateException("Agent 计划审批记录创建失败");
        }
        transitionTask(taskId, "PLANNING", "WAITING_APPROVAL");
        insertEvent(taskId, "PLAN_CREATED", "Agent 计划已生成，等待审批");
        insertEvent(taskId, "PLAN_APPROVAL_REQUIRED", "Agent 计划需要人工审批");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", mapper.findLatestPlan(taskId));
        result.put("steps", stepRows);
        return result;
    }

    @Transactional
    public Map<String, Object> approve(long taskId, Long reviewerId) {
        return approve(taskId, reviewerId, null, "计划已批准");
    }

    @Transactional
    public Map<String, Object> approveApproval(long approvalId, Long reviewerId, String decisionComment) {
        Map<String, Object> approval = requireApproval(approvalId);
        if ("TOOL".equalsIgnoreCase(String.valueOf(approval.get("approvalType")))) {
            return approveToolApproval(approval, reviewerId, decisionComment);
        }
        requirePlanApproval(approval);
        return approve(idOf(approval, "审批任务", "taskId"), reviewerId, approvalId, decisionComment);
    }

    @Transactional
    public Map<String, Object> rejectApproval(long approvalId, Long reviewerId, String decisionComment) {
        Map<String, Object> approval = requireApproval(approvalId);
        if ("TOOL".equalsIgnoreCase(String.valueOf(approval.get("approvalType")))) {
            return rejectToolApproval(approval, reviewerId, decisionComment);
        }
        requirePlanApproval(approval);
        if (!"PENDING".equals(String.valueOf(approval.get("status")))) {
            throw new IllegalArgumentException("审批记录已处理");
        }
        long taskId = idOf(approval, "审批任务", "taskId");
        Map<String, Object> task = requireTask(taskId);
        requireStatus(task, "WAITING_APPROVAL", "当前任务不在计划审批状态");
        Map<String, Object> latestApproval = mapper.findLatestApproval(taskId, "PLAN");
        if (latestApproval == null || idOf(latestApproval, "最新审批记录") != approvalId) {
            throw new IllegalArgumentException("只能处理任务最新的待审批计划");
        }
        Map<String, Object> plan = mapper.findLatestPlan(taskId);
        if (plan == null || !"WAITING_APPROVAL".equals(String.valueOf(plan.get("status")))) {
            throw new IllegalArgumentException("没有待审批的 Agent 计划");
        }
        long planId = idOf(plan, "计划");
        if (mapper.updatePlanStatus(planId, "WAITING_APPROVAL", "REJECTED", null) != 1) {
            throw new IllegalStateException("计划状态已变化，请刷新后重试");
        }
        if (mapper.updateApprovalStatus(
                approvalId, "PENDING", "REJECTED", reviewerId,
                decisionComment(decisionComment, "计划已拒绝")) != 1) {
            throw new IllegalStateException("审批记录状态已变化，请刷新后重试");
        }
        mapper.deletePlanSteps(planId);
        if (mapper.resetTaskPlanSummary(taskId) != 1) {
            throw new IllegalStateException("任务计划摘要重置失败");
        }
        transitionTask(taskId, "WAITING_APPROVAL", "DRAFT");
        insertEvent(taskId, "PLAN_REJECTED", "Agent 计划已拒绝，可重新生成计划");
        return Map.of(
                "approval", mapper.findApproval(approvalId),
                "task", mapper.findTask(taskId),
                "plan", mapper.findLatestPlan(taskId)
        );
    }

    private Map<String, Object> approve(
            long taskId,
            Long reviewerId,
            Long expectedApprovalId,
            String decisionComment
    ) {
        Map<String, Object> task = requireTask(taskId);
        requireStatus(task, "WAITING_APPROVAL", "当前任务不在计划审批状态");
        Map<String, Object> plan = mapper.findLatestPlan(taskId);
        if (plan == null || !"WAITING_APPROVAL".equals(String.valueOf(plan.get("status")))) {
            throw new IllegalArgumentException("没有待审批的 Agent 计划");
        }
        long planId = idOf(plan, "计划");
        Map<String, Object> approval = mapper.findLatestApproval(taskId, "PLAN");
        if (expectedApprovalId != null && (approval == null || idOf(approval, "审批记录") != expectedApprovalId)) {
            throw new IllegalArgumentException("只能处理任务最新的待审批计划");
        }
        if (approval != null && !"PENDING".equals(String.valueOf(approval.get("status")))) {
            throw new IllegalArgumentException("计划审批记录已处理");
        }
        if (mapper.updatePlanStatus(planId, "WAITING_APPROVAL", "APPROVED", reviewerId) != 1) {
            throw new IllegalStateException("计划状态已变化，请刷新后重试");
        }
        if (approval != null && mapper.updateApprovalStatus(
                idOf(approval, "审批记录"), "PENDING", "APPROVED", reviewerId,
                decisionComment(decisionComment, "计划已批准")) != 1) {
            throw new IllegalStateException("计划审批记录状态已变化，请刷新后重试");
        }
        transitionTask(taskId, "WAITING_APPROVAL", "READY");
        insertEvent(taskId, "PLAN_APPROVED", "Agent 计划已批准，可以执行");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", mapper.findLatestPlan(taskId));
        result.put("steps", mapper.listSteps(taskId));
        return result;
    }

    private void transitionTask(long taskId, String fromStatus, String toStatus) {
        if (mapper.updateTaskStatus(taskId, List.of(fromStatus), toStatus, true, 0, false) != 1) {
            throw new IllegalStateException("任务状态已变化，请刷新后重试");
        }
    }

    private void insertEvent(long taskId, String eventType, String summary) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("taskId", taskId);
        event.put("stepId", null);
        event.put("eventType", eventType);
        event.put("visibility", "PUBLIC");
        event.put("summary", summary);
        event.put("payloadJson", null);
        if (mapper.insertEvent(event) != 1) {
            throw new IllegalStateException("Agent 计划事件写入失败");
        }
    }

    private Map<String, Object> requireTask(long taskId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("任务编号无效");
        }
        Map<String, Object> task = mapper.findTask(taskId);
        if (task == null || task.isEmpty()) {
            throw new IllegalArgumentException("Agent 任务不存在");
        }
        return task;
    }

    private static void requireStatus(Map<String, Object> task, String expected, String message) {
        if (!expected.equals(String.valueOf(task.get("status")))) {
            throw new IllegalArgumentException(message);
        }
    }

    private Map<String, Object> requireApproval(long approvalId) {
        if (approvalId <= 0) {
            throw new IllegalArgumentException("审批编号无效");
        }
        Map<String, Object> approval = mapper.findApproval(approvalId);
        if (approval == null || approval.isEmpty()) {
            throw new IllegalArgumentException("Agent 审批记录不存在");
        }
        return approval;
    }

    private static void requirePlanApproval(Map<String, Object> approval) {
        if (!"PLAN".equals(String.valueOf(approval.get("approvalType")))) {
            throw new IllegalArgumentException("当前仅支持处理计划审批");
        }
    }

    private Map<String, Object> approveToolApproval(Map<String, Object> approval, Long reviewerId,
                                                    String decisionComment) {
        requirePendingApproval(approval);
        long taskId = idOf(approval, "工具审批任务", "taskId");
        long stepId = idOf(approval, "工具审批步骤", "stepId");
        requireStatus(requireTask(taskId), "WAITING_CONFIRMATION", "当前任务不在工具确认状态");
        if (mapper.updateApprovalStatus(idOf(approval, "工具审批"), "PENDING", "APPROVED", reviewerId,
                decisionComment(decisionComment, "工具执行已批准")) != 1
                || mapper.approveToolStep(stepId) != 1
                || mapper.updateTaskStatus(taskId, List.of("WAITING_CONFIRMATION"), "READY", true, 0, false) != 1) {
            throw new IllegalStateException("工具审批状态已变化，请刷新后重试");
        }
        insertEvent(taskId, "APPROVAL_APPROVED", "工具执行审批已通过");
        return Map.of("approval", mapper.findApproval(idOf(approval, "工具审批")), "task", mapper.findTask(taskId));
    }

    private Map<String, Object> rejectToolApproval(Map<String, Object> approval, Long reviewerId,
                                                   String decisionComment) {
        requirePendingApproval(approval);
        long taskId = idOf(approval, "工具审批任务", "taskId");
        long stepId = idOf(approval, "工具审批步骤", "stepId");
        if (mapper.updateApprovalStatus(idOf(approval, "工具审批"), "PENDING", "REJECTED", reviewerId,
                decisionComment(decisionComment, "工具执行已拒绝")) != 1
                || mapper.rejectToolStep(stepId, "APPROVAL_REJECTED", "工具执行审批被拒绝") != 1
                || mapper.failTask(taskId, "APPROVAL_REJECTED", "工具执行审批被拒绝") != 1) {
            throw new IllegalStateException("工具审批状态已变化，请刷新后重试");
        }
        insertEvent(taskId, "APPROVAL_REJECTED", "工具执行审批已拒绝");
        return Map.of("approval", mapper.findApproval(idOf(approval, "工具审批")), "task", mapper.findTask(taskId));
    }

    private static void requirePendingApproval(Map<String, Object> approval) {
        if (!"PENDING".equalsIgnoreCase(String.valueOf(approval.get("status")))) {
            throw new IllegalArgumentException("审批记录已处理");
        }
    }

    private String planJson(Map<String, Object> task, List<StepDefinition> definitions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("taskType", task.get("taskType"));
        payload.put("generatedBy", "SYSTEM_TEMPLATE");
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            StepDefinition definition = definitions.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepKey", definition.key());
            item.put("name", definition.name());
            item.put("agentRole", definition.agentRole());
            item.put("stepType", definition.stepType());
            item.put("toolName", definition.toolName());
            item.put("dependsOn", definition.dependsOn());
            item.put("order", index + 1);
            steps.add(item);
        }
        payload.put("steps", steps);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Agent 计划格式化失败", e);
        }
    }

    private String json(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Agent 步骤依赖格式化失败", e);
        }
    }

    private static String shortText(Object title, Object objective, int maxLength) {
        String heading = title == null ? "" : String.valueOf(title).trim();
        String body = objective == null ? "" : String.valueOf(objective).trim();
        String value = heading.isBlank() ? body : heading + "\n" + body;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Map<String, Object> mapperStep(Map<String, Object> step, long id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("stepKey", step.get("stepKey"));
        row.put("stepName", step.get("stepName"));
        row.put("agentRole", step.get("agentRole"));
        row.put("stepType", step.get("stepType"));
        row.put("status", step.get("status"));
        row.put("toolName", step.get("toolName"));
        row.put("sortOrder", step.get("sortOrder"));
        row.put("attemptCount", 0);
        row.put("maxAttempts", step.get("maxAttempts"));
        return row;
    }

    private static long idOf(Map<String, Object> row, String label) {
        return idOf(row, label, "id");
    }

    private static long idOf(Map<String, Object> row, String label, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            throw new IllegalStateException(label + "创建后没有返回编号");
        }
        return number.longValue();
    }

    private static String decisionComment(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.length() <= 2_000 ? normalized : normalized.substring(0, 2_000);
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String riskForTool(String toolName) {
        return switch (toolName == null ? "" : toolName) {
            case "worldbook.create", "character.sync_st" -> "EXTERNAL_SIDE_EFFECT";
            case "character.create", "character.publish", "story.create_outline", "story.write_chapter", "story.revise", "image.generate", "tts.generate", "artifact.render_html", "artifact.render_report", "artifact.package_zip" -> "WRITE_REVERSIBLE";
            default -> "READ_ONLY";
        };
    }

    private static String normalizedRisk(Object value) {
        String risk = value == null ? "READ_ONLY" : String.valueOf(value).trim().toUpperCase();
        return riskRank(risk) < 0 ? "READ_ONLY" : risk;
    }

    private static String maxRisk(String left, String right) {
        String safeLeft = normalizedRisk(left);
        String safeRight = normalizedRisk(right);
        return riskRank(safeLeft) >= riskRank(safeRight) ? safeLeft : safeRight;
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

    private static List<StepDefinition> templateFor(String taskType) {
        return switch (taskType == null ? "GENERAL" : taskType.trim().toUpperCase()) {
            case "CONTENT_CREATION" -> List.of(
                    step("requirements", "需求分析", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("world", "世界观与世界书", "World Builder", "CONTENT", "worldbook.create", List.of("requirements")),
                    step("characters", "角色设计", "Character Designer", "CONTENT", "character.create", List.of("world")),
                    step("character_review", "角色质量校验", "Reviewer", "REVIEW", "character.validate", List.of("characters")),
                    step("outline", "剧情大纲", "Plotter", "CONTENT", "story.create_outline", List.of("character_review")),
                    step("chapters", "章节正文", "Writer", "CONTENT", "story.write_chapter", List.of("outline")),
                    step("review", "一致性检查", "Reviewer", "REVIEW", "story.check_continuity", List.of("chapters")),
                    step("revise", "问题修订", "Writer", "CONTENT", "story.revise", List.of("review")),
                    step("review_final", "最终一致性复核", "Reviewer", "REVIEW", "story.check_continuity", List.of("revise")),
                    step("media", "媒体制作", "Media Producer", "MEDIA", "image.generate", List.of("review_final")),
                    step("voiceover", "角色配音", "Media Producer", "MEDIA", "tts.generate", List.of("media")),
                    step("web", "互动网页", "Delivery Agent", "DELIVERY", "artifact.render_html", List.of("voiceover")),
                    step("delivery", "作品交付", "Delivery Agent", "DELIVERY", "artifact.package_zip", List.of("web"))
            );
            case "DATA_ANALYSIS" -> List.of(
                    step("requirements", "分析需求", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("query", "读取指标", "Data Analyst", "ANALYSIS", "analytics.query", List.of("requirements")),
                    step("review", "结果复核", "Reviewer", "REVIEW", "analytics.review", List.of("query")),
                    step("report", "报告交付", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("review"))
            );
            case "SYSTEM_INSPECTION" -> List.of(
                    step("scope", "确定检查范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("inspect", "执行只读检查", "DevOps Inspector", "INSPECTION", "system.health_check", List.of("scope")),
                    step("report", "检查报告", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("inspect"))
            );
            case "CHARACTER_PUBLISH" -> List.of(
                    step("requirements", "确认发布目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("publish", "发布角色", "Reviewer", "CONTENT", "character.publish", List.of("requirements"))
            );
            case "CHARACTER_SYNC" -> List.of(
                    step("requirements", "确认同步目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("sync", "同步角色卡", "Delivery Agent", "DELIVERY", "character.sync_st", List.of("requirements"))
            );
            default -> List.of(
                    step("requirements", "分析任务目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("delivery", "整理任务结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("requirements"))
            );
        };
    }

    private static StepDefinition step(String key, String name, String role, String type, String tool, List<String> dependsOn) {
        return new StepDefinition(key, name, role, type, tool, dependsOn);
    }

    private record StepDefinition(
            String key,
            String name,
            String agentRole,
            String stepType,
            String toolName,
            List<String> dependsOn
    ) {}
}
