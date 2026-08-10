package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 原子并发闸门：多实例/重启后仍能约束全局与单用户并发。
 */
public class RedisChatConcurrencyGate implements ChatConcurrencyGate {

    private static final String KEY_GLOBAL = "chat:gen:global";
    private static final String KEY_USER_PREFIX = "chat:gen:user:";

    private final StringRedisTemplate redis;
    private final AppChatProperties props;
    private final DefaultRedisScript<List> acquireScript;
    private final DefaultRedisScript<List> releaseScript;

    public RedisChatConcurrencyGate(StringRedisTemplate redis, AppChatProperties props) {
        this.redis = redis;
        this.props = props;
        this.acquireScript = new DefaultRedisScript<>(ACQUIRE_LUA, List.class);
        this.releaseScript = new DefaultRedisScript<>(RELEASE_LUA, List.class);
    }

    @Override
    public Lease acquire(long userId) {
        String userKey = KEY_USER_PREFIX + userId;
        List<?> res = redis.execute(
                acquireScript,
                List.of(KEY_GLOBAL, userKey),
                String.valueOf(props.getGlobalConcurrentLimit()),
                String.valueOf(props.getPerUserConcurrentLimit()),
                String.valueOf(props.getCounterTtlSeconds())
        );

        if (res == null || res.size() < 2) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试");
        }
        long ok = toLong(res.get(0));
        String reason = String.valueOf(res.get(1));
        if (ok != 1L) {
            if ("USER".equals(reason)) {
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "当前已有生成任务进行中，请稍后重试");
            }
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试");
        }

        return new RedisLease(redis, releaseScript, userKey);
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception ignore) {
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
}
