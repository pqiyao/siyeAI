package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.support.service.SupportTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/support")
public class ApiV1SupportController {

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService appTokenService;
    private final SupportTicketService supportTicketService;
    private final H5UploadService uploadService;
    private final SocialUploadRateLimiter rateLimiter;

    public ApiV1SupportController(
            H5ClientUidAuthService h5Auth,
            AppTokenService appTokenService,
            SupportTicketService supportTicketService,
            H5UploadService uploadService,
            SocialUploadRateLimiter rateLimiter
    ) {
        this.h5Auth = h5Auth;
        this.appTokenService = appTokenService;
        this.supportTicketService = supportTicketService;
        this.uploadService = uploadService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/meta")
    public ApiV1Result<Map<String, Object>> meta() {
        return ApiV1Result.ok(supportTicketService.buildMeta());
    }

    @GetMapping("/tickets")
    public ApiV1Result<List<Map<String, Object>>> tickets(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit
    ) {
        return ApiV1Result.ok(supportTicketService.listUserTickets(resolveUser(clientUid).getId(), status, limit));
    }

    @GetMapping("/tickets/{ticketNo}")
    public ApiV1Result<Map<String, Object>> detail(
            @RequestParam("clientUid") String clientUid,
            @PathVariable String ticketNo
    ) {
        return ApiV1Result.ok(supportTicketService.getUserTicket(resolveUser(clientUid).getId(), ticketNo));
    }

    @PostMapping("/tickets/create")
    public ApiV1Result<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        String clientUid = asString(payload == null ? null : payload.get("clientUid"));
        AppUser user = resolveUser(clientUid);
        SupportTicketService.CreateTicketCommand command = new SupportTicketService.CreateTicketCommand(
                asString(payload == null ? null : payload.get("ticketType")),
                asString(payload == null ? null : payload.get("subject")),
                asString(payload == null ? null : payload.get("content")),
                asString(payload == null ? null : payload.get("orderNo")),
                asLong(payload == null ? null : payload.get("characterId")),
                asString(payload == null ? null : payload.get("characterName")),
                asStringList(payload == null ? null : payload.get("attachments")),
                asString(payload == null ? null : payload.get("priority"))
        );
        return ApiV1Result.ok(supportTicketService.createUserTicket(user.getId(), clientUid, command));
    }

    @PostMapping("/tickets/reply")
    public ApiV1Result<Map<String, Object>> reply(@RequestBody Map<String, Object> payload) {
        String clientUid = asString(payload == null ? null : payload.get("clientUid"));
        AppUser user = resolveUser(clientUid);
        String ticketNo = asString(payload == null ? null : payload.get("ticketNo"));
        String content = asString(payload == null ? null : payload.get("content"));
        List<String> attachments = asStringList(payload == null ? null : payload.get("attachments"));
        return ApiV1Result.ok(supportTicketService.replyAsUser(user.getId(), ticketNo, content, attachments));
    }

    @PostMapping("/tickets/report-character")
    public ApiV1Result<Map<String, Object>> reportCharacter(@RequestBody Map<String, Object> payload) {
        String clientUid = asString(payload == null ? null : payload.get("clientUid"));
        AppUser user = resolveUser(clientUid);
        return ApiV1Result.ok(
                supportTicketService.createCharacterReport(
                        user.getId(),
                        clientUid,
                        asLong(payload == null ? null : payload.get("characterId")),
                        asString(payload == null ? null : payload.get("characterName")),
                        asString(payload == null ? null : payload.get("subject")),
                        asString(payload == null ? null : payload.get("content")),
                        asStringList(payload == null ? null : payload.get("attachments"))
                )
        );
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiV1Result<Map<String, Object>> uploadImage(
            @RequestParam("clientUid") String clientUid,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        AppUser user = resolveUser(clientUid);
        rateLimiter.checkUpload(user, request, "support_image");
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "截图不能为空");
        }
        return ApiV1Result.ok(Map.of("url", uploadService.saveImageAndGetUrl(file)));
    }

    private AppUser resolveUser(String clientUid) {
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 缺失");
        }
        String token = h5Auth.issueTokenForClientUid(clientUid.trim());
        return appTokenService.validateAndLoadUser(token);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }
}
