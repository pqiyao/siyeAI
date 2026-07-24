package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminChatRuntimeMapper;
import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.service.AppChatFrontendBridgeService;
import com.example.sillyspringboot.chat.service.AppChatRuntimeRegistry;
import com.example.sillyspringboot.chat.service.ChatGenerationDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/admin/jiugai/chat-runtime")
@AdminPermitted("ops:chat-runtime:view")
public class AdminChatRuntimeController {

    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_PAGE_SIZE = 1000;
    /** Align with page size so one full page can be deleted in a single request. */
    private static final int MAX_DELETE_IDS = MAX_PAGE_SIZE;
    private static final Set<String> TASK_STATUSES = Set.of(
            "QUEUED", "GENERATING", "SUCCESS", "FAILED", "STOPPED"
    );

    private final AdminChatRuntimeMapper adminMapper;
    private final AppGenerationTaskMapper taskMapper;
    private final ChatGenerationDispatcher dispatcher;
    private final AppChatRuntimeRegistry runtimeRegistry;
    private final AppChatFrontendBridgeService bridge;

    public AdminChatRuntimeController(
            AdminChatRuntimeMapper adminMapper,
            AppGenerationTaskMapper taskMapper,
            ChatGenerationDispatcher dispatcher,
            AppChatRuntimeRegistry runtimeRegistry,
            AppChatFrontendBridgeService bridge
    ) {
        this.adminMapper = adminMapper;
        this.taskMapper = taskMapper;
        this.dispatcher = dispatcher;
        this.runtimeRegistry = runtimeRegistry;
        this.bridge = bridge;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", adminMapper.summary());
        data.put("dispatcher", dispatcher.status());
        data.put("runtime", runtimeRegistry.status());
        data.put("bridge", bridge.status());
        return AdminAjaxResult.okData(data);
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        String safeStatus = normalizeStatus(status);
        if (status != null && !status.isBlank() && safeStatus == null) {
            return AdminAjaxResult.error("任务状态无效");
        }
        int safePage = Math.max(1, Math.min(MAX_PAGE, pageNum));
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        int offset = (safePage - 1) * safeSize;
        String safeKeyword = clipToNull(keyword, 128);
        return AdminAjaxResult.table(
                adminMapper.count(safeStatus, safeKeyword),
                adminMapper.list(safeStatus, safeKeyword, offset, safeSize)
        );
    }

    @PostMapping("/{taskId}/cancel")
    @AdminPermitted("ops:chat-runtime:cancel")
    public Map<String, Object> cancel(@PathVariable long taskId, HttpServletRequest request) {
        if (taskId <= 0) {
            return AdminAjaxResult.error("任务编号无效");
        }
        AppGenerationTask task = taskMapper.findById(taskId);
        if (task == null) {
            return AdminAjaxResult.error("任务不存在");
        }
        if (!isActive(task.getStatus())) {
            return AdminAjaxResult.error("任务已经结束");
        }

        String operator = request == null ? null : clipToNull(String.valueOf(request.getAttribute("adminUsername")), 64);
        String reason = operator == null ? "cancelled by admin" : "cancelled by admin " + operator;
        int updated = taskMapper.updateStatus(taskId, "STOPPED", "ADMIN_CANCELLED", reason, null, 499);
        if (updated != 1) {
            return AdminAjaxResult.error("任务状态已变化，请刷新后重试");
        }

        boolean signalled = runtimeRegistry.cancelTask(taskId);
        Map<String, Object> result = AdminAjaxResult.ok(
                signalled ? "取消信号已发送" : "任务已标记停止，但当前实例未找到运行句柄"
        );
        result.put("taskId", taskId);
        result.put("signalled", signalled);
        return result;
    }

    @DeleteMapping("/batch")
    @Transactional
    @AdminPermitted("ops:chat-runtime:delete")
    public Map<String, Object> hardDelete(@RequestBody(required = false) DeleteRequest request) {
        List<Long> ids = normalizeDeleteIds(request == null ? null : request.ids());
        if (ids.isEmpty()) {
            return AdminAjaxResult.error("请选择要删除的任务");
        }
        if (ids.size() > MAX_DELETE_IDS) {
            return AdminAjaxResult.error("单次最多删除 " + MAX_DELETE_IDS + " 个任务");
        }

        List<Map<String, Object>> tasks = adminMapper.findTaskStatuses(ids);
        if (tasks.size() != ids.size()) {
            return AdminAjaxResult.error("部分任务不存在或已经删除，请刷新后重试");
        }
        List<Long> activeIds = tasks.stream()
                .filter(row -> isActive(String.valueOf(row.get("status"))))
                .map(row -> ((Number) row.get("id")).longValue())
                .sorted()
                .toList();
        if (!activeIds.isEmpty()) {
            return AdminAjaxResult.error("运行中或排队中的任务不能删除：" + activeIds);
        }

        int attemptsDeleted = adminMapper.deleteAttemptsByTaskIds(ids);
        int statsDeleted = adminMapper.deleteStatEventsByTaskIds(ids);
        int tasksDeleted = adminMapper.hardDeleteTasks(ids);
        if (tasksDeleted != ids.size()) {
            throw new IllegalStateException("任务状态发生变化，硬删除已回滚");
        }

        Map<String, Object> result = AdminAjaxResult.ok("已永久删除 " + tasksDeleted + " 个聊天运行任务");
        result.put("deleted", tasksDeleted);
        result.put("attemptsDeleted", attemptsDeleted);
        result.put("statsDeleted", statsDeleted);
        return result;
    }

    private static boolean isActive(String status) {
        return "QUEUED".equals(status) || "GENERATING".equals(status);
    }

    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return TASK_STATUSES.contains(normalized) ? normalized : null;
    }

    private static String clipToNull(String value, int maxLength) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static List<Long> normalizeDeleteIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : rawIds) {
            if (id == null || id <= 0) {
                return List.of();
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    public record DeleteRequest(List<Long> ids) {
    }
}
