package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.config.TelegramProperties;
import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.service.ChatConcurrencyGate;
import com.example.sillyspringboot.config.ApiRateLimitProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/app/runtime")
public class ApiV1RuntimeStatusController {

    private final Environment environment;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ChatConcurrencyGate chatConcurrencyGate;
    private final AppChatProperties chatProperties;
    private final ApiRateLimitProperties rateLimitProperties;
    private final TelegramStarsPaymentProperties telegramStarsPaymentProperties;
    private final TelegramProperties telegramProperties;

    public ApiV1RuntimeStatusController(
            Environment environment,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ChatConcurrencyGate chatConcurrencyGate,
            AppChatProperties chatProperties,
            ApiRateLimitProperties rateLimitProperties,
            TelegramStarsPaymentProperties telegramStarsPaymentProperties,
            TelegramProperties telegramProperties
    ) {
        this.environment = environment;
        this.redisTemplateProvider = redisTemplateProvider;
        this.chatConcurrencyGate = chatConcurrencyGate;
        this.chatProperties = chatProperties;
        this.rateLimitProperties = rateLimitProperties;
        this.telegramStarsPaymentProperties = telegramStarsPaymentProperties;
        this.telegramProperties = telegramProperties;
    }

    @GetMapping("/status")
    public ApiV1Result<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("environment", environment.getProperty("app.environment", "dev"));
        data.put("actuatorExposure", environment.getProperty("management.endpoints.web.exposure.include", ""));

        Map<String, Object> redis = new LinkedHashMap<>();
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        redis.put("configured", redisTemplate != null);
        redis.put("reachable", redisTemplate != null && canPingRedis(redisTemplate));
        redis.put("chatGate", chatConcurrencyGate.getClass().getSimpleName());
        data.put("redis", redis);

        Map<String, Object> rateLimit = new LinkedHashMap<>();
        rateLimit.put("enabled", rateLimitProperties.isEnabled());
        rateLimit.put("windowSeconds", rateLimitProperties.getWindowSeconds());
        rateLimit.put("maxRequestsPerWindow", rateLimitProperties.getMaxRequestsPerWindow());
        data.put("rateLimit", rateLimit);

        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("globalConcurrentLimit", chatProperties.getGlobalConcurrentLimit());
        chat.put("perUserConcurrentLimit", chatProperties.getPerUserConcurrentLimit());
        chat.put("generationWorkerThreads", chatProperties.getGenerationWorkerThreads());
        chat.put("generationQueueCapacity", chatProperties.getGenerationQueueCapacity());
        chat.put("maxQueueWaitSeconds", chatProperties.getMaxQueueWaitSeconds());
        chat.put("sseTimeoutSeconds", chatProperties.getSseTimeoutSeconds());
        chat.put("generationTimeoutSeconds", chatProperties.getGenerationTimeoutSeconds());
        data.put("chat", chat);

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("telegramStarsEnabled", telegramStarsPaymentProperties.isEnabled());
        payment.put("botTokenConfigured", telegramProperties.getBotToken() != null && !telegramProperties.getBotToken().isBlank());
        payment.put("botUsername", telegramStarsPaymentProperties.getBotUsername());
        payment.put("webhookSecretConfigured", telegramStarsPaymentProperties.getWebhookSecret() != null
                && !telegramStarsPaymentProperties.getWebhookSecret().isBlank());
        data.put("payment", payment);

        return ApiV1Result.ok(data);
    }

    private boolean canPingRedis(StringRedisTemplate redisTemplate) {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            return pong != null && !pong.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }
}
