package com.example.sillyspringboot.ops.entity;

import java.time.LocalDateTime;

public class AppUserTtsVoiceInstance {

    private Long id;
    private Long userId;
    private String templateCode;
    private String providerSource;
    private String baseUrl;
    private String modelName;
    private String configFingerprint;
    private String voiceUri;
    private String status;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getProviderSource() {
        return providerSource;
    }

    public void setProviderSource(String providerSource) {
        this.providerSource = providerSource;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getConfigFingerprint() {
        return configFingerprint;
    }

    public void setConfigFingerprint(String configFingerprint) {
        this.configFingerprint = configFingerprint;
    }

    public String getVoiceUri() {
        return voiceUri;
    }

    public void setVoiceUri(String voiceUri) {
        this.voiceUri = voiceUri;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
