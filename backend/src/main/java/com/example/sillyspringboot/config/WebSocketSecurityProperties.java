package com.example.sillyspringboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketSecurityProperties {

    /**
     * Empty means reusing app.cors.allowed-origin-patterns.
     */
    private String allowedOriginPatterns = "";

    private boolean allowMissingOrigin = false;

    private boolean requireHandshakeToken = true;

    public String getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    public boolean isAllowMissingOrigin() {
        return allowMissingOrigin;
    }

    public void setAllowMissingOrigin(boolean allowMissingOrigin) {
        this.allowMissingOrigin = allowMissingOrigin;
    }

    public boolean isRequireHandshakeToken() {
        return requireHandshakeToken;
    }

    public void setRequireHandshakeToken(boolean requireHandshakeToken) {
        this.requireHandshakeToken = requireHandshakeToken;
    }
}
