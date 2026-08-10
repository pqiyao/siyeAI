package com.example.sillyspringboot.character.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterCatalogIsolationTest {

    @Test
    void unknownStFileNeverCreatesSystemCharacter() {
        Fixture fixture = fixture("h5_u42_unknown.png");

        fixture.service.refreshFeedFromStNow();

        verify(fixture.mapper, never()).insert(any());
        verify(fixture.mapper, never()).insertFull(any());
        verify(fixture.mapper, never()).updateById(any());
    }

    @Test
    void deletedSystemCharacterIsNotRevivedByStScan() {
        Fixture fixture = fixture("deleted-system.png");
        AppCharacter deleted = new AppCharacter();
        deleted.setId(9L);
        when(fixture.mapper.findSystemByStAvatarUrlAny("deleted-system.png")).thenReturn(deleted);

        fixture.service.refreshFeedFromStNow();

        verify(fixture.mapper, never()).findSystemByStAvatarUrlAny("deleted-system.png");
        verify(fixture.mapper, never()).insertFull(any());
        verify(fixture.mapper, never()).updateById(any());
    }

    @Test
    void privateStFileCannotRefreshSystemCatalogRow() {
        Fixture fixture = fixture("h5draft_u42_private.png");
        AppCharacter privateCharacter = new AppCharacter();
        privateCharacter.setId(42L);
        privateCharacter.setOwnerUserId(42L);
        privateCharacter.setPrivateCard(true);
        when(fixture.mapper.findActivePrivateByStAvatarUrl("h5draft_u42_private.png"))
                .thenReturn(privateCharacter);

        fixture.service.refreshFeedFromStNow();

        verify(fixture.mapper, never()).findActiveSystemByStAvatarUrl("h5draft_u42_private.png");
        verify(fixture.mapper, never()).updateById(any());
    }

    private Fixture fixture(String avatar) {
        AppCharacterMapper mapper = mock(AppCharacterMapper.class);
        StAdapter stAdapter = mock(StAdapter.class);
        when(stAdapter.listCharactersAll()).thenReturn(List.of(
                new StCharacterSummary("Private", avatar, "description", 1L)
        ));
        CharacterCatalogService service = new CharacterCatalogService(
                mapper,
                stAdapter,
                mock(StWorldbookCatalogService.class),
                mock(EmbeddedLorebookSyncService.class),
                new CharacterPublicProfileService()
        );
        return new Fixture(service, mapper);
    }

    private record Fixture(CharacterCatalogService service, AppCharacterMapper mapper) {
    }
}
