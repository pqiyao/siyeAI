package com.example.sillyspringboot.ops.checkin.service;

import com.example.sillyspringboot.ops.checkin.entity.AppCheckinActivity;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckinRewardMathTest {

    @Test
    void mergesExactStreakDayExtras() {
        AppCheckinActivity activity = new AppCheckinActivity();
        activity.setRewardScore(10);
        activity.setRewardGold(0);
        activity.setRewardChatBonus(2);
        activity.setRewardImageBonus(0);
        activity.setStreakRulesJson(
                "[{\"day\":3,\"score\":5,\"gold\":0,\"chatBonus\":0,\"imageBonus\":0},"
                        + "{\"day\":7,\"score\":20,\"gold\":0,\"chatBonus\":1,\"imageBonus\":0}]"
        );

        CheckinService.RewardBundle day1 = CheckinService.computeReward(activity, 1);
        assertThat(day1.score()).isEqualTo(10);
        assertThat(day1.chatBonus()).isEqualTo(2);

        CheckinService.RewardBundle day3 = CheckinService.computeReward(activity, 3);
        assertThat(day3.score()).isEqualTo(15);
        assertThat(day3.chatBonus()).isEqualTo(2);

        CheckinService.RewardBundle day7 = CheckinService.computeReward(activity, 7);
        assertThat(day7.score()).isEqualTo(30);
        assertThat(day7.chatBonus()).isEqualTo(3);
    }

    @Test
    void tomorrowPreviewAlwaysUsesNextStreakDay() {
        assertThat(CheckinService.tomorrowStreakPreview(1)).isEqualTo(2);
        assertThat(CheckinService.tomorrowStreakPreview(6)).isEqualTo(7);
    }

    @Test
    void duplicateLegacyRulesCannotStackRewards() {
        AppCheckinActivity activity = new AppCheckinActivity();
        activity.setRewardScore(10);
        activity.setRewardGold(0);
        activity.setRewardChatBonus(0);
        activity.setRewardImageBonus(0);
        activity.setStreakRulesJson(
                "[{\"day\":3,\"score\":5},{\"day\":3,\"score\":500}]"
        );

        CheckinService.RewardBundle reward = CheckinService.computeReward(activity, 3);
        assertThat(reward.score()).isEqualTo(15);
    }

    @Test
    void rejectsDuplicateStreakRules() {
        CheckinService service = serviceForValidation();

        assertThatThrownBy(() -> service.normalizeStreakRulesJson(
                "[{\"day\":3,\"score\":5},{\"day\":3,\"score\":6}]"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复规则");
    }

    @Test
    void rejectsEmptyBaseRewardAndInvalidTimezone() {
        CheckinService service = serviceForValidation();
        AppCheckinActivity activity = baseActivity();
        activity.setRewardScore(0);

        assertThatThrownBy(() -> service.validateActivity(activity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能全部为 0");

        activity.setRewardScore(10);
        activity.setTimezone("Invalid/Timezone");
        assertThatThrownBy(() -> service.validateActivity(activity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("时区无效");
    }

    private static CheckinService serviceForValidation() {
        return new CheckinService(null, null, null, null, null, null, null, new ObjectMapper());
    }

    private static AppCheckinActivity baseActivity() {
        AppCheckinActivity activity = new AppCheckinActivity();
        activity.setName("每日签到");
        activity.setRewardScore(10);
        activity.setRewardGold(0);
        activity.setRewardChatBonus(0);
        activity.setRewardImageBonus(0);
        activity.setStreakRulesJson("[]");
        activity.setTimezone("Asia/Shanghai");
        activity.setNote("");
        return activity;
    }
}
