package com.example.sillyspringboot.auth.service;

import com.example.sillyspringboot.auth.dto.AppAuthSessionResponse;
import com.example.sillyspringboot.auth.dto.AppUserDto;
import com.example.sillyspringboot.auth.dto.H5AccountLoginRequest;
import com.example.sillyspringboot.auth.dto.H5AccountRegisterRequest;
import com.example.sillyspringboot.auth.dto.TelegramAuthPayload;
import com.example.sillyspringboot.auth.dto.TelegramBindRequest;
import com.example.sillyspringboot.auth.dto.TelegramLoginRequest;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.entity.AppUserIdentity;
import com.example.sillyspringboot.auth.mapper.AppUserIdentityMapper;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.telegram.TelegramWebAppInitDataValidator;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5ClientUid;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AppAuthService {

    private static final String IDENTITY_H5_ACCOUNT = "h5_account";
    private static final String IDENTITY_TELEGRAM = "telegram";

    private final TelegramWebAppInitDataValidator validator;
    private final AppUserMapper appUserMapper;
    private final AppUserIdentityMapper identityMapper;
    private final AppH5ClientUidMapper h5ClientUidMapper;
    private final AppTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final H5EntitlementService entitlementService;
    private final AppFeatureSettingsService featureSettingsService;

    public AppAuthService(
            TelegramWebAppInitDataValidator validator,
            AppUserMapper appUserMapper,
            AppUserIdentityMapper identityMapper,
            AppH5ClientUidMapper h5ClientUidMapper,
            AppTokenService tokenService,
            PasswordEncoder passwordEncoder,
            H5EntitlementService entitlementService,
            AppFeatureSettingsService featureSettingsService
    ) {
        this.validator = validator;
        this.appUserMapper = appUserMapper;
        this.identityMapper = identityMapper;
        this.h5ClientUidMapper = h5ClientUidMapper;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.entitlementService = entitlementService;
        this.featureSettingsService = featureSettingsService;
    }

    @Transactional
    public AppAuthSessionResponse loginWithTelegramInitData(TelegramLoginRequest request) {
        TelegramAuthPayload payload = validator.validate(request.getInitData());
        long telegramUserId = payload.user().id();
        String telegramIdentityKey = String.valueOf(telegramUserId);

        AppUserIdentity identity = identityMapper.findByTypeAndKey(IDENTITY_TELEGRAM, telegramIdentityKey);
        AppUser user = identity == null ? null : appUserMapper.findById(identity.getUserId());
        if (user == null) {
            user = appUserMapper.findByTelegramUserId(telegramUserId);
        }

        if (user == null) {
            user = new AppUser();
            user.setTelegramUserId(telegramUserId);
            user.setUsername(trimToNull(payload.user().username()));
            user.setFirstName(trimToNull(payload.user().firstName()));
            user.setLastName(trimToNull(payload.user().lastName()));
            user.setLanguageCode(trimToNull(payload.user().languageCode()));
            user.setPhotoUrl(trimToNull(payload.user().photoUrl()));
            appUserMapper.insert(user);
        } else {
            applyTelegramProfile(user, payload);
            appUserMapper.updateById(user);
        }

        ensureTelegramIdentity(user.getId(), telegramIdentityKey);
        return issueSession(user);
    }

    @Transactional
    public AppAuthSessionResponse registerWithH5Account(H5AccountRegisterRequest request) {
        return registerWithH5Account(request, null);
    }

    @Transactional
    public AppAuthSessionResponse registerWithH5Account(H5AccountRegisterRequest request, String clientUid) {
        featureSettingsService.ensureRegisterEnabled();
        String account = validateAccount(request.getAccount());
        String normalizedAccountKey = normalizeAccountKey(account);
        validatePassword(request.getPassword());
        if (identityMapper.findByTypeAndKey(IDENTITY_H5_ACCOUNT, normalizedAccountKey) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }

        AppUser user = resolveGuestUpgradeTarget(clientUid);
        if (user == null) {
            user = new AppUser();
            user.setTelegramUserId(null);
            user.setUsername(account);
            user.setFirstName(account);
            appUserMapper.insert(user);
        } else {
            boolean updateUser = false;
            if (shouldReplaceUsername(user.getUsername())) {
                user.setUsername(account);
                updateUser = true;
            }
            if (shouldReplaceFirstName(user.getFirstName())) {
                user.setFirstName(account);
                updateUser = true;
            }
            if (updateUser) {
                appUserMapper.updateById(user);
            }
        }
        insertH5Identity(user.getId(), normalizedAccountKey, passwordEncoder.encode(request.getPassword()));
        return issueSession(user);
    }

    @Transactional
    public AppAuthSessionResponse loginWithH5Account(H5AccountLoginRequest request) {
        featureSettingsService.ensureLoginEnabled();
        String account = validateAccount(request.getAccount());
        validatePassword(request.getPassword());

        AppUserIdentity identity = identityMapper.findByTypeAndKey(IDENTITY_H5_ACCOUNT, normalizeAccountKey(account));
        if (identity == null || identity.getCredentialHash() == null || identity.getCredentialHash().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), identity.getCredentialHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        AppUser user = appUserMapper.findById(identity.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        return issueSession(user);
    }

    @Transactional
    public AppUserDto bindTelegramToCurrentUser(String token, TelegramBindRequest request) {
        AppUser user = tokenService.validateAndLoadUser(token);
        TelegramAuthPayload payload = validator.validate(request.getInitData());
        String telegramIdentityKey = String.valueOf(payload.user().id());

        AppUserIdentity identity = identityMapper.findByTypeAndKey(IDENTITY_TELEGRAM, telegramIdentityKey);
        if (identity != null && !identity.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Telegram 已绑定到其他账号");
        }

        applyTelegramProfile(user, payload);
        appUserMapper.updateById(user);
        ensureTelegramIdentity(user.getId(), telegramIdentityKey);
        return toDto(user, entitlementService.ensureProfileExt(user));
    }

    @Transactional
    public AppUserDto bindH5AccountToCurrentUser(String token, H5AccountRegisterRequest request) {
        AppUser user = tokenService.validateAndLoadUser(token);
        String account = validateAccount(request.getAccount());
        String normalizedAccountKey = normalizeAccountKey(account);
        validatePassword(request.getPassword());

        AppUserIdentity existing = identityMapper.findByTypeAndKey(IDENTITY_H5_ACCOUNT, normalizedAccountKey);
        if (existing != null && !existing.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }
        if (existing == null) {
            insertH5Identity(user.getId(), normalizedAccountKey, passwordEncoder.encode(request.getPassword()));
        } else {
            existing.setCredentialHash(passwordEncoder.encode(request.getPassword()));
            existing.setVerified(Boolean.TRUE);
            identityMapper.updateById(existing);
        }

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(account);
            if (user.getFirstName() == null || user.getFirstName().isBlank()) {
                user.setFirstName(account);
            }
            appUserMapper.updateById(user);
        }
        return toDto(user, entitlementService.ensureProfileExt(user));
    }

    @Transactional(readOnly = true)
    public AppUserDto meByToken(String token) {
        AppUser user = tokenService.validateAndLoadUser(token);
        return toDto(user, entitlementService.ensureProfileExt(user));
    }

    private AppAuthSessionResponse issueSession(AppUser user) {
        AppH5UserProfileExt ext = entitlementService.ensureProfileExt(user);
        AppTokenService.TokenIssueResult token = tokenService.issueToken(user.getId());
        return new AppAuthSessionResponse(
                token.token(),
                token.tokenExpiresAtEpochSeconds(),
                toDto(user, ext)
        );
    }

    private void insertH5Identity(long userId, String identityKey, String credentialHash) {
        AppUserIdentity row = new AppUserIdentity();
        row.setUserId(userId);
        row.setIdentityType(IDENTITY_H5_ACCOUNT);
        row.setIdentityKey(identityKey);
        row.setCredentialHash(credentialHash);
        row.setVerified(Boolean.TRUE);
        try {
            identityMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在", ex);
        }
    }

    private void ensureTelegramIdentity(long userId, String identityKey) {
        AppUserIdentity existing = identityMapper.findByTypeAndKey(IDENTITY_TELEGRAM, identityKey);
        if (existing != null) {
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Telegram 已绑定到其他账号");
            }
            existing.setVerified(Boolean.TRUE);
            identityMapper.updateById(existing);
            return;
        }
        AppUserIdentity row = new AppUserIdentity();
        row.setUserId(userId);
        row.setIdentityType(IDENTITY_TELEGRAM);
        row.setIdentityKey(identityKey);
        row.setVerified(Boolean.TRUE);
        try {
            identityMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "Telegram 已绑定到其他账号", ex);
        }
    }

    private void applyTelegramProfile(AppUser user, TelegramAuthPayload payload) {
        boolean hasH5Account = identityMapper.findByUserIdAndType(user.getId(), IDENTITY_H5_ACCOUNT) != null;
        user.setTelegramUserId(payload.user().id());
        if (!hasH5Account && hasText(payload.user().username())) {
            user.setUsername(payload.user().username().trim());
        }
        if ((!hasH5Account || !hasText(user.getFirstName())) && hasText(payload.user().firstName())) {
            user.setFirstName(payload.user().firstName().trim());
        }
        if (hasText(payload.user().lastName())) {
            user.setLastName(payload.user().lastName().trim());
        }
        if (hasText(payload.user().languageCode())) {
            user.setLanguageCode(payload.user().languageCode().trim());
        }
        if (hasText(payload.user().photoUrl())) {
            user.setPhotoUrl(payload.user().photoUrl().trim());
        }
    }

    private AppUserDto toDto(AppUser user, AppH5UserProfileExt ext) {
        return new AppUserDto(
                user.getId(),
                user.getTelegramUserId(),
                blank(user.getUsername()),
                firstNonBlank(ext == null ? null : ext.getNickname(), user.getFirstName(), user.getUsername(), "user#" + user.getId()),
                firstNonBlank(ext == null ? null : ext.getAvatar(), user.getPhotoUrl(), ""),
                user.getTelegramUserId() != null
        );
    }

    private AppUser resolveGuestUpgradeTarget(String clientUid) {
        String normalized = clientUid == null ? "" : clientUid.trim();
        if (normalized.isBlank() || normalized.startsWith("h5u_")) {
            return null;
        }
        AppH5ClientUid mapping = h5ClientUidMapper.findByClientUid(normalized);
        if (mapping == null || mapping.getUserId() == null) {
            return null;
        }
        AppUser user = appUserMapper.findById(mapping.getUserId());
        if (user == null) {
            return null;
        }
        if (identityMapper.findByUserIdAndType(user.getId(), IDENTITY_H5_ACCOUNT) != null) {
            return null;
        }
        return user;
    }

    private static boolean shouldReplaceUsername(String value) {
        if (!hasText(value)) {
            return true;
        }
        return value.trim().toLowerCase(Locale.ROOT).startsWith("h5_");
    }

    private static boolean shouldReplaceFirstName(String value) {
        if (!hasText(value)) {
            return true;
        }
        String trimmed = value.trim();
        return "H5".equalsIgnoreCase(trimmed) || trimmed.toLowerCase(Locale.ROOT).startsWith("h5_");
    }

    private static String validateAccount(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 3 || value.length() > 32 || value.contains(" ")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "账号格式不正确");
        }
        return value;
    }

    private static void validatePassword(String raw) {
        if (raw == null || raw.length() < 6 || raw.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码长度需为 6-64 位");
        }
    }

    private static String normalizeAccountKey(String account) {
        return account.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
