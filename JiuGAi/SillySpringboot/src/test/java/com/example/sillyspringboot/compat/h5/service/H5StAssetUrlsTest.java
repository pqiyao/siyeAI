package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.integration.sillytavern.SillyTavernProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class H5StAssetUrlsTest {

    @Test
    void generatedCharacterAssetUrlCarriesValidSignature() {
        H5StAssetUrls urls = urls();
        String resolved = urls.resolve("private-card.png");
        var uri = UriComponentsBuilder.fromUriString(resolved).build();

        assertThat(urls.hasValidSignature(
                "private-card.png",
                Long.valueOf(uri.getQueryParams().getFirst("expires")),
                uri.getQueryParams().getFirst("sig")
        )).isTrue();
    }

    @Test
    void thumbnailRewritePreservesSignature() {
        H5StAssetUrls urls = urls();
        String resolved = urls.resolveWithPreset("private-card.png", "detail");
        var uri = UriComponentsBuilder.fromUriString(resolved).build();

        assertThat(uri.getPath()).isEqualTo("/api/v1/st-assets/characters-thumb/private-card.png");
        assertThat(uri.getQueryParams().getFirst("preset")).isEqualTo("detail");
        assertThat(urls.hasValidSignature(
                "private-card.png",
                Long.valueOf(uri.getQueryParams().getFirst("expires")),
                uri.getQueryParams().getFirst("sig")
        )).isTrue();
    }

    @Test
    void missingOrModifiedSignatureIsRejected() {
        H5StAssetUrls urls = urls();

        assertThat(urls.hasValidSignature("private-card.png", null, null)).isFalse();
        assertThat(urls.hasValidSignature("private-card.png", 4_102_444_800L, "invalid")).isFalse();
    }

    @Test
    void unrelatedBareAvatarFileCannotBecomeASigningOracle() {
        H5StAssetUrls urls = urls();

        String resolved = urls.portraitForCharacter(
                "another-users-private-card.png",
                "",
                "own-private-card.png"
        );

        assertThat(UriComponentsBuilder.fromUriString(resolved).build().getPath())
                .isEqualTo("/api/v1/st-assets/characters/own-private-card.png");
    }

    @Test
    void websiteUploadRemainsAValidPortraitOverride() {
        H5StAssetUrls urls = urls();

        assertThat(urls.portraitForCharacter(
                "/uploads/h5/owned-image.png",
                "",
                "own-private-card.png"
        )).isEqualTo("/uploads/h5/owned-image.png");
    }

    private H5StAssetUrls urls() {
        AppAuthProperties auth = new AppAuthProperties();
        auth.setSecret("test-only-st-asset-signature-secret");
        return new H5StAssetUrls(new SillyTavernProperties(), auth);
    }
}
