package com.example.sillyspringboot.ops.checkin.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.service.WalletLedgerService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ops.checkin.entity.AppCheckinActivity;
import com.example.sillyspringboot.ops.checkin.entity.AppCheckinClaim;
import com.example.sillyspringboot.ops.checkin.mapper.AppCheckinActivityMapper;
import com.example.sillyspringboot.ops.checkin.mapper.AppCheckinClaimMapper;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Daily check-in rewards. Identity isolation is NOT modified here:
 * resolves user exclusively via existing H5ClientUidAuthService + AppTokenService
 * (login required, h5u_{id} must match token user).
 */
@Service
public class CheckinService {

    public static final String DEFAULT_CODE = "daily_checkin";
    private static final int MAX_REWARD_AMOUNT = 999_999;
    private static final int MAX_BONUS_AMOUNT = 9_999;
    private static final int MAX_STREAK_DAY = 365;

    private final AppCheckinActivityMapper activityMapper;
    private final AppCheckinClaimMapper claimMapper;
    private final AppH5UserProfileExtMapper profileExtMapper;
    private final WalletLedgerService walletLedgerService;
    private final H5EntitlementService entitlementService;
    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final ObjectMapper objectMapper;

    public CheckinService(
            AppCheckinActivityMapper activityMapper,
            AppCheckinClaimMapper claimMapper,
            AppH5UserProfileExtMapper profileExtMapper,
            WalletLedgerService walletLedgerService,
            H5EntitlementService entitlementService,
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            ObjectMapper objectMapper
    ) {
        this.activityMapper = activityMapper;
        this.claimMapper = claimMapper;
        this.profileExtMapper = profileExtMapper;
        this.walletLedgerService = walletLedgerService;
        this.entitlementService = entitlementService;
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> status(String clientUid) {
        AppUser user = resolveUser(clientUid);
        AppCheckinActivity activity = requireActivity();
        entitlementService.updateDailyUsageTimezone(activity.getTimezone());
        LocalDate bizDate = bizDate(activity);
        AppCheckinClaim today = claimMapper.findByUserActivityDate(user.getId(), activity.getId(), bizDate);
        int streakDay = today != null
                ? nvl(today.getStreakDay())
                : previewStreakDay(user.getId(), activity.getId(), bizDate);
        RewardBundle todayReward = today != null
                ? RewardBundle.fromClaim(today)
                : computeReward(activity, streakDay);
        RewardBundle tomorrowReward = computeReward(activity, tomorrowStreakPreview(streakDay));
        AppH5UserProfileExt ext = entitlementService.ensureProfileExt(user);
        return buildStatusPayload(
                activity,
                bizDate,
                today != null,
                streakDay,
                todayReward,
                tomorrowReward,
                ext,
                null,
                false,
                user.getId()
        );
    }

    @Transactional
    public Map<String, Object> claim(String clientUid) {
        AppUser user = resolveUser(clientUid);
        AppCheckinActivity activity = requireActivity();
        assertActivityOpen(activity);
        entitlementService.updateDailyUsageTimezone(activity.getTimezone());
        LocalDate bizDate = bizDate(activity);
        entitlementService.ensureProfileExt(user);

        AppCheckinClaim existing = claimMapper.findByUserActivityDate(user.getId(), activity.getId(), bizDate);
        if (existing != null) {
            AppH5UserProfileExt ext = entitlementService.ensureProfileExt(user);
            RewardBundle granted = RewardBundle.fromClaim(existing);
            return buildStatusPayload(
                    activity,
                    bizDate,
                    true,
                    nvl(existing.getStreakDay()),
                    granted,
                    computeReward(activity, tomorrowStreakPreview(nvl(existing.getStreakDay()))),
                    ext,
                    granted,
                    false,
                    user.getId()
            );
        }

        int streakDay = previewStreakDay(user.getId(), activity.getId(), bizDate);
        RewardBundle reward = computeReward(activity, streakDay);
        if (reward.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u5f53\u524d\u7b7e\u5230\u5956\u52b1\u4e3a\u7a7a\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458");
        }

        String ledgerKey = "CHECKIN:" + activity.getId() + ":" + user.getId() + ":" + bizDate;
        AppCheckinClaim claim = new AppCheckinClaim();
        claim.setUserId(user.getId());
        claim.setActivityId(activity.getId());
        claim.setBizDate(bizDate);
        claim.setStreakDay(streakDay);
        claim.setRewardScore(reward.score());
        claim.setRewardGold(reward.gold());
        claim.setRewardChatBonus(reward.chatBonus());
        claim.setRewardImageBonus(reward.imageBonus());
        claim.setRewardJson(writeJson(reward.toMap()));
        claim.setLedgerIdempotencyKey(ledgerKey);

        try {
            if (claimMapper.insert(claim) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "\u7b7e\u5230\u8bb0\u5f55\u5199\u5165\u5931\u8d25");
            }
        } catch (DuplicateKeyException ignored) {
            AppCheckinClaim raced = claimMapper.findByUserActivityDate(user.getId(), activity.getId(), bizDate);
            AppH5UserProfileExt ext = entitlementService.ensureProfileExt(user);
            RewardBundle granted = raced == null ? reward : RewardBundle.fromClaim(raced);
            return buildStatusPayload(
                    activity,
                    bizDate,
                    true,
                    raced == null ? streakDay : nvl(raced.getStreakDay()),
                    granted,
                    computeReward(activity, tomorrowStreakPreview(nvl(raced == null ? streakDay : raced.getStreakDay()))),
                    ext,
                    granted,
                    false,
                    user.getId()
            );
        }

        if (reward.score() > 0 || reward.gold() > 0) {
            walletLedgerService.insertCheckinCredit(
                    user.getId(),
                    activity.getId() + ":" + user.getId() + ":" + bizDate,
                    reward.score(),
                    reward.gold(),
                    "\u6bcf\u65e5\u7b7e\u5230"
            );
        }
        if (reward.chatBonus() > 0 || reward.imageBonus() > 0) {
            if (profileExtMapper.addDailyBonus(user.getId(), reward.chatBonus(), reward.imageBonus()) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "\u7b7e\u5230\u6b21\u6570\u52a0\u8d60\u5931\u8d25");
            }
        }

