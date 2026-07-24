package com.example.sillyspringboot.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ChatPreferenceBodyLimitConfiguration {

    @Bean
    public FilterRegistrationBean<ChatPreferenceBodyLimitFilter> chatPreferenceBodyLimitFilterRegistration() {
        FilterRegistrationBean<ChatPreferenceBodyLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ChatPreferenceBodyLimitFilter());
        registration.addUrlPatterns("/api/v1/app/me/chat-preferences");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 7);
        return registration;
    }
}
