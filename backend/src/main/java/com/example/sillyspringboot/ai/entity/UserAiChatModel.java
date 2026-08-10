package com.example.sillyspringboot.ai.entity;

import java.time.LocalDateTime;

public class UserAiChatModel {
    private Long id;
    private Long userId;
    private String modelName;
    private String displayName;
    private Integer sortOrder;
    private Boolean defaultModel;
    private Boolean enabled;
    private String lastTestStatus;
    private LocalDateTime lastTestedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getDefaultModel() { return defaultModel; }
    public void setDefaultModel(Boolean defaultModel) { this.defaultModel = defaultModel; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getLastTestStatus() { return lastTestStatus; }
    public void setLastTestStatus(String lastTestStatus) { this.lastTestStatus = lastTestStatus; }
    public LocalDateTime getLastTestedAt() { return lastTestedAt; }
    public void setLastTestedAt(LocalDateTime lastTestedAt) { this.lastTestedAt = lastTestedAt; }
}

