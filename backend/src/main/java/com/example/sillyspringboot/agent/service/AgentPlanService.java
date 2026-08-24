package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.security.AgentParameterHasher;
import com.example.sillyspringboot.agent.tool.AgentEvaluationTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentPlanService {

    private static final Logger log = LoggerFactory.getLogger(AgentPlanService.class);
    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;
    private final AgentEvaluationTool evaluationTool;
    private final AgentPlannerModelService plannerModelService;

    @Value("${app.agent.workflow-template-enabled:false}")
    private boolean workflowTemplateEnabled;

    public AgentPlanService(AgentAdminMapper mapper, ObjectMapper objectMapper) {
        this(mapper, objectMapper, null, null);
    }

    @Autowired
    public AgentPlanService(
            AgentAdminMapper mapper,
            ObjectMapper objectMapper,
            AgentEvaluationTool evaluationTool,
            AgentPlannerModelService plannerModelService
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.evaluationTool = evaluationTool;
        this.plannerModelService = plannerModelService;
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
        PlanSelection selection = selectPlan(task);
        List<StepDefinition> definitions = selection.definitions();
        validatePlan(definitions);
        int maxSteps = Math.max(1, number(task.get("maxSteps"), 50));
        if (definitions.size() > maxSteps) {
            throw new IllegalArgumentException("Agent 计划步骤数超过任务 maxSteps 限制");
        }
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
        plan.put("generatedBy", selection.generatedBy());
        plan.put("inputHash", null);
        plan.put("planJson", planJson(task, definitions, selection.generatedBy()));
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
            step.put("inputJson", definition.inputJson() == null
                    ? inputJsonForTool(task, definition.toolName())
                    : definition.inputJson());
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
        approval.put("expiresAt", LocalDateTime.now().plusMinutes(30));
        if (mapper.insertApproval(approval) != 1) {
            throw new IllegalStateException("Agent 计划审批记录创建失败");
        }
        transitionTask(taskId, "PLANNING", "WAITING_APPROVAL");
        insertEvent(taskId, "PLAN_CREATED", "Agent 计划已生成，等待审批");
        if ("MODEL_PROPOSAL".equals(selection.generatedBy())) {
            insertEvent(taskId, "PLAN_MODEL_PROPOSAL", "Agent 计划来自受控模型提案，已完成服务端校验");
        }
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
        requirePendingApproval(approval);
        requirePlanApproval(approval);
        return approve(idOf(approval, "审批任务", "taskId"), reviewerId, approvalId, decisionComment);
    }

    @Transactional
    public Map<String, Object> rejectApproval(long approvalId, Long reviewerId, String decisionComment) {
        Map<String, Object> approval = requireApproval(approvalId);
        if ("TOOL".equalsIgnoreCase(String.valueOf(approval.get("approvalType")))) {
            return rejectToolApproval(approval, reviewerId, decisionComment);
        }
        requirePendingApproval(approval);
        requirePlanApproval(approval);
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
        Map<String, Object> task = requireTask(taskId);
        requireStatus(task, "WAITING_CONFIRMATION", "当前任务不在工具确认状态");
        List<Map<String, Object>> taskSteps = mapper.listSteps(taskId);
        Map<String, Object> step = taskSteps == null ? null : taskSteps.stream()
                .filter(row -> idOf(row, "工具审批步骤", "id") == stepId)
                .findFirst()
                .orElse(null);
        if (step == null) {
            throw new IllegalArgumentException("工具审批步骤不存在");
        }
        String expectedHash = String.valueOf(approval.getOrDefault("parameterHash", "")).trim();
        String actualHash = AgentParameterHasher.hash(
                objectMapper,
                String.valueOf(step.getOrDefault("toolName", "")),
                String.valueOf(step.getOrDefault("agentRole", "")),
                String.valueOf(step.getOrDefault("inputJson", "")),
                String.valueOf(task.getOrDefault("objective", "")),
                String.valueOf(task.getOrDefault("constraintsJson", ""))
        );
        if (expectedHash.isBlank() || !expectedHash.equals(actualHash)) {
            throw new IllegalArgumentException("工具审批参数已变化或缺少参数指纹，请重新生成审批");
        }
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
        saveEvaluationAfterTerminalTransition(taskId);
        return Map.of("approval", mapper.findApproval(idOf(approval, "工具审批")), "task", mapper.findTask(taskId));
    }

    private void saveEvaluationAfterTerminalTransition(long taskId) {
        if (evaluationTool == null) {
            return;
        }
        try {
            evaluationTool.saveArtifact(taskId, 0L, mapper.findTask(taskId));
        } catch (RuntimeException e) {
            log.warn("Agent 审批拒绝后的评估产物保存失败，taskId={}", taskId, e);
        }
    }

    private static void requirePendingApproval(Map<String, Object> approval) {
        if (!"PENDING".equalsIgnoreCase(String.valueOf(approval.get("status")))) {
            throw new IllegalArgumentException("审批记录已处理");
        }
        Object expiresAt = approval.get("expiresAt");
        if (expiresAt != null && !String.valueOf(expiresAt).isBlank()) {
            try {
                String value = String.valueOf(expiresAt).trim().replace(' ', 'T');
                if (LocalDateTime.parse(value).isBefore(LocalDateTime.now())) {
                    throw new IllegalArgumentException("审批已过期，请重新生成审批");
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("审批过期时间格式无效");
            }
        }
    }

    private String planJson(Map<String, Object> task, List<StepDefinition> definitions, String generatedBy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("taskType", task.get("taskType"));
        payload.put("generatedBy", generatedBy);
        String workflowKey = requestedWorkflowKey(task.get("constraintsJson"));
        if ("WORKFLOW_TEMPLATE".equals(generatedBy)) {
            Map<String, Object> template = mapper.findEnabledWorkflowTemplateForExecution(
                    String.valueOf(task.get("taskType")), workflowKey);
            if (template != null) {
                payload.put("workflowId", template.get("id"));
                payload.put("workflowKey", template.get("workflowKey"));
                payload.put("workflowVersion", template.get("version"));
                payload.put("failurePolicy", template.get("failurePolicy"));
            }
        } else if (workflowKey != null) {
            payload.put("workflowKey", workflowKey);
        }
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
            if (definition.inputJson() != null) {
                item.put("inputJson", definition.inputJson());
            }
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
            case "worldbook.create", "character.sync_st", "mcp.call", "code.run", "dsh.session.create", "dsh.session.prompt", "dsh.browser.interact", "dsh.devops.build", "user.ticket.reply", "user.account.credit", "content.moderation.action" -> "EXTERNAL_SIDE_EFFECT";
            case "agent.handoff" -> "WRITE_REVERSIBLE";
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

    private PlanSelection selectPlan(Map<String, Object> task) {
        String taskType = String.valueOf(task.get("taskType")).trim().toUpperCase();
        List<StepDefinition> configured = configuredTemplate(taskType, task);
        if (configured != null) {
            return new PlanSelection(configured, "WORKFLOW_TEMPLATE");
        }
        if ("GENERAL".equals(taskType)) {
            List<StepDefinition> proposal = modelProposal(task.get("constraintsJson"));
            if (proposal == null && plannerModelService != null) {
                try {
                    proposal = modelProposal(plannerModelService.propose(task));
                } catch (IllegalArgumentException ex) {
                    log.warn("Agent Planner 模型提案未通过解析，回退固定模板: {}", ex.getMessage());
                    insertEvent(idOf(task, "任务"), "PLAN_MODEL_PROPOSAL_REJECTED",
                            "模型计划提案未通过校验，已回退规则计划: " + shortText(ex.getMessage(), null, 500));
                }
            }
            if (proposal != null) {
                return new PlanSelection(proposal, "MODEL_PROPOSAL");
            }
        }
        List<StepDefinition> fixed = templateFor(taskType);
        if (!"GENERAL".equals(taskType)) {
            return new PlanSelection(fixed, "SYSTEM_TEMPLATE");
        }
        String objective = String.valueOf(task.get("objective")).toLowerCase();
        if (containsAny(objective, "deepseek harness", "deepseek-harness", "dsh 会话", "dsh prompt", "dsh agent")) {
            return new PlanSelection(templateFor("DSH_SESSION"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "dsh 平台巡检", "dsh platform audit", "dsh 能力检查")) {
            return new PlanSelection(templateFor("DSH_PLATFORM_AUDIT"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "dsh browser", "browser 交互", "网页交互")) {
            return new PlanSelection(templateFor("DSH_BROWSER"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "巡检", "健康检查", "jvm", "磁盘", "git")) {
            return new PlanSelection(templateFor("SYSTEM_INSPECTION"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "统计", "分析", "趋势", "报表", "运营")) {
            if (containsAny(objective, "系统运营", "运营优化", "生产环境报告", "系统与运营")) {
                return new PlanSelection(templateFor("OPERATIONS_REVIEW"), "RULE_BASED_PLANNER");
            }
            return new PlanSelection(templateFor("DATA_ANALYSIS"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "发布角色", "发布角色卡")) {
            return new PlanSelection(templateFor("CHARACTER_PUBLISH"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "生产发布", "部署服务", "构建产物", "回滚部署", "devops", "运维发布")) {
            if (containsAny(objective, "完整发布", "发布部署回滚", "发布并健康检查", "deploy and rollback")) {
                return new PlanSelection(templateFor("DSH_DEVOPS_RELEASE"), "RULE_BASED_PLANNER");
            }
            return new PlanSelection(templateFor("DEVOPS_REVIEW"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "构建项目", "执行构建", "maven package", "dsh devops build")) {
            return new PlanSelection(templateFor("DSH_DEVOPS_BUILD"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "同步角色", "同步到 sillytavern", "同步到st")) {
            return new PlanSelection(templateFor("CHARACTER_SYNC"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "网页搜索", "web search", "搜索网页", "查资料")) {
            return new PlanSelection(templateFor("WEB_SEARCH"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "浏览网页", "browser", "打开网页", "读取页面")) {
            return new PlanSelection(templateFor("BROWSER_OPEN"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "api请求", "api request", "外部接口", "接口查询")) {
            return new PlanSelection(templateFor("API_REQUEST"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "mcp", "模型上下文协议")) {
            return new PlanSelection(templateFor("MCP_CALL"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "代码执行", "运行python", "code runner")) {
            return new PlanSelection(templateFor("CODE_RUN"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "用户资料", "用户画像")) {
            return new PlanSelection(templateFor("USER_PROFILE"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "用户账户", "订单余额", "钱包余额")) {
            if (containsAny(objective, "补发", "充值", "加余额", "加款")) {
                return new PlanSelection(templateFor("USER_ACCOUNT_CREDIT"), "RULE_BASED_PLANNER");
            }
            return new PlanSelection(templateFor("USER_ACCOUNT"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "回复工单", "工单回复", "客服回复")) {
            return new PlanSelection(templateFor("USER_TICKET_REPLY"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "用户工单", "客服工单")) {
            return new PlanSelection(templateFor("USER_TICKET"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "用户风险", "异常用户", "风险设备")) {
            return new PlanSelection(templateFor("USER_RISK"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "内容审核", "角色审核", "违规内容")) {
            if (containsAny(objective, "审核通过", "审核驳回", "下架审核", "修改审核状态")) {
                return new PlanSelection(templateFor("CONTENT_MODERATION_ACTION"), "RULE_BASED_PLANNER");
            }
            return new PlanSelection(templateFor("CONTENT_MODERATION"), "RULE_BASED_PLANNER");
        }
        if (containsAny(objective, "协作交接", "handoff", "多 agent", "多agent")) {
            return new PlanSelection(templateFor("COLLABORATION"), "RULE_BASED_PLANNER");
        }
        return new PlanSelection(fixed, "SYSTEM_TEMPLATE");
    }

    private List<StepDefinition> configuredTemplate(String taskType, Map<String, Object> task) {
        if (!workflowTemplateEnabled || taskType == null || taskType.isBlank()) {
            return null;
        }
        String requestedWorkflowKey = requestedWorkflowKey(task.get("constraintsJson"));
        Map<String, Object> template = mapper.findEnabledWorkflowTemplateForExecution(taskType, requestedWorkflowKey);
        if (requestedWorkflowKey != null && template == null) {
            throw new IllegalArgumentException("请求的 Agent 工作流不存在、未启用或与任务类型不匹配: " + requestedWorkflowKey);
        }
        if (template == null) {
            return null;
        }
        String rawSteps = String.valueOf(template.get("stepsJson"));
        try {
            JsonNode steps = objectMapper.readTree(rawSteps);
            if (steps == null || !steps.isArray() || steps.isEmpty()) {
                throw new IllegalArgumentException("工作流模板 stepsJson 必须是非空数组");
            }
            List<StepDefinition> definitions = new ArrayList<>();
            for (JsonNode node : steps) {
                if (node == null || !node.isObject()) {
                    throw new IllegalArgumentException("工作流模板步骤必须是对象");
                }
                String key = boundedNodeText(node.get("key"), 100, "工作流步骤 key");
                String name = boundedNodeText(node.get("name"), 255, "工作流步骤名称");
                String role = boundedNodeText(node.get("agentRole"), 80, "工作流步骤角色");
                String type = boundedNodeText(node.get("stepType"), 64, "工作流步骤类型");
                String tool = boundedNodeText(node.get("toolName"), 160, "工作流步骤工具");
                JsonNode depends = node.get("dependsOn");
                if (depends == null || !depends.isArray()) {
                    throw new IllegalArgumentException("工作流步骤必须声明 dependsOn 数组");
                }
                List<String> dependencies = new ArrayList<>();
                for (JsonNode dependency : depends) {
                    dependencies.add(boundedNodeText(dependency, 100, "工作流步骤依赖"));
                }
                JsonNode input = node.get("inputJson");
                String inputJson = null;
                if (input != null && !input.isNull()) {
                    inputJson = input.isTextual() ? input.textValue() : objectMapper.writeValueAsString(input);
                    objectMapper.readTree(inputJson);
                }
                definitions.add(new StepDefinition(key, name, role, type, tool, dependencies, inputJson));
            }
            Set<String> allowedTools = templateStringSet(template.get("allowedToolsJson"), "工作流工具白名单");
            Set<String> allowedRoles = templateStringSet(template.get("allowedRolesJson"), "工作流角色白名单");
            int maxSteps = Math.max(1, Math.min(32, number(template.get("maxSteps"), 32)));
            if (definitions.size() > maxSteps) {
                throw new IllegalArgumentException("工作流模板步骤数超过模板 maxSteps 限制");
            }
            for (StepDefinition definition : definitions) {
                if (!allowedTools.contains(definition.toolName())) {
                    throw new IllegalArgumentException("工作流步骤工具不在模板白名单: " + definition.toolName());
                }
                if (!allowedRoles.contains(definition.agentRole())) {
                    throw new IllegalArgumentException("工作流步骤角色不在模板白名单: " + definition.agentRole());
                }
            }
            validatePlan(definitions);
            applyTemplateLimits(task, maxSteps, template);
            return definitions;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("工作流模板 stepsJson 无效", ex);
        }
    }

    private void applyTemplateLimits(Map<String, Object> task, int templateMaxSteps, Map<String, Object> template) {
        long taskId = idOf(task, "任务");
        int taskMaxSteps = Math.max(1, number(task.get("maxSteps"), 32));
        int taskMaxRuntime = Math.max(1, number(task.get("maxRuntimeSeconds"), 3600));
        long taskMaxTokens = longNumber(task.get("maxTokens"), 100_000_000L);
        BigDecimal taskMaxCost = decimal(task.get("maxCost"), BigDecimal.ZERO);
        int effectiveSteps = Math.min(taskMaxSteps, Math.max(1, templateMaxSteps));
        int effectiveRuntime = Math.min(taskMaxRuntime, Math.max(1, number(template.get("maxRuntimeSeconds"), taskMaxRuntime)));
        long configuredTokens = longNumber(template.get("maxTokens"), 0L);
        long effectiveTokens = configuredTokens > 0 ? Math.min(taskMaxTokens, configuredTokens) : taskMaxTokens;
        BigDecimal configuredCost = decimal(template.get("maxCost"), BigDecimal.ZERO);
        BigDecimal effectiveCost = configuredCost.signum() > 0 ? taskMaxCost.min(configuredCost) : taskMaxCost;
        if (mapper.updateTaskExecutionLimits(taskId, effectiveSteps, effectiveRuntime, effectiveTokens, effectiveCost) != 1) {
            throw new IllegalStateException("工作流模板执行边界写入失败");
        }
        task.put("maxSteps", effectiveSteps);
        task.put("maxRuntimeSeconds", effectiveRuntime);
        task.put("maxTokens", effectiveTokens);
        task.put("maxCost", effectiveCost);
    }

    private static long longNumber(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private static BigDecimal decimal(Object value, BigDecimal fallback) {
        try { return value == null ? fallback : new BigDecimal(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private String requestedWorkflowKey(Object rawConstraints) {
        if (rawConstraints == null || String.valueOf(rawConstraints).isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(String.valueOf(rawConstraints));
            JsonNode value = root == null ? null : root.get("workflowKey");
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isTextual()) {
                throw new IllegalArgumentException("constraints.workflowKey 必须是字符串");
            }
            String key = value.textValue().trim();
            if (key.isBlank() || key.length() > 80 || !key.matches("[a-z0-9][a-z0-9._-]{1,79}")) {
                throw new IllegalArgumentException("constraints.workflowKey 格式无效");
            }
            return key;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("任务 constraints JSON 无效", ex);
        }
    }

    private Set<String> templateStringSet(Object raw, String label) throws JsonProcessingException {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        JsonNode node = objectMapper.readTree(String.valueOf(raw));
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException(label + "必须是非空 JSON 数组");
        }
        Set<String> values = new HashSet<>();
        for (JsonNode value : node) {
            if (value == null || !value.isTextual() || value.textValue().isBlank()) {
                throw new IllegalArgumentException(label + "包含无效项");
            }
            values.add(value.textValue().trim());
        }
        return values;
    }

    /** Parses a model-produced candidate without granting it direct execution authority. */
    private List<StepDefinition> modelProposal(Object rawConstraints) {
        if (rawConstraints == null || String.valueOf(rawConstraints).isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(String.valueOf(rawConstraints));
            JsonNode proposal = root == null ? null : root.get("plannerProposal");
            if (proposal == null || proposal.isNull()) {
                return null;
            }
            if (!proposal.isObject()) {
                throw new IllegalArgumentException("plannerProposal 必须是 JSON 对象");
            }
            JsonNode steps = proposal.get("steps");
            if (steps == null || !steps.isArray() || steps.isEmpty()) {
                throw new IllegalArgumentException("plannerProposal.steps 不能为空");
            }
            List<StepDefinition> definitions = new ArrayList<>();
            for (JsonNode node : steps) {
                if (node == null || !node.isObject()) {
                    throw new IllegalArgumentException("模型计划步骤必须是对象");
                }
                String key = boundedNodeText(node.get("key"), 100, "步骤 key");
                String name = boundedNodeText(node.get("name"), 255, "步骤名称");
                String role = boundedNodeText(node.get("agentRole"), 80, "Agent 角色");
                String type = boundedNodeText(node.get("stepType"), 64, "步骤类型");
                String tool = boundedNodeText(node.get("toolName"), 160, "工具名称");
                JsonNode depends = node.get("dependsOn");
                if (depends == null || !depends.isArray()) {
                    throw new IllegalArgumentException("模型计划步骤必须声明 dependsOn 数组");
                }
                List<String> dependencies = new ArrayList<>();
                for (JsonNode dependency : depends) {
                    String dependencyKey = boundedNodeText(dependency, 100, "步骤依赖");
                    dependencies.add(dependencyKey);
                }
                JsonNode input = node.get("inputJson");
                String inputJson = null;
                if (input != null && !input.isNull()) {
                    inputJson = input.isTextual() ? input.textValue() : objectMapper.writeValueAsString(input);
                    objectMapper.readTree(inputJson);
                    if (inputJson.length() > 60_000) {
                        throw new IllegalArgumentException("模型计划步骤 inputJson 过长");
                    }
                }
                definitions.add(new StepDefinition(key, name, role, type, tool, dependencies, inputJson));
            }
            validatePlan(definitions);
            return definitions;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("plannerProposal JSON 无效", ex);
        }
    }

    private static String boundedNodeText(JsonNode node, int maxLength, String label) {
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String value = node.textValue() == null ? "" : node.textValue().trim();
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度无效");
        }
        return value;
    }

    private String inputJsonForTool(Map<String, Object> task, String toolName) {
        if (!constraintInputTools().contains(toolName)) {
            return null;
        }
        Object raw = task.get("constraintsJson");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return "{}";
        }
        String value = String.valueOf(raw).trim();
        try {
            objectMapper.readTree(value);
            return value;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("任务 constraints 必须是有效 JSON", ex);
        }
    }

    private static Set<String> constraintInputTools() {
        return Set.of(
                "api.request", "web.search", "browser.open", "dsh.browser.open", "dsh.browser.interact", "dsh.devops.inspect", "dsh.devops.build", "dsh.devops.rollback", "dsh.monitor.snapshot", "dsh.backup.inspect", "dsh.secret.status", "dsh.evaluate.run", "mcp.call", "code.run", "dsh.session.create", "dsh.session.prompt",
                "user.profile.query", "user.account.query", "user.account.credit", "user.ticket.query", "user.ticket.reply",
                "user.risk.query", "content.moderation.review", "content.moderation.action", "agent.handoff"
        );
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static void validatePlan(List<StepDefinition> definitions) {
        if (definitions.isEmpty() || definitions.size() > 32) {
            throw new IllegalArgumentException("Agent 计划步骤数必须在 1 到 32 之间");
        }
        Set<String> keys = new HashSet<>();
        Set<String> tools = allowedTools();
        for (StepDefinition definition : definitions) {
            if (!keys.add(definition.key())) {
                throw new IllegalArgumentException("Agent 计划包含重复步骤: " + definition.key());
            }
            if (!tools.contains(definition.toolName())) {
                throw new IllegalArgumentException("Agent 计划包含未注册工具: " + definition.toolName());
            }
        }
        for (StepDefinition definition : definitions) {
            for (String dependency : definition.dependsOn()) {
                if (!keys.contains(dependency) || dependency.equals(definition.key())) {
                    throw new IllegalArgumentException("Agent 计划依赖无效: " + definition.key() + " -> " + dependency);
                }
            }
        }
        for (StepDefinition definition : definitions) {
            if (hasDependencyCycle(definition.key(), definitions, new HashSet<>(), new HashSet<>())) {
                throw new IllegalArgumentException("Agent 计划存在循环依赖");
            }
        }
    }

    private static boolean hasDependencyCycle(String key, List<StepDefinition> definitions,
                                               Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) {
            return false;
        }
        if (!visiting.add(key)) {
            return true;
        }
        StepDefinition current = definitions.stream()
                .filter(definition -> definition.key().equals(key))
                .findFirst()
                .orElseThrow();
        for (String dependency : current.dependsOn()) {
            if (hasDependencyCycle(dependency, definitions, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(key);
        visited.add(key);
        return false;
    }

    private static Set<String> allowedTools() {
        return Set.of(
                "planner.analyze", "worldbook.create", "character.create", "character.validate",
                "character.publish", "character.sync_st", "story.create_outline", "story.write_chapter",
                "story.check_continuity", "story.revise", "image.generate", "tts.generate",
                "artifact.render_html", "artifact.package_zip", "analytics.query", "analytics.review",
                "artifact.render_report", "artifact.evaluate", "content.moderation.review", "content.moderation.action", "user.profile.query", "user.ticket.query", "user.ticket.reply", "user.account.query", "user.account.credit", "user.risk.query", "agent.handoff", "system.health_check", "api.request", "web.search", "browser.open", "dsh.browser.open", "dsh.browser.interact", "dsh.devops.inspect", "dsh.devops.build", "dsh.devops.publish", "dsh.devops.deploy", "dsh.devops.health", "dsh.devops.rollback", "dsh.monitor.snapshot", "dsh.backup.inspect", "dsh.secret.status", "dsh.evaluate.run", "mcp.call", "code.run", "dsh.session.create", "dsh.session.prompt"
        );
    }

    private record PlanSelection(List<StepDefinition> definitions, String generatedBy) {}

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
            case "DSH_SESSION" -> List.of(
                    step("requirements", "确认 DSH 会话目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("create_session", "创建 DSH 会话", "Developer", "AGENT_RUNTIME", "dsh.session.create", List.of("requirements")),
                    step("prompt", "提交 DSH 任务目标", "Developer", "AGENT_RUNTIME", "dsh.session.prompt", List.of("create_session")),
                    step("report", "记录 DSH 会话结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("prompt"))
            );
            case "DSH_PLATFORM_AUDIT" -> List.of(
                    step("requirements", "确认 DSH 平台巡检范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("secrets", "检查受控凭据就绪状态", "Developer", "INSPECTION", "dsh.secret.status", List.of("requirements")),
                    step("monitor", "采集系统监控快照", "Developer", "INSPECTION", "dsh.monitor.snapshot", List.of("requirements")),
                    step("backup", "检查备份恢复准备状态", "Developer", "INSPECTION", "dsh.backup.inspect", List.of("monitor")),
                    step("report", "生成 DSH 平台巡检报告", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("secrets", "backup")),
                    step("evaluate", "评估巡检任务质量", "Reviewer", "REVIEW", "dsh.evaluate.run", List.of("report"))
            );
            case "DSH_BROWSER" -> List.of(
                    step("requirements", "确认 DSH Browser 访问目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    stepWithInput("open", "打开白名单页面", "Researcher", "EXTERNAL", "dsh.browser.interact", List.of("requirements"), "{\"action\":\"open\"}"),
                    stepWithInput("input", "填写非敏感页面字段", "Researcher", "EXTERNAL", "dsh.browser.interact", List.of("open"), "{\"action\":\"input\"}"),
                    stepWithInput("click", "点击白名单安全控件", "Researcher", "EXTERNAL", "dsh.browser.interact", List.of("input"), "{\"action\":\"click\"}"),
                    stepWithInput("extract", "提取页面内容", "Researcher", "EXTERNAL", "dsh.browser.interact", List.of("click"), "{\"action\":\"extract\"}"),
                    stepWithInput("close", "关闭 Browser 会话", "Researcher", "EXTERNAL", "dsh.browser.interact", List.of("extract"), "{\"action\":\"close\"}"),
                    step("report", "整理 Browser 结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("close"))
            );
            case "SYSTEM_INSPECTION" -> List.of(
                    step("scope", "确定检查范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("inspect", "执行只读检查", "DevOps Inspector", "INSPECTION", "system.health_check", List.of("scope")),
                    step("report", "检查报告", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("inspect"))
            );
            case "OPERATIONS_REVIEW" -> List.of(
                    step("requirements", "确定系统与运营检查范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("analytics", "读取运营指标", "Data Analyst", "ANALYSIS", "analytics.query", List.of("requirements")),
                    step("inspect", "执行系统健康检查", "DevOps Inspector", "INSPECTION", "system.health_check", List.of("requirements")),
                    step("analytics_review", "复核运营指标", "Reviewer", "REVIEW", "analytics.review", List.of("analytics")),
                    step("report", "生成系统与运营报告", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("analytics_review", "inspect"))
            );
            case "DEVOPS_REVIEW" -> List.of(
                    step("requirements", "确认发布与回滚范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("inspect", "检查数据库、构建包与工作区", "DevOps Inspector", "INSPECTION", "dsh.devops.inspect", List.of("requirements")),
                    step("build", "执行审批后的 Maven 构建", "Developer", "EXTERNAL", "dsh.devops.build", List.of("inspect")),
                    step("report", "生成发布审批材料", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("build"))
            );
            case "DSH_DEVOPS_BUILD" -> List.of(
                    step("requirements", "确认构建范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("build", "执行受控 Maven 构建", "Developer", "EXTERNAL", "dsh.devops.build", List.of("requirements")),
                    step("report", "生成构建结果报告", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("build"))
            );
            case "DSH_DEVOPS_RELEASE" -> List.of(
                    step("requirements", "确认发布范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("inspect", "检查发布前工作区", "DevOps Inspector", "INSPECTION", "dsh.devops.inspect", List.of("requirements")),
                    step("build", "构建发布制品", "Developer", "EXTERNAL", "dsh.devops.build", List.of("inspect")),
                    step("publish", "发布构建制品", "Developer", "EXTERNAL", "dsh.devops.publish", List.of("build")),
                    step("deploy", "部署到受控本机目标", "Developer", "EXTERNAL", "dsh.devops.deploy", List.of("publish")),
                    step("health", "执行部署健康检查", "DevOps Inspector", "EXTERNAL", "dsh.devops.health", List.of("deploy")),
                    step("report", "生成发布报告", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("health"))
            );
            case "DSH_DEVOPS_ROLLBACK" -> List.of(
                    step("requirements", "确认回滚范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("rollback", "按受控引用执行回滚", "Developer", "EXTERNAL", "dsh.devops.rollback", List.of("requirements")),
                    step("health", "验证回滚后健康状态", "DevOps Inspector", "EXTERNAL", "dsh.devops.health", List.of("rollback")),
                    step("report", "生成回滚报告", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("health"))
            );
            case "CHARACTER_PUBLISH" -> List.of(
                    step("requirements", "确认发布目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("publish", "发布角色", "Reviewer", "CONTENT", "character.publish", List.of("requirements"))
            );
            case "CHARACTER_SYNC" -> List.of(
                    step("requirements", "确认同步目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("sync", "同步角色卡", "Delivery Agent", "DELIVERY", "character.sync_st", List.of("requirements"))
            );
            case "WEB_SEARCH" -> List.of(
                    step("requirements", "确认搜索需求", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("search", "执行网页搜索", "Researcher", "RESEARCH", "web.search", List.of("requirements")),
                    step("report", "整理搜索结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("search"))
            );
            case "BROWSER_OPEN" -> List.of(
                    step("requirements", "确认网页读取需求", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("open", "读取网页内容", "Researcher", "RESEARCH", "browser.open", List.of("requirements")),
                    step("report", "整理页面结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("open"))
            );
            case "API_REQUEST" -> List.of(
                    step("requirements", "确认接口查询需求", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("request", "执行白名单接口请求", "Integration Agent", "RESEARCH", "api.request", List.of("requirements")),
                    step("report", "整理接口结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("request"))
            );
            case "MCP_CALL" -> List.of(
                    step("requirements", "确认 MCP 调用需求", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("call", "执行受控 MCP 调用", "Integration Agent", "EXTERNAL", "mcp.call", List.of("requirements")),
                    step("report", "整理 MCP 结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("call"))
            );
            case "CODE_RUN" -> List.of(
                    step("requirements", "确认代码执行需求", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("run", "执行隔离代码", "Code Runner", "EXECUTION", "code.run", List.of("requirements")),
                    step("report", "整理代码结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("run"))
            );
            case "USER_PROFILE" -> userQueryTemplate("user.profile.query", "读取用户资料", "Operations Agent");
            case "USER_ACCOUNT" -> userQueryTemplate("user.account.query", "读取用户账户", "Operations Agent");
            case "USER_ACCOUNT_CREDIT" -> List.of(
                    step("requirements", "确认钱包补发需求", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("credit", "审批后补发钱包余额", "Finance Agent", "OPERATIONS", "user.account.credit", List.of("requirements")),
                    step("report", "整理补发结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("credit"))
            );
            case "USER_TICKET" -> userQueryTemplate("user.ticket.query", "读取用户工单", "Support Agent");
            case "USER_TICKET_REPLY" -> List.of(
                    step("requirements", "确认回复内容", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("reply", "回复用户工单", "Support Agent", "OPERATIONS", "user.ticket.reply", List.of("requirements")),
                    step("report", "整理回复结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("reply"))
            );
            case "USER_RISK" -> userQueryTemplate("user.risk.query", "读取用户风险", "Risk Agent");
            case "CONTENT_MODERATION" -> userQueryTemplate("content.moderation.review", "复核角色内容", "Moderator");
            case "CONTENT_MODERATION_ACTION" -> List.of(
                    step("requirements", "确认审核决定", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("moderate", "审批后更新角色审核状态", "Moderator", "MODERATION", "content.moderation.action", List.of("requirements")),
                    step("report", "整理审核结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("moderate"))
            );
            case "COLLABORATION" -> List.of(
                    step("requirements", "确认协作目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("handoff", "执行角色交接", "Coordinator", "COLLABORATION", "agent.handoff", List.of("requirements")),
                    step("report", "整理协作结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("handoff"))
            );
            default -> List.of(
                    step("requirements", "分析任务目标", "Planner", "PLANNING", "planner.analyze", List.of()),
                    step("delivery", "整理任务结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("requirements"))
            );
        };
    }

    private static StepDefinition step(String key, String name, String role, String type, String tool, List<String> dependsOn) {
        return new StepDefinition(key, name, role, type, tool, dependsOn, null);
    }

    private static StepDefinition stepWithInput(String key, String name, String role, String type, String tool,
                                                List<String> dependsOn, String inputJson) {
        return new StepDefinition(key, name, role, type, tool, dependsOn, inputJson);
    }

    private static List<StepDefinition> userQueryTemplate(String tool, String name, String role) {
        return List.of(
                step("requirements", "确认查询范围", "Planner", "PLANNING", "planner.analyze", List.of()),
                step("query", name, role, "QUERY", tool, List.of("requirements")),
                step("report", "整理查询结果", "Delivery Agent", "DELIVERY", "artifact.render_report", List.of("query"))
        );
    }

    private record StepDefinition(
            String key,
            String name,
            String agentRole,
            String stepType,
            String toolName,
            List<String> dependsOn,
            String inputJson
    ) {}
}
