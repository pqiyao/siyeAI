package com.example.sillyspringboot.community.entity;

public class CommunityPost {

    private Long id;
    private Long userId;
    private String content;
    private String sourceType;
    private String status;
    private Integer openComments;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getOpenComments() {
        return openComments;
    }

    public void setOpenComments(Integer openComments) {
        this.openComments = openComments;
    }
}
