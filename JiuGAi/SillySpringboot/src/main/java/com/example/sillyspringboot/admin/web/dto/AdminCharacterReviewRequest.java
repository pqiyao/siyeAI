package com.example.sillyspringboot.admin.web.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class AdminCharacterReviewRequest {
    private Long id;
    private List<Long> ids;
    private String reviewStatus;
    private String reviewReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public List<Long> resolveIds() {
        LinkedHashSet<Long> values = new LinkedHashSet<>();
        if (id != null && id > 0) {
            values.add(id);
        }
        if (ids != null) {
            for (Long value : ids) {
                if (value != null && value > 0) {
                    values.add(value);
                }
            }
        }
        return new ArrayList<>(values);
    }
}
