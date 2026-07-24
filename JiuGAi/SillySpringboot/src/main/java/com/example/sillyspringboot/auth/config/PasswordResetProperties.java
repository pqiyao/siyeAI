package com.example.sillyspringboot.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth.password-reset")
public class PasswordResetProperties {

    private boolean enabled;
    private String from = "";
    private long tokenTtlSeconds = 600;
    private long requestCooldownSeconds = 60;
    private int maxAttempts = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public long getRequestCooldownSeconds() {
        return requestCooldownSeconds;
    }

    public void setRequestCooldownSeconds(long requestCooldownSeconds) {
        this.requestCooldownSeconds = requestCooldownSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
