package com.example.sillyspringboot.admin.security;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.config.ApiRateLimitProperties;
import com.example.sillyspringboot.config.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminLoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final RuoYiAdminProperties properties;
    private final Clock clock;
    private final ClientIpResolver clientIpResolver;
    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    @Autowired
    public AdminLoginAttemptService(RuoYiAdminProperties properties, ClientIpResolver clientIpResolver) {
        this(properties, Clock.systemUTC(), clientIpResolver);
    }

    AdminLoginAttemptService(RuoYiAdminProperties properties, Clock clock) {
        this(properties, clock, new ClientIpResolver(new ApiRateLimitProperties()));
    }

    AdminLoginAttemptService(
            RuoYiAdminProperties properties,
            Clock clock,
            ClientIpResolver clientIpResolver
    ) {
        this.properties = properties;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    public AttemptDecision check(String key) {
        long now = clock.millis();
        AttemptState state = attempts.get(key);
        if (state == null) {
            return AttemptDecision.permit();
        }
        if (state.blockedUntilMillis() > now) {
            return AttemptDecision.deny(secondsUntil(state.blockedUntilMillis(), now));
        }
        if (state.windowStartedMillis() + windowMillis() <= now) {
            attempts.remove(key, state);
        }
        return AttemptDecision.permit();
    }

    public AttemptDecision recordFailure(String key) {
        long now = clock.millis();
        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_KEYS) {
            evictExpired(now);
            if (attempts.size() >= MAX_TRACKED_KEYS) {
                return AttemptDecision.deny(Math.max(1, properties.getLoginWindowSeconds()));
            }
        }
        int maxAttempts = Math.max(1, properties.getLoginMaxAttempts());
        AttemptState state = attempts.compute(key, (ignored, old) -> {
            if (old == null || old.windowStartedMillis() + windowMillis() <= now) {
                return new AttemptState(now, 1, 0);
            }
            if (old.blockedUntilMillis() > now) {
                return old;
            }
            int failures = old.failures() + 1;
            long blockedUntil = failures >= maxAttempts ? now + blockMillis() : 0;
            return new AttemptState(old.windowStartedMillis(), failures, blockedUntil);
        });
        return state.blockedUntilMillis() > now
                ? AttemptDecision.deny(secondsUntil(state.blockedUntilMillis(), now))
                : AttemptDecision.permit();
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    public String keyFor(String username, HttpServletRequest request) {
        String normalizedUser = normalizeSegment(username, 64).toLowerCase(Locale.ROOT);
        String clientAddress = normalizeSegment(clientIpResolver.resolve(request), 128);
        return clientAddress + "|user:" + normalizedUser;
    }

    private void evictExpired(long now) {
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            return state.blockedUntilMillis() <= now && state.windowStartedMillis() + windowMillis() <= now;
        });
    }

    private long windowMillis() {
        return Math.max(1, properties.getLoginWindowSeconds()) * 1000L;
    }

    private long blockMillis() {
        return Math.max(1, properties.getLoginBlockSeconds()) * 1000L;
    }

    private static long secondsUntil(long deadline, long now) {
        return Math.max(1, (deadline - now + 999L) / 1000L);
    }

    private static String normalizeSegment(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9._:@-]", "_");
        if (normalized.isBlank()) {
            return "unknown";
        }
        return normalized.substring(0, Math.min(maxLength, normalized.length()));
    }

    private record AttemptState(long windowStartedMillis, int failures, long blockedUntilMillis) {
    }

    public record AttemptDecision(boolean allowed, long retryAfterSeconds) {
        private static AttemptDecision permit() {
            return new AttemptDecision(true, 0);
        }

        private static AttemptDecision deny(long retryAfterSeconds) {
            return new AttemptDecision(false, retryAfterSeconds);
        }
    }
}
