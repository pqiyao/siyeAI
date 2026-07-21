package com.example.sillyspringboot.compat.h5.entity;

import java.time.LocalDateTime;

public class AppH5VisitorDevice {
    private Long id;
    private String deviceToken;
    private String firstClientUid;
    private String latestClientUid;
    private Long firstUserId;
    private Long latestUserId;
    private String firstIp;
    private String latestIp;
    private String uaHash;
    private String userAgent;
    private Integer anonymousChatAttemptCount;
    private Integer anonymousConversationCreateCount;
    private Integer anonymousCharacterCreateCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public String getFirstClientUid() {
        return firstClientUid;
    }

    public void setFirstClientUid(String firstClientUid) {
        this.firstClientUid = firstClientUid;
    }

    public String getLatestClientUid() {
        return latestClientUid;
    }

    public void setLatestClientUid(String latestClientUid) {
        this.latestClientUid = latestClientUid;
    }

    public Long getFirstUserId() {
        return firstUserId;
    }

    public void setFirstUserId(Long firstUserId) {
        this.firstUserId = firstUserId;
    }

    public Long getLatestUserId() {
        return latestUserId;
    }

    public void setLatestUserId(Long latestUserId) {
        this.latestUserId = latestUserId;
    }

    public String getFirstIp() {
        return firstIp;
    }

    public void setFirstIp(String firstIp) {
        this.firstIp = firstIp;
    }

    public String getLatestIp() {
        return latestIp;
    }

    public void setLatestIp(String latestIp) {
        this.latestIp = latestIp;
    }

    public String getUaHash() {
        return uaHash;
    }

    public void setUaHash(String uaHash) {
        this.uaHash = uaHash;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Integer getAnonymousChatAttemptCount() {
        return anonymousChatAttemptCount;
    }

    public void setAnonymousChatAttemptCount(Integer anonymousChatAttemptCount) {
        this.anonymousChatAttemptCount = anonymousChatAttemptCount;
    }

    public Integer getAnonymousConversationCreateCount() {
        return anonymousConversationCreateCount;
    }

    public void setAnonymousConversationCreateCount(Integer anonymousConversationCreateCount) {
        this.anonymousConversationCreateCount = anonymousConversationCreateCount;
    }

    public Integer getAnonymousCharacterCreateCount() {
        return anonymousCharacterCreateCount;
    }

    public void setAnonymousCharacterCreateCount(Integer anonymousCharacterCreateCount) {
        this.anonymousCharacterCreateCount = anonymousCharacterCreateCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
