package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class EntitlementPolicyServiceTest {

    @Test
    void refreshEffectiveQuotaClearsExpiredVipOverrides() {
        EntitlementPolicyService service = new EntitlementPolicyService(mock(AppRuntimeSettingMapper.class));
        AppH5UserProfileExt ext = new AppH5UserProfileExt();
        ext.setVipType(1);
        ext.setVipExpiresAt(LocalDateTime.now().minusDays(1));
        ext.setChatQuotaOverride(300);
        ext.setImageQuotaOverride(5);
        ext.setDailyChatQuota(300);
        ext.setDailyImageQuota(5);

        service.refreshEffectiveQuota(ext);

        assertNull(ext.getChatQuotaOverride());
        assertNull(ext.getImageQuotaOverride());
        assertEquals(100, ext.getDailyChatQuota());
        assertEquals(0, ext.getDailyImageQuota());
    }
}
