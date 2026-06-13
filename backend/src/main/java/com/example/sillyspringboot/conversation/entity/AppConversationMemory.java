package com.example.sillyspringboot.conversation.entity;

import java.time.LocalDateTime;

public class AppConversationMemory {

    private Long conversationId;
    private String summaryPreview;
    private int factsCount;
    private String memoryWorldName;
    private int entryCount;
    private int enabledEntryCount;
    private Long lastSourceMessageId;
    private int lastRefreshedMessageCount;
    private LocalDateTime lastSyncedAt;
    private String syncStatus;
    private String syncError;
    private LocalDateTime updatedAt;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getSummaryPreview() {
        return summaryPreview;
    }

    public void setSummaryPreview(String summaryPreview) {
        this.summaryPreview = summaryPreview;
    }

    public int getFactsCount() {
        return factsCount;
    }

    public void setFactsCount(int factsCount) {
        this.factsCount = factsCount;
    }

    public String getMemoryWorldName() {
        return memoryWorldName;
    }

    public void setMemoryWorldName(String memoryWorldName) {
        this.memoryWorldName = memoryWorldName;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

    public int getEnabledEntryCount() {
        return enabledEntryCount;
    }

    public void setEnabledEntryCount(int enabledEntryCount) {
        this.enabledEntryCount = enabledEntryCount;
    }

    public Long getLastSourceMessageId() {
        return lastSourceMessageId;
    }

    public void setLastSourceMessageId(Long lastSourceMessageId) {
        this.lastSourceMessageId = lastSourceMessageId;
    }

    public int getLastRefreshedMessageCount() {
        return lastRefreshedMessageCount;
    }

    public void setLastRefreshedMessageCount(int lastRefreshedMessageCount) {
        this.lastRefreshedMessageCount = lastRefreshedMessageCount;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getSyncError() {
        return syncError;
    }

    public void setSyncError(String syncError) {
        this.syncError = syncError;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
