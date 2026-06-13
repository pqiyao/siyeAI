package com.example.sillyspringboot.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class TelegramBindRequest {

    @NotBlank
    private String initData;

    public String getInitData() {
        return initData;
    }

    public void setInitData(String initData) {
        this.initData = initData;
    }
}
