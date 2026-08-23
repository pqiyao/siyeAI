package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.ops.service.ImageGenerationReadinessService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

@Service
public class AgentAdminQueryService {

    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EVENT_PAGE_SIZE = 500;
    private static final Set<String> TASK_STATUSES = Set.of(
            "DRAFT", "PLANNING", "WAITING_APPROVAL", "READY", "RUNNING", "PAUSED",
            "WAITING_CONFIRMATION", "CANCEL_REQUESTED", "SUCCEEDED", "PARTIAL_SUCCESS",
            "FAILED", "CANCELLED"
    );
    private static final Set<String> RISK_LEVELS = Set.of(
            "READ_ONLY", "WRITE_REVERSIBLE", "EXTERNAL_SIDE_EFFECT", "DESTRUCTIVE", "PRODUCTION_OPERATION"
    );
    private static final Set<String> APPROVAL_STATUSES = Set.of(
            "PENDING", "APPROVED", "REJECTED", "EXPIRED", "CANCELLED"
    );

    private final AgentAdminMapper mapper;
    private final AiRoutingService aiRoutingService;
    private final ImageGenerationReadinessService imageReadinessService;

    public AgentAdminQueryService(
            AgentAdminMapper mapper,
            AiRoutingService aiRoutingService,
            ImageGenerationReadinessService imageReadinessService
    ) {
        this.mapper = mapper;
        this.aiRoutingService = aiRoutingService;
        this.imageReadinessService = imageReadinessService;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> overview = mapper.overview();
        if (overview != null) {
            result.putAll(overview);
        }
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("chat", safeCapability(AiCapability.CHAT));
        capabilities.put("image", safeImageReadiness());
        capabilities.put("tts", safeCapability(AiCapability.TTS));
        result.put("capabilities", capabilities);
        return result;
    }

    public Map<String, Object> runtime() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", Instant.now().toString());
        result.put("service", "SillySpringboot");
        result.put("javaVersion", System.getProperty("java.version", ""));
        result.put("worker", Map.of(
                "enabled", true,
                "mode", "database-lease",
                "intervalSeconds", 5,
                "leaseSeconds", 60
        ));
        result.put("overview", overview());
        return result;
    }

    private Map<String, Object> safeCapability(AiCapability capability) {
        try {
            return aiRoutingService.capabilitySummary(capability);
        } catch (RuntimeException ex) {
            return Map.of("capability", capability.name(), "ready", false, "status", "unavailable");
        }
    }

    private Map<String, Object> safeImageReadiness() {
        try {
            return imageReadinessService.toMap(imageReadinessService.systemSnapshot());
        } catch (RuntimeException ex) {
            return Map.of("ready", false, "statusCode", "unavailable");
        }
    }

    public PageResult listTasks(int pageNum, int pageSize, String status, String riskLevel, String keyword) {
        String safeStatus = optionalEnum(status, TASK_STATUSES, "任务状态");
        String safeRiskLevel = optionalEnum(riskLevel, RISK_LEVELS, "风险等级");
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countTasks(safeStatus, safeRiskLevel, clip(keyword, 128)),
                mapper.listTasks(safeStatus, safeRiskLevel, clip(keyword, 128), page.offset(), page.size())
        );
    }

    public TaskDetail findTask(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("任务编号无效");
        }
        Map<String, Object> task = mapper.findTask(id);
        if (task == null || task.isEmpty()) {
            return null;
        }
        return new TaskDetail(task, mapper.findLatestPlan(id), mapper.listSteps(id), mapper.listToolCalls(id));
    }

    public List<Map<String, Object>> listEvents(long taskId, long afterSeq, int limit) {
        validateId(taskId, "任务编号");
        long safeAfterSeq = Math.max(0, afterSeq);
        int safeLimit = Math.max(1, Math.min(MAX_EVENT_PAGE_SIZE, limit));
        return mapper.listEvents(taskId, safeAfterSeq, safeLimit);
    }

    public List<Map<String, Object>> listArtifacts(long taskId) {
        validateId(taskId, "任务编号");
        return mapper.listArtifacts(taskId);
    }

    public Map<String, Object> findArtifact(long taskId, String artifactKey) {
        validateId(taskId, "任务编号");
        String key = artifactKey == null ? "" : artifactKey.trim();
        if (key.isBlank() || key.length() > 160) {
            throw new IllegalArgumentException("产物编号无效");
        }
        return mapper.findArtifact(taskId, key, 1);
    }

    public PageResult listApprovals(int pageNum, int pageSize, String status, String keyword) {
        String safeStatus = optionalEnum(status, APPROVAL_STATUSES, "审批状态");
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countApprovals(safeStatus, clip(keyword, 128)),
                mapper.listApprovals(safeStatus, clip(keyword, 128), page.offset(), page.size())
        );
    }

    public PageResult listTools(int pageNum, int pageSize, String category, Boolean enabled, String keyword) {
        PageWindow page = page(pageNum, pageSize);
        return new PageResult(
                mapper.countTools(clip(category, 64), enabled, clip(keyword, 128)),
                mapper.listTools(clip(category, 64), enabled, clip(keyword, 128), page.offset(), page.size())
        );
    }

    public Map<String, Object> findTool(long id) {
        validateId(id, "工具编号");
        return mapper.findTool(id);
    }

    private static PageWindow page(int pageNum, int pageSize) {
        int safePage = Math.max(1, Math.min(MAX_PAGE, pageNum));
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        long offset = (long) (safePage - 1) * safeSize;
        if (offset > Integer.MAX_VALUE) {
            offset = Integer.MAX_VALUE;
        }
        return new PageWindow((int) offset, safeSize);
    }

    private static String optionalEnum(String value, Set<String> allowed, String label) {
        String normalized = clip(value, 64);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase();
        if (!allowed.contains(upper)) {
            throw new IllegalArgumentException(label + "无效");
        }
        return upper;
    }

    private static String clip(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static void validateId(long id, String label) {
        if (id <= 0) {
            throw new IllegalArgumentException(label + "无效");
        }
    }

    public record PageResult(long total, List<Map<String, Object>> rows) {}

    public record TaskDetail(Map<String, Object> task,
                             Map<String, Object> plan,
                             List<Map<String, Object>> steps,
                             List<Map<String, Object>> toolCalls) {}

    private record PageWindow(int offset, int size) {}
}
