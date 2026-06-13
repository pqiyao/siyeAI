package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.admin.security.RuoYiAdminAccessService;
import com.example.sillyspringboot.admin.security.RuoYiAdminJwtService;
import com.example.sillyspringboot.admin.service.AdminIdentityService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class RuoYiShellAuthController {

    private final RuoYiAdminProperties props;
    private final RuoYiAdminJwtService jwtService;
    private final RuoYiAdminAccessService accessService;
    private final AdminIdentityService identityService;

    public RuoYiShellAuthController(
            RuoYiAdminProperties props,
            RuoYiAdminJwtService jwtService,
            RuoYiAdminAccessService accessService,
            AdminIdentityService identityService
    ) {
        this.props = props;
        this.jwtService = jwtService;
        this.accessService = accessService;
        this.identityService = identityService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        if (props.isCaptchaEnabled()) {
            return AdminAjaxResult.error("Captcha is not enabled on this admin portal.");
        }
        RuoYiAdminAccessService.AdminSession session = accessService.authenticate(username, password);
        if (session == null) {
            return AdminAjaxResult.error("Invalid username or password");
        }
        String token = jwtService.createToken(session.getUsername());
        identityService.recordLoginSuccess(session.getId(), request == null ? null : request.getRemoteAddr());
        Map<String, Object> result = AdminAjaxResult.ok("Login success");
        result.put("token", token);
        return result;
    }

    @GetMapping("/captchaImage")
    public Map<String, Object> captchaImage() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("captchaEnabled", false);
        return result;
    }

    @GetMapping("/getInfo")
    public Map<String, Object> getInfo(HttpServletRequest request) {
        RuoYiAdminAccessService.AdminSession session = sessionOf(request);
        if (session == null) {
            return AdminAjaxResult.unauthorized("Not logged in");
        }

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", session.getId());
        user.put("userName", session.getUsername());
        user.put("nickName", session.getNickName());
        user.put("avatar", "");

        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("user", user);
        result.put("roles", session.getRoles());
        result.put("permissions", session.getPermissions());
        result.put("isDefaultModifyPwd", false);
        result.put("isPasswordExpired", false);
        return result;
    }

    @GetMapping("/getRouters")
    public Map<String, Object> getRouters(HttpServletRequest request) {
        RuoYiAdminAccessService.AdminSession session = sessionOf(request);
        if (session == null) {
            return AdminAjaxResult.unauthorized("Not logged in");
        }

        List<Map<String, Object>> routes = new ArrayList<>();

        Map<String, Object> contentOps = routeParent("JiugaiContent", "/jiugai/content", "\u5185\u5bb9\u8fd0\u8425", "component");
        List<Map<String, Object>> contentChildren = new ArrayList<>();
        contentChildren.add(routeChild("JgCharacter", "character", "jiugai/character/index", "\u89d2\u8272\u7ba1\u7406", "user", "content:character:view"));
        contentChildren.add(routeChild("JgCharacterReviewLog", "reviewlog", "jiugai/characterreviewlog/index", "\u5ba1\u6838\u65e5\u5fd7", "clipboard", "content:review:view"));
        contentChildren.add(routeChild("JgTagLibrary", "taglibrary", "jiugai/taglibrary/index", "\u6807\u7b7e\u5e93", "dict", "content:tag:view"));
        contentChildren.add(routeChild("JgLorebook", "lorebook", "jiugai/lorebook/index", "\u4e16\u754c\u4e66", "documentation", "content:lorebook:view"));
        contentChildren.add(routeChild("JgOpenRouter", "openrouter", "jiugai/openrouter/index", "\u6a21\u578b\u8def\u7531", "server", "ops:openrouter:view"));
        contentChildren.add(routeChild("JgAiLog", "ailog", "jiugai/ailog/index", "AI \u65e5\u5fd7", "log", "ops:ailog:view"));
        contentOps.put("children", filterChildrenByPermission(contentChildren, session.getPermissions()));

        Map<String, Object> illustrationOps = routeParent("JiugaiIllustrationSite", "/jiugai/illustration", "\u63d2\u753b\u7f51\u7ad9", "color");
        List<Map<String, Object>> illustrationChildren = new ArrayList<>();
        illustrationChildren.add(routeChild("JgIllustrationWork", "work", "jiugai/illustrationwork/index", "\u63d2\u753b\u4f5c\u54c1", "theme", "content:illustration:view"));
        illustrationChildren.add(routeChild("JgIllustrationAccessKey", "accesskey", "jiugai/illustrationkey/index", "18+\u5bc6\u94a5", "lock", "content:illustration-key:view"));
        illustrationChildren.add(routeChild("JgIllustrationNotice", "notice", "jiugai/illustrationnotice/index", "\u901a\u77e5\u7ba1\u7406", "bell", "content:illustration-notice:view"));
        illustrationOps.put("children", filterChildrenByPermission(illustrationChildren, session.getPermissions()));

        Map<String, Object> commerceOps = routeParent("JiugaiCommerce", "/jiugai/commerce", "\u5546\u4e1a\u8fd0\u8425", "chart");
        List<Map<String, Object>> commerceChildren = new ArrayList<>();
        commerceChildren.add(routeChild("JgEntitlement", "entitlement", "jiugai/entitlement/index", "\u6743\u76ca\u7b56\u7565", "star", "commerce:entitlement:view"));
        commerceChildren.add(routeChild("JgEntitlementLog", "entitlementlog", "jiugai/entitlementlog/index", "\u6743\u76ca\u65e5\u5fd7", "clipboard", "commerce:entitlement-log:view"));
        commerceChildren.add(routeChild("JgH5User", "h5user", "jiugai/h5user/index", "\u7528\u6237\u7ba1\u7406", "people", "commerce:user:view"));
        commerceChildren.add(routeChild("JgStoreProduct", "storeproduct", "jiugai/storeproduct/index", "\u5546\u54c1\u7ba1\u7406", "shopping", "commerce:product:view"));
        commerceChildren.add(routeChild("JgPaymentChannel", "paymentchannel", "jiugai/paymentchannel/index", "\u652f\u4ed8\u6e20\u9053", "money", "commerce:payment:view"));
        commerceChildren.add(routeChild("JgStoreOrder", "storeorder", "jiugai/storeorder/index", "\u8ba2\u5355\u7ba1\u7406", "money", "commerce:order:view"));
        commerceChildren.add(routeChild("JgSupportTicket", "supportticket", "jiugai/supportticket/index", "\u5ba2\u670d\u5de5\u5355", "message", "support:ticket:list"));
        commerceOps.put("children", filterChildrenByPermission(commerceChildren, session.getPermissions()));

        Map<String, Object> systemOps = routeParent("JiugaiSystem", "/jiugai/system", "\u7cfb\u7edf\u7ba1\u7406", "system");
        List<Map<String, Object>> systemChildren = new ArrayList<>();
        systemChildren.add(routeChild("JgAdminAccount", "adminaccount", "jiugai/adminaccount/index", "\u7ba1\u7406\u5458\u8d26\u53f7", "user", "system:admin-user:view"));
        systemChildren.add(routeChild("JgAdminRole", "adminrole", "jiugai/adminrole/index", "\u7ba1\u7406\u5458\u89d2\u8272", "peoples", "system:admin-role:view"));
        systemChildren.add(routeChild("JgNotice", "notice", "jiugai/notice/index", "\u7cfb\u7edf\u516c\u544a", "bell", "system:notice:view"));
        systemChildren.add(routeChild("JgPermissionLog", "permissionlog", "jiugai/permissionlog/index", "\u6743\u9650\u53d8\u66f4\u65e5\u5fd7", "log", "system:permission-log:view", "system:admin-role:view", "system:admin-user:view"));
        systemOps.put("children", filterChildrenByPermission(systemChildren, session.getPermissions()));

        Map<String, Object> socialOps = routeParent("JiugaiSocial", "/jiugai/social", "\u793e\u4ea4\u4e2d\u5fc3", "social");
        List<Map<String, Object>> socialChildren = new ArrayList<>();
        socialChildren.add(routeChild("JgSocialSettings", "socialsettings", "jiugai/socialsettings/index", "\u793e\u4ea4\u8bbe\u7f6e", "switch", "social:settings:view"));
        socialChildren.add(routeChild("JgSocialPost", "socialpost", "jiugai/socialpost/index", "\u793e\u533a\u52a8\u6001", "post", "social:community:view"));
        socialChildren.add(routeChild("JgSocialComment", "socialcomment", "jiugai/socialcomment/index", "\u793e\u533a\u8bc4\u8bba", "message", "social:community:view"));
        socialChildren.add(routeChild("JgHumanChatConversation", "humanchatconversation", "jiugai/humanchatconversation/index", "\u771f\u4eba\u804a\u5929\u4f1a\u8bdd", "people", "social:chat-conversation:view"));
        socialChildren.add(routeChild("JgHumanChatMessage", "humanchatmessage", "jiugai/humanchatmessage/index", "\u771f\u4eba\u804a\u5929\u6d88\u606f", "message", "social:chat-message:view"));
        socialChildren.add(routeChild("JgHumanChatDeliveryLog", "humanchatdeliverylog", "jiugai/humanchatdeliverylog/index", "\u6295\u9012\u65e5\u5fd7", "log", "social:chat-delivery-log:view"));
        socialOps.put("children", filterChildrenByPermission(socialChildren, session.getPermissions()));

        if (!((List<?>) contentOps.get("children")).isEmpty()) {
            routes.add(contentOps);
        }
        if (!((List<?>) illustrationOps.get("children")).isEmpty()) {
            routes.add(illustrationOps);
        }
        if (!((List<?>) commerceOps.get("children")).isEmpty()) {
            routes.add(commerceOps);
        }
        if (!((List<?>) socialOps.get("children")).isEmpty()) {
            routes.add(socialOps);
        }
        if (!((List<?>) systemOps.get("children")).isEmpty()) {
            routes.add(systemOps);
        }

        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", routes);
        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        return AdminAjaxResult.ok("Logout success");
    }

    @PostMapping("/register")
    public Map<String, Object> register() {
        return AdminAjaxResult.error("Admin self-registration is disabled");
    }

    @PostMapping("/unlockscreen")
    public Map<String, Object> unlockScreen(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String password = body == null ? null : body.get("password");
        RuoYiAdminAccessService.AdminSession session = sessionOf(request);
        if (session == null || accessService.authenticate(session.getUsername(), password) == null) {
            return AdminAjaxResult.error("Password mismatch");
        }
        return AdminAjaxResult.ok();
    }

    private List<Map<String, Object>> filterChildrenByPermission(
            List<Map<String, Object>> routes,
            List<String> permissions
    ) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> route : routes) {
            Object value = route.get("permissions");
            if (!(value instanceof List<?> required) || required.isEmpty()) {
                filtered.add(route);
                continue;
            }
            List<String> requiredPermissions = required.stream().map(String::valueOf).toList();
            if (accessService.hasAnyPermission(permissions, requiredPermissions)) {
                filtered.add(route);
            }
        }
        return filtered;
    }

    private static Map<String, Object> routeParent(String name, String path, String title, String icon) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("name", name);
        route.put("path", path);
        route.put("hidden", false);
        route.put("redirect", "noRedirect");
        route.put("component", "Layout");
        route.put("alwaysShow", true);
        route.put("meta", routeMeta(title, icon));
        return route;
    }

    private static Map<String, Object> routeChild(
            String name,
            String path,
            String component,
            String title,
            String icon,
            String... permissions
    ) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("name", name);
        route.put("path", path);
        route.put("hidden", false);
        route.put("component", component);
        route.put("meta", routeMeta(title, icon));
        route.put("permissions", permissions == null ? List.of() : List.of(permissions));
        return route;
    }

    private static Map<String, Object> routeMeta(String title, String icon) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title);
        meta.put("icon", icon);
        meta.put("noCache", false);
        meta.put("link", null);
        return meta;
    }

    private static RuoYiAdminAccessService.AdminSession sessionOf(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("adminSession");
        return value instanceof RuoYiAdminAccessService.AdminSession session ? session : null;
    }
}
