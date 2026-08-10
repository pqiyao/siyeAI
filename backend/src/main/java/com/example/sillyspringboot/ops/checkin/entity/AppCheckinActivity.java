package com.example.sillyspringboot.ops.checkin.entity;

import java.time.LocalDateTime;

public class AppCheckinActivity {

    private Long id;
    private String code;
    private String name;
    private Integer enabled;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String audience;
    private Integer rewardScore;
    private Integer rewardGold;
    private Integer rewardChatBonus;
    private Integer rewardImageBonus;
    private String streakRulesJson;
    private String timezone;
    private String note;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Integer getRewardScore() { return rewardScore; }
    public void setRewardScore(Integer rewardScore) { this.rewardScore = rewardScore; }
    public Integer getRewardGold() { return rewardGold; }
    public void setRewardGold(Integer rewardGold) { this.rewardGold = rewardGold; }
    public Integer getRewardChatBonus() { return rewardChatBonus; }
    public void setRewardChatBonus(Integer rewardChatBonus) { this.rewardChatBonus = rewardChatBonus; }
    public Integer getRewardImageBonus() { return rewardImageBonus; }
    public void setRewardImageBonus(Integer rewardImageBonus) { this.rewardImageBonus = rewardImageBonus; }
    public String getStreakRulesJson() { return streakRulesJson; }
    public void setStreakRulesJson(String streakRulesJson) { this.streakRulesJson = streakRulesJson; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
