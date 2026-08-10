package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminH5UserMapper;
import com.example.sillyspringboot.admin.service.AdminH5UserLifecycleService;
import com.example.sillyspringboot.admin.service.AdminH5UserSecurityService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ProfileMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJiugaiH5UserControllerConcurrencyTest {

    @Mock AdminH5UserMapper adminH5UserMapper;
    @Mock AppH5UserProfileExtMapper profileExtMapper;
    @Mock AppH5ProfileMapper profileMapper;
    @Mock EntitlementPolicyService entitlementPolicyService;
    @Mock AppFeatureSettingsService featureSettingsService;
    @Mock EntitlementAuditLogService auditLogService;
    @Mock AdminH5UserLifecycleService userLifecycleService;
    @Mock AdminH5UserSecurityService userSecurityService;

    private AdminJiugaiH5UserController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminJiugaiH5UserController(
                adminH5UserMapper,
                profileExtMapper,
                profileMapper,
                entitlementPolicyService,
                featureSettingsService,
                auditLogService,
                userLifecycleService,
                userSecurityService
        );
    }

    @Test
    void detailIncludesExpectedFinancialSnapshot() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", 7L);
        detail.put("score", 120);
        detail.put("goldCoin", 340);
        detail.put("vipType", 2);
        detail.put("vipExpiresAt", "2030-01-02 03:04:05");
        when(adminH5UserMapper.findDetail(7L)).thenReturn(detail);
        when(adminH5UserMapper.listRecentConversationsByUser(7L, 20)).thenReturn(List.of());

        Map<String, Object> result = controller.get(7L);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data).containsEntry("expectedScore", 120)
                .containsEntry("expectedGoldCoin", 340)
                .containsEntry("expectedVipType", 2)
                .containsEntry("expectedVipExpiresAt", "2030-01-02 03:04:05");
    }

    @Test
    void updateRejectsStaleWalletSnapshotBeforeAnyWrite() {
        AppH5UserProfileExt current = profile(7L, 130, 340, 1, LocalDateTime.of(2030, 1, 2, 3, 4, 5));
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(current);

        Map<String, Object> result = controller.update(updateBody(
                120, 340, 1, "2030-01-02 03:04:05"
        ));

        assertThat(result.get("code")).isNotEqualTo(200);
        assertThat(result.get("msg")).asString().contains("钱包或会员权益已发生变化");
        verify(profileExtMapper, never()).upsert(any());
        verify(profileExtMapper, never()).setWalletBalances(anyLong(), anyInt(), anyInt());
        verify(profileExtMapper, never()).setMembership(anyLong(), anyInt(), any());
        verify(profileMapper, never()).upsert(anyLong(), any(), any(), any());
    }

    @Test
    void updateRejectsStaleMembershipSnapshotBeforeAnyWrite() {
        AppH5UserProfileExt current = profile(7L, 120, 340, 1, LocalDateTime.of(2030, 2, 2, 3, 4, 5));
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(current);

        Map<String, Object> result = controller.update(updateBody(
                120, 340, 1, "2030-01-02 03:04:05"
        ));

        assertThat(result.get("msg")).asString().contains("钱包或会员权益已发生变化");
        verify(profileExtMapper, never()).upsert(any());
        verify(profileExtMapper, never()).setWalletBalances(anyLong(), anyInt(), anyInt());
        verify(profileExtMapper, never()).setMembership(anyLong(), anyInt(), any());
    }

    @Test
    void updateRejectsMissingSnapshotBeforeLockingOrWriting() {
        Map<String, Object> body = updateBody(120, 340, 1, "2030-01-02 03:04:05");
        body.remove("expectedScore");

        Map<String, Object> result = controller.update(body);

        assertThat(result.get("msg")).asString().contains("并发校验快照");
        verify(profileExtMapper, never()).findByUserIdForUpdate(anyLong());
        verify(profileExtMapper, never()).upsert(any());
        verify(profileExtMapper, never()).setWalletBalances(anyLong(), anyInt(), anyInt());
    }

    @Test
    void updateAcceptsSameMembershipTimestampAfterSecondPrecisionNormalization() {
        LocalDateTime currentExpiry = LocalDateTime.of(2030, 1, 2, 3, 4, 5, 987_654_321);
        AppH5UserProfileExt current = profile(7L, 120, 340, 1, currentExpiry);
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(current);
        when(adminH5UserMapper.findDetail(7L)).thenReturn(detail("normal"), detail("normal"));

        Map<String, Object> body = updateBody(120, 340, 1, "2030-01-02 03:04:05");
        body.put("score", 150);
        body.put("goldCoin", 360);
        body.put("vipExpiresAt", "2031-01-02 03:04:05");
        Map<String, Object> result = controller.update(body);

        assertThat(result.get("code")).isEqualTo(200);
        verify(profileExtMapper).setWalletBalances(7L, 150, 360);
        verify(profileExtMapper).setMembership(7L, 1, LocalDateTime.of(2031, 1, 2, 3, 4, 5));
    }

    @Test
    void updateInitializesMissingExtensionOnlyForDefaultSnapshot() {
        AppH5UserProfileExt initialized = profile(7L, 0, 0, 0, null);
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(null, initialized);
        when(profileExtMapper.insertDefaultIfAbsent(7L)).thenReturn(1);
        when(adminH5UserMapper.findDetail(7L)).thenReturn(detail("normal"), detail("normal"));

        Map<String, Object> body = updateBody(0, 0, 0, "");
        body.put("vipType", 0);
        body.put("vipExpiresAt", "");
        Map<String, Object> result = controller.update(body);

        assertThat(result.get("code")).isEqualTo(200);
        verify(profileExtMapper).insertDefaultIfAbsent(7L);
        verify(profileExtMapper).setWalletBalances(7L, 120, 340);
    }

    @Test
    void updateDoesNotInitializeMissingExtensionForNonDefaultSnapshot() {
        when(profileExtMapper.findByUserIdForUpdate(7L)).thenReturn(null);

        Map<String, Object> result = controller.update(updateBody(
                1, 0, 0, ""
        ));

        assertThat(result.get("msg")).asString().contains("钱包或会员权益已发生变化");
        verify(profileExtMapper, never()).insertDefaultIfAbsent(anyLong());
        verify(profileExtMapper, never()).upsert(any());
        verify(profileExtMapper, never()).setWalletBalances(anyLong(), anyInt(), anyInt());
    }

    private static Map<String, Object> updateBody(
            int expectedScore,
            int expectedGoldCoin,
            int expectedVipType,
            String expectedVipExpiresAt
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", 7L);
        body.put("expectedScore", expectedScore);
        body.put("expectedGoldCoin", expectedGoldCoin);
        body.put("expectedVipType", expectedVipType);
        body.put("expectedVipExpiresAt", expectedVipExpiresAt);
        body.put("nickname", "updated user");
        body.put("score", 120);
        body.put("goldCoin", 340);
        body.put("vipType", 1);
        body.put("vipExpiresAt", "2030-01-02 03:04:05");
        body.put("status", "normal");
        return body;
    }

    private static AppH5UserProfileExt profile(
            long userId,
            int score,
            int goldCoin,
            int vipType,
            LocalDateTime vipExpiresAt
    ) {
        AppH5UserProfileExt ext = new AppH5UserProfileExt();
        ext.setUserId(userId);
        ext.setScore(score);
        ext.setGoldCoin(goldCoin);
        ext.setVipType(vipType);
        ext.setVipExpiresAt(vipExpiresAt);
        return ext;
    }

    private static Map<String, Object> detail(String status) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", 7L);
        detail.put("status", status);
        return detail;
    }
}
