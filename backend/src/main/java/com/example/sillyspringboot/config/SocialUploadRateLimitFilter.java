package com.example.sillyspringboot.config;

import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

public class SocialUploadRateLimitFilter extends OncePerRequestFilter {

    private final SocialUploadRateLimiter rateLimiter;

    public SocialUploadRateLimitFilter(SocialUploadRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = normalizePath(request.getRequestURI());
        return !isUploadPath(path) && !isSocialWritePath(path, request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = normalizePath(request.getRequestURI());
        try {
            if (isUploadPath(path)) {
                rateLimiter.checkGatewayUpload(request, uploadAction(path));
            }
            if (isSocialWritePath(path, request.getMethod())) {
                rateLimiter.checkGatewaySocialWrite(request, socialWriteAction(path));
            }
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.RATE_LIMITED) {
                writeTooManyRequests(response);
                return;
            }
            throw ex;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isUploadPath(String path) {
        return "/api/common/upload".equals(path)
                || "/api/app/chat/upload-image".equals(path)
                || "/api/app/illustrations/works/submissions".equals(path)
                || "/api/v1/support/upload-image".equals(path)
                || "/api/v1/characters/mine/upload-image".equals(path)
                || "/api/v1/characters/mine/import-sillytavern-png".equals(path)
                || "/api/v1/tavern/chat/transcribe-audio".equals(path);
    }

    private static boolean isSocialWritePath(String path, String method) {
        if (!isWriteMethod(method)) {
            return false;
        }
        return path.startsWith("/api/community/") || path.startsWith("/api/social-chat/");
    }

    private static boolean isWriteMethod(String method) {
        String safeMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        return "POST".equals(safeMethod)
                || "PUT".equals(safeMethod)
                || "PATCH".equals(safeMethod)
                || "DELETE".equals(safeMethod);
    }

    private static String uploadAction(String path) {
        if (path == null || path.isBlank()) {
            return "upload";
        }
        return "gateway_" + path.substring(path.lastIndexOf('/') + 1);
    }

    private static String socialWriteAction(String path) {
        if (path == null || path.isBlank()) {
            return "social_write";
        }
        if (path.startsWith("/api/social-chat/")) {
            return "social_chat";
        }
        return "community";
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String safe = path.trim();
        int semicolon = safe.indexOf(';');
        return semicolon >= 0 ? safe.substring(0, semicolon) : safe;
    }

    private static void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"Too many requests. Please try again later.\",\"data\":null}");
    }
}
