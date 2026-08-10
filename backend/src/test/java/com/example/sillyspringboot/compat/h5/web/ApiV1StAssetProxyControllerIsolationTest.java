package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.integration.sillytavern.SillyTavernProperties;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiV1StAssetProxyControllerIsolationTest {

    @Test
    void unsignedPrivateCharacterAssetIsNotFetchedFromSt() {
        Fixture fixture = fixture();
        AppCharacter privateCharacter = new AppCharacter();
        privateCharacter.setOwnerUserId(7L);
        privateCharacter.setPrivateCard(true);
        when(fixture.characterMapper.findActivePrivateByStAvatarUrl("private.png"))
                .thenReturn(privateCharacter);

        var response = fixture.controller.characterFile("private.png", null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(fixture.stClient, never()).fetchUserDirectoryFile("/characters/private.png");
    }

    @Test
    void unsignedSystemCharacterAssetRemainsCompatibleWithAdminUi() {
        Fixture fixture = fixture();
        AppCharacter systemCharacter = new AppCharacter();
        systemCharacter.setPrivateCard(false);
        when(fixture.characterMapper.findActiveSystemByStAvatarUrl("system.png"))
                .thenReturn(systemCharacter);
        when(fixture.stClient.fetchUserDirectoryFile("/characters/system.png"))
                .thenReturn(new byte[]{1, 2, 3});

        var response = fixture.controller.characterFile("system.png", null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    void signedActivePrivateCharacterAssetCanBeRead() {
        Fixture fixture = fixture();
        AppCharacter privateCharacter = new AppCharacter();
        privateCharacter.setOwnerUserId(7L);
        privateCharacter.setPrivateCard(true);
        when(fixture.characterMapper.findActivePrivateByStAvatarUrl("private.png"))
                .thenReturn(privateCharacter);
        when(fixture.stClient.fetchUserDirectoryFile("/characters/private.png"))
                .thenReturn(new byte[]{4, 5, 6});
        var signed = UriComponentsBuilder.fromUriString(fixture.urls.resolve("private.png")).build();

        var response = fixture.controller.characterFile(
                "private.png",
                Long.valueOf(signed.getQueryParams().getFirst("expires")),
                signed.getQueryParams().getFirst("sig"),
                null
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(4, 5, 6);
    }

    @Test
    void signedAssetIsRevokedWhenNoActiveCharacterReferencesIt() {
        Fixture fixture = fixture();
        var signed = UriComponentsBuilder.fromUriString(fixture.urls.resolve("deleted-private.png")).build();

        var response = fixture.controller.characterFile(
                "deleted-private.png",
                Long.valueOf(signed.getQueryParams().getFirst("expires")),
                signed.getQueryParams().getFirst("sig"),
                null
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(fixture.stClient, never()).fetchUserDirectoryFile("/characters/deleted-private.png");
    }

    private Fixture fixture() {
        StClient stClient = mock(StClient.class);
        AppCharacterMapper characterMapper = mock(AppCharacterMapper.class);
        SillyTavernProperties stProperties = new SillyTavernProperties();
        AppAuthProperties authProperties = new AppAuthProperties();
        authProperties.setSecret("test-only-st-asset-signature-secret");
        H5StAssetUrls urls = new H5StAssetUrls(stProperties, authProperties);
        ApiV1StAssetProxyController controller = new ApiV1StAssetProxyController(
                stClient,
                stProperties,
                urls,
                characterMapper
        );
        return new Fixture(controller, stClient, characterMapper, urls);
    }

    private record Fixture(
            ApiV1StAssetProxyController controller,
            StClient stClient,
            AppCharacterMapper characterMapper,
            H5StAssetUrls urls
    ) {
    }
}
