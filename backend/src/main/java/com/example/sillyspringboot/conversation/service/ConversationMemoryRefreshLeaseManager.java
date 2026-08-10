package com.example.sillyspringboot.conversation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates automatic memory refreshes across application instances.
 */
@Component
public class ConversationMemoryRefreshLeaseManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryRefreshLeaseManager.class);
    private static final String KEY_PREFIX = "conversation:memory:auto-refresh:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AtomicBoolean missingRedisLogged = new AtomicBoolean(false);
    private final AtomicBoolean redisFailureLogged = new AtomicBoolean(false);

    public ConversationMemoryRefreshLeaseManager(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public LeaseAttempt tryAcquire(String memoryKey, Duration ttl) {
        if (redis == null) {
            if (missingRedisLogged.compareAndSet(false, true)) {
                log.warn("Redis is not configured; conversation memory auto refresh uses JVM-local coordination only");
            }
            return LeaseAttempt.acquired(Lease.NOOP, true);
        }

        String redisKey = KEY_PREFIX + memoryKey;
        String ownerToken = UUID.randomUUID().toString().replace("-", "");
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(redisKey, ownerToken, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                if (redisFailureLogged.compareAndSet(true, false)) {
                    log.info("Redis coordination recovered for conversation memory auto refresh");
                }
                return LeaseAttempt.acquired(new RedisLease(redis, redisKey, ownerToken), false);
            }
            if (Boolean.FALSE.equals(acquired)) {
                return LeaseAttempt.heldByOther();
            }
            logRedisFailure("Redis returned no result while acquiring conversation memory refresh lease", null);
            return LeaseAttempt.unavailable();
        } catch (RuntimeException ex) {
            logRedisFailure("Redis unavailable while acquiring conversation memory refresh lease", ex);
            return LeaseAttempt.unavailable();
        }
    }

    private void logRedisFailure(String message, RuntimeException error) {
        if (!redisFailureLogged.compareAndSet(false, true)) {
            return;
        }
        if (error == null) {
            log.warn(message);
        } else {
            log.warn("{}: {}", message, error.getMessage());
        }
    }

    public enum Status {
        ACQUIRED,
        HELD_BY_OTHER,
        UNAVAILABLE
    }

    public record LeaseAttempt(Status status, Lease lease, boolean localOnly) {
        static LeaseAttempt acquired(Lease lease, boolean localOnly) {
            return new LeaseAttempt(Status.ACQUIRED, lease, localOnly);
        }

        static LeaseAttempt heldByOther() {
            return new LeaseAttempt(Status.HELD_BY_OTHER, null, false);
        }

        static LeaseAttempt unavailable() {
            return new LeaseAttempt(Status.UNAVAILABLE, null, false);
        }

        public boolean acquired() {
            return status == Status.ACQUIRED && lease != null;
        }
    }

    public interface Lease extends AutoCloseable {
        Lease NOOP = () -> { };

        @Override
        void close();
    }

    private static final class RedisLease implements Lease {
        private final StringRedisTemplate redis;
        private final String key;
        private final String ownerToken;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private RedisLease(StringRedisTemplate redis, String key, String ownerToken) {
            this.redis = redis;
            this.key = key;
            this.ownerToken = ownerToken;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                redis.execute(RELEASE_SCRIPT, List.of(key), ownerToken);
            } catch (RuntimeException ex) {
                log.warn("Redis unavailable while releasing conversation memory refresh lease key={}: {}",
                        key, ex.getMessage());
            }
        }
    }
}
