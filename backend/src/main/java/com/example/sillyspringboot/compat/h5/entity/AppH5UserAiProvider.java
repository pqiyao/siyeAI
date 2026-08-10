package com.example.sillyspringboot.compat.h5.entity;

import java.time.LocalDateTime;

public class AppH5UserAiProvider {

    private Long userId;
    private String providerMode;
    private String providerSource;
    private String modelName;
    private String visionModelName;
    private String audioModelName;
    private String sttModelName;
    private String ttsModelName;
    private String ttsVoiceName;
    private String ttsVoiceTemplateCode;
    private String officialTtsVoiceName;
    private String officialTtsVoiceTemplateCode;
    private String ttsProviderSource;
    private String ttsApiKeyCipher;
    private String ttsCustomUrl;
    private String imageModelName;
    private String imageProviderSource;
    private String imageApiKeyCipher;
    private String imageCustomUrl;
    private String imageCharacterConsistencyMode;
    private String imageReferenceSourceMode;
    private String apiKeyCipher;
    private String customUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getProviderMode() {
        return providerMode;
    }

    public void setProviderMode(String providerMode) {
        this.providerMode = providerMode;
    }

    public String getProviderSource() {
        return providerSource;
    }

    public void setProviderSource(String providerSource) {
        this.providerSource = providerSource;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiKeyCipher() {
        return apiKeyCipher;
    }

    public String getVisionModelName() {
        return visionModelName;
    }

    public void setVisionModelName(String visionModelName) {
        this.visionModelName = visionModelName;
    }

    public String getAudioModelName() {
        return audioModelName;
    }

    public void setAudioModelName(String audioModelName) {
        this.audioModelName = audioModelName;
    }

    public String getSttModelName() {
        return sttModelName;
    }

    public void setSttModelName(String sttModelName) {
        this.sttModelName = sttModelName;
    }

    public String getTtsModelName() {
        return ttsModelName;
    }

    public void setTtsModelName(String ttsModelName) {
        this.ttsModelName = ttsModelName;
    }

    public String getTtsVoiceName() {
        return ttsVoiceName;
    }

    public void setTtsVoiceName(String ttsVoiceName) {
        this.ttsVoiceName = ttsVoiceName;
    }

    public String getTtsVoiceTemplateCode() {
        return ttsVoiceTemplateCode;
    }

    public void setTtsVoiceTemplateCode(String ttsVoiceTemplateCode) {
        this.ttsVoiceTemplateCode = ttsVoiceTemplateCode;
    }

    public String getOfficialTtsVoiceName() {
        return officialTtsVoiceName;
    }

    public void setOfficialTtsVoiceName(String officialTtsVoiceName) {
        this.officialTtsVoiceName = officialTtsVoiceName;
    }

    public String getOfficialTtsVoiceTemplateCode() {
        return officialTtsVoiceTemplateCode;
    }

    public void setOfficialTtsVoiceTemplateCode(String officialTtsVoiceTemplateCode) {
        this.officialTtsVoiceTemplateCode = officialTtsVoiceTemplateCode;
    }

    public String getTtsProviderSource() {
        return ttsProviderSource;
    }

    public void setTtsProviderSource(String ttsProviderSource) {
        this.ttsProviderSource = ttsProviderSource;
    }

    public String getTtsApiKeyCipher() {
        return ttsApiKeyCipher;
    }

    public void setTtsApiKeyCipher(String ttsApiKeyCipher) {
        this.ttsApiKeyCipher = ttsApiKeyCipher;
    }

    public String getTtsCustomUrl() {
        return ttsCustomUrl;
    }

    public void setTtsCustomUrl(String ttsCustomUrl) {
        this.ttsCustomUrl = ttsCustomUrl;
    }

    public String getImageModelName() {
        return imageModelName;
    }

    public void setImageModelName(String imageModelName) {
        this.imageModelName = imageModelName;
    }

    public String getImageProviderSource() {
        return imageProviderSource;
    }

    public void setImageProviderSource(String imageProviderSource) {
        this.imageProviderSource = imageProviderSource;
    }

    public String getImageApiKeyCipher() {
        return imageApiKeyCipher;
    }

    public void setImageApiKeyCipher(String imageApiKeyCipher) {
        this.imageApiKeyCipher = imageApiKeyCipher;
    }

    public String getImageCustomUrl() {
        return imageCustomUrl;
    }

    public void setImageCustomUrl(String imageCustomUrl) {
        this.imageCustomUrl = imageCustomUrl;
    }

    public String getImageCharacterConsistencyMode() {
        return imageCharacterConsistencyMode;
    }

    public void setImageCharacterConsistencyMode(String imageCharacterConsistencyMode) {
        this.imageCharacterConsistencyMode = imageCharacterConsistencyMode;
    }

    public String getImageReferenceSourceMode() {
        return imageReferenceSourceMode;
    }

    public void setImageReferenceSourceMode(String imageReferenceSourceMode) {
        this.imageReferenceSourceMode = imageReferenceSourceMode;
    }

    public void setApiKeyCipher(String apiKeyCipher) {
        this.apiKeyCipher = apiKeyCipher;
    }

    public String getCustomUrl() {
        return customUrl;
    }

    public void setCustomUrl(String customUrl) {
        this.customUrl = customUrl;
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
