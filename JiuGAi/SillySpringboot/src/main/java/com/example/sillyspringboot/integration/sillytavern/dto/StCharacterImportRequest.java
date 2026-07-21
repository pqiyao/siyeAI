package com.example.sillyspringboot.integration.sillytavern.dto;

/**
 * ST /api/characters/import 请求。
 *
 * @param fileType 固定为 "png"（本阶段仅支持 PNG 角色卡导入）
 * @param preservedName 可选：ST 会用其（去扩展名）作为保留名
 */
public record StCharacterImportRequest(
        String fileType,
        String preservedName
) {}

