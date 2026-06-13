package com.example.sillyspringboot.chat.entity;

import java.time.LocalDateTime;

public class AppMessage {

    private Long id;
    private Long userId;
    private Long conversationId;
    private String role;
    private String messageKind;
    private Long continueFromMessageId;
    private String clientMessageId;
    private String content;
    private String voiceUrl;
    private Integer voiceDurationMs;
    private String stMessageRef;
    private Integer swipeIndex;
    private String status;
    private String errorCode;
    private String traceId;
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

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessageKind() {
        return messageKind;
    }

    public void setMessageKind(String messageKind) {
        this.messageKind = messageKind;
    }

    public Long getContinueFromMessageId() {
        return continueFromMessageId;
    }

    public void setContinueFromMessageId(Long continueFromMessageId) {
        this.continueFromMessageId = continueFromMessageId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getVoiceUrl() {
        return voiceUrl;
    }

    public void setVoiceUrl(String voiceUrl) {
        this.voiceUrl = voiceUrl;
    }

    public Integer getVoiceDurationMs() {
        return voiceDurationMs;
    }

    public void setVoiceDurationMs(Integer voiceDurationMs) {
        this.voiceDurationMs = voiceDurationMs;
    }

    public String getStMessageRef() {
        return stMessageRef;
    }

    public void setStMessageRef(String stMessageRef) {
        this.stMessageRef = stMessageRef;
    }

    public Integer getSwipeIndex() {
        return swipeIndex;
    }

    public void setSwipeIndex(Integer swipeIndex) {
        this.swipeIndex = swipeIndex;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
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

