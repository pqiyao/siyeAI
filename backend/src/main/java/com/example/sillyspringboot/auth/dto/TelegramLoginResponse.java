package com.example.sillyspringboot.auth.dto;

/**
 * Phase 3（A）最小登录响应：token + 用户信息。
 */
public record TelegramLoginResponse(
        String token,
        long tokenExpiresAtEpochSeconds,
        AppUserDto user
) {}
