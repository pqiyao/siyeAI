package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5VisitorDevice;
import com.example.sillyspringboot.compat.h5.mapper.AppH5VisitorDeviceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class H5ClientUidAuthService {

    private static final Logger log = LoggerFactory.getLogger(H5ClientUidAuthService.class);
    private static final String H5_USER_PREFIX = "h5u_";

    private final AppUserMapper userMapper;
    private final AppTokenService tokenService;
    private final AppH5VisitorDeviceMapper visitorDeviceMapper;

    public H5ClientUidAuthService(
            AppUserMapper userMapper,
            AppTokenService tokenService,
            AppH5VisitorDeviceMapper visitorDeviceMapper
    ) {
        this.userMapper = userMapper;
        this.tokenService = tokenService;
        this.visitorDeviceMapper = visitorDeviceMapper;
    }

    /**
     * Kept for older call sites, but intentionally no longer creates a synthetic AppUser
     * from an anonymous H5 clientUid.
     */
    @Deprecated
    public String issueTokenForClientUid(String clientUid) {
        return requireAuthenticatedTokenForClientUid(clientUid);
    }

    public String requireAuthenticatedTokenForClientUid(String clientUid) {
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");
        }
        String normalized = clientUid.trim();
        AuthenticatedRequestContext authenticated = resolveAuthenticatedRequestContext();
        if (authenticated != null) {
            if (normalized.startsWith(H5_USER_PREFIX)) {
                long expectedUserId = parseUserId(normalized.substring(H5_USER_PREFIX.length()));
                ensureAuthenticatedUserMatches(authenticated.user(), expectedUserId, normalized);
            }
            return authenticated.token();
        }
        if (normalized.startsWith(H5_USER_PREFIX)) {
            long expectedUserId = parseUserId(normalized.substring(H5_USER_PREFIX.length()));
            String bridgedToken = tryIssueLegacyRegisteredToken(normalized, expectedUserId);
            if (bridgedToken != null) {
                return bridgedToken;
            }
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
    }

    public AppUser resolveAuthenticatedRequestUser() {
        AuthenticatedRequestContext context = resolveAuthenticatedRequestContext();
        return context == null ? null : context.user();
    }

    public boolean hasAuthenticatedRequestUser() {
        return resolveAuthenticatedRequestUser() != null;
    }

    private String issueTokenForUserId(long userId) {
        AppUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login expired");
        }
        return tokenService.issueToken(user.getId()).token();
    }

    private static long parseUserId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login expired");
        }
    }

    private void ensureAuthenticatedUserMatches(AppUser user, long expectedUserId, String clientUid) {
        if (user == null || user.getId() != expectedUserId) {
            log.warn("reject mismatched h5 client uid identity clientUid={} expectedUserId={} actualUserId={}",
                    clientUid, expectedUserId, user == null ? null : user.getId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login expired");
        }
    }

    private String tryIssueLegacyRegisteredToken(String clientUid, long expectedUserId) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String deviceToken = H5VisitorDeviceService.resolveDeviceToken(request);
        if (deviceToken.isBlank()) {
            return null;
        }
        AppH5VisitorDevice device = visitorDeviceMapper.findByDeviceToken(deviceToken);
        if (!matchesLegacyRegisteredDevice(device, clientUid, expectedUserId)) {
            return null;
        }
        log.info("bridge legacy h5 registered request via device token userId={} clientUid={} deviceTokenSuffix={}",
                expectedUserId, clientUid, tail(deviceToken));
        return issueTokenForUserId(expectedUserId);
    }

    private boolean matchesLegacyRegisteredDevice(AppH5VisitorDevice device, String clientUid, long expectedUserId) {
        if (device == null) {
            return false;
        }
        if (equalsUserId(device.getLatestUserId(), expectedUserId) || equalsUserId(device.getFirstUserId(), expectedUserId)) {
            return true;
        }
        String latestClientUid = trimToEmpty(device.getLatestClientUid());
        String firstClientUid = trimToEmpty(device.getFirstClientUid());
        return clientUid.equals(latestClientUid) || clientUid.equals(firstClientUid);
    }

    private AuthenticatedRequestContext resolveAuthenticatedRequestContext() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String presentedToken = resolveRequestToken(request);
        if (presentedToken.isBlank()) {
            return null;
        }
        try {
            AppUser user = tokenService.validateAndLoadUser(presentedToken);
            return new AuthenticatedRequestContext(presentedToken, user);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private static String resolveRequestToken(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String bearer = trimToEmpty(request.getHeader("Authorization"));
        if (!bearer.isBlank()) {
            if (bearer.regionMatches(true, 0, "Bearer ", 0, 7) && bearer.length() > 7) {
                return bearer.substring(7).trim();
            }
            return bearer;
        }
        String headerToken = trimToEmpty(request.getHeader("token"));
        if (!headerToken.isBlank()) {
            return headerToken;
        }
        return trimToEmpty(request.getParameter("token"));
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private static boolean equalsUserId(Long actual, long expected) {
        return actual != null && actual == expected;
    }

    private static String tail(String raw) {
        String safe = trimToEmpty(raw);
        if (safe.length() <= 6) {
            return safe;
        }
        return safe.substring(safe.length() - 6);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record AuthenticatedRequestContext(String token, AppUser user) {}
}
