package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImageGenerationResultStore {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationResultStore.class);
    private static final int MAX_LOCAL_ENTRIES = 24;
    private static final TypeReference<Map<String, Object>> RESULT_TYPE = new TypeReference<>() {};

    private final AppImageGenerationProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, LocalValue> local = new ConcurrentHashMap<>();

    public ImageGenerationResultStore(
            AppImageGenerationProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisProvider
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redis = redisProvider.getIfAvailable();
    }

    public Optional<Map<String, Object>> get(long userId, String requestId) {
        String key = key(userId, requestId);
        long now = System.currentTimeMillis();
        LocalValue localValue = local.get(key);
        if (localValue != null) {
            if (localValue.expiresAt() > now) {
                return read(localValue.json());
            }
            local.remove(key, localValue);
        }
        if (redis == null) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key);
            Optional<Map<String, Object>> result = read(json);
            if (result.isPresent() && json != null) {
                local.put(key, new LocalValue(json, now + ttl().toMillis()));
                trimLocal(now);
            }
            return result;
        } catch (RuntimeException ex) {
            log.warn("Image result Redis read failed: userId={}, requestIdHash={}", userId, hash(requestId), ex);
            return Optional.empty();
        }
    }

    public void put(long userId, String requestId, Map<String, Object> result) {
        String json = write(result);
        assertSize(json);
        String key = key(userId, requestId);
        long now = System.currentTimeMillis();
        local.put(key, new LocalValue(json, now + ttl().toMillis()));
        trimLocal(now);
        if (redis != null) {
            try {
                redis.opsForValue().set(key, json, ttl());
            } catch (RuntimeException ex) {
                local.remove(key);
                throw new BusinessException(
                        ErrorCode.SERVICE_BUSY,
                        "生图结果暂时无法安全保存，请稍后重试",
                        ex
                );
            }
        }
    }

    /**
     * Validate the serialized result before entitlement settlement. This keeps an
     * oversized data URL from being charged successfully and then becoming
     * unrecoverable after the HTTP request is lost.
     */
    public void validate(Map<String, Object> result) {
        assertSize(write(result));
    }

    private void assertSize(String json) {
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        int maxBytes = Math.max(1_048_576, properties.getMaxCachedResultBytes());
        if (bytes > maxBytes) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成图片结果过大，无法安全保存，请调整系统图片尺寸");
        }
    }

    private Optional<Map<String, Object>> read(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RESULT_TYPE));
        } catch (Exception ex) {
            log.warn("Invalid cached image result ignored", ex);
            return Optional.empty();
        }
    }

    private String write(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result == null ? Map.of() : result);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生图结果保存失败", ex);
        }
    }

    private Duration ttl() {
        return Duration.ofSeconds(Math.max(60, properties.getResultTtlSeconds()));
    }

    private void trimLocal(long now) {
        local.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        while (local.size() > MAX_LOCAL_ENTRIES) {
            local.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().expiresAt()))
                    .ifPresent(entry -> local.remove(entry.getKey(), entry.getValue()));
        }
    }

    private static String key(long userId, String requestId) {
        return "image:result:" + userId + ":" + hash(requestId);
    }

    private static String hash(String value) {
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

    private record LocalValue(String json, long expiresAt) {}
}
