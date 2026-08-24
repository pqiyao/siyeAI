package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.service.AgentArtifactStorageService;
import com.example.sillyspringboot.agent.service.AgentPlanService;
import com.example.sillyspringboot.agent.service.AgentTaskService;
import com.example.sillyspringboot.agent.tool.AgentEvaluationTool;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** User-facing Agent workbench API. It only exposes tasks owned by the authenticated H5 user. */
@RestController
@RequestMapping("/api/v1/agent")
public class ApiV1AgentController {

    private static final int MAX_PAGE_SIZE = 30;

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AgentAdminMapper mapper;
    private final AgentTaskService taskService;
    private final AgentPlanService planService;
    private final AgentArtifactStorageService artifactStorageService;
    private final AgentEvaluationTool evaluationTool;

    public ApiV1AgentController(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AgentAdminMapper mapper,
            AgentTaskService taskService,
            AgentPlanService planService,
            AgentArtifactStorageService artifactStorageService,
            AgentEvaluationTool evaluationTool
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.mapper = mapper;
        this.taskService = taskService;
        this.planService = planService;
        this.artifactStorageService = artifactStorageService;
        this.evaluationTool = evaluationTool;
    }

    @GetMapping("/tasks")
    public ApiV1Result<Map<String, Object>> listTasks(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        long userId = requireUserId(clientUid);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        String safeStatus = clip(status, 32);
        String safeKeyword = clip(keyword, 128);
        long total = mapper.countTasksForOwner(userId, safeStatus, safeKeyword);
        List<Map<String, Object>> rows = mapper.listTasksForOwner(
                userId, safeStatus, safeKeyword, (safePage - 1) * safePageSize, safePageSize
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", safePage);
        data.put("pageSize", safePageSize);
        data.put("rows", rows == null ? List.of() : rows);
        return ApiV1Result.ok(data);
    }

    @PostMapping("/tasks")
    public ApiV1Result<Map<String, Object>> createTask(
            @RequestParam("clientUid") String clientUid,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        long userId = requireUserId(clientUid);
        Map<String, Object> safe = new LinkedHashMap<>();
        if (body != null) {
            safe.put("title", body.get("title"));
            safe.put("objective", body.get("objective"));
        safe.put("constraints", body.get("constraints"));
        safe.put("idempotencyKey", body.get("idempotencyKey"));
        safe.put("tenantKey", "user:" + userId);
        }
        safe.put("taskType", "CONTENT_CREATION");
        safe.put("riskLevel", "WRITE_REVERSIBLE");
        safe.put("maxSteps", 50);
        safe.put("maxRetries", 2);
        safe.put("maxRuntimeSeconds", 1800);
        safe.put("maxTokens", 100000);
        safe.put("maxCost", "5.000000");
        try {
            return ApiV1Result.ok(taskService.create(safe, userId));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw userValidation(ex);
        }
    }

    @GetMapping("/tasks/{id}")
    public ApiV1Result<Map<String, Object>> task(
            @PathVariable long id,
            @RequestParam("clientUid") String clientUid
    ) {
        long userId = requireUserId(clientUid);
        Map<String, Object> owned = requireOwnedTask(id, userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", owned);
        data.put("plan", mapper.findLatestPlan(id));
        data.put("steps", mapper.listSteps(id));
        data.put("toolCalls", mapper.listToolCalls(id));
        data.put("collaborations", mapper.listCollaborations(id));
        data.put("events", mapper.listEvents(id, 0, 200));
        data.put("artifacts", mapper.listArtifacts(id));
        data.put("usage", mapper.summarizeToolUsage(id));
        String status = String.valueOf(owned.get("status"));
        if (List.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED").contains(status)) {
            data.put("evaluation", evaluationTool.evaluate(owned));
        }
        return ApiV1Result.ok(data);
    }

    @PostMapping("/tasks/{id}/plan")
    public ApiV1Result<Map<String, Object>> generatePlan(
            @PathVariable long id,
            @RequestParam("clientUid") String clientUid
    ) {
        long userId = requireUserId(clientUid);
        requireOwnedTask(id, userId);
        try {
            return ApiV1Result.ok(planService.generate(id, userId));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw userValidation(ex);
        }
    }

    @PostMapping("/tasks/{id}/plan/approve")
    public ApiV1Result<Map<String, Object>> approvePlan(
            @PathVariable long id,
            @RequestParam("clientUid") String clientUid
    ) {
        long userId = requireUserId(clientUid);
        requireOwnedTask(id, userId);
        try {
            return ApiV1Result.ok(planService.approve(id, userId));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw userValidation(ex);
        }
    }

    @PostMapping("/tasks/{id}/{action}")
    public ApiV1Result<Map<String, Object>> control(
            @PathVariable long id,
            @PathVariable String action,
            @RequestParam("clientUid") String clientUid
    ) {
        long userId = requireUserId(clientUid);
        requireOwnedTask(id, userId);
        String safeAction = switch (action == null ? "" : action.trim().toLowerCase()) {
            case "pause", "resume", "cancel", "retry", "replan" -> action.trim().toUpperCase();
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的任务操作");
        };
        try {
            return ApiV1Result.ok(taskService.control(id, safeAction));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw userValidation(ex);
        }
    }

    @GetMapping("/tasks/{id}/events")
    public ApiV1Result<List<Map<String, Object>>> events(
            @PathVariable long id,
            @RequestParam("clientUid") String clientUid,
            @RequestParam(defaultValue = "0") long afterSeq
    ) {
        long userId = requireUserId(clientUid);
        requireOwnedTask(id, userId);
        return ApiV1Result.ok(mapper.listEvents(id, Math.max(0, afterSeq), 100));
    }

    @GetMapping("/tasks/{taskId}/artifacts/{artifactKey}/download")
    public ResponseEntity<Resource> downloadArtifact(
            @PathVariable long taskId,
            @PathVariable String artifactKey,
            @RequestParam("clientUid") String clientUid
    ) {
        long userId = requireUserId(clientUid);
        requireOwnedTask(taskId, userId);
        Map<String, Object> artifact = mapper.findArtifact(taskId, clip(artifactKey, 160), 1);
        if (artifact == null || artifact.get("uri") == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path path = artifactStorageService.resolveDownload(taskId, String.valueOf(artifact.get("uri")));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(String.valueOf(artifact.getOrDefault("name", "agent-artifact.bin")), StandardCharsets.UTF_8)
                    .build());
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(Files.size(path));
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));
        } catch (IOException | IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private long requireUserId(String clientUid) {
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "clientUid missing");
        }
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid.trim());
        return tokenService.validateAndLoadUser(token).getId();
    }

    private Map<String, Object> requireOwnedTask(long id, long userId) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务编号无效");
        }
        Map<String, Object> task = mapper.findTask(id);
        if (task == null || !sameOwner(task.get("createdBy"), userId)
                || !("user:" + userId).equals(String.valueOf(task.getOrDefault("tenantKey", "")))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private static boolean sameOwner(Object owner, long userId) {
        if (owner instanceof Number number) return number.longValue() == userId;
        try { return Long.parseLong(String.valueOf(owner)) == userId; }
        catch (Exception ignored) { return false; }
    }

    private static String clip(String value, int max) {
        if (value == null) return null;
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static BusinessException userValidation(RuntimeException exception) {
        String message = exception == null ? "任务操作失败" : exception.getMessage();
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                message == null || message.isBlank() ? "任务操作失败，请稍后重试" : message);
    }
}
