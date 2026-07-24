package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import com.example.sillyspringboot.ops.entity.AppCharacterImagePolicy;
import com.example.sillyspringboot.ops.mapper.AppCharacterImagePolicyMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageGenerationPolicyServiceTest {

    private AppImageGenerationSettingsService settingsService;
    private AppCharacterImagePolicyMapper policyMapper;
    private AppCharacterMapper characterMapper;
    private ImageGenerationPolicyService service;
    private AppImageGenerationSettings global;
    private AppCharacter character;

    @BeforeEach
    void setUp() {
        settingsService = mock(AppImageGenerationSettingsService.class);
        policyMapper = mock(AppCharacterImagePolicyMapper.class);
        characterMapper = mock(AppCharacterMapper.class);
        service = new ImageGenerationPolicyService(
                settingsService, policyMapper, characterMapper, new ObjectMapper());

        global = new AppImageGenerationSettings();
        global.setDefaultConsistencyMode("balanced");
        global.setAllowedConsistencyModes(List.of("free", "balanced", "strong"));
        global.setDefaultReferenceSourceMode("latest_generated_first");
        global.setAllowedReferenceSourceModes(List.of("latest_generated_first", "avatar_only"));
        global.setReferenceImagesEnabled(true);
        global.setRecentSceneContextEnabled(true);
        global.setNegativePrompt("watermark");
        global.setReferenceWorkflow("reference.json");
        when(settingsService.getSettings()).thenReturn(global);

        character = new AppCharacter();
        character.setId(12L);
        character.setName("Lin");
        character.setDescription("silver hair and green eyes");
        character.setPersona("quiet swordswoman");
        character.setScenario("rainy station");
        character.setAvatarUrl("https://assets.example/lin.png");
        character.setPublicTagsJson("[\"anime\",\"swordswoman\"]");
        when(characterMapper.findById(12L)).thenReturn(character);
    }

    @Test
    void freeModeKeepsPureTextPromptAndRemovesReference() {
        ImageGenerationPolicyService.Resolution result = service.resolve(12L, "st_comfy", Map.of(
                "prompt", "sitting by the window",
                "referenceMode", "free",
                "referenceImageUrl", "data:image/png;base64,abc",
                "recentSceneHint", "she just arrived"
        ));

        assertThat(result.effectiveMode()).isEqualTo("free");
        assertThat(result.referencePolicy()).isEqualTo("prompt_first");
        assertThat(result.payload().get("prompt")).isEqualTo("sitting by the window");
        assertThat(result.payload().get("referenceImageUrl")).isEqualTo("");
    }

    @Test
    void balancedModeAddsBoundedCharacterAndRecentSceneContext() {
        ImageGenerationPolicyService.Resolution result = service.resolve(12L, "st_comfy", Map.of(
                "prompt", "holding an umbrella",
                "referenceMode", "balanced",
                "recentSceneHint", "waiting for the last train"
        ));

        assertThat(result.effectiveMode()).isEqualTo("balanced");
        assertThat(result.referencePolicy()).isEqualTo("balanced");
        assertThat(String.valueOf(result.payload().get("prompt")))
                .contains("holding an umbrella")
                .contains("Character identity: Lin")
                .contains("silver hair and green eyes")
                .contains("waiting for the last train")
                .doesNotContain("system prompt");
    }

    @Test
    void strongModeRejectsMissingReference() {
        character.setAvatarUrl("");
        character.setCoverUrl("");
        character.setStAvatarUrl("");

        assertThatThrownBy(() -> service.resolve(12L, "st_comfy", Map.of(
                "prompt", "portrait", "referenceMode", "strong")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需要可用的角色头像");
    }

    @Test
    void disallowedRequestedModeFallsBackToAdministratorDefault() {
        global.setDefaultConsistencyMode("free");
        global.setAllowedConsistencyModes(List.of("free"));

        ImageGenerationPolicyService.Resolution result = service.resolve(12L, "st_comfy", Map.of(
                "prompt", "portrait", "referenceMode", "strong"));

        assertThat(result.effectiveMode()).isEqualTo("free");
        assertThat(result.warnings()).anyMatch(message -> message.contains("管理员未开放"));
    }

    @Test
    void characterOverrideCanDisableImagesWithoutChangingGlobalPolicy() {
        AppCharacterImagePolicy override = new AppCharacterImagePolicy();
        override.setCharacterId(12L);
        override.setImageEnabled(false);
        when(policyMapper.findByCharacterId(12L)).thenReturn(override);

        assertThatThrownBy(() -> service.resolve(12L, "st_comfy", Map.of("prompt", "portrait")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前角色已关闭聊天生图");
        assertThat(global.getAllowedConsistencyModes()).containsExactly("free", "balanced", "strong");
    }
}
