package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminH5UserMapper;
import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.service.AdminH5UserLifecycleService;
import com.example.sillyspringboot.admin.service.AdminH5UserSecurityService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.compat.h5.entity.AppH5Profile;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ProfileMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.shared.error.BusinessException;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/h5-user")
@AdminPermitted("commerce:user:view")
public class AdminJiugaiH5UserController {

    private final AdminH5UserMapper adminH5UserMapper;
    private final AppH5UserProfileExtMapper profileExtMapper;
    private final AppH5ProfileMapper profileMapper;
    private final EntitlementPolicyService entitlementPolicyService;
    private final AppFeatureSettingsService featureSettingsService;
    private final EntitlementAuditLogService auditLogService;
    private final AdminH5UserLifecycleService userLifecycleService;
    private final AdminH5UserSecurityService userSecurityService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public AdminJiugaiH5UserController(
            AdminH5UserMapper adminH5UserMapper,
            AppH5UserProfileExtMapper profileExtMapper,
            AppH5ProfileMapper profileMapper,
            EntitlementPolicyService entitlementPolicyService,
            AppFeatureSettingsService featureSettingsService,
            EntitlementAuditLogService auditLogService,
            AdminH5UserLifecycleService userLifecycleService,
            AdminH5UserSecurityService userSecurityService
    ) {
        this.adminH5UserMapper = adminH5UserMapper;
        this.profileExtMapper = profileExtMapper;
        this.profileMapper = profileMapper;
        this.entitlementPolicyService = entitlementPolicyService;
        this.featureSettingsService = featureSettingsService;
        this.auditLogService = auditLogService;
        this.userLifecycleService = userLifecycleService;
        this.userSecurityService = userSecurityService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer vipType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer needEdit
    ) {
        int page = Math.max(0, pageNum - 1);
        int size = Math.min(100, Math.max(1, pageSize));
        String safeStatus = blankToNull(status);
        Integer safeVipType = normalizeOptionalEnum(vipType);
        Integer safeNeedEdit = normalizeOptionalEnum(needEdit);
        long total = adminH5UserMapper.countList(blankToNull(keyword), safeVipType, safeStatus, safeNeedEdit);
        List<Map<String, Object>> rows = adminH5UserMapper.listPage(
                blankToNull(keyword),
                safeVipType,
                safeStatus,
                safeNeedEdit,
                page * size,
                size
        );
        return AdminAjaxResult.table(total, rows);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Map<String, Object> data = adminH5UserMapper.findDetail(id);
        if (data == null || data.isEmpty()) {
            return AdminAjaxResult.error("用户不存在");
        }
        data.put("conversations", normalizeConversationRows(adminH5UserMapper.listRecentConversationsByUser(id, 20)));
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", data);
        return result;
    }

