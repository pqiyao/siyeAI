package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.service.WalletLedgerService;
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
import com.example.sillyspringboot.ops.checkin.entity.AppCheckinActivity;
import com.example.sillyspringboot.ops.checkin.mapper.AppCheckinActivityMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class H5EntitlementService {

    private static final String DAILY_CHECKIN_CODE = "daily_checkin";
    private static final ZoneId DEFAULT_DAILY_USAGE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long DAILY_USAGE_ZONE_CACHE_MILLIS = 60_000L;

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
            String action,
            boolean usesWallet,
            int scoreCost,
            int goldCost,
            String consumeBizRef,
            boolean walletChargeCreated
    ) {
        public AccessTicket(
                long userId,
                String clientUid,
                boolean consumesQuota,
                int consumeAmount,
                QuotaBucket quotaBucket,
                Long characterId,
                String action,
                boolean usesWallet,
                int scoreCost,
                int goldCost,
                String consumeBizRef
        ) {
            this(userId, clientUid, consumesQuota, consumeAmount, quotaBucket, characterId, action,
                    usesWallet, scoreCost, goldCost, consumeBizRef, false);
        }

        public AccessTicket(
                long userId,
                String clientUid,
                boolean consumesQuota,
                int consumeAmount,
                QuotaBucket quotaBucket,
                Long characterId,
                String action
        ) {
            this(userId, clientUid, consumesQuota, consumeAmount, quotaBucket, characterId, action,
                    false, 0, 0, "", false);
        }
    }

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
    private final WalletLedgerService walletLedgerService;
    private final AppCheckinActivityMapper checkinActivityMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile ZoneId dailyUsageZone = DEFAULT_DAILY_USAGE_ZONE;
    private volatile long dailyUsageZoneRefreshAt;

    public H5EntitlementService(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppH5UserProfileExtMapper profileExtMapper,
            H5MyCharacterMapper h5MyCharacterMapper,
            AppCharacterMapper characterMapper,
            EntitlementPolicyService entitlementPolicyService,
            H5UserAiProviderService userAiProviderService,
            AppFeatureSettingsService featureSettingsService,
            EntitlementAuditLogService auditLogService,
            WalletLedgerService walletLedgerService,
            AppCheckinActivityMapper checkinActivityMapper
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
        this.walletLedgerService = walletLedgerService;
        this.checkinActivityMapper = checkinActivityMapper;
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

        String actionName = action == null ? "" : action.name();
        if (!consumesQuota) {
            return new AccessTicket(
                    user.getId(),
                    blankClientUid(clientUid),
                    false,
                    0,
                    quotaBucket,
                    characterId,
                    actionName,
                    false,
                    0,
                    0,
                    ""
            );
        }

        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        int quota;
        int used;
        int bonus = 0;
        if (byokActive) {
            quota = entitlementPolicyService.byokChatQuotaFor(policy, entitlementPolicyService.effectiveVipLevel(ext));
            used = nvl(ext.getDailyByokChatUsed());
        } else {
            quota = nvl(ext.getDailyChatQuota());
            used = nvl(ext.getDailyChatUsed());
            bonus = nvl(ext.getDailyChatBonus());
        }

        int effectiveQuota = quota + bonus;
        if (effectiveQuota > 0 && used < effectiveQuota) {
            return new AccessTicket(
                    user.getId(),
                    blankClientUid(clientUid),
                    true,
                    1,
                    quotaBucket,
                    characterId,
                    actionName,
                    false,
                    0,
                    0,
                    ""
            );
        }

        return buildWalletOverageTicket(
                user,
                ext,
                blankClientUid(clientUid),
                quotaBucket,
                characterId,
                actionName,
                Math.max(0, policy.getChatScoreCost()),
                Math.max(0, policy.getChatGoldCost()),
                policy.isOverQuotaBillingEnabled(),
                byokActive
                        ? "今日自定义 API 聊天次数已用完，请明日再试"
                        : "今日聊天次数已用完，请升级会员或明日再试",
                "chat"
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
        if (ticket == null || !generatedContentReady) {
            return;
        }
        if (ticket.usesWallet()) {
            walletLedgerService.consumeDiamonds(
                    ticket.userId(),
                    ticket.scoreCost(),
                    ticket.goldCost(),
                    WalletLedgerService.BIZ_CHAT_CONSUME,
                    ticket.consumeBizRef(),
                    "聊天超额消费"
            );
            return;
        }
        if (!ticket.consumesQuota()) {
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
        return guardImage(clientUid, imageCount, characterId, null);
    }

    @Transactional
    public AccessTicket guardImage(String clientUid, int imageCount, long characterId, String requestId) {
        AppUser user = resolveUser(clientUid);
        if (!featureSettingsService.getSettings().isImageGenerationEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭聊天生图");
        }
        ensureProfileExt(user);
        AppH5UserProfileExt ext = profileExtMapper.findByUserIdForUpdate(user.getId());
        if (ext == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "用户权益数据不存在");
        }
        boolean changed = refreshUsageWindow(ext);
        if (entitlementPolicyService.refreshEffectiveQuota(ext)) {
            changed = true;
        }
        if (changed) {
            profileExtMapper.upsert(ext);
            ext = profileExtMapper.findByUserIdForUpdate(user.getId());
        }
        int requested = Math.max(1, imageCount);
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        int quota = nvl(ext.getDailyImageQuota());
        int used = nvl(ext.getDailyImageUsed());
        int freeRemaining = Math.max(0, quota + nvl(ext.getDailyImageBonus()) - used);

        if (freeRemaining >= requested) {
            ext.setDailyImageUsed(used + requested);
            profileExtMapper.upsert(ext);
            return new AccessTicket(
                    user.getId(),
                    blankClientUid(clientUid),
                    true,
                    requested,
                    QuotaBucket.IMAGE,
                    characterId > 0 ? characterId : null,
                    "GENERATE_IMAGE",
                    false,
                    0,
                    0,
                    "",
                    false
            );
        }

        int paidCount = requested - freeRemaining;
        int unitScore = Math.max(0, policy.getImageScoreCost());
        int unitGold = Math.max(0, policy.getImageGoldCost());
        int scoreCost = unitScore * paidCount;
        int goldCost = unitGold * paidCount;

        if (!policy.isOverQuotaBillingEnabled() || (scoreCost <= 0 && goldCost <= 0)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "今日生图次数已用完，请升级会员或明日再试");
        }
        assertWalletBalance(ext, scoreCost, goldCost);
        String bizRef = retryableConsumeBizRef(
                imageConsumeBizRef(user.getId(), requestId),
                WalletLedgerService.BIZ_IMAGE_CONSUME,
                WalletLedgerService.BIZ_IMAGE_REFUND
        );
        boolean chargeCreated = walletLedgerService.consumeDiamonds(
                user.getId(), scoreCost, goldCost, WalletLedgerService.BIZ_IMAGE_CONSUME, bizRef, "生图超额消费");
        if (freeRemaining > 0) {
            ext.setDailyImageUsed(used + freeRemaining);
            profileExtMapper.upsert(ext);
        }
        return new AccessTicket(
                user.getId(),
                blankClientUid(clientUid),
                freeRemaining > 0,
                freeRemaining,
                QuotaBucket.IMAGE,
                characterId > 0 ? characterId : null,
                "GENERATE_IMAGE",
                true,
                scoreCost,
                goldCost,
                bizRef,
                chargeCreated
        );
    }

    /**
     * TTS 无免费日额度：cost&gt;0 时走钱包；均为 0 则语音功能开启后不限次免费。
     */
    @Transactional
    public AccessTicket guardTts(String clientUid) {
        return guardTts(clientUid, null);
    }

    @Transactional
    public AccessTicket guardTts(String clientUid, String requestId) {
        AppUser user = resolveUser(clientUid);
        featureSettingsService.ensureVoiceFeatureEnabled();
        AppH5UserProfileExt ext = ensureProfileExt(user);
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        int scoreCost = Math.max(0, policy.getTtsScoreCost());
        int goldCost = Math.max(0, policy.getTtsGoldCost());
        if (scoreCost == 0 && goldCost == 0) {
            return new AccessTicket(
                    user.getId(),
                    blankClientUid(clientUid),
                    false,
                    0,
                    QuotaBucket.OFFICIAL_CHAT,
                    null,
                    "TTS",
                    false,
                    0,
                    0,
                    ""
            );
        }
        String bizRef = retryableConsumeBizRef(
                ttsConsumeBizRef(user.getId(), requestId),
                WalletLedgerService.BIZ_TTS_CONSUME,
                WalletLedgerService.BIZ_TTS_REFUND
        );
        if (!walletLedgerService.hasLedgerEntry(WalletLedgerService.BIZ_TTS_CONSUME, bizRef)) {
            assertWalletBalance(ext, scoreCost, goldCost);
        }
        return new AccessTicket(
                user.getId(),
                blankClientUid(clientUid),
                false,
                0,
                QuotaBucket.OFFICIAL_CHAT,
                null,
                "TTS",
                true,
                scoreCost,
                goldCost,
                bizRef
        );
    }

    @Transactional
    public boolean recordSuccessfulTts(AccessTicket ticket) {
        if (ticket == null || !ticket.usesWallet()) {
            return false;
        }
        return walletLedgerService.consumeDiamonds(
                ticket.userId(),
                ticket.scoreCost(),
                ticket.goldCost(),
                WalletLedgerService.BIZ_TTS_CONSUME,
                ticket.consumeBizRef(),
                "语音合成消费"
        );
    }

    /**
     * Refund a wallet consume that was reserved before an operation failed.
     * Uses a distinct refund idempotency key to avoid colliding with the original consume row.
     */
    @Transactional
    public void refundWalletConsume(AccessTicket ticket) {
        if (ticket == null || !ticket.usesWallet()) {
            return;
        }
        if (ticket.scoreCost() <= 0 && ticket.goldCost() <= 0) {
            return;
        }
        String bizRef = ticket.consumeBizRef();
        if (bizRef == null || bizRef.isBlank()) {
            return;
        }
        walletLedgerService.refundConsume(
                ticket.userId(),
                ticket.scoreCost(),
                ticket.goldCost(),
                WalletLedgerService.BIZ_TTS_REFUND,
                "refund:" + bizRef,
                "语音合成失败退回"
        );
    }

    /**
     * STT 默认免费；后台配置单价后，每个录音任务只扣一次，失败自动退款。
     */
    @Transactional
    public AccessTicket guardStt(String clientUid, String requestId) {
        AppUser user = resolveUser(clientUid);
        featureSettingsService.ensureVoiceFeatureEnabled();
        AppH5UserProfileExt ext = ensureProfileExt(user);
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        int scoreCost = Math.max(0, policy.getSttScoreCost());
        int goldCost = Math.max(0, policy.getSttGoldCost());
        if (scoreCost == 0 && goldCost == 0) {
            return new AccessTicket(
                    user.getId(), blankClientUid(clientUid), false, 0, QuotaBucket.OFFICIAL_CHAT,
                    null, "STT", false, 0, 0, ""
            );
        }
        String bizRef = retryableConsumeBizRef(
                hashedConsumeBizRef("stt", user.getId(), requestId),
                WalletLedgerService.BIZ_STT_CONSUME,
                WalletLedgerService.BIZ_STT_REFUND
        );
        if (!walletLedgerService.hasLedgerEntry(WalletLedgerService.BIZ_STT_CONSUME, bizRef)) {
            assertWalletBalance(ext, scoreCost, goldCost);
        }
        return new AccessTicket(
                user.getId(), blankClientUid(clientUid), false, 0, QuotaBucket.OFFICIAL_CHAT,
                null, "STT", true, scoreCost, goldCost, bizRef
        );
    }

    @Transactional
    public boolean reserveSttCharge(AccessTicket ticket) {
        if (ticket == null || !ticket.usesWallet()) {
            return false;
        }
        return walletLedgerService.consumeDiamonds(
                ticket.userId(), ticket.scoreCost(), ticket.goldCost(),
                WalletLedgerService.BIZ_STT_CONSUME, ticket.consumeBizRef(), "语音识别消费"
        );
    }

    @Transactional
    public void refundSttCharge(AccessTicket ticket) {
        if (ticket == null || !ticket.usesWallet() || ticket.consumeBizRef().isBlank()) {
            return;
        }
        walletLedgerService.refundConsume(
                ticket.userId(), ticket.scoreCost(), ticket.goldCost(),
                WalletLedgerService.BIZ_STT_REFUND, "refund:" + ticket.consumeBizRef(), "语音识别失败退回"
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
        if (ticket == null || generatedCount <= 0) {
            return;
        }
        if (!ticket.consumesQuota()) {
            return;
        }
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(ticket.userId());
        if (ext == null) {
            return;
        }
        refreshUsageWindow(ext);
        int quota = nvl(ext.getDailyImageQuota());
        int quotaConsume = Math.max(0, ticket.consumeAmount());
        if (quotaConsume <= 0) {
            return;
        }
        int afterUsed = nvl(ext.getDailyImageUsed());
        int beforeUsed = Math.max(0, afterUsed - quotaConsume);
        auditLogService.recordQuotaConsumed(
                ticket.userId(),
                ticket.clientUid(),
                ticket.quotaBucket().name(),
                quotaConsume,
                quota,
                beforeUsed,
                afterUsed,
                ticket.characterId(),
                ticket.action()
        );
    }

    @Transactional
    public void releaseImageReservation(AccessTicket ticket) {
        if (ticket == null || ticket.quotaBucket() != QuotaBucket.IMAGE) {
            return;
        }
        if (ticket.consumesQuota() && ticket.consumeAmount() > 0) {
            AppH5UserProfileExt ext = profileExtMapper.findByUserIdForUpdate(ticket.userId());
            if (ext != null) {
                refreshUsageWindow(ext);
                ext.setDailyImageUsed(Math.max(0, nvl(ext.getDailyImageUsed()) - ticket.consumeAmount()));
                profileExtMapper.upsert(ext);
            }
        }
        if (ticket.walletChargeCreated()) {
            walletLedgerService.refundConsume(
                    ticket.userId(),
                    ticket.scoreCost(),
                    ticket.goldCost(),
                    WalletLedgerService.BIZ_IMAGE_REFUND,
                    "refund:" + ticket.consumeBizRef(),
                    "生图失败退回"
            );
        }
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
            ext.setDailyChatBonus(0);
            ext.setDailyByokChatUsed(0);
            ext.setDailyImageUsed(0);
            ext.setDailyImageBonus(0);
            ext.setUsageResetDate(currentUsageDate());
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
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
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
        return Math.max(0, quota + nvl(ext.getDailyImageBonus()) - used);
    }

    private boolean refreshUsageWindow(AppH5UserProfileExt ext) {
        LocalDate today = currentUsageDate();
        boolean changed = false;
        if (ext.getUsageResetDate() == null || !today.equals(ext.getUsageResetDate())) {
            ext.setUsageResetDate(today);
            ext.setDailyChatUsed(0);
            ext.setDailyByokChatUsed(0);
            ext.setDailyImageUsed(0);
            ext.setDailyChatBonus(0);
            ext.setDailyImageBonus(0);
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
        if (ext.getDailyChatBonus() == null) {
            ext.setDailyChatBonus(0);
            changed = true;
        }
        if (ext.getDailyImageBonus() == null) {
            ext.setDailyImageBonus(0);
            changed = true;
        }
        return changed;
    }

    public void updateDailyUsageTimezone(String timezone) {
        dailyUsageZone = parseDailyUsageZone(timezone);
        dailyUsageZoneRefreshAt = System.currentTimeMillis() + DAILY_USAGE_ZONE_CACHE_MILLIS;
    }

    private LocalDate currentUsageDate() {
        return LocalDate.now(currentDailyUsageZone());
    }

    private ZoneId currentDailyUsageZone() {
        long now = System.currentTimeMillis();
        if (now < dailyUsageZoneRefreshAt) {
            return dailyUsageZone;
        }
        synchronized (this) {
            if (now < dailyUsageZoneRefreshAt) {
                return dailyUsageZone;
            }
            try {
                AppCheckinActivity activity = checkinActivityMapper.findByCode(DAILY_CHECKIN_CODE);
                dailyUsageZone = parseDailyUsageZone(activity == null ? null : activity.getTimezone());
            } catch (RuntimeException ignored) {
                dailyUsageZone = DEFAULT_DAILY_USAGE_ZONE;
            }
            dailyUsageZoneRefreshAt = now + DAILY_USAGE_ZONE_CACHE_MILLIS;
            return dailyUsageZone;
        }
    }

    private static ZoneId parseDailyUsageZone(String timezone) {
        String value = timezone == null || timezone.isBlank() ? DEFAULT_DAILY_USAGE_ZONE.getId() : timezone.trim();
        try {
            return ZoneId.of(value);
        } catch (Exception ignored) {
            return DEFAULT_DAILY_USAGE_ZONE;
        }
    }

    private AccessTicket buildWalletOverageTicket(
            AppUser user,
            AppH5UserProfileExt ext,
            String clientUid,
            QuotaBucket quotaBucket,
            Long characterId,
            String action,
            int scoreCost,
            int goldCost,
            boolean overQuotaBillingEnabled,
            String exhaustedMessage,
            String bizPrefix
    ) {
        if (!overQuotaBillingEnabled || (scoreCost <= 0 && goldCost <= 0)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, exhaustedMessage);
        }
        assertWalletBalance(ext, scoreCost, goldCost);
        String bizRef = newConsumeBizRef(bizPrefix, user.getId());
        return new AccessTicket(
                user.getId(),
                clientUid,
                false,
                0,
                quotaBucket,
                characterId,
                action,
                true,
                scoreCost,
                goldCost,
                bizRef
        );
    }

    private void assertWalletBalance(AppH5UserProfileExt ext, int scoreCost, int goldCost) {
        if (nvl(ext.getScore()) < scoreCost || nvl(ext.getGoldCoin()) < goldCost) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "免费次数已用完，钻石不足，请充值");
        }
    }

    private String newConsumeBizRef(String prefix, long userId) {
        return prefix + ":" + userId + ":" + System.currentTimeMillis() + ":"
                + Integer.toHexString(secureRandom.nextInt())
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String ttsConsumeBizRef(long userId, String requestId) {
        String safeRequestId = requestId == null ? "" : requestId.trim();
        if (safeRequestId.isBlank()) {
            return newConsumeBizRef("tts", userId);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + safeRequestId).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", digest[i] & 0xff));
            }
            return "tts:" + userId + ":" + hex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "TTS 幂等标识生成失败", ex);
        }
    }

    private String imageConsumeBizRef(long userId, String requestId) {
        String safeRequestId = requestId == null ? "" : requestId.trim();
        if (safeRequestId.isBlank()) {
            return newConsumeBizRef("image", userId);
        }
        return hashedConsumeBizRef("image", userId, safeRequestId);
    }

    private String hashedConsumeBizRef(String prefix, long userId, String requestId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + requestId).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", digest[i] & 0xff));
            }
            return prefix + ":" + userId + ":" + hex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, prefix + " 幂等标识生成失败", ex);
        }
    }

    private String retryableConsumeBizRef(String baseBizRef, String consumeType, String refundType) {
        String base = baseBizRef == null ? "" : baseBizRef.trim();
        for (int attempt = 0; attempt < 32; attempt++) {
            String candidate = attempt == 0 ? base : base + ":r" + attempt;
            boolean consumed = walletLedgerService.hasLedgerEntry(consumeType, candidate);
            boolean refunded = walletLedgerService.hasLedgerEntry(refundType, "refund:" + candidate);
            if (!consumed || !refunded) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.RATE_LIMITED, "媒体任务重试次数过多，请稍后重新发起");
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
