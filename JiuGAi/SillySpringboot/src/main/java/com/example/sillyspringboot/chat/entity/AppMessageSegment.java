package com.example.sillyspringboot.chat.entity;

import java.time.LocalDateTime;

public class AppMessageSegment {
    private Long id;
    private Long messageId;
    private Integer segmentIndex;
    private String segmentType;
    private Long speakerMemberId;
    private String speakerNameSnapshot;
    private String speakerAvatarSnapshot;
    private String content;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Integer getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(Integer segmentIndex) { this.segmentIndex = segmentIndex; }
    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String segmentType) { this.segmentType = segmentType; }
    public Long getSpeakerMemberId() { return speakerMemberId; }
    public void setSpeakerMemberId(Long speakerMemberId) { this.speakerMemberId = speakerMemberId; }
    public String getSpeakerNameSnapshot() { return speakerNameSnapshot; }
    public void setSpeakerNameSnapshot(String speakerNameSnapshot) { this.speakerNameSnapshot = speakerNameSnapshot; }
    public String getSpeakerAvatarSnapshot() { return speakerAvatarSnapshot; }
    public void setSpeakerAvatarSnapshot(String speakerAvatarSnapshot) { this.speakerAvatarSnapshot = speakerAvatarSnapshot; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
