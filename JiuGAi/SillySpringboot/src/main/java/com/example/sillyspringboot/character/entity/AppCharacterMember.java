package com.example.sillyspringboot.character.entity;

import java.time.LocalDateTime;

public class AppCharacterMember {
    private Long id;
    private Long characterId;
    private String name;
    private String tagline;
    private String persona;
    private String avatarUrl;
    private String voiceConfigJson;
    private String imageReferenceUrl;
    private Boolean primaryMember;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public String getPersona() { return persona; }
    public void setPersona(String persona) { this.persona = persona; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getVoiceConfigJson() { return voiceConfigJson; }
    public void setVoiceConfigJson(String voiceConfigJson) { this.voiceConfigJson = voiceConfigJson; }
    public String getImageReferenceUrl() { return imageReferenceUrl; }
    public void setImageReferenceUrl(String imageReferenceUrl) { this.imageReferenceUrl = imageReferenceUrl; }
    public Boolean getPrimaryMember() { return primaryMember; }
    public void setPrimaryMember(Boolean primaryMember) { this.primaryMember = primaryMember; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
