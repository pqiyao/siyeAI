package com.example.sillyspringboot.illustration.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.example.sillyspringboot.illustration.service.IllustrationAccessKeyService;
import com.example.sillyspringboot.illustration.service.IllustrationNoticeService;
import com.example.sillyspringboot.illustration.service.IllustrationWorkService;
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.web.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/app/illustrations")
public class AppIllustrationController {

    private final IllustrationWorkService illustrationWorkService;
    private final IllustrationAccessKeyService accessKeyService;
    private final IllustrationNoticeService noticeService;
    private final H5UploadService uploadService;
    private final AppTokenService tokenService;
    private final SocialUploadRateLimiter rateLimiter;

    public AppIllustrationController(
            IllustrationWorkService illustrationWorkService,
            IllustrationAccessKeyService accessKeyService,
            IllustrationNoticeService noticeService,
            H5UploadService uploadService,
            AppTokenService tokenService,
            SocialUploadRateLimiter rateLimiter
    ) {
        this.illustrationWorkService = illustrationWorkService;
        this.accessKeyService = accessKeyService;
        this.noticeService = noticeService;
        this.uploadService = uploadService;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/works")
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String accessKey
    ) {
        return ApiResult.ok(illustrationWorkService.listPublicWorks(
                keyword,
                category,
                tag,
                accessKeyService.canAccessR18(accessKey),
                pageNum,
                pageSize
        ));
    }

    @GetMapping("/works/{slug}")
    public ApiResult<Map<String, Object>> detail(
            @PathVariable String slug,
            @RequestParam(required = false) String accessKey
    ) {
        Map<String, Object> data = illustrationWorkService.getPublicWork(slug, accessKeyService.canAccessR18(accessKey));
        if (data == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作品不存在或暂不可见");
        }
        return ApiResult.ok(data);
    }

    @GetMapping("/access-key/validate")
    public ApiResult<Map<String, Object>> validateAccessKey(@RequestParam(required = false) String code) {
        return ApiResult.ok(accessKeyService.validateForPublic(code));
    }

    @GetMapping("/notices")
    public ApiResult<java.util.List<Map<String, Object>>> notices() {
        return ApiResult.ok(noticeService.listPublicNotices());
    }

    @PostMapping(value = "/works/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> submit(
            @RequestPart("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false, defaultValue = "插画") String category,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request
    ) {
        AppUser user = resolveOptionalUser(token, authorization);
        rateLimiter.checkUpload(user, request, "illustration_submission");
        String imageUrl = uploadService.saveImageAndGetUrl(file);
        Map<String, Object> data = illustrationWorkService.submitUserWork(
                user == null ? null : user.getId(),
                title,
                nickname,
                category,
                tags,
                description,
                imageUrl
        );
        return ApiResult.ok(data);
    }

    private AppUser resolveOptionalUser(String token, String authorization) {
        String safeToken = firstNonBlank(extractBearer(authorization), token);
        if (safeToken == null) {
            return null;
        }
        return tokenService.validateAndLoadUser(safeToken);
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
