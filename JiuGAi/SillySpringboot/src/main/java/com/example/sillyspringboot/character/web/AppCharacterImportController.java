package com.example.sillyspringboot.character.web;

import com.example.sillyspringboot.character.service.CharacterCatalogService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.web.ApiResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 阶段 6：PNG 导入入口（对齐 H5 “我的角色/PNG 导入”）。
 *
 * 注意：这是“导入角色卡”，不是相册/图片生成。
 */
@RestController
@RequestMapping("/api/app/characters/import")
public class AppCharacterImportController {

    private final StAdapter stAdapter;
    private final CharacterCatalogService catalog;

    public AppCharacterImportController(StAdapter stAdapter, CharacterCatalogService catalog) {
        this.stAdapter = stAdapter;
        this.catalog = catalog;
    }

    @PostMapping(value = "/png", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Object> importPng(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "preservedName", required = false) String preservedName
    ) {
        try {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件不能为空");
            }
            Object result = stAdapter.importCharacterPng(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    new StCharacterImportRequest("png", preservedName)
            );
            // 尝试同步到角色目录：ST 返回 {file_name: "...png"}，再 get 一次详情填充 name/description
            if (result instanceof java.util.Map<?, ?> m) {
                Object fn = m.get("file_name");
                if (fn != null) {
                    String avatarUrl = String.valueOf(fn);
                    StCharacterDetail d = stAdapter.getCharacter(new StCharacterGetRequest(avatarUrl));
                    if (d != null) {
                        catalog.syncFeed(1); // 轻量触发目录可用（后续可优化为精确 upsert）
                    }
                }
            }
            return ApiResult.ok(result);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "导入失败，请稍后重试");
        }
    }
}

