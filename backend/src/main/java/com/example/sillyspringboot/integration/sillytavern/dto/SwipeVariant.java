package com.example.sillyspringboot.integration.sillytavern.dto;

import java.time.Instant;

public record SwipeVariant(String messageId, int variantIndex, String text, Instant createdAt) {}
