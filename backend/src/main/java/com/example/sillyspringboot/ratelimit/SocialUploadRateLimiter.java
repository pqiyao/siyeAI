package com.example.sillyspringboot.ratelimit;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.config.SocialUploadRateLimitProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SocialUploadRateLimiter {

    private final SocialUploadRateLimitProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, WindowCounter> localCounters = new ConcurrentHashMap<>();

    public SocialUploadRateLimiter(
            SocialUploadRateLimitProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public void checkUpload(AppUser user, HttpServletRequest request, String action) {
        String subject = subject(user, request);
        check("upload:total", subject, properties.getUploadTotal());
        check("upload:" + safeSegment(action), subject, properties.getUpload());
    }

    public void checkUpload(HttpServletRequest request, String action) {
        checkUpload((AppUser) null, request, action);
    }

    public void checkUpload(long userId, String action) {
        checkUpload("user:" + userId, action);
    }

    public void checkUpload(String subject, String action) {
        String safeSubject = safeSubject(subject);
        check("upload:total", safeSubject, properties.getUploadTotal());
        check("upload:" + safeSegment(action), safeSubject, properties.getUpload());
    }

    public void checkSocialWrite(AppUser user, HttpServletRequest request, String action) {
        String subject = subject(user, request);
        checkSocialWrite(subject, action);
    }

    public void checkSocialWrite(HttpServletRequest request, String action) {
        checkSocialWrite(subject(null, request), action);
    }

    public void checkSocialWrite(String subject, String action) {
        String safeSubject = safeSubject(subject);
        check("social:write:total", safeSubject, properties.getSocialWriteTotal());
        check("social:write:" + safeSegment(action), safeSubject, properties.getSocialWrite());
    }

    public void checkGatewayUpload(HttpServletRequest request, String action) {
        String subject = subject(null, request);
        check("gateway:upload:total", subject, properties.getGatewayUploadTotal());
        check("gateway:upload:" + safeSegment(action), subject, properties.getGatewayUpload());
    }

    public void checkGatewaySocialWrite(HttpServletRequest request, String action) {
        String subject = subject(null, request);
        check("gateway:social:write:total", subject, properties.getGatewaySocialWriteTotal());
        check("gateway:social:write:" + safeSegment(action), subject, properties.getGatewaySocialWrite());
    }

    public void checkWebSocketHandshake(String subject) {
        check("ws:handshake", safeSubject(subject), properties.getWebsocketHandshake());
    }

    private void check(String bucketName, String subject, SocialUploadRateLimitProperties.Bucket bucket) {
        if (!properties.isEnabled() || bucket == null) {
            return;
        }
        int used;
        try {
            used = redisTemplate != null
                    ? incrementRedis(bucketName, subject, bucket)
                    : incrementLocal(bucketName, subject, bucket);
        } catch (RuntimeException ex) {
            used = incrementLocal(bucketName, subject, bucket);
        }
        if (used > bucket.getMaxRequests()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Too many requests. Please try again later.");
        }
    }

    private int incrementRedis(String bucketName, String subject, SocialUploadRateLimitProperties.Bucket bucket) {
        String key = "app:social-upload-rate-limit:" + bucketName + ":" + subject;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(bucket.getWindowSeconds()));
        }
        return current == null ? 1 : current.intValue();
    }

    private int incrementLocal(String bucketName, String subject, SocialUploadRateLimitProperties.Bucket bucket) {
        long now = System.currentTimeMillis();
        long ttl = bucket.getWindowSeconds() * 1000L;
        String key = bucketName + ":" + subject;
        WindowCounter counter = localCounters.compute(key, (ignored, old) -> {
            if (old == null || now >= old.expiresAt) {
                return new WindowCounter(now + ttl, new AtomicInteger(1));
            }
            old.count.incrementAndGet();
            return old;
        });
        cleanupExpiredLocalCounters(now);
        return counter.count.get();
    }

    private void cleanupExpiredLocalCounters(long now) {
        if (localCounters.size() < 4096) {
            return;
        }
        localCounters.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private static String subject(AppUser user, HttpServletRequest request) {
        if (user != null && user.getId() != null) {
            return "user:" + user.getId();
        }
        String ip = request == null ? "" : firstNonBlank(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
        );
        if (ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(','));
        }
        return "ip:" + safeSegment(ip);
    }

    private static String safeSubject(String subject) {
        String safe = subject == null ? "" : subject.trim();
        return safe.isEmpty() ? "unknown" : safeSegment(safe);
    }

    private static String safeSegment(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (safe.isEmpty()) {
            return "unknown";
        }
        return safe.replaceAll("[^a-z0-9._:-]", "_");
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

    private record WindowCounter(long expiresAt, AtomicInteger count) {}
}
