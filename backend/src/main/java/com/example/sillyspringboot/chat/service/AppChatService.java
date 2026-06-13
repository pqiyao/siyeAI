package com.example.sillyspringboot.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.dto.AppChatStreamRequest;
import com.example.sillyspringboot.chat.dto.AppChatContinueRequest;
import com.example.sillyspringboot.chat.dto.AppChatRegenerateRequest;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.entity.H5MyCharacter;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.entity.AppH5Profile;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ProfileMapper;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAttachService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatMessage;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.ConversationIdentity;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.SwipeVariant;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class AppChatService {

    private static final Logger log = LoggerFactory.getLogger(AppChatService.class);
    private static final String DEFAULT_CONTINUE_NUDGE =
            "[Continue your last message without repeating its original content.]";
    private static final int EXPRESSION_HINT_LIMIT = 12;
    private static final int ATTACHMENT_HINT_LIMIT = 48;
    private static final String ATTACHMENT_MODE_EXPRESSION = "expression";
    private static final String ATTACHMENT_MODE_PHOTO = "photo";
    private static final String PHOTO_ROLEPLAY_SYSTEM_PROMPT = """
            The user has shared an image, and a short machine-generated summary of that image will appear in the next user message.
            Treat that summary only as auxiliary scene context.

            Hard rules:
            - Stay fully in-character inside the current fictional roleplay.
            - Reply as the character only, not as a generic assistant.
            - Do not say you are an AI, a language model, or that you have no real body or cannot wear clothes, unless that is explicitly part of the character setting already established in the roleplay.
            - Use the image summary only to understand what the user showed you, then respond naturally as the character.
            """;
    private static final String IMAGE_SUMMARY_SYSTEM_PROMPT = """
            You are a careful vision assistant helping a roleplay chat app understand a user-uploaded image.

            Hard rules:
            - Respond in the same primary language as the user's text when possible, otherwise use concise Chinese.
            - Describe only the main subject, action, scene, mood, and user-relevant visual details.
            - Do not output OCR dumps, random text transcription, coordinates, grounding tags, markup, or tokens like <|LOC_123|>.
            - If there is visible text, mention it only when it is clearly central to the user's intent.
            - Keep it short, clean, and natural.
            - Return plain text only.
            """;
    private static final String REPLY_SUGGESTION_SYSTEM_PROMPT = """
            You are an expert roleplay reply coach inside a SillyTavern chat.
            Your job is to write candidate messages that the HUMAN USER could send next.
            
            Hard rules:
            - Keep the same primary language as the current conversation.
            - Write in the user's voice and point of view only.
            - Do not answer as the character, narrator, assistant, system, or AI.
            - Do not summarize or explain the conversation.
            - Avoid generic filler such as "tell me more", "I do not know", "what should I say", or "as an AI".
            - Each option must be directly sendable as one chat message.
            - Make the 4 options meaningfully different: gentle, playful/teasing, direct/active, emotionally deeper.
            - If the scene is intimate, tense, or awkward, preserve that tone without becoming crude or robotic.
            - Prefer 12-60 Chinese characters for Chinese conversations, or 6-35 words for English conversations.
            - Return JSON only, exactly in this shape: {"suggestions":["...","...","...","..."]}
            """;

    private final AppConversationMapper conversationMapper;
    private final AppConversationStBindingMapper bindingMapper;
    private final AppMessageMapper messageMapper;
    private final AppGenerationTaskMapper taskMapper;
    private final ChatAuditService chatAuditService;
    private final AppTokenService tokenService;
    private final StAdapter stAdapter;
    private final ChatConcurrencyGate gate;
    private final AppChatRuntimeRegistry runtimeRegistry;
    private final AppChatProperties chatProperties;
    private final ChatSnapshotService snapshotService;
    private final H5MyCharacterMapper h5MyCharacterMapper;
    private final AppCharacterMapper characterMapper;
    private final AppH5ProfileMapper h5ProfileMapper;
    private final H5UserAiProviderService userAiProviderService;
    private final ChatImageContentService chatImageContentService;
    private final ConversationMemoryAttachService memoryAttachService;
    private final ConversationMemoryAutoRefreshService memoryAutoRefreshService;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public AppChatService(
            AppConversationMapper conversationMapper,
            AppConversationStBindingMapper bindingMapper,
            AppMessageMapper messageMapper,
            AppGenerationTaskMapper taskMapper,
            ChatAuditService chatAuditService,
            AppTokenService tokenService,
            StAdapter stAdapter,
            ChatConcurrencyGate gate,
            AppChatRuntimeRegistry runtimeRegistry,
            AppChatProperties chatProperties,
            ChatSnapshotService snapshotService,
            H5MyCharacterMapper h5MyCharacterMapper,
            AppCharacterMapper characterMapper,
            AppH5ProfileMapper h5ProfileMapper,
            H5UserAiProviderService userAiProviderService,
            ChatImageContentService chatImageContentService,
            ConversationMemoryAttachService memoryAttachService,
            ConversationMemoryAutoRefreshService memoryAutoRefreshService
    ) {
        this.conversationMapper = conversationMapper;
        this.bindingMapper = bindingMapper;
        this.messageMapper = messageMapper;
        this.taskMapper = taskMapper;
        this.chatAuditService = chatAuditService;
        this.tokenService = tokenService;
        this.stAdapter = stAdapter;
        this.gate = gate;
        this.runtimeRegistry = runtimeRegistry;
        this.chatProperties = chatProperties;
        this.snapshotService = snapshotService;
        this.h5MyCharacterMapper = h5MyCharacterMapper;
        this.characterMapper = characterMapper;
        this.h5ProfileMapper = h5ProfileMapper;
        this.userAiProviderService = userAiProviderService;
        this.chatImageContentService = chatImageContentService;
        this.memoryAttachService = memoryAttachService;
        this.memoryAutoRefreshService = memoryAutoRefreshService;
    }

    public ChatConcurrencyGate.Lease acquireLease(String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        return gate.acquire(userId);
    }

    public long resolveUserId(String token) {
        return tokenService.validateAndLoadUser(token).getId();
    }

    public int maxQueueWaitSeconds() {
        return chatProperties.getMaxQueueWaitSeconds();
    }

    public long sseTimeoutMillis() {
        return java.time.Duration.ofSeconds(chatProperties.getSseTimeoutSeconds()).toMillis();
    }

    public int generationTimeoutSeconds() {
        return chatProperties.getGenerationTimeoutSeconds();
    }

    public StStreamControl registerControl(long conversationId, StStreamControl control) {
        return runtimeRegistry.register(conversationId, control);
    }

    public void unregisterControl(long conversationId) {
        runtimeRegistry.unregister(conversationId);
    }

    public boolean stop(long conversationId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        boolean localCancelled = runtimeRegistry.cancel(conversationId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            return localCancelled;
        }

        try {
            boolean stCancelled = stAdapter.stopGeneration(new ConversationIdentity(
                    c.getId(),
                    userId,
                    c.getCharacterId(),
                    nz(binding.getStCharacterRef()),
                    nz(binding.getStChatRef()),
                    nz(binding.getStRuntimeProfile()),
                    nz(binding.getStAvatarUrl()),
                    nz(binding.getStChatFileName())
            ));
            return localCancelled || stCancelled;
        } catch (RuntimeException ex) {
            log.warn("runtime stop failed for conversationId={}, avatarUrl={}, fileName={}",
                    conversationId, binding.getStAvatarUrl(), binding.getStChatFileName(), ex);
            return localCancelled;
        }
    }

    @Transactional
    public boolean ensureOpeningAssistantMessage(long conversationId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        RoleplayBundle bundle = resolveRoleplayBundle(conversation.getCharacterId(), userId);
        String opening = pickOpeningText(bundle);
        if (opening.isBlank()) {
            return false;
        }

        List<AppMessage> existing = messageMapper.listByConversationAsc(conversationId, 32);
        if (existing != null && !existing.isEmpty()) {
            List<AppMessage> visible = existing.stream()
                    .filter(AppChatService::includeVisibleMessage)
                    .toList();
            if (!visible.isEmpty()) {
                if (repairBogusOpeningMessageIfNeeded(conversationId, visible, bundle, opening)) {
                    return true;
                }
                return false;
            }
        }

        AppMessage openingMessage = new AppMessage();
        openingMessage.setUserId(userId);
        openingMessage.setConversationId(conversationId);
        openingMessage.setRole("assistant");
        openingMessage.setClientMessageId("opening_" + System.currentTimeMillis());
        openingMessage.setContent(opening);
        openingMessage.setStatus("SUCCESS");
        openingMessage.setTraceId(traceIdSafe());
        messageMapper.insert(openingMessage);
        messageMapper.incrementTotalMessageCounter();

        String title = bundle.detail() == null ? "" : nz(bundle.detail().name());
        if (title != null && !title.isBlank()) {
            conversationMapper.setTitleIfNull(conversationId, title);
        } else {
            conversationMapper.setTitleToCharacterNameIfNull(conversationId);
        }
        chatAuditService.touchAfterAssistantContentUpdate(openingMessage.getId());
        snapshotService.saveSnapshotFromDb(conversationId, 800);
        return true;
    }

    public void streamGenerate(AppChatStreamRequest req, String token, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        streamGenerate(req, token, "", onChunk, control);
    }

    public void streamGenerate(AppChatStreamRequest req, String token, String stMessageRef, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(req.getConversationId(), userId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }

        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(req.getConversationId());
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ST binding missing (avatar_url/file_name)");
        }
        UserModelOverride userModelOverride = resolveUserModelOverride(userId);
        String userName = displayNameForSt(user, userId, binding);
        String charName = bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name());
        List<String> worldNames = worldNamesForGeneration(req.getConversationId(), binding, c.getCharacterId());
        String tailMemoryPrompt = tailMemoryPromptForGeneration(req.getConversationId());
        List<String> inlineImageUrls = chatImageContentService.resolveInlineDataUrls(req.getImageUrls());
        String attachmentMode = normalizeAttachmentMode(req.getAttachmentMode(), inlineImageUrls);
        String attachmentHint = normalizeAttachmentHint(req.getAttachmentHint());
        boolean useInlineImages = !inlineImageUrls.isEmpty() && !ATTACHMENT_MODE_EXPRESSION.equals(attachmentMode);
        ensureImageChatRuntimeReady(userModelOverride, useInlineImages ? inlineImageUrls : List.of());
        String roleplayUserMessage = nz(req.getUserMessage());
        List<String> expressionHints = normalizeExpressionHints(req.getExpressionHints());
        List<String> avoidExpressionHints = normalizeExpressionHints(req.getAvoidExpressionHints());
        boolean needsPromptMessages = useInlineImages
                || ATTACHMENT_MODE_EXPRESSION.equals(attachmentMode)
                || !expressionHints.isEmpty();
        List<String> directInlineImages = useInlineImages ? inlineImageUrls : List.of();
        List<ChatMessage> promptMessages = !needsPromptMessages
                ? List.of()
                : buildGeneratePromptMessages(
                        binding.getStAvatarUrl(),
                        binding.getStChatFileName(),
                        userName,
                        charName,
                        worldNames,
                        roleplayUserMessage,
                        directInlineImages,
                        expressionHints,
                        avoidExpressionHints,
                        attachmentMode,
                        attachmentHint
                );

        ChatGenerateRequest primaryRequest = new ChatGenerateRequest(
                req.getConversationId(),
                req.getUserMessage(),
                promptMessages,
                req.getClientMessageId(),
                true,
                "generate",
                Set.of(),
                userName,
                charName,
                List.of(),
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                stMessageRef == null ? "" : stMessageRef,
                worldNames,
                tailMemoryPrompt,
                userModelOverride
        );

        final boolean[] hasPrimaryDelta = {false};
        Consumer<ChatGenerateChunk> primaryChunkConsumer = chunk -> {
            if (chunk != null && chunk.delta() != null && !chunk.delta().isEmpty()) {
                hasPrimaryDelta[0] = true;
            }
            onChunk.accept(chunk);
        };
        try {
            stAdapter.streamGenerateAssistantReply(primaryRequest, primaryChunkConsumer, control);
        } catch (BusinessException ex) {
            if (hasPrimaryDelta[0] || !shouldFallbackToVisionSummary(ex, attachmentMode, directInlineImages)) {
                throw ex;
            }
            List<ChatMessage> fallbackPromptMessages = buildGeneratePromptMessages(
                    binding.getStAvatarUrl(),
                    binding.getStChatFileName(),
                    userName,
                    charName,
                    worldNames,
                    buildPhotoRoleplayUserMessage(
                            req.getConversationId(),
                            userName,
                            charName,
                            worldNames,
                            req.getUserMessage(),
                            inlineImageUrls,
                            userModelOverride
                    ),
                    List.of(),
                    expressionHints,
                    avoidExpressionHints,
                    attachmentMode,
                    attachmentHint
            );

            ChatGenerateRequest fallbackRequest = new ChatGenerateRequest(
                    req.getConversationId(),
                    req.getUserMessage(),
                    fallbackPromptMessages,
                    req.getClientMessageId(),
                    true,
                    "generate",
                    Set.of(),
                    userName,
                    charName,
                    List.of(),
                    binding.getStAvatarUrl(),
                    binding.getStChatFileName(),
                    stMessageRef == null ? "" : stMessageRef,
                    worldNames,
                    tailMemoryPrompt,
                    userModelOverride
            );
            stAdapter.streamGenerateAssistantReply(fallbackRequest, onChunk, control);
        }
    }

    // ===== Phase 5 stubs (will be implemented after ST chat snapshot wiring) =====

    public void streamContinue(AppChatContinueRequest req, String token, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(req.getConversationId(), userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");

        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(req.getConversationId());
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ST binding missing (avatar_url/file_name)");
        }
        UserModelOverride userModelOverride = resolveUserModelOverride(userId);
        String userName = displayNameForSt(user, userId, binding);
        String charName = bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name());
        List<String> worldNames = worldNamesForGeneration(req.getConversationId(), binding, c.getCharacterId());
        String tailMemoryPrompt = tailMemoryPromptForGeneration(req.getConversationId());
        List<String> expressionHints = normalizeExpressionHints(req.getExpressionHints());
        List<String> avoidExpressionHints = normalizeExpressionHints(req.getAvoidExpressionHints());
        List<ChatMessage> promptMessages = expressionHints.isEmpty()
                ? List.of()
                : buildRuntimePromptMessagesWithExpressionHints(
                        binding.getStAvatarUrl(),
                        binding.getStChatFileName(),
                        userName,
                        charName,
                        worldNames,
                        expressionHints,
                        avoidExpressionHints
                );
        ChatGenerateRequest stReq = new ChatGenerateRequest(
                req.getConversationId(),
                null,
                promptMessages,
                req.getClientMessageId(),
                true,
                "continue",
                Set.of("continue"),
                userName,
                charName,
                List.of(),
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                "",
                worldNames,
                tailMemoryPrompt,
                userModelOverride
        );
        stAdapter.streamGenerateAssistantReply(stReq, onChunk, control);
    }

    public void streamRegenerate(AppChatRegenerateRequest req, String token, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(req.getConversationId(), userId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }

        // A锛氬晢鐢ㄥ畨鍏ㄧ害鏉熲€斺€斾粎鍏佽 regenerate 浼氳瘽鏈€鍚庝竴鏉?assistant
        long targetId;
        try {
            String raw = req.getTargetMessageId() == null ? "" : req.getTargetMessageId().trim();
            if (raw.startsWith("db_")) raw = raw.substring(3);
            targetId = Long.parseLong(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "targetMessageId invalid");
        }
        AppMessage lastAssistant = findLastVisibleAssistant(req.getConversationId());
        if (lastAssistant == null || lastAssistant.getId() == null || lastAssistant.getId().longValue() != targetId) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前仅支持重写最后一条回复");
        }

        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(req.getConversationId());
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ST binding missing (avatar_url/file_name)");
        }
        UserModelOverride userModelOverride = resolveUserModelOverride(userId);
        String userName = displayNameForSt(user, userId, binding);
        String charName = bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name());
        List<String> worldNames = worldNamesForGeneration(req.getConversationId(), binding, c.getCharacterId());
        String tailMemoryPrompt = tailMemoryPromptForGeneration(req.getConversationId());
        List<String> expressionHints = normalizeExpressionHints(req.getExpressionHints());
        List<String> avoidExpressionHints = normalizeExpressionHints(req.getAvoidExpressionHints());
        List<ChatMessage> promptMessages = expressionHints.isEmpty()
                ? List.of()
                : buildRuntimePromptMessagesWithExpressionHints(
                        binding.getStAvatarUrl(),
                        binding.getStChatFileName(),
                        userName,
                        charName,
                        worldNames,
                        expressionHints,
                        avoidExpressionHints
                );
        ChatGenerateRequest stReq = new ChatGenerateRequest(
                req.getConversationId(),
                null,
                promptMessages,
                req.getClientMessageId(),
                true,
                "regenerate",
                Set.of("regenerate"),
                userName,
                charName,
                List.of(),
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                "root:" + targetId,
                worldNames,
                tailMemoryPrompt,
                userModelOverride
        );
        stAdapter.streamGenerateAssistantReply(stReq, onChunk, control);
    }

    public List<String> suggestReplies(long conversationId, String token, String currentDraft) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }

        RoleplayBundle bundle = resolveRoleplayBundle(conversation.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ST binding missing (avatar_url/file_name)");
        }

        String userName = displayNameForSt(user, userId, binding);
        String charName = bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name());
        List<String> worldNames = worldNamesForGeneration(conversationId, binding, conversation.getCharacterId());
        List<Map<String, String>> runtimeMessages = stAdapter.buildRuntimeMessages(
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                userName,
                charName,
                List.of(),
                worldNames
        );
        if (runtimeMessages == null || runtimeMessages.isEmpty()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 助手暂时不可用，请稍后再试");
        }

        try {
            List<ChatMessage> promptMessages = buildReplySuggestionMessages(
                    runtimeMessages,
                    currentDraft,
                    userName,
                    charName
            );
            UserModelOverride userModelOverride = resolveUserModelOverride(userId);

            ChatGenerateRequest request = new ChatGenerateRequest(
                    conversationId,
                    "",
                    promptMessages,
                    "suggest_" + System.currentTimeMillis(),
                    true,
                    "reply_suggestions",
                    Set.of(),
                    userName,
                    charName,
                    List.of(),
                    binding.getStAvatarUrl(),
                    binding.getStChatFileName(),
                    "",
                    worldNames,
                    "",
                    userModelOverride
            );
            StStreamControl control = new StStreamControl();
            StringBuilder raw = new StringBuilder();
            stAdapter.streamGenerateAssistantReply(request, chunk -> {
                if (chunk != null && chunk.delta() != null) {
                    raw.append(chunk.delta());
                }
            }, control);

            List<String> suggestions = parseReplySuggestions(raw.toString());
            if (!suggestions.isEmpty()) {
                return suggestions;
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("reply suggestions failed conversationId={} userId={} cause={}",
                    conversationId, userId, rootCauseMessage(ex));
        }
        throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 助手暂时不可用，请稍后再试");
    }

    private List<ChatMessage> buildReplySuggestionMessages(
            List<Map<String, String>> runtimeMessages,
            String currentDraft,
            String userName,
            String charName
    ) {
        List<ChatMessage> promptMessages = new ArrayList<>((runtimeMessages == null ? 0 : runtimeMessages.size()) + 2);
        if (runtimeMessages != null) {
            for (Map<String, String> message : runtimeMessages) {
                if (message == null) {
                    continue;
                }
                String role = normalizePromptRole(message.get("role"));
                String content = nz(message.get("content"));
                if (!role.isBlank() && !content.isBlank()) {
                    promptMessages.add(ChatMessage.text(role, content));
                }
            }
        }
        if (promptMessages.isEmpty()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 助手暂时不可用，请稍后再试");
        }
        promptMessages.add(ChatMessage.text("system", REPLY_SUGGESTION_SYSTEM_PROMPT));
        promptMessages.add(ChatMessage.text("user", buildReplySuggestionPrompt(currentDraft, userName, charName)));
        return promptMessages;
    }

    private List<ChatMessage> buildRuntimePromptMessages(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> worldNames
    ) {
        List<Map<String, String>> runtimeMessages = stAdapter.buildRuntimeMessages(
                avatarUrl,
                fileName,
                userName,
                charName,
                List.of(),
                worldNames
        );
        if (runtimeMessages == null || runtimeMessages.isEmpty()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "瑙嗚鑱婂ぉ涓婁笅鏂囨殏鏃朵笉鍙敤锛岃绋嶅悗閲嶈瘯");
        }
        List<ChatMessage> promptMessages = new ArrayList<>(runtimeMessages.size() + 1);
        for (Map<String, String> message : runtimeMessages) {
            if (message == null) {
                continue;
            }
            String role = normalizePromptRole(message.get("role"));
            String content = nz(message.get("content"));
            if (!role.isBlank() && !content.isBlank()) {
                promptMessages.add(ChatMessage.text(role, content));
            }
        }
        if (promptMessages.isEmpty()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "瑙嗚鑱婂ぉ涓婁笅鏂囨殏鏃朵笉鍙敤锛岃绋嶅悗閲嶈瘯");
        }
        return promptMessages;
    }

    private List<ChatMessage> buildGeneratePromptMessages(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> worldNames,
            String userMessage,
            List<String> inlineImageUrls,
            List<String> expressionHints,
            List<String> avoidExpressionHints,
            String attachmentMode,
            String attachmentHint
    ) {
        List<ChatMessage> promptMessages = buildRuntimePromptMessagesWithExpressionHints(
                avatarUrl,
                fileName,
                userName,
                charName,
                worldNames,
                expressionHints,
                avoidExpressionHints
        );
        if (ATTACHMENT_MODE_PHOTO.equals(attachmentMode)) {
            promptMessages = injectPhotoRoleplaySystemMessage(promptMessages);
        }
        String effectiveUserMessage = buildAttachmentAwareUserMessage(
                userMessage,
                inlineImageUrls,
                attachmentMode,
                attachmentHint
        );
        if (inlineImageUrls == null || inlineImageUrls.isEmpty()) {
            promptMessages.add(ChatMessage.text("user", effectiveUserMessage));
        } else {
            promptMessages.add(ChatMessage.multimodalUser(effectiveUserMessage, inlineImageUrls));
        }
        return promptMessages;
    }

    private List<ChatMessage> injectPhotoRoleplaySystemMessage(List<ChatMessage> promptMessages) {
        List<ChatMessage> source = promptMessages == null ? List.of() : promptMessages;
        List<ChatMessage> next = new ArrayList<>(source.size() + 1);
        boolean inserted = false;
        ChatMessage systemPrompt = ChatMessage.text("system", PHOTO_ROLEPLAY_SYSTEM_PROMPT);
        for (ChatMessage message : source) {
            String role = normalizePromptRole(message == null ? "" : message.role());
            if (!inserted && !"system".equals(role)) {
                next.add(systemPrompt);
                inserted = true;
            }
            if (message != null) {
                next.add(message);
            }
        }
        if (!inserted) {
            next.add(systemPrompt);
        }
        return next;
    }

    private String buildAttachmentAwareUserMessage(
            String userMessage,
            List<String> inlineImageUrls,
            String attachmentMode,
            String attachmentHint
    ) {
        String baseMessage = nz(userMessage);
        if (ATTACHMENT_MODE_EXPRESSION.equals(attachmentMode)) {
            String hint = normalizeAttachmentHint(attachmentHint);
            StringBuilder sb = new StringBuilder();
            sb.append("The user sent a local sticker/expression");
            if (!hint.isBlank()) {
                sb.append(" labeled \"").append(hint).append("\"");
            }
            sb.append(". Treat it as an emotional cue, not as a real photo, OCR task, or file description.\n");
            sb.append("Infer the likely mood or reaction, then reply naturally in the same language as the conversation.");
            if (!baseMessage.isBlank()) {
                sb.append("\nUser text: ").append(baseMessage);
            }
            return sb.toString();
        }
        if (inlineImageUrls != null && !inlineImageUrls.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("The user attached an image. Reply naturally based on the image and the ongoing conversation.\n");
            sb.append("Unless the user explicitly asks for OCR or text extraction:\n");
            sb.append("- do not transcribe random text from the image\n");
            sb.append("- do not output coordinates, grounding tags, or tokens like <|LOC_123|>\n");
            sb.append("- focus on the main subject, mood, and likely user intent\n");
            sb.append("- keep the reply concise and in the same language as the conversation");
            if (!baseMessage.isBlank()) {
                sb.append("\nUser text: ").append(baseMessage);
            }
            return sb.toString();
        }
        return baseMessage;
    }

    private String buildPhotoRoleplayUserMessage(
            Long conversationId,
            String userName,
            String charName,
            List<String> worldNames,
            String userMessage,
            List<String> inlineImageUrls,
            UserModelOverride userModelOverride
    ) {
        String summary = summarizeUserImages(
                conversationId,
                userName,
                charName,
                worldNames,
                userMessage,
                inlineImageUrls,
                userModelOverride
        );
        String baseMessage = nz(userMessage).trim();
        if (baseMessage.isBlank()) {
            return "用户发来了一张图片。\n请继续以角色身份自然回应。\n图像摘要：" + summary;
        }
        return baseMessage + "\n\n请继续以角色身份自然回应。下面是这张图片的辅助摘要：\n" + summary;
    }

    private String summarizeUserImages(
            Long conversationId,
            String userName,
            String charName,
            List<String> worldNames,
            String userMessage,
            List<String> inlineImageUrls,
            UserModelOverride userModelOverride
    ) {
        if (inlineImageUrls == null || inlineImageUrls.isEmpty()) {
            return "";
        }
        List<ChatMessage> promptMessages = new ArrayList<>(2);
        promptMessages.add(ChatMessage.text("system", IMAGE_SUMMARY_SYSTEM_PROMPT));
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Please summarize the attached image for a roleplay chat reply.");
        if (!nz(userMessage).trim().isBlank()) {
            userPrompt.append("\nUser text: ").append(nz(userMessage).trim());
        }
        promptMessages.add(ChatMessage.multimodalUser(userPrompt.toString(), inlineImageUrls));

        ChatGenerateRequest summaryRequest = new ChatGenerateRequest(
                conversationId,
                "",
                promptMessages,
                "vision_summary_" + System.currentTimeMillis(),
                true,
                "vision_summary",
                Set.of(),
                "",
                "",
                List.of(),
                "",
                "",
                "",
                worldNames == null ? List.of() : worldNames,
                userModelOverride
        );
        StStreamControl control = new StStreamControl();
        StringBuilder raw = new StringBuilder();
        stAdapter.streamGenerateAssistantReply(summaryRequest, chunk -> {
            if (chunk != null && chunk.delta() != null) {
                raw.append(chunk.delta());
            }
        }, control);
        String summary = sanitizeImageSummary(raw.toString());
        if (summary.isBlank()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "图片识别暂时不可用，请稍后再试");
        }
        return summary;
    }

    private String sanitizeImageSummary(String raw) {
        String text = nz(raw);
        text = text.replaceAll("<\\|[A-Za-z0-9_:-]+\\|>", "");
        text = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]+", "");
        text = text.replaceAll("\\s+\\n", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.trim();
        if (text.length() > 240) {
            text = text.substring(0, 240).trim();
        }
        return text;
    }

    private boolean shouldFallbackToVisionSummary(
            BusinessException ex,
            String attachmentMode,
            List<String> directInlineImages
    ) {
        if (!ATTACHMENT_MODE_PHOTO.equals(attachmentMode) || directInlineImages == null || directInlineImages.isEmpty()) {
            return false;
        }
        String message = nz(ex.getMessage()).toLowerCase();
        return message.contains("does not support images")
                || message.contains("image_url")
                || message.contains("unsupported image")
                || message.contains("unsupported content type")
                || message.contains("invalid image")
                || message.contains("content part");
    }

    private void ensureImageChatRuntimeReady(UserModelOverride override, List<String> inlineImageUrls) {
        if (inlineImageUrls == null || inlineImageUrls.isEmpty()) {
            return;
        }
        if (override == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "鍙戦€佸浘鐗囧墠锛岃鍏堝湪 AI 璁剧疆椤甸厤缃彲鐢ㄦā鍨嬪拰 API Key"
            );
        }
        if (StringUtils.hasText(nz(override.providerSource()))
                && StringUtils.hasText(nz(override.apiKey()))
                && StringUtils.hasText(nz(override.textModelOrFallback()))) {
            return;
        }
        throw new BusinessException(
                ErrorCode.VALIDATION_FAILED,
                "鍙戦€佸浘鐗囧墠锛岃鍏堝湪 AI 璁剧疆椤佃ˉ鍏ㄦā鍨嬪拰 API Key"
        );
    }

    private String normalizeAttachmentMode(String rawMode, List<String> inlineImageUrls) {
        String mode = nz(rawMode).trim().toLowerCase();
        if (ATTACHMENT_MODE_EXPRESSION.equals(mode)) {
            return ATTACHMENT_MODE_EXPRESSION;
        }
        return inlineImageUrls == null || inlineImageUrls.isEmpty() ? "" : ATTACHMENT_MODE_PHOTO;
    }

    private String normalizeAttachmentHint(String rawHint) {
        String hint = nz(rawHint).replaceAll("\\s+", " ").trim();
        if (hint.length() > ATTACHMENT_HINT_LIMIT) {
            hint = hint.substring(0, ATTACHMENT_HINT_LIMIT).trim();
        }
        return hint;
    }

    private List<ChatMessage> buildRuntimePromptMessagesWithExpressionHints(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> worldNames,
            List<String> expressionHints,
            List<String> avoidExpressionHints
    ) {
        List<ChatMessage> promptMessages = buildRuntimePromptMessages(
                avatarUrl,
                fileName,
                userName,
                charName,
                worldNames
        );
        return injectExpressionHintSystemMessage(promptMessages, expressionHints, avoidExpressionHints);
    }


    private List<ChatMessage> injectExpressionHintSystemMessage(
            List<ChatMessage> promptMessages,
            List<String> expressionHints,
            List<String> avoidExpressionHints
    ) {
        List<String> safeHints = normalizeExpressionHints(expressionHints);
        List<String> safeAvoidHints = normalizeExpressionHints(avoidExpressionHints);
        List<ChatMessage> source = promptMessages == null ? List.of() : promptMessages;
        if (safeHints.isEmpty()) {
            return new ArrayList<>(source);
        }
        ChatMessage systemPrompt = ChatMessage.text("system", buildExpressionHintSystemPrompt(safeHints, safeAvoidHints));
        List<ChatMessage> next = new ArrayList<>(source.size() + 1);
        boolean inserted = false;
        for (ChatMessage message : source) {
            String role = normalizePromptRole(message == null ? "" : message.role());
            if (!inserted && !"system".equals(role)) {
                next.add(systemPrompt);
                inserted = true;
            }
            if (message != null) {
                next.add(message);
            }
        }
        if (!inserted) {
            next.add(systemPrompt);
        }
        return next;
    }

    private String buildExpressionHintSystemPrompt(List<String> expressionHints, List<String> avoidExpressionHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("You may naturally weave in at most one keyword from the user's local expression library if it fits this reply.\n");
        sb.append("If you choose one keyword, append exactly one hidden marker at the very end using this format: [[expr:KEYWORD]].\n");
        sb.append("The visible reply does not need to literally contain the keyword; the hidden marker controls the attachment.\n");
        sb.append("Keep the visible reply natural, and do not explain the marker.\n");
        sb.append("Rules:\n");
        sb.append("- Use at most one keyword.\n");
        sb.append("- Copy the keyword exactly as listed.\n");
        sb.append("- If you add a marker, output exactly one marker at the very end and nothing else in marker form.\n");
        sb.append("- Only add [[expr:KEYWORD]] when you intentionally selected that keyword for this reply.\n");
        sb.append("- Be conservative with short or generic interjections; if the fit is weak, skip the expression.\n");
        sb.append("- Do not enumerate, explain, or force any keyword.\n");
        sb.append("- Do not print the raw keyword itself in the visible reply unless the user explicitly asked for that exact word.\n");
        sb.append("- Do not output bracketed mood tags, sticker names, or file-like text such as [棣媇, (寮€蹇?, 鐤媯鏆楃ず.jpg, xxx.png, or similar label text in visible reply.\n");
        sb.append("- If none fit, do not use one.\n");
        if (avoidExpressionHints != null && !avoidExpressionHints.isEmpty()) {
            sb.append("- Strongly avoid repeating these recently used keywords in consecutive replies unless one is clearly the best fit:\n");
            for (String hint : avoidExpressionHints) {
                sb.append("  - ").append(hint).append('\n');
            }
        }
        sb.append("Available keywords:\n");
        for (String hint : expressionHints) {
            sb.append("- ").append(hint).append('\n');
        }
        return sb.toString().trim();
    }

    private List<String> normalizeExpressionHints(List<String> rawHints) {
        if (rawHints == null || rawHints.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String rawHint : rawHints) {
            String normalized = normalizeExpressionHint(rawHint);
            if (!normalized.isBlank()) {
                deduped.add(normalized);
            }
            if (deduped.size() >= EXPRESSION_HINT_LIMIT) {
                break;
            }
        }
        return deduped.isEmpty() ? List.of() : List.copyOf(deduped);
    }

    private String normalizeExpressionHint(String rawHint) {
        if (rawHint == null) {
            return "";
        }
        String normalized = rawHint.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20).trim();
        }
        return normalized;
    }

    private static String normalizePromptRole(String raw) {
        String role = nz(raw).toLowerCase();
        return switch (role) {
            case "system", "assistant", "user", "tool" -> role;
            case "char", "character", "bot" -> "assistant";
            default -> "";
        };
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor != null && cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor == null ? "" : cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = error == null ? "" : error.toString();
        }
        return message == null ? "" : message.trim();
    }

    /**
     * 鏂规涓€锛圫tepA锛夛細鐢熸垚鎴愬姛鍚庡皢 assistant 鏈€缁堝洖澶嶅啓鍥?ST chat锛屼繚璇?ST 涓鸿繍琛屾椂浜嬪疄婧愩€?     */
    public void syncAssistantReplyToSt(long conversationId, String stMessageRef, String assistantContent, String token) {
        if (assistantContent == null || assistantContent.isBlank()) return;
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) return;
        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            return;
        }
        ChatGenerateRequest req = new ChatGenerateRequest(
                conversationId,
                null,
                List.of(),
                "sync_" + System.currentTimeMillis(),
                false,
                "sync",
                Set.of(),
                displayNameForSt(user, userId, binding),
                bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name()),
                List.of(),
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                stMessageRef == null ? "" : stMessageRef,
                parseWorldNames(binding, c.getCharacterId()),
                null
        );
        stAdapter.appendAssistantMessage(req, assistantContent);
    }

    /**
     * A锛歴wipe 鍒囨崲鍚庤 ST chat 鐨勬渶鍚庝竴鏉?assistant 涓庘€滃綋鍓嶅睍绀虹増鏈€濅竴鑷淬€?     * 鍏堝仛鍟嗙敤瀹夊叏锛氫粎鍏佽鍚屾鈥滀細璇濇渶鍚庝竴鏉?assistant鈥濄€?     */
    public void syncSwipeSelectionToSt(long conversationId, long assistantMessageId, String token) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) return;

        AppMessage target = messageMapper.findById(assistantMessageId);
        if (target == null || target.getConversationId() == null || target.getConversationId() != conversationId) return;
        if (!"assistant".equalsIgnoreCase(target.getRole())) return;

        // 浠呭厑璁告渶鍚庝竴鏉?assistant锛堝晢鐢ㄥ己绾︽潫锛岄伩鍏嶈鏀瑰巻鍙查€犳垚涓婁笅鏂囬敊涔憋級
        AppMessage lastAssistant = findLastVisibleAssistant(conversationId);
        if (lastAssistant == null || lastAssistant.getId() == null || lastAssistant.getId().longValue() != assistantMessageId) {
            throw new BusinessException(ErrorCode.CONFLICT, "鍙兘瀵规渶鍚庝竴鏉″洖澶嶈繘琛?swipe/鍚屾");
        }

        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            return;
        }

        String ref = ensureSwipeRootRef(target, traceIdSafe());
        ChatGenerateRequest stReq = new ChatGenerateRequest(
                conversationId,
                null,
                List.of(),
                "swipe_" + System.currentTimeMillis(),
                false,
                "swipe",
                Set.of(),
                displayNameForSt(user, userId, binding),
                bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name()),
                List.of(),
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                ref,
                parseWorldNames(binding, c.getCharacterId()),
                null
        );
        stAdapter.replaceLastAssistantMessage(stReq, target.getContent());
    }

    private UserModelOverride resolveUserModelOverride(long userId) {
        return userAiProviderService == null ? null : userAiProviderService.resolveActiveOverrideForUser(userId);
    }

    private void triggerMemoryRefreshAfterCommit(long conversationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            memoryAutoRefreshService.maybeTriggerAfterGenerationSuccess(conversationId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                memoryAutoRefreshService.maybeTriggerAfterGenerationSuccess(conversationId);
            }
        });
    }

    private final com.fasterxml.jackson.databind.ObjectMapper worldMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private List<String> worldNamesForGeneration(long conversationId, AppConversationStBinding binding, long characterId) {
        return parseWorldNames(binding, characterId);
    }

    private String tailMemoryPromptForGeneration(long conversationId) {
        try {
            return memoryAttachService.buildTailMemoryPromptIfAvailable(conversationId);
        } catch (Exception ex) {
            log.warn("tail memory prompt build failed conversationId={} cause={}", conversationId, rootCauseMessage(ex));
            return "";
        }
    }

    private List<String> parseWorldNames(AppConversationStBinding binding, long characterId) {
        if (binding != null) {
            List<String> conversationWorldNames = parseWorldNamesJson(binding.getStWorldNamesJson());
            if (!conversationWorldNames.isEmpty()) {
                return conversationWorldNames;
            }
        }
        if (characterId > 0) {
            AppCharacter character = characterMapper.findById(characterId);
            if (character != null) {
                List<String> characterWorldNames = parseWorldNamesJson(character.getStWorldNamesJson());
                if (!characterWorldNames.isEmpty()) {
                    return characterWorldNames;
                }
            }
        }
        return List.of();
    }

    private List<String> parseWorldNamesJson(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<?> list = worldMapper.readValue(raw, java.util.List.class);
            if (list == null) return List.of();
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            for (Object o : list) {
                String s = o == null ? "" : String.valueOf(o).trim();
                if (!s.isBlank()) out.add(s);
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private AppMessage findLastVisibleAssistant(long conversationId) {
        List<AppMessage> rows = messageMapper.listByConversation(conversationId, 2000);
        for (AppMessage m : rows) {
            if (m == null || m.getId() == null) continue;
            if (!includeVisibleMessage(m)) continue;
            if ("assistant".equalsIgnoreCase(m.getRole())) {
                return m;
            }
        }
        return null;
    }

    private String displayNameForSt(AppUser user, long userId, AppConversationStBinding binding) {
        String override = binding == null || binding.getStDisplayNameOverride() == null
                ? ""
                : binding.getStDisplayNameOverride().trim();
        if (!override.isBlank()) {
            return override;
        }
        try {
            AppH5Profile p = h5ProfileMapper == null ? null : h5ProfileMapper.findByUserId(userId);
            String stDisplayName = p == null || p.getStDisplayName() == null ? "" : p.getStDisplayName().trim();
            if (!stDisplayName.isBlank()) return stDisplayName;
            String dn = p == null || p.getDisplayName() == null ? "" : p.getDisplayName().trim();
            if (!dn.isBlank()) return dn;
        } catch (Exception ignored) {
            // best-effort only
        }

        if (user == null) return "";
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        if (!username.isBlank()) return username;
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        return (first + " " + last).trim();
    }

    public List<SwipeVariant> listSwipes(long conversationId, String messageId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");

        long mid;
        try {
            mid = Long.parseLong(messageId);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "messageId invalid");
        }
        AppMessage target = messageMapper.findById(mid);
        if (target == null || target.getConversationId() == null || target.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (!"assistant".equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "only assistant messages support swipe");
        }

        String stRef = ensureSwipeRootRef(target, traceIdSafe());
        List<AppMessage> rows = messageMapper.listByStMessageRef(stRef);
        return rows.stream()
                .filter(m -> "SUCCESS".equalsIgnoreCase(m.getStatus()))
                .filter(m -> m.getSwipeIndex() != null)
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .map(m -> new SwipeVariant(
                        String.valueOf(mid),
                        m.getSwipeIndex(),
                        m.getContent(),
                        m.getCreatedAt() == null ? Instant.now() : m.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
                ))
                .toList();
    }

    public SwipeVariant switchSwipe(long conversationId, String messageId, int variantIndex, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");

        long mid;
        try {
            mid = Long.parseLong(messageId);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "messageId invalid");
        }
        AppMessage target = messageMapper.findById(mid);
        if (target == null || target.getConversationId() == null || target.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (!"assistant".equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "only assistant messages support swipe");
        }

        String stRef = ensureSwipeRootRef(target, traceIdSafe());
        persistDisplayedSwipeVariant(target);
        AppMessage chosen = messageMapper.findByStMessageRefAndSwipeIndex(stRef, variantIndex);
        if (chosen == null && target.getSwipeIndex() != null && target.getSwipeIndex() == variantIndex) {
            chosen = target;
        }
        if (chosen == null || chosen.getContent() == null || chosen.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "swipe variant not found");
        }

        messageMapper.updateStatusAndContent(mid, "SUCCESS", chosen.getContent(), null, traceIdSafe());
        messageMapper.updateVariantMeta(mid, stRef, variantIndex, traceIdSafe());
        snapshotService.saveSnapshotFromDb(conversationId, 800);
        try {
            syncSwipeSelectionToSt(conversationId, mid, token);
        } catch (Exception ignored) {
        }
        return new SwipeVariant(String.valueOf(mid), variantIndex, chosen.getContent(), Instant.now());
    }

    /**
     * regenerate闂佹寧绋掗懝楣冩儍閵忋倕瀚夋い鎺嶇贰閸嬔囨煟閵忋垹鏋戦柛銊︽皑缁辨帡骞樼€甸晲鍑介梺璋庡嫭顫楁い鏂挎处缁?target 闂佹眹鍔岀€氼參寮?variant闂佹寧绋戦懟顖炴嚐閻斿壊娓舵俊顖涱儥閸氬洭鏌涢幒鎴炲鐎规洘鐓″畷姘跺箯瀹€濠傛畽 variant闂?     *
     * @param conversationId 婵炴潙鍚嬫穱娲儊?
     * @param targetMessageId 闁荤偞鍑归崑濠囧闯閹间焦鍋ㄩ柣鏃傚劋閻?assistant 濠电偞鍨甸悧濠冨?id
     * @param generatedMessageId 闂佸搫鐗滈崜姘额敃婵傚憡鍋ㄩ柣鏃傤焾閻忓洭鏌涢幇顓犳噧闁告瑥妫濋幆?assistant 濠电偞鍨甸悧濠冨?id闂佹寧绋戦悧蹇涘极鏉堚斁鍋撶€涖們鍊ら崥鈧梻浣哄亾瀹曟﹢鎯屾ィ鍐ㄧ婵炴垶顭囩槐锕傛煥?     */
    public SwipeVariant promoteRegenerateVariant(long conversationId, long targetMessageId, long generatedMessageId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");

        AppMessage target = messageMapper.findById(targetMessageId);
        if (target == null || target.getConversationId() == null || target.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "target message not found");
        }
        if (!"assistant".equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "target must be assistant");
        }
        AppMessage generated = messageMapper.findById(generatedMessageId);
        if (generated == null || generated.getConversationId() == null || generated.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.CONFLICT, "generated message not found");
        }
        if (generated.getContent() == null || generated.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "generated message is empty");
        }

        String stRef = ensureSwipeRootRef(target, traceIdSafe());
        persistDisplayedSwipeVariant(target);
        Integer max = messageMapper.findMaxSwipeIndex(stRef);
        int next = (max == null ? 0 : max) + 1;

        messageMapper.updateContinuationMeta(
                generatedMessageId,
                normalizeMessageKind(target.getMessageKind()),
                target.getContinueFromMessageId(),
                traceIdSafe()
        );
        messageMapper.updateVariantMeta(generatedMessageId, stRef, next, traceIdSafe());
        messageMapper.updateStatusAndContent(targetMessageId, "SUCCESS", generated.getContent(), null, traceIdSafe());
        messageMapper.updateVariantMeta(targetMessageId, stRef, next, traceIdSafe());
        snapshotService.saveSnapshotFromDb(conversationId, 800);
        try {
            syncSwipeSelectionToSt(conversationId, targetMessageId, token);
        } catch (Exception ignored) {
        }
        return new SwipeVariant(String.valueOf(targetMessageId), next, generated.getContent(), Instant.now());
    }

    private static String normalizeMessageKind(String value) {
        String kind = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return "CONTINUATION".equals(kind) ? "CONTINUATION" : "NORMAL";
    }


    @Transactional
    public void finalizeContinueAsMessage(
            long conversationId,
            long anchorAssistantId,
            long provisionalAssistantId,
            long taskId,
            String suffixContent,
            String token
    ) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");

        AppMessage anchor = messageMapper.findById(anchorAssistantId);
        if (anchor == null || anchor.getConversationId() == null || anchor.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "continue anchor not found");
        }
        if (!"assistant".equalsIgnoreCase(anchor.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "continue anchor must be assistant");
        }
        AppMessage provisional = messageMapper.findById(provisionalAssistantId);
        if (provisional == null || provisional.getConversationId() == null || provisional.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provisional message not found");
        }

        String content = suffixContent == null ? "" : suffixContent.trim();
        messageMapper.updateContinuationMeta(provisionalAssistantId, "CONTINUATION", anchorAssistantId, traceIdSafe());
        if (content.isBlank()) {
            messageMapper.updateStatusAndContent(provisionalAssistantId, "STOPPED", "", null, traceIdSafe());
        } else {
            String ref = "root:" + provisionalAssistantId;
            messageMapper.updateStatusAndContent(provisionalAssistantId, "SUCCESS", content, null, traceIdSafe());
            messageMapper.updateVariantMeta(provisionalAssistantId, ref, 0, traceIdSafe());
            try {
                syncAssistantReplyToSt(conversationId, ref, content, token);
            } catch (Exception ignored) {
            }
        }
        taskMapper.updateStatus(taskId, "SUCCESS", null, null, traceIdSafe(), null);
        snapshotService.saveSnapshotFromDb(conversationId, 800);
        chatAuditService.touchAfterAssistantContentUpdate(provisionalAssistantId);
        triggerMemoryRefreshAfterCommit(conversationId);
    }

    @Transactional
    public void abortContinueEmpty(long conversationId, long provisionalAssistantId, long taskId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        AppMessage prov = messageMapper.findById(provisionalAssistantId);
        if (prov == null || prov.getConversationId() == null || prov.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provisional message not found");
        }
        taskMapper.updateStatus(taskId, "STOPPED", null, null, traceIdSafe(), null);
        messageMapper.deleteById(provisionalAssistantId);
    }

    /**
     * 缂佺虎鍙庨崰鏇犳崲濮樿泛瑙﹂悘鐐跺亹椤忛亶鏌?assistant 闂?swipe 闂佺偨鍎茬划宥夋偋閺夋埈鍤曢柡鍥╁У閺嗗繘鏌嶉妷锔剧畵闁宦板姂瀹曠兘濡搁敐鍌氫壕?     * 缂備焦鎷濈粻鎴︽偩妤ｅ啯鏅慨婵堫棟_message_ref = "root:<targetMessageId>"闂?     */
    private String ensureSwipeRootRef(AppMessage target, String traceId) {
        String ref = target.getStMessageRef();
        if (ref == null || ref.isBlank()) {
            ref = "root:" + target.getId();
            messageMapper.updateVariantMeta(target.getId(), ref, target.getSwipeIndex() == null ? 0 : target.getSwipeIndex(), traceId);
            target.setStMessageRef(ref);
            if (target.getSwipeIndex() == null) target.setSwipeIndex(0);
        } else if (target.getSwipeIndex() == null) {
            messageMapper.updateVariantMeta(target.getId(), ref, 0, traceId);
            target.setSwipeIndex(0);
        }
        return ref;
    }

    private static String traceIdSafe() {
        return org.slf4j.MDC.get("traceId") == null ? "unknown" : org.slf4j.MDC.get("traceId");
    }

    /**
     * 闂佺厧顨庢禍顏堝焵椤掑倸孝鐎规洝椴哥粋鎺楀川椤撶姵鎲?app_character闂佹寧绋戦鎶癆vatarUrl 婵犮垼鍩栭惌顔炬嫻閻旂厧纭€闁绘浜粔?png闂?api/characters/get 闂佸搫鍟版慨鐢稿几閸愨晝顩烽悹浣告贡楠?
     * 闂婎偄娲ら幊姗€濡磋箛鏃傤浄閹艰揪绱曞銊╂煕?persona/scenario/system_prompt 缂備焦绋戦ˇ鎵偓?system闂佹寧绋戦懟顖炲箚娓氣偓瀹曟艾鈻庨幆闀愮窔瀹曞湱鈧綆浜為弳姘舵煕韫囧濡虹紒妤€顦遍幐褔骞忓畝濠傛櫗闁诲氦顫夐崫搴ㄥ焵?     */
    private RoleplayBundle resolveRoleplayBundle(long characterId, long userId) {
        H5MyCharacter mine = h5MyCharacterMapper.findById(characterId);
        if (mine != null
                && mine.getOwnerUserId() != null
                && mine.getOwnerUserId().longValue() == userId) {
            String desc = nz(mine.getDescription());
            String mex = nz(mine.getMesExample());
            if (!mex.isBlank()) {
                desc = desc.isBlank() ? ("闂侀潧妫欓崝鏇㈩敋椤旂偓瀚氭繝闈涚墑娴犳稑銆掑顒夊剭闁逞屽墯閸欐" + mex) : desc + "\n\n闂侀潧妫欓崝鏇㈩敋椤旂偓瀚氭繝闈涚墑娴犳稑銆掑顒夊剭闁逞屽墯閸欐" + mex;
            }
            StCharacterDetail d =
                    new StCharacterDetail(
                            nz(mine.getName()),
                            nz(mine.getStAvatarUrl()),
                            desc,
                            nz(mine.getScenario()),
                            nz(mine.getFirstMessage()),
                            nz(mine.getPersona()),
                            List.of(),
                            mine.getAlternateGreetings() == null ? List.of() : mine.getAlternateGreetings(),
                            mex,
                            nz(mine.getSystemPrompt()),
                            nz(mine.getPostHistoryInstructions()),
                            "",
                            "",
                            List.of(),
                            "",
                            "");
            return new RoleplayBundle(d, nz(mine.getSystemPrompt()), nz(mine.getPostHistoryInstructions()));
        }

        AppCharacter pub = characterMapper.findById(characterId);
        if (pub == null) {
            return new RoleplayBundle(null, "", "");
        }
        assertRoleplayCharacterVisibleToUser(pub, userId);
        StCharacterDetail st = null;
        if (pub.getStAvatarUrl() != null && !pub.getStAvatarUrl().isBlank()) {
            try {
                st = stAdapter.getCharacter(new StCharacterGetRequest(pub.getStAvatarUrl()));
            } catch (Exception ignored) {
            }
        }
        if (st == null) {
            st = new StCharacterDetail(
                    nz(pub.getName()),
                    nz(pub.getStAvatarUrl()),
                    nz(pub.getDescription()),
                    nz(pub.getScenario()),
                    nz(pub.getFirstMessage()),
                    nz(pub.getPersona()),
                    List.of(),
                    parseWorldNamesJson(pub.getAlternateGreetingsJson()),
                    nz(pub.getMesExample()),
                    nz(pub.getSystemPrompt()),
                    nz(pub.getPostHistoryInstructions()),
                    nz(pub.getCreatorNotes()),
                    "",
                    List.of(),
                    "",
                    "");
        }
        return new RoleplayBundle(st, nz(pub.getSystemPrompt()), nz(pub.getPostHistoryInstructions()));
    }

    private void assertRoleplayCharacterVisibleToUser(AppCharacter character, long userId) {
        Long ownerId = character.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(character.getPrivateCard())) {
            if (ownerId == null || ownerId.longValue() != userId) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
            }
            return;
        }
        if (character.getDeletedAt() != null
                || Boolean.FALSE.equals(character.getClientVisible())
                || !CharacterReviewStatus.APPROVED.equals(CharacterReviewStatus.normalize(character.getReviewStatus()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
        }
    }

    private record RoleplayBundle(StCharacterDetail detail, String systemPromptExtra, String postHistoryExtra) {}

    private static String pickOpeningText(RoleplayBundle bundle) {
        if (bundle == null || bundle.detail() == null) {
            return "";
        }
        return nz(bundle.detail().firstMes());
    }

    private boolean repairBogusOpeningMessageIfNeeded(
            long conversationId,
            List<AppMessage> visibleMessages,
            RoleplayBundle bundle,
            String opening
    ) {
        if (visibleMessages == null || visibleMessages.size() != 1) {
            return false;
        }
        AppMessage only = visibleMessages.get(0);
        if (only == null
                || only.getId() == null
                || !"assistant".equalsIgnoreCase(only.getRole())
                || !includeVisibleMessage(only)) {
            return false;
        }
        String current = nz(only.getContent());
        if (current.isBlank() || current.equals(opening) || !isBogusTagOpening(bundle, current)) {
            return false;
        }
        String status = nz(only.getStatus());
        messageMapper.updateStatusAndContent(
                only.getId(),
                status.isBlank() ? "SUCCESS" : status,
                opening,
                only.getErrorCode(),
                traceIdSafe());
        chatAuditService.touchAfterAssistantContentUpdate(only.getId());
        snapshotService.saveSnapshotFromDb(conversationId, 800);
        return true;
    }

    private static boolean isBogusTagOpening(RoleplayBundle bundle, String content) {
        if (bundle == null || bundle.detail() == null || bundle.detail().tags() == null) {
            return false;
        }
        String normalized = nz(content);
        if (normalized.isBlank()) {
            return false;
        }
        for (String tag : bundle.detail().tags()) {
            if (normalized.equalsIgnoreCase(nz(tag))) {
                return true;
            }
        }
        return false;
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "" : s.strip();
    }

    private static String buildReplySuggestionPrompt(String currentDraft, String userName, String charName) {
        String draft = nz(currentDraft);
        String participants = """
                User display name: %s
                Character name: %s
                """.formatted(nz(userName), nz(charName));
        if (draft.isBlank()) {
            return participants + """
                    Based on the full roleplay context above, generate 4 distinct candidate replies that the USER could send next.
                    Focus on the most recent character message, the current emotional tension, and the relationship dynamic.
                    Do not continue as the character. Do not be generic. Do not explain.
                    """;
        }
        return participants + """
                The USER is currently drafting an idea for their next message.
                Use it as a hint, but improve it into 4 distinct sendable options.
                Keep the user's intent, sharpen the emotion/action, and do not copy the same sentence 4 times.
                Do not continue as the character. Do not explain.
                Current rough draft:
                """ + "\n" + draft;
    }

    private List<String> parseReplySuggestions(String raw) {
        String text = stripSuggestionWrapper(raw);
        if (text.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        collectSuggestionsFromJson(text, dedup);
        if (dedup.isEmpty()) {
            collectSuggestionsFromLines(text, dedup);
        }

        if (dedup.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String item : dedup) {
            String value = polishSuggestion(cleanupSuggestion(item));
            if (isLowQualitySuggestion(value)) {
                continue;
            }
            String key = normalizeSuggestionKey(value);
            if (key.isBlank() || normalized.contains(key)) {
                continue;
            }
            normalized.add(key);
            out.add(value);
            if (out.size() >= 4) {
                break;
            }
        }
        return out;
    }

    private void collectSuggestionsFromJson(String text, LinkedHashSet<String> sink) {
        try {
            JsonNode root = jsonMapper.readTree(text);
            JsonNode suggestions = root.path("suggestions");
            if (!suggestions.isArray()) {
                return;
            }
            for (JsonNode node : suggestions) {
                String value = cleanupSuggestion(node == null ? "" : node.asText(""));
                if (!value.isBlank()) {
                    sink.add(value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void collectSuggestionsFromLines(String text, LinkedHashSet<String> sink) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String value = cleanupSuggestion(line);
            if (!value.isBlank()) {
                sink.add(value);
            }
        }
    }

    private static String stripSuggestionWrapper(String raw) {
        String text = nz(raw);
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int jsonStart = text.indexOf('{');
        int jsonEnd = text.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1).trim();
        }
        return text;
    }

    private static String cleanupSuggestion(String raw) {
        String value = nz(raw)
                .replaceFirst("^[\\-鈥?\\d\\s.銆?锛?锛塢+", "")
                .replaceFirst("^['\"鈥溾€濃€樷€橾+", "")
                .replaceFirst("['\"鈥溾€濃€樷€橾+$", "")
                .trim();
        if (value.startsWith("suggestions")) {
            return "";
        }
        if (value.startsWith("{") || value.startsWith("[")) {
            return "";
        }
        return value;
    }

    private static String polishSuggestion(String raw) {
        return nz(raw)
                .replaceFirst("^\\s*[-*\\u2022]+\\s*", "")
                .replaceFirst("^\\s*(?:\\d+|[A-Da-d])\\s*[\\).\\u3001:\\uff1a-]\\s*", "")
                .replaceFirst("^\\s*(?:User|USER|Human|Assistant|Character|AI|Me|Option\\s*\\d*)\\s*[:\\uff1a-]\\s*", "")
                .replaceFirst("^[\"'`\\u201c\\u201d\\u2018\\u2019]+", "")
                .replaceFirst("[\"'`\\u201c\\u201d\\u2018\\u2019]+$", "")
                .trim();
    }

    private static boolean isLowQualitySuggestion(String value) {
        String s = nz(value);
        if (s.length() < 2) {
            return true;
        }
        String lower = s.toLowerCase();
        String compact = lower.replaceAll("\\s+", "");
        if (lower.startsWith("suggestions") || lower.startsWith("option") || lower.startsWith("json")) {
            return true;
        }
        return lower.contains("as an ai")
                || lower.contains("as a language model")
                || lower.contains("what should i say")
                || lower.contains("tell me more")
                || lower.contains("need more context")
                || lower.contains("cannot provide")
                || compact.contains("\u4f5c\u4e3aai")
                || compact.contains("\u4f5c\u4e3a\u4e00\u4e2aai")
                || compact.contains("\u6211\u4e0d\u77e5\u9053\u8be5\u8bf4\u4ec0\u4e48")
                || compact.contains("\u4e0d\u77e5\u9053\u8bf4\u4ec0\u4e48")
                || compact.contains("\u9700\u8981\u66f4\u591a\u4e0a\u4e0b\u6587")
                || compact.contains("\u65e0\u6cd5\u63d0\u4f9b");
    }

    private static String normalizeSuggestionKey(String value) {
        return nz(value)
                .toLowerCase()
                .replaceAll("[\\s\\p{Punct}\\u3000-\\u303f\\uff00-\\uffef]+", "");
    }

    private AppMessage requireAssistantMessage(long conversationId, String rawMessageId, String label) {
        long targetMessageId;
        try {
            targetMessageId = Long.parseLong(rawMessageId);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.CONFLICT, label + " invalid");
        }
        AppMessage target = messageMapper.findById(targetMessageId);
        if (target == null
                || target.getConversationId() == null
                || target.getConversationId() != conversationId
                || !"assistant".equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, label + " not found");
        }
        String status = target.getStatus() == null ? "" : target.getStatus();
        if (!"SUCCESS".equalsIgnoreCase(status) && !"STOPPED".equalsIgnoreCase(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, label + " is not ready");
        }
        return target;
    }

    private void persistDisplayedSwipeVariant(AppMessage target) {
        if (target == null || target.getContent() == null || target.getContent().isBlank()) {
            return;
        }
        String ref = ensureSwipeRootRef(target, traceIdSafe());
        int currentIndex = target.getSwipeIndex() == null ? 0 : target.getSwipeIndex();
        AppMessage existing = messageMapper.findByStMessageRefAndSwipeIndex(ref, currentIndex);
        if (existing == null || existing.getId() == null || existing.getId().equals(target.getId())) {
            AppMessage clone = new AppMessage();
            clone.setUserId(target.getUserId());
            clone.setConversationId(target.getConversationId());
            clone.setRole(target.getRole());
            clone.setMessageKind(target.getMessageKind());
            clone.setContinueFromMessageId(target.getContinueFromMessageId());
            clone.setClientMessageId(
                    (target.getClientMessageId() == null ? "swipe" : target.getClientMessageId())
                            + "_idx_" + currentIndex + "_" + System.currentTimeMillis()
            );
            clone.setContent(target.getContent());
            clone.setStatus(target.getStatus() == null ? "SUCCESS" : target.getStatus());
            clone.setStMessageRef(ref);
            clone.setSwipeIndex(currentIndex);
            clone.setTraceId(traceIdSafe());
            messageMapper.insert(clone);
            messageMapper.incrementTotalMessageCounter();
            return;
        }
        if (!target.getContent().equals(existing.getContent())) {
            messageMapper.updateStatusAndContent(
                    existing.getId(),
                    target.getStatus() == null ? "SUCCESS" : target.getStatus(),
                    target.getContent(),
                    existing.getErrorCode(),
                    traceIdSafe()
            );
        }
    }

    static boolean includeVisibleMessage(AppMessage m) {
        if (m == null || m.getContent() == null || m.getContent().isBlank()) {
            return false;
        }
        String status = m.getStatus() == null ? "" : m.getStatus();
        if ("FAILED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status)) {
            return false;
        }
        if ("user".equalsIgnoreCase(m.getRole())) {
            return true;
        }
        if (!"assistant".equalsIgnoreCase(m.getRole())) {
            return false;
        }
        if (!"SUCCESS".equalsIgnoreCase(status) && !"STOPPED".equalsIgnoreCase(status)) {
            return false;
        }
        String ref = m.getStMessageRef();
        if (ref != null && ref.startsWith("root:")) {
            try {
                long rootId = Long.parseLong(ref.substring("root:".length()));
                return m.getId() != null && m.getId() == rootId;
            } catch (Exception ignored) {
                return true;
            }
        }
        return true;
    }

    private static String mergeContinuationText(String prefix, String suffix) {
        String base = prefix == null ? "" : prefix;
        String ext = suffix == null ? "" : suffix;
        if (ext.isBlank()) {
            return base;
        }
        if (base.isBlank()) {
            return ext.stripLeading();
        }
        if (needsSpaceBetween(base, ext)) {
            return base + " " + ext.stripLeading();
        }
        return base + ext;
    }

    private static boolean needsSpaceBetween(String prefix, String suffix) {
        if (prefix.isEmpty() || suffix.isEmpty()) {
            return false;
        }
        char last = prefix.charAt(prefix.length() - 1);
        char first = suffix.charAt(0);
        if (Character.isWhitespace(last) || Character.isWhitespace(first)) {
            return false;
        }
        if (isCjkChar(last) || isCjkChar(first) || isNoSpacePunctuation(first)) {
            return false;
        }
        return Character.isLetterOrDigit(last) && Character.isLetterOrDigit(first);
    }

    private static boolean isCjkChar(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    private static boolean isNoSpacePunctuation(char ch) {
        return ",.;:!?)]}\"'".indexOf(ch) >= 0;
    }
}
