package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.compat.h5.entity.AppNotice;
import com.example.sillyspringboot.compat.h5.mapper.AppNoticeMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/notice")
@AdminPermitted("system:notice:view")
public class AdminJiugaiNoticeController {

    private static final DateTimeFormatter CREATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.CHINA);

    private final AppNoticeMapper noticeMapper;

    public AdminJiugaiNoticeController(AppNoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String title
    ) {
        int p = Math.max(0, pageNum - 1);
        int size = Math.min(100, Math.max(1, pageSize));
        long total = noticeMapper.countAdminList(blankToNull(title));
        List<AppNotice> page = noticeMapper.listAdminPage(blankToNull(title), p * size, size);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AppNotice notice : page) {
            rows.add(toRow(notice));
        }
        return AdminAjaxResult.table(total, rows);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        AppNotice notice = noticeMapper.findById(id);
        if (notice == null) {
            return AdminAjaxResult.error("公告不存在");
        }
        Map<String, Object> r = AdminAjaxResult.ok();
        r.put("data", toDetail(notice));
        return r;
    }

    @PostMapping
    @AdminPermitted("system:notice:edit")
    public Map<String, Object> add(@RequestBody(required = false) AppNotice body) {
        if (body == null || blank(body.getTitle()).isBlank()) {
            return AdminAjaxResult.error("标题不能为空");
        }
        if (blank(body.getContent()).isBlank()) {
            return AdminAjaxResult.error("内容不能为空");
        }
        body.setId(null);
        body.setLevel(blank(body.getLevel()).isBlank() ? "info" : body.getLevel().trim());
        if (body.getSortOrder() == null) {
            body.setSortOrder(0);
        }
        if (body.getEnabled() == null) {
            body.setEnabled(Boolean.TRUE);
        }
        if (body.getGuestVisible() == null) {
            body.setGuestVisible(Boolean.TRUE);
        }
        body.setDisplayType(normalizeDisplayType(body.getDisplayType()));
        noticeMapper.insert(body);
        Map<String, Object> r = AdminAjaxResult.ok("新增成功");
        r.put("data", toDetail(noticeMapper.findById(body.getId())));
        return r;
    }

    @PutMapping
    @AdminPermitted("system:notice:edit")
    public Map<String, Object> update(@RequestBody(required = false) AppNotice body) {
        if (body == null || body.getId() == null) {
            return AdminAjaxResult.error("缺少 id");
        }
        AppNotice cur = noticeMapper.findById(body.getId());
        if (cur == null) {
            return AdminAjaxResult.error("公告不存在");
        }
        cur.setTitle(blank(body.getTitle()));
        cur.setContent(blank(body.getContent()));
        cur.setLevel(blank(body.getLevel()).isBlank() ? "info" : body.getLevel().trim());
        cur.setSortOrder(body.getSortOrder() == null ? 0 : body.getSortOrder());
        cur.setEnabled(Boolean.TRUE.equals(body.getEnabled()));
        cur.setGuestVisible(Boolean.TRUE.equals(body.getGuestVisible()));
        cur.setDisplayType(normalizeDisplayType(body.getDisplayType()));
        noticeMapper.updateById(cur);
        return AdminAjaxResult.ok("修改成功");
    }

    @DeleteMapping("/{ids}")
    @Transactional
    @AdminPermitted("system:notice:edit")
    public Map<String, Object> remove(@PathVariable String ids) {
        for (Long id : parseIds(ids)) {
            noticeMapper.deleteById(id);
        }
        return AdminAjaxResult.ok("删除成功");
    }

    private static Map<String, Object> toRow(AppNotice notice) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", notice.getId());
        row.put("title", blank(notice.getTitle()));
        row.put("sortOrder", notice.getSortOrder() == null ? 0 : notice.getSortOrder());
        row.put("enabled", Boolean.TRUE.equals(notice.getEnabled()));
        row.put("guestVisible", Boolean.TRUE.equals(notice.getGuestVisible()));
        row.put("displayType", normalizeDisplayType(notice.getDisplayType()));
        row.put("createTime", notice.getCreatedAt() == null ? "" : CREATE_FMT.format(notice.getCreatedAt()));
        return row;
    }

    private static Map<String, Object> toDetail(AppNotice notice) {
        Map<String, Object> row = toRow(notice);
        row.put("content", blank(notice.getContent()));
        row.put("level", blank(notice.getLevel()));
        return row;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        String s = blank(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeDisplayType(String value) {
        String s = blank(value).trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "banner", "popup" -> s;
            default -> "inbox";
        };
    }

    private static List<Long> parseIds(String ids) {
        List<Long> out = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return out;
        }
        for (String token : ids.split(",")) {
            try {
                out.add(Long.parseLong(token.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
