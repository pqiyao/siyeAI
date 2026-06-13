package com.example.sillyspringboot.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * 等效登录 token（不是必须用库的 JWT，实现等效签名 token）。
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

    @NotBlank
    private String secret;

    private long tokenTtlSeconds = 2592000; // 30 days

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }
}