        AppH5UserProfileExt ext = entitlementService.ensureProfileExt(user);
        return buildStatusPayload(
                activity,
                bizDate,
                true,
                streakDay,
                reward,
                computeReward(activity, tomorrowStreakPreview(streakDay)),
                ext,
                reward,
                true,
                user.getId()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminGetActivity() {
        return toAdminActivityMap(requireActivity());
    }

    @Transactional
    public Map<String, Object> adminSaveActivity(Map<String, Object> body) {
        AppCheckinActivity activity = requireActivity();
        if (body == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "body required");
        }
        if (body.containsKey("name")) {
            activity.setName(stringVal(body.get("name")));
        }
        if (body.containsKey("enabled")) {
            activity.setEnabled(boolInt(body.get("enabled")));
        }
        if (body.containsKey("startAt")) {
            activity.setStartAt(parseDateTime(body.get("startAt")));
        }
        if (body.containsKey("endAt")) {
            activity.setEndAt(parseDateTime(body.get("endAt")));
        }
        if (body.containsKey("rewardScore")) {
            activity.setRewardScore(boundedInt(body.get("rewardScore"), "rewardScore", MAX_REWARD_AMOUNT));
        }
        if (body.containsKey("rewardGold")) {
            activity.setRewardGold(boundedInt(body.get("rewardGold"), "rewardGold", MAX_REWARD_AMOUNT));
        }
        if (body.containsKey("rewardChatBonus")) {
            activity.setRewardChatBonus(boundedInt(body.get("rewardChatBonus"), "rewardChatBonus", MAX_BONUS_AMOUNT));
        }
        if (body.containsKey("rewardImageBonus")) {
            activity.setRewardImageBonus(boundedInt(body.get("rewardImageBonus"), "rewardImageBonus", MAX_BONUS_AMOUNT));
        }
        if (body.containsKey("streakRules")) {
            activity.setStreakRulesJson(normalizeStreakRulesJson(body.get("streakRules")));
        } else if (body.containsKey("streakRulesJson")) {
            activity.setStreakRulesJson(normalizeStreakRulesJson(body.get("streakRulesJson")));
        }
        if (body.containsKey("timezone")) {
            String tz = stringVal(body.get("timezone"));
            activity.setTimezone(requireZoneId(tz.isBlank() ? "Asia/Shanghai" : tz).getId());
        }
        if (body.containsKey("note")) {
            activity.setNote(stringVal(body.get("note")));
        }
        activity.setAudience("ALL_LOGIN");
        validateActivity(activity);
        if (activityMapper.updateFull(activity) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "\u4fdd\u5b58\u7b7e\u5230\u6d3b\u52a8\u5931\u8d25");
        }
        entitlementService.updateDailyUsageTimezone(activity.getTimezone());
        return toAdminActivityMap(activityMapper.findById(activity.getId()));
    }

    @Transactional(readOnly = true)
    public long countAdminClaims(String keyword, String bizDate) {
        AppCheckinActivity activity = requireActivity();
        return claimMapper.countAdmin(trimToNull(keyword), parseDate(bizDate), activity.getId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminClaims(String keyword, String bizDate, int pageNum, int pageSize) {
        AppCheckinActivity activity = requireActivity();
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return claimMapper.listAdminPage(
                        trimToNull(keyword),
                        parseDate(bizDate),
                        activity.getId(),
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::toAdminClaimMap)
                .toList();
    }

    private Map<String, Object> buildStatusPayload(
            AppCheckinActivity activity,
            LocalDate bizDate,
            boolean claimedToday,
            int streakDay,
            RewardBundle todayReward,
            RewardBundle tomorrowReward,
            AppH5UserProfileExt ext,
            RewardBundle granted,
            boolean justClaimed,
            long userId
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", nvl(activity.getEnabled()) == 1 && isInWindow(activity));
        data.put("activityEnabled", nvl(activity.getEnabled()) == 1);
        data.put("inWindow", isInWindow(activity));
        data.put("claimedToday", claimedToday);
        data.put("bizDate", bizDate.toString());
        data.put("streakDay", streakDay);
        data.put("activityName", blank(activity.getName()));
        data.put("activityCode", blank(activity.getCode()));
        data.put("todayReward", todayReward.toMap());
        data.put("tomorrowReward", tomorrowReward.toMap());
        data.put("weekTrack", buildWeekTrack(activity.getId(), userId, bizDate));
        data.put("profile", toProfileSnapshot(ext));
        if (granted != null) {
            data.put("granted", granted.toMap());
        }
        data.put("justClaimed", justClaimed);
        return data;
    }

    private List<Map<String, Object>> buildWeekTrack(long activityId, long userId, LocalDate bizDate) {
        LocalDate from = bizDate.minusDays(6);
        Set<LocalDate> claimed = userId <= 0
                ? Set.of()
                : claimMapper.listByUserActivityDateRange(userId, activityId, from, bizDate)
                .stream()
                .map(AppCheckinClaim::getBizDate)
                .collect(Collectors.toSet());
        List<Map<String, Object>> track = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = from.plusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("dayIndex", i + 1);
            item.put("dayOfMonth", date.getDayOfMonth());
            item.put("claimed", claimed.contains(date));
            item.put("isToday", date.equals(bizDate));
            track.add(item);
        }
        return track;
    }

    private Map<String, Object> toProfileSnapshot(AppH5UserProfileExt ext) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (ext == null) {
            data.put("score", 0);
            data.put("goldCoin", 0);
            data.put("dailyChatRemaining", 0);
            data.put("dailyImageRemaining", 0);
            data.put("dailyChatBonus", 0);
            data.put("dailyImageBonus", 0);
            return data;
        }
        int chatBonus = nvl(ext.getDailyChatBonus());
        int imageBonus = nvl(ext.getDailyImageBonus());
        data.put("score", nvl(ext.getScore()));
        data.put("goldCoin", nvl(ext.getGoldCoin()));
        data.put("dailyChatBonus", chatBonus);
        data.put("dailyImageBonus", imageBonus);
        data.put("dailyChatQuota", nvl(ext.getDailyChatQuota()));
        data.put("dailyChatUsed", nvl(ext.getDailyChatUsed()));
        data.put("dailyChatRemaining", Math.max(0, nvl(ext.getDailyChatQuota()) + chatBonus - nvl(ext.getDailyChatUsed())));
        data.put("dailyImageQuota", nvl(ext.getDailyImageQuota()));
        data.put("dailyImageUsed", nvl(ext.getDailyImageUsed()));
        data.put("dailyImageRemaining", Math.max(0, nvl(ext.getDailyImageQuota()) + imageBonus - nvl(ext.getDailyImageUsed())));
        return data;
    }

    private int previewStreakDay(long userId, long activityId, LocalDate bizDate) {
        AppCheckinClaim yesterday = claimMapper.findByUserActivityDate(userId, activityId, bizDate.minusDays(1));
        if (yesterday == null) {
            return 1;
        }
        return Math.max(1, nvl(yesterday.getStreakDay()) + 1);
    }

    static int tomorrowStreakPreview(int currentStreak) {
        return Math.max(1, currentStreak + 1);
    }

    static RewardBundle computeReward(AppCheckinActivity activity, int streakDay) {
        int score = Math.max(0, nvl(activity.getRewardScore()));
        int gold = Math.max(0, nvl(activity.getRewardGold()));
        int chat = Math.max(0, nvl(activity.getRewardChatBonus()));
        int image = Math.max(0, nvl(activity.getRewardImageBonus()));
        for (StreakRule rule : parseStreakRules(activity.getStreakRulesJson())) {
            if (rule.day() == streakDay) {
                score += Math.max(0, rule.score());
                gold += Math.max(0, rule.gold());
                chat += Math.max(0, rule.chatBonus());
                image += Math.max(0, rule.imageBonus());
                break;
            }
        }
        return new RewardBundle(score, gold, chat, image);
    }

    static List<StreakRule> parseStreakRules(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> rows = mapper.readValue(json, new TypeReference<>() {});
            List<StreakRule> rules = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                int day = intVal(row.get("day"));
                if (day <= 0) {
                    continue;
                }
                rules.add(new StreakRule(
                        day,
                        Math.max(0, intVal(first(row, "score", "rewardScore"))),
                        Math.max(0, intVal(first(row, "gold", "rewardGold"))),
                        Math.max(0, intVal(first(row, "chatBonus", "rewardChatBonus"))),
                        Math.max(0, intVal(first(row, "imageBonus", "rewardImageBonus")))
                ));
            }
            return rules;
        } catch (Exception ex) {
            return List.of();
        }
    }

    String normalizeStreakRulesJson(Object raw) {
        if (raw == null) {
            return "[]";
        }
        try {
            String asJson = raw instanceof String text ? text : objectMapper.writeValueAsString(raw);
            if (asJson.isBlank()) {
                return "[]";
            }
            List<Map<String, Object>> rows = objectMapper.readValue(asJson, new TypeReference<>() {});
            if (rows.size() > MAX_STREAK_DAY) {
                throw validationError("连续签到规则不能超过 " + MAX_STREAK_DAY + " 条");
            }
            Set<Integer> days = new HashSet<>();
            List<StreakRule> rules = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    throw validationError("连续签到规则不能为空");
                }
                int day = boundedInt(row.get("day"), "连续签到天数", MAX_STREAK_DAY);
                if (day < 1 || !days.add(day)) {
                    throw validationError(day < 1 ? "连续签到天数必须大于 0" : "第 " + day + " 天存在重复规则");
                }
                int score = boundedInt(first(row, "score", "rewardScore"), "额外钻石", MAX_REWARD_AMOUNT);
                int gold = boundedInt(first(row, "gold", "rewardGold"), "额外金币", MAX_REWARD_AMOUNT);
                int chat = boundedInt(first(row, "chatBonus", "rewardChatBonus"), "额外聊天次数", MAX_BONUS_AMOUNT);
                int image = boundedInt(first(row, "imageBonus", "rewardImageBonus"), "额外生图次数", MAX_BONUS_AMOUNT);
                if (score == 0 && gold == 0 && chat == 0 && image == 0) {
                    throw validationError("第 " + day + " 天的额外奖励不能为空");
                }
                rules.add(new StreakRule(day, score, gold, chat, image));
            }
            return objectMapper.writeValueAsString(rules.stream().map(StreakRule::toMap).toList());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw validationError("连续签到规则格式不正确");
        }
    }

    void validateActivity(AppCheckinActivity activity) {
        String name = stringVal(activity.getName());
        if (name.isBlank() || name.length() > 64) {
            throw validationError("活动名称不能为空且不能超过 64 个字符");
        }
        activity.setName(name);
        if (activity.getStartAt() != null && activity.getEndAt() != null
                && !activity.getStartAt().isBefore(activity.getEndAt())) {
            throw validationError("活动开始时间必须早于结束时间");
        }
        requireRange(nvl(activity.getRewardScore()), "rewardScore", MAX_REWARD_AMOUNT);
        requireRange(nvl(activity.getRewardGold()), "rewardGold", MAX_REWARD_AMOUNT);
        requireRange(nvl(activity.getRewardChatBonus()), "rewardChatBonus", MAX_BONUS_AMOUNT);
        requireRange(nvl(activity.getRewardImageBonus()), "rewardImageBonus", MAX_BONUS_AMOUNT);
        if (nvl(activity.getRewardScore()) == 0
                && nvl(activity.getRewardGold()) == 0
                && nvl(activity.getRewardChatBonus()) == 0
                && nvl(activity.getRewardImageBonus()) == 0) {
            throw validationError("每日基础奖励不能全部为 0");
        }
        activity.setStreakRulesJson(normalizeStreakRulesJson(activity.getStreakRulesJson()));
        activity.setTimezone(requireZoneId(activity.getTimezone()).getId());
        String note = stringVal(activity.getNote());
        if (note.length() > 500) {
            throw validationError("备注不能超过 500 个字符");
        }
        activity.setNote(note);
    }

    private AppCheckinActivity requireActivity() {
        AppCheckinActivity activity = activityMapper.findByCode(DEFAULT_CODE);
        if (activity == null || activity.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "\u7b7e\u5230\u6d3b\u52a8\u672a\u914d\u7f6e");
        }
        return activity;
    }

    private void assertActivityOpen(AppCheckinActivity activity) {
        if (nvl(activity.getEnabled()) != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "\u7b7e\u5230\u6d3b\u52a8\u672a\u5f00\u542f");
        }
        if (!isInWindow(activity)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "\u4e0d\u5728\u7b7e\u5230\u6d3b\u52a8\u65f6\u95f4\u7a97\u5185");
        }
    }

    private static boolean isInWindow(AppCheckinActivity activity) {
        LocalDateTime now = LocalDateTime.now(zoneId(activity));
        if (activity.getStartAt() != null && now.isBefore(activity.getStartAt())) {
            return false;
        }
        if (activity.getEndAt() != null && now.isAfter(activity.getEndAt())) {
            return false;
        }
        return true;
    }

    private static LocalDate bizDate(AppCheckinActivity activity) {
        return LocalDate.now(zoneId(activity));
    }

    private static ZoneId zoneId(AppCheckinActivity activity) {
        String tz = activity.getTimezone() == null || activity.getTimezone().isBlank()
                ? "Asia/Shanghai"
                : activity.getTimezone().trim();
        try {
            return ZoneId.of(tz);
        } catch (Exception ex) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    /**
     * Reuse existing isolation chain only ?? do not alter auth services.
     */
    private AppUser resolveUser(String clientUid) {
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        return tokenService.validateAndLoadUser(token);
    }

    private Map<String, Object> toAdminActivityMap(AppCheckinActivity activity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", activity.getId());
        data.put("code", blank(activity.getCode()));
        data.put("name", blank(activity.getName()));
        data.put("enabled", nvl(activity.getEnabled()) == 1);
        data.put("startAt", activity.getStartAt());
        data.put("endAt", activity.getEndAt());
        data.put("audience", blank(activity.getAudience()));
        data.put("rewardScore", nvl(activity.getRewardScore()));
        data.put("rewardGold", nvl(activity.getRewardGold()));
        data.put("rewardChatBonus", nvl(activity.getRewardChatBonus()));
        data.put("rewardImageBonus", nvl(activity.getRewardImageBonus()));
        data.put("streakRules", parseStreakRules(activity.getStreakRulesJson()).stream().map(StreakRule::toMap).toList());
        data.put("streakRulesJson", blank(activity.getStreakRulesJson()));
        data.put("timezone", blank(activity.getTimezone()));
        data.put("note", blank(activity.getNote()));
        data.put("updatedAt", activity.getUpdatedAt());
        return data;
    }

    private Map<String, Object> toAdminClaimMap(AppCheckinClaim claim) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", claim.getId());
        data.put("userId", claim.getUserId());
        data.put("activityId", claim.getActivityId());
        data.put("bizDate", claim.getBizDate() == null ? "" : claim.getBizDate().toString());
        data.put("streakDay", nvl(claim.getStreakDay()));
        data.put("rewardScore", nvl(claim.getRewardScore()));
        data.put("rewardGold", nvl(claim.getRewardGold()));
        data.put("rewardChatBonus", nvl(claim.getRewardChatBonus()));
        data.put("rewardImageBonus", nvl(claim.getRewardImageBonus()));
        data.put("ledgerIdempotencyKey", blank(claim.getLedgerIdempotencyKey()));
        data.put("createdAt", claim.getCreatedAt());
        return data;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static Object first(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private static LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay();
            }
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (Exception ex) {
            throw validationError("活动时间格式不正确");
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private static int boolInt(Object value) {
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0 ? 1 : 0;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) ? 1 : 0;
    }

    private static int intVal(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static int boundedInt(Object value, String field, int max) {
        if (value == null) {
            return 0;
        }
        String text = String.valueOf(value).trim();
        if (!text.matches("[0-9]+")) {
            throw validationError(field + " 必须是非负整数");
        }
        try {
            long parsed = Long.parseLong(text);
            if (parsed > max) {
                throw validationError(field + " 不能超过 " + max);
            }
            return (int) parsed;
        } catch (NumberFormatException ex) {
            throw validationError(field + " 数值过大");
        }
    }

    private static void requireRange(int value, String field, int max) {
        if (value < 0 || value > max) {
            throw validationError(field + " 必须在 0 到 " + max + " 之间");
        }
    }

    private static ZoneId requireZoneId(String timezone) {
        String value = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
        try {
            return ZoneId.of(value);
        } catch (Exception ex) {
            throw validationError("时区无效，请填写 IANA 时区，例如 Asia/Shanghai");
        }
    }

    private static BusinessException validationError(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static long nvlLong(Long value) {
        return value == null ? 0L : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record StreakRule(int day, int score, int gold, int chatBonus, int imageBonus) {
        Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("day", day);
            data.put("score", score);
            data.put("gold", gold);
            data.put("chatBonus", chatBonus);
            data.put("imageBonus", imageBonus);
            return data;
        }
    }

    record RewardBundle(int score, int gold, int chatBonus, int imageBonus) {
        boolean isEmpty() {
            return score <= 0 && gold <= 0 && chatBonus <= 0 && imageBonus <= 0;
        }

        Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("score", score);
            data.put("gold", gold);
            data.put("chatBonus", chatBonus);
            data.put("imageBonus", imageBonus);
            return data;
        }

        static RewardBundle fromClaim(AppCheckinClaim claim) {
            return new RewardBundle(
                    nvl(claim.getRewardScore()),
                    nvl(claim.getRewardGold()),
                    nvl(claim.getRewardChatBonus()),
                    nvl(claim.getRewardImageBonus())
            );
        }
    }
}
