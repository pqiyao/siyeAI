package com.example.sillyspringboot.config;

import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final ApiRateLimitProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, WindowCounter> localCounters = new ConcurrentHashMap<>();

    public ApiRateLimitFilter(ApiRateLimitProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return true;
        }
        return "/api/v1/app/runtime/status".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String subject = resolveSubject(request);
        String counterKey = request.getMethod() + ":" + subject;
        int used = redisTemplate != null
                ? incrementRedisCounter(counterKey)
                : incrementLocalCounter(counterKey);

        if (used > properties.getMaxRequestsPerWindow()) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private int incrementRedisCounter(String counterKey) {
        String key = "api:rate-limit:" + counterKey;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
        }
        return current == null ? 1 : current.intValue();
    }

    private int incrementLocalCounter(String counterKey) {
        long now = System.currentTimeMillis();
        long ttl = properties.getWindowSeconds() * 1000L;
        WindowCounter counter = localCounters.compute(counterKey, (key, old) -> {
            if (old == null || now >= old.expiresAt) {
                return new WindowCounter(now + ttl, new AtomicInteger(1));
            }
            old.count.incrementAndGet();
            return old;
        });
        return counter.count.get();
    }

    private String resolveSubject(HttpServletRequest request) {
        String deviceToken = H5VisitorDeviceService.resolveDeviceToken(request);
        if (!deviceToken.isEmpty()) {
            return "device:" + deviceToken;
        }
        String clientUid = H5VisitorDeviceService.resolveClientUid(request);
        if (!clientUid.isEmpty()) {
            return "client:" + clientUid
                    + "|ip:" + safeSegment(H5VisitorDeviceService.resolveClientIp(request))
                    + "|ua:" + safeSegment(H5VisitorDeviceService.hashUserAgent(request.getHeader("User-Agent")));
        }
        String ip = safeSegment(H5VisitorDeviceService.resolveClientIp(request));
        String uaHash = safeSegment(H5VisitorDeviceService.hashUserAgent(request.getHeader("User-Agent")));
        return "ip:" + ip + "|ua:" + uaHash;
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

    private record WindowCounter(long expiresAt, AtomicInteger count) {}
}
