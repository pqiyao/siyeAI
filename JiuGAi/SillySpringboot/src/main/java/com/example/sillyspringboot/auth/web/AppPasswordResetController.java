package com.example.sillyspringboot.auth.web;

import com.example.sillyspringboot.auth.dto.H5PasswordResetConfirmRequest;
import com.example.sillyspringboot.auth.dto.H5PasswordResetRequest;
import com.example.sillyspringboot.auth.dto.H5PasswordResetRequestResponse;
import com.example.sillyspringboot.auth.service.H5PasswordResetService;
import com.example.sillyspringboot.shared.web.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/auth/h5/password-reset")
public class AppPasswordResetController {

    private final H5PasswordResetService passwordResetService;

    public AppPasswordResetController(H5PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public ApiResult<H5PasswordResetRequestResponse> request(@Valid @RequestBody H5PasswordResetRequest request) {
        return ApiResult.ok(passwordResetService.requestReset(request));
    }

    @PostMapping("/confirm")
    public ApiResult<Void> confirm(@Valid @RequestBody H5PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request);
        return ApiResult.okEmpty();
    }
}
