package com.example.sillyspringboot.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram WebApp 用户字段（来自 initData 中 user JSON）。
 */
public record TelegramInitDataUser(
        @JsonProperty("id") long id,
        @JsonProperty("username") String username,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("language_code") String languageCode,
        @JsonProperty("photo_url") String photoUrl
) {}
