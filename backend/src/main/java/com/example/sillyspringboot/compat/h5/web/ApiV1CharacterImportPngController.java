package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Retained only to reject old clients explicitly. User PNG imports must use the owner-scoped /mine endpoint.
 */
@RestController
@RequestMapping("/api/v1/characters")
public class ApiV1CharacterImportPngController {

    @PostMapping(value = "/import-sillytavern-png", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiV1Result<Map<String, Object>> importPng(@RequestPart("file") MultipartFile file) {
        throw new BusinessException(
                ErrorCode.UNSUPPORTED_OPERATION,
                "旧角色导入入口已停用，请更新客户端后从“我的角色”导入"
        );
    }
}
