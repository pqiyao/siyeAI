package com.example.sillyspringboot.admin.security;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class AdminPermissionCatalog {

    public static final String ALL_PERMISSION = "*:*:*";

    public List<PermissionGroup> permissionGroups() {
        return List.of(
                group("system", "\u7cfb\u7edf", "\u7cfb\u7edf\u914d\u7f6e\u3001\u540e\u53f0\u8d26\u53f7\u3001\u89d2\u8272\u548c\u516c\u544a", 10, List.of(
                        item("system:admin-user:view", "\u67e5\u770b\u7ba1\u7406\u5458\u8d26\u53f7", "admin-user", "\u7ba1\u7406\u5458\u8d26\u53f7", "view", "\u67e5\u770b", RiskLevel.HIGH, roles("super-admin", "admin", "ops")),
                        item("system:admin-user:edit", "\u7f16\u8f91\u7ba1\u7406\u5458\u8d26\u53f7", "admin-user", "\u7ba1\u7406\u5458\u8d26\u53f7", "edit", "\u7f16\u8f91", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-user:create", "\u65b0\u589e\u7ba1\u7406\u5458\u8d26\u53f7", "admin-user", "\u7ba1\u7406\u5458\u8d26\u53f7", "create", "\u65b0\u589e", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-user:update", "\u66f4\u65b0\u7ba1\u7406\u5458\u8d26\u53f7", "admin-user", "\u7ba1\u7406\u5458\u8d26\u53f7", "update", "\u66f4\u65b0", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-user:status", "\u542f\u505c\u7ba1\u7406\u5458\u8d26\u53f7", "admin-user", "\u7ba1\u7406\u5458\u8d26\u53f7", "status", "\u542f\u505c", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-user:reset-password", "\u91cd\u7f6e\u7ba1\u7406\u5458\u5bc6\u7801", "admin-user", "\u7ba1\u7406\u5458\u8d26\u53f7", "reset-password", "\u91cd\u7f6e\u5bc6\u7801", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-user:delete", "\u5220\u9664\u7ba1\u7406\u5458\u8d26\u53f7", "admin-user", "\u7ba1\u7406\u5458\u8d26\u53f7", "delete", "\u5220\u9664", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-role:view", "\u67e5\u770b\u7ba1\u7406\u5458\u89d2\u8272", "admin-role", "\u7ba1\u7406\u5458\u89d2\u8272", "view", "\u67e5\u770b", RiskLevel.HIGH, roles("super-admin", "admin", "ops")),
                        item("system:admin-role:edit", "\u7f16\u8f91\u7ba1\u7406\u5458\u89d2\u8272", "admin-role", "\u7ba1\u7406\u5458\u89d2\u8272", "edit", "\u7f16\u8f91", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-role:create", "\u65b0\u589e\u7ba1\u7406\u5458\u89d2\u8272", "admin-role", "\u7ba1\u7406\u5458\u89d2\u8272", "create", "\u65b0\u589e", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-role:update", "\u66f4\u65b0\u7ba1\u7406\u5458\u89d2\u8272", "admin-role", "\u7ba1\u7406\u5458\u89d2\u8272", "update", "\u66f4\u65b0", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-role:status", "\u542f\u505c\u7ba1\u7406\u5458\u89d2\u8272", "admin-role", "\u7ba1\u7406\u5458\u89d2\u8272", "status", "\u542f\u505c", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:admin-role:delete", "\u5220\u9664\u7ba1\u7406\u5458\u89d2\u8272", "admin-role", "\u7ba1\u7406\u5458\u89d2\u8272", "delete", "\u5220\u9664", RiskLevel.CRITICAL, roles("super-admin", "admin")),
                        item("system:permission-log:view", "\u67e5\u770b\u6743\u9650\u53d8\u66f4\u65e5\u5fd7", "permission-log", "\u6743\u9650\u53d8\u66f4\u65e5\u5fd7", "view", "\u67e5\u770b", RiskLevel.HIGH, roles("super-admin", "admin", "ops")),
                        item("system:notice:view", "\u67e5\u770b\u7cfb\u7edf\u516c\u544a", "notice", "\u7cfb\u7edf\u516c\u544a", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "support", "content", "finance", "ops")),
                        item("system:notice:edit", "\u7f16\u8f91\u7cfb\u7edf\u516c\u544a", "notice", "\u7cfb\u7edf\u516c\u544a", "edit", "\u7f16\u8f91", RiskLevel.MEDIUM, roles("super-admin", "admin", "ops")),
                        item("system:inbox-ad:view", "\u67e5\u770b\u4f1a\u8bdd\u5e7f\u544a", "inbox-ad", "\u4f1a\u8bdd\u5e7f\u544a", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "ops", "content")),
                        item("system:inbox-ad:edit", "\u7f16\u8f91\u4f1a\u8bdd\u5e7f\u544a", "inbox-ad", "\u4f1a\u8bdd\u5e7f\u544a", "edit", "\u7f16\u8f91", RiskLevel.MEDIUM, roles("super-admin", "admin", "ops"))
                )),
                group("support", "\u5ba2\u670d", "\u5ba2\u670d\u5de5\u5355\u5904\u7406\u548c\u7528\u6237\u95ee\u9898\u8ddf\u8fdb", 20, List.of(
                        item("support:ticket:list", "\u67e5\u770b\u5de5\u5355\u5217\u8868", "ticket", "\u5ba2\u670d\u5de5\u5355", "list", "\u5217\u8868", RiskLevel.LOW, roles("super-admin", "admin", "support")),
                        item("support:ticket:view", "\u67e5\u770b\u5de5\u5355\u8be6\u60c5", "ticket", "\u5ba2\u670d\u5de5\u5355", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "support")),
                        item("support:ticket:reply", "\u56de\u590d\u5ba2\u670d\u5de5\u5355", "ticket", "\u5ba2\u670d\u5de5\u5355", "reply", "\u56de\u590d", RiskLevel.MEDIUM, roles("super-admin", "admin", "support")),
                        item("support:ticket:update", "\u66f4\u65b0\u5de5\u5355\u72b6\u6001", "ticket", "\u5ba2\u670d\u5de5\u5355", "update", "\u66f4\u65b0\u72b6\u6001", RiskLevel.MEDIUM, roles("super-admin", "admin", "support"))
                )),
                group("content", "\u5185\u5bb9", "\u89d2\u8272\u3001\u5ba1\u6838\u3001\u6807\u7b7e\u548c\u4e16\u754c\u4e66\u7ba1\u7406", 30, List.of(
                        item("content:character:view", "\u67e5\u770b\u89d2\u8272", "character", "\u89d2\u8272\u7ba1\u7406", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "content")),
                        item("content:character:edit", "\u7f16\u8f91\u89d2\u8272", "character", "\u89d2\u8272\u7ba1\u7406", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "content")),
                        item("content:review:view", "\u67e5\u770b\u5ba1\u6838\u65e5\u5fd7", "review", "\u5ba1\u6838\u65e5\u5fd7", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "content")),
                        item("content:review:edit", "\u5ba1\u6838\u89d2\u8272", "review", "\u5ba1\u6838\u65e5\u5fd7", "review", "\u5ba1\u6838", RiskLevel.HIGH, roles("super-admin", "admin", "content")),
                        item("content:tag:view", "\u67e5\u770b\u6807\u7b7e", "tag", "\u6807\u7b7e\u5e93", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "content")),
                        item("content:tag:edit", "\u7f16\u8f91\u6807\u7b7e", "tag", "\u6807\u7b7e\u5e93", "edit", "\u7f16\u8f91", RiskLevel.MEDIUM, roles("super-admin", "admin", "content")),
                        item("content:illustration:view", "\u67e5\u770b\u63d2\u753b\u4f5c\u54c1", "illustration", "\u63d2\u753b\u4f5c\u54c1", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "content")),
                        item("content:illustration:edit", "\u7f16\u8f91\u63d2\u753b\u4f5c\u54c1", "illustration", "\u63d2\u753b\u4f5c\u54c1", "edit", "\u7f16\u8f91", RiskLevel.MEDIUM, roles("super-admin", "admin", "content")),
                        item("content:illustration:review", "\u5ba1\u6838\u63d2\u753b\u4f5c\u54c1", "illustration", "\u63d2\u753b\u4f5c\u54c1", "review", "\u5ba1\u6838", RiskLevel.HIGH, roles("super-admin", "admin", "content")),
                        item("content:illustration:delete", "\u5220\u9664\u63d2\u753b\u4f5c\u54c1", "illustration", "\u63d2\u753b\u4f5c\u54c1", "delete", "\u5220\u9664", RiskLevel.HIGH, roles("super-admin", "admin", "content")),
                        item("content:illustration-key:view", "\u67e5\u770b18+\u4e34\u65f6\u5bc6\u94a5", "illustration-key", "18+\u4e34\u65f6\u5bc6\u94a5", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "content")),
                        item("content:illustration-key:edit", "\u751f\u6210\u548c\u505c\u752818+\u4e34\u65f6\u5bc6\u94a5", "illustration-key", "18+\u4e34\u65f6\u5bc6\u94a5", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "content")),
                        item("content:illustration-notice:view", "\u67e5\u770b\u63d2\u753b\u7f51\u7ad9\u901a\u77e5", "illustration-notice", "\u63d2\u753b\u7f51\u7ad9\u901a\u77e5", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "content")),
                        item("content:illustration-notice:edit", "\u7f16\u8f91\u63d2\u753b\u7f51\u7ad9\u901a\u77e5", "illustration-notice", "\u63d2\u753b\u7f51\u7ad9\u901a\u77e5", "edit", "\u7f16\u8f91", RiskLevel.MEDIUM, roles("super-admin", "admin", "content")),
                        item("content:lorebook:view", "\u67e5\u770b\u4e16\u754c\u4e66", "lorebook", "\u4e16\u754c\u4e66", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "content")),
                        item("content:lorebook:edit", "\u7f16\u8f91\u4e16\u754c\u4e66", "lorebook", "\u4e16\u754c\u4e66", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "content")),
                        item("content:chat-preset:view", "\u67e5\u770b\u804a\u5929\u9884\u8bbe", "chat-preset", "\u804a\u5929\u9884\u8bbe", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "content")),
                        item("content:chat-preset:edit", "\u7f16\u8f91\u804a\u5929\u9884\u8bbe", "chat-preset", "\u804a\u5929\u9884\u8bbe", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "content"))
                )),
                group("commerce", "\u5546\u4e1a", "\u7528\u6237\u3001\u6743\u76ca\u3001\u5546\u54c1\u3001\u652f\u4ed8\u548c\u8ba2\u5355", 40, List.of(
                        item("commerce:entitlement:view", "\u67e5\u770b\u6743\u76ca\u7b56\u7565", "entitlement", "\u6743\u76ca\u7b56\u7565", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "finance")),
                        item("commerce:entitlement:edit", "\u7f16\u8f91\u6743\u76ca\u7b56\u7565", "entitlement", "\u6743\u76ca\u7b56\u7565", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "finance")),
                        item("commerce:entitlement-log:view", "\u67e5\u770b\u6743\u76ca\u65e5\u5fd7", "entitlement-log", "\u6743\u76ca\u65e5\u5fd7", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "finance")),
                        item("commerce:entitlement-log:delete", "\u5220\u9664\u6743\u76ca\u65e5\u5fd7", "entitlement-log", "\u6743\u76ca\u65e5\u5fd7", "delete", "\u5220\u9664", RiskLevel.HIGH, roles("super-admin", "admin", "finance")),
                        item("commerce:user:view", "\u67e5\u770b H5 \u7528\u6237", "h5-user", "H5 \u7528\u6237", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "support", "finance")),
                        item("commerce:user:edit", "\u7f16\u8f91 H5 \u7528\u6237", "h5-user", "H5 \u7528\u6237", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "finance")),
                        item("commerce:user:update", "\u66f4\u65b0 H5 \u7528\u6237\u8d44\u6599\u548c\u989d\u5ea6", "h5-user", "H5 \u7528\u6237", "update", "\u66f4\u65b0", RiskLevel.HIGH, roles("super-admin", "admin", "finance")),
                        item("commerce:user:security", "\u91cd\u7f6e H5 \u7528\u6237\u5bc6\u7801", "h5-user", "H5 \u7528\u6237", "security", "\u8d26\u53f7\u5b89\u5168", RiskLevel.CRITICAL, roles("super-admin", "admin", "finance")),
                        item("commerce:user:batch-policy", "\u6279\u91cf\u66f4\u65b0 H5 \u7528\u6237\u7b56\u7565", "h5-user", "H5 \u7528\u6237", "batch-policy", "\u6279\u91cf\u7b56\u7565", RiskLevel.CRITICAL, roles("super-admin", "admin", "finance")),
                        item("commerce:user:delete", "\u5220\u9664 H5 \u7528\u6237", "h5-user", "H5 \u7528\u6237", "delete", "\u5220\u9664", RiskLevel.CRITICAL, roles("super-admin", "admin", "finance")),
                        item("commerce:product:view", "\u67e5\u770b\u5546\u54c1", "product", "\u5546\u54c1", "view", "\u67e5\u770b", RiskLevel.LOW, roles("super-admin", "admin", "finance")),
                        item("commerce:product:edit", "\u7f16\u8f91\u5546\u54c1", "product", "\u5546\u54c1", "edit", "\u7f16\u8f91", RiskLevel.MEDIUM, roles("super-admin", "admin", "finance")),
                        item("commerce:payment:view", "\u67e5\u770b\u652f\u4ed8\u6e20\u9053", "payment", "\u652f\u4ed8\u6e20\u9053", "view", "\u67e5\u770b", RiskLevel.HIGH, roles("super-admin", "admin", "finance", "ops")),
                        item("commerce:payment:edit", "\u7f16\u8f91\u652f\u4ed8\u6e20\u9053", "payment", "\u652f\u4ed8\u6e20\u9053", "edit", "\u7f16\u8f91", RiskLevel.CRITICAL, roles("super-admin", "admin", "finance")),
                        item("commerce:wallet:view", "\u67e5\u770b\u94b1\u5305\u6d41\u6c34", "wallet", "\u94b1\u5305\u6d41\u6c34", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "finance")),
                        item("commerce:checkin:view", "\u67e5\u770b\u6bcf\u65e5\u7b7e\u5230", "checkin", "\u6bcf\u65e5\u7b7e\u5230", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "finance")),
                        item("commerce:checkin:edit", "\u7f16\u8f91\u6bcf\u65e5\u7b7e\u5230", "checkin", "\u6bcf\u65e5\u7b7e\u5230", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "finance")),
                        item("commerce:order:view", "\u67e5\u770b\u8ba2\u5355", "order", "\u8ba2\u5355", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "finance")),
                        item("commerce:order:edit", "\u64cd\u4f5c\u8ba2\u5355", "order", "\u8ba2\u5355", "edit", "\u64cd\u4f5c", RiskLevel.HIGH, roles("super-admin", "admin", "finance"))
                )),
                group("ops", "\u8fd0\u7ef4", "\u6a21\u578b\u8def\u7531\u3001AI \u65e5\u5fd7\u548c\u8fd0\u884c\u914d\u7f6e", 50, List.of(
                        item("ops:openrouter:view", "\u67e5\u770b\u751f\u6210\u53c2\u6570", "openrouter", "\u6a21\u578b\u8def\u7531", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "ops")),
                        item("ops:openrouter:edit", "\u7f16\u8f91\u751f\u6210\u53c2\u6570", "openrouter", "\u6a21\u578b\u8def\u7531", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "ops")),
                        item("ops:openrouter:delete", "\u5220\u9664\u6a21\u578b\u63d0\u4f9b\u5546\u6216\u8def\u7531", "openrouter", "\u6a21\u578b\u8def\u7531", "delete", "\u5220\u9664", RiskLevel.HIGH, roles("super-admin", "admin", "ops")),
                        item("ops:media:view", "查看 AI 媒体中心", "media", "AI 媒体中心", "view", "查看", RiskLevel.MEDIUM, roles("super-admin", "admin", "ops", "content")),
                        item("ops:media:image:edit", "编辑聊天生图策略", "media-image", "聊天生图策略", "edit", "编辑", RiskLevel.HIGH, roles("super-admin", "admin", "ops")),
                        item("ops:media:voice:edit", "编辑语音运行策略", "media-voice", "语音运行策略", "edit", "编辑", RiskLevel.HIGH, roles("super-admin", "admin", "ops")),
                        item("content:voice-template:edit", "编辑角色音色模板", "voice-template", "角色音色模板", "edit", "编辑", RiskLevel.HIGH, roles("super-admin", "admin", "ops", "content")),
                        item("ops:ailog:view", "\u67e5\u770b AI \u65e5\u5fd7", "ai-log", "AI \u65e5\u5fd7", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "ops")),
                        item("ops:ailog:clean", "\u6e05\u7406 AI \u65e5\u5fd7", "ai-log", "AI \u65e5\u5fd7", "clean", "\u6e05\u7406", RiskLevel.HIGH, roles("super-admin", "admin", "ops"))
                        ,item("ops:chat-runtime:view", "\u67e5\u770b\u804a\u5929\u8fd0\u884c\u4e2d\u5fc3", "chat-runtime", "\u804a\u5929\u8fd0\u884c\u4e2d\u5fc3", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "ops"))
                        ,item("ops:chat-runtime:cancel", "\u53d6\u6d88\u804a\u5929\u4efb\u52a1", "chat-runtime", "\u804a\u5929\u8fd0\u884c\u4e2d\u5fc3", "cancel", "\u53d6\u6d88", RiskLevel.HIGH, roles("super-admin", "admin", "ops"))
                        ,item("ops:chat-runtime:delete", "\u786c\u5220\u9664\u804a\u5929\u8fd0\u884c\u4efb\u52a1", "chat-runtime", "\u804a\u5929\u8fd0\u884c\u4e2d\u5fc3", "delete", "\u786c\u5220\u9664", RiskLevel.HIGH, roles("super-admin", "admin", "ops"))
                        ,item("ops:visitor-risk:view", "\u67e5\u770b\u8bbf\u5ba2\u98ce\u9669", "visitor-risk", "\u8bbf\u5ba2\u98ce\u9669", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "ops"))
                        ,item("ops:visitor-risk:delete", "\u786c\u5220\u9664\u8bbf\u5ba2\u8bbe\u5907\u4e0e\u98ce\u9669\u4e8b\u4ef6", "visitor-risk", "\u8bbf\u5ba2\u98ce\u9669", "delete", "\u786c\u5220\u9664", RiskLevel.CRITICAL, roles("super-admin", "admin"))
                )),
                group("conversation", "\u4f1a\u8bdd", "\u4f1a\u8bdd\u8fd0\u884c\u65f6\u7ed1\u5b9a\u548c\u4e16\u754c\u4e66\u5173\u8054", 60, List.of(
                        item("conversation:runtime:view", "\u67e5\u770b\u4f1a\u8bdd\u8fd0\u884c\u65f6\u7ed1\u5b9a", "runtime", "\u8fd0\u884c\u65f6\u7ed1\u5b9a", "view", "\u67e5\u770b", RiskLevel.MEDIUM, roles("super-admin", "admin", "support", "ops")),
                        item("conversation:runtime:edit", "\u7f16\u8f91\u4f1a\u8bdd\u8fd0\u884c\u65f6\u7ed1\u5b9a", "runtime", "\u8fd0\u884c\u65f6\u7ed1\u5b9a", "edit", "\u7f16\u8f91", RiskLevel.HIGH, roles("super-admin", "admin", "support"))
                ))
        );
    }

    public Map<String, RoleTemplate> builtInRoleTemplates() {
        Map<String, RoleTemplate> map = new LinkedHashMap<>();
        map.put("super-admin", new RoleTemplate(
                "super-admin",
                "\u8d85\u7ea7\u7ba1\u7406\u5458",
                "\u62e5\u6709\u6240\u6709\u540e\u53f0\u6743\u9650\uff0c\u7528\u4e8e\u6700\u9ad8\u6743\u9650\u7ba1\u7406\u548c\u5e94\u6025\u515c\u5e95\u3002",
                List.of(ALL_PERMISSION),
                true,
                10
        ));
        map.put("admin", new RoleTemplate(
                "admin",
                "\u517c\u5bb9\u7ba1\u7406\u5458",
                "\u4fdd\u7559\u8001\u7248\u5168\u6743\u9650\u89d2\u8272\uff0c\u907f\u514d\u65e7\u8d26\u53f7\u5347\u7ea7\u540e\u4e22\u5931\u6743\u9650\u3002",
                List.of(ALL_PERMISSION),
                true,
                20
        ));
        map.put("support", new RoleTemplate(
                "support",
                "\u5ba2\u670d",
                "\u5904\u7406\u5de5\u5355\u3001\u67e5\u770b\u7528\u6237\u57fa\u7840\u4fe1\u606f\uff0c\u4ee5\u53ca\u5fc5\u8981\u7684\u4f1a\u8bdd\u8fd0\u884c\u65f6\u7ed1\u5b9a\u3002",
                List.of(
                        "support:ticket:list",
                        "support:ticket:view",
                        "support:ticket:reply",
                        "support:ticket:update",
                        "commerce:user:view",
                        "system:notice:view",
                        "conversation:runtime:view",
                        "conversation:runtime:edit"
                ),
                false,
                30
        ));
        map.put("content", new RoleTemplate(
                "content",
                "\u5185\u5bb9\u5ba1\u6838",
                "\u7ba1\u7406\u89d2\u8272\u3001\u6807\u7b7e\u548c\u4e16\u754c\u4e66\uff0c\u5e76\u5904\u7406\u5185\u5bb9\u5ba1\u6838\u3002",
                List.of(
                        "content:character:view",
                        "content:character:edit",
                        "content:review:view",
                        "content:review:edit",
                        "content:tag:view",
                        "content:tag:edit",
                        "content:illustration:view",
                        "content:illustration:edit",
                        "content:illustration:review",
                        "content:illustration:delete",
                        "content:illustration-key:view",
                        "content:illustration-key:edit",
                        "content:illustration-notice:view",
                        "content:illustration-notice:edit",
                        "content:lorebook:view",
                        "content:lorebook:edit",
                         "content:chat-preset:view",
                         "content:chat-preset:edit",
                         "ops:media:view",
                         "content:voice-template:edit",
                        "system:notice:view",
                        "system:inbox-ad:view"
                ),
                false,
                40
        ));
        map.put("finance", new RoleTemplate(
                "finance",
                "\u8d22\u52a1",
                "\u7ba1\u7406\u5546\u54c1\u3001\u8ba2\u5355\u3001\u652f\u4ed8\u6e20\u9053\u548c\u6743\u76ca\u76f8\u5173\u529f\u80fd\u3002",
                List.of(
                        "commerce:entitlement:view",
                        "commerce:entitlement:edit",
                        "commerce:entitlement-log:view",
                        "commerce:entitlement-log:delete",
                        "commerce:user:view",
                        "commerce:user:edit",
                        "commerce:user:update",
                        "commerce:user:security",
                        "commerce:user:batch-policy",
                        "commerce:user:delete",
                        "commerce:product:view",
                        "commerce:product:edit",
                        "commerce:payment:view",
                        "commerce:payment:edit",
                        "commerce:wallet:view",
                        "commerce:checkin:view",
                        "commerce:checkin:edit",
                        "commerce:order:view",
                        "commerce:order:edit",
                        "system:notice:view"
                ),
                false,
                50
        ));
        map.put("ops", new RoleTemplate(
                "ops",
                "\u6280\u672f\u8fd0\u7ef4",
                "\u7ba1\u7406\u6a21\u578b\u8def\u7531\u3001AI \u65e5\u5fd7\u548c\u5fc5\u8981\u7684\u7cfb\u7edf\u67e5\u770b\u6743\u9650\u3002",
                List.of(
                        "ops:openrouter:view",
                        "ops:openrouter:edit",
                         "ops:openrouter:delete",
                         "ops:media:view",
                         "ops:media:image:edit",
                         "ops:media:voice:edit",
                         "content:voice-template:edit",
                        "ops:ailog:view",
                        "ops:ailog:clean",
                        "ops:chat-runtime:view",
                        "ops:chat-runtime:cancel",
                        "ops:chat-runtime:delete",
                        "ops:visitor-risk:view",
                        "system:notice:view",
                        "system:inbox-ad:view",
                        "system:inbox-ad:edit",
                        "conversation:runtime:view"
                ),
                false,
                60
        ));
        return map;
    }

    public Map<String, List<String>> builtInRolePermissions() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (RoleTemplate template : builtInRoleTemplates().values()) {
            map.put(template.key(), template.permissionKeys());
        }
        return map;
    }

    public List<PermissionItem> permissionItems() {
        return permissionGroups().stream()
                .flatMap(group -> group.items().stream())
                .toList();
    }

    public Map<String, PermissionItem> permissionItemMap() {
        Map<String, PermissionItem> map = new LinkedHashMap<>();
        for (PermissionItem item : permissionItems()) {
            map.put(item.key(), item);
        }
        return map;
    }

    public Set<String> allPermissionKeys() {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (PermissionItem item : permissionItems()) {
            all.add(item.key());
        }
        all.add(ALL_PERMISSION);
        return all;
    }

    public boolean isValidPermission(String permission) {
        return permission != null && allPermissionKeys().contains(permission);
    }

    private static PermissionGroup group(String key, String label, String description, int sortOrder, List<PermissionItem> items) {
        return new PermissionGroup(key, label, description, sortOrder, items);
    }

    private static PermissionItem item(
            String key,
            String label,
            String pageKey,
            String pageLabel,
            String actionKey,
            String actionLabel,
            RiskLevel riskLevel,
            List<String> recommendedRoleKeys
    ) {
        return new PermissionItem(key, label, pageKey, pageLabel, actionKey, actionLabel, riskLevel, label, recommendedRoleKeys);
    }

    private static List<String> roles(String... roleKeys) {
        return Arrays.asList(roleKeys);
    }

    public enum RiskLevel {
        LOW("\u4f4e", 10),
        MEDIUM("\u4e2d", 20),
        HIGH("\u9ad8", 30),
        CRITICAL("\u6781\u9ad8", 40);

        private final String label;
        private final int sortOrder;

        RiskLevel(String label, int sortOrder) {
            this.label = label;
            this.sortOrder = sortOrder;
        }

        public String label() {
            return label;
        }

        public int sortOrder() {
            return sortOrder;
        }
    }

    public record PermissionGroup(
            String key,
            String label,
            String description,
            int sortOrder,
            List<PermissionItem> items
    ) {
        public PermissionGroup(String key, String label, List<PermissionItem> items) {
            this(key, label, "", 0, items);
        }

        public PermissionGroup {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            description = description == null ? "" : description;
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record PermissionItem(
            String key,
            String label,
            String pageKey,
            String pageLabel,
            String actionKey,
            String actionLabel,
            RiskLevel riskLevel,
            String description,
            List<String> recommendedRoleKeys
    ) {
        public PermissionItem(String key, String label) {
            this(key, label, "", "", "", "", RiskLevel.MEDIUM, label, List.of());
        }

        public PermissionItem {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            pageKey = pageKey == null ? "" : pageKey;
            pageLabel = pageLabel == null ? "" : pageLabel;
            actionKey = actionKey == null ? "" : actionKey;
            actionLabel = actionLabel == null ? "" : actionLabel;
            riskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
            description = description == null ? "" : description;
            recommendedRoleKeys = recommendedRoleKeys == null ? List.of() : List.copyOf(recommendedRoleKeys);
        }
    }

    public record RoleTemplate(
            String key,
            String label,
            String description,
            List<String> permissionKeys,
            boolean fullAccess,
            int sortOrder
    ) {
        public RoleTemplate {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            description = description == null ? "" : description;
            permissionKeys = permissionKeys == null ? List.of() : List.copyOf(permissionKeys);
        }
    }
}
