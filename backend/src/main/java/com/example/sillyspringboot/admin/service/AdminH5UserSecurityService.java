package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.entity.AppUserIdentity;
import com.example.sillyspringboot.auth.mapper.AppUserIdentityMapper;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminH5UserSecurityService {

    private static final String IDENTITY_H5_ACCOUNT = "h5_account";

    private final AppUserMapper userMapper;
    private final AppUserIdentityMapper identityMapper;
    private final AppTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final EntitlementAuditLogService auditLogService;

    public AdminH5UserSecurityService(
            AppUserMapper userMapper,
            AppUserIdentityMapper identityMapper,
            AppTokenService tokenService,
            PasswordEncoder passwordEncoder,
            EntitlementAuditLogService auditLogService
    ) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Map<String, Object> resetPassword(long userId, String rawPassword, boolean revokeSessions) {
        validatePassword(rawPassword);
        AppUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        AppUserIdentity identity = identityMapper.findByUserIdAndType(userId, IDENTITY_H5_ACCOUNT);
        if (identity == null || identity.getIdentityKey() == null || identity.getIdentityKey().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该用户不是账号密码登录，不能重置密码");
        }

        identity.setCredentialHash(passwordEncoder.encode(rawPassword));
        identity.setVerified(Boolean.TRUE);
        identityMapper.updateById(identity);

        int revokedSessions = revokeSessions ? tokenService.revokeActiveSessions(userId) : 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("account", identity.getIdentityKey());
        result.put("revokedSessions", revokedSessions);
        auditLogService.recordUserSecurityUpdate(userId, "RESET_PASSWORD", result, "admin");
        return result;
    }

    @Transactional
    public int revokeSessions(long userId, String reason) {
        AppUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        int revokedSessions = tokenService.revokeActiveSessions(userId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userId", userId);
        detail.put("reason", reason == null ? "" : reason);
        detail.put("revokedSessions", revokedSessions);
        auditLogService.recordUserSecurityUpdate(userId, "REVOKE_SESSIONS", detail, "admin");
        return revokedSessions;
    }

    private static void validatePassword(String raw) {
        if (raw == null || raw.length() < 6 || raw.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码长度必须为 6-64 位");
        }
    }
}
