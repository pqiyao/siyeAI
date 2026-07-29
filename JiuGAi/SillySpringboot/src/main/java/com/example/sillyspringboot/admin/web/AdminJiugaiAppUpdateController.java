package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.entity.AppAndroidRelease;
import com.example.sillyspringboot.ops.mapper.AppAndroidReleaseMapper;
import com.example.sillyspringboot.ops.service.AppUpdateService;
import org.springframework.dao.DuplicateKeyException;
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
@RequestMapping("/admin/jiugai/app-update")
@AdminPermitted("system:app-update:view")
public class AdminJiugaiAppUpdateController {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AppAndroidReleaseMapper mapper;

    public AdminJiugaiAppUpdateController(AppAndroidReleaseMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        int size = Math.max(1, Math.min(100, pageSize));
        int offset = Math.max(0, pageNum - 1) * size;
        String safeKeyword = blankToNull(keyword);
        String safeStatus = normalizeStatus(status, true);
        long total = mapper.countAdminList(safeKeyword, safeStatus);
        List<Map<String, Object>> rows = mapper.listAdminPage(safeKeyword, safeStatus, offset, size)
                .stream().map(AdminJiugaiAppUpdateController::toMap).toList();
        return AdminAjaxResult.table(total, rows);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        AppAndroidRelease row = mapper.findById(id);
        return row == null ? AdminAjaxResult.error("版本不存在") : AdminAjaxResult.okData(toMap(row));
    }

