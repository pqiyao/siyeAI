package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class H5EntitlementService {

    public enum QuotaBucket {
        OFFICIAL_CHAT,
        BYOK_CHAT,
        IMAGE
    }

    public record AccessTicket(
            long userId,
            String clientUid,
            boolean consumesQuota,
            int consumeAmount,
            QuotaBucket quotaBucket,
            Long characterId,
            String action
    ) {}

    public record CharacterAccess(boolean unlocked, boolean vipOnly, String lockReason) {}

    public record CharacterCreationAccess(
            boolean allowed,
            int limit,
            int used,
            int remaining,
            String message
    ) {}

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AppH5UserProfileExtMapper profileExtMapper;
    private final H5MyCharacterMapper h5MyCharacterMapper;
    private final AppCharacterMapper characterMapper;
    private final EntitlementPolicyService entitlementPolicyService;
    private final H5UserAiProviderService userAiProviderService;
    private final AppFeatureSettingsService featureSettingsService;
    private final EntitlementAuditLogService auditLogService;

    public H5EntitlementService(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppH5UserProfileExtMapper profileExtMapper,
            H5MyCharacterMapper h5MyCharacterMapper,
            AppCharacterMapper characterMapper,
            EntitlementPolicyService entitlementPolicyService,
            H5UserAiProviderService userAiProviderService,
            AppFeatureSettingsService featureSettingsService,
            EntitlementAuditLogService auditLogService
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.profileExtMapper = profileExtMapper;
        this.h5MyCharacterMapper = h5MyCharacterMapper;
        this.characterMapper = characterMapper;
        this.entitlementPolicyService = entitlementPolicyService;
        this.userAiProviderService = userAiProviderService;
        this.featureSettingsService = featureSettingsService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void guardCharacterCreation(AppUser user, int additionalActiveCount) {
        if (user == null || additionalActiveCount <= 0) {
            return;
        }
        AppFeatureSettings settings = featureSettingsService.getSettings();
        if (!settings.isUserCharacterCreationEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭用户端自建角色卡");
        }
        AppH5UserProfileExt ext = ensureProfileExt(user);
        if (!Integer.valueOf(1).equals(ext.getCharacterCreateAllowed())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号暂未开通自建角色卡权限");
        }
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        int vipLevel = entitlementPolicyService.effectiveVipLevel(ext);
        int limit = entitlementPolicyService.characterCreateLimitFor(policy, vipLevel);
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前权益暂不支持自建角色卡");
        }
        int activeCount = h5MyCharacterMapper.countMineActive(user.getId());
        if (activeCount + additionalActiveCount > limit) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "当前权益最多可自建 " + limit + " 张角色卡，已达到上限"
            );
        }
    }

    @Transactional
    public void requireCharacterCreationAccess(AppUser user, int additionalActiveCount) {
        if (user == null) {
            return;
        }
        CharacterCreationAccess access = resolveCharacterCreationAccess(user);
        int requested = Math.max(1, additionalActiveCount);
        if (!access.allowed() || access.remaining() < requested) {
            throw new BusinessException(ErrorCode.FORBIDDEN, access.message());
        }
    }

    @Transactional
    public CharacterCreationAccess resolveCharacterCreationAccess(String clientUid) {
        return resolveCharacterCreationAccess(resolveUser(clientUid));
    }

    @Transactional
    public CharacterCreationAccess resolveCharacterCreationAccess(AppUser user) {
        if (user == null) {
            return new CharacterCreationAccess(false, 0, 0, 0, "请先登录后再自建角色卡");
        }
        AppFeatureSettings settings = featureSettingsService.getSettings();
        if (!settings.isUserCharacterCreationEnabled()) {
            int used = Math.max(0, h5MyCharacterMapper.countMineActive(user.getId()));
            return new CharacterCreationAccess(false, 0, used, 0, "当前已关闭用户端自建角色卡");
        }
        AppH5UserProfileExt ext = ensureProfileExt(user);
        int used = Math.max(0, h5MyCharacterMapper.countMineActive(user.getId()));
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        int vipLevel = entitlementPolicyService.effectiveVipLevel(ext);
        int limit = entitlementPolicyService.characterCreateLimitFor(policy, vipLevel);
        int remaining = Math.max(0, limit - used);
        if (!Integer.valueOf(1).equals(ext.getCharacterCreateAllowed())) {
            return new CharacterCreationAccess(false, limit, used, remaining, "当前账号暂未开通自建角色卡权限");
        }
        if (limit <= 0) {
            return new CharacterCreationAccess(false, limit, used, remaining, "当前权益暂不支持自建角色卡");
        }
        if (remaining <= 0) {
            return new CharacterCreationAccess(false, limit, used, 0, "当前权益最多可自建 " + limit + " 张角色卡，已达到上限");
        }
        return new CharacterCreationAccess(true, limit, used, remaining, "");
    }

    public Map<String, Object> toMap(CharacterCreationAccess access) {
        CharacterCreationAccess safe = access == null
                ? new CharacterCreationAccess(false, 0, 0, 0, "当前暂不可自建角色卡")
                : access;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("allowed", safe.allowed());
        AppFeatureSettings settings = featureSettingsService.getSettings();
        data.put("globalEnabled", settings.isUserCharacterCreationEnabled());
        data.put("limit", safe.limit());
        data.put("used", safe.used());
        data.put("remaining", safe.remaining());
        data.put("message", safe.message());
        return data;
    }

    @Transactional
    public AccessTicket guardChat(
            String clientUid,
            long characterId,
            EntitlementPolicyService.ChatQuotaAction action
    ) {
        AppUser user = resolveUser(clientUid);
        AppCharacter character = characterMapper.findById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在或已下架");
        }

        assertCharacterVisibleToUser(character, user.getId());

        AppH5UserProfileExt ext = ensureProfileExt(user);
        boolean byokActive = userAiProviderService.resolveActiveOverrideForUser(user.getId()) != null;
        boolean consumesQuota = byokActive
                ? entitlementPolicyService.consumesByokChatQuota(action)
                : entitlementPolicyService.consumesChatQuota(action);
        QuotaBucket quotaBucket = byokActive ? QuotaBucket.BYOK_CHAT : QuotaBucket.OFFICIAL_CHAT;

        if (Boolean.TRUE.equals(character.getVipOnly()) && !entitlementPolicyService.canAccessVipCharacter(ext)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色仅会员可用，请先开通会员");
        }

        if (consumesQuota) {
            int quota;
            int used;
            if (byokActive) {
                EntitlementPolicy policy = entitlementPolicyService.getPolicy();
                quota = entitlementPolicyService.byokChatQuotaFor(policy, entitlementPolicyService.effectiveVipLevel(ext));
                used = nvl(ext.getDailyByokChatUsed());
            } else {
                quota = nvl(ext.getDailyChatQuota());
                used = nvl(ext.getDailyChatUsed());
            }
            if (quota <= 0 || used >= quota) {
                if (byokActive) {
                    throw new BusinessException(ErrorCode.RATE_LIMITED, "今日自定义 API 聊天次数已用完，请明日再试");
                }
                throw new BusinessException(ErrorCode.RATE_LIMITED, "今日聊天次数已用完，请升级会员或明日再试");
            }
        }
        return new AccessTicket(
                user.getId(),
                blankClientUid(clientUid),
                consumesQuota,
                consumesQuota ? 1 : 0,
                quotaBucket,
                characterId,
                action == null ? "" : action.name()
        );
    }

    @Transactional(readOnly = true)
    public boolean canAccessVipCharacters(String clientUid) {
        if (clientUid == null || clientUid.isBlank()) {
            return entitlementPolicyService.canAccessVipCharacter(null);
        }
        try {
            AppUser user = resolveUser(clientUid);
            AppH5UserProfileExt ext = ensureProfileExt(user);
            return entitlementPolicyService.canAccessVipCharacter(ext);
        } catch (Exception ignored) {
            return entitlementPolicyService.canAccessVipCharacter(null);
        }
    }

    private void assertCharacterVisibleToUser(AppCharacter character, long userId) {
        Long ownerId = character.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(character.getPrivateCard())) {
            if (ownerId == null || ownerId.longValue() != userId) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
            }
            return;
        }
        if (Boolean.FALSE.equals(character.getClientVisible())
                || !CharacterReviewStatus.APPROVED.equals(CharacterReviewStatus.normalize(character.getReviewStatus()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
        }
    }

    @Transactional(readOnly = true)
    public CharacterAccess resolveCharacterAccess(String clientUid, Boolean vipOnly, Boolean unlockedDefault) {
        boolean baseUnlocked = !Boolean.FALSE.equals(unlockedDefault);
        if (!Boolean.TRUE.equals(vipOnly)) {
            return new CharacterAccess(baseUnlocked, false, baseUnlocked ? "" : "当前角色暂未开放");
        }
        boolean canAccessVip = canAccessVipCharacters(clientUid);
        boolean unlocked = baseUnlocked && canAccessVip;
        return new CharacterAccess(unlocked, true, unlocked ? "" : "当前角色仅会员可用，请先开通会员");
    }

    @Transactional(readOnly = true)
    public int currentVipLevel(String clientUid) {
        if (clientUid == null || clientUid.isBlank()) {
            return 0;
        }
        try {
            AppUser user = resolveUser(clientUid);
            AppH5UserProfileExt ext = ensureProfileExt(user);
            return entitlementPolicyService.effectiveVipLevel(ext);
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Transactional
    public void recordSuccessfulChat(AccessTicket ticket, boolean generatedContentReady) {
        if (ticket == null || !ticket.consumesQuota() || !generatedContentReady) {
            return;
        }
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(ticket.userId());
        if (ext == null) {
            return;
        }
        refreshUsageWindow(ext);
        int quota;
        int beforeUsed;
        if (ticket.quotaBucket() == QuotaBucket.BYOK_CHAT) {
            EntitlementPolicy policy = entitlementPolicyService.getPolicy();
            quota = entitlementPolicyService.byokChatQuotaFor(policy, entitlementPolicyService.effectiveVipLevel(ext));
            beforeUsed = nvl(ext.getDailyByokChatUsed());
            ext.setDailyByokChatUsed(beforeUsed + Math.max(1, ticket.consumeAmount()));
        } else {
            quota = nvl(ext.getDailyChatQuota());
            beforeUsed = nvl(ext.getDailyChatUsed());
            ext.setDailyChatUsed(beforeUsed + Math.max(1, ticket.consumeAmount()));
        }
        profileExtMapper.upsert(ext);
        int afterUsed = ticket.quotaBucket() == QuotaBucket.BYOK_CHAT
                ? nvl(ext.getDailyByokChatUsed())
                : nvl(ext.getDailyChatUsed());
        auditLogService.recordQuotaConsumed(
                ticket.userId(),
                ticket.clientUid(),
                ticket.quotaBucket().name(),
                Math.max(1, ticket.consumeAmount()),
                quota,
                beforeUsed,
                afterUsed,
                ticket.characterId(),
                ticket.action()
        );
    }

    @Transactional
    public AccessTicket guardImage(String clientUid, int imageCount) {
        return guardImage(clientUid, imageCount, 0L);
    }

    @Transactional
    public AccessTicket guardImage(String clientUid, int imageCount, long characterId) {
        AppUser user = resolveUser(clientUid);
        if (!featureSettingsService.getSettings().isImageGenerationEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭聊天生图");
        }
        AppH5UserProfileExt ext = ensureProfileExt(user);
        int requested = Math.max(1, imageCount);
        int quota = nvl(ext.getDailyImageQuota());
        int used = nvl(ext.getDailyImageUsed());
        if (quota <= 0 || used + requested > quota) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "今日生图次数已用完，请升级会员或明日再试");
        }
        return new AccessTicket(
                user.getId(),
                blankClientUid(clientUid),
                true,
                requested,
                QuotaBucket.IMAGE,
                characterId > 0 ? characterId : null,
                "GENERATE_IMAGE"
        );
    }

    @Transactional(readOnly = true)
    public void guardImageCharacterAccess(String clientUid, long characterId) {
        if (characterId <= 0) {
            return;
        }
        AppCharacter character = characterMapper.findById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        Long ownerId = character.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(character.getPrivateCard())) {
            AppUser user = resolveUser(clientUid);
            if (ownerId == null || !ownerId.equals(user.getId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
            }
            return;
        }
        if (Boolean.FALSE.equals(character.getClientVisible())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        if (!CharacterReviewStatus.APPROVED.equals(CharacterReviewStatus.normalize(character.getReviewStatus()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
        }
        CharacterAccess access = resolveCharacterAccess(clientUid, character.getVipOnly(), character.getUnlockedDefault());
        if (!access.unlocked()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, access.lockReason() == null || access.lockReason().isBlank()
                    ? "当前角色不可访问"
                    : access.lockReason());
        }
    }

    @Transactional
    public void recordSuccessfulImage(AccessTicket ticket, int generatedCount) {
        if (ticket == null || !ticket.consumesQuota() || generatedCount <= 0) {
            return;
        }
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(ticket.userId());
        if (ext == null) {
            return;
        }
        refreshUsageWindow(ext);
        int quota = nvl(ext.getDailyImageQuota());
        int beforeUsed = nvl(ext.getDailyImageUsed());
        ext.setDailyImageUsed(beforeUsed + Math.max(1, generatedCount));
        profileExtMapper.upsert(ext);
        auditLogService.recordQuotaConsumed(
                ticket.userId(),
                ticket.clientUid(),
                ticket.quotaBucket().name(),
                Math.max(1, generatedCount),
                quota,
                beforeUsed,
                nvl(ext.getDailyImageUsed()),
                ticket.characterId(),
                ticket.action()
        );
    }

    @Transactional
    public AppH5UserProfileExt ensureProfileExt(AppUser user) {
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(user.getId());
        boolean changed = false;
        if (ext == null) {
            ext = new AppH5UserProfileExt();
            ext.setUserId(user.getId());
            ext.setNickname(fallbackUsername(user));
            ext.setAvatar(user.getPhotoUrl());
            ext.setBio("");
            ext.setVipType(0);
            ext.setVipExpiresAt(null);
            ext.setScore(0);
            ext.setGoldCoin(0);
            ext.setDailyChatQuota(0);
            ext.setDailyImageQuota(0);
            ext.setChatQuotaOverride(null);
            ext.setImageQuotaOverride(null);
            ext.setDailyChatUsed(0);
            ext.setDailyByokChatUsed(0);
            ext.setDailyImageUsed(0);
            ext.setUsageResetDate(LocalDate.now());
            ext.setCharacterCreateAllowed(0);
            ext.setNeedEdit(0);
            ext.setStatus("normal");
            ext.setGender(0);
            ext.setBirthday("");
            ext.setHeight("");
            ext.setWeight("");
            ext.setCountry("");
            ext.setCharacters("");
            ext.setRelation("");
            ext.setOccupation("");
            ext.setLabel("");
            changed = true;
        } else {
            if (ext.getNickname() == null || ext.getNickname().isBlank()) {
                ext.setNickname(fallbackUsername(user));
                changed = true;
            }
            if ((ext.getAvatar() == null || ext.getAvatar().isBlank()) && user.getPhotoUrl() != null) {
                ext.setAvatar(user.getPhotoUrl());
                changed = true;
            }
            if (ext.getCharacterCreateAllowed() == null) {
                ext.setCharacterCreateAllowed(0);
                changed = true;
            }
        }
        if (refreshUsageWindow(ext)) {
            changed = true;
        }
        if (entitlementPolicyService.refreshEffectiveQuota(ext)) {
            changed = true;
        }
        if (changed) {
            profileExtMapper.upsert(ext);
            ext = profileExtMapper.findByUserId(user.getId());
        }
        return ext;
    }

    public AppUser resolveUser(String clientUid) {
        String token = h5Auth.issueTokenForClientUid(clientUid);
        return tokenService.validateAndLoadUser(token);
    }

    @Transactional(readOnly = true)
    public int currentRemainingImageQuota(long userId) {
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(userId);
        if (ext == null) {
            return 0;
        }
        int quota = nvl(ext.getDailyImageQuota());
        int used = nvl(ext.getDailyImageUsed());
        return Math.max(0, quota - used);
    }

    private boolean refreshUsageWindow(AppH5UserProfileExt ext) {
        LocalDate today = LocalDate.now();
        boolean changed = false;
        if (ext.getUsageResetDate() == null || !today.equals(ext.getUsageResetDate())) {
            ext.setUsageResetDate(today);
            ext.setDailyChatUsed(0);
            ext.setDailyByokChatUsed(0);
            ext.setDailyImageUsed(0);
            changed = true;
        }
        if (ext.getDailyChatUsed() == null) {
            ext.setDailyChatUsed(0);
            changed = true;
        }
        if (ext.getDailyByokChatUsed() == null) {
            ext.setDailyByokChatUsed(0);
            changed = true;
        }
        if (ext.getDailyImageUsed() == null) {
            ext.setDailyImageUsed(0);
            changed = true;
        }
        return changed;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static String fallbackUsername(AppUser user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "用户" + user.getId();
    }

    private static String blankClientUid(String clientUid) {
        return clientUid == null ? "" : clientUid.trim();
    }
}
