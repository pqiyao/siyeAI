package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.dto.AppChatStreamRequest;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatRuntimeRegistry;
import com.example.sillyspringboot.chat.service.AppChatCompatibilityService;
import com.example.sillyspringboot.chat.service.AppChatFrontendBridgeService;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.chat.service.ChatConcurrencyGate;
import com.example.sillyspringboot.chat.service.ChatImageContentService;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ProfileMapper;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAttachService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import com.example.sillyspringboot.conversation.service.ConversationBranchService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.ops.service.ChatPresetService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AppChatServiceMemoryInjectionTest {

    private StAdapter serviceTestStAdapter;
    private ChatPresetService serviceTestChatPresetService;
    private ChatImageContentService serviceTestChatImageContentService;
    private ChatSnapshotService serviceTestSnapshotService;

    @Test
    void streamGenerate_shouldExcludeOnlyCurrentUserMessageRefFromSnapshot() {
        long conversationId = 129L;
        AppChatService service = buildService(conversationId);

        AppChatStreamRequest request = new AppChatStreamRequest();
        request.setConversationId(conversationId);
        request.setUserMessage("hello once");
        request.setClientMessageId("client-current-message");

        service.streamGenerate(request, "token", "root:456", chunk -> {}, new StStreamControl());

        verify(serviceTestSnapshotService).saveSnapshotFromDb(conversationId, 99L, 800, "root:456");
        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        assertThat(requestCaptor.getValue().stMessageRef()).isEqualTo("root:456");
        assertThat(requestCaptor.getValue().userMessage()).isEqualTo("hello once");
    }

    @Test
    void syncAssistantReplyToStReportsWhetherTheRuntimeWriteCompleted() {
        long conversationId = 130L;
        AppChatService service = buildService(conversationId);

        assertThat(service.syncAssistantReplyToSt(conversationId, "root:457", "assistant reply", "token"))
                .isTrue();
        assertThat(service.syncAssistantReplyToSt(conversationId, "root:458", "  ", "token"))
                .isFalse();
        assertThat(service.syncAssistantReplyToSt(conversationId, "root:459", "", "token", true))
                .isTrue();

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter, times(1)).appendAssistantMessage(requestCaptor.capture(), eq("assistant reply"));
        assertThat(requestCaptor.getValue().stMessageRef()).isEqualTo("root:457");
        verify(serviceTestStAdapter).appendAssistantMessage(
                any(ChatGenerateRequest.class),
                eq(""),
                eq(true)
        );
    }

    @Test
    void normalizeAssistantOutputReturnsTheCanonicalStRegexText() {
        long conversationId = 131L;
        AppChatService service = buildService(conversationId);
        when(serviceTestStAdapter.applyAssistantOutputRegex(any(ChatGenerateRequest.class), eq("raw <status>x</status>")))
                .thenReturn("\n raw \t");

        AppChatService.AssistantOutputNormalization result =
                service.normalizeAssistantOutput(conversationId, " raw <status>x</status> ", "token");

        assertThat(result.content()).isEqualTo("raw");
        assertThat(result.finalized()).isTrue();
        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter).applyAssistantOutputRegex(
                requestCaptor.capture(),
                eq("raw <status>x</status>")
        );
        assertThat(requestCaptor.getValue().stAvatarUrl()).isEqualTo("avatar.png");
    }

    @Test
    void normalizeAssistantOutputKeepsFailOpenTextCanonicalWhenStRegexCallFails() {
        long conversationId = 132L;
        AppChatService service = buildService(conversationId);
        when(serviceTestStAdapter.applyAssistantOutputRegex(any(ChatGenerateRequest.class), eq("raw output")))
                .thenThrow(new RuntimeException("ST unavailable"));

        AppChatService.AssistantOutputNormalization result =
                service.normalizeAssistantOutput(conversationId, "raw output", "token");

        assertThat(result.content()).isEqualTo("raw output");
        assertThat(result.finalized()).isTrue();
        assertThat(service.syncAssistantReplyToSt(
                conversationId,
                "root:459",
                result.content(),
                "token",
                result.finalized()
        )).isTrue();
        verify(serviceTestStAdapter).appendAssistantMessage(
                any(ChatGenerateRequest.class),
                eq("raw output"),
                eq(true)
        );
    }

    @Test
    void normalizeAssistantOutputDistinguishesSuccessfulEmptyTextFromFailure() {
        long conversationId = 133L;
        AppChatService service = buildService(conversationId);
        when(serviceTestStAdapter.applyAssistantOutputRegex(any(ChatGenerateRequest.class), eq("internal only")))
                .thenReturn("");

        AppChatService.AssistantOutputNormalization result =
                service.normalizeAssistantOutput(conversationId, "internal only", "token");

        assertThat(result.content()).isEmpty();
        assertThat(result.finalized()).isTrue();
        assertThat(service.syncAssistantReplyToSt(
                conversationId,
                "root:460",
                result.content(),
                "token",
                result.finalized()
        )).isTrue();
        verify(serviceTestStAdapter).appendAssistantMessage(
                any(ChatGenerateRequest.class),
                eq(""),
                eq(true)
        );
    }

    @Test
    void streamGenerate_shouldKeepBaseWorldNamesAttachMemoryWorldbookAndSkipDuplicateTailMemoryPrompt() {
        long conversationId = 123L;
        AppChatService service = buildService(conversationId);

        AppChatStreamRequest request = new AppChatStreamRequest();
        request.setConversationId(conversationId);
        request.setUserMessage("hello");
        request.setClientMessageId("client-1");

        service.streamGenerate(request, "token", chunk -> {}, new StStreamControl());

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        ChatGenerateRequest captured = requestCaptor.getValue();
        assertThat(captured.messages()).isEmpty();
        assertThat(captured.stWorldNames()).containsExactly("base_world", "memory_world");
        assertThat(captured.tailSystemPrompt()).isNullOrEmpty();
    }

    @Test
    void streamGenerate_shouldInferExistingSameNameWorldbookWhenNoBindingWorldNamesExist() {
        long conversationId = 127L;
        String worldName = "\u521b\u4e16\u56de\u5eca1.5";
        AppChatService service = buildService(
                conversationId,
                null,
                "Different Card Title",
                worldName + ".png",
                List.of(worldName)
        );

        AppChatStreamRequest request = new AppChatStreamRequest();
        request.setConversationId(conversationId);
        request.setUserMessage("hello");
        request.setClientMessageId("client-same-name-worldbook");

        service.streamGenerate(request, "token", chunk -> {}, new StStreamControl());

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        ChatGenerateRequest captured = requestCaptor.getValue();
        assertThat(captured.messages()).isEmpty();
        assertThat(captured.stWorldNames()).containsExactly(worldName, "memory_world");
        assertThat(captured.tailSystemPrompt()).isNullOrEmpty();
    }

    @Test
    void streamGenerate_shouldRespectExplicitEmptyConversationWorldbookBinding() {
        long conversationId = 128L;
        String worldName = "\u521b\u4e16\u56de\u5eca1.5";
        AppChatService service = buildService(
                conversationId,
                "[]",
                "Different Card Title",
                worldName + ".png",
                List.of(worldName)
        );

        AppChatStreamRequest request = new AppChatStreamRequest();
        request.setConversationId(conversationId);
        request.setUserMessage("hello");
        request.setClientMessageId("client-explicit-empty-worldbook");

        service.streamGenerate(request, "token", chunk -> {}, new StStreamControl());

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        ChatGenerateRequest captured = requestCaptor.getValue();
        assertThat(captured.messages()).isEmpty();
        assertThat(captured.stWorldNames()).containsExactly("memory_world");
        assertThat(captured.tailSystemPrompt()).isNullOrEmpty();
    }

    @Test
    void suggestReplies_shouldUseMemoryWorldNamesWithoutDuplicatingTailMemoryPrompt() {
        long conversationId = 124L;
        AppChatService service = buildService(conversationId);

        List<String> suggestions = service.suggestReplies(conversationId, "token", "draft");

        assertThat(suggestions).containsExactly("ok", "sure", "continue", "here");

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        ChatGenerateRequest captured = requestCaptor.getValue();
        assertThat(captured.mode()).isEqualTo("reply_suggestions");
        assertThat(captured.tailSystemPrompt()).isNullOrEmpty();
        assertThat(captured.stWorldNames()).containsExactly("base_world", "memory_world");
        assertThat(captured.messages())
                .extracting("role")
                .containsExactly("user", "assistant", "user");
        assertThat(captured.messages().get(captured.messages().size() - 1).content())
                .doesNotContain("Relevant conversation memory:")
                .contains("SillyTavern-style user impersonation prompt:");
    }

    @Test
    void streamGenerate_expressionShouldStayOnRuntimePathAndUseBoundRuntimePresetBundle() {
        long conversationId = 125L;
        AppChatService service = buildService(conversationId);
        String runtimePresetBundle = "{\"generation\":{\"temperature\":0.7,\"prompts\":[{\"identifier\":\"main\",\"content\":\"preset main\"}]}}";
        when(serviceTestChatPresetService.resolveRuntimePresetBundle(any())).thenReturn(runtimePresetBundle);

        AppChatStreamRequest request = new AppChatStreamRequest();
        request.setConversationId(conversationId);
        request.setUserMessage("sticker");
        request.setAttachmentMode("expression");
        request.setAttachmentHint("smile");
        request.setExpressionHints(List.of("smile", "pout"));
        request.setClientMessageId("client-expression");

        service.streamGenerate(request, "token", chunk -> {}, new StStreamControl());

        verify(serviceTestStAdapter, never()).buildRuntimeMessages(any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        ChatGenerateRequest captured = requestCaptor.getValue();
        assertThat(captured.runtimePresetBundle()).isEqualTo(runtimePresetBundle);
        assertThat(captured.stWorldNames()).containsExactly("base_world", "memory_world");
        assertThat(captured.messages()).isEmpty();
        assertThat(captured.userMessage()).contains("local sticker/expression").contains("smile");
        assertThat(captured.tailSystemPrompt()).contains("[[expr:KEYWORD]]").contains("smile").contains("pout");
    }

    @Test
    void streamGenerate_photoSummaryShouldStayOnRuntimePathForMainChat() {
        long conversationId = 126L;
        AppChatService service = buildService(conversationId);
        List<String> imageUrls = List.of("https://example.test/image.png");
        when(serviceTestChatImageContentService.resolveInlineDataUrls(imageUrls))
                .thenReturn(List.of("data:image/png;base64,AAAA"));

        AppChatStreamRequest request = new AppChatStreamRequest();
        request.setConversationId(conversationId);
        request.setUserMessage("look at this");
        request.setImageUrls(imageUrls);
        request.setClientMessageId("client-photo");

        service.streamGenerate(request, "token", chunk -> {}, new StStreamControl());

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(serviceTestStAdapter, times(2)).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        List<ChatGenerateRequest> captured = requestCaptor.getAllValues();
        ChatGenerateRequest summary = captured.stream()
                .filter(item -> "vision_summary".equals(item.mode()))
                .findFirst()
                .orElseThrow();
        ChatGenerateRequest main = captured.stream()
                .filter(item -> "generate".equals(item.mode()))
                .findFirst()
                .orElseThrow();

        assertThat(summary.messages()).isNotEmpty();
        assertThat(main.messages()).isEmpty();
        assertThat(main.userMessage()).contains("look at this").contains("image summary");
        assertThat(main.stWorldNames()).containsExactly("base_world", "memory_world");
    }

    private AppChatService buildService(long conversationId) {
        return buildService(conversationId, "[\"base_world\"]", "Character", "avatar.png", List.of());
    }

    private AppChatService buildService(
            long conversationId,
            String bindingWorldNamesJson,
            String characterName,
            String stAvatarUrl,
            List<String> inferredWorldNames
    ) {
        long userId = 77L;
        long characterId = 88L;
        long branchId = 99L;

        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationStBindingMapper bindingMapper = mock(AppConversationStBindingMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppGenerationTaskMapper taskMapper = mock(AppGenerationTaskMapper.class);
        ChatAuditService chatAuditService = mock(ChatAuditService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        StAdapter stAdapter = mock(StAdapter.class);
        ChatConcurrencyGate gate = mock(ChatConcurrencyGate.class);
        AppChatRuntimeRegistry runtimeRegistry = mock(AppChatRuntimeRegistry.class);
        ChatSnapshotService snapshotService = mock(ChatSnapshotService.class);
        H5MyCharacterMapper h5MyCharacterMapper = mock(H5MyCharacterMapper.class);
        AppCharacterMapper characterMapper = mock(AppCharacterMapper.class);
        AppH5ProfileMapper h5ProfileMapper = mock(AppH5ProfileMapper.class);
        H5UserAiProviderService userAiProviderService = mock(H5UserAiProviderService.class);
        ChatImageContentService chatImageContentService = mock(ChatImageContentService.class);
        ConversationMemoryAttachService memoryAttachService = mock(ConversationMemoryAttachService.class);
        ConversationMemoryAutoRefreshService memoryAutoRefreshService = mock(ConversationMemoryAutoRefreshService.class);
        ConversationBranchService branchService = mock(ConversationBranchService.class);
        StWorldbookCatalogService worldbookCatalogService = mock(StWorldbookCatalogService.class);
        ChatPresetService chatPresetService = mock(ChatPresetService.class);

        AppUser user = new AppUser();
        user.setId(userId);
        user.setUsername("yao");
        when(tokenService.validateAndLoadUser("token")).thenReturn(user);

        AppConversation conversation = new AppConversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setCharacterId(characterId);
        when(conversationMapper.findByIdForUser(conversationId, userId)).thenReturn(conversation);
        AppConversationBranch branch = new AppConversationBranch();
        branch.setId(branchId);
        branch.setConversationId(conversationId);
        branch.setUserId(userId);
        when(branchService.requireActiveBranch(conversation)).thenReturn(branch);

        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setConversationId(conversationId);
        binding.setStAvatarUrl(stAvatarUrl);
        binding.setStChatFileName("chat.jsonl");
        binding.setStWorldNamesJson(bindingWorldNamesJson);
        when(bindingMapper.findByConversationId(conversationId)).thenReturn(binding);

        AppCharacter character = new AppCharacter();
        character.setId(characterId);
        character.setName(characterName);
        character.setStAvatarUrl(stAvatarUrl);
        when(characterMapper.findById(characterId)).thenReturn(character);

        when(worldbookCatalogService.normalizeAndFilterAvailableWorldNames(any()))
                .thenReturn(inferredWorldNames == null ? List.of() : inferredWorldNames);

        when(chatImageContentService.resolveInlineDataUrls(null)).thenReturn(List.of());
        when(userAiProviderService.resolveActiveOverrideForUser(userId)).thenReturn(new UserModelOverride(
                "openrouter",
                "openai/gpt-4o-mini",
                "openai/gpt-4o-mini",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "test-key",
                ""
        ));
        when(memoryAttachService.attachMemoryWorldbookIfAvailable(eq(conversationId), eq(branchId), any()))
                .thenAnswer(invocation -> {
                    List<String> base = invocation.getArgument(2);
                    java.util.ArrayList<String> out = new java.util.ArrayList<>();
                    if (base != null) {
                        out.addAll(base);
                    }
                    out.add("memory_world");
                    return List.copyOf(out);
                });
        when(memoryAttachService.buildTailMemoryPromptFallbackIfWorldbookUnavailable(conversationId, branchId))
                .thenReturn("");
        when(memoryAttachService.buildTailMemoryPromptIfAvailable(conversationId, branchId))
                .thenReturn("""
                        Long-term memory for this conversation:
                        - User likes being called captain.
                        Use these memories naturally only when relevant. Keep the character's original personality and setting.
                        """.trim());
        when(stAdapter.buildRuntimeMessages(
                "avatar.png",
                "chat.jsonl",
                "yao",
                "Character",
                List.of(),
                List.of("base_world", "memory_world"),
                null
        )).thenReturn(List.of(
                Map.of("role", "system", "content", "Write Character's next reply in a fictional chat between Character and yao."),
                Map.of("role", "user", "content", "hello"),
                Map.of("role", "assistant", "content", "hi")
        ));
        doAnswer(invocation -> {
            ChatGenerateRequest request = invocation.getArgument(0);
            Consumer<ChatGenerateChunk> onChunk = invocation.getArgument(1);
            if ("vision_summary".equals(request.mode())) {
                onChunk.accept(new ChatGenerateChunk(conversationId, request.clientMessageId(), 0,
                        "image summary", false, null, null));
                onChunk.accept(new ChatGenerateChunk(conversationId, request.clientMessageId(), 1, "", true, null, null));
                return null;
            }
            onChunk.accept(new ChatGenerateChunk(conversationId, request.clientMessageId(), 0,
                    "{\"suggestions\":[\"ok\",\"sure\",\"continue\",\"here\"]}", false, null, null));
            onChunk.accept(new ChatGenerateChunk(conversationId, request.clientMessageId(), 1, "", true, null, null));
            return null;
        }).when(stAdapter).streamGenerateAssistantReply(any(ChatGenerateRequest.class), any(), any(StStreamControl.class));

        AppChatService service = new AppChatService(
                conversationMapper,
                bindingMapper,
                messageMapper,
                taskMapper,
                chatAuditService,
                tokenService,
                stAdapter,
                gate,
                runtimeRegistry,
                new AppChatProperties(),
                snapshotService,
                h5MyCharacterMapper,
                characterMapper,
                h5ProfileMapper,
                userAiProviderService,
                chatImageContentService,
                memoryAttachService,
                memoryAutoRefreshService,
                branchService,
                worldbookCatalogService,
                chatPresetService,
                mock(AppChatCompatibilityService.class),
                mock(AppChatFrontendBridgeService.class),
                mock(com.example.sillyspringboot.ops.service.H5EntitlementService.class)
        );
        serviceTestStAdapter = stAdapter;
        serviceTestChatPresetService = chatPresetService;
        serviceTestChatImageContentService = chatImageContentService;
        serviceTestSnapshotService = snapshotService;
        return service;
    }
}
