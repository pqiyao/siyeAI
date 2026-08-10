package com.example.sillyspringboot.config;

import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class H5SecurityContextTest {

    @Autowired
    private H5VisitorDeviceService visitorDeviceService;

    @Autowired
    @Qualifier("apiRateLimitFilterRegistration")
    private FilterRegistrationBean<ApiRateLimitFilter> rateLimitRegistration;

    @Autowired
    @Qualifier("h5VisitorDeviceFilterRegistration")
    private FilterRegistrationBean<H5VisitorDeviceFilter> deviceRegistration;

    @Autowired
    @Qualifier("verifiedDeviceRateLimitFilterRegistration")
    private FilterRegistrationBean<VerifiedDeviceRateLimitFilter> verifiedDeviceRateLimitRegistration;

    @Test
    void contextCreatesDeviceServiceAndPlacesDeviceLimitAfterVerifiedDeviceLookup() {
        assertThat(visitorDeviceService).isNotNull();
        assertThat(rateLimitRegistration.getOrder()).isLessThan(deviceRegistration.getOrder());
        assertThat(deviceRegistration.getOrder()).isLessThan(verifiedDeviceRateLimitRegistration.getOrder());
    }
}
