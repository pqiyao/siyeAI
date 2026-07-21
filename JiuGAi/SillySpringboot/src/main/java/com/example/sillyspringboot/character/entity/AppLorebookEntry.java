package com.example.sillyspringboot.character.entity;

import java.time.LocalDateTime;

public class AppLorebookEntry {

    private Long id;
    private Long characterId;
    private String keywordsCsv;
    private String content;
    private Integer priority;
    private Boolean constantInjection;
    private Integer scanDepth;
    private Boolean enabled;
    private String source;
    private String rawEntryJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public String getKeywordsCsv() {
        return keywordsCsv;
    }

    public void setKeywordsCsv(String keywordsCsv) {
        this.keywordsCsv = keywordsCsv;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getConstantInjection() {
        return constantInjection;
    }

    public void setConstantInjection(Boolean constantInjection) {
        this.constantInjection = constantInjection;
    }

    public Integer getScanDepth() {
        return scanDepth;
    }

    public void setScanDepth(Integer scanDepth) {
        this.scanDepth = scanDepth;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRawEntryJson() {
        return rawEntryJson;
    }

    public void setRawEntryJson(String rawEntryJson) {
        this.rawEntryJson = rawEntryJson;
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
