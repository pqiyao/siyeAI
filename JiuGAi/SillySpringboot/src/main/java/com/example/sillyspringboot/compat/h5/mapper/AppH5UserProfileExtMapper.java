package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppH5UserProfileExtMapper {

    AppH5UserProfileExt findByUserId(long userId);

    AppH5UserProfileExt findByUserIdForUpdate(long userId);

    int insertDefaultIfAbsent(long userId);

    /**
     * Creates the extension row when absent and updates non-financial profile/usage fields otherwise.
     * Existing wallet and membership columns are intentionally preserved; those domains have dedicated writes below.
     */
    void upsert(AppH5UserProfileExt row);

    int setWalletBalances(
            @Param("userId") long userId,
            @Param("score") int score,
            @Param("goldCoin") int goldCoin
    );

    int setMembership(
            @Param("userId") long userId,
            @Param("vipType") int vipType,
            @Param("vipExpiresAt") LocalDateTime vipExpiresAt
    );

    int updateMembershipAndEffectiveQuotas(
            @Param("userId") long userId,
            @Param("vipType") int vipType,
            @Param("vipExpiresAt") LocalDateTime vipExpiresAt,
            @Param("dailyChatQuota") int dailyChatQuota,
            @Param("chatQuotaOverride") Integer chatQuotaOverride,
            @Param("dailyImageQuota") int dailyImageQuota,
            @Param("imageQuotaOverride") Integer imageQuotaOverride
    );

    int upsertCharacterCreateAllowedForUsers(@Param("userIds") List<Long> userIds, @Param("allowed") int allowed);

    int syncExpiredVipEffectiveQuotas(
            @Param("guestChatQuota") int guestChatQuota,
            @Param("guestImageQuota") int guestImageQuota
    );

    /**
     * 原子扣减钱包余额。仅当 score/gold 均足够时更新成功，返回影响行数。
     */
    int deductWallet(
            @Param("userId") long userId,
            @Param("scoreCost") int scoreCost,
            @Param("goldCost") int goldCost
    );

    /**
     * 钱包回补（并发幂等冲突时退回本次误扣）。
     */
    int creditWallet(
            @Param("userId") long userId,
            @Param("scoreAmount") int scoreAmount,
            @Param("goldAmount") int goldAmount
    );

    /** Atomically add same-day check-in quota bonuses. */
    int addDailyBonus(
            @Param("userId") long userId,
            @Param("chatBonus") int chatBonus,
            @Param("imageBonus") int imageBonus
    );
}
