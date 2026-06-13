package com.example.sillyspringboot.integration.sillytavern.dto;

import java.util.List;

/**
 * ST /api/characters/get 返回的“深”角色信息（字段集会随 ST 版本变化，此处先保留联调必需字段）。
 */
public record StCharacterDetail(
        String name,
        String avatar,
        String description,
        String scenario,
        String firstMes,
        String personality,
        List<String> tags,
        List<String> alternateGreetings,
        String mesExample,
        String systemPrompt,
        String postHistoryInstructions,
        String creatorNotes,
        String creator,
        List<String> worldNames,
        String embeddedCharacterBookJson,
        String rawJson
) {}
