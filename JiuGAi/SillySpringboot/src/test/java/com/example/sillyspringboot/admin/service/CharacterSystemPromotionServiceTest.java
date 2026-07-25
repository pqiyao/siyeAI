package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.entity.AppCharacterSystemPromotion;
import com.example.sillyspringboot.admin.mapper.AppCharacterSystemPromotionMapper;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.entity.AppCharacterOpening;
import com.example.sillyspringboot.character.entity.AppCharacterOpeningSegment;
import com.example.sillyspringboot.character.entity.AppLorebookEntry;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterSystemPromotionServiceTest {

    @Test
    void disabledSwitchRejectsBeforeReadingCharacterOrSt() {
        Fixture fixture = fixture(false);

        assertThatThrownBy(() -> fixture.service.promote(1L, true, "operator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("开关");

        verify(fixture.characterMapper, never()).findById(1L);
        verify(fixture.stAdapter, never()).exportCharacterPng(anyString());
    }

    @Test
    void duplicateSourceIsRejectedWithoutCreatingAnotherStFile() {
        Fixture fixture = fixture(true);
        AppCharacter source = sourceCharacter();
        AppCharacterSystemPromotion existing = new AppCharacterSystemPromotion();
        existing.setTargetCharacterId(88L);
        when(fixture.characterMapper.findById(1L)).thenReturn(source);
        when(fixture.promotionMapper.findBySourceCharacterId(1L)).thenReturn(existing);

        assertThatThrownBy(() -> fixture.service.promote(1L, true, "operator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("88");

        verify(fixture.stAdapter, never()).exportCharacterPng(anyString());
        verify(fixture.stAdapter, never()).importCharacterPng(any(), anyString(), any());
    }

    @Test
    void promotionCreatesIndependentSystemDraftAndRemapsStructuredIds() {
        Fixture fixture = fixture(true);
        AppCharacter source = sourceCharacter();
        byte[] exportedPng = new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47 };
        when(fixture.characterMapper.findById(1L)).thenReturn(source);
        when(fixture.stAdapter.exportCharacterPng("h5draft_u7_source.png")).thenReturn(exportedPng);
        when(fixture.stAdapter.importCharacterPng(same(exportedPng), eq("h5draft_u7_source.png"), any()))
                .thenReturn(Map.of("file_name", "system_copy_1_new.png"));
        doAnswer(invocation -> {
            ((AppCharacter) invocation.getArgument(0)).setId(100L);
            return null;
        }).when(fixture.characterMapper).insertFull(any(AppCharacter.class));

        AppCharacterMember member = new AppCharacterMember();
        member.setId(10L);
        member.setName("成员");
        member.setVoiceConfigJson("user-private-voice");
        member.setPrimaryMember(true);
        member.setEnabled(true);
        when(fixture.studioMapper.findCardType(1L)).thenReturn("ENSEMBLE");
        when(fixture.studioMapper.listMembers(1L)).thenReturn(List.of(member));
        doAnswer(invocation -> {
            ((AppCharacterMember) invocation.getArgument(0)).setId(110L);
            return 1;
        }).when(fixture.studioMapper).insertMember(any(AppCharacterMember.class));

        AppCharacterOpening opening = new AppCharacterOpening();
        opening.setId(20L);
        opening.setTitle("开场");
        opening.setEnabled(true);
        when(fixture.studioMapper.listOpenings(1L)).thenReturn(List.of(opening));
        doAnswer(invocation -> {
            ((AppCharacterOpening) invocation.getArgument(0)).setId(120L);
            return 1;
        }).when(fixture.studioMapper).insertOpening(any(AppCharacterOpening.class));

        AppCharacterOpeningSegment segment = new AppCharacterOpeningSegment();
        segment.setOpeningId(20L);
        segment.setSpeakerMemberId(10L);
        segment.setSpeakerType("CHARACTER");
        segment.setContent("你好");
        when(fixture.studioMapper.listSegmentsByCharacter(1L)).thenReturn(List.of(segment));

        AppLorebookEntry lorebook = new AppLorebookEntry();
        lorebook.setMemberId(10L);
        lorebook.setContent("世界设定");
        lorebook.setEnabled(true);
        when(fixture.lorebookMapper.listAllByCharacterId(1L)).thenReturn(List.of(lorebook));

        CharacterSystemPromotionService.PromotionResult result = fixture.service.promote(1L, true, "alice");

        assertThat(result.targetCharacterId()).isEqualTo(100L);
        ArgumentCaptor<StCharacterImportRequest> importRequest =
                ArgumentCaptor.forClass(StCharacterImportRequest.class);
        verify(fixture.stAdapter).importCharacterPng(
                same(exportedPng),
                eq("h5draft_u7_source.png"),
                importRequest.capture()
        );
        assertThat(importRequest.getValue().fileType()).isEqualTo("png");
        assertThat(importRequest.getValue().preservedName())
                .startsWith("system_copy_1_")
                .endsWith(".png");
        ArgumentCaptor<AppCharacter> character = ArgumentCaptor.forClass(AppCharacter.class);
        verify(fixture.characterMapper).insertFull(character.capture());
        assertThat(character.getValue().getOwnerUserId()).isNull();
        assertThat(character.getValue().getPrivateCard()).isFalse();
        assertThat(character.getValue().getClientVisible()).isFalse();
        assertThat(character.getValue().getReviewStatus()).isEqualTo("APPROVED");
        assertThat(character.getValue().getLikeCount()).isZero();
        assertThat(character.getValue().getStAvatarUrl()).isEqualTo("system_copy_1_new.png");

        ArgumentCaptor<AppCharacterMember> copiedMember = ArgumentCaptor.forClass(AppCharacterMember.class);
        verify(fixture.studioMapper).insertMember(copiedMember.capture());
        assertThat(copiedMember.getValue().getCharacterId()).isEqualTo(100L);
        assertThat(copiedMember.getValue().getVoiceConfigJson()).isNull();

        ArgumentCaptor<AppCharacterOpeningSegment> copiedSegment =
                ArgumentCaptor.forClass(AppCharacterOpeningSegment.class);
        verify(fixture.studioMapper).insertOpeningSegment(copiedSegment.capture());
        assertThat(copiedSegment.getValue().getOpeningId()).isEqualTo(120L);
        assertThat(copiedSegment.getValue().getSpeakerMemberId()).isEqualTo(110L);

        ArgumentCaptor<AppLorebookEntry> copiedLorebook = ArgumentCaptor.forClass(AppLorebookEntry.class);
        verify(fixture.lorebookMapper).insert(copiedLorebook.capture());
        assertThat(copiedLorebook.getValue().getCharacterId()).isEqualTo(100L);
        assertThat(copiedLorebook.getValue().getMemberId()).isEqualTo(110L);

        ArgumentCaptor<AppCharacterSystemPromotion> audit =
                ArgumentCaptor.forClass(AppCharacterSystemPromotion.class);
        verify(fixture.promotionMapper).insert(audit.capture());
        assertThat(audit.getValue().getSourceUserId()).isEqualTo(7L);
        assertThat(audit.getValue().getTargetCharacterId()).isEqualTo(100L);
        assertThat(audit.getValue().getPromotedBy()).isEqualTo("alice");
        verify(fixture.stAdapter, never()).deleteCharacter("system_copy_1_new.png", false);
    }

    @Test
    void databaseFailureDeletesNewStFileWhenNoTransactionSynchronizationIsActive() {
        Fixture fixture = fixture(true);
        byte[] exportedPng = new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47 };
        when(fixture.characterMapper.findById(1L)).thenReturn(sourceCharacter());
        when(fixture.stAdapter.exportCharacterPng("h5draft_u7_source.png")).thenReturn(exportedPng);
        when(fixture.stAdapter.importCharacterPng(same(exportedPng), eq("h5draft_u7_source.png"), any()))
                .thenReturn(Map.of("file_name", "system_copy_1_failed.png"));
        doThrow(new IllegalStateException("db failed"))
                .when(fixture.characterMapper).insertFull(any(AppCharacter.class));

        assertThatThrownBy(() -> fixture.service.promote(1L, false, "operator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db failed");

        verify(fixture.stAdapter).deleteCharacter("system_copy_1_failed.png", false);
    }

    @Test
    void emptyStPngExportStopsBeforeImportAndDatabaseWrites() {
        Fixture fixture = fixture(true);
        when(fixture.characterMapper.findById(1L)).thenReturn(sourceCharacter());
        when(fixture.stAdapter.exportCharacterPng("h5draft_u7_source.png")).thenReturn(new byte[0]);

        assertThatThrownBy(() -> fixture.service.promote(1L, true, "operator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("导出为空");

        verify(fixture.stAdapter, never()).importCharacterPng(any(), anyString(), any());
        verify(fixture.characterMapper, never()).insertFull(any());
    }

    private static Fixture fixture(boolean enabled) {
        AppFeatureSettingsService settingsService = mock(AppFeatureSettingsService.class);
        AppFeatureSettings settings = new AppFeatureSettings();
        settings.setUserCharacterPromotionEnabled(enabled);
        when(settingsService.getSettings()).thenReturn(settings);
        AppCharacterMapper characterMapper = mock(AppCharacterMapper.class);
        CharacterStudioMapper studioMapper = mock(CharacterStudioMapper.class);
        AppLorebookEntryMapper lorebookMapper = mock(AppLorebookEntryMapper.class);
        AppCharacterSystemPromotionMapper promotionMapper = mock(AppCharacterSystemPromotionMapper.class);
        StAdapter stAdapter = mock(StAdapter.class);
        CharacterSystemPromotionService service = new CharacterSystemPromotionService(
                settingsService,
                characterMapper,
                studioMapper,
                lorebookMapper,
                promotionMapper,
                stAdapter
        );
        return new Fixture(service, characterMapper, studioMapper, lorebookMapper, promotionMapper, stAdapter);
    }

    private static AppCharacter sourceCharacter() {
        AppCharacter source = new AppCharacter();
        source.setId(1L);
        source.setOwnerUserId(7L);
        source.setPrivateCard(true);
        source.setStAvatarUrl("h5draft_u7_source.png");
        source.setName("来源角色");
        source.setCreatorName("原作者");
        source.setCreatorHandle("creator");
        source.setLikeCount(99);
        source.setDislikeCount(3);
        return source;
    }

    private record Fixture(
            CharacterSystemPromotionService service,
            AppCharacterMapper characterMapper,
            CharacterStudioMapper studioMapper,
            AppLorebookEntryMapper lorebookMapper,
            AppCharacterSystemPromotionMapper promotionMapper,
            StAdapter stAdapter
    ) {
    }
}
