package com.example.sillyspringboot.config;

import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final ApiRateLimitProperties properties;
    private final ApiRateLimitCounterStore counterStore;
    private final AppH5SecurityEventMapper securityEvents;
    private final ClientIpResolver clientIpResolver;
    private final H5VisitorDeviceService visitorDeviceService;

    public ApiRateLimitFilter(
            ApiRateLimitProperties properties,
            ApiRateLimitCounterStore counterStore,
            AppH5SecurityEventMapper securityEvents,
            ClientIpResolver clientIpResolver,
            H5VisitorDeviceService visitorDeviceService
    ) {
        this.properties = properties;
        this.counterStore = counterStore;
        this.securityEvents = securityEvents;
        this.clientIpResolver = clientIpResolver;
        this.visitorDeviceService = visitorDeviceService;
    }

    public ApiRateLimitFilter(
            ApiRateLimitProperties properties,
            ApiRateLimitCounterStore counterStore,
            AppH5SecurityEventMapper securityEvents,
            ClientIpResolver clientIpResolver
    ) {
        this(properties, counterStore, securityEvents, clientIpResolver, null);
    }

    ApiRateLimitFilter(ApiRateLimitProperties properties, StringRedisTemplate redisTemplate) {
        this(properties, new ApiRateLimitCounterStore(redisTemplate), null, new ClientIpResolver(properties), null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (!isProtectedApiPath(path)) {
            return true;
        }
        return "/api/v1/app/runtime/status".equals(path);
    }

    private static boolean isProtectedApiPath(String path) {
        return path != null && (
                path.startsWith("/api/v1/")
                        || path.startsWith("/api/app/")
                        || path.startsWith("/api/index/")
                        || path.startsWith("/api/common/")
                        || path.startsWith("/api/user/")
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String resolvedIp = clientIpResolver.resolve(request);
        String ip = safeSegment(resolvedIp);
        int ipUsed = incrementCounter(method + ":ip:" + ip);
        int clientUsed = incrementCounter(method + ":client:" + resolveClientSubject(request, ip));

        if (ipUsed > properties.getMaxRequestsPerIpWindow()
                || clientUsed > properties.getMaxRequestsPerWindow()) {
            if (securityEvents != null && shouldRecordLimitEvent(request, resolvedIp)) {
                Long deviceId = visitorDeviceService == null
                        ? null
                        : visitorDeviceService.resolvePersistedDeviceId(request);
                securityEvents.insert(deviceId, "RATE_LIMIT_HIT", H5VisitorDeviceService.resolveClientUid(request), null,
                        resolvedIp, H5VisitorDeviceService.hashUserAgent(request.getHeader("User-Agent")),
                        request.getRequestURI(), networkLimitDetail(request, ipUsed, clientUsed));
            }
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldRecordLimitEvent(HttpServletRequest request, String resolvedIp) {
        String eventKey = "security-event:network:"
                + safeSegment(resolvedIp) + ':'
                + safeSegment(request.getMethod()) + ':'
                + safeSegment(request.getRequestURI());
        return counterStore.increment(eventKey, properties.getSecurityEventDedupSeconds()) == 1;
    }

    private int incrementCounter(String counterKey) {
        return counterStore.increment(counterKey, properties.getWindowSeconds());
    }

    private String resolveClientSubject(HttpServletRequest request, String ip) {
        String uaHash = safeSegment(H5VisitorDeviceService.hashUserAgent(request.getHeader("User-Agent")));
        return "ip:" + ip + "|ua:" + uaHash;
    }

    private String networkLimitDetail(HttpServletRequest request, int ipUsed, int clientUsed) {
        StringBuilder scopes = new StringBuilder();
        if (ipUsed > properties.getMaxRequestsPerIpWindow()) {
            scopes.append("ip");
        }
        if (clientUsed > properties.getMaxRequestsPerWindow()) {
            if (!scopes.isEmpty()) {
                scopes.append(',');
            }
            scopes.append("ip+ua");
        }
        return "scope=" + scopes + ";method=" + request.getMethod();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}");
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeSegment(String value) {
        String normalized = trimToEmpty(value);
        return normalized.isEmpty() ? "unknown" : normalized;
    }
}
