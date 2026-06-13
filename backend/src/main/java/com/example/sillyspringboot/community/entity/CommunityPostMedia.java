package com.example.sillyspringboot.community.entity;

public class CommunityPostMedia {

    private Long id;
    private Long postId;
    private Long userId;
    private String mediaKey;
    private String storageProvider;
    private String mediaUrlSnapshot;
    private String mediaType;
    private Integer sortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMediaKey() {
        return mediaKey;
    }

    public void setMediaKey(String mediaKey) {
        this.mediaKey = mediaKey;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getMediaUrlSnapshot() {
        return mediaUrlSnapshot;
    }

    public void setMediaUrlSnapshot(String mediaUrlSnapshot) {
        this.mediaUrlSnapshot = mediaUrlSnapshot;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
