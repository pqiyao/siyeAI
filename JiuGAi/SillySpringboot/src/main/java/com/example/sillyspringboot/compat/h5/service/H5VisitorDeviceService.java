package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.compat.h5.entity.AppH5ClientUid;
import com.example.sillyspringboot.compat.h5.entity.AppH5VisitorDevice;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5VisitorDeviceMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class H5VisitorDeviceService {

    public static final String DEVICE_TOKEN_HEADER = "X-Device-Token";
    public static final String CLIENT_UID_HEADER = "X-Client-Uid";
    public static final String REQUEST_ATTR_DEVICE_TOKEN = "h5.deviceToken";
    public static final String REQUEST_ATTR_DEVICE_ID = "h5.deviceId";

    private final AppH5VisitorDeviceMapper visitorDeviceMapper;
    private final AppH5ClientUidMapper clientUidMapper;
    private final H5ClientUidAuthService h5Auth;

    public H5VisitorDeviceService(
            AppH5VisitorDeviceMapper visitorDeviceMapper,
            AppH5ClientUidMapper clientUidMapper,
            H5ClientUidAuthService h5Auth
    ) {
        this.visitorDeviceMapper = visitorDeviceMapper;
        this.clientUidMapper = clientUidMapper;
        this.h5Auth = h5Auth;
    }

    @Transactional
    public DeviceTouchContext resolveOrIssue(HttpServletRequest request) {
        RequestSnapshot snapshot = RequestSnapshot.from(request, clientUidMapper, h5Auth);
        String presentedToken = normalizeToken(resolveDeviceToken(request));
        if (!presentedToken.isEmpty()) {
            AppH5VisitorDevice existed = visitorDeviceMapper.findByDeviceToken(presentedToken);
            if (existed != null) {
                visitorDeviceMapper.touch(
                        existed.getId(),
                        snapshot.clientUid(),
                        snapshot.userId(),
                        snapshot.ip(),
                        snapshot.uaHash(),
                        snapshot.userAgent()
                );
                return new DeviceTouchContext(existed.getId(), existed.getDeviceToken(), false);
            }
        }

        AppH5VisitorDevice row = new AppH5VisitorDevice();
        row.setDeviceToken(generateDeviceToken());
        row.setFirstClientUid(snapshot.clientUid());
        row.setLatestClientUid(snapshot.clientUid());
        row.setFirstUserId(snapshot.userId());
        row.setLatestUserId(snapshot.userId());
        row.setFirstIp(snapshot.ip());
        row.setLatestIp(snapshot.ip());
        row.setUaHash(snapshot.uaHash());
        row.setUserAgent(snapshot.userAgent());
        row.setAnonymousChatAttemptCount(0);
        row.setAnonymousConversationCreateCount(0);
        row.setAnonymousCharacterCreateCount(0);
        visitorDeviceMapper.insert(row);
        return new DeviceTouchContext(row.getId(), row.getDeviceToken(), true);
    }

    public static String resolveClientUid(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String fromQuery = trimToEmpty(request.getParameter("clientUid"));
        if (!fromQuery.isEmpty()) {
            return clip(fromQuery, 64);
        }
        String fromHeader = trimToEmpty(request.getHeader(CLIENT_UID_HEADER));
        return fromHeader.isEmpty() ? "" : clip(fromHeader, 64);
    }

    public static String resolveDeviceToken(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        Object attr = request.getAttribute(REQUEST_ATTR_DEVICE_TOKEN);
        if (attr instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return normalizeToken(request.getHeader(DEVICE_TOKEN_HEADER));
    }

    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String[] candidates = new String[]{
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
        };
        for (String candidate : candidates) {
            String normalized = trimToEmpty(candidate);
            if (normalized.isEmpty()) {
                continue;
            }
            int commaIndex = normalized.indexOf(',');
            if (commaIndex >= 0) {
                normalized = normalized.substring(0, commaIndex).trim();
            }
            if (!normalized.isEmpty()) {
                return clip(normalized, 64);
            }
        }
        return "";
    }

    public static String hashUserAgent(String userAgent) {
        String normalized = trimToEmpty(userAgent);
        if (normalized.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.substring(0, Math.min(24, builder.length()));
        } catch (NoSuchAlgorithmException ignored) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    private static String normalizeToken(String raw) {
        String normalized = trimToEmpty(raw);
        if (normalized.isEmpty() || normalized.length() > 80) {
            return "";
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            boolean allowed =
                    (ch >= 'a' && ch <= 'z')
                            || (ch >= 'A' && ch <= 'Z')
                            || (ch >= '0' && ch <= '9')
                            || ch == '_'
                            || ch == '-';
            if (!allowed) {
                return "";
            }
        }
        return normalized;
    }

    private static String generateDeviceToken() {
        return "dv_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record DeviceTouchContext(Long deviceId, String deviceToken, boolean created) {}

    private record RequestSnapshot(String clientUid, Long userId, String ip, String uaHash, String userAgent) {
        static RequestSnapshot from(
                HttpServletRequest request,
                AppH5ClientUidMapper clientUidMapper,
                H5ClientUidAuthService h5Auth
        ) {
            String clientUid = resolveClientUid(request);
            return new RequestSnapshot(
                    clientUid,
                    resolveUserIdSnapshot(request, clientUid, clientUidMapper, h5Auth),
                    resolveClientIp(request),
                    hashUserAgent(request == null ? null : request.getHeader("User-Agent")),
                    clip(trimToEmpty(request == null ? null : request.getHeader("User-Agent")), 255)
            );
        }

        private static Long resolveUserIdSnapshot(
                HttpServletRequest request,
                String clientUid,
                AppH5ClientUidMapper clientUidMapper,
                H5ClientUidAuthService h5Auth
        ) {
            AppUser authenticatedUser = h5Auth == null ? null : h5Auth.resolveAuthenticatedRequestUser();
            if (authenticatedUser != null) {
                return authenticatedUser.getId();
            }
            if (clientUid == null || clientUid.isBlank()) {
                return null;
            }
            String normalized = clientUid.trim();
            if (normalized.startsWith("h5u_")) {
                return null;
            }
            if (clientUidMapper == null) {
                return null;
            }
            AppH5ClientUid mapping = clientUidMapper.findByClientUid(normalized);
            return mapping == null ? null : mapping.getUserId();
        }
    }
}
