package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.service.TagLibraryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/tag")
@AdminPermitted("content:tag:view")
public class AdminJiugaiTagController {

    private final TagLibraryService tagLibraryService;

    public AdminJiugaiTagController(TagLibraryService tagLibraryService) {
        this.tagLibraryService = tagLibraryService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled
    ) {
        return AdminAjaxResult.table(
                tagLibraryService.countAdminList(keyword, category, enabled),
                tagLibraryService.listAdminPage(keyword, category, enabled, pageNum, pageSize)
        );
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", tagLibraryService.listEnabledOptions());
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Map<String, Object> data = tagLibraryService.get(id);
        if (data == null) {
            return AdminAjaxResult.error("标签不存在");
        }
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", data);
        return result;
    }

    @PostMapping
    @AdminPermitted("content:tag:edit")
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> data = tagLibraryService.save(body);
        Map<String, Object> result = AdminAjaxResult.ok("新增成功");
        result.put("data", data);
        return result;
    }

    @PutMapping
    @AdminPermitted("content:tag:edit")
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> data = tagLibraryService.save(body);
        Map<String, Object> result = AdminAjaxResult.ok("保存成功");
        result.put("data", data);
        return result;
    }

    @PostMapping("/sync-existing")
    @AdminPermitted("content:tag:edit")
    public Map<String, Object> syncExisting() {
        int created = tagLibraryService.syncExistingCharacterTags();
        return AdminAjaxResult.ok("已同步 " + created + " 个角色标签");
    }

    @PostMapping("/batch-delete")
    @AdminPermitted("content:tag:edit")
    public Map<String, Object> removeBatch(@RequestBody(required = false) Map<String, Object> body) {
        List<Long> ids = body == null ? List.of() : parseIds(body.get("ids"));
        int deleted = tagLibraryService.removeBatch(ids);
        return AdminAjaxResult.ok("批量删除完成，共删除 " + deleted + " 个标签");
    }

    @DeleteMapping("/{id}")
    @AdminPermitted("content:tag:edit")
    public Map<String, Object> remove(@PathVariable long id) {
        tagLibraryService.remove(id);
        return AdminAjaxResult.ok("删除成功");
    }

    private static List<Long> parseIds(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            if (item instanceof Number number) {
                ids.add(number.longValue());
                continue;
            }
            try {
                ids.add(Long.parseLong(String.valueOf(item).trim()));
            } catch (Exception ignored) {
                // ignore invalid id
            }
        }
        return ids;
    }
}
