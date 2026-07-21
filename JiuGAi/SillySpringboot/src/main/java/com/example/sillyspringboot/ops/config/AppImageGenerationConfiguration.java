package com.example.sillyspringboot.ops.config;

import com.example.sillyspringboot.ops.service.ImageGenerationConcurrencyGate;
import com.example.sillyspringboot.ops.service.InMemoryImageGenerationConcurrencyGate;
import com.example.sillyspringboot.ops.service.AppImageGenerationSettingsService;
import com.example.sillyspringboot.ops.service.RedisImageGenerationConcurrencyGate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AppImageGenerationConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public ImageGenerationConcurrencyGate redisImageGenerationConcurrencyGate(
            StringRedisTemplate redisTemplate,
            AppImageGenerationSettingsService settingsService
    ) {
        return new RedisImageGenerationConcurrencyGate(redisTemplate, settingsService);
    }

    @Bean
    @ConditionalOnMissingBean(ImageGenerationConcurrencyGate.class)
    public ImageGenerationConcurrencyGate inMemoryImageGenerationConcurrencyGate(
            AppImageGenerationSettingsService settingsService
    ) {
        return new InMemoryImageGenerationConcurrencyGate(settingsService);
    }
}
