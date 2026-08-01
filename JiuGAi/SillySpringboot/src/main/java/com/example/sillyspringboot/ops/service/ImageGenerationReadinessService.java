package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.config.AppProperties;
import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ImageGenerationReadinessService {

    private static final long REDIS_STATUS_CACHE_MS = 3_000L;

    private final AppProperties appProperties;
    private final AppImageGenerationProperties imageProperties;
    private final ImageGenerationAssetStorageService assetStorageService;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<AppImageGenerationSettingsService> settingsProvider;
    private volatile RedisStatus cachedRedisStatus = new RedisStatus(false, false, 0L);

    public ImageGenerationReadinessService(
            AppProperties appProperties,
            AppImageGenerationProperties imageProperties,
            ImageGenerationAssetStorageService assetStorageService,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectProvider<AppImageGenerationSettingsService> settingsProvider
    ) {
        this.appProperties = appProperties;
        this.imageProperties = imageProperties;
        this.assetStorageService = assetStorageService;
        this.redisProvider = redisProvider;
        this.settingsProvider = settingsProvider;
    }

    public Snapshot systemSnapshot() {
        String engine = normalizeEngine(imageProperties.getEngine());
        try {
            AppImageGenerationSettingsService settingsService = settingsProvider.getIfAvailable();
            if (settingsService != null) {
                AppImageGenerationSettings settings = settingsService.getSettings();
                engine = normalizeEngine(settings == null ? null : settings.getEngine());
            }
        } catch (RuntimeException ignored) {
            // Environment configuration remains a safe fallback while runtime settings are unavailable.
        }
        return snapshot(engine);
    }

    public Snapshot snapshot(String engineName) {
        String engine = normalizeEngine(engineName);
        RedisStatus redis = redisStatus();
        boolean storageReady = assetStorageService.isReady();
        boolean tokenConfigured = !"novelai".equals(engine)
                || StringUtils.hasText(imageProperties.getNovelAi().getToken());
        boolean redisRequired = isSystemEngine(engine);

        String statusCode = "READY";
        String message = "";
        if (!tokenConfigured) {
            statusCode = "MISSING_TOKEN";
            message = "系统 NovelAI 尚未配置 Token";
        } else if (redisRequired && !redis.configured()) {
            statusCode = "REDIS_NOT_CONFIGURED";
            message = "系统生图尚未配置 Redis";
        } else if (redisRequired && !redis.reachable()) {
            statusCode = "REDIS_UNAVAILABLE";
            message = "系统生图 Redis 当前不可用";
        } else if (!storageReady) {
            statusCode = "STORAGE_UNAVAILABLE";
            message = "系统生图媒体存储当前不可用";
        }
        boolean ready = "READY".equals(statusCode);
        return new Snapshot(
                ready,
                statusCode,
                message,
                engine,
                tokenConfigured,
                redis.configured(),
                redis.reachable(),
                storageReady,
                isProduction()
        );
    }

    public void guardReady(String engineName) {
        Snapshot snapshot = snapshot(engineName);
        if (!snapshot.ready()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, snapshot.message());
        }
    }

    public Map<String, Object> toMap(Snapshot snapshot) {
        Snapshot safe = snapshot == null ? systemSnapshot() : snapshot;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ready", safe.ready());
        result.put("statusCode", safe.statusCode());
        result.put("message", safe.message());
        result.put("engine", safe.engine());
        result.put("tokenConfigured", safe.tokenConfigured());
        result.put("redisConfigured", safe.redisConfigured());
        result.put("redisReachable", safe.redisReachable());
        result.put("storageReady", safe.storageReady());
        result.put("production", safe.production());
        return result;
    }

    public boolean isProduction() {
        String environment = appProperties.getEnvironment() == null
                ? "" : appProperties.getEnvironment().trim().toLowerCase(Locale.ROOT);
        return "prod".equals(environment) || "production".equals(environment);
    }

    private RedisStatus redisStatus() {
        long now = System.currentTimeMillis();
        RedisStatus cached = cachedRedisStatus;
        if (now - cached.checkedAt() <= REDIS_STATUS_CACHE_MS) {
            return cached;
        }
        synchronized (this) {
            cached = cachedRedisStatus;
            if (now - cached.checkedAt() <= REDIS_STATUS_CACHE_MS) {
                return cached;
            }
            StringRedisTemplate redisTemplate = redisProvider.getIfAvailable();
            boolean configured = redisTemplate != null;
            boolean reachable = configured && canPingRedis(redisTemplate);
            RedisStatus refreshed = new RedisStatus(configured, reachable, now);
            cachedRedisStatus = refreshed;
            return refreshed;
        }
    }

    private static boolean canPingRedis(StringRedisTemplate redisTemplate) {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            return pong != null && !pong.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isSystemEngine(String engine) {
        return "novelai".equals(engine) || "st_comfy".equals(engine);
    }

    static String normalizeEngine(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("st_comfy".equals(text) || "comfy".equals(text) || "comfyui".equals(text)) {
            return "st_comfy";
        }
        if ("openai_compatible".equals(text) || "user_openai_compatible".equals(text)) {
            return "openai_compatible";
        }
        return "novelai";
    }

    public record Snapshot(
            boolean ready,
            String statusCode,
            String message,
            String engine,
            boolean tokenConfigured,
            boolean redisConfigured,
            boolean redisReachable,
            boolean storageReady,
            boolean production
    ) {}

    private record RedisStatus(boolean configured, boolean reachable, long checkedAt) {}
}
