package com.example.sillyspringboot.character.web;

import com.example.sillyspringboot.character.dto.AppCharacterSummaryDto;
import com.example.sillyspringboot.character.service.CharacterCatalogService;
import com.example.sillyspringboot.shared.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阶段 6：发现页（对齐 H5 主链路）最小可联调接口。
 */
@RestController
@RequestMapping("/api/app/discover")
public class AppDiscoverController {

    private final CharacterCatalogService catalog;

    public AppDiscoverController(CharacterCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/feed")
    public ApiResult<List<AppCharacterSummaryDto>> feed(@RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {
        int lim = Math.max(1, Math.min(200, limit));
        return ApiResult.ok(catalog.syncFeed(lim));
    }
}

