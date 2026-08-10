package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class RedisImageGenerationConcurrencyGate implements ImageGenerationConcurrencyGate {

    private static final String KEY_GLOBAL = "image:gen:global";
    private static final String KEY_USER_PREFIX = "image:gen:user:";

    private final StringRedisTemplate redis;
    private final AppImageGenerationSettingsService settingsService;
    private final AppImageGenerationProperties properties;
    private final DefaultRedisScript<List> acquireScript;
    private final DefaultRedisScript<List> releaseScript;
    private final DefaultRedisScript<List> claimRequestScript;
    private final DefaultRedisScript<Long> completeRequestScript;
    private final DefaultRedisScript<Long> cancelRequestScript;

    public RedisImageGenerationConcurrencyGate(
            StringRedisTemplate redis,
            AppImageGenerationSettingsService settingsService,
            AppImageGenerationProperties properties
    ) {
        this.redis = redis;
        this.settingsService = settingsService;
        this.properties = properties;
        this.acquireScript = new DefaultRedisScript<>(ACQUIRE_LUA, List.class);
        this.releaseScript = new DefaultRedisScript<>(RELEASE_LUA, List.class);
        this.claimRequestScript = new DefaultRedisScript<>(CLAIM_REQUEST_LUA, List.class);
        this.completeRequestScript = new DefaultRedisScript<>(COMPLETE_REQUEST_LUA, Long.class);
        this.cancelRequestScript = new DefaultRedisScript<>(CANCEL_REQUEST_LUA, Long.class);
    }

    @Override
    public Lease acquire(long userId) {
        String userKey = KEY_USER_PREFIX + userId;
        List<?> res = redis.execute(
                acquireScript,
                List.of(KEY_GLOBAL, userKey),
                String.valueOf(settingsService.getSettings().getGlobalConcurrentLimit()),
                String.valueOf(settingsService.getSettings().getPerUserConcurrentLimit()),
                String.valueOf(settingsService.getSettings().getCounterTtlSeconds())
        );
        if (res == null || res.size() < 2) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "生图引擎繁忙，请稍后再试");
        }
        long ok = toLong(res.get(0));
        String reason = String.valueOf(res.get(1));
        if (ok != 1L) {
            if ("USER".equals(reason)) {
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "当前已有生图任务进行中，请稍后再试");
            }
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "生图引擎繁忙，请稍后再试");
        }
        return new RedisLease(redis, releaseScript, userKey);
    }

    @Override
    public RequestLease claimRequest(long userId, String requestId) {
        String key = "image:request:" + userId + ":" + sha256(requestId);
        String token = UUID.randomUUID().toString();
        List<?> result = redis.execute(claimRequestScript, List.of(key), token, String.valueOf(requestRunningTtlSeconds()));
        if (result == null || result.isEmpty() || toLong(result.get(0)) != 1L) {
            String status = result != null && result.size() > 1 ? String.valueOf(result.get(1)) : "RUNNING";
            if ("DONE".equals(status)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该生图请求已经完成，请勿重复提交");
            }
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "该生图请求正在处理中，请稍后查看结果");
        }
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        AtomicBoolean completing = new AtomicBoolean(false);
        return new RequestLease() {
            @Override
            public void markSucceeded() {
                if (succeeded.get() || !completing.compareAndSet(false, true)) {
                    return;
                }
                try {
                    Long completed = redis.execute(
                            completeRequestScript, List.of(key), token, String.valueOf(resultTtlSeconds()));
                    if (completed == null || completed != 1L) {
                        throw new IllegalStateException("Image request completion token no longer owns the request");
                    }
                    succeeded.set(true);
                } finally {
                    completing.set(false);
                }
            }

            @Override
            public void close() {
                if (closed.compareAndSet(false, true) && !succeeded.get()) {
                    redis.execute(cancelRequestScript, List.of(key), token);
                }
            }
        };
    }

    private int requestRunningTtlSeconds() {
        long novel = properties.getNovelAi().getRequestTimeout().toSeconds();
        long comfy = properties.getStComfy().getRequestTimeout().toSeconds();
        return (int) Math.max(300L, Math.min(Integer.MAX_VALUE, Math.max(novel, comfy) + 60L));
    }

    private int resultTtlSeconds() {
        return Math.max(60, properties.getResultTtlSeconds());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) out.append(String.format("%02x", item & 0xff));
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static final class RedisLease implements Lease {
        private final StringRedisTemplate redis;
        private final DefaultRedisScript<List> releaseScript;
        private final String userKey;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private RedisLease(StringRedisTemplate redis, DefaultRedisScript<List> releaseScript, String userKey) {
            this.redis = redis;
            this.releaseScript = releaseScript;
            this.userKey = userKey;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            redis.execute(releaseScript, List.of(KEY_GLOBAL, userKey));
        }
    }

    private static final String ACQUIRE_LUA = """
            local gKey = KEYS[1]
            local uKey = KEYS[2]
            local gLimit = tonumber(ARGV[1])
            local uLimit = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])

            local u = tonumber(redis.call('get', uKey) or '0')
            if u >= uLimit then
              return {0, 'USER'}
            end

            local g = tonumber(redis.call('get', gKey) or '0')
            if g >= gLimit then
              return {0, 'GLOBAL'}
            end

            redis.call('incr', uKey)
            redis.call('expire', uKey, ttl)
            redis.call('incr', gKey)
            redis.call('expire', gKey, ttl)
            return {1, 'OK'}
            """;

    private static final String RELEASE_LUA = """
            local gKey = KEYS[1]
            local uKey = KEYS[2]

            if redis.call('exists', uKey) == 1 then
              local u = tonumber(redis.call('decr', uKey) or '0')
              if u <= 0 then
                redis.call('del', uKey)
              end
            end

            if redis.call('exists', gKey) == 1 then
              local g = tonumber(redis.call('decr', gKey) or '0')
              if g <= 0 then
                redis.call('del', gKey)
              end
            end
            return {1, 'OK'}
            """;

    private static final String CLAIM_REQUEST_LUA = """
            local current = redis.call('get', KEYS[1])
            if current then
              if current == 'DONE' then return {0, 'DONE'} end
              return {0, 'RUNNING'}
            end
            local accepted = redis.call('set', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2]), 'NX')
            if accepted then return {1, 'RUNNING'} end
            return {0, 'RUNNING'}
            """;

    private static final String COMPLETE_REQUEST_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              redis.call('set', KEYS[1], 'DONE', 'EX', tonumber(ARGV[2]))
              return 1
            end
            return 0
            """;

    private static final String CANCEL_REQUEST_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              redis.call('del', KEYS[1])
              return 1
            end
            return 0
            """;
}
