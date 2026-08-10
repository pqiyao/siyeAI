package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

class ImageGenerationResultStoreTest {

    @Test
    void storesAndReadsLocalRecoveryResultWithoutRedis() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        ImageGenerationResultStore store = new ImageGenerationResultStore(
                new AppImageGenerationProperties(), new ObjectMapper(), redisProvider);
        Map<String, Object> result = Map.of(
                "status", "DONE",
                "imageRequestId", "image_request_1",
                "images", List.of(Map.of("url", "/uploads/generated/user/image.png"))
        );

        store.put(7L, "image_request_1", result);

        assertThat(store.get(7L, "image_request_1")).hasValueSatisfying(cached -> {
            assertThat(cached).containsEntry("status", "DONE");
            assertThat(cached.get("images")).isInstanceOf(List.class);
        });
        assertThat(store.get(8L, "image_request_1")).isEmpty();
    }

    @Test
    void redisWriteFailureDoesNotPretendCrossInstanceRecoverySucceeded() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("redis down"))
                .when(values).set(any(), any(), any(java.time.Duration.class));
        ImageGenerationResultStore store = new ImageGenerationResultStore(
                new AppImageGenerationProperties(), new ObjectMapper(), redisProvider);

        assertThatThrownBy(() -> store.put(7L, "image_request_1", Map.of("status", "DONE")))
                .isInstanceOf(com.example.sillyspringboot.shared.error.BusinessException.class)
                .hasMessageContaining("无法安全保存");
        assertThat(store.get(7L, "image_request_1")).isEmpty();
    }
}
