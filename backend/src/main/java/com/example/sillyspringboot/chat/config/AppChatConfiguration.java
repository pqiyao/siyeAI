package com.example.sillyspringboot.chat.config;

import com.example.sillyspringboot.chat.service.AppChatRuntimeRegistry;
import com.example.sillyspringboot.chat.service.ChatConcurrencyGate;
import com.example.sillyspringboot.chat.service.ChatGenerationDispatcher;
import com.example.sillyspringboot.chat.service.InMemoryChatConcurrencyGate;
import com.example.sillyspringboot.chat.service.RedisChatConcurrencyGate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(AppChatProperties.class)
public class AppChatConfiguration {

    @Bean
    public AppChatRuntimeRegistry appChatRuntimeRegistry() {
        return new AppChatRuntimeRegistry();
    }

    @Bean
    public ChatGenerationDispatcher chatGenerationDispatcher(AppChatProperties props) {
        return new ChatGenerationDispatcher(props);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public ChatConcurrencyGate redisChatConcurrencyGate(StringRedisTemplate redisTemplate, AppChatProperties props) {
        return new RedisChatConcurrencyGate(redisTemplate, props);
    }

    @Bean
    @ConditionalOnMissingBean(ChatConcurrencyGate.class)
    public ChatConcurrencyGate inMemoryChatConcurrencyGate(AppChatProperties props) {
        // 测试环境、未接入 Redis，或 Redis 自动配置不可用时回退到内存闸门。
        return new InMemoryChatConcurrencyGate(props.getGlobalConcurrentLimit(), props.getPerUserConcurrentLimit());
    }
}