    @PostMapping
    @AdminPermitted("system:app-update:edit")
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body) {
        String error = validate(body);
        if (error != null) return AdminAjaxResult.error(error);
        AppAndroidRelease row = fromBody(body);
        row.setStatus("DRAFT");
        try {
            mapper.insert(row);
            return AdminAjaxResult.okData(toMap(mapper.findById(row.getId())));
        } catch (DuplicateKeyException ex) {
            return AdminAjaxResult.error("相同应用、包名和版本号已经存在");
        }
    }

    @PutMapping
    @AdminPermitted("system:app-update:edit")
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        long id = asLong(body == null ? null : body.get("id"));
        AppAndroidRelease current = id > 0 ? mapper.findById(id) : null;
        if (current == null) return AdminAjaxResult.error("版本不存在");
        if (!"DRAFT".equals(current.getStatus())) return AdminAjaxResult.error("只有草稿可以编辑，请先下架后新建更高版本");
        String error = validate(body);
        if (error != null) return AdminAjaxResult.error(error);
        AppAndroidRelease row = fromBody(body);
        row.setId(id);
        try {
            mapper.updateById(row);
            return AdminAjaxResult.ok("版本已保存");
        } catch (DuplicateKeyException ex) {
            return AdminAjaxResult.error("相同应用、包名和版本号已经存在");
        }
    }

    @PutMapping("/{id}/publish")
    @AdminPermitted("system:app-update:edit")
    public Map<String, Object> publish(@PathVariable long id) {
        AppAndroidRelease row = mapper.findById(id);
        if (row == null) return AdminAjaxResult.error("版本不存在");
        if (!AppUpdateService.isHttpsUrl(row.getDownloadUrl())) return AdminAjaxResult.error("下载地址必须是有效的 HTTPS 地址");
        mapper.updateStatus(id, "PUBLISHED");
        return AdminAjaxResult.ok(row.getPublishAt() != null && row.getPublishAt().isAfter(LocalDateTime.now())
                ? "版本已设置为定时发布" : "版本已发布");
    }

    @PutMapping("/{id}/revoke")
    @AdminPermitted("system:app-update:edit")
    public Map<String, Object> revoke(@PathVariable long id) {
        if (mapper.findById(id) == null) return AdminAjaxResult.error("版本不存在");
        mapper.updateStatus(id, "REVOKED");
        return AdminAjaxResult.ok("版本已下架");
    }

    @PutMapping("/{id}/remind-again")
    @AdminPermitted("system:app-update:edit")
    public Map<String, Object> remindAgain(@PathVariable long id) {
        return mapper.bumpPolicyRevision(id) > 0
                ? AdminAjaxResult.ok("策略修订号已递增，忽略过此版本的用户会再次收到提醒")
                : AdminAjaxResult.error("只有已发布版本可以重新提醒");
    }

    @DeleteMapping("/{id}")
    @AdminPermitted("system:app-update:edit")
    public Map<String, Object> delete(@PathVariable long id) {
        return mapper.deleteDraft(id) > 0
                ? AdminAjaxResult.ok("草稿已删除")
                : AdminAjaxResult.error("只有草稿可以删除");
    }

    private static String validate(Map<String, Object> body) {
        if (body == null) return "请求参数不能为空";
        if (limited(body.get("appId"), 64).isEmpty()) return "AppID 不能为空";
        if (limited(body.get("packageName"), 191).isEmpty()) return "安卓包名不能为空";
        if (limited(body.get("versionName"), 32).isEmpty()) return "版本名称不能为空";
        if (asInt(body.get("versionCode"), 0) <= 0) return "versionCode 必须大于 0";
        if (!AppUpdateService.isHttpsUrl(asString(body.get("downloadUrl")))) return "下载地址必须是有效的 HTTPS 地址";
        String mode = asString(body.get("updateMode")).trim().toUpperCase(Locale.ROOT);
        if (!mode.isEmpty() && !List.of("NORMAL", "FORCE").contains(mode)) return "更新模式不正确";
        int min = asInt(body.get("minSupportedVersionCode"), 0);
        if (min < 0) return "最低支持版本号不能小于 0";
        int hours = asInt(body.get("remindLaterHours"), 6);
        if (hours < 1 || hours > 168) return "稍后提醒时间必须在 1 至 168 小时之间";
        String sha = asString(body.get("apkSha256")).trim();
        if (!sha.isEmpty() && !sha.matches("(?i)^[0-9a-f]{64}$")) return "SHA-256 必须是 64 位十六进制字符串";
        if (asString(body.get("changelog")).length() > 10000) return "更新说明不能超过 10000 字";
        if (asString(body.get("title")).length() > 128) return "标题不能超过 128 字";
        Object publishAt = body.get("publishAt");
        if (publishAt != null && !asString(publishAt).isBlank() && parseDate(publishAt) == null) return "发布时间格式不正确";
        return null;
    }

    private static AppAndroidRelease fromBody(Map<String, Object> body) {
        AppAndroidRelease row = new AppAndroidRelease();
        row.setAppId(limited(body.get("appId"), 64));
        row.setPackageName(limited(body.get("packageName"), 191));
        row.setChannelCode(AppUpdateService.normalizeChannel(asString(body.get("channelCode"))));
        row.setVersionName(limited(body.get("versionName"), 32));
        row.setVersionCode(asInt(body.get("versionCode"), 0));
        String mode = asString(body.get("updateMode")).trim().toUpperCase(Locale.ROOT);
        row.setUpdateMode("FORCE".equals(mode) ? "FORCE" : "NORMAL");
        row.setMinSupportedVersionCode(Math.max(0, asInt(body.get("minSupportedVersionCode"), 0)));
        row.setPolicyRevision(Math.max(1, asInt(body.get("policyRevision"), 1)));
        row.setTitle(limited(body.get("title"), 128));
        row.setChangelog(asString(body.get("changelog")).trim());
        row.setDownloadUrl(asString(body.get("downloadUrl")).trim());
        row.setRemindLaterHours(Math.max(1, Math.min(168, asInt(body.get("remindLaterHours"), 6))));
        long size = asLong(body.get("apkSizeBytes"));
        row.setApkSizeBytes(size > 0 ? size : null);
        row.setApkSha256(asString(body.get("apkSha256")).trim().toLowerCase(Locale.ROOT));
        row.setPublishAt(parseDate(body.get("publishAt")));
        return row;
    }

    private static Map<String, Object> toMap(AppAndroidRelease row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("appId", row.getAppId());
        data.put("packageName", row.getPackageName());
        data.put("channelCode", row.getChannelCode());
        data.put("versionName", row.getVersionName());
        data.put("versionCode", row.getVersionCode());
        data.put("updateMode", row.getUpdateMode());
        data.put("minSupportedVersionCode", row.getMinSupportedVersionCode());
        data.put("policyRevision", row.getPolicyRevision());
        data.put("title", row.getTitle());
        data.put("changelog", row.getChangelog());
        data.put("downloadUrl", row.getDownloadUrl());
        data.put("remindLaterHours", row.getRemindLaterHours());
        data.put("apkSizeBytes", row.getApkSizeBytes());
        data.put("apkSha256", row.getApkSha256());
        data.put("status", row.getStatus());
        data.put("publishAt", formatDate(row.getPublishAt()));
        data.put("createdAt", formatDate(row.getCreatedAt()));
        data.put("updatedAt", formatDate(row.getUpdatedAt()));
        return data;
    }

    private static String normalizeStatus(String value, boolean allowBlank) {
        String status = asString(value).trim().toUpperCase(Locale.ROOT);
        if (status.isEmpty() && allowBlank) return null;
        return List.of("DRAFT", "PUBLISHED", "REVOKED").contains(status) ? status : null;
    }

    private static String limited(Object value, int max) {
        String text = asString(value).trim();
        return text.length() <= max ? text : "";
    }

    private static String blankToNull(String value) {
        String text = asString(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String asString(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int asInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(asString(value).trim()); } catch (NumberFormatException ex) { return fallback; }
    }
    private static long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(asString(value).trim()); } catch (NumberFormatException ex) { return 0L; }
    }
    private static LocalDateTime parseDate(Object value) {
        String text = asString(value).trim().replace('T', ' ');
        if (text.isEmpty()) return null;
        if (text.length() == 16) text += ":00";
        try { return LocalDateTime.parse(text, DATE_FMT); } catch (DateTimeParseException ex) { return null; }
    }
    private static String formatDate(LocalDateTime value) { return value == null ? "" : DATE_FMT.format(value); }
}
