package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.CharacterCleanupMapper;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.example.sillyspringboot.character.service.CharacterPublicProfileService;
import com.example.sillyspringboot.character.service.EmbeddedLorebookSyncService;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.service.AppUserMessageService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.ops.service.TagLibraryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminJiugaiCharacterRemovalTest {

    @Test
    void externalFailureAfterTaskRegistrationCannotUndoSoftDelete() {
        AppCharacterMapper characterMapper = mock(AppCharacterMapper.class);
        AppLorebookEntryMapper lorebookMapper = mock(AppLorebookEntryMapper.class);
        AppH5ClientUidMapper clientUidMapper = mock(AppH5ClientUidMapper.class);
        AdminUserDisplayService userDisplayService = mock(AdminUserDisplayService.class);
        TagLibraryService tagLibraryService = mock(TagLibraryService.class);
        AppUserMessageService userMessageService = mock(AppUserMessageService.class);
        CharacterReviewAuditLogService reviewAuditLogService = mock(CharacterReviewAuditLogService.class);
        StWorldbookCatalogService worldbookCatalogService = mock(StWorldbookCatalogService.class);
        StAdapter stAdapter = mock(StAdapter.class);
        EmbeddedLorebookSyncService lorebookSyncService = mock(EmbeddedLorebookSyncService.class);
        CharacterPublicProfileService publicProfileService = mock(CharacterPublicProfileService.class);
        CharacterCleanupMapper cleanupMapper = mock(CharacterCleanupMapper.class);
        ExternalCleanupTaskService cleanupTaskService = mock(ExternalCleanupTaskService.class);

        AppCharacter character = new AppCharacter();
        character.setId(101L);
        character.setStAvatarUrl("alice.png");
        when(cleanupMapper.lockCharacters(List.of(101L))).thenReturn(List.of(character));
        when(cleanupMapper.listMemberMedia(List.of(101L))).thenReturn(List.of());
        when(cleanupTaskService.enqueueCharacterStBundle(
                anyString(),
                any(Long.class),
                any(),
                anyString(),
                anyList()
        )).thenReturn("cleanup-task-1");
        when(cleanupTaskService.processImmediately(List.of("cleanup-task-1")))
                .thenThrow(new IllegalStateException("cleanup temporarily unavailable"));

        AdminJiugaiCharacterService service = new AdminJiugaiCharacterService(
                characterMapper,
                lorebookMapper,
                clientUidMapper,
                userDisplayService,
                tagLibraryService,
                userMessageService,
                reviewAuditLogService,
                worldbookCatalogService,
                stAdapter,
                lorebookSyncService,
                publicProfileService,
                cleanupMapper,
                cleanupTaskService
        );

        AdminJiugaiCharacterService.RemoveSummary result = service.removeIds("101", true);

        assertThat(result.localDeleted()).isEqualTo(1);
        assertThat(result.cleanupScheduled()).isEqualTo(1);
        verify(characterMapper).softDeleteById(101L);
        verify(stAdapter, never()).deleteCharacter(anyString(), any(Boolean.class));
    }
}
