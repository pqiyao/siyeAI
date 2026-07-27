package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.chat.service.MediaConcurrencyGate;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
    private H5EntitlementService entitlementService;
    private CharacterStudioMapper characterStudioMapper;
    private UserTtsVoiceService service;

    @BeforeEach
    void setUp() {
        voiceMapper = mock(AppUserTtsVoiceMapper.class);
        bindingMapper = mock(AppUserTtsVoiceBindingMapper.class);
        userAiProviderService = mock(H5UserAiProviderService.class);
        entitlementPolicyService = mock(EntitlementPolicyService.class);
        provisionService = mock(TtsVoiceProvisionService.class);
        entitlementService = mock(H5EntitlementService.class);
        characterStudioMapper = mock(CharacterStudioMapper.class);
        service = new UserTtsVoiceService(
                voiceMapper,
                bindingMapper,
                mock(AppH5UserProfileExtMapper.class),
                entitlementPolicyService,
                userAiProviderService,
                provisionService,
                mock(UserTtsVoiceReservationService.class),
                mock(MediaConcurrencyGate.class),
                entitlementService,
                characterStudioMapper);
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
    void createUsesActualAudioDurationInsteadOfClientClaim() {
        assertThatThrownBy(() -> service.create(
                7L, "voice-request-long-001", "测试音色", "这是一段测试台词", 6000, wavFile(61)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5 到 60 秒");
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

    @Test
    void overviewRecoversStaleProvisioningBeforeCountingQuota() {
        enabledPolicy();

        service.overview(7L);

        verify(voiceMapper).failStaleProvisioningByUserId(
                eq(7L), any(java.time.LocalDateTime.class), contains("3 分钟"));
    }

    @Test
    void adminCanFinishOnlyActiveProvisioningTask() {
        AppUserTtsVoice voice = new AppUserTtsVoice();
        voice.setId(81L);
        voice.setStatus("PROVISIONING");
        when(voiceMapper.findById(81L)).thenReturn(voice);
        when(voiceMapper.failProvisioningById(eq(81L), contains("管理员"))).thenReturn(1);

        service.finishAdminProvisioning(81L);

        verify(voiceMapper).failProvisioningById(eq(81L), contains("管理员"));
    }

    @Test
    void memberBindingRequiresMemberToBelongToVisibleCharacter() {
        AppCharacterMember otherMember = new AppCharacterMember();
        otherMember.setId(23L);
        when(characterStudioMapper.listMembers(11L)).thenReturn(java.util.List.of(otherMember));

        assertThatThrownBy(() -> service.saveBinding(7L, "MEMBER", 11L, 22L, 81L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前角色");

        verify(entitlementService).requireCharacterVisibleToUser(11L, 7L);
        verify(voiceMapper, never()).findOwnedById(7L, 81L);
    }

    @Test
    void validMemberBindingKeepsByokAndOwnershipChecks() {
        TtsVoiceProvisionService.TtsRuntimeContext original = runtime("key-a");
        AppCharacterMember member = new AppCharacterMember();
        member.setId(22L);
        when(characterStudioMapper.listMembers(11L)).thenReturn(java.util.List.of(member));
        when(voiceMapper.findOwnedById(7L, 81L)).thenReturn(readyVoice(81L, 7L, original));
        when(userAiProviderService.resolveActiveTtsSettingsForUser(7L)).thenReturn(
                settings("siliconflow", original.modelName(), "key-a", original.baseUrl()));
        when(bindingMapper.updateVoice(any(AppUserTtsVoiceBinding.class))).thenReturn(1);

        service.saveBinding(7L, "MEMBER", 11L, 22L, 81L);

        verify(entitlementService).requireCharacterVisibleToUser(11L, 7L);
        verify(bindingMapper).updateVoice(any(AppUserTtsVoiceBinding.class));
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
        return wavFile(6);
    }

    private static MockMultipartFile wavFile(int seconds) {
        int dataSize = 16_000 * 2 * seconds;
        byte[] bytes = new byte[44 + dataSize];
        ascii(bytes, 0, "RIFF");
        le32(bytes, 4, bytes.length - 8);
        ascii(bytes, 8, "WAVEfmt ");
        le32(bytes, 16, 16);
        bytes[20] = 1;
        bytes[22] = 1;
        le32(bytes, 24, 16_000);
        le32(bytes, 28, 32_000);
        bytes[32] = 2;
        bytes[34] = 16;
        ascii(bytes, 36, "data");
        le32(bytes, 40, dataSize);
        return new MockMultipartFile("file", "sample.wav", "audio/wav", bytes);
    }

    private static void ascii(byte[] target, int offset, String value) {
        for (int i = 0; i < value.length(); i++) target[offset + i] = (byte) value.charAt(i);
    }

    private static void le32(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }
}
