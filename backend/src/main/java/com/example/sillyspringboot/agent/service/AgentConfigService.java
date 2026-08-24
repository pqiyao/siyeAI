package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Versioned, approval-friendly lifecycle for Agent role and workflow configuration. */
@Service
public class AgentConfigService {

    private static final Pattern CONFIG_KEY = Pattern.compile("[a-z0-9][a-z0-9._-]{1,79}");
    private static final Set<String> RISK_LEVELS = Set.of(
            "READ_ONLY", "WRITE_REVERSIBLE", "EXTERNAL_SIDE_EFFECT", "DESTRUCTIVE", "PRODUCTION_OPERATION"
    );
    private static final Set<String> FAILURE_POLICIES = Set.of("STOP", "CONTINUE_DEGRADED", "RETRY");

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;

    public AgentConfigService(AgentAdminMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createRole(Map<String, Object> body, Long operatorId) {
        Map<String, Object> source = body == null ? Map.of() : body;
        String roleKey = configKey(source.get("roleKey"), "roleKey");
        String displayName = text(source.get("displayName"), 255, "displayName");
        String inputSchema = jsonObject(source.get("inputSchema"), "inputSchema");
        String outputSchema = jsonObject(source.get("outputSchema"), "outputSchema");
        String allowedTools = jsonStringArray(source.get("allowedToolsJson"), "allowedToolsJson", false);
        String systemPrompt = text(source.get("systemPrompt"), 20_000, "systemPrompt");
        String riskLevel = enumValue(source.get("riskLevel"), RISK_LEVELS, "riskLevel");
        int maxLoops = boundedInt(source.get("maxLoops"), 1, 100, "maxLoops");
        long maxTokens = boundedLong(source.get("maxTokens"), 1, 1_000_000, "maxTokens");
        String routeKey = optionalText(source.get("routeKey"), 120);
        int version = mapper.nextAgentRoleVersion(roleKey);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleKey", roleKey);
        row.put("version", version);
        row.put("displayName", displayName);
        row.put("description", optionalText(source.get("description"), 2_000));
        row.put("systemPrompt", systemPrompt);
        row.put("inputSchema", inputSchema);
        row.put("outputSchema", outputSchema);
        row.put("allowedToolsJson", allowedTools);
        row.put("routeKey", routeKey);
        row.put("maxTokens", maxTokens);
        row.put("maxLoops", maxLoops);
        row.put("riskLevel", riskLevel);
        row.put("enabled", false);
        row.put("createdBy", operatorId);
        row.put("updatedBy", operatorId);
        if (mapper.insertAgentRoleDefinition(row) != 1) {
            throw new IllegalStateException("Agent 角色版本创建失败");
        }
        audit("ROLE", row, "CREATE", operatorId, "Created Agent role version");
        return mapper.findAgentRole(number(row.get("id"), "角色"));
    }

    @Transactional
    public Map<String, Object> createWorkflow(Map<String, Object> body, Long operatorId) {
        Map<String, Object> source = body == null ? Map.of() : body;
        String workflowKey = configKey(source.get("workflowKey"), "workflowKey");
        String displayName = text(source.get("displayName"), 255, "displayName");
        String taskType = enumValue(source.get("taskType"), Set.of("CONTENT_CREATION", "GENERAL", "OPERATIONS_REVIEW"), "taskType");
        String steps = jsonArray(source.get("stepsJson"), "stepsJson");
        String allowedRoles = jsonStringArray(source.get("allowedRolesJson"), "allowedRolesJson", false);
        String allowedTools = jsonStringArray(source.get("allowedToolsJson"), "allowedToolsJson", false);
        int maxSteps = boundedInt(source.get("maxSteps"), 1, 32, "maxSteps");
        int maxRuntime = boundedInt(source.get("maxRuntimeSeconds"), 1, 86_400, "maxRuntimeSeconds");
        long maxTokens = boundedLong(source.get("maxTokens"), 1, 10_000_000, "maxTokens");
        BigDecimal maxCost = boundedCost(source.get("maxCost"));
        String failurePolicy = enumValue(source.get("failurePolicy"), FAILURE_POLICIES, "failurePolicy");
        boolean approvalRequired = booleanValue(source.get("approvalRequired"));
        validateWorkflowSteps(steps, allowedRoles, allowedTools, maxSteps);
        int version = mapper.nextWorkflowTemplateVersion(workflowKey);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("workflowKey", workflowKey);
        row.put("version", version);
        row.put("displayName", displayName);
        row.put("description", optionalText(source.get("description"), 2_000));
        row.put("taskType", taskType);
        row.put("stepsJson", steps);
        row.put("allowedRolesJson", allowedRoles);
        row.put("allowedToolsJson", allowedTools);
        row.put("maxSteps", maxSteps);
        row.put("maxRuntimeSeconds", maxRuntime);
        row.put("maxTokens", maxTokens);
        row.put("maxCost", maxCost);
        row.put("failurePolicy", failurePolicy);
        row.put("approvalRequired", approvalRequired);
        row.put("enabled", false);
        row.put("createdBy", operatorId);
        row.put("updatedBy", operatorId);
        if (mapper.insertWorkflowTemplate(row) != 1) {
            throw new IllegalStateException("Agent 工作流版本创建失败");
        }
        audit("WORKFLOW", row, "CREATE", operatorId, "Created Agent workflow version");
        return mapper.findWorkflowTemplate(number(row.get("id"), "工作流"));
    }

    @Transactional
    public Map<String, Object> activateRole(long id, Long operatorId) {
        Map<String, Object> row = mapper.findAgentRoleForMutation(id);
        if (row == null) {
            throw new IllegalArgumentException("Agent 角色不存在");
        }
        validateRoleForActivation(row);
        String key = String.valueOf(row.get("roleKey"));
        mapper.disableAgentRoleVersions(key);
        if (mapper.enableAgentRoleVersion(id) != 1) {
            throw new IllegalStateException("Agent 角色版本启用失败");
        }
        audit("ROLE", row, "ACTIVATE", operatorId, "Activated Agent role version");
        return mapper.findAgentRole(id);
    }

    @Transactional
    public Map<String, Object> activateWorkflow(long id, Long operatorId) {
        Map<String, Object> row = mapper.findWorkflowTemplateForMutation(id);
        if (row == null) {
            throw new IllegalArgumentException("Agent 工作流不存在");
        }
        String steps = text(row.get("stepsJson"), 1_000_000, "stepsJson");
        String allowedRoles = text(row.get("allowedRolesJson"), 100_000, "allowedRolesJson");
        String allowedTools = text(row.get("allowedToolsJson"), 100_000, "allowedToolsJson");
        validateWorkflowSteps(steps, allowedRoles, allowedTools,
                boundedInt(row.get("maxSteps"), 1, 32, "maxSteps"));
        String key = String.valueOf(row.get("workflowKey"));
        mapper.disableWorkflowTemplateVersions(key);
        if (mapper.enableWorkflowTemplateVersion(id) != 1) {
            throw new IllegalStateException("Agent 工作流版本启用失败");
        }
        audit("WORKFLOW", row, "ACTIVATE", operatorId, "Activated Agent workflow version");
        return mapper.findWorkflowTemplate(id);
    }

    private void validateRoleForActivation(Map<String, Object> row) {
        text(row.get("roleKey"), 80, "roleKey");
        text(row.get("displayName"), 255, "displayName");
        text(row.get("systemPrompt"), 20_000, "systemPrompt");
        jsonObject(row.get("inputSchema"), "inputSchema");
        jsonObject(row.get("outputSchema"), "outputSchema");
        jsonStringArray(row.get("allowedToolsJson"), "allowedToolsJson", false);
        enumValue(row.get("riskLevel"), RISK_LEVELS, "riskLevel");
        boundedInt(row.get("maxLoops"), 1, 100, "maxLoops");
        boundedLong(row.get("maxTokens"), 1, 1_000_000, "maxTokens");
    }

    private void audit(String type, Map<String, Object> row, String action, Long operatorId, String summary) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("configType", type);
        audit.put("configId", row.get("id"));
        audit.put("configKey", type.equals("ROLE") ? row.get("roleKey") : row.get("workflowKey"));
        audit.put("version", row.get("version"));
        audit.put("action", action);
        audit.put("operatorId", operatorId);
        audit.put("summary", summary);
        if (mapper.insertConfigAudit(audit) != 1) {
            throw new IllegalStateException("Agent 配置审计写入失败");
        }
    }

