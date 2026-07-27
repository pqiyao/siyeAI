package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.integration.sillytavern.SillyTavernProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * 把库里的 st_avatar_url（文件名）或上传相对路径，转成 H5 能直接加载的绝对 URL。
 */
@Component
public class H5StAssetUrls {

    private static final String ST_CHARACTER_PROXY_PREFIX = "/api/v1/st-assets/characters/";
    private static final String ST_CHARACTER_THUMB_PREFIX = "/api/v1/st-assets/characters-thumb";
    private static final String SIGNATURE_VERSION = "st-asset-v1";
    private static final long EXPIRY_BUCKET_SECONDS = Duration.ofDays(1).toSeconds();
    private static final long SIGNATURE_TTL_SECONDS = Duration.ofDays(8).toSeconds();
    private static final long MAX_FUTURE_SECONDS = Duration.ofDays(9).toSeconds();

    private final SillyTavernProperties sillyTavernProperties;
    private final AppAuthProperties appAuthProperties;

    public H5StAssetUrls(
            SillyTavernProperties sillyTavernProperties,
            AppAuthProperties appAuthProperties
    ) {
        this.sillyTavernProperties = sillyTavernProperties;
        this.appAuthProperties = appAuthProperties;
    }

    /** 优先使用用户上传地址，否则按 ST 角色文件名拼公开根路径 */
    public String portraitForCharacter(String avatarUrl, String coverUrl, String stAvatarUrl) {
        String uploaded = firstTrustedPortraitReference(avatarUrl, coverUrl, stAvatarUrl);
        if (!uploaded.isEmpty()) {
            return resolve(uploaded);
        }
        return resolve(stAvatarUrl);
    }

    public String portraitForCharacterThumb(String avatarUrl, String coverUrl, String stAvatarUrl, String preset) {
        String uploaded = firstTrustedPortraitReference(avatarUrl, coverUrl, stAvatarUrl);
        if (!uploaded.isEmpty()) {
            return resolveWithPreset(uploaded, preset);
        }
        return resolveWithPreset(stAvatarUrl, preset);
    }

    /**
     * @param raw 可能是：http(s) URL、以 / 开头的站内路径、ST 角色 png 文件名
     */
    public String resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String f = raw.trim();
        String lower = f.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return f;
        }
        if (lower.startsWith("/")) {
            return f;
        }
        // 角色 png 在 ST 上走需登录的 /characters/*；H5 无 Cookie，经本服务代理到 ST
        long expires = signatureExpiryEpochSecond();
        return UriComponentsBuilder.fromPath("/api/v1/st-assets/characters")
                .pathSegment(f)
                .queryParam("expires", expires)
                .queryParam("sig", sign(f, expires))
                .build()
                .toUriString();
    }

    public boolean hasValidSignature(String fileName, Long expires, String signature) {
        if (fileName == null || fileName.isBlank() || expires == null || signature == null || signature.isBlank()) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (expires < now || expires > now + MAX_FUTURE_SECONDS) {
            return false;
        }
        return constantTimeEquals(sign(fileName.trim(), expires), signature.trim());
    }

    public String resolveWithPreset(String raw, String preset) {
        String resolved = resolve(raw);
        try {
            return rewriteToCharacterThumb(resolved, preset);
        } catch (Exception ignored) {
            return resolved;
        }
    }

    private static String rewriteToCharacterThumb(String resolved, String preset) {
        if (resolved == null || resolved.isBlank()) {
            return "";
        }
        String safePreset = normalizePreset(preset);
        if (safePreset.isEmpty()) {
            return resolved;
        }
        if (!resolved.startsWith(ST_CHARACTER_PROXY_PREFIX)) {
            return resolved;
        }
        var source = UriComponentsBuilder.fromUriString(resolved).build();
        String sourcePath = source.getPath();
        if (sourcePath == null || !sourcePath.startsWith(ST_CHARACTER_PROXY_PREFIX)) {
            return resolved;
        }
        String fileName = sourcePath.substring(ST_CHARACTER_PROXY_PREFIX.length());
        if (fileName.isBlank()) {
            return resolved;
        }
        return UriComponentsBuilder.fromPath(ST_CHARACTER_THUMB_PREFIX)
                .pathSegment(fileName)
                .queryParams(source.getQueryParams())
                .queryParam("preset", safePreset)
                .build()
                .toUriString();
    }

    private long signatureExpiryEpochSecond() {
        long now = Instant.now().getEpochSecond();
        long nextBucket = ((now / EXPIRY_BUCKET_SECONDS) + 1L) * EXPIRY_BUCKET_SECONDS;
        return nextBucket + SIGNATURE_TTL_SECONDS;
    }

    private String sign(String fileName, long expires) {
        String payload = SIGNATURE_VERSION + "|" + fileName + "|" + expires;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    appAuthProperties.getSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return toHexLower(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 not available", ex);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length; i++) {
            result |= left[i] ^ right[i];
        }
        return result == 0;
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }

    private static String normalizePreset(String preset) {
        if (preset == null || preset.isBlank()) {
            return "";
        }
        String value = preset.trim().toLowerCase(Locale.ROOT);
        if ("avatar".equals(value) || "card".equals(value) || "detail".equals(value)) {
            return value;
        }
        return "";
    }

    private static String firstTrustedPortraitReference(String avatarUrl, String coverUrl, String stAvatarUrl) {
        String stFile = stAvatarUrl == null ? "" : stAvatarUrl.trim();
        for (String candidate : new String[]{avatarUrl, coverUrl}) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String value = candidate.trim();
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http://") || lower.startsWith("https://")
                    || lower.startsWith("data:") || value.startsWith("/")
                    || value.equalsIgnoreCase(stFile)) {
                return value;
            }
        }
        return "";
    }
}
