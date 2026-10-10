package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.security.AgentServiceCredentialProvider;
import com.example.sillyspringboot.admin.security.RuoYiAdminAccessService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.agent.service.AgentAdminQueryService;
import com.example.sillyspringboot.agent.service.AgentTenantService;
import com.example.sillyspringboot.agent.service.PlatformExtendedQueryService;
import com.example.sillyspringboot.agent.tool.AgentPlatformActionTool;
import com.example.sillyspringboot.agent.tool.AgentPlatformQueryTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Local MCP bridge endpoints. The stdio MCP process calls these endpoints. */
@RestController
@RequestMapping("/admin/jiugai/xiaoye/mcp")
@AdminPermitted("agent:center:view")
public class AdminXiaoyeMcpController {
    private final AgentPlatformQueryTool queryTool;
    private final AgentPlatformActionTool actionTool;
    private final AgentAdminQueryService adminQuery;
    private final PlatformExtendedQueryService permissions;
    private final AgentTenantService tenantService;
    private final ObjectMapper objectMapper;
    private final AgentServiceCredentialProvider serviceCredentials;
    private final RuoYiAdminAccessService adminAccessService;

    public AdminXiaoyeMcpController(AgentPlatformQueryTool queryTool,
                                    AgentPlatformActionTool actionTool,
                                    AgentAdminQueryService adminQuery,
                                    PlatformExtendedQueryService permissions,
                                    AgentTenantService tenantService,
                                    ObjectMapper objectMapper,
                                    AgentServiceCredentialProvider serviceCredentials,
                                    RuoYiAdminAccessService adminAccessService) {
        this.queryTool = queryTool;
        this.actionTool = actionTool;
        this.adminQuery = adminQuery;
        this.permissions = permissions;
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
        this.serviceCredentials = serviceCredentials;
        this.adminAccessService = adminAccessService;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return AdminAjaxResult.okData(AgentPlatformQueryTool.capabilityCatalog());
    }

