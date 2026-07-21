package com.example.sillyspringboot.auth.token;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.entity.AppUserSession;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.mapper.AppUserSessionMapper;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

@Service
public class AppTokenService {

    private static final String TOKEN_VERSION = "v1";
    private final AppAuthProperties appAuthProperties;
    private final AppUserMapper appUserMapper;
    private final AppUserSessionMapper appUserSessionMapper;
    private final AppH5UserProfileExtMapper profileExtMapper;

    public AppTokenService(
            AppAuthProperties appAuthProperties,
            AppUserMapper appUserMapper,
            AppUserSessionMapper appUserSessionMapper,
            AppH5UserProfileExtMapper profileExtMapper
    ) {
        this.appAuthProperties = appAuthProperties;
        this.appUserMapper = appUserMapper;
        this.appUserSessionMapper = appUserSessionMapper;
        this.profileExtMapper = profileExtMapper;
    }

    public TokenIssueResult issueToken(long userId) {
        ensureUserEnabled(userId);
        long now = Instant.now().getEpochSecond();
        long ttl = appAuthProperties.getTokenTtlSeconds();
        long exp = now + ttl;
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(exp), ZoneId.systemDefault());

        AppUserSession session = new AppUserSession();
        session.setUserId(userId);
        session.setSessionId(sessionId);
        session.setExpiresAt(expiresAt);
        appUserSessionMapper.insertSession(session);

        String payload = buildPayload(sessionId, userId, exp);
        String token = signAndEncode(payload);
        return new TokenIssueResult(token, exp);
    }

    /**
     * token 验证并加载用户。
     */
    public AppUser validateAndLoadUser(String token) {
        TokenPayload payload = parseAndVerify(token);
        AppUserSession active = appUserSessionMapper.findActiveBySessionId(payload.sessionId());
        if (active == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效");
        }
        AppUser user = appUserMapper.findById(active.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效");
        }
        ensureUserEnabled(user.getId());
        return user;
    }

    public int revokeActiveSessions(long userId) {
        return appUserSessionMapper.revokeActiveByUserId(userId);
    }

    private void ensureUserEnabled(long userId) {
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(userId);
        if (ext != null && "disabled".equalsIgnoreCase(blank(ext.getStatus()))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户已停用");
        }
    }

    private TokenPayload parseAndVerify(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        String payloadEncoded = parts[0];
        String sigHex = parts[1];

        String payload = new String(Base64.getUrlDecoder().decode(payloadEncoded), StandardCharsets.UTF_8);
        if (!payload.startsWith(TOKEN_VERSION + "|")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }

        String computedSig = signHex(payload);
        if (!constantTimeEquals(computedSig, sigHex)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }

        String[] payloadParts = payload.split("\\|");
        if (payloadParts.length != 4) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        String version = payloadParts[0];
        String sessionId = payloadParts[1];
        long userId = Long.parseLong(payloadParts[2]);
        long exp = Long.parseLong(payloadParts[3]);
        if (Instant.now().getEpochSecond() > exp) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已过期");
        }
        return new TokenPayload(version, sessionId, userId, exp);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int result = 0;
        for (int i = 0; i < ba.length; i++) {
            result |= ba[i] ^ bb[i];
        }
        return result == 0;
    }

    private String signAndEncode(String payload) {
        String sigHex = signHex(payload);
        String payloadEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return payloadEncoded + "." + sigHex;
    }

    private String signHex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appAuthProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHexLower(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private String buildPayload(String sessionId, long userId, long expEpochSeconds) {
        return TOKEN_VERSION + "|" + sessionId + "|" + userId + "|" + expEpochSeconds;
    }

    private record TokenPayload(String version, String sessionId, long userId, long exp) {}

    public record TokenIssueResult(String token, long tokenExpiresAtEpochSeconds) {}

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
