package com.example.sillyspringboot.integration.sillytavern.dto;

import java.time.LocalDateTime;

public class StModelRouteAdminDto {

    private Long id;
    private String sceneKey;
    private String displayName;
    private String primaryProviderKey;
    private String fallbackProviderKeys;
    private Boolean enabled;
    private String note;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSceneKey() { return sceneKey; }
    public void setSceneKey(String sceneKey) { this.sceneKey = sceneKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPrimaryProviderKey() { return primaryProviderKey; }
    public void setPrimaryProviderKey(String primaryProviderKey) { this.primaryProviderKey = primaryProviderKey; }
    public String getFallbackProviderKeys() { return fallbackProviderKeys; }
    public void setFallbackProviderKeys(String fallbackProviderKeys) { this.fallbackProviderKeys = fallbackProviderKeys; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
