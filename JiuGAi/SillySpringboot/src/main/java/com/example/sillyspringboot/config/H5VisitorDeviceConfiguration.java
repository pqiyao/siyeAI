package com.example.sillyspringboot.config;

import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class H5VisitorDeviceConfiguration {

    @Bean
    public FilterRegistrationBean<H5VisitorDeviceFilter> h5VisitorDeviceFilterRegistration(
            H5VisitorDeviceService visitorDeviceService
    ) {
        FilterRegistrationBean<H5VisitorDeviceFilter> registration =
                new FilterRegistrationBean<>(new H5VisitorDeviceFilter(visitorDeviceService));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
