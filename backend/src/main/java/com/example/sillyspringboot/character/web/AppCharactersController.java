package com.example.sillyspringboot.character.web;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.service.CharacterCatalogService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阶段 6：角色列表/详情（对齐 H5 主链路）最小可联调接口。
 *
 * 约定：characterId 先沿用 ST 的 avatar_url（如 "xxx.png"）。
 * 后续可引入业务侧 catalogId 并做映射，不影响 adapter 收口。
 */
@RestController
@RequestMapping("/api/app/characters")
public class AppCharactersController {

    private final StAdapter stAdapter;
    private final CharacterCatalogService catalog;

    public AppCharactersController(StAdapter stAdapter, CharacterCatalogService catalog) {
        this.stAdapter = stAdapter;
        this.catalog = catalog;
    }

    @GetMapping("/{characterId}")
    public ApiResult<StCharacterDetail> get(@PathVariable long characterId) {
        AppCharacter c = catalog.ensureCharacter(characterId);
        if (c == null || c.getStAvatarUrl() == null || c.getStAvatarUrl().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        StCharacterDetail d = stAdapter.getCharacter(new StCharacterGetRequest(c.getStAvatarUrl()));
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return ApiResult.ok(d);
    }
}