    @PutMapping
    @AdminPermitted({"commerce:user:update", "commerce:user:edit"})
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return AdminAjaxResult.error("请求体不能为空");
        }
        Long id = longVal(body.get("id"));
        Map<String, Object> before = id == null ? null : adminH5UserMapper.findDetail(id);
        if (id == null || before == null || before.isEmpty()) {
            return AdminAjaxResult.error("用户不存在");
        }

        AppH5UserProfileExt ext = profileExtMapper.findByUserId(id);
        AppH5Profile profile = profileMapper.findByUserId(id);
        if (ext == null) {
            ext = new AppH5UserProfileExt();
            ext.setUserId(id);
        }

        int vipType = intVal(body.get("vipType"), 0);
        LocalDateTime vipExpiresAt = parseDateTime(body.get("vipExpiresAt"));
        if (vipType > 0 && vipExpiresAt == null) {
            return AdminAjaxResult.error("设置 VIP/SVIP 时必须填写会员到期时间");
        }
        if (vipType > 0 && !vipExpiresAt.isAfter(LocalDateTime.now())) {
            return AdminAjaxResult.error("会员到期时间必须晚于当前时间");
        }
        if (vipType <= 0) {
            vipExpiresAt = null;
        }

        ext.setNickname(str(body.get("nickname")));
        ext.setAvatar(str(body.get("avatar")));
        ext.setBio(str(body.get("bio")));
        ext.setVipType(vipType);
        ext.setVipExpiresAt(vipExpiresAt);
        ext.setScore(intVal(body.get("score"), 0));
        ext.setGoldCoin(intVal(body.get("goldCoin"), 0));
        ext.setChatQuotaOverride(nullableInt(body.get("chatQuotaOverride")));
        ext.setImageQuotaOverride(nullableInt(body.get("imageQuotaOverride")));
        if (body.containsKey("dailyChatQuota") && !body.containsKey("chatQuotaOverride")) {
            ext.setChatQuotaOverride(nullableInt(body.get("dailyChatQuota")));
        }
        if (body.containsKey("dailyImageQuota") && !body.containsKey("imageQuotaOverride")) {
            ext.setImageQuotaOverride(nullableInt(body.get("dailyImageQuota")));
        }
        ext.setDailyChatUsed(intVal(body.get("dailyChatUsed"), ext.getDailyChatUsed() == null ? 0 : ext.getDailyChatUsed()));
        ext.setDailyImageUsed(intVal(body.get("dailyImageUsed"), ext.getDailyImageUsed() == null ? 0 : ext.getDailyImageUsed()));
        ext.setCharacterCreateAllowed(boolVal(body.get("characterCreateAllowed"), false) ? 1 : 0);
        entitlementPolicyService.refreshEffectiveQuota(ext);
        ext.setNeedEdit(intVal(body.get("needEdit"), 0));
        ext.setStatus(defaultIfBlank(str(body.get("status")), "normal"));
        ext.setGender(intVal(body.get("gender"), 0));
        ext.setBirthday(str(body.get("birthday")));
        ext.setHeight(str(body.get("height")));
        ext.setWeight(str(body.get("weight")));
        ext.setCountry(str(body.get("country")));
        ext.setCharacters(str(body.get("characters")));
        ext.setRelation(str(body.get("relation")));
        ext.setOccupation(str(body.get("occupation")));
        ext.setLabel(str(body.get("label")));

        profileExtMapper.upsert(ext);
        String persona = body.containsKey("persona")
                ? str(body.get("persona"))
                : (profile == null ? "" : str(profile.getPersona()));
        String stDisplayName = body.containsKey("stDisplayName")
                ? AppConversationService.normalizeStDisplayName(str(body.get("stDisplayName")))
                : AppConversationService.normalizeStDisplayName(profile == null ? null : profile.getStDisplayName());
        profileMapper.upsert(id, ext.getNickname(), persona, stDisplayName);

        Map<String, Object> after = adminH5UserMapper.findDetail(id);
        auditLogService.recordUserProfileUpdate(id, before, after, "admin");
        if (!isDisabled(before.get("status")) && isDisabled(after == null ? null : after.get("status"))) {
            userSecurityService.revokeSessions(id, "user disabled by admin");
        }
        return AdminAjaxResult.ok("保存成功");
    }

    @PostMapping
    @AdminPermitted("commerce:user:edit")
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body) {
        return AdminAjaxResult.error("当前 H5 用户由 clientUid 或 Telegram 自动建档，不支持后台手动创建账号");
    }

    @PostMapping("/reset-password")
    @AdminPermitted({"commerce:user:security", "commerce:user:edit"})
    public Map<String, Object> resetPassword(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return AdminAjaxResult.error("请求体不能为空");
        }
        Long id = longVal(body.get("id"));
        if (id == null || id <= 0) {
            return AdminAjaxResult.error("请选择要重置密码的用户");
        }
        boolean revokeSessions = boolVal(body.get("revokeSessions"), true);
        try {
            Map<String, Object> data = userSecurityService.resetPassword(id, str(body.get("password")), revokeSessions);
            Map<String, Object> result = AdminAjaxResult.ok("密码已重置");
            result.put("data", data);
            return result;
        } catch (BusinessException ex) {
            return AdminAjaxResult.error(ex.getMessage());
        }
    }

    @GetMapping("/runtime-settings")
    @AdminPermitted("commerce:entitlement:view")
    public Map<String, Object> runtimeSettingsAlias() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", featureSettingsService.toMap(featureSettingsService.getSettings()));
        return result;
    }

    @PutMapping("/runtime-settings")
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> updateRuntimeSettingsAlias(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> before = featureSettingsService.toMap(featureSettingsService.getSettings());
        Map<String, Object> after = featureSettingsService.toMap(featureSettingsService.saveSettings(body));
        auditLogService.recordPolicyUpdate(before, after, "admin");
        Map<String, Object> result = AdminAjaxResult.ok("保存成功");
        result.put("data", after);
        return result;
    }

    @PutMapping("/character-create-allowed/batch")
    @AdminPermitted({"commerce:user:batch-policy", "commerce:user:edit"})
    @Transactional
    public Map<String, Object> batchUpdateCharacterCreateAllowed(@RequestBody(required = false) Map<String, Object> body) {
        boolean allowed = boolVal(body == null ? null : body.get("allowed"), false);
        List<Long> userIds = normalizeUserIds(body == null ? null : body.get("ids"));
        if (userIds.isEmpty()) {
            return AdminAjaxResult.error("请先选择要操作的用户");
        }
        int affectedRows = profileExtMapper.upsertCharacterCreateAllowedForUsers(userIds, allowed ? 1 : 0);
        Map<String, Object> before = new HashMap<>();
        before.put("operation", "batchUpdateCharacterCreateAllowed");
        Map<String, Object> after = new HashMap<>();
        after.put("allowed", allowed);
        after.put("userIds", userIds);
        after.put("affectedRows", affectedRows);
        auditLogService.recordPolicyUpdate(before, after, "admin");
        Map<String, Object> data = new HashMap<>();
        data.put("allowed", allowed);
        data.put("requested", userIds.size());
        data.put("affectedRows", affectedRows);
        String message = (allowed ? "已批量开启已选用户自建角色卡权限" : "已批量关闭已选用户自建角色卡权限")
                + "，已处理 " + affectedRows + " / " + userIds.size() + " 个用户";
        Map<String, Object> result = AdminAjaxResult.ok(message);
        result.put("data", data);
        return result;
    }
    @DeleteMapping("/{ids}")
    @AdminPermitted({"commerce:user:delete", "commerce:user:edit"})
    public Map<String, Object> remove(@PathVariable String ids) {
        List<Long> userIds = Arrays.stream(String.valueOf(ids).split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(AdminJiugaiH5UserController::longVal)
                .filter(id -> id != null && id > 0)
                .toList();
        if (userIds.isEmpty()) {
            return AdminAjaxResult.error("请选择要删除的用户");
        }
        Map<String, Object> summary = userLifecycleService.deleteUsers(userIds);
        int failedCount = intVal(summary.get("failedCount"), 0);
        String message = "已删除 " + summary.get("deleted") + " / " + summary.get("requested") + " 个用户";
        if (failedCount > 0) {
            String firstReason = firstFailureReason(summary.get("failed"));
            message = message + "，失败 " + failedCount + " 个";
            if (!firstReason.isBlank()) {
                message = message + "，" + firstReason;
            }
        }
        Map<String, Object> result = AdminAjaxResult.ok(message);
        result.put("data", summary);
        return result;
    }

    private static String firstFailureReason(Object failed) {
        if (failed instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> item) {
            Object reason = item.get("reason");
            return reason == null ? "" : String.valueOf(reason).trim();
        }
        return "";
    }

    private static List<Long> normalizeUserIds(Object rawIds) {
        if (rawIds == null) {
            return List.of();
        }
        if (rawIds instanceof List<?> list) {
            return list.stream()
                    .map(AdminJiugaiH5UserController::longVal)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        }
        return Arrays.stream(String.valueOf(rawIds).split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(AdminJiugaiH5UserController::longVal)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> normalizeConversationRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>(row);
            item.put("worldNames", parseWorldNames(row.get("stWorldNamesJson")));
            normalized.add(item);
        }
        return normalized;
    }

    private List<String> parseWorldNames(Object rawValue) {
        String raw = rawValue == null ? "" : String.valueOf(rawValue).trim();
        if (raw.isBlank()) {
            return List.of();
        }
        try {
            List<?> list = objectMapper.readValue(raw, java.util.List.class);
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String text = item == null ? "" : String.valueOf(item).trim();
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Integer normalizeOptionalEnum(Integer value) {
        if (value == null || value < 0) {
            return null;
        }
        return value;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer nullableInt(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean boolVal(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private static boolean isDisabled(Object status) {
        return "disabled".equalsIgnoreCase(str(status));
    }

    private static LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

