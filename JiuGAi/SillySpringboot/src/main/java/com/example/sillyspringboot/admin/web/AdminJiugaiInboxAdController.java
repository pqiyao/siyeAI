package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.compat.h5.entity.AppInboxAd;
import com.example.sillyspringboot.compat.h5.mapper.AppInboxAdMapper;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/inbox-ad")
@AdminPermitted("system:inbox-ad:view")
public class AdminJiugaiInboxAdController {

    private static final DateTimeFormatter CREATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.CHINA);
    private static final int TITLE_MAX = 128;
    private static final int CONTENT_MAX = 500;
    private static final int URL_MAX = 512;

    private final AppInboxAdMapper inboxAdMapper;

    public AdminJiugaiInboxAdController(AppInboxAdMapper inboxAdMapper) {
        this.inboxAdMapper = inboxAdMapper;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Boolean enabled
    ) {
        int p = Math.max(0, pageNum - 1);
        int size = Math.min(100, Math.max(1, pageSize));
        long total = inboxAdMapper.countAdminList(blankToNull(title), enabled);
        List<AppInboxAd> page = inboxAdMapper.listAdminPage(blankToNull(title), enabled, p * size, size);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AppInboxAd ad : page) {
            rows.add(toRow(ad));
        }
        return AdminAjaxResult.table(total, rows);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        AppInboxAd ad = inboxAdMapper.findById(id);
        if (ad == null) {
            return AdminAjaxResult.error("\u5e7f\u544a\u4e0d\u5b58\u5728");
        }
        Map<String, Object> r = AdminAjaxResult.ok();
        r.put("data", toDetail(ad));
        return r;
    }

    @PostMapping
    @AdminPermitted("system:inbox-ad:edit")
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body) {
        String error = validateBody(body);
        if (error != null) {
            return AdminAjaxResult.error(error);
        }
        AppInboxAd row = fromBody(body);
        row.setId(null);
        inboxAdMapper.insert(row);
        Map<String, Object> r = AdminAjaxResult.ok("\u65b0\u589e\u6210\u529f");
        r.put("data", toDetail(inboxAdMapper.findById(row.getId())));
        return r;
    }

    @PutMapping
    @AdminPermitted("system:inbox-ad:edit")
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null || asLong(body.get("id")) <= 0) {
            return AdminAjaxResult.error("\u7f3a\u5c11 id");
        }
        long id = asLong(body.get("id"));
        AppInboxAd cur = inboxAdMapper.findById(id);
        if (cur == null) {
            return AdminAjaxResult.error("\u5e7f\u544a\u4e0d\u5b58\u5728");
        }
        String error = validateBody(body);
        if (error != null) {
            return AdminAjaxResult.error(error);
        }
        AppInboxAd row = fromBody(body);
        row.setId(id);
        inboxAdMapper.updateById(row);
        return AdminAjaxResult.ok("\u4fee\u6539\u6210\u529f");
    }

    @DeleteMapping("/{ids}")
    @Transactional
    @AdminPermitted("system:inbox-ad:edit")
    public Map<String, Object> remove(@PathVariable String ids) {
        for (Long id : parseIds(ids)) {
            inboxAdMapper.deleteById(id);
        }
        return AdminAjaxResult.ok("\u5220\u9664\u6210\u529f");
    }

    private static String validateBody(Map<String, Object> body) {
        if (body == null) {
            return "\u8bf7\u6c42\u4f53\u4e0d\u80fd\u4e3a\u7a7a";
        }
        String title = blank(asString(body.get("title")));
        String content = blank(asString(body.get("content")));
        String imageUrl = blank(asString(body.get("imageUrl")));
        String linkUrl = blank(asString(body.get("linkUrl")));
        if (title.length() > TITLE_MAX) {
            return "\u6807\u9898\u6700\u957f " + TITLE_MAX + " \u5b57";
        }
        if (content.length() > CONTENT_MAX) {
            return "\u6587\u6848\u6700\u957f " + CONTENT_MAX + " \u5b57";
        }
        if (imageUrl.length() > URL_MAX) {
            return "\u56fe\u7247\u5730\u5740\u8fc7\u957f";
        }
        if (linkUrl.length() > URL_MAX) {
            return "\u8df3\u8f6c\u94fe\u63a5\u8fc7\u957f";
        }
        if (title.isBlank() && content.isBlank() && imageUrl.isBlank()) {
            return "\u8bf7\u81f3\u5c11\u586b\u5199\u6807\u9898\u3001\u6587\u6848\u6216\u4e0a\u4f20\u56fe\u7247\u5176\u4e00";
        }
        if (!imageUrl.isBlank() && !isSafeAssetOrHttpUrl(imageUrl)) {
            return "\u56fe\u7247\u5730\u5740\u683c\u5f0f\u4e0d\u6b63\u786e\uff08\u652f\u6301 /uploads/... \u6216 http(s) \u94fe\u63a5\uff09";
        }
        if (!linkUrl.isBlank() && !isSafeLinkUrl(linkUrl)) {
            return "\u8df3\u8f6c\u94fe\u63a5\u683c\u5f0f\u4e0d\u6b63\u786e\uff08\u7ad9\u5185\u4ee5 / \u5f00\u5934\uff0c\u5916\u94fe\u9700 http(s)\uff09";
        }
        LocalDateTime startAt = parseDateTime(body.get("startAt"));
        LocalDateTime endAt = parseDateTime(body.get("endAt"));
        if (body.get("startAt") != null && !blank(asString(body.get("startAt"))).isBlank() && startAt == null) {
            return "\u751f\u6548\u5f00\u59cb\u65f6\u95f4\u683c\u5f0f\u4e0d\u6b63\u786e";
        }
        if (body.get("endAt") != null && !blank(asString(body.get("endAt"))).isBlank() && endAt == null) {
            return "\u751f\u6548\u7ed3\u675f\u65f6\u95f4\u683c\u5f0f\u4e0d\u6b63\u786e";
        }
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            return "\u7ed3\u675f\u65f6\u95f4\u4e0d\u80fd\u65e9\u4e8e\u5f00\u59cb\u65f6\u95f4";
        }
        return null;
    }

    private static AppInboxAd fromBody(Map<String, Object> body) {
        AppInboxAd row = new AppInboxAd();
        row.setTitle(blank(asString(body.get("title"))).trim());
        row.setContent(blank(asString(body.get("content"))).trim());
        row.setImageUrl(blank(asString(body.get("imageUrl"))).trim());
        row.setLinkUrl(blank(asString(body.get("linkUrl"))).trim());
        Object enabled = body.get("enabled");
        if (enabled instanceof Boolean b) {
            row.setEnabled(b);
        } else if (enabled != null) {
            row.setEnabled(!"0".equals(String.valueOf(enabled)) && !"false".equalsIgnoreCase(String.valueOf(enabled)));
        } else {
            row.setEnabled(Boolean.TRUE);
        }
        Object sort = body.get("sortOrder");
        if (sort instanceof Number n) {
            row.setSortOrder(n.intValue());
        } else if (sort != null && !String.valueOf(sort).isBlank()) {
            try {
                row.setSortOrder(Integer.parseInt(String.valueOf(sort).trim()));
            } catch (NumberFormatException ex) {
                row.setSortOrder(0);
            }
        } else {
            row.setSortOrder(0);
        }
        row.setStartAt(parseDateTime(body.get("startAt")));
        row.setEndAt(parseDateTime(body.get("endAt")));
        return row;
    }

    private static Map<String, Object> toRow(AppInboxAd ad) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", ad.getId());
        row.put("title", blank(ad.getTitle()));
        row.put("content", blank(ad.getContent()));
        row.put("imageUrl", blank(ad.getImageUrl()));
        row.put("linkUrl", blank(ad.getLinkUrl()));
        row.put("enabled", Boolean.TRUE.equals(ad.getEnabled()));
        row.put("sortOrder", ad.getSortOrder() == null ? 0 : ad.getSortOrder());
        row.put("startAt", formatDateTime(ad.getStartAt()));
        row.put("endAt", formatDateTime(ad.getEndAt()));
        row.put("createTime", formatDateTime(ad.getCreatedAt()));
        row.put("updateTime", formatDateTime(ad.getUpdatedAt()));
        return row;
    }

    private static Map<String, Object> toDetail(AppInboxAd ad) {
        return toRow(ad);
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : CREATE_FMT.format(value);
    }

    private static LocalDateTime parseDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt;
        }
        String s = blank(asString(raw)).trim();
        if (s.isEmpty()) {
            return null;
        }
        String normalized = s.replace('T', ' ');
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        try {
            return LocalDateTime.parse(normalized, CREATE_FMT);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException ignored2) {
                return null;
            }
        }
    }

    private static boolean isSafeAssetOrHttpUrl(String value) {
        String s = value.trim();
        if (s.startsWith("/uploads/") || s.startsWith("/art/")) {
            return !s.contains("..");
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static boolean isSafeLinkUrl(String value) {
        String s = value.trim();
        if (s.startsWith("/")) {
            return s.length() > 1 && !s.startsWith("//") && !s.contains("..");
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        String s = blank(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static List<Long> parseIds(String ids) {
        List<Long> out = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return out;
        }
        for (String token : ids.split(",")) {
            try {
                long id = Long.parseLong(token.trim());
                if (id > 0) {
                    out.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
