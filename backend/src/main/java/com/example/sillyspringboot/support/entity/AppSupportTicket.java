package com.example.sillyspringboot.support.entity;

import java.time.LocalDateTime;

public class AppSupportTicket {
    private Long id;
    private String ticketNo;
    private Long userId;
    private String clientUidSnapshot;
    private String ticketType;
    private String subject;
    private String content;
    private String orderNo;
    private Long characterId;
    private String characterName;
    private String status;
    private String priority;
    private String source;
    private String latestMessagePreview;
    private Integer messageCount;
    private LocalDateTime lastUserReplyAt;
    private LocalDateTime lastAdminReplyAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getClientUidSnapshot() {
        return clientUidSnapshot;
    }

    public void setClientUidSnapshot(String clientUidSnapshot) {
        this.clientUidSnapshot = clientUidSnapshot;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLatestMessagePreview() {
        return latestMessagePreview;
    }

    public void setLatestMessagePreview(String latestMessagePreview) {
        this.latestMessagePreview = latestMessagePreview;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public LocalDateTime getLastUserReplyAt() {
        return lastUserReplyAt;
    }

    public void setLastUserReplyAt(LocalDateTime lastUserReplyAt) {
        this.lastUserReplyAt = lastUserReplyAt;
    }

    public LocalDateTime getLastAdminReplyAt() {
        return lastAdminReplyAt;
    }

    public void setLastAdminReplyAt(LocalDateTime lastAdminReplyAt) {
        this.lastAdminReplyAt = lastAdminReplyAt;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
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
