package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.chat.service.MediaConcurrencyGate;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoice;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceBinding;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceBindingMapper;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTtsVoiceServiceTest {

    private AppUserTtsVoiceMapper voiceMapper;
    private AppUserTtsVoiceBindingMapper bindingMapper;
    private H5UserAiProviderService userAiProviderService;
    private EntitlementPolicyService entitlementPolicyService;
    private TtsVoiceProvisionService provisionService;
    private UserTtsVoiceService service;

    @BeforeEach
    void setUp() {
        voiceMapper = mock(AppUserTtsVoiceMapper.class);
        bindingMapper = mock(AppUserTtsVoiceBindingMapper.class);
        userAiProviderService = mock(H5UserAiProviderService.class);
        entitlementPolicyService = mock(EntitlementPolicyService.class);
        provisionService = mock(TtsVoiceProvisionService.class);
        service = new UserTtsVoiceService(
                voiceMapper,
                bindingMapper,
                mock(AppH5UserProfileExtMapper.class),
                entitlementPolicyService,
                userAiProviderService,
                provisionService,
                mock(UserTtsVoiceReservationService.class),
                mock(MediaConcurrencyGate.class));
    }

    @Test
    void createRejectsOfficialModeBeforeCallingProvisioner() {
        enabledPolicy();
        when(userAiProviderService.resolveActiveTtsSettingsForUser(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(
                7L, "voice-request-001", "测试音色", "这是一段测试台词", 6000, wavFile()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自己的 TTS API");
        verify(provisionService, never()).provisionUserVoice(
                anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRejectsNonSiliconFlowByok() {
        enabledPolicy();
        when(userAiProviderService.resolveActiveTtsSettingsForUser(7L)).thenReturn(
                settings("openai", "tts-1", "key-a", "https://api.openai.com/v1"));

        assertThatThrownBy(() -> service.create(
                7L, "voice-request-002", "测试音色", "这是一段测试台词", 6000, wavFile()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持硅基流动");
        verify(provisionService, never()).provisionUserVoice(
                anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void runtimeRejectsChangedKeyAndAdminDisabledVoice() {
        TtsVoiceProvisionService.TtsRuntimeContext original = runtime("key-a");
        AppUserTtsVoice voice = readyVoice(81L, 7L, original);
        when(voiceMapper.findOwnedById(7L, 81L)).thenReturn(voice);

        assertThatThrownBy(() -> service.resolveForRuntime(7L, 81L, runtime("key-b")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key");

        voice.setDisabled(true);
        assertThatThrownBy(() -> service.resolveForRuntime(7L, 81L, original))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可用");
    }

    @Test
    void runtimeRejectsVoiceOwnedByAnotherUser() {
        when(voiceMapper.findOwnedById(7L, 92L)).thenReturn(null);

        assertThatThrownBy(() -> service.resolveForRuntime(7L, 92L, runtime("key-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void bindingPriorityIsMemberThenCharacterThenGlobal() {
        when(bindingMapper.find(7L, "MEMBER", 11L, 22L)).thenReturn(binding(101L));
        when(bindingMapper.find(7L, "CHARACTER", 11L, 0L)).thenReturn(binding(102L));
        when(bindingMapper.find(7L, "GLOBAL", 0L, 0L)).thenReturn(binding(103L));

        assertThat(service.resolveBoundVoiceId(7L, 11L, 22L)).isEqualTo(101L);

        when(bindingMapper.find(7L, "MEMBER", 11L, 22L)).thenReturn(null);
        assertThat(service.resolveBoundVoiceId(7L, 11L, 22L)).isEqualTo(102L);

        when(bindingMapper.find(7L, "CHARACTER", 11L, 0L)).thenReturn(null);
        assertThat(service.resolveBoundVoiceId(7L, 11L, 22L)).isEqualTo(103L);
    }

    @Test
    void saveBindingRejectsOfficialModeAndChangedConfiguration() {
        TtsVoiceProvisionService.TtsRuntimeContext original = runtime("key-a");
        when(voiceMapper.findOwnedById(7L, 81L)).thenReturn(readyVoice(81L, 7L, original));
        when(userAiProviderService.resolveActiveTtsSettingsForUser(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveBinding(7L, "GLOBAL", 0L, 0L, 81L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自己的 TTS API");
        verify(bindingMapper, never()).insert(org.mockito.ArgumentMatchers.any());

        when(userAiProviderService.resolveActiveTtsSettingsForUser(7L)).thenReturn(
                settings("siliconflow", original.modelName(), "key-b", original.baseUrl()));
        assertThatThrownBy(() -> service.saveBinding(7L, "GLOBAL", 0L, 0L, 81L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void removeClearsAllBindingsBeforeSoftDelete() {
        when(voiceMapper.findOwnedById(7L, 81L)).thenReturn(readyVoice(81L, 7L, runtime("key-a")));

        service.remove(7L, 81L);

        verify(bindingMapper).deleteByVoiceId(81L);
        verify(voiceMapper).softDelete(7L, 81L);
    }

    private void enabledPolicy() {
        EntitlementPolicy policy = new EntitlementPolicy();
        policy.setUserVoiceCreationEnabled(true);
        when(entitlementPolicyService.getPolicy()).thenReturn(policy);
    }

    private static H5UserAiProviderService.UserTtsSettings settings(
            String provider, String model, String key, String baseUrl) {
        return new H5UserAiProviderService.UserTtsSettings(provider, model, "", "", key, baseUrl);
    }

    private static TtsVoiceProvisionService.TtsRuntimeContext runtime(String key) {
        return new TtsVoiceProvisionService.TtsRuntimeContext(
                true, "siliconflow", "https://api.siliconflow.cn/v1", key, "FunAudioLLM/CosyVoice2-0.5B");
    }

    private static AppUserTtsVoice readyVoice(
            long id,
            long userId,
            TtsVoiceProvisionService.TtsRuntimeContext runtime) {
        AppUserTtsVoice voice = new AppUserTtsVoice();
        voice.setId(id);
        voice.setUserId(userId);
        voice.setDisplayName("测试音色");
        voice.setProviderSource("siliconflow");
        voice.setModelName(runtime.modelName());
        voice.setVoiceUri("speech:user-voice");
        voice.setStatus("READY");
        voice.setDisabled(false);
        voice.setConfigFingerprint(TtsVoiceProvisionService.buildRuntimeFingerprint(runtime, runtime.modelName()));
        return voice;
    }

    private static AppUserTtsVoiceBinding binding(long voiceId) {
        AppUserTtsVoiceBinding row = new AppUserTtsVoiceBinding();
        row.setVoiceId(voiceId);
        return row;
    }

    private static MockMultipartFile wavFile() {
        byte[] bytes = new byte[48];
        bytes[0] = 'R';
        bytes[1] = 'I';
        bytes[2] = 'F';
        bytes[3] = 'F';
        bytes[8] = 'W';
        bytes[9] = 'A';
        bytes[10] = 'V';
        bytes[11] = 'E';
        return new MockMultipartFile("file", "sample.wav", "audio/wav", bytes);
    }
}
