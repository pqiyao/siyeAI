package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.compat.h5.service.H5LegacyUserCompatibilityService;
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/common")
public class H5LegacyCommonUploadController {

    private final H5UploadService uploadService;
    private final H5LegacyUserCompatibilityService legacyUserService;
    private final SocialUploadRateLimiter rateLimiter;

    public H5LegacyCommonUploadController(
            H5UploadService uploadService,
            H5LegacyUserCompatibilityService legacyUserService,
            SocialUploadRateLimiter rateLimiter
    ) {
        this.uploadService = uploadService;
        this.legacyUserService = legacyUserService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "token", required = false) String token,
            HttpServletRequest request
    ) {
        try {
            AppUser user = legacyUserService.requireUserByToken(token);
            rateLimiter.checkUpload(user, request, "h5_common");
            String url = uploadService.saveOwnedImageAndGetUrl(file, user.getId(), "h5_common");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            return legacyResult(1, "ok", data);
        } catch (Exception ex) {
            return legacyResult(4003, safeMessage(ex.getMessage()), null);
        }
    }

    private static Map<String, Object> legacyResult(int code, String msg, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", data);
        return body;
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "upload failed" : message.trim();
    }
}
