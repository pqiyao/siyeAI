package com.example.sillyspringboot.character.entity;

public class AppCharacterOpeningSegment {
    private Long id;
    private Long openingId;
    private Long speakerMemberId;
    private String speakerType;
    private String content;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOpeningId() { return openingId; }
    public void setOpeningId(Long openingId) { this.openingId = openingId; }
    public Long getSpeakerMemberId() { return speakerMemberId; }
    public void setSpeakerMemberId(Long speakerMemberId) { this.speakerMemberId = speakerMemberId; }
    public String getSpeakerType() { return speakerType; }
    public void setSpeakerType(String speakerType) { this.speakerType = speakerType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
