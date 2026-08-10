package com.example.sillyspringboot.conversation.entity;

import java.time.LocalDateTime;

public class AppConversationBranch {

    private Long id;
    private Long conversationId;
    private Long userId;
    private Long parentBranchId;
    private Long forkMessageId;
    private Integer openingVariantIndex;
    private String title;
    private boolean defaultBranch;
    private long memorySourceRevision;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getParentBranchId() {
        return parentBranchId;
    }

    public void setParentBranchId(Long parentBranchId) {
        this.parentBranchId = parentBranchId;
    }

    public Long getForkMessageId() {
        return forkMessageId;
    }

    public void setForkMessageId(Long forkMessageId) {
        this.forkMessageId = forkMessageId;
    }

    public Integer getOpeningVariantIndex() {
        return openingVariantIndex;
    }

    public void setOpeningVariantIndex(Integer openingVariantIndex) {
        this.openingVariantIndex = openingVariantIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(boolean defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public long getMemorySourceRevision() {
        return memorySourceRevision;
    }

    public void setMemorySourceRevision(long memorySourceRevision) {
        this.memorySourceRevision = memorySourceRevision;
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
