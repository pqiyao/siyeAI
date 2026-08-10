package com.example.sillyspringboot.auth.dto;

public record AppAuthSessionResponse(
        String token,
        long tokenExpiresAtEpochSeconds,
        AppUserDto user
) {}
