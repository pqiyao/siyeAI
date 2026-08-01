package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.config.AppProperties;
import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageGenerationReadinessServiceTest {

    @Test
    void missingNovelAiTokenBlocksSystemImageGenerationBeforeRuntimeUse() {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        ImageGenerationAssetStorageService storage = mock(ImageGenerationAssetStorageService.class);
        when(storage.isReady()).thenReturn(true);

        ImageGenerationReadinessService service = service(properties, storage, null);

        assertThat(service.snapshot("novelai"))
                .extracting(
                        ImageGenerationReadinessService.Snapshot::ready,
                        ImageGenerationReadinessService.Snapshot::statusCode
                )
                .containsExactly(false, "MISSING_TOKEN");
    }

    @Test
    void missingRedisBlocksConfiguredNovelAiSystem() {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        properties.getNovelAi().setToken("configured-token");
        ImageGenerationAssetStorageService storage = mock(ImageGenerationAssetStorageService.class);
        when(storage.isReady()).thenReturn(true);

        ImageGenerationReadinessService service = service(properties, storage, null);

        assertThat(service.snapshot("novelai"))
                .extracting(
                        ImageGenerationReadinessService.Snapshot::ready,
                        ImageGenerationReadinessService.Snapshot::statusCode
                )
                .containsExactly(false, "REDIS_NOT_CONFIGURED");
    }

    @Test
    void configuredTokenReachableRedisAndWritableStorageAreReady() {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        properties.getNovelAi().setToken("configured-token");
        ImageGenerationAssetStorageService storage = mock(ImageGenerationAssetStorageService.class);
        when(storage.isReady()).thenReturn(true);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        ImageGenerationReadinessService service = service(properties, storage, redis);

        assertThat(service.snapshot("novelai").ready()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static ImageGenerationReadinessService service(
            AppImageGenerationProperties properties,
            ImageGenerationAssetStorageService storage,
            StringRedisTemplate redis
    ) {
        AppProperties appProperties = new AppProperties();
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        ObjectProvider<AppImageGenerationSettingsService> settingsProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(settingsProvider.getIfAvailable()).thenReturn(null);
        return new ImageGenerationReadinessService(
                appProperties, properties, storage, redisProvider, settingsProvider
        );
    }
}
