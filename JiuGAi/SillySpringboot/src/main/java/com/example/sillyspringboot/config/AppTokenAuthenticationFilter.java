package com.example.sillyspringboot.config;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.shared.error.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class AppTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final List<SimpleGrantedAuthority> APP_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_APP_USER"));

    private final AppTokenService tokenService;

    public AppTokenAuthenticationFilter(AppTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !requiresAppToken(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token.isBlank()) {
            writeUnauthorized(response);
            return;
        }
        try {
            AppUser user = tokenService.validateAndLoadUser(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, token, APP_AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (BusinessException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
        }
    }

    private boolean requiresAppToken(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }
        String path = normalizedPath(request);
        if (!path.startsWith("/api/app/")) {
            return false;
        }
        return !isPublicAppPath(path, request.getMethod());
    }

    private boolean isPublicAppPath(String path, String method) {
        if (HttpMethod.GET.matches(method) && "/api/app/ping".equals(path)) {
            return true;
        }
        if (HttpMethod.POST.matches(method)
                && ("/api/app/auth/telegram/login".equals(path)
                || "/api/app/auth/h5/login".equals(path)
                || "/api/app/auth/h5/register".equals(path)
                || "/api/app/auth/h5/password-reset/request".equals(path)
                || "/api/app/auth/h5/password-reset/confirm".equals(path))) {
            return true;
        }
        if (HttpMethod.GET.matches(method)
                && (path.startsWith("/api/app/discover/")
                || path.matches("^/api/app/characters/\\d+$")
                || "/api/app/illustrations/works".equals(path)
                || path.startsWith("/api/app/illustrations/works/")
                || "/api/app/illustrations/access-key/validate".equals(path)
                || "/api/app/illustrations/notices".equals(path))) {
            return true;
        }
        return HttpMethod.POST.matches(method)
                && "/api/app/illustrations/works/submissions".equals(path);
    }

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri != null && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri == null ? "" : uri;
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = extractBearerToken(request.getHeader("Authorization"));
        if (!bearer.isBlank()) {
            return bearer;
        }
        String token = request.getHeader("token");
        if (token == null || token.isBlank()) {
            token = request.getHeader("Token");
        }
        return token == null ? "" : token.trim();
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return "";
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"unauthorized\",\"data\":null}");
    }
}
