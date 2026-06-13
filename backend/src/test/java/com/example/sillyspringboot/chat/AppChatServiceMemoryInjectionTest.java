package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.dto.AppChatContinueRequest;
import com.example.sillyspringboot.chat.dto.AppChatStreamRequest;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatRuntimeRegistry;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.chat.service.ChatConcurrencyGate;
import com.example.sillyspringboot.chat.service.ChatImageContentService;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ProfileMapper;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAttachService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AppChatServiceMemoryInjectionTest {

    @Test
    void streamGenerate_shouldKeepBaseWorldNamesAndAppendTailMemoryPrompt() {
        TestContext ctx = new TestContext();
        when(ctx.chatImageContentService.resolveInlineDataUrls(null)).thenReturn(List.of());
        when(ctx.memoryAttachService.buildTailMemoryPromptIfAvailable(ctx.conversationId))
                .thenReturn("Long-term memory for this conversation:\n- 用户希望角色称呼他为哥哥。");

        AppChatService service = ctx.createService();
        AppChatStreamRequest request = new AppChatStreamRequest();
        request.setConversationId(ctx.conversationId);
        request.setUserMessage("hello");
        request.setClientMessageId("client-1");

        service.streamGenerate(request, "token", chunk -> {}, new StStreamControl());

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(ctx.stAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        ChatGenerateRequest actual = requestCaptor.getValue();
        assertThat(actual.stWorldNames()).containsExactly("base_world");
        assertThat(actual.tailSystemPrompt()).contains("Long-term memory for this conversation:");
        assertThat(actual.tailSystemPrompt()).contains("用户希望角色称呼他为哥哥。");
    }

    @Test
    void streamContinue_shouldPassTailMemoryPromptToSt() {
        TestContext ctx = new TestContext();
        when(ctx.memoryAttachService.buildTailMemoryPromptIfAvailable(ctx.conversationId))
                .thenReturn("Long-term memory for this conversation:\n- 用户和角色已经确认恋人关系。");

        AppChatService service = ctx.createService();
        AppChatContinueRequest request = new AppChatContinueRequest();
        request.setConversationId(ctx.conversationId);
        request.setClientMessageId("client-continue");
        request.setTargetMessageId("db_99");

        service.streamContinue(request, "token", chunk -> {}, new StStreamControl());

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(ctx.stAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        ChatGenerateRequest actual = requestCaptor.getValue();
        assertThat(actual.stWorldNames()).containsExactly("base_world");
        assertThat(actual.tailSystemPrompt()).contains("用户和角色已经确认恋人关系。");
    }

    @Test
    void suggestReplies_shouldNotInjectConversationMemory() {
        TestContext ctx = new TestContext();
        when(ctx.memoryAttachService.buildTailMemoryPromptIfAvailable(ctx.conversationId))
                .thenReturn("Long-term memory for this conversation:\n- 不应进入建议回复。");
        when(ctx.stAdapter.buildRuntimeMessages(
                eq("avatar.png"),
                eq("chat.jsonl"),
                eq("yao"),
                eq("Character"),
                eq(List.of()),
                eq(List.of("base_world"))
        )).thenReturn(List.of(
                Map.of("role", "system", "content", "Write Character's next reply in a fictional chat between Character and yao."),
                Map.of("role", "system", "content", "[Start a new Chat]"),
                Map.of("role", "assistant", "content", "你好")
        ));

        AppChatService service = ctx.createService();
        assertThatThrownBy(() -> service.suggestReplies(ctx.conversationId, "token", ""))
                .isInstanceOf(BusinessException.class);

        ArgumentCaptor<ChatGenerateRequest> requestCaptor = ArgumentCaptor.forClass(ChatGenerateRequest.class);
        verify(ctx.stAdapter).streamGenerateAssistantReply(requestCaptor.capture(), any(), any(StStreamControl.class));
        ChatGenerateRequest actual = requestCaptor.getValue();
        assertThat(actual.stWorldNames()).containsExactly("base_world");
        assertThat(actual.tailSystemPrompt()).isBlank();
    }

    private static final class TestContext {
        private final long conversationId = 123L;
        private final long userId = 77L;
        private final long characterId = 88L;

        private final AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        private final AppConversationStBindingMapper bindingMapper = mock(AppConversationStBindingMapper.class);
        private final AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        private final AppGenerationTaskMapper taskMapper = mock(AppGenerationTaskMapper.class);
        private final ChatAuditService chatAuditService = mock(ChatAuditService.class);
        private final AppTokenService tokenService = mock(AppTokenService.class);
        private final StAdapter stAdapter = mock(StAdapter.class);
        private final ChatConcurrencyGate gate = mock(ChatConcurrencyGate.class);
        private final AppChatRuntimeRegistry runtimeRegistry = mock(AppChatRuntimeRegistry.class);
        private final ChatSnapshotService snapshotService = mock(ChatSnapshotService.class);
        private final H5MyCharacterMapper h5MyCharacterMapper = mock(H5MyCharacterMapper.class);
        private final AppCharacterMapper characterMapper = mock(AppCharacterMapper.class);
        private final AppH5ProfileMapper h5ProfileMapper = mock(AppH5ProfileMapper.class);
        private final H5UserAiProviderService userAiProviderService = mock(H5UserAiProviderService.class);
        private final ChatImageContentService chatImageContentService = mock(ChatImageContentService.class);
        private final ConversationMemoryAttachService memoryAttachService = mock(ConversationMemoryAttachService.class);
        private final ConversationMemoryAutoRefreshService memoryAutoRefreshService = mock(ConversationMemoryAutoRefreshService.class);

        private TestContext() {
            AppUser user = new AppUser();
            user.setId(userId);
            user.setUsername("yao");
            when(tokenService.validateAndLoadUser("token")).thenReturn(user);

            AppConversation conversation = new AppConversation();
            conversation.setId(conversationId);
            conversation.setUserId(userId);
            conversation.setCharacterId(characterId);
            when(conversationMapper.findByIdForUser(conversationId, userId)).thenReturn(conversation);

            AppConversationStBinding binding = new AppConversationStBinding();
            binding.setConversationId(conversationId);
            binding.setStAvatarUrl("avatar.png");
            binding.setStChatFileName("chat.jsonl");
            binding.setStWorldNamesJson("[\"base_world\"]");
            when(bindingMapper.findByConversationId(conversationId)).thenReturn(binding);

            AppCharacter character = new AppCharacter();
            character.setId(characterId);
            character.setName("Character");
            when(characterMapper.findById(characterId)).thenReturn(character);
        }

        private AppChatService createService() {
            return new AppChatService(
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
                    memoryAutoRefreshService
            );
        }
    }
}
