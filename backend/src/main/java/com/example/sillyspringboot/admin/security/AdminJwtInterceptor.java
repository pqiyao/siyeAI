package com.example.sillyspringboot.admin.security;

import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminJwtInterceptor implements HandlerInterceptor {

    private final RuoYiAdminJwtService jwtService;
    private final RuoYiAdminAccessService accessService;
    private final ObjectMapper objectMapper;

    public AdminJwtInterceptor(
            RuoYiAdminJwtService jwtService,
            RuoYiAdminAccessService accessService,
            ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = bearerToken(request.getHeader("Authorization"));
        if (token == null || token.isBlank()) {
            writeJson(response, HttpServletResponse.SC_OK, AdminAjaxResult.unauthorized("未登录"));
            return false;
        }
        try {
            String username = jwtService.parseUsername(token);
            RuoYiAdminAccessService.AdminSession session = accessService.loadSession(username);
            if (session == null) {
                writeJson(response, HttpServletResponse.SC_OK, AdminAjaxResult.unauthorized("登录已失效"));
                return false;
            }
            request.setAttribute("adminUsername", session.getUsername());
            request.setAttribute("adminRoles", session.getRoles());
            request.setAttribute("adminPermissions", session.getPermissions());
            request.setAttribute("adminSession", session);

            if (handler instanceof HandlerMethod handlerMethod) {
                AdminPermitted permitted = handlerMethod.getMethodAnnotation(AdminPermitted.class);
                if (permitted == null) {
                    permitted = handlerMethod.getBeanType().getAnnotation(AdminPermitted.class);
                }
                if (permitted != null && !accessService.hasAnyPermission(session.getPermissions(), permitted.value())) {
                    writeJson(response, HttpServletResponse.SC_OK, AdminAjaxResult.forbidden("没有权限"));
                    return false;
                }
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            writeJson(response, HttpServletResponse.SC_OK, AdminAjaxResult.unauthorized("登录已失效"));
            return false;
        }
    }

    private void writeJson(HttpServletResponse response, int status, Object payload) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), payload);
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }
}
