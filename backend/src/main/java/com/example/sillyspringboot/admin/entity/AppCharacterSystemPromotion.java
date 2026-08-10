package com.example.sillyspringboot.admin.entity;

import java.time.LocalDateTime;

public class AppCharacterSystemPromotion {
    private Long id;
    private Long sourceCharacterId;
    private Long sourceUserId;
    private Long targetCharacterId;
    private Boolean keepCreatorAttribution;
    private String promotedBy;
    private LocalDateTime promotedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceCharacterId() { return sourceCharacterId; }
    public void setSourceCharacterId(Long sourceCharacterId) { this.sourceCharacterId = sourceCharacterId; }
    public Long getSourceUserId() { return sourceUserId; }
    public void setSourceUserId(Long sourceUserId) { this.sourceUserId = sourceUserId; }
    public Long getTargetCharacterId() { return targetCharacterId; }
    public void setTargetCharacterId(Long targetCharacterId) { this.targetCharacterId = targetCharacterId; }
    public Boolean getKeepCreatorAttribution() { return keepCreatorAttribution; }
    public void setKeepCreatorAttribution(Boolean keepCreatorAttribution) { this.keepCreatorAttribution = keepCreatorAttribution; }
    public String getPromotedBy() { return promotedBy; }
    public void setPromotedBy(String promotedBy) { this.promotedBy = promotedBy; }
    public LocalDateTime getPromotedAt() { return promotedAt; }
    public void setPromotedAt(LocalDateTime promotedAt) { this.promotedAt = promotedAt; }
}