    private void validateWorkflowSteps(String stepsJson, String allowedRolesJson, String allowedToolsJson, int maxSteps) {
        try {
            JsonNode steps = objectMapper.readTree(stepsJson);
            Set<String> roles = stringSet(objectMapper.readTree(allowedRolesJson), "allowedRolesJson");
            Set<String> tools = stringSet(objectMapper.readTree(allowedToolsJson), "allowedToolsJson");
            if (steps == null || !steps.isArray() || steps.isEmpty() || steps.size() > maxSteps) {
                throw new IllegalArgumentException("工作流 stepsJson 数量无效");
            }
            Set<String> keys = new HashSet<>();
            Map<String, Set<String>> dependenciesByKey = new LinkedHashMap<>();
            for (JsonNode step : steps) {
                if (!step.isObject() || !step.path("key").isTextual()) {
                    throw new IllegalArgumentException("工作流步骤 key 无效或重复");
                }
                String key = step.path("key").asText().trim();
                if (key.isBlank() || key.length() > 100 || !keys.add(key)) {
                    throw new IllegalArgumentException("工作流步骤 key 无效或重复");
                }
                String role = step.path("agentRole").asText("");
                String tool = step.path("toolName").asText("");
                if (!roles.contains(role) || !tools.contains(tool)) {
                    throw new IllegalArgumentException("工作流步骤超出角色或工具白名单");
                }
                JsonNode dependencies = step.get("dependsOn");
                if (dependencies != null && !dependencies.isNull() && !dependencies.isArray()) {
                    throw new IllegalArgumentException("工作流步骤 dependsOn 必须是数组");
                }
                Set<String> dependencyKeys = new HashSet<>();
                if (dependencies != null && dependencies.isArray()) {
                    for (JsonNode dependency : dependencies) {
                        if (!dependency.isTextual() || dependency.asText().isBlank()) {
                            throw new IllegalArgumentException("工作流步骤依赖无效");
                        }
                        String dependencyKey = dependency.asText().trim();
                        if (!dependencyKeys.add(dependencyKey)) {
                            throw new IllegalArgumentException("工作流步骤依赖重复: " + key);
                        }
                    }
                }
                dependenciesByKey.put(key, dependencyKeys);
            }
            for (Map.Entry<String, Set<String>> entry : dependenciesByKey.entrySet()) {
                for (String dependency : entry.getValue()) {
                    if (!keys.contains(dependency) || entry.getKey().equals(dependency)) {
                        throw new IllegalArgumentException("工作流步骤依赖无效: " + entry.getKey() + " -> " + dependency);
                    }
                }
            }
            for (String key : keys) {
                if (hasDependencyCycle(key, dependenciesByKey, new HashSet<>(), new HashSet<>())) {
                    throw new IllegalArgumentException("工作流存在循环依赖");
                }
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("工作流 JSON 无效", ex);
        }
    }

    private static boolean hasDependencyCycle(String key, Map<String, Set<String>> dependenciesByKey,
                                              Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) {
            return false;
        }
        if (!visiting.add(key)) {
            return true;
        }
        for (String dependency : dependenciesByKey.getOrDefault(key, Set.of())) {
            if (hasDependencyCycle(dependency, dependenciesByKey, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(key);
        visited.add(key);
        return false;
    }

    private Set<String> stringSet(JsonNode node, String label) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException(label + "必须是非空数组");
        }
        Set<String> values = new HashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalArgumentException(label + "包含无效项");
            }
            values.add(value.asText().trim());
        }
        return values;
    }

    private String jsonObject(Object value, String label) { return jsonNode(value, label, true); }
    private String jsonArray(Object value, String label) { return jsonNode(value, label, false); }

    private String jsonNode(Object value, String label, boolean object) {
        try {
            JsonNode node = objectMapper.readTree(text(value, 1_000_000, label));
            if (node == null || (object ? !node.isObject() : !node.isArray())) {
                throw new IllegalArgumentException(label + " JSON 类型无效");
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(label + " JSON 无效", ex);
        }
    }

    private String jsonStringArray(Object value, String label, boolean allowEmpty) {
        try {
            Set<String> values = stringSet(objectMapper.readTree(text(value, 100_000, label)), label);
            if (!allowEmpty && values.isEmpty()) {
                throw new IllegalArgumentException(label + "不能为空");
            }
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(label + " JSON 无效", ex);
        }
    }

    private static String configKey(Object value, String label) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!CONFIG_KEY.matcher(text).matches()) throw new IllegalArgumentException(label + "格式无效");
        return text;
    }
    private static String text(Object value, int max, String label) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank() || text.length() > max) throw new IllegalArgumentException(label + "不能为空或过长");
        return text;
    }
    private static String optionalText(Object value, int max) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return text(value, max, "文本");
    }
    private static String enumValue(Object value, Set<String> allowed, String label) {
        String normalized = text(value, 80, label).toUpperCase();
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(label + "值不支持");
        return normalized;
    }
    private static int boundedInt(Object value, int min, int max, String label) {
        long number = value instanceof Number n ? n.longValue() : Long.parseLong(text(value, 20, label));
        if (number < min || number > max) throw new IllegalArgumentException(label + "超出范围");
        return (int) number;
    }
    private static long boundedLong(Object value, long min, long max, String label) {
        long number = value instanceof Number n ? n.longValue() : Long.parseLong(text(value, 30, label));
        if (number < min || number > max) throw new IllegalArgumentException(label + "超出范围");
        return number;
    }
    private static BigDecimal boundedCost(Object value) {
        BigDecimal cost;
        try { cost = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value == null ? "0" : value)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("maxCost 格式无效", ex); }
        if (cost.signum() < 0 || cost.compareTo(new BigDecimal("100000")) > 0) throw new IllegalArgumentException("maxCost 超出范围");
        return cost;
    }
    private static boolean booleanValue(Object value) { return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value)); }
    private static long number(Object value, String label) {
        if (!(value instanceof Number n) || n.longValue() <= 0) throw new IllegalStateException(label + "编号生成失败");
        return n.longValue();
    }
}
