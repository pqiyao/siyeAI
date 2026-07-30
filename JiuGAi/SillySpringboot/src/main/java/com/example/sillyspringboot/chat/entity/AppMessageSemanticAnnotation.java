package com.example.sillyspringboot.chat.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AppMessageSemanticAnnotation {
    private Long messageId;
    private String contentHash;
    private Integer schemaVersion;
    private String classifierVersion;
    private String status;
    private String segmentsJson;
    private BigDecimal confidence;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getClassifierVersion() { return classifierVersion; }
    public void setClassifierVersion(String classifierVersion) { this.classifierVersion = classifierVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSegmentsJson() { return segmentsJson; }
    public void setSegmentsJson(String segmentsJson) { this.segmentsJson = segmentsJson; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
