package com.example.sillyspringboot.app.security;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class CurrentAppUserResolver {

    private final AppTokenService tokenService;

    public CurrentAppUserResolver(AppTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public AppUser requireUser(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return tokenService.validateAndLoadUser(token);
    }

    public AppUser optionalUser(HttpServletRequest request) {
        String token = resolveToken(request);
        return token == null ? null : tokenService.validateAndLoadUser(token);
    }

    public String resolveToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String bearer = extractBearer(request.getHeader("Authorization"));
        if (bearer != null) {
            return bearer;
        }
        String headerToken = trimToNull(request.getHeader("token"));
        if (headerToken != null) {
            return headerToken;
        }
        return trimToNull(request.getParameter("token"));
    }

    private static String extractBearer(String authorization) {
        String value = trimToNull(authorization);
        if (value == null) {
            return null;
        }
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimToNull(value.substring(7));
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
