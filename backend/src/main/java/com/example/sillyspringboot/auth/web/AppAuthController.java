package com.example.sillyspringboot.auth.web;

import com.example.sillyspringboot.auth.dto.AppAuthSessionResponse;
import com.example.sillyspringboot.auth.dto.AppUserDto;
import com.example.sillyspringboot.auth.dto.H5AccountLoginRequest;
import com.example.sillyspringboot.auth.dto.H5AccountRegisterRequest;
import com.example.sillyspringboot.auth.dto.TelegramBindRequest;
import com.example.sillyspringboot.auth.dto.TelegramLoginRequest;
import com.example.sillyspringboot.auth.service.AppAuthService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.web.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    private final AppAuthService appAuthService;

    public AppAuthController(AppAuthService appAuthService) {
        this.appAuthService = appAuthService;
    }

    @PostMapping("/telegram/login")
    public ApiResult<AppAuthSessionResponse> telegramLogin(@Valid @RequestBody TelegramLoginRequest request) {
        return ApiResult.ok(appAuthService.loginWithTelegramInitData(request));
    }

    @PostMapping("/telegram/bind")
    public ApiResult<AppUserDto> telegramBind(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody TelegramBindRequest request
    ) {
        return ApiResult.ok(appAuthService.bindTelegramToCurrentUser(extractToken(authorization), request));
    }

    @PostMapping("/h5/login")
    public ApiResult<AppAuthSessionResponse> h5Login(@Valid @RequestBody H5AccountLoginRequest request) {
        return ApiResult.ok(appAuthService.loginWithH5Account(request));
    }

    @PostMapping("/h5/register")
    public ApiResult<AppAuthSessionResponse> h5Register(@Valid @RequestBody H5AccountRegisterRequest request) {
        return ApiResult.ok(appAuthService.registerWithH5Account(request));
    }

    @PostMapping("/h5/bind")
    public ApiResult<AppUserDto> h5Bind(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody H5AccountRegisterRequest request
    ) {
        return ApiResult.ok(appAuthService.bindH5AccountToCurrentUser(extractToken(authorization), request));
    }

    @GetMapping("/me")
    public ApiResult<AppUserDto> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return ApiResult.ok(appAuthService.meByToken(extractToken(authorization)));
    }

    private static String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
