package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class H5ExpiredVipQuotaSyncService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(H5ExpiredVipQuotaSyncService.class);

    private final AppH5UserProfileExtMapper profileExtMapper;
    private final EntitlementPolicyService entitlementPolicyService;

    public H5ExpiredVipQuotaSyncService(
            AppH5UserProfileExtMapper profileExtMapper,
            EntitlementPolicyService entitlementPolicyService
    ) {
        this.profileExtMapper = profileExtMapper;
        this.entitlementPolicyService = entitlementPolicyService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        int changed = profileExtMapper.syncExpiredVipEffectiveQuotas(
                Math.max(0, policy.getGuestDailyChatQuota()),
                Math.max(0, policy.getGuestDailyImageQuota())
        );
        if (changed > 0) {
            log.info("synced expired h5 vip quota overrides, affected={}", changed);
        }
    }
}
