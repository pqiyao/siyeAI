package com.example.sillyspringboot.auth.dto;

public record H5PasswordResetRequestResponse(
        String requestId,
        boolean deliveryAvailable,
        long expiresInSeconds,
        long retryAfterSeconds
) {
}
