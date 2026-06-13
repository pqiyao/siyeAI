package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.character.service.CharacterCatalogService;
import com.example.sillyspringboot.character.service.EmbeddedLorebookSyncService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * H5 compat: /api/v1/characters/import-sillytavern-png
 */
@RestController
@RequestMapping("/api/v1/characters")
public class ApiV1CharacterImportPngController {

    private final StAdapter stAdapter;
    private final CharacterCatalogService catalog;
    private final H5VisitorTrialGuardService visitorTrialGuardService;
    private final EmbeddedLorebookSyncService embeddedLorebookSyncService;

    public ApiV1CharacterImportPngController(
            StAdapter stAdapter,
            CharacterCatalogService catalog,
            H5VisitorTrialGuardService visitorTrialGuardService,
            EmbeddedLorebookSyncService embeddedLorebookSyncService
    ) {
        this.stAdapter = stAdapter;
        this.catalog = catalog;
        this.visitorTrialGuardService = visitorTrialGuardService;
        this.embeddedLorebookSyncService = embeddedLorebookSyncService;
    }

    @PostMapping(value = "/import-sillytavern-png", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiV1Result<Map<String, Object>> importPng(
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        try {
            visitorTrialGuardService.guardAnonymousCharacterCreation(H5VisitorDeviceService.resolveClientUid(request));
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件不能为空");
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".png")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前仅支持 ST 导出的角色卡 PNG，不支持普通立绘图片或其他格式");
            }

            Object raw = stAdapter.importCharacterPng(
                    file.getBytes(),
                    originalFilename,
                    new StCharacterImportRequest("png", null)
            );
            String importError = extractImportError(raw);
            if (!importError.isBlank()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, importError);
            }

            String avatarUrl = extractImportedAvatarUrl(raw);
            if (avatarUrl.isBlank()) {
                throw new BusinessException(
                        ErrorCode.UPSTREAM_ERROR,
                        "PNG 导入失败：SillyTavern 没有返回角色文件名，请确认这是一张可导入的 ST 角色卡 PNG"
                );
            }

            StCharacterDetail detail = stAdapter.getCharacter(new StCharacterGetRequest(avatarUrl));
            if (detail == null || detail.name() == null || detail.name().isBlank()) {
                throw new BusinessException(
                        ErrorCode.UPSTREAM_ERROR,
                        "PNG 导入成功，但读取 ST 角色详情失败，请刷新后查看是否已在 ST 角色列表中生成"
                );
            }

            var row = catalog.upsertImportedCharacter(avatarUrl, detail);
            int importedLorebookEntries = row == null || row.getId() == null
                    ? 0
                    : embeddedLorebookSyncService.replaceEmbeddedLorebook(row.getId(), detail.embeddedCharacterBookJson());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", row == null ? 0 : row.getId());
            data.put("avatarUrl", row == null ? "" : row.getAvatarUrl());
            data.put("stAvatarUrl", row == null ? avatarUrl : row.getStAvatarUrl());
            data.put("importedTags", detail.tags());
            data.put("importedWorldNames", detail.worldNames());
            data.put("importedLorebookEntries", importedLorebookEntries);
            return ApiV1Result.ok(data);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, resolveImportErrorMessage(ex));
        }
    }

    private static String extractImportedAvatarUrl(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object fileName = map.get("file_name");
            if (fileName != null) {
                return String.valueOf(fileName).trim();
            }
            String avatar = firstNonBlank(
                    stringValue(map.get("avatar")),
                    stringValue(map.get("avatar_url")),
                    stringValue(map.get("avatarUrl"))
            );
            if (!avatar.isBlank()) {
                return avatar.trim();
            }
        }
        return "";
    }

    private static String extractImportError(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return "";
        }
        Object error = map.get("error");
        if (error instanceof Boolean b && !b) {
            return "";
        }
        String message = firstNonBlank(
                stringValue(map.get("message")),
                stringValue(map.get("detail")),
                stringValue(map.get("error"))
        );
        return normalizeImportMessage(message);
    }

    private static String resolveImportErrorMessage(Throwable error) {
        Throwable cursor = error;
        String lastMessage = "";
        while (cursor != null) {
            if (cursor instanceof RestClientResponseException responseException) {
                String responseBody = responseException.getResponseBodyAsString();
                String normalized = normalizeImportMessage(responseBody);
                if (!normalized.isBlank()) {
                    return normalized;
                }
            }
            String message = normalizeImportMessage(cursor.getMessage());
            if (!message.isBlank()) {
                lastMessage = message;
            }
            cursor = cursor.getCause();
        }
        return lastMessage.isBlank() ? "PNG 导入失败，请确认这是一张可导入的 ST 角色卡 PNG" : lastMessage;
    }

    private static String normalizeImportMessage(String raw) {
        String message = stringValue(raw);
        if (message.isBlank()) {
            return "";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("failed to read character data")
                || lower.contains("unexpected end of json input")
                || lower.contains("json.parse")
                || lower.contains("invalid png")
                || lower.contains("metadata")) {
            return "这不是可导入的 ST 角色卡 PNG，或者图片元数据已经丢失。请直接使用 ST 导出的原始角色卡 PNG。";
        }
        if (lower.contains("unsupported format")) {
            return "当前仅支持 ST 导出的角色卡 PNG。";
        }
        if (lower.contains("too large")) {
            return "上传文件过大，请更换更小的角色卡 PNG 再试。";
        }
        if (message.contains("服务暂时不可用")) {
            return "SillyTavern 当前不可用，请先确认 ST 已正常启动。";
        }
        return message;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
