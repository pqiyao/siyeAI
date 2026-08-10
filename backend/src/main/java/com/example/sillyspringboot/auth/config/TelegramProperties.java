package com.example.sillyspringboot.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Telegram WebApp initData 校验配置。
 * <p>
 * 注意：不在启动期强校验 botToken，避免开发/测试环境启动失败；
 * 仅在实际调用登录接口时进行校验并在失败时返回标准化错误。
 */
@Validated
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    /**
     * Telegram Bot Token（用于 initData HMAC 校验）。
     */
    private String botToken = "";

    /**
     * auth_date 最大允许偏差（秒），默认 24h。
     */
    private long authMaxAgeSeconds = 86400;

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public long getAuthMaxAgeSeconds() {
        return authMaxAgeSeconds;
    }

    public void setAuthMaxAgeSeconds(long authMaxAgeSeconds) {
        this.authMaxAgeSeconds = authMaxAgeSeconds;
    }
}
