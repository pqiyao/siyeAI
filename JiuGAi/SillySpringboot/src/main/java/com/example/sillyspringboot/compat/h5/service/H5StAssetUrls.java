package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.integration.sillytavern.SillyTavernProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;

/**
 * 把库里的 st_avatar_url（文件名）或上传相对路径，转成 H5 能直接加载的绝对 URL。
 */
@Component
public class H5StAssetUrls {

    private static final String ST_CHARACTER_PROXY_PREFIX = "/api/v1/st-assets/characters/";
    private static final String ST_CHARACTER_THUMB_PREFIX = "/api/v1/st-assets/characters-thumb";

    private final SillyTavernProperties sillyTavernProperties;

    public H5StAssetUrls(SillyTavernProperties sillyTavernProperties) {
        this.sillyTavernProperties = sillyTavernProperties;
    }

    /** 优先使用用户上传地址，否则按 ST 角色文件名拼公开根路径 */
    public String portraitForCharacter(String avatarUrl, String coverUrl, String stAvatarUrl) {
        String uploaded = firstNonBlank(avatarUrl, coverUrl);
        if (!uploaded.isEmpty()) {
            return resolve(uploaded);
        }
        return resolve(stAvatarUrl);
    }

    public String portraitForCharacterThumb(String avatarUrl, String coverUrl, String stAvatarUrl, String preset) {
        String uploaded = firstNonBlank(avatarUrl, coverUrl);
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
        return UriComponentsBuilder.fromPath("/api/v1/st-assets/characters")
                .pathSegment(f)
                .build()
                .toUriString();
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
        String fileName = resolved.substring(ST_CHARACTER_PROXY_PREFIX.length());
        if (fileName.isBlank()) {
            return resolved;
        }
        return ST_CHARACTER_THUMB_PREFIX + "/" + fileName + "?preset=" + safePreset;
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

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return "";
    }
}
