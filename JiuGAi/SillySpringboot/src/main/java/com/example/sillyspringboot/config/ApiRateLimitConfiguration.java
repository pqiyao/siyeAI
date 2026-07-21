package com.example.sillyspringboot.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ApiRateLimitConfiguration {

    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitFilterRegistration(
            ApiRateLimitProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(
                new ApiRateLimitFilter(properties, redisTemplateProvider.getIfAvailable())
        );
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
