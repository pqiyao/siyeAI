package com.example.sillyspringboot.ops.entity;

import java.time.LocalDateTime;

public class AppTtsVoiceTemplate {

    private Long id;
    private String templateCode;
    private String displayName;
    private String providerSource;
    private String ttsModelName;
    private String description;
    private String referenceAudioUrl;
    private String coverImageUrl;
    private String sampleScript;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProviderSource() {
        return providerSource;
    }

    public void setProviderSource(String providerSource) {
        this.providerSource = providerSource;
    }

    public String getTtsModelName() {
        return ttsModelName;
    }

    public void setTtsModelName(String ttsModelName) {
        this.ttsModelName = ttsModelName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReferenceAudioUrl() {
        return referenceAudioUrl;
    }

    public void setReferenceAudioUrl(String referenceAudioUrl) {
        this.referenceAudioUrl = referenceAudioUrl;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getSampleScript() {
        return sampleScript;
    }

    public void setSampleScript(String sampleScript) {
        this.sampleScript = sampleScript;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
