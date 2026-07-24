package com.example.sillyspringboot.config;

import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class VerifiedDeviceRateLimitFilter extends OncePerRequestFilter {

    private final ApiRateLimitProperties properties;
    private final ApiRateLimitCounterStore counterStore;
    private final AppH5SecurityEventMapper securityEvents;
    private final ClientIpResolver clientIpResolver;

    public VerifiedDeviceRateLimitFilter(
            ApiRateLimitProperties properties,
            ApiRateLimitCounterStore counterStore,
            AppH5SecurityEventMapper securityEvents,
            ClientIpResolver clientIpResolver
    ) {
        this.properties = properties;
        this.counterStore = counterStore;
        this.securityEvents = securityEvents;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (!isH5CompatibilityPath(path)) {
            return true;
        }
        return "/api/v1/app/runtime/status".equals(path);
    }

    private static boolean isH5CompatibilityPath(String path) {
        return path != null && (
                path.startsWith("/api/v1/")
                        || path.startsWith("/api/index/")
                        || path.startsWith("/api/common/")
                        || path.startsWith("/api/user/")
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        VerifiedDevice device = resolveVerifiedDevice(request);
        if (device == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String counterKey = request.getMethod() + ":device:" + device.id();
        int used = counterStore.increment(counterKey, properties.getWindowSeconds());
        if (used > properties.getMaxRequestsPerWindow()) {
            String resolvedIp = clientIpResolver.resolve(request);
            if (securityEvents != null && shouldRecordLimitEvent(request, device.id(), resolvedIp)) {
                securityEvents.insert(
                        device.id(),
                        "RATE_LIMIT_HIT",
                        H5VisitorDeviceService.resolveClientUid(request),
                        null,
                        resolvedIp,
                        H5VisitorDeviceService.hashUserAgent(request.getHeader("User-Agent")),
                        request.getRequestURI(),
                        "scope=device;method=" + request.getMethod()
                );
            }
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldRecordLimitEvent(HttpServletRequest request, long deviceId, String resolvedIp) {
        String eventKey = "security-event:device:"
                + deviceId + ':'
                + safeSegment(resolvedIp) + ':'
                + safeSegment(request.getMethod()) + ':'
                + safeSegment(request.getRequestURI());
        return counterStore.increment(eventKey, properties.getSecurityEventDedupSeconds()) == 1;
    }

    private static String safeSegment(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private VerifiedDevice resolveVerifiedDevice(HttpServletRequest request) {
        Object rawId = request.getAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_ID);
        Object rawToken = request.getAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_TOKEN);
        if (!(rawId instanceof Number number) || number.longValue() <= 0L) {
            return null;
        }
        if (!(rawToken instanceof String token) || token.isBlank()) {
            return null;
        }
        return new VerifiedDevice(number.longValue());
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}");
    }

    private record VerifiedDevice(long id) {}
}
