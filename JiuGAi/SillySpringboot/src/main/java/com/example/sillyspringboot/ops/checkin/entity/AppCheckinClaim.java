package com.example.sillyspringboot.ops.checkin.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AppCheckinClaim {

    private Long id;
    private Long userId;
    private Long activityId;
    private LocalDate bizDate;
    private Integer streakDay;
    private Integer rewardScore;
    private Integer rewardGold;
    private Integer rewardChatBonus;
    private Integer rewardImageBonus;
    private String rewardJson;
    private String ledgerIdempotencyKey;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public LocalDate getBizDate() { return bizDate; }
    public void setBizDate(LocalDate bizDate) { this.bizDate = bizDate; }
    public Integer getStreakDay() { return streakDay; }
    public void setStreakDay(Integer streakDay) { this.streakDay = streakDay; }
    public Integer getRewardScore() { return rewardScore; }
    public void setRewardScore(Integer rewardScore) { this.rewardScore = rewardScore; }
    public Integer getRewardGold() { return rewardGold; }
    public void setRewardGold(Integer rewardGold) { this.rewardGold = rewardGold; }
    public Integer getRewardChatBonus() { return rewardChatBonus; }
    public void setRewardChatBonus(Integer rewardChatBonus) { this.rewardChatBonus = rewardChatBonus; }
    public Integer getRewardImageBonus() { return rewardImageBonus; }
    public void setRewardImageBonus(Integer rewardImageBonus) { this.rewardImageBonus = rewardImageBonus; }
    public String getRewardJson() { return rewardJson; }
    public void setRewardJson(String rewardJson) { this.rewardJson = rewardJson; }
    public String getLedgerIdempotencyKey() { return ledgerIdempotencyKey; }
    public void setLedgerIdempotencyKey(String ledgerIdempotencyKey) { this.ledgerIdempotencyKey = ledgerIdempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
