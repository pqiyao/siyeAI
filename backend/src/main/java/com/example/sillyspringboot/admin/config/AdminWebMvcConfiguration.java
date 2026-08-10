package com.example.sillyspringboot.admin.config;

import com.example.sillyspringboot.admin.security.AdminJwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebMvcConfiguration implements WebMvcConfigurer {

    private final AdminJwtInterceptor adminJwtInterceptor;

    public AdminWebMvcConfiguration(AdminJwtInterceptor adminJwtInterceptor) {
        this.adminJwtInterceptor = adminJwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminJwtInterceptor)
                .addPathPatterns(
                        "/admin/**",
                        "/system/**",
                        "/getInfo",
                        "/getRouters",
                        "/logout",
                        "/unlockscreen"
                )
                .excludePathPatterns(
                        "/login",
                        "/captchaImage",
                        "/favicon.ico",
                        "/error"
                );
    }
}
