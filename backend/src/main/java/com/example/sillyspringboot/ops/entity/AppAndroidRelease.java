package com.example.sillyspringboot.ops.entity;

import java.time.LocalDateTime;

public class AppAndroidRelease {
    private Long id;
    private String appId;
    private String packageName;
    private String channelCode;
    private String versionName;
    private Integer versionCode;
    private String updateMode;
    private Integer minSupportedVersionCode;
    private Integer policyRevision;
    private String title;
    private String changelog;
    private String downloadUrl;
    private Integer remindLaterHours;
    private Long apkSizeBytes;
    private String apkSha256;
    private String status;
    private LocalDateTime publishAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public Integer getVersionCode() { return versionCode; }
    public void setVersionCode(Integer versionCode) { this.versionCode = versionCode; }
    public String getUpdateMode() { return updateMode; }
    public void setUpdateMode(String updateMode) { this.updateMode = updateMode; }
    public Integer getMinSupportedVersionCode() { return minSupportedVersionCode; }
    public void setMinSupportedVersionCode(Integer minSupportedVersionCode) { this.minSupportedVersionCode = minSupportedVersionCode; }
    public Integer getPolicyRevision() { return policyRevision; }
    public void setPolicyRevision(Integer policyRevision) { this.policyRevision = policyRevision; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public Integer getRemindLaterHours() { return remindLaterHours; }
    public void setRemindLaterHours(Integer remindLaterHours) { this.remindLaterHours = remindLaterHours; }
    public Long getApkSizeBytes() { return apkSizeBytes; }
    public void setApkSizeBytes(Long apkSizeBytes) { this.apkSizeBytes = apkSizeBytes; }
    public String getApkSha256() { return apkSha256; }
    public void setApkSha256(String apkSha256) { this.apkSha256 = apkSha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPublishAt() { return publishAt; }
    public void setPublishAt(LocalDateTime publishAt) { this.publishAt = publishAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
