package com.example.sillyspringboot.auth.dto;

/**
 * initData 校验通过后的解析结果。
 */
public record TelegramAuthPayload(
        String queryId,
        long authDateEpochSeconds,
        TelegramInitDataUser user
) {}
