package com.example.sillyspringboot.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class H5PasswordResetConfirmRequest {

    @NotBlank
    @Size(max = 64)
    private String requestId;

    @NotBlank
    @Email
    @Size(max = 128)
    private String email;

    @NotBlank
    @Pattern(regexp = "\\d{8}")
    private String code;

    @NotBlank
    @Size(min = 6, max = 64)
    private String newPassword;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
