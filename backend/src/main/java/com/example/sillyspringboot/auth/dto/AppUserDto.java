package com.example.sillyspringboot.auth.dto;

public record AppUserDto(
        Long appUserId,
        Long telegramUserId,
        String username,
        String displayName,
        String avatarUrl,
        boolean telegramBound
) {}
