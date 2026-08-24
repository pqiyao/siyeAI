package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.security.RuoYiAdminAccessService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.agent.service.AgentTaskService;
import com.example.sillyspringboot.agent.service.AgentAdminQueryService;
import com.example.sillyspringboot.agent.service.AgentPlanService;
import com.example.sillyspringboot.agent.service.AgentArtifactStorageService;
import com.example.sillyspringboot.agent.service.AgentConfigService;
import com.example.sillyspringboot.agent.service.AgentTenantService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/admin/jiugai/agent")
@AdminPermitted("agent:center:view")
public class AdminAgentController {

    private static final Logger log = LoggerFactory.getLogger(AdminAgentController.class);

    private static final ScheduledExecutorService EVENT_STREAM_EXECUTOR = Executors.newScheduledThreadPool(
            2,
            runnable -> {
                Thread thread = new Thread(runnable, "agent-event-stream");
                thread.setDaemon(true);
                return thread;
            }
    );

    private final AgentAdminQueryService queryService;
    private final AgentTaskService taskService;
    private final AgentPlanService planService;
    private final AgentArtifactStorageService artifactStorageService;
    private final AgentConfigService configService;
    private final AgentTenantService tenantService;

    public AdminAgentController(
            AgentAdminQueryService queryService,
            AgentTaskService taskService,
            AgentPlanService planService,
            AgentArtifactStorageService artifactStorageService,
            AgentConfigService configService,
            AgentTenantService tenantService
    ) {
        this.queryService = queryService;
        this.taskService = taskService;
        this.planService = planService;
        this.artifactStorageService = artifactStorageService;
        this.configService = configService;
        this.tenantService = tenantService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(HttpServletRequest request) {
        try { requirePlatformScope(request); return AdminAjaxResult.okData(queryService.overview()); }
        catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/runtime")
    @AdminPermitted("agent:runtime:view")
    public Map<String, Object> runtime(HttpServletRequest request) {
        try { requirePlatformScope(request); return AdminAjaxResult.okData(queryService.runtime()); }
        catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/release-readiness")
    @AdminPermitted("agent:runtime:view")
    public Map<String, Object> releaseReadiness(HttpServletRequest request) {
        try { requirePlatformScope(request); return AdminAjaxResult.okData(queryService.releaseReadiness()); }
        catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/tasks")
    @AdminPermitted("agent:task:create")
    public Map<String, Object> createTask(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        try {
            String tenantKey = body == null || body.get("tenantKey") == null
                    ? null : String.valueOf(body.get("tenantKey"));
            requireTenantScope(request, tenantKey, "ADMIN");
            return AdminAjaxResult.okData(taskService.createAdminTask(body, adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tasks")
    @AdminPermitted("agent:task:view")
    public Map<String, Object> tasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String tenantKey,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            AgentAdminQueryService.PageResult result = queryService.listTasks(pageNum, pageSize, status, riskLevel, tenantKey, keyword);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tasks/{id}")
    @AdminPermitted("agent:task:view")
    public Map<String, Object> task(@PathVariable long id, HttpServletRequest request) {
        try {
            AgentAdminQueryService.TaskDetail detail = queryService.findTask(id);
            if (detail == null) {
                return AdminAjaxResult.error("Agent 任务不存在");
            }
            requireTenantScope(request, String.valueOf(detail.task().get("tenantKey")), "VIEWER");
            Map<String, Object> result = AdminAjaxResult.ok();
            result.put("data", detail.task());
            result.put("plan", detail.plan());
            result.put("steps", detail.steps());
            result.put("toolCalls", detail.toolCalls());
            result.put("artifacts", detail.artifacts());
            result.put("evaluation", detail.evaluation());
            return result;
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/plan")
    @AdminPermitted("agent:task:control")
    public Map<String, Object> generatePlan(@PathVariable long id, HttpServletRequest request) {
        try {
            requireTaskScope(request, id, "ADMIN");
            return AdminAjaxResult.okData(planService.generate(id, adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/plan/approve")
    @AdminPermitted("agent:approval:approve")
    public Map<String, Object> approvePlan(@PathVariable long id, HttpServletRequest request) {
        try {
            requireTaskScope(request, id, "ADMIN");
            return AdminAjaxResult.okData(planService.approve(id, adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tasks/{id}/events")
    @AdminPermitted("agent:task:view")
    public Map<String, Object> events(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") long afterSeq,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request
    ) {
        try {
            requireTaskScope(request, id, "VIEWER");
            return AdminAjaxResult.okData(queryService.listEvents(id, afterSeq, limit));
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping(value = "/tasks/{id}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AdminPermitted("agent:task:view")
    public SseEmitter eventStream(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") long afterSeq,
            HttpServletRequest request
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException("任务编号无效");
        }
        requireTaskScope(request, id, "VIEWER");
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicLong cursor = new AtomicLong(Math.max(0L, afterSeq));
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        Runnable close = () -> {
            if (closed.compareAndSet(false, true)) {
                ScheduledFuture<?> future = futureRef.get();
                if (future != null) {
                    future.cancel(false);
                }
            }
        };
        emitter.onCompletion(close);
        emitter.onTimeout(() -> {
            close.run();
            emitter.complete();
        });
        emitter.onError(error -> close.run());

        ScheduledFuture<?> future = EVENT_STREAM_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (closed.get()) {
                return;
            }
            try {
                List<Map<String, Object>> rows = queryService.listEvents(id, cursor.get(), 100);
                if (rows.isEmpty()) {
                    emitter.send(SseEmitter.event().name("ping").data(Map.of("taskId", id)));
                    return;
                }
                for (Map<String, Object> row : rows) {
                    long sequence = number(row.get("eventSeq"));
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(sequence))
                            .name("agent-event")
                            .data(row));
                    cursor.accumulateAndGet(sequence, Math::max);
                }
            } catch (IOException | RuntimeException error) {
                close.run();
                emitter.complete();
            }
        }, 0L, 1L, TimeUnit.SECONDS);
        futureRef.set(future);
        return emitter;
    }

    @GetMapping("/tasks/{id}/artifacts")
    @AdminPermitted("agent:artifact:view")
    public Map<String, Object> artifacts(@PathVariable long id, HttpServletRequest request) {
        try {
            requireTaskScope(request, id, "VIEWER");
            return AdminAjaxResult.okData(queryService.listArtifacts(id));
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}/artifacts/{artifactKey}/download")
    @AdminPermitted("agent:artifact:view")
    public ResponseEntity<Resource> downloadArtifact(
            @PathVariable long taskId,
            @PathVariable String artifactKey,
            HttpServletRequest request
    ) {
        try {
            requireTaskScope(request, taskId, "VIEWER");
            Map<String, Object> artifact = queryService.findArtifact(taskId, artifactKey);
            if (artifact == null || artifact.get("uri") == null) {
                return ResponseEntity.notFound().build();
            }
            Path path = artifactStorageService.resolveDownload(taskId, String.valueOf(artifact.get("uri")));
            long size = Files.size(path);
            String name = String.valueOf(artifact.getOrDefault("name", "agent-artifact.bin"));
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(name, StandardCharsets.UTF_8)
                    .build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(disposition);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(size);
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));
        } catch (IllegalArgumentException | java.io.IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/tasks/{id}/pause")
    @AdminPermitted("agent:task:control")
    public Map<String, Object> pause(@PathVariable long id, HttpServletRequest request) {
        return control(id, "PAUSE", request);
    }

    @PostMapping("/tasks/{id}/resume")
    @AdminPermitted("agent:task:control")
    public Map<String, Object> resume(@PathVariable long id, HttpServletRequest request) {
        return control(id, "RESUME", request);
    }

    @PostMapping("/tasks/{id}/cancel")
    @AdminPermitted("agent:task:control")
    public Map<String, Object> cancel(@PathVariable long id, HttpServletRequest request) {
        return control(id, "CANCEL", request);
    }

    @PostMapping("/tasks/{id}/retry")
    @AdminPermitted("agent:task:retry")
    public Map<String, Object> retry(@PathVariable long id, HttpServletRequest request) {
        return control(id, "RETRY", request);
    }

    @PostMapping("/tasks/{id}/replan")
    @AdminPermitted("agent:task:retry")
    public Map<String, Object> replan(@PathVariable long id, HttpServletRequest request) {
        return control(id, "REPLAN", request);
    }

    @GetMapping("/approvals")
    @AdminPermitted("agent:approval:view")
    public Map<String, Object> approvals(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tenantKey,
            HttpServletRequest request
    ) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            AgentAdminQueryService.PageResult result = queryService.listApprovals(pageNum, pageSize, status, keyword, tenantKey);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/approvals/{id}/approve")
    @AdminPermitted("agent:approval:approve")
    public Map<String, Object> approveApproval(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        try {
            requireApprovalScope(request, id, "ADMIN");
            return AdminAjaxResult.okData(planService.approveApproval(id, adminId(request), decisionComment(body)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/approvals/{id}/reject")
    @AdminPermitted("agent:approval:approve")
    public Map<String, Object> rejectApproval(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        try {
            requireApprovalScope(request, id, "ADMIN");
            return AdminAjaxResult.okData(planService.rejectApproval(id, adminId(request), decisionComment(body)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tools")
    @AdminPermitted("agent:tool:view")
    public Map<String, Object> tools(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            requirePlatformScope(request);
            AgentAdminQueryService.PageResult result = queryService.listTools(pageNum, pageSize, category, enabled, keyword);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/tools/{id}")
    @AdminPermitted("agent:tool:view")
    public Map<String, Object> tool(@PathVariable long id, HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            Map<String, Object> tool = queryService.findTool(id);
            return tool == null ? AdminAjaxResult.error("Agent 工具不存在") : AdminAjaxResult.okData(tool);
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/roles")
    @AdminPermitted("agent:role:view")
    public Map<String, Object> roles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            requirePlatformScope(request);
            AgentAdminQueryService.PageResult result = queryService.listAgentRoles(pageNum, pageSize, enabled, keyword);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/roles/{id}")
    @AdminPermitted("agent:role:view")
    public Map<String, Object> role(@PathVariable long id, HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            Map<String, Object> role = queryService.findAgentRole(id);
            return role == null ? AdminAjaxResult.error("Agent 角色不存在") : AdminAjaxResult.okData(role);
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/roles")
    @AdminPermitted("agent:role:edit")
    public Map<String, Object> createRole(@RequestBody(required = false) Map<String, Object> body,
                                           HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            return AdminAjaxResult.okData(configService.createRole(body, adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/roles/{id}/activate")
    @AdminPermitted("agent:role:edit")
    public Map<String, Object> activateRole(@PathVariable long id, HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            return AdminAjaxResult.okData(configService.activateRole(id, adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/workflows")
    @AdminPermitted("agent:workflow:view")
    public Map<String, Object> workflows(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            requirePlatformScope(request);
            AgentAdminQueryService.PageResult result = queryService.listWorkflowTemplates(
                    pageNum, pageSize, enabled, taskType, keyword);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/workflows/{id}")
    @AdminPermitted("agent:workflow:view")
    public Map<String, Object> workflow(@PathVariable long id, HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            Map<String, Object> workflow = queryService.findWorkflowTemplate(id);
            return workflow == null ? AdminAjaxResult.error("Agent 工作流不存在") : AdminAjaxResult.okData(workflow);
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/workflows")
    @AdminPermitted("agent:workflow:edit")
    public Map<String, Object> createWorkflow(@RequestBody(required = false) Map<String, Object> body,
                                               HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            return AdminAjaxResult.okData(configService.createWorkflow(body, adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/workflows/{id}/activate")
    @AdminPermitted("agent:workflow:edit")
    public Map<String, Object> activateWorkflow(@PathVariable long id, HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            return AdminAjaxResult.okData(configService.activateWorkflow(id, adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/config-audit")
    @AdminPermitted("agent:security:view")
    public Map<String, Object> configAudit(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String configType,
            @RequestParam(required = false) String configKey,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            requirePlatformScope(request);
            AgentAdminQueryService.PageResult result = queryService.listConfigAudits(
                    pageNum, pageSize, configType, configKey, action, operatorId, keyword);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tenants")
    @AdminPermitted("agent:security:view")
    public Map<String, Object> tenants(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        try {
            requirePlatformScope(request);
            AgentAdminQueryService.PageResult result = queryService.listTenants(pageNum, pageSize, status, keyword);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tenants/{tenantKey}")
    @AdminPermitted("agent:security:view")
    public Map<String, Object> tenant(@PathVariable String tenantKey, HttpServletRequest request) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            Map<String, Object> row = queryService.findTenant(tenantKey);
            return row == null ? AdminAjaxResult.error("Agent 租户不存在") : AdminAjaxResult.okData(row);
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/tenants")
    @AdminPermitted("agent:security:edit")
    public Map<String, Object> createTenant(@RequestBody(required = false) Map<String, Object> body,
                                             HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            return AdminAjaxResult.okData(tenantService.createTenant(
                    body == null ? null : body.get("tenantKey"),
                    body == null ? null : body.get("displayName"), adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/tenants/{tenantKey}/status")
    @AdminPermitted("agent:security:edit")
    public Map<String, Object> changeTenantStatus(@PathVariable String tenantKey,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            return AdminAjaxResult.okData(tenantService.changeTenantStatus(tenantKey,
                    body == null ? null : String.valueOf(body.get("status")), adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/usage-ledger")
    @AdminPermitted("agent:budget:view")
    public Map<String, Object> usageLedger(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String tenantKey,
            @RequestParam(required = false) Long taskId,
            HttpServletRequest request
    ) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            AgentAdminQueryService.PageResult result = queryService.listUsageLedger(pageNum, pageSize, tenantKey, taskId);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tenants/{tenantKey}/usage-summary")
    @AdminPermitted("agent:budget:view")
    public Map<String, Object> tenantUsageSummary(@PathVariable String tenantKey, HttpServletRequest request) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            return AdminAjaxResult.okData(queryService.tenantUsageSummary(tenantKey));
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/tenants/{tenantKey}/budget")
    @AdminPermitted("agent:budget:view")
    public Map<String, Object> tenantBudget(@PathVariable String tenantKey, HttpServletRequest request) {
        try { requireTenantScope(request, tenantKey, "VIEWER"); return AdminAjaxResult.okData(tenantService.budget(tenantKey)); }
        catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/tenants/{tenantKey}/budget")
    @AdminPermitted("agent:budget:edit")
    public Map<String, Object> updateTenantBudget(@PathVariable String tenantKey,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   HttpServletRequest request) {
        try { requireTenantScope(request, tenantKey, "ADMIN"); return AdminAjaxResult.okData(tenantService.updateBudget(tenantKey,
                body == null ? null : body.get("maxDailyTokens"), body == null ? null : body.get("maxDailyCost"),
                body == null ? null : body.get("maxDailyTasks"), adminId(request))); }
        catch (IllegalArgumentException | IllegalStateException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/tenants/{tenantKey}/members")
    @AdminPermitted("agent:security:view")
    public Map<String, Object> tenantMembers(
            @PathVariable String tenantKey,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            AgentAdminQueryService.PageResult result = queryService.listTenantMembers(pageNum, pageSize, tenantKey, status);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/tenants/{tenantKey}/members")
    @AdminPermitted("agent:security:edit")
    public Map<String, Object> addTenantMember(@PathVariable String tenantKey,
                                                @RequestBody(required = false) Map<String, Object> body,
                                                HttpServletRequest request) {
        try {
            requireTenantScope(request, tenantKey, "ADMIN");
            return AdminAjaxResult.okData(tenantService.addMember(tenantKey,
                    body == null ? null : body.get("userId"),
                    body == null ? null : body.get("memberRole"), adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/tenants/{tenantKey}/members/{userId}/status")
    @AdminPermitted("agent:security:edit")
    public Map<String, Object> changeTenantMemberStatus(@PathVariable String tenantKey,
                                                         @PathVariable long userId,
                                                         @RequestBody(required = false) Map<String, Object> body,
                                                         HttpServletRequest request) {
        try {
            requireTenantScope(request, tenantKey, "ADMIN");
            return AdminAjaxResult.okData(tenantService.changeMemberStatus(tenantKey, userId,
                    body == null ? null : String.valueOf(body.get("status")), adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/admin-scopes/me")
    @AdminPermitted("agent:security:view")
    public Map<String, Object> myAdminScopes(HttpServletRequest request) {
        try {
            Long id = adminId(request);
            if (id == null) throw new IllegalArgumentException("管理员会话无效");
            return AdminAjaxResult.okData(tenantService.adminScopes(id));
        } catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/admin-scopes/{adminId:\\d+}")
    @AdminPermitted("agent:security:view")
    public Map<String, Object> adminScopes(@PathVariable long adminId, HttpServletRequest request) {
        try { requirePlatformScope(request); return AdminAjaxResult.okData(tenantService.adminScopes(adminId)); }
        catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/admin-scopes/{adminId:\\d+}")
    @AdminPermitted("agent:security:edit")
    public Map<String, Object> upsertAdminScope(@PathVariable long adminId,
                                                @RequestBody(required = false) Map<String, Object> body,
                                                HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            return AdminAjaxResult.okData(tenantService.upsertAdminScope(adminId,
                    body == null ? null : String.valueOf(body.get("tenantKey")),
                    body == null ? null : body.get("accessRole"), adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/admin-scopes/{adminId:\\d+}/{tenantKey}/status")
    @AdminPermitted("agent:security:edit")
    public Map<String, Object> changeAdminScopeStatus(@PathVariable long adminId,
                                                      @PathVariable String tenantKey,
                                                      @RequestBody(required = false) Map<String, Object> body,
                                                      HttpServletRequest request) {
        try {
            requirePlatformScope(request);
            return AdminAjaxResult.okData(tenantService.changeAdminScopeStatus(adminId, tenantKey,
                    body == null ? null : String.valueOf(body.get("status")), adminId(request)));
        } catch (IllegalArgumentException | IllegalStateException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/budget")
    @AdminPermitted("agent:budget:view")
    public Map<String, Object> budget(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean overBudget,
            @RequestParam(required = false) String tenantKey,
            HttpServletRequest request
    ) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            return AdminAjaxResult.okData(queryService.budget(pageNum, pageSize, taskType, status, overBudget, tenantKey));
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/evaluation")
    @AdminPermitted("agent:evaluation:view")
    public Map<String, Object> evaluation(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) Integer minimumScore,
            @RequestParam(required = false) Boolean gatePassed,
            @RequestParam(required = false) String tenantKey,
            HttpServletRequest request
    ) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            return AdminAjaxResult.okData(queryService.evaluation(pageNum, pageSize, taskType, verdict, minimumScore, gatePassed, tenantKey));
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/security")
    @AdminPermitted("agent:security:view")
    public Map<String, Object> security(HttpServletRequest request) {
        try { requirePlatformScope(request); return AdminAjaxResult.okData(queryService.security()); }
        catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/artifacts")
    @AdminPermitted("agent:artifact:view")
    public Map<String, Object> artifacts(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String artifactType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tenantKey,
            HttpServletRequest request
    ) {
        try {
            requireTenantScope(request, tenantKey, "VIEWER");
            AgentAdminQueryService.PageResult result = queryService.artifacts(pageNum, pageSize, artifactType, status, keyword, tenantKey);
            return AdminAjaxResult.table(result.total(), result.rows());
        } catch (IllegalArgumentException e) { return AdminAjaxResult.error(e.getMessage()); }
    }

    private Map<String, Object> control(long id, String action, HttpServletRequest request) {
        try {
            requireTaskScope(request, id, "ADMIN");
            return AdminAjaxResult.okData(taskService.control(id, action));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    private static Long adminId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("adminSession");
        if (value instanceof RuoYiAdminAccessService.AdminSession session) {
            return session.getId();
        }
        return null;
    }

    private void requireTenantScope(HttpServletRequest request, String tenantKey, String role) {
        if (tenantKey == null || tenantKey.isBlank()) {
            if (request != null && isPlatformScope(request)) return;
            log.warn("Agent tenant scope denied adminId={} tenantKey=<none> requiredRole={}", adminId(request), role);
            throw new IllegalArgumentException("普通管理员必须指定已授权 tenantKey");
        }
        if (request == null || isPlatformScope(request)) return;
        Long admin = adminId(request);
        if (admin == null || !tenantService.hasAdminTenantAccess(admin, tenantKey, role)) {
            log.warn("Agent tenant scope denied adminId={} tenantKey={} requiredRole={}", admin, tenantKey, role);
            throw new IllegalArgumentException("管理员未授权访问该租户");
        }
    }

    private void requireTaskScope(HttpServletRequest request, long taskId, String role) {
        AgentAdminQueryService.TaskDetail detail = queryService.findTask(taskId);
        if (detail == null || detail.task() == null) throw new IllegalArgumentException("Agent 任务不存在");
        requireTenantScope(request, String.valueOf(detail.task().get("tenantKey")), role);
    }

    private void requireApprovalScope(HttpServletRequest request, long approvalId, String role) {
        Map<String, Object> approval = queryService.findApproval(approvalId);
        if (approval == null) throw new IllegalArgumentException("Agent 审批不存在");
        requireTaskScope(request, number(approval.get("taskId")), role);
    }

    private static boolean isPlatformScope(HttpServletRequest request) {
        Object permissions = request == null ? null : request.getAttribute("adminPermissions");
        return permissions instanceof java.util.Collection<?> values
                && values.stream().anyMatch(value -> "*:*:*".equals(String.valueOf(value)));
    }

    private static void requirePlatformScope(HttpServletRequest request) {
        if (!isPlatformScope(request)) {
            log.warn("Agent platform scope denied adminId={}", adminId(request));
            throw new IllegalArgumentException("仅平台超级权限可以执行该操作");
        }
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String decisionComment(Map<String, Object> body) {
        if (body == null || body.get("decisionComment") == null) {
            return null;
        }
        return String.valueOf(body.get("decisionComment"));
    }
}
