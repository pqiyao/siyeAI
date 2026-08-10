package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementPolicyBillingFieldsTest {

    @Test
    void defaultsIncludeOverQuotaCosts() {
        EntitlementPolicy policy = new EntitlementPolicy();
        assertThat(policy.isOverQuotaBillingEnabled()).isTrue();
        assertThat(policy.getChatScoreCost()).isEqualTo(1);
        assertThat(policy.getChatGoldCost()).isZero();
        assertThat(policy.getChatWalletMode()).isEqualTo("DIAMOND_AND_GOLD");
        assertThat(policy.getImageScoreCost()).isEqualTo(5);
        assertThat(policy.getTtsScoreCost()).isEqualTo(2);
        assertThat(policy.getSttScoreCost()).isZero();
    }

    @Test
    void toMapExposesBillingFields() {
        EntitlementPolicyService service = new EntitlementPolicyService(null);
        EntitlementPolicy policy = new EntitlementPolicy();
        policy.setChatScoreCost(3);
        policy.setChatWalletMode("DIAMOND_OR_GOLD");
        policy.setImageGoldCost(1);
        Map<String, Object> map = service.toMap(policy);
        assertThat(map).containsEntry("overQuotaBillingEnabled", true);
        assertThat(map).containsEntry("chatScoreCost", 3);
        assertThat(map).containsEntry("chatWalletMode", "DIAMOND_OR_GOLD");
        assertThat(map).containsEntry("imageGoldCost", 1);
        assertThat(map).containsEntry("ttsScoreCost", 2);
        assertThat(map).containsEntry("sttScoreCost", 0);
    }
}
