package com.example.sillyspringboot.character.web;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.web.ApiResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Retained only to reject the legacy unscoped import route without creating ST or system assets.
 */
@RestController
@RequestMapping("/api/app/characters/import")
public class AppCharacterImportController {

    @PostMapping(value = "/png", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Object> importPng(@RequestPart("file") MultipartFile file) {
        throw new BusinessException(
                ErrorCode.UNSUPPORTED_OPERATION,
                "旧角色导入入口已停用，请使用当前客户端的“我的角色”导入功能"
        );
    }
}
