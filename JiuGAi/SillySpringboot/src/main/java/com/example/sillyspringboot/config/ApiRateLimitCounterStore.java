package com.example.sillyspringboot.config;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class ApiRateLimitCounterStore {

    private final StringRedisTemplate redisTemplate;
    private final Map<String, WindowCounter> localCounters = new ConcurrentHashMap<>();
    private final AtomicLong localOperations = new AtomicLong();

    ApiRateLimitCounterStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    int increment(String counterKey, int windowSeconds) {
        return redisTemplate != null
                ? incrementRedisCounter(counterKey, windowSeconds)
                : incrementLocalCounter(counterKey, windowSeconds);
    }

    private int incrementRedisCounter(String counterKey, int windowSeconds) {
        String key = "api:rate-limit:" + counterKey;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return current == null ? 1 : current.intValue();
    }

    private int incrementLocalCounter(String counterKey, int windowSeconds) {
        long now = System.currentTimeMillis();
        evictExpiredLocalCounters(now);
        long ttl = windowSeconds * 1000L;
        WindowCounter counter = localCounters.compute(counterKey, (key, old) -> {
            if (old == null || now >= old.expiresAt) {
                return new WindowCounter(now + ttl, new AtomicInteger(1));
            }
            old.count.incrementAndGet();
            return old;
        });
        return counter.count.get();
    }

    private void evictExpiredLocalCounters(long now) {
        if ((localOperations.incrementAndGet() & 1023L) != 0L) {
            return;
        }
        localCounters.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
    }

    private record WindowCounter(long expiresAt, AtomicInteger count) {}
}
