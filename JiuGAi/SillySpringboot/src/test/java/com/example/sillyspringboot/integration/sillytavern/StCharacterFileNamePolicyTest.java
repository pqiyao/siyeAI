package com.example.sillyspringboot.integration.sillytavern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StCharacterFileNamePolicyTest {

    @Test
    void acceptsOnlySimplePngInsideCurrentImportNamespace() {
        String preserved = "h5_u7_abc123";

        assertThat(StCharacterFileNamePolicy.isExpectedImportResult("h5_u7_abc123.png", preserved)).isTrue();
        assertThat(StCharacterFileNamePolicy.isExpectedImportResult("h5_u7_abc123_1.png", preserved)).isTrue();
        assertThat(StCharacterFileNamePolicy.isExpectedImportResult("system.png", preserved)).isFalse();
        assertThat(StCharacterFileNamePolicy.isExpectedImportResult("../h5_u7_abc123.png", preserved)).isFalse();
        assertThat(StCharacterFileNamePolicy.isExpectedImportResult("folder/h5_u7_abc123.png", preserved)).isFalse();
        assertThat(StCharacterFileNamePolicy.isExpectedImportResult("h5_u7_abc123.jpg", preserved)).isFalse();
    }

    @Test
    void syncMustReturnTheExactExistingSimplePngFileName() {
        assertThat(StCharacterFileNamePolicy.isStableSyncResult("角色卡.png", "角色卡.png")).isTrue();
        assertThat(StCharacterFileNamePolicy.isStableSyncResult("old.png", "new.png")).isFalse();
        assertThat(StCharacterFileNamePolicy.isStableSyncResult("folder/old.png", "folder/old.png")).isFalse();
        assertThat(StCharacterFileNamePolicy.isStableSyncResult("old.png", "../old.png")).isFalse();
    }
}
