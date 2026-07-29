package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.entity.AppAndroidRelease;
import com.example.sillyspringboot.ops.mapper.AppAndroidReleaseMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AppUpdateService {
    public static final String OFFICIAL_CHANNEL = "official";

    private final AppAndroidReleaseMapper mapper;

    public AppUpdateService(AppAndroidReleaseMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> checkAndroid(
            String appId,
            String packageName,
            String channelCode,
            int currentVersionCode
    ) {
        String safeAppId = normalizeRequired(appId, 64);
        String safePackage = normalizeRequired(packageName, 191);
        String safeChannel = normalizeChannel(channelCode);
        if (safeAppId.isEmpty() || safePackage.isEmpty() || currentVersionCode < 0) {
            return noUpdate();
        }

        AppAndroidRelease release = mapper.findEffective(safeAppId, safePackage, safeChannel);
        if (release == null && !OFFICIAL_CHANNEL.equals(safeChannel)) {
            release = mapper.findEffective(safeAppId, safePackage, OFFICIAL_CHANNEL);
        }
        if (release == null || release.getVersionCode() == null
                || release.getVersionCode() <= currentVersionCode
                || !isHttpsUrl(release.getDownloadUrl())) {
            return noUpdate();
        }

        int minSupported = Math.max(0, valueOr(release.getMinSupportedVersionCode(), 0));
        boolean force = "FORCE".equalsIgnoreCase(release.getUpdateMode())
                || currentVersionCode < minSupported;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hasUpdate", true);
        data.put("force", force);
        data.put("updateMode", force ? "FORCE" : "NORMAL");
        data.put("versionName", text(release.getVersionName()));
        data.put("versionCode", release.getVersionCode());
        data.put("minSupportedVersionCode", minSupported);
        data.put("policyRevision", Math.max(1, valueOr(release.getPolicyRevision(), 1)));
        data.put("title", text(release.getTitle()));
        data.put("changelog", text(release.getChangelog()));
        data.put("downloadUrl", release.getDownloadUrl().trim());
        data.put("remindLaterHours", clamp(valueOr(release.getRemindLaterHours(), 6), 1, 168));
        data.put("apkSizeBytes", release.getApkSizeBytes());
        data.put("apkSha256", text(release.getApkSha256()).toLowerCase(Locale.ROOT));
        data.put("channel", text(release.getChannelCode()));
        return data;
    }

    public static boolean isHttpsUrl(String value) {
        if (value == null || value.isBlank() || value.length() > 1024) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static String normalizeChannel(String value) {
        String normalized = normalizeRequired(value, 32).toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? OFFICIAL_CHANNEL : normalized;
    }

    private static Map<String, Object> noUpdate() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hasUpdate", false);
        return data;
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeRequired(String value, int maxLength) {
        String normalized = text(value).trim();
        return normalized.length() > maxLength ? "" : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
