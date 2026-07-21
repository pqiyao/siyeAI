package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.service.AdminCharacterPageResult;
import com.example.sillyspringboot.admin.service.AdminJiugaiCharacterService;
import com.example.sillyspringboot.admin.web.dto.AdminCharacterPayload;
import com.example.sillyspringboot.admin.web.dto.AdminCharacterReviewRequest;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.service.CharacterCatalogService;
import com.example.sillyspringboot.character.service.EmbeddedLorebookSyncService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StUnavailableException;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/character")
@AdminPermitted("content:character:view")
public class AdminJiugaiCharacterController {

    private final AdminJiugaiCharacterService adminCharacterService;
    private final AppCharacterMapper characterMapper;
    private final StAdapter stAdapter;
    private final CharacterCatalogService catalogService;
    private final StWorldbookCatalogService worldbookCatalogService;
    private final EmbeddedLorebookSyncService embeddedLorebookSyncService;

    public AdminJiugaiCharacterController(
            AdminJiugaiCharacterService adminCharacterService,
            AppCharacterMapper characterMapper,
            StAdapter stAdapter,
            CharacterCatalogService catalogService,
            StWorldbookCatalogService worldbookCatalogService,
            EmbeddedLorebookSyncService embeddedLorebookSyncService
    ) {
        this.adminCharacterService = adminCharacterService;
        this.characterMapper = characterMapper;
        this.stAdapter = stAdapter;
        this.catalogService = catalogService;
        this.worldbookCatalogService = worldbookCatalogService;
        this.embeddedLorebookSyncService = embeddedLorebookSyncService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String ownerClientUid,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false, defaultValue = "system") String scope
    ) {
        String safeScope = scope == null ? "system" : scope.trim().toLowerCase(Locale.ROOT);
        if (!"user".equals(safeScope) && (ownerClientUid == null || ownerClientUid.isBlank())) {
            catalogService.refreshFeedFromStNow();
        }
        AdminCharacterPageResult result =
                adminCharacterService.listPage(pageNum, pageSize, name, scope, ownerClientUid, reviewStatus);
        return AdminAjaxResult.table(result.total(), result.rows());
    }

    @GetMapping("/user-created-stats")
    public Map<String, Object> userCreatedStats(@RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> data = adminCharacterService.userCreatedStats(limit);
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", data);
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        AppCharacter row = characterMapper.findById(id);
        if (row == null || row.getDeletedAt() != null) {
            return AdminAjaxResult.error("角色不存在");
        }
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", adminCharacterService.toFormMap(row));
        return result;
    }

    @GetMapping("/worldbooks/options")
    public Map<String, Object> worldbookOptions() {
        return AdminAjaxResult.okData(worldbookCatalogService.listAvailableWorldbooks());
    }

    @PostMapping
    @AdminPermitted("content:character:edit")
    public Map<String, Object> add(@RequestBody AdminCharacterPayload body) {
        if (body == null || body.getName() == null || body.getName().isBlank()) {
            return AdminAjaxResult.error("名称不能为空");
        }
        body.setId(null);
        try {
            AppCharacter saved = adminCharacterService.createFromPayload(body);
            Map<String, Object> result = AdminAjaxResult.ok("新增成功");
            result.put("data", adminCharacterService.toFormMap(saved));
            return result;
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping
    @AdminPermitted("content:character:edit")
    public Map<String, Object> update(@RequestBody AdminCharacterPayload body) {
        if (body == null || body.getId() == null) {
            return AdminAjaxResult.error("缺少 id");
        }
        try {
            AppCharacter saved = adminCharacterService.updateFromPayload(body);
            if (saved == null) {
                return AdminAjaxResult.error("角色不存在");
            }
            return AdminAjaxResult.ok("修改成功");
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/review")
    @AdminPermitted("content:review:edit")
    public Map<String, Object> review(@RequestBody AdminCharacterReviewRequest body) {
        if (body == null || body.resolveIds().isEmpty()) {
            return AdminAjaxResult.error("缺少角色 id");
        }
        if (body.getReviewStatus() == null || body.getReviewStatus().isBlank()) {
            return AdminAjaxResult.error("缺少审核状态");
        }
        try {
            List<AppCharacter> savedList =
                    adminCharacterService.reviewCharacters(
                            body.resolveIds(),
                            body.getReviewStatus(),
                            body.getReviewReason(),
                            "admin"
                    );
            Map<String, Object> result =
                    AdminAjaxResult.ok(savedList.size() > 1 ? "批量审核结果已保存" : "审核结果已保存");
            if (savedList.size() == 1) {
                result.put("data", adminCharacterService.toFormMap(savedList.get(0)));
            }
            result.put("count", savedList.size());
            return result;
        } catch (IllegalStateException | IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/{ids}")
    @AdminPermitted("content:character:edit")
    public Map<String, Object> remove(
            @PathVariable String ids,
            @RequestParam(name = "syncStFile", defaultValue = "false") boolean syncStFile
    ) {
        AdminJiugaiCharacterService.RemoveSummary summary = adminCharacterService.removeIds(ids, syncStFile);
        String message;
        if (syncStFile) {
            if (summary.stDeleted() > 0) {
                message = "删除成功，已同步清理 " + summary.stDeleted() + " 个 ST 角色文件";
            } else {
                message = "本地删除成功，未同步清理到 ST 文件";
            }
        } else {
            message = "本地删除成功";
        }
        Map<String, Object> result = AdminAjaxResult.ok(message);
        result.put("data", new LinkedHashMap<>(Map.of(
                "localDeleted", summary.localDeleted(),
                "stDeleted", summary.stDeleted(),
                "syncStFile", syncStFile
        )));
        return result;
    }

    @PostMapping("/batch-evict-lore-cache")
    @AdminPermitted("content:character:edit")
    public Map<String, Object> batchEvictLoreCache(@RequestBody(required = false) Map<String, Object> body) {
        return AdminAjaxResult.error("世界书缓存失效暂未接入，当前 SillySpringboot 阶段能力还未覆盖这条链路");
    }

    @PostMapping("/import-sillytavern")
    @AdminPermitted("content:character:edit")
    public Map<String, Object> importSillyTavernJson(@RequestBody(required = false) Object body) {
        return AdminAjaxResult.error("JSON 角色卡导入暂未实现，请先使用 PNG 导入");
    }

    @PostMapping(value = "/import-sillytavern-png", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminPermitted("content:character:edit")
    public Map<String, Object> importPng(@RequestPart("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return AdminAjaxResult.error("文件不能为空");
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".png")) {
                return AdminAjaxResult.error("当前仅支持 ST 导出的角色卡 PNG，不支持普通立绘图片或其他格式");
            }
            Object raw = stAdapter.importCharacterPng(
                    file.getBytes(),
                    originalFilename,
                    new StCharacterImportRequest("png", null)
            );
            String importError = extractImportError(raw);
            if (!importError.isBlank()) {
                return AdminAjaxResult.error(importError);
            }
            String avatarUrl = extractImportedAvatarUrl(raw);
            if (avatarUrl.isBlank()) {
                return AdminAjaxResult.error("PNG 导入失败：SillyTavern 没有返回角色文件名，请确认这是一张可导入的 ST 角色卡 PNG");
            }
            StCharacterDetail detail = stAdapter.getCharacter(new StCharacterGetRequest(avatarUrl));
            if (detail == null || detail.name() == null || detail.name().isBlank()) {
                return AdminAjaxResult.error("PNG 导入成功，但读取 ST 角色详情失败，请刷新后查看是否已在 ST 角色列表中生成");
            }
            AppCharacter row = catalogService.upsertImportedCharacter(avatarUrl, detail);
            int importedLorebookEntries = row == null || row.getId() == null
                    ? 0
                    : embeddedLorebookSyncService.replaceEmbeddedLorebook(row.getId(), detail.embeddedCharacterBookJson());
            Map<String, Object> result = AdminAjaxResult.ok(buildImportSuccessMessage(detail));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", row.getId());
            data.put("avatarUrl", row.getAvatarUrl());
            data.put("stAvatarUrl", row.getStAvatarUrl());
            data.put("importedTags", detail.tags());
            data.put("importedWorldNames", detail.worldNames());
            data.put("importedLorebookEntries", importedLorebookEntries);
            result.put("data", data);
            return result;
        } catch (StUnavailableException e) {
            return AdminAjaxResult.error(resolveImportErrorMessage(e));
        } catch (Exception e) {
            return AdminAjaxResult.error(resolveImportErrorMessage(e));
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

    private static String buildImportSuccessMessage(StCharacterDetail detail) {
        int tagCount = detail.tags() == null ? 0 : detail.tags().size();
        int worldCount = detail.worldNames() == null ? 0 : detail.worldNames().size();
        boolean hasEmbeddedLore = detail.embeddedCharacterBookJson() != null
                && !detail.embeddedCharacterBookJson().isBlank();
        StringBuilder message = new StringBuilder("PNG 导入成功");
        if (tagCount > 0) {
            message.append("，已同步 ").append(tagCount).append(" 个标签");
        }
        if (worldCount > 0) {
            message.append("，检测到 ").append(worldCount).append(" 个世界书绑定");
        }
        if (hasEmbeddedLore) {
            message.append("，角色卡包含 embedded lore");
        }
        return message.toString();
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
