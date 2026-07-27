package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.admin.service.CharacterContentScreeningService;
import com.example.sillyspringboot.admin.service.CharacterReviewAuditLogService;
import com.example.sillyspringboot.admin.service.ExternalCleanupTaskService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.service.CharacterStudioService;
import com.example.sillyspringboot.character.service.EmbeddedLorebookSyncService;
import com.example.sillyspringboot.compat.h5.entity.H5MyCharacter;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.compat.h5.service.H5TavernSessionService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiV1MyCharactersImportIsolationTest {

    @Test
    void databaseFailureSchedulesCleanupForTheNewPrivateStFile() {
        Fixture fixture = fixture();
        when(fixture.stAdapter.importCharacterPng(any(), eq("card.png"), any()))
                .thenAnswer(invocation -> Map.of(
                        "file_name",
                        ((StCharacterImportRequest) invocation.getArgument(2)).preservedName() + ".png"
                ));
        StCharacterDetail detail = mock(StCharacterDetail.class);
        when(detail.name()).thenReturn("Imported card");
        when(fixture.stAdapter.getCharacter(any())).thenReturn(detail);
        doThrow(new IllegalStateException("db failed"))
                .when(fixture.mineMapper).insertMine(any(H5MyCharacter.class));
        when(fixture.cleanupTaskService.enqueueArtifactRollbackTasks(anyLong(), any(), eq(Set.of())))
                .thenReturn(List.of("cleanup-1"));

        assertThatThrownBy(() -> fixture.controller.importMinePng(png(), "client-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("db failed");

        ArgumentCaptor<String> stFile = ArgumentCaptor.forClass(String.class);
        verify(fixture.cleanupTaskService).enqueueArtifactRollbackTasks(eq(7L), stFile.capture(), eq(Set.of()));
        assertThat(stFile.getValue()).startsWith("h5_u7_").endsWith(".png");
        verify(fixture.cleanupTaskService).processImmediately(List.of("cleanup-1"));
    }

    @Test
    void foreignStFileResultIsRejectedWithoutDeletingUnknownFile() {
        Fixture fixture = fixture();
        when(fixture.stAdapter.importCharacterPng(any(), eq("card.png"), any()))
                .thenReturn(Map.of("file_name", "system-role.png"));

        assertThatThrownBy(() -> fixture.controller.importMinePng(png(), "client-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于本次导入");

        verify(fixture.mineMapper, never()).insertMine(any());
        verify(fixture.cleanupTaskService, never()).enqueueArtifactRollbackTasks(anyLong(), any(), any());
        verify(fixture.stAdapter, never()).deleteCharacter(any(), eq(true));
    }

    private Fixture fixture() {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        H5MyCharacterMapper mineMapper = mock(H5MyCharacterMapper.class);
        StAdapter stAdapter = mock(StAdapter.class);
        ExternalCleanupTaskService cleanupTaskService = mock(ExternalCleanupTaskService.class);
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        AppUser user = new AppUser();
        user.setId(7L);
        when(tokenService.validateAndLoadUser("token-1")).thenReturn(user);

        ApiV1MyCharactersController controller = new ApiV1MyCharactersController(
                h5Auth,
                tokenService,
                mineMapper,
                mock(H5UploadService.class),
                mock(H5StAssetUrls.class),
                stAdapter,
                mock(AppCharacterMapper.class),
                mock(CharacterContentScreeningService.class),
                mock(CharacterReviewAuditLogService.class),
                mock(AppFeatureSettingsService.class),
                mock(H5VisitorTrialGuardService.class),
                mock(H5EntitlementService.class),
                mock(H5TavernSessionService.class),
                mock(EmbeddedLorebookSyncService.class),
                mock(CharacterStudioService.class),
                cleanupTaskService
        );
        return new Fixture(controller, mineMapper, stAdapter, cleanupTaskService);
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "card.png", "image/png", new byte[]{1, 2, 3});
    }

    private record Fixture(
            ApiV1MyCharactersController controller,
            H5MyCharacterMapper mineMapper,
            StAdapter stAdapter,
            ExternalCleanupTaskService cleanupTaskService
    ) {
    }
}
