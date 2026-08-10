package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminVisitorRiskMapper;
import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/visitor-risk")
@AdminPermitted("ops:visitor-risk:view")
public class AdminVisitorRiskController {

    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int EVENT_LIMIT = 100;
    /** Align with page size so one full page can be deleted in a single request. */
    private static final int MAX_DELETE_IDS = MAX_PAGE_SIZE;

    private final AdminVisitorRiskMapper mapper;

    public AdminVisitorRiskController(AdminVisitorRiskMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return AdminAjaxResult.okData(mapper.overview());
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean riskOnly
    ) {
        int safePage = Math.max(1, Math.min(MAX_PAGE, pageNum));
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        int offset = (safePage - 1) * safeSize;
        String safeKeyword = clipToNull(keyword, 64);
        return AdminAjaxResult.table(
                mapper.count(safeKeyword, riskOnly),
                mapper.list(safeKeyword, riskOnly, offset, safeSize)
        );
    }

    @GetMapping("/{deviceId}/events")
    public Map<String, Object> events(@PathVariable long deviceId) {
        if (deviceId <= 0) {
            return AdminAjaxResult.error("设备编号无效");
        }
        return AdminAjaxResult.okData(mapper.events(deviceId, EVENT_LIMIT));
    }

    @DeleteMapping("/batch")
    @Transactional
    @AdminPermitted("ops:visitor-risk:delete")
    public Map<String, Object> hardDelete(@RequestBody(required = false) DeleteRequest request) {
        List<Long> ids = normalizeDeleteIds(request == null ? null : request.ids());
        if (ids.isEmpty()) {
            return AdminAjaxResult.error("请选择要删除的访客设备");
        }
        if (ids.size() > MAX_DELETE_IDS) {
            return AdminAjaxResult.error("单次最多删除 " + MAX_DELETE_IDS + " 个访客设备");
        }
        if (mapper.countDevicesByIds(ids) != ids.size()) {
            return AdminAjaxResult.error("部分访客设备不存在或已经删除，请刷新后重试");
        }

        int eventsDeleted = mapper.deleteEventsByDeviceIds(ids);
        int devicesDeleted = mapper.deleteDevicesByIds(ids);
        if (devicesDeleted != ids.size()) {
            throw new IllegalStateException("访客设备状态发生变化，硬删除已回滚");
        }

        Map<String, Object> result = AdminAjaxResult.ok("已永久删除 " + devicesDeleted + " 个访客设备");
        result.put("deleted", devicesDeleted);
        result.put("eventsDeleted", eventsDeleted);
        return result;
    }

    private static String clipToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
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
