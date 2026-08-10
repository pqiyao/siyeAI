package com.example.sillyspringboot.ops.entity;

import java.time.LocalDateTime;

public class AppCharacterImagePolicy {

    private Long characterId;
    private Boolean imageEnabled;
    private String defaultMode;
    private String allowedModesJson;
    private String referenceSourceMode;
    private Boolean referenceImagesEnabled;
    private String negativePrompt;
    private LocalDateTime updatedAt;

    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public Boolean getImageEnabled() { return imageEnabled; }
    public void setImageEnabled(Boolean imageEnabled) { this.imageEnabled = imageEnabled; }
    public String getDefaultMode() { return defaultMode; }
    public void setDefaultMode(String defaultMode) { this.defaultMode = defaultMode; }
    public String getAllowedModesJson() { return allowedModesJson; }
    public void setAllowedModesJson(String allowedModesJson) { this.allowedModesJson = allowedModesJson; }
    public String getReferenceSourceMode() { return referenceSourceMode; }
    public void setReferenceSourceMode(String referenceSourceMode) { this.referenceSourceMode = referenceSourceMode; }
    public Boolean getReferenceImagesEnabled() { return referenceImagesEnabled; }
    public void setReferenceImagesEnabled(Boolean referenceImagesEnabled) { this.referenceImagesEnabled = referenceImagesEnabled; }
    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
