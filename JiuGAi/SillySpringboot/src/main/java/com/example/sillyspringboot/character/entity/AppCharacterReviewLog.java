package com.example.sillyspringboot.character.entity;

import java.time.LocalDateTime;

public class AppCharacterReviewLog {

    private Long id;
    private Long characterId;
    private String characterName;
    private Long ownerUserId;
    private String ownerClientUid;
    private String reviewStatus;
    private String reviewReason;
    private String operatorName;
    private String batchNo;
    private String eventType;
    private String screeningLevel;
    private String screeningFlags;
    private Integer screeningHits;
    private String summary;
    private String detailJson;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Long characterId) {
        this.characterId = characterId;
    }

    public String getCharacterName() {
        return characterName;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerClientUid() {
        return ownerClientUid;
    }

    public void setOwnerClientUid(String ownerClientUid) {
        this.ownerClientUid = ownerClientUid;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getScreeningLevel() {
        return screeningLevel;
    }

    public void setScreeningLevel(String screeningLevel) {
        this.screeningLevel = screeningLevel;
    }

    public String getScreeningFlags() {
        return screeningFlags;
    }

    public void setScreeningFlags(String screeningFlags) {
        this.screeningFlags = screeningFlags;
    }

    public Integer getScreeningHits() {
        return screeningHits;
    }

    public void setScreeningHits(Integer screeningHits) {
        this.screeningHits = screeningHits;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
