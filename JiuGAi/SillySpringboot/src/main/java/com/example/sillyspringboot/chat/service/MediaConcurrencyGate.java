package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.ops.dto.AppMediaRuntimeSettings;
import com.example.sillyspringboot.ops.service.AppMediaRuntimeSettingsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MediaConcurrencyGate {

    private static final Logger log = LoggerFactory.getLogger(MediaConcurrencyGate.class);

    public enum Capability { TTS, STT }

    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    private final AppMediaRuntimeSettingsService settingsService;
    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WindowCounter> rates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> rateIdentities = new ConcurrentHashMap<>();
    private final DefaultRedisScript<List> acquireScript = new DefaultRedisScript<>(ACQUIRE_LUA, List.class);
    private final DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>(RELEASE_LUA, Long.class);
    private final DefaultRedisScript<Long> rateScript = new DefaultRedisScript<>(RATE_LUA, Long.class);
    private final DefaultRedisScript<Long> rateOnceScript = new DefaultRedisScript<>(RATE_ONCE_LUA, Long.class);

    public MediaConcurrencyGate(AppMediaRuntimeSettingsService settingsService, ObjectProvider<StringRedisTemplate> redisProvider) {
        this.settingsService = settingsService;
        this.redis = redisProvider.getIfAvailable();
    }

    public Lease acquire(Capability capability, long userId) {
        return acquire(capability, userId, "");
    }

    public Lease acquire(Capability capability, long userId, String requestIdentity) {
        AppMediaRuntimeSettings settings = settingsService.getSettings();
        AppMediaRuntimeSettings.Limits limits = limits(capability, settings);
        if (redis != null) {
            try {
                checkRate(capability, userId, limits, requestIdentity, true, settings);
                return acquireRedis(capability, userId, limits, settings);
            } catch (BusinessException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                log.warn("Redis media gate unavailable; falling back to process-local limits: capability={}", capability, ex);
            }
        }
        checkRate(capability, userId, limits, requestIdentity, false, settings);
        return acquireInMemory(capability, userId, limits);
    }

    private Lease acquireRedis(Capability capability, long userId, AppMediaRuntimeSettings.Limits limits, AppMediaRuntimeSettings settings) {
        String prefix = keyPrefix(capability);
        String globalKey = prefix + ":concurrent:global";
        String userKey = prefix + ":concurrent:user:" + userId;
        List<?> result = redis.execute(
                acquireScript,
                List.of(globalKey, userKey),
                String.valueOf(limits.getGlobalConcurrentLimit()),
                String.valueOf(limits.getPerUserConcurrentLimit()),
                String.valueOf(settings.getCounterTtlSeconds())
        );
        if (result == null || result.isEmpty() || number(result.get(0)) != 1L) {
            throw busy(capability, result != null && result.size() > 1 ? String.valueOf(result.get(1)) : "GLOBAL");
        }
        AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                try {
                    redis.execute(releaseScript, List.of(globalKey, userKey));
                } catch (RuntimeException ex) {
                    log.warn("Redis media lease release failed: capability={}, userId={}", capability, userId, ex);
                }
            }
        };
    }

    private Lease acquireInMemory(Capability capability, long userId, AppMediaRuntimeSettings.Limits limits) {
        String prefix = keyPrefix(capability);
        String globalKey = prefix + ":global";
        String userKey = prefix + ":user:" + userId;
        AtomicInteger user = counters.computeIfAbsent(userKey, ignored -> new AtomicInteger());
        if (user.incrementAndGet() > limits.getPerUserConcurrentLimit()) {
            decrement(userKey, user);
            throw busy(capability, "USER");
        }
        AtomicInteger global = counters.computeIfAbsent(globalKey, ignored -> new AtomicInteger());
        if (global.incrementAndGet() > limits.getGlobalConcurrentLimit()) {
            decrement(globalKey, global);
            decrement(userKey, user);
            throw busy(capability, "GLOBAL");
        }
        AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                decrement(globalKey, global);
                decrement(userKey, user);
            }
        };
    }

    private void checkRate(
            Capability capability,
            long userId,
            AppMediaRuntimeSettings.Limits limits,
            String requestIdentity,
            boolean useRedis,
            AppMediaRuntimeSettings settings
    ) {
        String key = keyPrefix(capability) + ":rate:user:" + userId;
        String identity = requestIdentity == null ? "" : requestIdentity.trim();
        long used;
        if (useRedis) {
            Long value = identity.isBlank()
                    ? redis.execute(
                            rateScript,
                            List.of(key),
                            String.valueOf(settings.getRateWindowSeconds())
                    )
                    : redis.execute(
                            rateOnceScript,
                            List.of(key, keyPrefix(capability) + ":rate-id:" + userId + ":" + sha256(identity)),
                            String.valueOf(settings.getRateWindowSeconds())
                    );
            used = value == null ? 1L : value;
        } else {
            long window = Math.max(1L, Instant.now().getEpochSecond() / settings.getRateWindowSeconds());
            boolean shouldIncrement = identity.isBlank() || registerRateIdentity(key, identity, settings.getRateWindowSeconds());
            WindowCounter counter = rates.compute(key, (ignored, current) -> {
                if (current == null || current.window != window) {
                    return new WindowCounter(window, new AtomicInteger(shouldIncrement ? 1 : 0));
                }
                if (shouldIncrement) {
                    current.count.incrementAndGet();
                }
                return current;
            });
            used = counter.count.get();
        }
        if (used > limits.getPerUserRequestsPerWindow()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    capability == Capability.TTS ? "语音生成过于频繁，请稍后再试" : "语音识别过于频繁，请稍后再试");
        }
    }

    private AppMediaRuntimeSettings.Limits limits(Capability capability, AppMediaRuntimeSettings settings) {
        return capability == Capability.TTS ? settings.getTts() : settings.getStt();
    }

    private static String keyPrefix(Capability capability) {
        return "media:" + capability.name().toLowerCase();
    }

    private BusinessException busy(Capability capability, String reason) {
        if ("USER".equals(reason)) {
            return new BusinessException(ErrorCode.SERVICE_BUSY,
                    capability == Capability.TTS ? "当前已有语音生成任务，请稍后再试" : "当前已有语音识别任务，请稍后再试");
        }
        return new BusinessException(ErrorCode.SERVICE_BUSY,
                capability == Capability.TTS ? "语音生成服务繁忙，请稍后再试" : "语音识别服务繁忙，请稍后再试");
    }

    private void decrement(String key, AtomicInteger counter) {
        if (counter.decrementAndGet() <= 0) {
            counters.remove(key, counter);
        }
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private boolean registerRateIdentity(String rateKey, String identity, int rateWindowSeconds) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + rateWindowSeconds;
        String marker = rateKey + ":" + sha256(identity);
        Long existing = rateIdentities.get(marker);
        if (existing != null && existing > now) {
            return false;
        }
        rateIdentities.entrySet().removeIf(entry -> entry.getValue() <= now);
        return rateIdentities.putIfAbsent(marker, expiresAt) == null;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                out.append(Character.forDigit((item >>> 4) & 0x0f, 16));
                out.append(Character.forDigit(item & 0x0f, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record WindowCounter(long window, AtomicInteger count) {}

    private static final String ACQUIRE_LUA = """
            local g = tonumber(redis.call('get', KEYS[1]) or '0')
            local u = tonumber(redis.call('get', KEYS[2]) or '0')
            if u >= tonumber(ARGV[2]) then return {0, 'USER'} end
            if g >= tonumber(ARGV[1]) then return {0, 'GLOBAL'} end
            redis.call('incr', KEYS[1]); redis.call('expire', KEYS[1], tonumber(ARGV[3]))
            redis.call('incr', KEYS[2]); redis.call('expire', KEYS[2], tonumber(ARGV[3]))
            return {1, 'OK'}
            """;

    private static final String RELEASE_LUA = """
            for i = 1, 2 do
              if redis.call('exists', KEYS[i]) == 1 then
                local value = tonumber(redis.call('decr', KEYS[i]) or '0')
                if value <= 0 then redis.call('del', KEYS[i]) end
              end
            end
            return 1
            """;

    private static final String RATE_LUA = """
            local value = redis.call('incr', KEYS[1])
            if value == 1 then redis.call('expire', KEYS[1], tonumber(ARGV[1])) end
            return value
            """;

    private static final String RATE_ONCE_LUA = """
            local accepted = redis.call('set', KEYS[2], '1', 'EX', tonumber(ARGV[1]), 'NX')
            if accepted then
              local value = redis.call('incr', KEYS[1])
              if value == 1 then redis.call('expire', KEYS[1], tonumber(ARGV[1])) end
              return value
            end
            return tonumber(redis.call('get', KEYS[1]) or '0')
            """;
}
