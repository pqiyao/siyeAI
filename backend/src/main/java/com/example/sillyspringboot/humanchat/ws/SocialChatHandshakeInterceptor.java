package com.example.sillyspringboot.humanchat.ws;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.config.WebSocketSecurityProperties;
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class SocialChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "appUserId";

    private final AppTokenService tokenService;
    private final SocialUploadRateLimiter rateLimiter;
    private final WebSocketSecurityProperties properties;
    private final List<String> allowedOriginPatterns;

    public SocialChatHandshakeInterceptor(
            AppTokenService tokenService,
            SocialUploadRateLimiter rateLimiter,
            WebSocketSecurityProperties properties,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String corsOriginPatterns
    ) {
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.allowedOriginPatterns = parsePatterns(firstNonBlank(properties.getAllowedOriginPatterns(), corsOriginPatterns));
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!isAllowedOrigin(request)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        try {
            rateLimiter.checkWebSocketHandshake("ip:" + resolveClientIp(request));
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.RATE_LIMITED) {
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return false;
            }
            throw ex;
        }
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            if (properties.isRequireHandshakeToken()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            return true;
        }

        try {
            AppUser user = tokenService.validateAndLoadUser(token);
            if (user == null || user.getId() == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            rateLimiter.checkWebSocketHandshake("user:" + user.getId());
            attributes.put(ATTR_USER_ID, user.getId());
            return true;
        } catch (BusinessException ex) {
            response.setStatusCode(ex.getErrorCode() == ErrorCode.RATE_LIMITED
                    ? HttpStatus.TOO_MANY_REQUESTS
                    : HttpStatus.UNAUTHORIZED);
            return false;
        } catch (Exception ignored) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    public String[] allowedOriginPatterns() {
        if (allowedOriginPatterns.isEmpty()) {
            return new String[]{"*"};
        }
        return allowedOriginPatterns.toArray(String[]::new);
    }

    private boolean isAllowedOrigin(ServerHttpRequest request) {
        String origin = request.getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            return properties.isAllowMissingOrigin();
        }
        CorsConfiguration config = new CorsConfiguration();
        List<String> patterns = allowedOriginPatterns.isEmpty() ? List.of("*") : allowedOriginPatterns;
        patterns.forEach(config::addAllowedOriginPattern);
        return config.checkOrigin(origin) != null;
    }

    private static String resolveToken(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        String bearer = extractBearer(authorization);
        if (bearer != null) {
            return bearer;
        }
        String tokenHeader = trimToNull(headers.getFirst("token"));
        if (tokenHeader != null) {
            return tokenHeader;
        }
        var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String token = trimToNull(params.getFirst("token"));
        if (token != null) {
            return token;
        }
        return trimToNull(params.getFirst("access_token"));
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

    private static String resolveClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String forwarded = firstNonBlank(
                headers.getFirst("X-Forwarded-For"),
                headers.getFirst("X-Real-IP")
        );
        if (!forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return safeIp(comma >= 0 ? forwarded.substring(0, comma) : forwarded);
        }
        InetSocketAddress address = request.getRemoteAddress();
        return safeIp(address == null || address.getAddress() == null
                ? "unknown"
                : address.getAddress().getHostAddress());
    }

    private static String safeIp(String value) {
        String safe = trimToNull(value);
        return safe == null ? "unknown" : safe;
    }

    private static List<String> parsePatterns(String csv) {
        return Arrays.stream((csv == null ? "" : csv).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
