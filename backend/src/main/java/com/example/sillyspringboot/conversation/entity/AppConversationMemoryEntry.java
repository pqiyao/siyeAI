package com.example.sillyspringboot.conversation.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AppConversationMemoryEntry {

    private Long id;
    private Long conversationId;
    private String entryKey;
    private String memoryType;
    private String title;
    private String content;
    private String keywordsJson;
    private String secondaryKeywordsJson;
    private int priority;
    private String position;
    private boolean constantInjection;
    private boolean selective;
    private boolean enabled;
    private BigDecimal confidence;
    private Long sourceMessageFromId;
    private Long sourceMessageToId;
    private LocalDateTime lastActivatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getEntryKey() {
        return entryKey;
    }

    public void setEntryKey(String entryKey) {
        this.entryKey = entryKey;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getKeywordsJson() {
        return keywordsJson;
    }

    public void setKeywordsJson(String keywordsJson) {
        this.keywordsJson = keywordsJson;
    }

    public String getSecondaryKeywordsJson() {
        return secondaryKeywordsJson;
    }

    public void setSecondaryKeywordsJson(String secondaryKeywordsJson) {
        this.secondaryKeywordsJson = secondaryKeywordsJson;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public boolean isConstantInjection() {
        return constantInjection;
    }

    public void setConstantInjection(boolean constantInjection) {
        this.constantInjection = constantInjection;
    }

    public boolean isSelective() {
        return selective;
    }

    public void setSelective(boolean selective) {
        this.selective = selective;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Long getSourceMessageFromId() {
        return sourceMessageFromId;
    }

    public void setSourceMessageFromId(Long sourceMessageFromId) {
        this.sourceMessageFromId = sourceMessageFromId;
    }

    public Long getSourceMessageToId() {
        return sourceMessageToId;
    }

    public void setSourceMessageToId(Long sourceMessageToId) {
        this.sourceMessageToId = sourceMessageToId;
    }

    public LocalDateTime getLastActivatedAt() {
        return lastActivatedAt;
    }

    public void setLastActivatedAt(LocalDateTime lastActivatedAt) {
        this.lastActivatedAt = lastActivatedAt;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
