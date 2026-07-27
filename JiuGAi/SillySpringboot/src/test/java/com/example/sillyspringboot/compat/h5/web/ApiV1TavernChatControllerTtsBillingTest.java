package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatAudioSpeechService;
import com.example.sillyspringboot.chat.service.ChatAudioTranscriptionService;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.chat.service.ChatGenerationDispatcher;
import com.example.sillyspringboot.chat.service.MediaConcurrencyGate;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.compat.h5.web.dto.H5ChatPayload;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.integration.sillytavern.StModelRoutingService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ops.service.UserTtsVoiceService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiV1TavernChatControllerTtsBillingTest {

    @Mock H5ClientUidAuthService h5Auth;
    @Mock AppConversationService conversationService;
    @Mock AppChatService chatService;
    @Mock ChatGenerationDispatcher dispatcher;
    @Mock ChatAuditService auditService;
    @Mock ChatAudioTranscriptionService chatAudioTranscriptionService;
    @Mock ChatAudioSpeechService chatAudioSpeechService;
    @Mock MediaConcurrencyGate mediaConcurrencyGate;
    @Mock ChatSnapshotService snapshotService;
    @Mock AppMessageMapper messageMapper;
    @Mock H5EntitlementService entitlementService;
    @Mock H5VisitorTrialGuardService visitorTrialGuardService;
    @Mock AppFeatureSettingsService featureSettingsService;
    @Mock H5UserAiProviderService userAiProviderService;
    @Mock StModelRoutingService modelRoutingService;
    @Mock UserTtsVoiceService userTtsVoiceService;
    @Mock CharacterStudioMapper characterStudioMapper;

    private ApiV1TavernChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiV1TavernChatController(
                h5Auth,
                conversationService,
                chatService,
                dispatcher,
                auditService,
                chatAudioTranscriptionService,
                chatAudioSpeechService,
                mediaConcurrencyGate,
                snapshotService,
                messageMapper,
                entitlementService,
                visitorTrialGuardService,
                featureSettingsService,
                userAiProviderService,
                modelRoutingService,
                userTtsVoiceService
        );
        ReflectionTestUtils.setField(controller, "characterStudioMapper", characterStudioMapper);
    }

    @Test
    void inaccessibleMemberVoiceCharacterIsRejectedBeforeBillingOrMemberRead() {
        H5ChatPayload payload = new H5ChatPayload();
        payload.setClientUid("client-1");
        payload.setContent("hello");
        payload.setCharacterId(99L);
        payload.setSpeakerMemberId(11L);
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(chatService.resolveUserId("token-1")).thenReturn(7L);
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "character not found"))
                .when(entitlementService).requireCharacterVisibleToUser(99L, 7L);

        assertThatThrownBy(() -> controller.synthesizeSpeech(payload))
                .isInstanceOf(BusinessException.class)
                .hasMessage("character not found");

        verify(characterStudioMapper, never()).listMembers(99L);
        verify(entitlementService, never()).guardTts("client-1");
        verify(entitlementService, never()).recordSuccessfulTts(org.mockito.ArgumentMatchers.any());
        verify(chatAudioSpeechService, never()).synthesizeForUser(
                7L, "hello", "", "", "", null, ""
        );
    }

    @Test
    void visibleMemberVoiceCharacterStillUsesMemberVoiceNormally() {
        H5ChatPayload payload = new H5ChatPayload();
        payload.setClientUid("client-1");
        payload.setContent("hello");
        payload.setCharacterId(99L);
        payload.setSpeakerMemberId(11L);
        AppCharacterMember member = new AppCharacterMember();
        member.setId(11L);
        member.setVoiceConfigJson(
                "{\"ttsProviderSource\":\"siliconflow\",\"ttsVoiceName\":\"member-voice\"}");
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(chatService.resolveUserId("token-1")).thenReturn(7L);
        when(userAiProviderService.isCustomModeSelectedForUser(7L)).thenReturn(true);
        when(userAiProviderService.resolveActiveTtsSettingsForUser(7L)).thenReturn(
                new H5UserAiProviderService.UserTtsSettings(
                        "siliconflow", "tts-model", "", "", "key", "https://tts.example"));
        when(characterStudioMapper.listMembers(99L)).thenReturn(List.of(member));
        when(chatAudioSpeechService.synthesizeForUser(
                7L, "hello", "", "member-voice", "", null, "siliconflow"))
                .thenReturn(new ChatAudioSpeechService.AudioSpeechResult(
                        new byte[]{1}, "audio/mpeg", "tts-model", "member-voice"));

        assertThat(controller.synthesizeSpeech(payload).data())
                .containsEntry("voiceName", "member-voice");

        verify(entitlementService).requireCharacterVisibleToUser(99L, 7L);
        verify(characterStudioMapper).listMembers(99L);
        verify(entitlementService, never()).guardTts("client-1");
    }

    @Test
    void userResolutionFailureDoesNotCreateWalletCharge() {
        H5ChatPayload payload = new H5ChatPayload();
        payload.setClientUid("client-1");
        payload.setContent("hello");
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(chatService.resolveUserId("token-1"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "token expired"));

        assertThatThrownBy(() -> controller.synthesizeSpeech(payload))
                .isInstanceOf(BusinessException.class)
                .hasMessage("token expired");

        verify(entitlementService, never()).guardTts("client-1");
        verify(entitlementService, never()).recordSuccessfulTts(org.mockito.ArgumentMatchers.any());
        verify(entitlementService, never()).refundWalletConsume(org.mockito.ArgumentMatchers.any());
        verify(chatAudioSpeechService, never()).synthesizeForUser(
                7L, "hello", "", "", "", null, ""
        );
    }

    @Test
    void userByokTtsNeverConsumesPlatformWallet() {
        H5ChatPayload payload = new H5ChatPayload();
        payload.setClientUid("client-1");
        payload.setContent("hello");
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(chatService.resolveUserId("token-1")).thenReturn(7L);
        when(userAiProviderService.isCustomModeSelectedForUser(7L)).thenReturn(true);
        when(chatAudioSpeechService.synthesizeForUser(7L, "hello", "", "", "", null, ""))
                .thenReturn(new ChatAudioSpeechService.AudioSpeechResult(
                        new byte[]{1}, "audio/mpeg", "tts-model", "private-voice"));

        assertThat(controller.synthesizeSpeech(payload).data())
                .containsEntry("voiceName", "private-voice");

        verify(entitlementService, never()).guardTts("client-1");
        verify(entitlementService, never()).recordSuccessfulTts(org.mockito.ArgumentMatchers.any());
        verify(entitlementService, never()).refundWalletConsume(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allSegmentsUseOneRequestIdAndLaterFailureDoesNotRefund() {
        H5EntitlementService.AccessTicket ticket = walletTicket("tts:7:stable");
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(entitlementService.guardTts("client-1", "tts_db_44_12345678")).thenReturn(ticket);
        when(entitlementService.recordSuccessfulTts(ticket)).thenReturn(true, false);
        when(chatService.resolveUserId("token-1")).thenReturn(7L);
        when(chatAudioSpeechService.synthesizeForUser(eq(7L), eq("first"), eq(""), eq(""), eq(""), eq(null), eq("")))
                .thenReturn(new ChatAudioSpeechService.AudioSpeechResult(new byte[]{1}, "audio/mpeg", "tts-model", "alloy"));
        when(chatAudioSpeechService.synthesizeForUser(eq(7L), eq("second"), eq(""), eq(""), eq(""), eq(null), eq("")))
                .thenThrow(new BusinessException(ErrorCode.UPSTREAM_ERROR, "upstream failed"));

        H5ChatPayload first = segmentPayload("first", 0, 2);
        H5ChatPayload second = segmentPayload("second", 1, 2);
        assertThat(controller.synthesizeSpeech(first).data()).containsEntry("modelName", "tts-model");
        assertThatThrownBy(() -> controller.synthesizeSpeech(second)).hasMessage("upstream failed");

        verify(entitlementService, times(2)).guardTts("client-1", "tts_db_44_12345678");
        verify(entitlementService, times(2)).recordSuccessfulTts(ticket);
        verify(entitlementService, never()).refundWalletConsume(ticket);
    }

    @Test
    void firstSegmentFailureRefundsOnce() {
        H5EntitlementService.AccessTicket ticket = walletTicket("tts:7:first");
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(entitlementService.guardTts("client-1", "tts_db_44_12345678")).thenReturn(ticket);
        when(entitlementService.recordSuccessfulTts(ticket)).thenReturn(true);
        when(chatService.resolveUserId("token-1")).thenReturn(7L);
        when(chatAudioSpeechService.synthesizeForUser(eq(7L), eq("first"), eq(""), eq(""), eq(""), eq(null), eq("")))
                .thenThrow(new BusinessException(ErrorCode.UPSTREAM_ERROR, "upstream failed"));

        assertThatThrownBy(() -> controller.synthesizeSpeech(segmentPayload("first", 0, 2)))
                .hasMessage("upstream failed");

        verify(entitlementService).refundWalletConsume(ticket);
    }

    @Test
    void globalPrivateVoiceDoesNotOverrideCharacterOrMemberPublicVoice() {
        assertThat(ApiV1TavernChatController.shouldUseGlobalPrivateVoice(null, "bella", "", "", ""))
                .isFalse();
        assertThat(ApiV1TavernChatController.shouldUseGlobalPrivateVoice(null, "", "template-a", "", ""))
                .isFalse();
        assertThat(ApiV1TavernChatController.shouldUseGlobalPrivateVoice(null, "", "", "alex", ""))
                .isFalse();
        assertThat(ApiV1TavernChatController.shouldUseGlobalPrivateVoice(null, "", "", "", "template-b"))
                .isFalse();
        assertThat(ApiV1TavernChatController.shouldUseGlobalPrivateVoice(null, "", "", "", ""))
                .isTrue();
        assertThat(ApiV1TavernChatController.shouldUseGlobalPrivateVoice(81L, "", "", "", ""))
                .isFalse();
    }

    @Test
    void explicitChatPublicVoiceOverridesStoredPrivateBindingLookup() {
        assertThat(ApiV1TavernChatController.shouldResolveSpecificPrivateVoice(null, "bella", ""))
                .isFalse();
        assertThat(ApiV1TavernChatController.shouldResolveSpecificPrivateVoice(null, "", "template-a"))
                .isFalse();
        assertThat(ApiV1TavernChatController.shouldResolveSpecificPrivateVoice(null, "", ""))
                .isTrue();
    }

    @Test
    void roleOverrideOnlyAppliesToTheByokProviderThatSavedIt() {
        H5UserAiProviderService.UserTtsSettings settings =
                new H5UserAiProviderService.UserTtsSettings(
                        "siliconflow", "tts-model", "", "", "key", "https://api.siliconflow.cn/v1");

        assertThat(ApiV1TavernChatController.ttsOverrideScopeMatchesProvider("siliconflow", settings))
                .isTrue();
        assertThat(ApiV1TavernChatController.ttsOverrideScopeMatchesProvider("SILICONFLOW", settings))
                .isTrue();
        assertThat(ApiV1TavernChatController.ttsOverrideScopeMatchesProvider("openai", settings))
                .isFalse();
        assertThat(ApiV1TavernChatController.ttsOverrideScopeMatchesProvider("", settings))
                .isFalse();
        assertThat(ApiV1TavernChatController.ttsOverrideScopeMatchesProvider("siliconflow", null))
                .isFalse();
    }

    private static H5ChatPayload segmentPayload(String content, int index, int count) {
        H5ChatPayload payload = new H5ChatPayload();
        payload.setClientUid("client-1");
        payload.setContent(content);
        payload.setTtsRequestId("tts_db_44_12345678");
        payload.setTtsSegmentIndex(index);
        payload.setTtsSegmentCount(count);
        return payload;
    }

    private static H5EntitlementService.AccessTicket walletTicket(String bizRef) {
        return new H5EntitlementService.AccessTicket(
                7L, "client-1", false, 0, H5EntitlementService.QuotaBucket.OFFICIAL_CHAT,
                null, "TTS", true, 10, 2, bizRef
        );
    }
}
