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
    void copyPlatformPresetPersistsOnlyWhitelistedGenerationParameters() throws Exception {
        Fixture fixture = fixture();
        AppChatPreset source = preset(1L, null, "PUBLIC", true);
        source.setName("官方平衡");
        source.setBundleJson("""
                {"generation":{"temperature":0.8,"top_p":0.9,"openai_max_tokens":700,
                "openai_max_context":16000,"chat_completion_source":"custom","reverse_proxy":"secret",
                "prompts":[{"identifier":"main","content":"hidden"}]}}
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
        assertThat(generation.size()).isEqualTo(4);
        assertThat(generation.path("temperature").asDouble()).isEqualTo(0.8d);
        assertThat(generation.path("top_p").asDouble()).isEqualTo(0.9d);
        assertThat(generation.path("openai_max_tokens").asInt()).isEqualTo(700);
        assertThat(generation.path("openai_max_context").asInt()).isEqualTo(16000);
        assertThat(stored.getBundleJson()).doesNotContain("custom", "reverse_proxy", "hidden", "prompts");
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
    void disablingPrivatePresetClearsExistingConversationBindings() {
        Fixture fixture = fixture();
        AppChatPreset existing = preset(5L, USER_ID, "PRIVATE", true);
        AppChatPreset updated = preset(5L, USER_ID, "PRIVATE", false);
        updated.setName("低随机");
        when(fixture.presetMapper.findPrivateByIdForOwner(5L, USER_ID)).thenReturn(existing, updated);
        when(fixture.presetMapper.updatePrivate(
                5L, USER_ID, "低随机", "temp=0.4 / top_p=0.8 / tokens=512 / context=8192",
                "{\"schemaVersion\":1,\"source_type\":\"USER_COPY\",\"generation\":{\"temperature\":0.4,\"top_p\":0.8,\"openai_max_tokens\":512,\"openai_max_context\":8192}}",
                false
        )).thenReturn(1);

        fixture.service.updatePrivatePreset(USER_ID, 5L, "低随机", 0.4d, 0.8d, 512, 8192, false);

        verify(fixture.bindingMapper).clearChatPresetId(5L);
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
        when(presetMapper.lockOwnerUser(USER_ID)).thenReturn(USER_ID);
        ChatPresetService service = new ChatPresetService(presetMapper, conversationMapper, bindingMapper, stClient);
        return new Fixture(service, presetMapper, conversationMapper, bindingMapper, stClient);
    }

    private record Fixture(
            ChatPresetService service,
            AppChatPresetMapper presetMapper,
            AppConversationMapper conversationMapper,
            AppConversationStBindingMapper bindingMapper,
            StClient stClient
    ) {
    }
}