    /** Browser-side verification of the same service-account channel used by Xiaoye MCP calls. */
    @GetMapping("/authorization")
    public Map<String, Object> authorization() {
        AgentServiceCredentialProvider.Credentials credentials = serviceCredentials.resolve();
        RuoYiAdminAccessService.AdminSession session = credentials.configured()
                ? adminAccessService.loadSession(credentials.username()) : null;
        boolean fullAccess = session != null && session.getPermissions().contains("*:*:*");
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("serviceCredentialConfigured", credentials.configured());
        status.put("serviceAccountReady", session != null);
        status.put("fullPlatformAccess", fullAccess);
        status.put("serviceAccount", session == null ? credentials.username() : session.getUsername());
        status.put("credentialSource", credentials.source());
        status.put("queryCatalog", AgentPlatformQueryTool.capabilityCatalog());
        status.put("actionCount", AgentPlatformActionTool.actionCatalog().size());
        if (!credentials.configured()) status.put("reason", "小叶服务令牌未配置");
        else if (session == null) status.put("reason", "小叶服务账号不存在或已停用");
        else if (!fullAccess) status.put("reason", "小叶服务账号不是超级管理员");
        else status.put("reason", "");
        return AdminAjaxResult.okData(status);
    }

    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest request) {
        try {
            Map<String, Object> input = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
            String operation = text(input.remove("operation"));
            Object rawInput = input.remove("input");
            Map<String, Object> queryInput = rawInput instanceof Map<?, ?> map ? copyMap(map) : input;
            Map<String, Object> task = taskContext(request, queryInput);
            Map<String, Object> result = switch (operation.isBlank() ? "search" : operation.toLowerCase()) {
                case "search" -> queryTool.search(step(queryInput), task);
                case "get", "detail" -> queryTool.get(step(queryInput), task);
                case "metrics", "metric" -> queryTool.metrics(step(queryInput), task);
                case "snapshot" -> queryTool.snapshot(step(queryInput), task);
                default -> throw new IllegalArgumentException("平台查询 operation 必须是 search、get、metrics 或 snapshot");
            };
            return AdminAjaxResult.okData(result);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return AdminAjaxResult.error(ex.getMessage());
        }
    }

    @PostMapping("/action")
    public Map<String, Object> action(@RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest request) {
        try {
            Map<String, Object> input = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
            Object rawParameters = input.remove("parameters");
            if (rawParameters instanceof Map<?, ?> map) {
                copyMap(map).forEach(input::putIfAbsent);
            }
            Map<String, Object> task = taskContext(request, input);
            return AdminAjaxResult.okData(actionTool.request(step(input), task));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return AdminAjaxResult.error(ex.getMessage());
        }
    }

    @PostMapping("/action-status")
    public Map<String, Object> actionStatus(@RequestBody(required = false) Map<String, Object> body,
                                            HttpServletRequest request) {
        try {
            long taskId = number(body == null ? null : body.get("taskId"));
            if (taskId <= 0) throw new IllegalArgumentException("taskId 必须是正整数");
            Map<String, Object> context = taskContext(request, body == null ? Map.of() : body);
            AgentAdminQueryService.TaskDetail detail = adminQuery.findTask(taskId);
            if (detail == null) throw new IllegalArgumentException("Agent 任务不存在");
            permissions.requirePermission(context, "agent:task:view");
            enforceTenant(context, detail.task());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("task", detail.task());
            result.put("plan", detail.plan());
            result.put("steps", detail.steps());
            result.put("toolCalls", detail.toolCalls());
            result.put("externalOperations", detail.externalOperations());
            result.put("artifacts", detail.artifacts());
            result.put("evaluation", detail.evaluation());
            return AdminAjaxResult.okData(result);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return AdminAjaxResult.error(ex.getMessage());
        }
    }

    private Map<String, Object> taskContext(HttpServletRequest request, Map<String, Object> input) {
        Object value = request == null ? null : request.getAttribute("adminSession");
        if (!(value instanceof RuoYiAdminAccessService.AdminSession session) || session.getId() == null) {
            throw new IllegalArgumentException("管理员身份无效");
        }
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("createdBy", session.getId());
        String tenantKey = text(input == null ? null : input.get("tenantKey"));
        boolean platformScope = isPlatformScope(request);
        if (tenantKey.isBlank()) {
            if (!platformScope) throw new IllegalArgumentException("普通管理员必须提供已授权 tenantKey");
            tenantKey = "platform";
        }
        if (!platformScope && (tenantService == null
                || !tenantService.hasAdminTenantAccess(session.getId(), tenantKey, "VIEWER"))) {
            throw new IllegalArgumentException("管理员未授权访问该租户");
        }
        if (!"platform".equalsIgnoreCase(tenantKey)
                && !tenantKey.matches("[a-z0-9][a-z0-9:_-]{0,119}")) {
            throw new IllegalArgumentException("tenantKey 格式无效");
        }
        task.put("tenantKey", tenantKey);
        return task;
    }

    private static boolean isPlatformScope(HttpServletRequest request) {
        Object permissions = request == null ? null : request.getAttribute("adminPermissions");
        return permissions instanceof java.util.Collection<?> values
                && values.stream().anyMatch(value -> "*:*:*".equals(String.valueOf(value)));
    }

    private Map<String, Object> step(Map<String, Object> input) {
        Map<String, Object> step = new LinkedHashMap<>();
        try {
            step.put("inputJson", objectMapper.writeValueAsString(input == null ? Map.of() : input));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("MCP 参数无法序列化", ex);
        }
        return step;
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static void enforceTenant(Map<String, Object> context, Map<String, Object> target) {
        String current = text(context.get("tenantKey"));
        String targetTenant = text(target == null ? null : target.get("tenantKey"));
        if (!current.isBlank() && !"platform".equalsIgnoreCase(current)
                && !current.equalsIgnoreCase(targetTenant)) {
            throw new IllegalArgumentException("当前管理员无权访问该租户任务");
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? 0 : Long.parseLong(String.valueOf(value).trim()); }
        catch (NumberFormatException ex) { return 0; }
    }
}
