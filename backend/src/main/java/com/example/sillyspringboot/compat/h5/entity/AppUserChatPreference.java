package com.example.sillyspringboot.compat.h5.entity;

public class AppUserChatPreference {
    private Long userId;
    private Long characterId;
    private String bubbleJson;
    private String readingJson;
    private String replyFormatJson;
    private Integer revision;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public String getBubbleJson() { return bubbleJson; }
    public void setBubbleJson(String bubbleJson) { this.bubbleJson = bubbleJson; }
    public String getReadingJson() { return readingJson; }
    public void setReadingJson(String readingJson) { this.readingJson = readingJson; }
    public String getReplyFormatJson() { return replyFormatJson; }
    public void setReplyFormatJson(String replyFormatJson) { this.replyFormatJson = replyFormatJson; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
}
