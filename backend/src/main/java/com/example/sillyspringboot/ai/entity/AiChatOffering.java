package com.example.sillyspringboot.ai.entity;

import java.time.LocalDateTime;

public class AiChatOffering {
    private Long id;
    private String offeringCode;
    private String displayName;
    private String shortDescription;
    private String description;
    private String tags;
    private String badge;
    private String contextLabel;
    private Integer speedLevel;
    private Integer qualityLevel;
    private String routeKey;
    private Integer vipMinLevel;
    private Boolean recommended;
    private Boolean defaultOffering;
    private Integer sortOrder;
    private Boolean enabled;
    private Boolean maintenance;
    private Long versionNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOfferingCode() { return offeringCode; }
    public void setOfferingCode(String offeringCode) { this.offeringCode = offeringCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }
    public String getContextLabel() { return contextLabel; }
    public void setContextLabel(String contextLabel) { this.contextLabel = contextLabel; }
    public Integer getSpeedLevel() { return speedLevel; }
    public void setSpeedLevel(Integer speedLevel) { this.speedLevel = speedLevel; }
    public Integer getQualityLevel() { return qualityLevel; }
    public void setQualityLevel(Integer qualityLevel) { this.qualityLevel = qualityLevel; }
    public String getRouteKey() { return routeKey; }
    public void setRouteKey(String routeKey) { this.routeKey = routeKey; }
    public Integer getVipMinLevel() { return vipMinLevel; }
    public void setVipMinLevel(Integer vipMinLevel) { this.vipMinLevel = vipMinLevel; }
    public Boolean getRecommended() { return recommended; }
    public void setRecommended(Boolean recommended) { this.recommended = recommended; }
    public Boolean getDefaultOffering() { return defaultOffering; }
    public void setDefaultOffering(Boolean defaultOffering) { this.defaultOffering = defaultOffering; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getMaintenance() { return maintenance; }
    public void setMaintenance(Boolean maintenance) { this.maintenance = maintenance; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

