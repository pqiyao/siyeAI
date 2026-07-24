package com.example.sillyspringboot.compat.h5.mapper;

import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppH5UserProfileExtMapperConsistencyTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppH5UserProfileExtMapper profileExtMapper;

    @Test
    void staleProfileUpsertCannotRestorePreviouslyDeductedWalletBalance() {
        long userId = insertUser("wallet-stale");
        insertProfile(userId, 100, 200, 0, null);
        AppH5UserProfileExt stale = profileExtMapper.findByUserIdForUpdate(userId);
        assertThat(stale).isNotNull();

        assertThat(profileExtMapper.deductWallet(userId, 10, 20)).isEqualTo(1);
        stale.setNickname("updated profile");
        stale.setDailyChatUsed(3);
        profileExtMapper.upsert(stale);

        AppH5UserProfileExt saved = profileExtMapper.findByUserId(userId);
        assertThat(saved.getScore()).isEqualTo(90);
        assertThat(saved.getGoldCoin()).isEqualTo(180);
        assertThat(saved.getNickname()).isEqualTo("updated profile");
        assertThat(saved.getDailyChatUsed()).isEqualTo(3);

        profileExtMapper.setWalletBalances(userId, 55, 66);
        saved = profileExtMapper.findByUserId(userId);
        assertThat(saved.getScore()).isEqualTo(55);
        assertThat(saved.getGoldCoin()).isEqualTo(66);
    }

    @Test
    void insertsDefaultExtensionForExistingUserAndLocksIt() {
        long userId = insertUser("default-extension");

        assertThat(profileExtMapper.insertDefaultIfAbsent(userId)).isEqualTo(1);
        AppH5UserProfileExt saved = profileExtMapper.findByUserIdForUpdate(userId);

        assertThat(saved).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getScore()).isZero();
        assertThat(saved.getGoldCoin()).isZero();
        assertThat(saved.getVipType()).isZero();
        assertThat(saved.getVipExpiresAt()).isNull();
    }

    @Test
    void staleProfileUpsertCannotOverwriteMembershipAppliedByPayment() {
        long userId = insertUser("membership-stale");
        LocalDateTime originalExpiry = LocalDateTime.of(2030, 1, 1, 0, 0);
        LocalDateTime paidExpiry = LocalDateTime.of(2030, 2, 1, 0, 0);
        insertProfile(userId, 0, 0, 1, originalExpiry);
        AppH5UserProfileExt stale = profileExtMapper.findByUserId(userId);

        assertThat(profileExtMapper.updateMembershipAndEffectiveQuotas(
                userId, 2, paidExpiry, 100, null, 20, null
        )).isEqualTo(1);
        stale.setNickname("profile after payment");
        profileExtMapper.upsert(stale);

        AppH5UserProfileExt saved = profileExtMapper.findByUserId(userId);
        assertThat(saved.getVipType()).isEqualTo(2);
        assertThat(saved.getVipExpiresAt()).isEqualTo(paidExpiry);
        assertThat(saved.getNickname()).isEqualTo("profile after payment");

        LocalDateTime adminExpiry = LocalDateTime.of(2031, 1, 1, 0, 0);
        profileExtMapper.setMembership(userId, 1, adminExpiry);
        saved = profileExtMapper.findByUserId(userId);
        assertThat(saved.getVipType()).isEqualTo(1);
        assertThat(saved.getVipExpiresAt()).isEqualTo(adminExpiry);
    }

    private long insertUser(String prefix) {
        long telegramUserId = Math.abs(System.nanoTime());
        jdbc.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramUserId,
                prefix + telegramUserId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramUserId
        );
    }

    private void insertProfile(
            long userId,
            int score,
            int goldCoin,
            int vipType,
            LocalDateTime vipExpiresAt
    ) {
        jdbc.update("""
                INSERT INTO app_h5_user_profile_ext (
                    user_id, nickname, vip_type, vip_expires_at, score, gold_coin
                ) VALUES (?, 'initial profile', ?, ?, ?, ?)
                """, userId, vipType, vipExpiresAt, score, goldCoin);
    }
}
