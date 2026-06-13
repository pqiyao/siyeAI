package com.example.sillyspringboot.conversation.entity;

import java.time.LocalDateTime;

public class AppConversationStBinding {

    private Long id;
    private Long userId;
    private Long characterId;
    private Long conversationId;

    private String stRuntimeProfile;
    private String stCharacterRef;
    private String stChatRef;
    private String stAvatarUrl;
    private String stChatFileName;
    /**
     * 会话级世界书绑定（ST worldinfo 文件名列表），JSON 数组字符串，如 ["New World (1)"]。
     */
    private String stWorldNamesJson;
    private String stDisplayNameOverride;

    private String status;
    private LocalDateTime lastSyncedAt;

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

    public Long getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Long characterId) {
        this.characterId = characterId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getStRuntimeProfile() {
        return stRuntimeProfile;
    }

    public void setStRuntimeProfile(String stRuntimeProfile) {
        this.stRuntimeProfile = stRuntimeProfile;
    }

    public String getStCharacterRef() {
        return stCharacterRef;
    }

    public void setStCharacterRef(String stCharacterRef) {
        this.stCharacterRef = stCharacterRef;
    }

    public String getStChatRef() {
        return stChatRef;
    }

    public void setStChatRef(String stChatRef) {
        this.stChatRef = stChatRef;
    }

    public String getStAvatarUrl() {
        return stAvatarUrl;
    }

    public void setStAvatarUrl(String stAvatarUrl) {
        this.stAvatarUrl = stAvatarUrl;
    }

    public String getStChatFileName() {
        return stChatFileName;
    }

    public void setStChatFileName(String stChatFileName) {
        this.stChatFileName = stChatFileName;
    }

    public String getStWorldNamesJson() {
        return stWorldNamesJson;
    }

    public void setStWorldNamesJson(String stWorldNamesJson) {
        this.stWorldNamesJson = stWorldNamesJson;
    }

    public String getStDisplayNameOverride() {
        return stDisplayNameOverride;
    }

    public void setStDisplayNameOverride(String stDisplayNameOverride) {
        this.stDisplayNameOverride = stDisplayNameOverride;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
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
