package com.example.sillyspringboot.ai.entity;

public class AiChatModelSettings {
    private Long id;
    private Boolean enabled;
    private Boolean shadowEnabled;
    private Integer canaryPercent;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getShadowEnabled() { return shadowEnabled; }
    public void setShadowEnabled(Boolean shadowEnabled) { this.shadowEnabled = shadowEnabled; }
    public Integer getCanaryPercent() { return canaryPercent; }
    public void setCanaryPercent(Integer canaryPercent) { this.canaryPercent = canaryPercent; }
}

