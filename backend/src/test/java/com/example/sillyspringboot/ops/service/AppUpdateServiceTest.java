package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.entity.AppAndroidRelease;
import com.example.sillyspringboot.ops.mapper.AppAndroidReleaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppUpdateServiceTest {
    private AppAndroidReleaseMapper mapper;
    private AppUpdateService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AppAndroidReleaseMapper.class);
        service = new AppUpdateService(mapper);
    }

    @Test
    void returnsNoUpdateWhenPublishedVersionIsNotNewer() {
        when(mapper.findEffective("__UNI__200F612", "com.example.app", "official"))
                .thenReturn(release(101, "NORMAL", 0, "https://download.example/app.apk"));

        Map<String, Object> result = service.checkAndroid("__UNI__200F612", "com.example.app", "official", 101);

        assertThat(result).containsEntry("hasUpdate", false).hasSize(1);
    }

    @Test
    void returnsNormalUpdateWithBoundedReminder() {
        AppAndroidRelease release = release(102, "NORMAL", 90, "https://download.example/app.apk");
        release.setRemindLaterHours(999);
        when(mapper.findEffective("app", "com.example.app", "official")).thenReturn(release);

        Map<String, Object> result = service.checkAndroid("app", "com.example.app", "official", 101);

        assertThat(result)
                .containsEntry("hasUpdate", true)
                .containsEntry("force", false)
                .containsEntry("versionCode", 102)
                .containsEntry("remindLaterHours", 168);
    }

    @Test
    void forcesUpdateWhenCurrentVersionIsBelowMinimum() {
        when(mapper.findEffective("app", "com.example.app", "official"))
                .thenReturn(release(105, "NORMAL", 103, "https://download.example/app.apk"));

        Map<String, Object> result = service.checkAndroid("app", "com.example.app", "official", 101);

        assertThat(result).containsEntry("force", true).containsEntry("updateMode", "FORCE");
    }

    @Test
    void forcesUpdateWhenReleaseModeIsForce() {
        when(mapper.findEffective("app", "com.example.app", "official"))
                .thenReturn(release(102, "FORCE", 0, "https://download.example/app.apk"));

        assertThat(service.checkAndroid("app", "com.example.app", "official", 101))
                .containsEntry("force", true);
    }

    @Test
    void fallsBackToOfficialChannel() {
        AppAndroidRelease official = release(103, "NORMAL", 0, "https://download.example/app.apk");
        when(mapper.findEffective("app", "com.example.app", "beta")).thenReturn(null);
        when(mapper.findEffective("app", "com.example.app", "official")).thenReturn(official);

        Map<String, Object> result = service.checkAndroid("app", "com.example.app", "beta", 101);

        assertThat(result).containsEntry("hasUpdate", true).containsEntry("versionCode", 103);
        InOrder order = inOrder(mapper);
        order.verify(mapper).findEffective("app", "com.example.app", "beta");
        order.verify(mapper).findEffective("app", "com.example.app", "official");
    }

    @Test
    void rejectsNonHttpsDownloadUrl() {
        when(mapper.findEffective("app", "com.example.app", "official"))
                .thenReturn(release(102, "NORMAL", 0, "http://download.example/app.apk"));

        assertThat(service.checkAndroid("app", "com.example.app", "official", 101))
                .containsEntry("hasUpdate", false);
    }

    @Test
    void rejectsMissingPackageWithoutQueryingDatabase() {
        assertThat(service.checkAndroid("app", "", "official", 101)).containsEntry("hasUpdate", false);
        verify(mapper, org.mockito.Mockito.never()).findEffective(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private static AppAndroidRelease release(int versionCode, String mode, int minSupported, String url) {
        AppAndroidRelease release = new AppAndroidRelease();
        release.setVersionName("1.3.7");
        release.setVersionCode(versionCode);
        release.setUpdateMode(mode);
        release.setMinSupportedVersionCode(minSupported);
        release.setPolicyRevision(1);
        release.setTitle("发现新版本");
        release.setChangelog("修复问题");
        release.setDownloadUrl(url);
        release.setRemindLaterHours(6);
        release.setChannelCode("official");
        return release;
    }
}
