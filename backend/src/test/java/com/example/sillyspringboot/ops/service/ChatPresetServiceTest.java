package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.ops.entity.AppChatPreset;
import com.example.sillyspringboot.ops.mapper.AppChatPresetMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPresetServiceTest {

    private static final long USER_ID = 10L;
    private static final long CONVERSATION_ID = 20L;

    @Test
    void copyPlatformPresetKeepsPromptStructureAndDropsConnectionConfiguration() throws Exception {
        Fixture fixture = fixture();
        AppChatPreset source = preset(1L, null, "PUBLIC", true);
        source.setName("官方平衡");
        source.setBundleJson("""
                {"prompts":[{"identifier":"main","content":"private writing style"}],
                 "prompt_order":[{"character_id":100001,"order":[{"identifier":"main","enabled":true}]}],
                 "generation":{"temperature":0.8,"top_p":0.9,"frequency_penalty":0.2,
                "presence_penalty":0.3,"openai_max_tokens":700,
                "openai_max_context":16000,"chat_completion_source":"custom","reverse_proxy":"secret",
                "custom_url":"https://provider.invalid"}}
                """);
        when(fixture.presetMapper.findEnabledPublicById(1L)).thenReturn(source);

        fixture.service.copyPlatformPreset(USER_ID, 1L, "我的平衡");

        ArgumentCaptor<AppChatPreset> captor = ArgumentCaptor.forClass(AppChatPreset.class);
        verify(fixture.presetMapper).insertPrivate(captor.capture());
        AppChatPreset stored = captor.getValue();
        JsonNode generation = new ObjectMapper().readTree(stored.getBundleJson()).path("generation");
        assertThat(stored.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(stored.getScope()).isEqualTo("PRIVATE");
        assertThat(stored.getName()).isEqualTo("我的平衡");
        assertThat(generation.size()).isEqualTo(6);
        assertThat(generation.path("temperature").asDouble()).isEqualTo(0.8d);
        assertThat(generation.path("top_p").asDouble()).isEqualTo(0.9d);
        assertThat(generation.path("frequency_penalty").asDouble()).isEqualTo(0.2d);
        assertThat(generation.path("presence_penalty").asDouble()).isEqualTo(0.3d);
        assertThat(generation.path("openai_max_tokens").asInt()).isEqualTo(800);
        assertThat(generation.path("openai_max_context").asInt()).isEqualTo(16000);
        assertThat(stored.getBundleJson()).contains("private writing style", "prompt_order")
                .doesNotContain("custom\"", "reverse_proxy", "provider.invalid");
        verify(fixture.presetMapper).lockOwnerUser(USER_ID);
        verify(fixture.presetMapper).countPrivateByOwner(USER_ID);
    }

    @Test
    void editingPrivatePresetPreservesPromptStructureInRuntimeBundle() throws Exception {
        Fixture fixture = fixture();
        AppChatPreset existing = preset(5L, USER_ID, "PRIVATE", true);
        existing.setBundleJson("""
                {"schemaVersion":1,"source_type":"USER_COPY",
                 "prompts":[{"identifier":"main","content":"You write restrained literary Chinese."}],
                 "prompt_order":[{"character_id":100001,"order":[{"identifier":"main","enabled":true}]}],
                 "generation":{"temperature":1,"top_p":1,"openai_max_tokens":800,"openai_max_context":8192}}
                """);
        when(fixture.presetMapper.findPrivateByIdForOwner(5L, USER_ID)).thenReturn(existing, existing);
        when(fixture.presetMapper.updatePrivate(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq("我的预设"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(1);

        fixture.service.updatePrivatePreset(USER_ID, 5L, "我的预设", 0.7d, 0.9d, 0.1d, 0.0d, 1000, true);

        ArgumentCaptor<String> bundleCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.presetMapper).updatePrivate(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq("我的预设"),
                org.mockito.ArgumentMatchers.anyString(),
                bundleCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(true)
        );
        JsonNode updated = new ObjectMapper().readTree(bundleCaptor.getValue());
        assertThat(updated.path("prompts").get(0).path("content").asText())
                .isEqualTo("You write restrained literary Chinese.");
        assertThat(updated.path("prompt_order").isArray()).isTrue();
        assertThat(updated.path("generation").path("openai_max_tokens").asInt()).isEqualTo(1000);
    }

    @Test
    void createPrivatePresetStartsWithSafeDefaultsWithoutPlatformPreset() throws Exception {
        Fixture fixture = fixture();
        fixture.service.createPrivatePreset(USER_ID, "我的自定义");

        ArgumentCaptor<AppChatPreset> captor = ArgumentCaptor.forClass(AppChatPreset.class);
        verify(fixture.presetMapper).insertPrivate(captor.capture());
        AppChatPreset stored = captor.getValue();
        JsonNode generation = new ObjectMapper().readTree(stored.getBundleJson()).path("generation");
        assertThat(stored.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(stored.getScope()).isEqualTo("PRIVATE");
        assertThat(stored.getSourceType()).isEqualTo("USER_CUSTOM");
        assertThat(stored.getName()).isEqualTo("我的自定义");
        assertThat(new ObjectMapper().readTree(stored.getBundleJson()).path("source_type").asText())
                .isEqualTo("USER_CUSTOM");
        assertThat(generation.path("temperature").asDouble()).isEqualTo(1.0d);
        assertThat(generation.path("top_p").asDouble()).isEqualTo(1.0d);
        assertThat(generation.path("frequency_penalty").asDouble()).isZero();
        assertThat(generation.path("presence_penalty").asDouble()).isZero();
        assertThat(generation.path("openai_max_tokens").asInt()).isEqualTo(800);
        assertThat(generation.path("openai_max_context").asInt()).isEqualTo(8192);
        verify(fixture.presetMapper).lockOwnerUser(USER_ID);
        verify(fixture.presetMapper).countPrivateByOwner(USER_ID);
    }

    @Test
    void copyCannotCreatePresetForDeletedUser() {
        Fixture fixture = fixture();
        AppChatPreset source = preset(1L, null, "PUBLIC", true);
        when(fixture.presetMapper.findEnabledPublicById(1L)).thenReturn(source);
        when(fixture.presetMapper.lockOwnerUser(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> fixture.service.copyPlatformPreset(USER_ID, 1L, "orphan"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));

        verify(fixture.presetMapper, never()).insertPrivate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bindRejectsPresetThatIsNotAvailableToConversationOwner() {
        Fixture fixture = fixture();
        AppConversation conversation = new AppConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID)).thenReturn(conversation);
        when(fixture.presetMapper.findEnabledAvailableById(99L, USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> fixture.service.bindConversationPreset(USER_ID, CONVERSATION_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void invalidPrivateBundleFallsBackWithoutBreakingGeneration() {
        Fixture fixture = fixture();
        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setUserId(USER_ID);
        binding.setChatPresetId(5L);
        AppChatPreset preset = preset(5L, USER_ID, "PRIVATE", true);
        preset.setBundleJson("{broken");
        when(fixture.presetMapper.findEnabledAvailableById(5L, USER_ID)).thenReturn(preset);

        assertThat(fixture.service.resolveRuntimePresetBundle(binding)).isNull();
    }

    @Test
    void legacyPrivatePresetBelowNewEditableMinimumStillResolvesAtRuntime() {
        Fixture fixture = fixture();
        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setUserId(USER_ID);
        binding.setChatPresetId(5L);
        AppChatPreset preset = preset(5L, USER_ID, "PRIVATE", true);
        when(fixture.presetMapper.findEnabledAvailableById(5L, USER_ID)).thenReturn(preset);

        assertThat(fixture.service.resolveRuntimePresetBundle(binding))
                .contains("\"openai_max_tokens\":512")
                .contains("\"frequency_penalty\":0.0")
                .contains("\"presence_penalty\":0.0");
    }

    @Test
    void conversationWithoutPresetUsesEnabledGlobalDefault() throws Exception {
        Fixture fixture = fixture();
        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setUserId(USER_ID);
        AppChatPreset preset = preset(1L, null, "PUBLIC", true);
        preset.setName("Default");
        preset.setGlobalDefault(true);
        preset.setBundleJson("{\"generation\":{\"openai_max_tokens\":1000,\"openai_max_context\":65536}}");
        when(fixture.presetMapper.findEnabledGlobalDefault()).thenReturn(preset);
        when(fixture.runtimeDefaultSettingsService.current()).thenReturn(
                new ChatRuntimeDefaultSettingsService.Settings(1000, 65536, "legacy-default"));

        JsonNode resolved = new ObjectMapper().readTree(fixture.service.resolveRuntimePresetBundle(binding));

        assertThat(resolved.path("_effective_preset_id").asLong()).isEqualTo(1L);
        assertThat(resolved.path("generation").path("openai_max_tokens").asInt()).isEqualTo(1000);
        assertThat(resolved.path("generation").path("openai_max_context").asInt()).isEqualTo(65536);
    }

    @Test
    void globalRuntimeSettingsOverrideOnlyTheDefaultPresetSnapshot() throws Exception {
        Fixture fixture = fixture();
        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setUserId(USER_ID);
        AppChatPreset preset = preset(1L, null, "PUBLIC", true);
        preset.setName("Default");
        preset.setGlobalDefault(true);
        // The ST snapshot can contain different values; app runtime policy wins
        // only when this row is selected as the global default.
        preset.setBundleJson("{\"generation\":{\"openai_max_tokens\":2805,\"openai_max_context\":2000000}}");
        when(fixture.presetMapper.findEnabledGlobalDefault()).thenReturn(preset);
        when(fixture.runtimeDefaultSettingsService.current()).thenReturn(
                new ChatRuntimeDefaultSettingsService.Settings(2536, 300000, "database"));

        JsonNode resolved = new ObjectMapper().readTree(fixture.service.resolveRuntimePresetBundle(binding));

        assertThat(resolved.path("generation").path("openai_max_tokens").asInt()).isEqualTo(2536);
        assertThat(resolved.path("generation").path("openai_max_context").asInt()).isEqualTo(300000);
    }

    @Test
    void conversationPresetTakesPriorityOverGlobalDefault() throws Exception {
        Fixture fixture = fixture();
        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setUserId(USER_ID);
        binding.setChatPresetId(5L);
        AppChatPreset privatePreset = preset(5L, USER_ID, "PRIVATE", true);
        when(fixture.presetMapper.findEnabledAvailableById(5L, USER_ID)).thenReturn(privatePreset);

        JsonNode resolved = new ObjectMapper().readTree(fixture.service.resolveRuntimePresetBundle(binding));

        assertThat(resolved.path("_effective_preset_id").asLong()).isEqualTo(5L);
        verify(fixture.presetMapper, never()).findEnabledGlobalDefault();
        verify(fixture.runtimeDefaultSettingsService, never()).current();
    }

    @Test
    void adminOverrideIsUsedForRuntimeWithoutChangingTheStSnapshot() throws Exception {
        Fixture fixture = fixture();
        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setUserId(USER_ID);
        binding.setChatPresetId(7L);
        AppChatPreset preset = preset(7L, null, "PUBLIC", true);
        preset.setBundleJson("{\"generation\":{\"temperature\":0.7,\"openai_max_tokens\":1024,\"openai_max_context\":8192}}");
        preset.setAdminOverrideJson("{\"generation\":{\"temperature\":0.4,\"openai_max_tokens\":2048,\"openai_max_context\":8192},\"api_type\":\"openai\",\"source_type\":\"ST_PLATFORM\"}");
        when(fixture.presetMapper.findEnabledAvailableById(7L, USER_ID)).thenReturn(preset);

        JsonNode resolved = new ObjectMapper().readTree(fixture.service.resolveRuntimePresetBundle(binding));

        assertThat(resolved.path("generation").path("temperature").asDouble()).isEqualTo(0.4d);
        assertThat(resolved.path("generation").path("openai_max_tokens").asInt()).isEqualTo(2048);
        assertThat(preset.getBundleJson()).contains("0.7").doesNotContain("2048");
        verify(fixture.runtimeDefaultSettingsService, never()).current();
    }

    @Test
    void adminConfigurationPersistsSeparateOverrideAndCanRestoreStSnapshot() {
        Fixture fixture = fixture();
        AppChatPreset preset = preset(7L, null, "PUBLIC", true);
        when(fixture.presetMapper.findPublicById(7L)).thenReturn(preset);
        when(fixture.presetMapper.updateAdminOverride(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        when(fixture.presetMapper.clearAdminOverride(7L)).thenReturn(1);

        assertThat(fixture.service.updateAdminConfiguration(7L,
                "{\"generation\":{\"temperature\":0.5,\"openai_max_tokens\":2048,\"openai_max_context\":8192}}"))
                .isTrue();
        ArgumentCaptor<String> override = ArgumentCaptor.forClass(String.class);
        verify(fixture.presetMapper).updateAdminOverride(org.mockito.ArgumentMatchers.eq(7L), override.capture());
        assertThat(override.getValue()).contains("\"api_type\":\"openai\"")
                .contains("\"source_type\":\"ST_PLATFORM\"");
        assertThat(fixture.service.restoreStConfiguration(7L)).isTrue();
        verify(fixture.presetMapper).clearAdminOverride(7L);
    }

    @Test
    void adminConfigurationRejectsContextSmallerThanOutputBudget() {
        Fixture fixture = fixture();
        when(fixture.presetMapper.findPublicById(7L)).thenReturn(preset(7L, null, "PUBLIC", true));

        assertThatThrownBy(() -> fixture.service.updateAdminConfiguration(7L,
                "{\"generation\":{\"openai_max_tokens\":4096,\"openai_max_context\":4096}}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("+ 512");

        verify(fixture.presetMapper, never()).updateAdminOverride(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void syncingDefaultPreservesStSnapshotBudgets() throws Exception {
        Fixture fixture = fixture();
        JsonNode envelope = new ObjectMapper().readTree("""
                {"openai_setting_names":["Default"],
                 "openai_settings":["{\\\"openai_max_tokens\\\":300,\\\"openai_max_context\\\":4095}"]}
                """);
        when(fixture.stClient.readStSettingsEnvelope()).thenReturn(envelope);
        when(fixture.presetMapper.listUnavailablePlatformPresetIds("openai")).thenReturn(List.of());

        fixture.service.syncOpenAiPlatformPresetsFromSt();

        ArgumentCaptor<AppChatPreset> captor = ArgumentCaptor.forClass(AppChatPreset.class);
        verify(fixture.presetMapper).upsertPlatformPreset(captor.capture());
        JsonNode generation = new ObjectMapper().readTree(captor.getValue().getBundleJson()).path("generation");
        assertThat(captor.getValue().getGlobalDefault()).isTrue();
        assertThat(generation.path("openai_max_tokens").asInt()).isEqualTo(300);
        assertThat(generation.path("openai_max_context").asInt()).isEqualTo(4095);
    }

    @Test
    void disablingPrivatePresetClearsExistingConversationBindings() {
        Fixture fixture = fixture();
        AppChatPreset existing = preset(5L, USER_ID, "PRIVATE", true);
        AppChatPreset updated = preset(5L, USER_ID, "PRIVATE", false);
        updated.setName("低随机");
        when(fixture.presetMapper.findPrivateByIdForOwner(5L, USER_ID)).thenReturn(existing, updated);
        when(fixture.presetMapper.updatePrivate(
                5L, USER_ID, "低随机", "temp=0.4 / top_p=0.8 / frequency=0.1 / presence=0.2 / tokens=800 / context=8192",
                "{\"schemaVersion\":1,\"source_type\":\"USER_COPY\",\"generation\":{\"temperature\":0.4,\"top_p\":0.8,\"frequency_penalty\":0.1,\"presence_penalty\":0.2,\"openai_max_tokens\":800,\"openai_max_context\":8192}}",
                false
        )).thenReturn(1);

        fixture.service.updatePrivatePreset(USER_ID, 5L, "低随机", 0.4d, 0.8d, 0.1d, 0.2d, 800, false);

        verify(fixture.bindingMapper).clearChatPresetId(5L);
    }

    @Test
    void editingPrivatePresetRejectsUnsafeLowOutputBudget() {
        Fixture fixture = fixture();
        AppChatPreset existing = preset(5L, USER_ID, "PRIVATE", true);
        when(fixture.presetMapper.findPrivateByIdForOwner(5L, USER_ID)).thenReturn(existing);

        assertThatThrownBy(() -> fixture.service.updatePrivatePreset(
                USER_ID, 5L, "过短", 1.0d, 1.0d, 0.0d, 0.0d, 799, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("800");

        verify(fixture.presetMapper, never()).updatePrivate(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean()
        );
    }

    @Test
    void editingPrivatePresetAutomaticallyExpandsHiddenContextBudget() {
        Fixture fixture = fixture();
        AppChatPreset existing = preset(5L, USER_ID, "PRIVATE", true);
        when(fixture.presetMapper.findPrivateByIdForOwner(5L, USER_ID)).thenReturn(existing, existing);
        when(fixture.presetMapper.updatePrivate(
                5L,
                USER_ID,
                "长输出",
                "temp=1 / top_p=1 / frequency=0 / presence=0 / tokens=8192 / context=8704",
                "{\"schemaVersion\":1,\"source_type\":\"USER_COPY\",\"generation\":{\"temperature\":1.0,\"top_p\":1.0,\"frequency_penalty\":0.0,\"presence_penalty\":0.0,\"openai_max_tokens\":8192,\"openai_max_context\":8704}}",
                true
        )).thenReturn(1);

        fixture.service.updatePrivatePreset(
                USER_ID, 5L, "长输出", 1.0d, 1.0d, 0.0d, 0.0d, 8192, true);

        verify(fixture.presetMapper).updatePrivate(
                5L,
                USER_ID,
                "长输出",
                "temp=1 / top_p=1 / frequency=0 / presence=0 / tokens=8192 / context=8704",
                "{\"schemaVersion\":1,\"source_type\":\"USER_COPY\",\"generation\":{\"temperature\":1.0,\"top_p\":1.0,\"frequency_penalty\":0.0,\"presence_penalty\":0.0,\"openai_max_tokens\":8192,\"openai_max_context\":8704}}",
                true
        );
    }

    @Test
    void syncMirrorsSourceAvailabilityAndReportsMalformedEntries() throws Exception {
        Fixture fixture = fixture();
        JsonNode envelope = new ObjectMapper().readTree("""
                {"openai_setting_names":["Balanced","Broken","Trailing"],
                 "openai_settings":["{\\\"temperature\\\":0.7}","not-json"]}
                """);
        when(fixture.stClient.readStSettingsEnvelope()).thenReturn(envelope);
        when(fixture.presetMapper.listUnavailablePlatformPresetIds("openai")).thenReturn(List.of(9L));

        Map<String, Object> result = fixture.service.syncOpenAiPlatformPresetsFromSt();

        assertThat(result.get("imported")).isEqualTo(1);
        assertThat(result.get("skipped")).isEqualTo(2);
        assertThat(result.get("unavailable")).isEqualTo(1);
        verify(fixture.presetMapper).markAllPlatformPresetsSourceUnavailable("openai");
        ArgumentCaptor<AppChatPreset> presetCaptor = ArgumentCaptor.forClass(AppChatPreset.class);
        verify(fixture.presetMapper).upsertPlatformPreset(presetCaptor.capture());
        assertThat(presetCaptor.getValue().getSourceAvailable()).isTrue();
        verify(fixture.bindingMapper).clearChatPresetId(9L);
    }

    @Test
    void syncReportsSettingsJsonThatIsNotAnObject() throws Exception {
        Fixture fixture = fixture();
        JsonNode envelope = new ObjectMapper().readTree("""
                {"openai_setting_names":["NotObject"],"openai_settings":["[]"]}
                """);
        when(fixture.stClient.readStSettingsEnvelope()).thenReturn(envelope);
        when(fixture.presetMapper.listUnavailablePlatformPresetIds("openai")).thenReturn(List.of());

        Map<String, Object> result = fixture.service.syncOpenAiPlatformPresetsFromSt();

        assertThat(result.get("imported")).isEqualTo(0);
        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat((List<?>) result.get("warnings"))
                .anySatisfy(value -> assertThat(value).asString().contains("NotObject").contains("JSON object"));
    }

    @Test
    void disablingPublicPresetClearsExistingConversationBindings() {
        Fixture fixture = fixture();
        AppChatPreset preset = preset(7L, null, "PUBLIC", true);
        when(fixture.presetMapper.findPublicById(7L)).thenReturn(preset);
        when(fixture.presetMapper.updateStatus(7L, false)).thenReturn(1);

        assertThat(fixture.service.updateStatus(7L, false)).isTrue();

        verify(fixture.bindingMapper).clearChatPresetId(7L);
    }

    @Test
    void enablingSourceUnavailablePublicPresetIsRejected() {
        Fixture fixture = fixture();
        AppChatPreset preset = preset(7L, null, "PUBLIC", false);
        preset.setSourceAvailable(false);
        when(fixture.presetMapper.findPublicById(7L)).thenReturn(preset);

        assertThatThrownBy(() -> fixture.service.updateStatus(7L, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void deletingPublicPresetClearsBindingsBeforeDeletingRecord() {
        Fixture fixture = fixture();
        AppChatPreset preset = preset(8L, null, "PUBLIC", true);
        when(fixture.presetMapper.findPublicById(8L)).thenReturn(preset);
        when(fixture.presetMapper.deleteById(8L)).thenReturn(1);

        assertThat(fixture.service.delete(8L)).isTrue();

        verify(fixture.bindingMapper).clearChatPresetId(8L);
        verify(fixture.presetMapper).deleteById(8L);
    }

    @Test
    void adminDetailCannotReadPrivatePresetById() {
        Fixture fixture = fixture();
        when(fixture.presetMapper.findPublicById(5L)).thenReturn(null);

        assertThat(fixture.service.adminDetail(5L)).isEmpty();
    }

    private static AppChatPreset preset(long id, Long ownerUserId, String scope, boolean enabled) {
        AppChatPreset preset = new AppChatPreset();
        preset.setId(id);
        preset.setOwnerUserId(ownerUserId);
        preset.setScope(scope);
        preset.setSourceType(ownerUserId == null ? "ST_PLATFORM" : "USER_COPY");
        preset.setApiType("openai");
        preset.setSourceName("source-" + id);
        preset.setName("preset-" + id);
        preset.setDescription("");
        preset.setBundleJson("{\"generation\":{\"temperature\":1,\"top_p\":1,\"openai_max_tokens\":512,\"openai_max_context\":8192}}");
        preset.setEnabled(enabled);
        preset.setSourceAvailable(true);
        preset.setSortOrder(100);
        return preset;
    }

    private static Fixture fixture() {
        AppChatPresetMapper presetMapper = mock(AppChatPresetMapper.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationStBindingMapper bindingMapper = mock(AppConversationStBindingMapper.class);
        StClient stClient = mock(StClient.class);
        ChatRuntimeDefaultSettingsService runtimeDefaultSettingsService = mock(ChatRuntimeDefaultSettingsService.class);
        when(presetMapper.lockOwnerUser(USER_ID)).thenReturn(USER_ID);
        ChatPresetService service = new ChatPresetService(
                presetMapper, conversationMapper, bindingMapper, stClient, runtimeDefaultSettingsService);
        return new Fixture(service, presetMapper, conversationMapper, bindingMapper, stClient,
                runtimeDefaultSettingsService);
    }

    private record Fixture(
            ChatPresetService service,
            AppChatPresetMapper presetMapper,
            AppConversationMapper conversationMapper,
            AppConversationStBindingMapper bindingMapper,
            StClient stClient,
            ChatRuntimeDefaultSettingsService runtimeDefaultSettingsService
    ) {
    }
}
