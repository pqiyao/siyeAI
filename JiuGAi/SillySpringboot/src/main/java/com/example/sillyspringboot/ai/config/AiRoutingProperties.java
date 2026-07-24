package com.example.sillyspringboot.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-routing")
public class AiRoutingProperties {

    private boolean enabled = false;
    private boolean shadowEnabled = true;
    private int chatCanaryPercent = 0;
    private boolean imageEnabled = false;
    private boolean ttsEnabled = false;
    private boolean sttEnabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isShadowEnabled() { return shadowEnabled; }
    public void setShadowEnabled(boolean shadowEnabled) { this.shadowEnabled = shadowEnabled; }
    public int getChatCanaryPercent() { return chatCanaryPercent; }
    public void setChatCanaryPercent(int chatCanaryPercent) {
        this.chatCanaryPercent = Math.max(0, Math.min(100, chatCanaryPercent));
    }
    public boolean isImageEnabled() { return imageEnabled; }
    public void setImageEnabled(boolean imageEnabled) { this.imageEnabled = imageEnabled; }
    public boolean isTtsEnabled() { return ttsEnabled; }
    public void setTtsEnabled(boolean ttsEnabled) { this.ttsEnabled = ttsEnabled; }
    public boolean isSttEnabled() { return sttEnabled; }
    public void setSttEnabled(boolean sttEnabled) { this.sttEnabled = sttEnabled; }
}
