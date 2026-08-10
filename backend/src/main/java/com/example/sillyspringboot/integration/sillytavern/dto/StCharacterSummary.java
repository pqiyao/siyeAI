package com.example.sillyspringboot.integration.sillytavern.dto;

/**
 * ST /api/characters/all 返回的“浅”角色信息（字段集会随 ST 版本变化，此处仅保留联调必需字段）。
 */
public record StCharacterSummary(
        String name,
        String avatar,
        String description,
        Long dateAddedMs
) {}

