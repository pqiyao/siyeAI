package com.example.sillyspringboot.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;

@Configuration
public class ApiRateLimitConfiguration {

    @Bean
    ApiRateLimitCounterStore apiRateLimitCounterStore(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        return new ApiRateLimitCounterStore(redisTemplateProvider.getIfAvailable());
    }

    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitFilterRegistration(
            ApiRateLimitProperties properties,
            ApiRateLimitCounterStore counterStore,
            AppH5SecurityEventMapper securityEvents,
            ClientIpResolver clientIpResolver
    ) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(
                new ApiRateLimitFilter(properties, counterStore, securityEvents, clientIpResolver)
        );
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registration.addUrlPatterns("/api/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<VerifiedDeviceRateLimitFilter> verifiedDeviceRateLimitFilterRegistration(
            ApiRateLimitProperties properties,
            ApiRateLimitCounterStore counterStore,
            AppH5SecurityEventMapper securityEvents,
            ClientIpResolver clientIpResolver
    ) {
        FilterRegistrationBean<VerifiedDeviceRateLimitFilter> registration = new FilterRegistrationBean<>(
                new VerifiedDeviceRateLimitFilter(properties, counterStore, securityEvents, clientIpResolver)
        );
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
