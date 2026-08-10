package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminAiLogMapper;
import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.retention.GenerationRetentionService;
import com.example.sillyspringboot.shared.logging.SensitiveLogSanitizer;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/admin/jiugai/ai-log")
@AdminPermitted("ops:ailog:view")
public class AdminJiugaiAiLogController {

    private final AdminAiLogMapper aiLogMapper;
    private final GenerationRetentionService retentionService;

    public AdminJiugaiAiLogController(AdminAiLogMapper aiLogMapper, GenerationRetentionService retentionService) {
        this.aiLogMapper = aiLogMapper;
        this.retentionService = retentionService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String providerKey,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) String startedAfter,
            @RequestParam(required = false) String startedBefore
    ) {
        int p = Math.max(0, pageNum - 1);
        int size = Math.min(100, Math.max(1, pageSize));
        QueryFilter filter = filter(channel, status, traceId, keyword, providerKey, model, httpStatus, startedAfter, startedBefore);
        long total = aiLogMapper.countList(filter.channel(), filter.status(), filter.traceId(), filter.keyword(),
                filter.providerKey(), filter.model(), filter.httpStatus(), filter.startedAfter(), filter.startedBefore());
        List<Map<String, Object>> rows = sanitizeRows(aiLogMapper.listPage(filter.channel(), filter.status(), filter.traceId(),
                filter.keyword(), filter.providerKey(), filter.model(), filter.httpStatus(), filter.startedAfter(),
                filter.startedBefore(), p * size, size));
        return AdminAjaxResult.table(total, rows);
    }

    @GetMapping("/attempts/{taskId}")
    public Map<String, Object> attempts(@PathVariable long taskId) {
        if (taskId <= 0) return AdminAjaxResult.table(0, List.of());
        List<Map<String, Object>> rows = sanitizeRows(aiLogMapper.listAttemptsByTaskId(taskId));
        return AdminAjaxResult.table(rows.size(), rows);
    }

    @GetMapping("/standalone/list")
    public Map<String, Object> standalone(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String capability,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String providerKey,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) String startedAfter,
            @RequestParam(required = false) String startedBefore
    ) {
        int p = Math.max(0, pageNum - 1);
        int size = Math.min(100, Math.max(1, pageSize));
        QueryFilter filter = filter(capability, status, traceId, keyword, providerKey, model, httpStatus, startedAfter, startedBefore);
        long total = aiLogMapper.countStandaloneRequests(filter.channel(), filter.status(), filter.traceId(),
                filter.keyword(), filter.providerKey(), filter.model(), filter.httpStatus(), filter.startedAfter(), filter.startedBefore());
        List<Map<String, Object>> rows = sanitizeRows(aiLogMapper.listStandaloneRequests(filter.channel(), filter.status(),
                filter.traceId(), filter.keyword(), filter.providerKey(), filter.model(), filter.httpStatus(),
                filter.startedAfter(), filter.startedBefore(), p * size, size));
        return AdminAjaxResult.table(total, rows);
    }

    @GetMapping("/standalone/attempts/{requestId}")
    public Map<String, Object> standaloneAttempts(@PathVariable String requestId) {
        String normalized = blankToNull(requestId);
        if (normalized == null || normalized.length() > 128) {
            return AdminAjaxResult.table(0, List.of());
        }
        List<Map<String, Object>> rows = sanitizeRows(aiLogMapper.listStandaloneAttemptsByRequestId(normalized));
        return AdminAjaxResult.table(rows.size(), rows);
    }

    @DeleteMapping("/clean/{beforeDays}")
    @AdminPermitted("ops:ailog:clean")
    public Map<String, Object> clean(@PathVariable int beforeDays) {
        int days = Math.max(1, Math.min(3650, beforeDays));
        int deleted = retentionService.cleanupBeforeDays(days);
        return AdminAjaxResult.ok("已归档并清理 " + deleted + " 条日志明细");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static QueryFilter filter(String channel, String status, String traceId, String keyword,
                                      String providerKey, String model, Integer httpStatus,
                                      String startedAfter, String startedBefore) {
        return new QueryFilter(blankToNull(channel), normalizeStatus(status), blankToNull(traceId),
                blankToNull(keyword), blankToNull(providerKey), blankToNull(model), validHttpStatus(httpStatus),
                parseDateTime(startedAfter), parseDateTime(startedBefore));
    }

    private static String normalizeStatus(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase();
        return List.of("QUEUED", "GENERATING", "SUCCESS", "FAILED", "STOPPED", "CANCELLED").contains(normalized)
                ? normalized : null;
    }

    private static Integer validHttpStatus(Integer value) {
        return value != null && value >= 100 && value <= 599 ? value : null;
    }

    private static LocalDateTime parseDateTime(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static List<Map<String, Object>> sanitizeRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        for (Map<String, Object> row : rows) {
            if (row == null) continue;
            for (String key : List.copyOf(row.keySet())) {
                if ("errorMessage".equalsIgnoreCase(key) && row.get(key) != null) {
                    row.put(key, SensitiveLogSanitizer.sanitize(String.valueOf(row.get(key)), 512));
                }
            }
        }
        return rows;
    }

    private record QueryFilter(String channel, String status, String traceId, String keyword, String providerKey,
                               String model, Integer httpStatus, LocalDateTime startedAfter, LocalDateTime startedBefore) {}
}
