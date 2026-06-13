package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminNoticeReadMapper;
import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.security.RuoYiAdminJwtService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.compat.h5.entity.AppNotice;
import com.example.sillyspringboot.compat.h5.mapper.AppNoticeMapper;
import io.jsonwebtoken.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/system/notice")
@AdminPermitted("system:notice:view")
public class SystemNoticeController {

    private static final DateTimeFormatter CREATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.CHINA);

    private final AppNoticeMapper noticeMapper;
    private final AdminNoticeReadMapper noticeReadMapper;
    private final RuoYiAdminJwtService jwtService;

    public SystemNoticeController(
            AppNoticeMapper noticeMapper,
            AdminNoticeReadMapper noticeReadMapper,
            RuoYiAdminJwtService jwtService
    ) {
        this.noticeMapper = noticeMapper;
        this.noticeReadMapper = noticeReadMapper;
        this.jwtService = jwtService;
    }

    @GetMapping("/listTop")
    public Map<String, Object> listTop(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String adminUsername = resolveAdminUsername(authorization);
        List<AppNotice> notices = noticeMapper.listTopForAdmin(8);
        Set<Long> readIds = adminUsername == null || notices.isEmpty()
                ? Set.of()
                : Set.copyOf(noticeReadMapper.listReadNoticeIds(
                        adminUsername,
                        notices.stream().map(AppNotice::getId).toList()
                ));

        List<Map<String, Object>> data = notices.stream()
                .map(n -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("noticeId", n.getId());
                    row.put("noticeTitle", blank(n.getTitle()));
                    row.put("noticeType", "1".equals(blank(n.getLevel())) || "warn".equalsIgnoreCase(blank(n.getLevel())) ? "1" : "2");
                    row.put("createTime", n.getCreatedAt() == null ? "" : CREATE_FMT.format(n.getCreatedAt()));
                    row.put("isRead", readIds.contains(n.getId()));
                    return row;
                })
                .toList();

        long unreadCount = data.stream().filter(it -> !Boolean.TRUE.equals(it.get("isRead"))).count();
        Map<String, Object> r = AdminAjaxResult.ok();
        r.put("data", data);
        r.put("unreadCount", unreadCount);
        return r;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        AppNotice notice = noticeMapper.findById(id);
        if (notice == null) {
            return AdminAjaxResult.error("公告不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeId", notice.getId());
        data.put("noticeTitle", blank(notice.getTitle()));
        data.put("noticeType", "1".equals(blank(notice.getLevel())) || "warn".equalsIgnoreCase(blank(notice.getLevel())) ? "1" : "2");
        data.put("noticeContent", blank(notice.getContent()));
        data.put("createBy", "SillySpringboot");
        data.put("createTime", notice.getCreatedAt() == null ? "" : CREATE_FMT.format(notice.getCreatedAt()));
        Map<String, Object> r = AdminAjaxResult.ok();
        r.put("data", data);
        return r;
    }

    @PostMapping("/markRead")
    public Map<String, Object> markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam long noticeId
    ) {
        String adminUsername = resolveRequiredAdminUsername(authorization);
        noticeReadMapper.markRead(adminUsername, noticeId);
        return AdminAjaxResult.ok();
    }

    @PostMapping("/markReadAll")
    public Map<String, Object> markReadAll(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String ids
    ) {
        String adminUsername = resolveRequiredAdminUsername(authorization);
        if (ids != null && !ids.isBlank()) {
            for (String token : ids.split(",")) {
                try {
                    noticeReadMapper.markRead(adminUsername, Long.parseLong(token.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return AdminAjaxResult.ok();
    }

    private String resolveRequiredAdminUsername(String authorization) {
        String username = resolveAdminUsername(authorization);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("admin token required");
        }
        return username;
    }

    private String resolveAdminUsername(String authorization) {
        String token = bearerToken(authorization);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return jwtService.parseUsername(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String s = authorization.trim();
        if (s.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return s.substring(7).trim();
        }
        return s;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
