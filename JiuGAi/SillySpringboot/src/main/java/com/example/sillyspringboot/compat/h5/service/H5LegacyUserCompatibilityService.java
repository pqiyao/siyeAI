package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.entity.AppUserIdentity;
import com.example.sillyspringboot.auth.mapper.AppUserIdentityMapper;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class H5LegacyUserCompatibilityService {

    private static final String IDENTITY_H5_ACCOUNT = "h5_account";

    private final AppTokenService tokenService;
    private final AppUserMapper appUserMapper;
    private final AppUserIdentityMapper identityMapper;
    private final AppH5UserProfileExtMapper profileExtMapper;
    private final H5EntitlementService entitlementService;
    private final PasswordEncoder passwordEncoder;

    public H5LegacyUserCompatibilityService(
            AppTokenService tokenService,
            AppUserMapper appUserMapper,
            AppUserIdentityMapper identityMapper,
            AppH5UserProfileExtMapper profileExtMapper,
            H5EntitlementService entitlementService,
            PasswordEncoder passwordEncoder
    ) {
        this.tokenService = tokenService;
        this.appUserMapper = appUserMapper;
        this.identityMapper = identityMapper;
        this.profileExtMapper = profileExtMapper;
        this.entitlementService = entitlementService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AppUser requireUserByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login expired");
        }
        return tokenService.validateAndLoadUser(token.trim());
    }

    @Transactional
    public AppH5UserProfileExt ensureProfileExt(AppUser user) {
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(user.getId());
        if (ext != null) {
            return ext;
        }
        return entitlementService.ensureProfileExt(user);
    }

    @Transactional
    public Map<String, Object> buildLegacyUserInfoByToken(String token) {
        AppUser user = requireUserByToken(token);
        AppH5UserProfileExt ext = ensureProfileExt(user);
        return buildLegacyUserInfo(user, ext, token);
    }

    @Transactional
    public Map<String, Object> buildLegacyProfileByToken(String token) {
        AppUser user = requireUserByToken(token);
        AppH5UserProfileExt ext = ensureProfileExt(user);
        Map<String, Object> data = new LinkedHashMap<>(buildLegacyUserInfo(user, ext, token));
        data.put("bio", blank(ext.getBio()));
        data.put("gender", nvl(ext.getGender()));
        data.put("birthday", blank(ext.getBirthday()));
        data.put("height", blank(ext.getHeight()));
        data.put("weight", blank(ext.getWeight()));
        data.put("country", blank(ext.getCountry()));
        data.put("characters", blank(ext.getCharacters()));
        data.put("relation", blank(ext.getRelation()));
        data.put("occupation", blank(ext.getOccupation()));
        data.put("label", blank(ext.getLabel()));
        data.put("language_code", blank(user.getLanguageCode()));
        return data;
    }

    @Transactional
    public Map<String, Object> updateProfileByToken(String token, MultiValueMap<String, String> form) {
        AppUser user = requireUserByToken(token);
        AppH5UserProfileExt ext = ensureProfileExt(user);

        boolean updateUser = false;
        if (hasValue(form, "nickname")) {
            ext.setNickname(trimToEmpty(firstValue(form, "nickname")));
        }
        if (hasValue(form, "avatar")) {
            String avatar = trimToEmpty(firstValue(form, "avatar"));
            ext.setAvatar(avatar);
            user.setPhotoUrl(avatar.isBlank() ? null : avatar);
            updateUser = true;
        }
        if (hasValue(form, "bio")) {
            ext.setBio(trimToEmpty(firstValue(form, "bio")));
        }
        if (hasValue(form, "gender")) {
            ext.setGender(parseInt(firstValue(form, "gender"), nvl(ext.getGender())));
        }
        if (hasValue(form, "birthday")) {
            ext.setBirthday(trimToEmpty(firstValue(form, "birthday")));
        }
        if (hasValue(form, "height")) {
            ext.setHeight(trimToEmpty(firstValue(form, "height")));
        }
        if (hasValue(form, "weight")) {
            ext.setWeight(trimToEmpty(firstValue(form, "weight")));
        }
        if (hasValue(form, "country")) {
            ext.setCountry(trimToEmpty(firstValue(form, "country")));
        }
        if (hasValue(form, "characters")) {
            ext.setCharacters(trimToEmpty(joinValues(form.get("characters"))));
        }
        if (hasValue(form, "relation")) {
            ext.setRelation(trimToEmpty(joinValues(form.get("relation"))));
        }
        if (hasValue(form, "occupation")) {
            ext.setOccupation(trimToEmpty(joinValues(form.get("occupation"))));
        }
        if (hasValue(form, "label")) {
            ext.setLabel(trimToEmpty(joinValues(form.get("label"))));
        }
        if (hasValue(form, "need_edit")) {
            ext.setNeedEdit(parseInt(firstValue(form, "need_edit"), nvl(ext.getNeedEdit())));
        }
        profileExtMapper.upsert(ext);
        if (updateUser) {
            appUserMapper.updateById(user);
        }
        return buildLegacyProfileByToken(token);
    }

    @Transactional
    public void updateLanguageByToken(String token, String languageCode) {
        AppUser user = requireUserByToken(token);
        appUserMapper.updateLanguageById(user.getId(), trimToEmpty(languageCode));
    }

    @Transactional
    public void resetPasswordByToken(String token, String oldPassword, String newPassword) {
        AppUser user = requireUserByToken(token);
        validatePassword(newPassword);

        AppUserIdentity identity = identityMapper.findByUserIdAndType(user.getId(), IDENTITY_H5_ACCOUNT);
        if (identity == null || identity.getCredentialHash() == null || identity.getCredentialHash().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Current account has no password login");
        }
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, identity.getCredentialHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Old password is incorrect");
        }

        identity.setCredentialHash(passwordEncoder.encode(newPassword));
        identity.setVerified(Boolean.TRUE);
        identityMapper.updateById(identity);
    }

    public String pickToken(String headerToken, String requestToken) {
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken.trim();
        }
        return requestToken == null ? "" : requestToken.trim();
    }

    private Map<String, Object> buildLegacyUserInfo(AppUser user, AppH5UserProfileExt ext, String token) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean vipActive = isVipActive(ext);
        data.put("user_id", user.getId());
        data.put("id", user.getId());
        data.put("appUserId", user.getId());
        data.put("token", token);
        data.put("username", blank(user.getUsername()));
        data.put("nickname", blank(ext.getNickname()));
        data.put("avatar", blank(ext.getAvatar()));
        data.put("need_edit", nvl(ext.getNeedEdit()));
        data.put("status", blank(ext.getStatus()).isBlank() ? "normal" : ext.getStatus());
        data.put("vipType", nvl(ext.getVipType()));
        data.put("vipActive", vipActive);
        data.put("vipExpiresAt", ext.getVipExpiresAt());
        data.put("telegram_bound", user.getTelegramUserId() != null ? 1 : 0);
        return data;
    }

    private static boolean isVipActive(AppH5UserProfileExt ext) {
        return ext != null
                && nvl(ext.getVipType()) > 0
                && ext.getVipExpiresAt() != null
                && ext.getVipExpiresAt().isAfter(LocalDateTime.now());
    }

    private static boolean hasValue(MultiValueMap<String, String> form, String key) {
        return form != null && form.containsKey(key);
    }

    private static String firstValue(MultiValueMap<String, String> form, String key) {
        if (form == null) {
            return "";
        }
        List<String> values = form.get(key);
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.get(0);
    }

    private static String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return String.join(",", values);
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static void validatePassword(String raw) {
        if (raw == null || raw.length() < 6 || raw.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Password length must be 6-64");
        }
    }
}
