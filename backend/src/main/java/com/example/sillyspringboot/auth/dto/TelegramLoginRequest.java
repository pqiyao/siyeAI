package com.example.sillyspringboot.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Telegram WebApp initData（querystring 形式）。
 */
public class TelegramLoginRequest {

    @NotBlank
    private String initData;

    public String getInitData() {
        return initData;
    }

    public void setInitData(String initData) {
        this.initData = initData;
    }
}
