package com.example.sillyspringboot.billing.entity;

import java.time.LocalDateTime;

public class AppWalletLedger {
    private Long id;
    private Long userId;
    private String bizType;
    private String orderNo;
    private String bizRef;
    private String idempotencyKey;
    private Integer deltaScore;
    private Integer deltaGoldCoin;
    private String note;
    private LocalDateTime createdAt;

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

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getBizRef() {
        return bizRef;
    }

    public void setBizRef(String bizRef) {
        this.bizRef = bizRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Integer getDeltaScore() {
        return deltaScore;
    }

    public void setDeltaScore(Integer deltaScore) {
        this.deltaScore = deltaScore;
    }

    public Integer getDeltaGoldCoin() {
        return deltaGoldCoin;
    }

    public void setDeltaGoldCoin(Integer deltaGoldCoin) {
        this.deltaGoldCoin = deltaGoldCoin;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
