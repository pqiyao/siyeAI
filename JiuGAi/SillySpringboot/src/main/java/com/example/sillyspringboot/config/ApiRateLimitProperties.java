package com.example.sillyspringboot.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.api-rate-limit")
public class ApiRateLimitProperties {

    private boolean enabled = true;

    @Min(1)
    private int windowSeconds = 60;

    @Min(1)
    private int maxRequestsPerWindow = 180;

    @Min(1)
    private int maxRequestsPerIpWindow = 900;

    @Min(1)
    private int securityEventDedupSeconds = 60;

    private List<String> trustedProxyCidrs = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getMaxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    public void setMaxRequestsPerWindow(int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    public int getMaxRequestsPerIpWindow() {
        return maxRequestsPerIpWindow;
    }

    public void setMaxRequestsPerIpWindow(int maxRequestsPerIpWindow) {
        this.maxRequestsPerIpWindow = maxRequestsPerIpWindow;
    }

    public int getSecurityEventDedupSeconds() {
        return securityEventDedupSeconds;
    }

    public void setSecurityEventDedupSeconds(int securityEventDedupSeconds) {
        this.securityEventDedupSeconds = securityEventDedupSeconds;
    }

    public List<String> getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    public void setTrustedProxyCidrs(List<String> trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs == null ? new ArrayList<>() : new ArrayList<>(trustedProxyCidrs);
    }
}
