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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
        when(fixture.presetMapper.listPrivateByOwner(USER_ID)).thenReturn(List.of());

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
        preset.setSortOrder(100);
        return preset;
    }

    private static Fixture fixture() {
        AppChatPresetMapper presetMapper = mock(AppChatPresetMapper.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationStBindingMapper bindingMapper = mock(AppConversationStBindingMapper.class);
        ChatPresetService service = new ChatPresetService(presetMapper, conversationMapper, bindingMapper, mock(StClient.class));
        return new Fixture(service, presetMapper, conversationMapper, bindingMapper);
    }

    private record Fixture(
            ChatPresetService service,
            AppChatPresetMapper presetMapper,
            AppConversationMapper conversationMapper,
            AppConversationStBindingMapper bindingMapper
    ) {
    }
}
