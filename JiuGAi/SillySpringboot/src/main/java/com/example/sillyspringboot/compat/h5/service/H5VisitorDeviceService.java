package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.compat.h5.entity.AppH5VisitorDevice;
import com.example.sillyspringboot.compat.h5.mapper.AppH5VisitorDeviceMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import com.example.sillyspringboot.config.ClientIpResolver;
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
    private final H5ClientUidAuthService h5Auth;
    private final AppH5SecurityEventMapper securityEvents;
    private final ClientIpResolver clientIpResolver;

    public H5VisitorDeviceService(
            AppH5VisitorDeviceMapper visitorDeviceMapper,
            H5ClientUidAuthService h5Auth,
            AppH5SecurityEventMapper securityEvents,
            ClientIpResolver clientIpResolver
    ) {
        this.visitorDeviceMapper = visitorDeviceMapper;
        this.h5Auth = h5Auth;
        this.securityEvents = securityEvents;
        this.clientIpResolver = clientIpResolver;
    }

    @Transactional
    public DeviceTouchContext resolveOrIssue(HttpServletRequest request) {
        RequestSnapshot snapshot = RequestSnapshot.from(request, h5Auth, clientIpResolver);
        String presentedToken = normalizeToken(resolveDeviceToken(request));
        if (!presentedToken.isEmpty()) {
            AppH5VisitorDevice existed = visitorDeviceMapper.findByDeviceToken(presentedToken);
            if (existed != null) {
                recordChanges(existed, snapshot);
                visitorDeviceMapper.touch(
                        existed.getId(),
                        snapshot.clientUid(),
                        snapshot.userId(),
                        snapshot.userId(),
                        snapshot.ip(),
                        snapshot.uaHash(),
                        snapshot.userAgent()
                );
                return new DeviceTouchContext(existed.getId(), existed.getDeviceToken(), false);
            }

            // Unknown client-provided tokens are persisted only after a real app token has
            // authenticated the request. Anonymous callers cannot create arbitrary rows.
            if (snapshot.userId() != null) {
                return insertDevice(presentedToken, snapshot);
            }
        }

        // The client stores this response token and presents it on a later request. The
        // anonymous bootstrap request itself remains on the stable network rate-limit key.
        return new DeviceTouchContext(null, generateDeviceToken(), false);
    }

    private DeviceTouchContext insertDevice(String deviceToken, RequestSnapshot snapshot) {
        AppH5VisitorDevice row = new AppH5VisitorDevice();
        row.setDeviceToken(deviceToken);
        row.setFirstClientUid(snapshot.clientUid());
        row.setLatestClientUid(snapshot.clientUid());
        row.setFirstUserId(snapshot.userId());
        row.setLatestUserId(snapshot.userId());
        row.setTrustedUserId(snapshot.userId());
        row.setFirstIp(snapshot.ip());
        row.setLatestIp(snapshot.ip());
        row.setUaHash(snapshot.uaHash());
        row.setUserAgent(snapshot.userAgent());
        row.setAnonymousChatAttemptCount(0);
        row.setAnonymousConversationCreateCount(0);
        row.setAnonymousCharacterCreateCount(0);
        visitorDeviceMapper.insert(row);
        AppH5VisitorDevice persisted = visitorDeviceMapper.findByDeviceToken(deviceToken);
        if (persisted == null || persisted.getId() == null) {
            throw new IllegalStateException("device binding insert did not produce a persisted row");
        }
        if (!snapshot.userId().equals(persisted.getTrustedUserId())) {
            visitorDeviceMapper.touch(
                    persisted.getId(),
                    snapshot.clientUid(),
                    snapshot.userId(),
                    snapshot.userId(),
                    snapshot.ip(),
                    snapshot.uaHash(),
                    snapshot.userAgent()
            );
        }
        if (securityEvents != null) securityEvents.insert(persisted.getId(), "DEVICE_BOUND", snapshot.clientUid(), snapshot.userId(), snapshot.ip(), snapshot.uaHash(), "device", "authenticated device bound");
        return new DeviceTouchContext(persisted.getId(), persisted.getDeviceToken(), true);
    }

    private void recordChanges(AppH5VisitorDevice previous, RequestSnapshot next) {
        if (securityEvents == null) return;
        if (changed(previous.getLatestClientUid(), next.clientUid())) securityEvents.insert(previous.getId(), "CLIENT_UID_CHANGED", next.clientUid(), next.userId(), next.ip(), next.uaHash(), "identity", previous.getLatestClientUid());
        if (next.userId() != null && previous.getLatestUserId() != null && !next.userId().equals(previous.getLatestUserId())) securityEvents.insert(previous.getId(), "USER_CHANGED", next.clientUid(), next.userId(), next.ip(), next.uaHash(), "identity", "from user " + previous.getLatestUserId());
        if (changed(previous.getLatestIp(), next.ip())) securityEvents.insert(previous.getId(), "IP_CHANGED", next.clientUid(), next.userId(), next.ip(), next.uaHash(), "network", "ip changed");
    }

    private static boolean changed(String before, String after) {
        String a = trimToEmpty(before), b = trimToEmpty(after);
        return !a.isEmpty() && !b.isEmpty() && !a.equals(b);
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
                H5ClientUidAuthService h5Auth,
                ClientIpResolver clientIpResolver
        ) {
            String clientUid = resolveClientUid(request);
            return new RequestSnapshot(
                    clientUid,
                    resolveAuthenticatedUserId(h5Auth),
                    clientIpResolver.resolve(request),
                    hashUserAgent(request == null ? null : request.getHeader("User-Agent")),
                    clip(trimToEmpty(request == null ? null : request.getHeader("User-Agent")), 255)
            );
        }

        private static Long resolveAuthenticatedUserId(H5ClientUidAuthService h5Auth) {
            AppUser authenticatedUser = h5Auth == null ? null : h5Auth.resolveAuthenticatedRequestUser();
            return authenticatedUser == null ? null : authenticatedUser.getId();
        }
    }
}
