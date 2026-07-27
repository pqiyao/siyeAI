package com.example.sillyspringboot.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.dto.AppChatStreamRequest;
import com.example.sillyspringboot.chat.dto.AppChatContinueRequest;
import com.example.sillyspringboot.chat.dto.AppChatRegenerateRequest;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.entity.AppLorebookEntry;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
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
import com.example.sillyspringboot.conversation.service.ConversationBranchService;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatMessage;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.ConversationIdentity;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.SwipeVariant;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.ops.service.ChatPresetService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String REPLY_SPLIT_NONE = "none";
    private static final String REPLY_SPLIT_BUBBLE = "bubble";
    private static final String REPLY_SPLIT_PARAGRAPH_LEGACY = "paragraph";
    private static final String REPLY_SPLIT_SPEECH_LEGACY = "speech";
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
            You are generating SillyTavern-style impersonation drafts for the HUMAN USER.
            Treat the previous messages as the active SillyTavern roleplay context, including character card, lorebook, memory, and chat history.
            Your job is to write candidate messages that the HUMAN USER could send next.
            
            Hard rules:
            - Keep the same primary language as the current conversation.
            - Write in the user's voice and point of view only.
            - Do not answer as the character, narrator, assistant, system, or AI.
            - Do not continue the character's last message.
            - Do not summarize or explain the conversation.
            - Do not include role/name prefixes, numbering, labels, markdown, or quotation marks around the options.
            - Avoid generic filler such as "tell me more", "I do not know", "what should I say", or "as an AI".
            - Each option must be directly sendable as one chat message.
            - Make the 4 options meaningfully different: gentle, playful/teasing, direct/active, emotionally deeper.
            - If the scene is intimate, tense, or awkward, preserve that tone without becoming crude or robotic.
            - Prefer 12-60 Chinese characters for Chinese conversations, or 6-35 words for English conversations.
            - Return JSON only, exactly in this shape: {"suggestions":["...","...","...","..."]}
            """;
    private static final String ST_IMPERSONATION_PROMPT = """
            Write your next reply from the point of view of {{user}}, using the chat history so far as a guideline for the writing style of {{user}}.
            Don't write as {{char}} or system. Don't describe actions of {{char}}.
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
    private final ConversationBranchService branchService;
    private final StWorldbookCatalogService worldbookCatalogService;
    private final ChatPresetService chatPresetService;
    private final AppChatCompatibilityService compatibilityService;
    private final AppChatFrontendBridgeService frontendBridgeService;
    private final H5EntitlementService entitlementService;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Autowired(required = false)
    private CharacterStudioMapper characterStudioMapper;

    @Autowired(required = false)
    private AppLorebookEntryMapper lorebookEntryMapper;

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
            ConversationMemoryAutoRefreshService memoryAutoRefreshService,
            ConversationBranchService branchService,
            StWorldbookCatalogService worldbookCatalogService,
            ChatPresetService chatPresetService,
            AppChatCompatibilityService compatibilityService,
            AppChatFrontendBridgeService frontendBridgeService,
            H5EntitlementService entitlementService
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
        this.branchService = branchService;
        this.worldbookCatalogService = worldbookCatalogService;
        this.chatPresetService = chatPresetService;
        this.compatibilityService = compatibilityService;
        this.frontendBridgeService = frontendBridgeService;
        this.entitlementService = entitlementService;
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

    public List<String> resolveWorldNamesForGeneration(long conversationId, String token) {
        AppUser user = tokenService.validateAndLoadUser(token);
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, user.getId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationBranch activeBranch = branchService.requireActiveBranch(conversation);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            return List.of();
        }
        return worldNamesForGeneration(conversationId, activeBranch.getId(), binding, conversation.getCharacterId());
    }

    public long requireActiveBranchId(long conversationId, String token) {
        AppUser user = tokenService.validateAndLoadUser(token);
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, user.getId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationBranch activeBranch = branchService.requireActiveBranch(conversation);
        return activeBranch.getId();
    }

    public String resolveRuntimePresetBundleForGeneration(long conversationId, String token) {
        AppUser user = tokenService.validateAndLoadUser(token);
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, user.getId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            return null;
        }
        return runtimePresetBundleForGeneration(binding);
    }

    public StStreamControl registerControl(long conversationId, StStreamControl control) {
        return runtimeRegistry.register(conversationId, control);
    }

    public boolean bindControlTask(long conversationId, long taskId, StStreamControl control) {
        return runtimeRegistry.bindTask(conversationId, taskId, control);
    }

    public void unregisterControl(long conversationId, StStreamControl control) {
        runtimeRegistry.unregister(conversationId, control);
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
        AppConversationBranch activeBranch = branchService.requireActiveBranch(conversation);
        RoleplayBundle bundle = resolveRoleplayBundle(conversation.getCharacterId(), userId);
        List<String> rawOpeningVariants = openingVariants(bundle);
        String rawOpening = rawOpeningVariants.isEmpty() ? "" : rawOpeningVariants.get(0);
        if (rawOpening.isBlank()) {
            return false;
        }
        List<AssistantOutputNormalization> normalizedOpenings = rawOpeningVariants.stream()
                .map(value -> normalizeAssistantOutput(conversationId, value, token))
                .toList();
        List<String> openingVariants = normalizedOpenings.stream()
                .map(AssistantOutputNormalization::content)
                .toList();
        AssistantOutputNormalization openingNormalization = normalizedOpenings.get(0);
        String opening = openingNormalization.content();

        List<AppMessage> existing = messageMapper.listByConversationBranchAsc(conversationId, activeBranch.getId(), 32);
        if (existing != null && !existing.isEmpty()) {
            List<AppMessage> visible = existing.stream()
                    .filter(AppChatService::includeVisibleMessage)
                    .toList();
            if (!visible.isEmpty()) {
                AppMessage openingMessage = findOpeningAssistantMessage(visible);
                if (repairBogusOpeningMessageIfNeeded(conversationId, activeBranch.getId(), visible, bundle, opening)) {
                    if (openingMessage != null) {
                        AppMessage repaired = messageMapper.findById(openingMessage.getId());
                        ensureOpeningSwipeVariantsForExisting(repaired, openingVariants);
                    }
                    return true;
                }
                if (openingMessage != null) {
                    ensureOpeningSwipeVariantsForExisting(openingMessage, openingVariants);
                }
                return false;
            }
        }

        AppMessage openingMessage = new AppMessage();
        openingMessage.setUserId(userId);
        openingMessage.setConversationId(conversationId);
        openingMessage.setBranchId(activeBranch.getId());
        openingMessage.setRole("assistant");
        openingMessage.setClientMessageId("opening_" + System.currentTimeMillis());
        openingMessage.setContent(opening);
        openingMessage.setStatus("SUCCESS");
        openingMessage.setTraceId(traceIdSafe());
        messageMapper.insert(openingMessage);
        messageMapper.incrementTotalMessageCounter();
        Long openingId = openingMessage.getId();
        if (openingId != null && openingId > 0) {
            String openingRef = "root:" + openingId;
            messageMapper.updateVariantMeta(openingId, openingRef, 0, traceIdSafe());
            insertOpeningSwipeVariants(openingMessage, openingVariants, openingRef);
            try {
                syncAssistantReplyToSt(
                        conversationId,
                        openingRef,
                        opening,
                        token,
                        openingNormalization.finalized()
                );
            } catch (Exception ex) {
                log.warn("opening sync to ST skipped conversationId={} messageId={} cause={}",
                        conversationId, openingId, rootCauseMessage(ex));
            }
        }

        String title = bundle.detail() == null ? "" : nz(bundle.detail().name());
        if (title != null && !title.isBlank()) {
            conversationMapper.setTitleIfNull(conversationId, title);
        } else {
            conversationMapper.setTitleToCharacterNameIfNull(conversationId);
        }
        chatAuditService.touchAfterAssistantContentUpdate(openingMessage.getId());
        snapshotService.saveSnapshotFromDb(conversationId, activeBranch.getId(), 800);
        return true;
    }

    public List<String> listOpeningVariants(long conversationId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        return openingVariants(resolveRoleplayBundle(conversation.getCharacterId(), userId));
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
        long activeBranchId = resolveRuntimeBranchId(c, stMessageRef);

        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(req.getConversationId());
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ST binding missing (avatar_url/file_name)");
        }
        snapshotService.saveSnapshotFromDb(req.getConversationId(), activeBranchId, 800, stMessageRef);
        UserModelOverride userModelOverride = resolveUserModelOverride(
                userId, req.getChatModelSource(), req.getChatModelName());
        boolean customModeSelected = isCustomModeSelected(userId, req.getChatModelSource());
        String userName = displayNameForSt(user, userId, binding);
        String charName = bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name());
        List<String> worldNames = worldNamesForGeneration(req.getConversationId(), activeBranchId, binding, c.getCharacterId());
        String tailMemoryPrompt = tailMemoryPromptForGeneration(req.getConversationId(), activeBranchId);
        String runtimePresetBundle = runtimePresetBundleForGeneration(binding);
        AppChatCompatibilityService.Decision compatibilityDecision =
                recordCompatibilityDecision(req.getConversationId(), bundle, binding, worldNames, runtimePresetBundle);
        List<String> inlineImageUrls = chatImageContentService.resolveInlineDataUrls(req.getImageUrls());
        String attachmentMode = normalizeAttachmentMode(req.getAttachmentMode(), inlineImageUrls);
        String attachmentHint = normalizeAttachmentHint(req.getAttachmentHint());
        boolean useInlineImages = !inlineImageUrls.isEmpty() && !ATTACHMENT_MODE_EXPRESSION.equals(attachmentMode);
        ensureVisionModelReadyForImages(
                userModelOverride,
                customModeSelected,
                useInlineImages ? inlineImageUrls : List.of()
        );
        String roleplayUserMessage = ATTACHMENT_MODE_PHOTO.equals(attachmentMode)
                ? buildPhotoRoleplayUserMessage(
                        req.getConversationId(),
                        userName,
                        charName,
                        worldNames,
                        req.getUserMessage(),
                        inlineImageUrls,
                        userModelOverride,
                        user,
                        normalizeVisionRequestId(req.getVisionRequestId(), req.getClientMessageId()),
                        control
                )
                : nz(req.getUserMessage());
        List<String> expressionHints = normalizeExpressionHints(req.getExpressionHints());
        List<String> avoidExpressionHints = normalizeExpressionHints(req.getAvoidExpressionHints());
        String runtimeUserMessage = buildRuntimeUserMessageForGeneration(
                roleplayUserMessage,
                attachmentMode,
                attachmentHint
        );
        String tailSystemPrompt = combineSystemPrompts(
                buildCharacterStudioRuntimePrompt(c.getCharacterId(), req.getConversationId(), activeBranchId, runtimeUserMessage),
                tailMemoryPrompt,
                buildExpressionTailPrompt(attachmentMode, expressionHints, avoidExpressionHints),
                buildReplySplitTailPrompt(req.getReplySplitMode())
        );
        boolean forwardInlineImages = useInlineImages && !ATTACHMENT_MODE_PHOTO.equals(attachmentMode);
        boolean needsPromptMessages = forwardInlineImages;
        List<ChatMessage> promptMessages = !needsPromptMessages
                ? List.of()
                : buildGeneratePromptMessages(
                        binding.getStAvatarUrl(),
                        binding.getStChatFileName(),
                        userName,
                        charName,
                        worldNames,
                        roleplayUserMessage,
                        forwardInlineImages ? inlineImageUrls : List.of(),
                        expressionHints,
                        avoidExpressionHints,
                        attachmentMode,
                        attachmentHint,
                        runtimePresetBundle
                );

        ChatGenerateRequest stReq = new ChatGenerateRequest(
                req.getConversationId(),
                runtimeUserMessage,
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
                userModelOverride,
                tailSystemPrompt,
                runtimePresetBundle,
                AiCapability.CHAT,
                req.getChatRouteKey()
        );

        if (tryFrontendBridge(
                stReq,
                compatibilityDecision,
                userModelOverride,
                needsPromptMessages,
                tailSystemPrompt,
                onChunk,
                control
        )) {
            return;
        }
        stAdapter.streamGenerateAssistantReply(stReq, onChunk, control);
    }

    // ===== Phase 5 stubs (will be implemented after ST chat snapshot wiring) =====

    public void streamContinue(AppChatContinueRequest req, String token, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(req.getConversationId(), userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        long activeBranchId = resolveRuntimeBranchId(c, req.getTargetMessageId());

        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(req.getConversationId());
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ST binding missing (avatar_url/file_name)");
        }
        snapshotService.saveSnapshotFromDb(req.getConversationId(), activeBranchId, 800);
        UserModelOverride userModelOverride = resolveUserModelOverride(
                userId, req.getChatModelSource(), req.getChatModelName());
        String userName = displayNameForSt(user, userId, binding);
        String charName = bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name());
        List<String> worldNames = worldNamesForGeneration(req.getConversationId(), activeBranchId, binding, c.getCharacterId());
        String tailMemoryPrompt = tailMemoryPromptForGeneration(req.getConversationId(), activeBranchId);
        String runtimePresetBundle = runtimePresetBundleForGeneration(binding);
        AppChatCompatibilityService.Decision compatibilityDecision =
                recordCompatibilityDecision(req.getConversationId(), bundle, binding, worldNames, runtimePresetBundle);
        List<ChatMessage> promptMessages = List.of();
        String tailSystemPrompt = combineSystemPrompts(
                buildCharacterStudioRuntimePrompt(c.getCharacterId(), req.getConversationId(), activeBranchId, ""),
                tailMemoryPrompt,
                buildReplySplitTailPrompt(req.getReplySplitMode())
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
                userModelOverride,
                tailSystemPrompt,
                runtimePresetBundle,
                AiCapability.CHAT,
                req.getChatRouteKey()
        );
        if (tryFrontendBridge(
                stReq,
                compatibilityDecision,
                userModelOverride,
                false,
                tailSystemPrompt,
                onChunk,
                control
        )) {
            return;
        }
        stAdapter.streamGenerateAssistantReply(stReq, onChunk, control);
    }

    public void streamRegenerate(AppChatRegenerateRequest req, String token, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(req.getConversationId(), userId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }

        // A：商用安全约束——仅允许 regenerate 会话最后一条 assistant
        long activeBranchId = resolveRuntimeBranchId(c, req.getTargetMessageId());
        long targetId;
        try {
            String raw = req.getTargetMessageId() == null ? "" : req.getTargetMessageId().trim();
            if (raw.startsWith("db_")) raw = raw.substring(3);
            targetId = Long.parseLong(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "targetMessageId invalid");
        }
        Long targetBranchId = branchIdFromMessageId(req.getConversationId(), targetId);
        if (targetBranchId != null && targetBranchId > 0) {
            activeBranchId = targetBranchId;
        }
        AppMessage lastAssistant = findLastVisibleAssistant(req.getConversationId(), activeBranchId);
        if (lastAssistant == null || lastAssistant.getId() == null || lastAssistant.getId().longValue() != targetId) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前仅支持重写最后一条回复");
        }

        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(req.getConversationId());
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ST binding missing (avatar_url/file_name)");
        }
        UserModelOverride userModelOverride = resolveUserModelOverride(
                userId, req.getChatModelSource(), req.getChatModelName());
        String userName = displayNameForSt(user, userId, binding);
        String charName = bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name());
        List<String> worldNames = worldNamesForGeneration(req.getConversationId(), activeBranchId, binding, c.getCharacterId());
        String tailMemoryPrompt = tailMemoryPromptForGeneration(req.getConversationId(), activeBranchId);
        String runtimePresetBundle = runtimePresetBundleForGeneration(binding);
        AppChatCompatibilityService.Decision compatibilityDecision =
                recordCompatibilityDecision(req.getConversationId(), bundle, binding, worldNames, runtimePresetBundle);
        List<ChatMessage> promptMessages = List.of();
        snapshotService.saveSnapshotFromDb(req.getConversationId(), activeBranchId, 800);
        String tailSystemPrompt = combineSystemPrompts(
                buildCharacterStudioRuntimePrompt(c.getCharacterId(), req.getConversationId(), activeBranchId, ""),
                tailMemoryPrompt,
                buildReplySplitTailPrompt(req.getReplySplitMode())
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
                userModelOverride,
                tailSystemPrompt,
                runtimePresetBundle,
                AiCapability.CHAT,
                req.getChatRouteKey()
        );
        if (tryFrontendBridge(
                stReq,
                compatibilityDecision,
                userModelOverride,
                false,
                tailSystemPrompt,
                onChunk,
                control
        )) {
            return;
        }
        stAdapter.streamGenerateAssistantReply(stReq, onChunk, control);
    }

    public List<String> suggestReplies(long conversationId, String token, String currentDraft) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationBranch activeBranch = branchService.requireActiveBranch(conversation);
        long activeBranchId = activeBranch.getId();

        RoleplayBundle bundle = resolveRoleplayBundle(conversation.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ST binding missing (avatar_url/file_name)");
        }

        String userName = displayNameForSt(user, userId, binding);
        String charName = bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name());
        snapshotService.saveSnapshotFromDb(conversationId, activeBranchId, 800);
        List<String> worldNames = worldNamesForGeneration(conversationId, activeBranchId, binding, conversation.getCharacterId());
        String runtimePresetBundle = runtimePresetBundleForGeneration(binding);
        List<Map<String, String>> runtimeMessages = stAdapter.buildRuntimeMessages(
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                userName,
                charName,
                List.of(),
                worldNames,
                runtimePresetBundle
        );
        if (runtimeMessages == null || runtimeMessages.isEmpty()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 帮答暂时不可用，请稍后再试");
        }

        try {
            String tailMemoryPrompt = combineSystemPrompts(
                    buildCharacterStudioRuntimePrompt(conversation.getCharacterId(), conversationId, activeBranchId, currentDraft),
                    tailMemoryPromptForGeneration(conversationId, activeBranchId)
            );
            List<ChatMessage> promptMessages = buildReplySuggestionMessages(
                    runtimeMessages,
                    currentDraft,
                    userName,
                    charName,
                    tailMemoryPrompt
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
                    userModelOverride,
                    null,
                    runtimePresetBundle
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
        throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 帮答暂时不可用，请稍后再试");
    }

    private List<ChatMessage> buildReplySuggestionMessages(
            List<Map<String, String>> runtimeMessages,
            String currentDraft,
            String userName,
            String charName,
            String tailMemoryPrompt
    ) {
        List<ChatMessage> contextMessages = new ArrayList<>(runtimeMessages == null ? 0 : runtimeMessages.size());
        List<String> deferredSystemPrompts = new ArrayList<>();
        boolean conversationStarted = false;
        if (runtimeMessages != null) {
            for (Map<String, String> message : runtimeMessages) {
                if (message == null) {
                    continue;
                }
                String role = normalizePromptRole(message.get("role"));
                String content = nz(message.get("content"));
                if (isCharacterNextReplyInstruction(role, content, charName)) {
                    continue;
                }
                if (!role.isBlank() && !content.isBlank()) {
                    if ("system".equals(role) && conversationStarted) {
                        deferredSystemPrompts.add(content);
                        continue;
                    }
                    if (!"system".equals(role)) {
                        conversationStarted = true;
                    }
                    contextMessages.add(ChatMessage.text(role, content));
                }
            }
        }
        if (contextMessages.isEmpty()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 帮答暂时不可用，请稍后再试");
        }
        String instruction = buildReplySuggestionInstruction(
                currentDraft,
                userName,
                charName,
                tailMemoryPrompt,
                deferredSystemPrompts
        );
        return normalizeReplySuggestionMessageOrder(contextMessages, instruction);
    }

    private static List<ChatMessage> normalizeReplySuggestionMessageOrder(
            List<ChatMessage> contextMessages,
            String finalInstruction
    ) {
        List<String> leadingSystemPrompts = new ArrayList<>();
        List<ChatMessage> conversationMessages = new ArrayList<>();
        boolean seenConversation = false;
        if (contextMessages != null) {
            for (ChatMessage message : contextMessages) {
                String role = normalizePromptRole(message == null ? "" : message.role());
                String content = nz(message == null ? "" : message.content());
                if (role.isBlank() || content.isBlank()) {
                    continue;
                }
                if ("system".equals(role) && !seenConversation) {
                    leadingSystemPrompts.add(content);
                    continue;
                }
                if ("system".equals(role)) {
                    appendCompatibleMessage(conversationMessages, "user", content);
                    seenConversation = true;
                    continue;
                }
                if ("tool".equals(role)) {
                    role = "user";
                }
                seenConversation = true;
                appendCompatibleMessage(conversationMessages, role, content);
            }
        }

        if (!conversationMessages.isEmpty() && "assistant".equals(conversationMessages.get(0).role())) {
            conversationMessages.add(0, ChatMessage.text("user", "[Start a new Chat]"));
        }
        appendCompatibleMessage(conversationMessages, "user", finalInstruction);

        List<ChatMessage> out = new ArrayList<>(conversationMessages.size() + 1);
        if (!leadingSystemPrompts.isEmpty()) {
            out.add(ChatMessage.text("system", String.join("\n\n", leadingSystemPrompts)));
        }
        out.addAll(conversationMessages);
        return out;
    }

    private static void appendCompatibleMessage(List<ChatMessage> messages, String role, String content) {
        String safeRole = normalizePromptRole(role);
        String safeContent = nz(content);
        if (safeRole.isBlank() || safeContent.isBlank()) {
            return;
        }
        if ("tool".equals(safeRole)) {
            safeRole = "user";
        }
        if (!messages.isEmpty() && safeRole.equals(messages.get(messages.size() - 1).role())) {
            ChatMessage last = messages.remove(messages.size() - 1);
            messages.add(ChatMessage.text(safeRole, nz(last.content()) + "\n\n" + safeContent));
            return;
        }
        messages.add(ChatMessage.text(safeRole, safeContent));
    }

    private static boolean isCharacterNextReplyInstruction(String role, String content, String charName) {
        if (!"system".equals(normalizePromptRole(role))) {
            return false;
        }
        String text = nz(content).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        String normalizedChar = nz(charName).toLowerCase(Locale.ROOT);
        if (!normalizedChar.isBlank()) {
            String expectedPrefix = "write " + normalizedChar + "'s next reply in a fictional chat";
            if (text.startsWith(expectedPrefix)) {
                return true;
            }
        }
        return text.startsWith("write ")
                && text.contains("'s next reply in a fictional chat between ");
    }

    private List<ChatMessage> buildRuntimePromptMessages(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> worldNames,
            String runtimePresetBundle
    ) {
        List<Map<String, String>> runtimeMessages = stAdapter.buildRuntimeMessages(
                avatarUrl,
                fileName,
                userName,
                charName,
                List.of(),
                worldNames,
                runtimePresetBundle
        );
        if (runtimeMessages == null || runtimeMessages.isEmpty()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "视觉聊天上下文暂时不可用，请稍后重试");
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
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "视觉聊天上下文暂时不可用，请稍后重试");
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
            String attachmentHint,
            String runtimePresetBundle
    ) {
        List<ChatMessage> promptMessages = buildRuntimePromptMessagesWithExpressionHints(
                avatarUrl,
                fileName,
                userName,
                charName,
                worldNames,
                expressionHints,
                avoidExpressionHints,
                runtimePresetBundle
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

    private String buildRuntimeUserMessageForGeneration(
            String roleplayUserMessage,
            String attachmentMode,
            String attachmentHint
    ) {
        if (ATTACHMENT_MODE_EXPRESSION.equals(attachmentMode)) {
            return buildAttachmentAwareUserMessage(roleplayUserMessage, List.of(), attachmentMode, attachmentHint);
        }
        return nz(roleplayUserMessage);
    }

    private String buildExpressionTailPrompt(
            String attachmentMode,
            List<String> expressionHints,
            List<String> avoidExpressionHints
    ) {
        if (!ATTACHMENT_MODE_EXPRESSION.equals(attachmentMode)) {
            return "";
        }
        List<String> safeHints = normalizeExpressionHints(expressionHints);
        if (safeHints.isEmpty()) {
            return "";
        }
        return buildExpressionHintSystemPrompt(safeHints, normalizeExpressionHints(avoidExpressionHints));
    }

    private static String buildReplySplitTailPrompt(String replySplitMode) {
        String mode = normalizeReplySplitMode(replySplitMode);
        if (REPLY_SPLIT_BUBBLE.equals(mode)) {
            return """
                    Reply formatting requirement:
                    - When the reply contains multiple semantic or roleplay beats, separate them with natural blank-line paragraph boundaries.
                    - Usually keep each paragraph to 1-2 related sentences; do not split every short sentence mechanically.
                    - Keep dialogue, its immediately related action, and meaningfully connected narration together when that reads more naturally.
                    - Keep the result as one assistant message. Do not create multiple chat messages.
                    - The client may display those paragraphs as several visual bubbles, but they remain one logical assistant reply.
                    - Do not change character behavior, story facts, memory, or roleplay continuity just to satisfy formatting.
                    """;
        }
        return "";
    }

    private static String normalizeReplySplitMode(String replySplitMode) {
        if (!StringUtils.hasText(replySplitMode)) {
            return REPLY_SPLIT_NONE;
        }
        String mode = replySplitMode.trim().toLowerCase(Locale.ROOT);
        if (REPLY_SPLIT_BUBBLE.equals(mode)
                || REPLY_SPLIT_PARAGRAPH_LEGACY.equals(mode)
                || REPLY_SPLIT_SPEECH_LEGACY.equals(mode)) {
            return REPLY_SPLIT_BUBBLE;
        }
        return REPLY_SPLIT_NONE;
    }

    private static String combineSystemPrompts(String... prompts) {
        if (prompts == null || prompts.length == 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String prompt : prompts) {
            String part = nz(prompt);
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return String.join("\n\n", parts);
    }

    private String buildPhotoRoleplayUserMessage(
            Long conversationId,
            String userName,
            String charName,
            List<String> worldNames,
            String userMessage,
            List<String> inlineImageUrls,
            UserModelOverride userModelOverride,
            AppUser user,
            String visionRequestId,
            StStreamControl control
    ) {
        String summary = summarizeUserImages(
                conversationId,
                userName,
                charName,
                worldNames,
                userMessage,
                inlineImageUrls,
                userModelOverride,
                user,
                visionRequestId,
                control
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
            UserModelOverride userModelOverride,
            AppUser user,
            String visionRequestId,
            StStreamControl control
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
                userModelOverride,
                null,
                null,
                AiCapability.VISION
        );
        StringBuilder raw = new StringBuilder();
        H5EntitlementService.AccessTicket visionTicket = userModelOverride == null
                ? entitlementService.guardVision(user, visionRequestId)
                : null;
        boolean chargeCreated = visionTicket != null && entitlementService.reserveVisionCharge(visionTicket);
        try {
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
        } catch (RuntimeException ex) {
            entitlementService.refundVisionCharge(visionTicket, chargeCreated);
            throw ex;
        }
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
            List<String> avoidExpressionHints,
            String runtimePresetBundle
    ) {
        List<ChatMessage> promptMessages = buildRuntimePromptMessages(
                avatarUrl,
                fileName,
                userName,
                charName,
                worldNames,
                runtimePresetBundle
        );
        return injectExpressionHintSystemMessage(promptMessages, expressionHints, avoidExpressionHints);
    }

    private void ensureVisionModelReadyForImages(
            UserModelOverride override,
            boolean customModeSelected,
            List<String> inlineImageUrls
    ) {
        if (inlineImageUrls == null || inlineImageUrls.isEmpty()) {
            return;
        }
        if (customModeSelected && override == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "自定义 API 配置无效，请先在 AI 设置页补全 API Key、主模型和视觉模型"
            );
        }
        if (!customModeSelected) {
            return;
        }
        String explicitVisionModel = nz(override.visionModelName());
        if (StringUtils.hasText(explicitVisionModel)) {
            return;
        }
        String textModel = override.textModelOrFallback();
        if (looksLikeVisionCapableModel(textModel)) {
            return;
        }
        throw new BusinessException(
                ErrorCode.VALIDATION_FAILED,
                "当前主聊天模型不像视觉模型。发送照片或表情前，请先在 AI 设置页填写辅助视觉模型"
        );
    }

    private static String normalizeVisionRequestId(String raw, String clientMessageId) {
        String value = nz(raw).trim();
        if (value.isBlank()) {
            value = "vision_" + nz(clientMessageId).trim();
        }
        if (!value.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "识图请求 ID 格式不合法");
        }
        return value;
    }

    private boolean looksLikeVisionCapableModel(String modelName) {
        String text = nz(modelName).toLowerCase();
        if (text.isBlank()) {
            return false;
        }
        return text.matches(".*(vl|vision|multimodal|image-to-text|vision-language|omni|4o|4v|5v).*")
                || text.contains("gemini")
                || text.contains("claude")
                || text.contains("glm-4v")
                || text.contains("glm-5v");
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
        sb.append("- Do not output bracketed mood tags, sticker names, or file-like text such as [馋], (开心), 疯狂暗示.jpg, xxx.png, or similar label text in visible reply.\n");
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
     * 方案一（StepA）：生成成功后将 assistant 最终回复写回 ST chat，保证 ST 为运行时事实源。
     */
    public boolean syncAssistantReplyToSt(long conversationId, String stMessageRef, String assistantContent, String token) {
        return syncAssistantReplyToSt(conversationId, stMessageRef, assistantContent, token, null);
    }

    public boolean syncAssistantReplyToSt(
            long conversationId,
            String stMessageRef,
            String assistantContent,
            String token,
            boolean outputRegexApplied
    ) {
        return syncAssistantReplyToSt(
                conversationId,
                stMessageRef,
                assistantContent,
                token,
                Boolean.valueOf(outputRegexApplied)
        );
    }

    private boolean syncAssistantReplyToSt(
            long conversationId,
            String stMessageRef,
            String assistantContent,
            String token,
            Boolean outputRegexApplied
    ) {
        if (assistantContent == null) return false;
        if (assistantContent.isBlank() && !Boolean.TRUE.equals(outputRegexApplied)) return false;
        ChatGenerateRequest req = resolveAssistantRuntimeRequest(conversationId, stMessageRef, token);
        if (req == null) return false;
        if (outputRegexApplied == null) {
            stAdapter.appendAssistantMessage(req, assistantContent);
        } else {
            stAdapter.appendAssistantMessage(req, assistantContent, outputRegexApplied);
        }
        return true;
    }

    public record AssistantOutputNormalization(String content, boolean finalized) {
        public static AssistantOutputNormalization passthrough(String content) {
            return new AssistantOutputNormalization(content == null ? "" : content.trim(), false);
        }
    }

    public AssistantOutputNormalization normalizeAssistantOutput(
            long conversationId,
            String assistantContent,
            String token
    ) {
        String raw = assistantContent == null ? "" : assistantContent.trim();
        if (raw.isBlank()) {
            return AssistantOutputNormalization.passthrough(raw);
        }
        try {
            ChatGenerateRequest request = resolveAssistantRuntimeRequest(conversationId, "", token);
            if (request == null) {
                return new AssistantOutputNormalization(raw, true);
            }
            String normalized = stAdapter.applyAssistantOutputRegex(request, raw);
            if (normalized == null) {
                log.warn("assistant output regex returned null conversationId={}", conversationId);
                return new AssistantOutputNormalization(raw, true);
            }
            // Canonicalize surrounding whitespace once here; all downstream stores receive this value unchanged.
            return new AssistantOutputNormalization(normalized.trim(), true);
        } catch (Exception ex) {
            log.warn("assistant output regex fallback conversationId={} cause={}",
                    conversationId, rootCauseMessage(ex));
            return new AssistantOutputNormalization(raw, true);
        }
    }

    private ChatGenerateRequest resolveAssistantRuntimeRequest(
            long conversationId,
            String stMessageRef,
            String token
    ) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) return null;
        AppConversationBranch activeBranch = branchService.requireActiveBranch(c);
        Long targetBranchId = branchIdFromMessageRef(conversationId, stMessageRef);
        if (targetBranchId != null && targetBranchId > 0 && targetBranchId.longValue() != activeBranch.getId()) {
            return null;
        }
        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            return null;
        }
        return new ChatGenerateRequest(
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
                worldNamesForGeneration(conversationId, activeBranch.getId(), binding, c.getCharacterId()),
                null
        );
    }

    private void syncContinuationReplyToSt(
            long conversationId,
            long anchorAssistantId,
            String continuationContent,
            String token,
            boolean outputRegexApplied
    ) {
        if (continuationContent == null || continuationContent.isBlank()) return;
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) return;

        AppMessage anchor = messageMapper.findById(anchorAssistantId);
        if (anchor == null || anchor.getConversationId() == null || anchor.getConversationId() != conversationId) return;
        if (!"assistant".equalsIgnoreCase(anchor.getRole())) return;
        AppConversationBranch activeBranch = branchService.requireActiveBranch(c);
        if (anchor.getBranchId() == null || anchor.getBranchId().longValue() != activeBranch.getId()) return;

        RoleplayBundle bundle = resolveRoleplayBundle(c.getCharacterId(), userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            return;
        }

        String mergedContent = mergeContinuationForSt(anchor.getContent(), continuationContent);
        if (!StringUtils.hasText(mergedContent)) return;
        String stRef = StringUtils.hasText(anchor.getStMessageRef())
                ? anchor.getStMessageRef()
                : "root:" + anchorAssistantId;

        ChatGenerateRequest req = new ChatGenerateRequest(
                conversationId,
                null,
                List.of(),
                "continue_sync_" + System.currentTimeMillis(),
                false,
                "continue-sync",
                Set.of(),
                displayNameForSt(user, userId, binding),
                bundle == null || bundle.detail() == null ? "" : nz(bundle.detail().name()),
                List.of(),
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                stRef,
                worldNamesForGeneration(conversationId, activeBranch.getId(), binding, c.getCharacterId()),
                null
        );
        stAdapter.replaceLastAssistantMessage(req, mergedContent, outputRegexApplied);
    }

    private static String mergeContinuationForSt(String baseContent, String continuationContent) {
        String base = baseContent == null ? "" : baseContent;
        String suffix = continuationContent == null ? "" : continuationContent;
        if (base.isBlank()) return suffix.trim();
        if (suffix.isBlank()) return base.trim();
        return base + suffix;
    }

    /**
     * A：swipe 切换后让 ST chat 的最后一条 assistant 与“当前展示版本”一致。
     * 先做商用安全：仅允许同步“会话最后一条 assistant”。
     */
    public void syncSwipeSelectionToSt(long conversationId, long assistantMessageId, String token) {
        // Swipe variants stored in app_message have already crossed the output-regex boundary.
        syncSwipeSelectionToSt(conversationId, assistantMessageId, token, true);
    }

    private void syncSwipeSelectionToSt(
            long conversationId,
            long assistantMessageId,
            String token,
            boolean outputRegexApplied
    ) {
        AppUser user = tokenService.validateAndLoadUser(token);
        long userId = user.getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) return;
        AppConversationBranch activeBranch = branchService.requireActiveBranch(c);
        long activeBranchId = activeBranch.getId();

        AppMessage target = messageMapper.findById(assistantMessageId);
        if (target == null || target.getConversationId() == null || target.getConversationId() != conversationId) return;
        if (!"assistant".equalsIgnoreCase(target.getRole())) return;
        if (target.getBranchId() == null || target.getBranchId().longValue() != activeBranchId) return;

        // 仅允许最后一条 assistant（商用强约束，避免误改历史造成上下文错乱）
        AppMessage lastAssistant = findLastVisibleAssistant(conversationId, activeBranchId);
        if (lastAssistant == null || lastAssistant.getId() == null || lastAssistant.getId().longValue() != assistantMessageId) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能对最后一条回复进行 swipe/同步");
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
                worldNamesForGeneration(conversationId, activeBranchId, binding, c.getCharacterId()),
                null
        );
        stAdapter.replaceLastAssistantMessage(stReq, target.getContent(), outputRegexApplied);
    }

    private UserModelOverride resolveUserModelOverride(long userId) {
        return userAiProviderService == null ? null : userAiProviderService.resolveActiveOverrideForUser(userId);
    }

    private UserModelOverride resolveUserModelOverride(long userId, String source, String selectedModelName) {
        String normalizedSource = nz(source).trim().toUpperCase(java.util.Locale.ROOT);
        if ("SYSTEM".equals(normalizedSource)) {
            return null;
        }
        UserModelOverride base = resolveUserModelOverride(userId);
        if (!"BYOK".equals(normalizedSource)) {
            return base;
        }
        if (base == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "自定义 API 配置不可用");
        }
        String model = nz(selectedModelName).trim();
        if (model.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择自己的聊天模型");
        }
        return new UserModelOverride(
                base.providerSource(), model, base.visionModelName(), base.audioModelName(),
                base.sttModelName(), base.ttsModelName(), base.ttsVoiceName(),
                base.ttsProviderSource(), base.ttsApiKey(), base.ttsCustomUrl(),
                base.imageModelName(), base.imageProviderSource(), base.imageApiKey(), base.imageCustomUrl(),
                base.apiKey(), base.customUrl()
        );
    }

    private boolean isCustomModeSelected(long userId, String source) {
        String normalizedSource = nz(source).trim().toUpperCase(java.util.Locale.ROOT);
        if ("SYSTEM".equals(normalizedSource)) return false;
        if ("BYOK".equals(normalizedSource)) return true;
        return userAiProviderService != null && userAiProviderService.isCustomModeSelectedForUser(userId);
    }

    private void triggerMemoryRefreshAfterCommit(long conversationId) {
        triggerMemoryRefreshAfterCommit(conversationId, null);
    }

    private void triggerMemoryRefreshAfterCommit(long conversationId, Long branchId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            memoryAutoRefreshService.maybeTriggerAfterGenerationSuccess(conversationId, branchId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                memoryAutoRefreshService.maybeTriggerAfterGenerationSuccess(conversationId, branchId);
            }
        });
    }

    private static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private final com.fasterxml.jackson.databind.ObjectMapper worldMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Keeps the structured character studio authoritative without replacing the existing SillyTavern
     * snapshot contract. Legacy cards have no studio rows and therefore produce an empty prompt here.
     */
    private String buildCharacterStudioRuntimePrompt(
            long characterId,
            long conversationId,
            long branchId,
            String currentInput
    ) {
        if (characterStudioMapper == null && lorebookEntryMapper == null) {
            return "";
        }
        try {
            List<AppCharacterMember> members = characterStudioMapper == null
                    ? List.of()
                    : characterStudioMapper.listMembers(characterId);
            List<AppLorebookEntry> loreEntries = lorebookEntryMapper == null
                    ? List.of()
                    : lorebookEntryMapper.listEnabledByCharacterId(characterId);
            if (members.isEmpty() && loreEntries.isEmpty()) {
                return "";
            }

            StringBuilder prompt = new StringBuilder();
            if (members.size() > 1) {
                prompt.append("Ensemble roleplay rules:\n")
                        .append("- This card has multiple independent characters. Never merge their personalities or invent a member outside this roster.\n")
                        .append("- Reply only for the member or members naturally present in the scene. Do not speak for the user.\n")
                        .append("- Prefix each character segment exactly as 【Name】. Use 【旁白】 only for scene narration when needed.\n")
                        .append("- Keep a member's personality, knowledge and relationships consistent across the full conversation.\n")
                        .append("Roster:\n");
                for (AppCharacterMember member : members) {
                    if (member == null || !StringUtils.hasText(member.getName())) {
                        continue;
                    }
                    prompt.append("- ").append(member.getName());
                    if (Boolean.TRUE.equals(member.getPrimaryMember())) {
                        prompt.append(" (primary)");
                    }
                    String tagline = nz(member.getTagline());
                    String persona = nz(member.getPersona());
                    if (!tagline.isBlank()) prompt.append(": ").append(tagline);
                    if (!persona.isBlank()) prompt.append("\n  Persona: ").append(persona);
                    prompt.append('\n');
                }
            }

            if (!loreEntries.isEmpty()) {
                List<AppMessage> history = messageMapper.listByConversationBranchAsc(conversationId, branchId, 200);
                String beforeCharacter = matchedStudioLore(loreEntries, history, currentInput, "BEFORE_CHARACTER", members);
                String afterCharacter = matchedStudioLore(loreEntries, history, currentInput, "AFTER_CHARACTER", members);
                String beforeHistory = matchedStudioLore(loreEntries, history, currentInput, "BEFORE_HISTORY", members);
                appendStudioLoreSection(prompt, "Worldbook facts before character", beforeCharacter);
                appendStudioLoreSection(prompt, "Worldbook facts after character", afterCharacter);
                appendStudioLoreSection(prompt, "Worldbook facts for current history", beforeHistory);
            }
            return prompt.length() > 16000 ? prompt.substring(0, 16000) : prompt.toString().trim();
        } catch (Exception ex) {
            log.warn("character studio runtime prompt skipped characterId={} conversationId={} cause={}",
                    characterId, conversationId, rootCauseMessage(ex));
            return "";
        }
    }

    private String matchedStudioLore(
            List<AppLorebookEntry> entries,
            List<AppMessage> history,
            String currentInput,
            String injectionPosition,
            List<AppCharacterMember> members
    ) {
        StringBuilder result = new StringBuilder();
        for (AppLorebookEntry entry : entries) {
            if (entry == null || !StringUtils.hasText(entry.getContent())
                    || !injectionPosition.equalsIgnoreCase(nz(entry.getInjectionPosition()))) {
                continue;
            }
            if (!Boolean.TRUE.equals(entry.getConstantInjection())
                    && !studioLoreMatches(entry, history, currentInput)) {
                continue;
            }
            String title = nz(entry.getTitle());
            if (title.isBlank()) title = "Worldbook";
            result.append("[ ").append(title).append(" ]");
            String scopedMember = studioMemberName(entry.getMemberId(), members);
            if (!scopedMember.isBlank()) result.append(" (member: ").append(scopedMember).append(')');
            result.append('\n').append(entry.getContent().trim()).append("\n\n");
            if (result.length() >= 9000) break;
        }
        return result.toString().trim();
    }

    private boolean studioLoreMatches(AppLorebookEntry entry, List<AppMessage> history, String currentInput) {
        int depth = entry.getScanDepth() == null ? 8 : Math.max(1, Math.min(entry.getScanDepth(), 100));
        StringBuilder corpus = new StringBuilder(nz(currentInput));
        int start = Math.max(0, (history == null ? 0 : history.size()) - depth);
        if (history != null) {
            for (int i = start; i < history.size(); i++) {
                AppMessage message = history.get(i);
                if (includeVisibleMessage(message)) corpus.append('\n').append(nz(message.getContent()));
            }
        }
        String haystack = corpus.toString().toLowerCase(Locale.ROOT);
        List<String> primary = studioKeywords(entry.getKeywordsCsv());
        if (primary.isEmpty()) return false;
        boolean all = "ALL".equalsIgnoreCase(nz(entry.getMatchMode()));
        boolean primaryMatches = all
                ? primary.stream().allMatch(keyword -> haystack.contains(keyword))
                : primary.stream().anyMatch(keyword -> haystack.contains(keyword));
        if (!primaryMatches) return false;
        List<String> secondary = studioKeywords(entry.getSecondaryKeywordsCsv());
        return secondary.isEmpty() || secondary.stream().anyMatch(keyword -> haystack.contains(keyword));
    }

    private static List<String> studioKeywords(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        return List.of(csv.split(",")).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static String studioMemberName(Long memberId, List<AppCharacterMember> members) {
        if (memberId == null || members == null) return "";
        for (AppCharacterMember member : members) {
            if (member != null && memberId.equals(member.getId())) return nz(member.getName());
        }
        return "";
    }

    private static void appendStudioLoreSection(StringBuilder target, String title, String body) {
        if (body == null || body.isBlank()) return;
        target.append(title).append(":\n").append(body).append("\n\n");
    }

    private List<String> worldNamesForGeneration(long conversationId, AppConversationStBinding binding, long characterId) {
        return worldNamesForGeneration(conversationId, null, binding, characterId);
    }

    private List<String> worldNamesForGeneration(long conversationId, Long branchId, AppConversationStBinding binding, long characterId) {
        List<String> baseWorldNames = parseWorldNames(binding, characterId);
        if (memoryAttachService == null) {
            return baseWorldNames;
        }
        try {
            return branchId == null || branchId <= 0
                    ? memoryAttachService.attachMemoryWorldbookIfAvailable(conversationId, baseWorldNames)
                    : memoryAttachService.attachMemoryWorldbookIfAvailable(conversationId, branchId, baseWorldNames);
        } catch (Exception ex) {
            log.warn("conversation memory worldbook attach skipped conversationId={} cause={}",
                    conversationId, rootCauseMessage(ex));
            return baseWorldNames;
        }
    }

    private String tailMemoryPromptForGeneration(long conversationId) {
        return tailMemoryPromptForGeneration(conversationId, null);
    }

    private String tailMemoryPromptForGeneration(long conversationId, Long branchId) {
        try {
            if (memoryAttachService == null) {
                return "";
            }
            return branchId == null || branchId <= 0
                    ? memoryAttachService.buildTailMemoryPromptFallbackIfWorldbookUnavailable(conversationId)
                    : memoryAttachService.buildTailMemoryPromptFallbackIfWorldbookUnavailable(conversationId, branchId);
        } catch (Exception ex) {
            log.warn("conversation memory tail prompt skipped conversationId={} cause={}",
                    conversationId, rootCauseMessage(ex));
            return "";
        }
    }

    private String runtimePresetBundleForGeneration(AppConversationStBinding binding) {
        return chatPresetService == null ? null : chatPresetService.resolveRuntimePresetBundle(binding);
    }

    private AppChatCompatibilityService.Decision recordCompatibilityDecision(
            long conversationId,
            RoleplayBundle bundle,
            AppConversationStBinding binding,
            List<String> worldNames,
            String runtimePresetBundle
    ) {
        if (compatibilityService == null) {
            return null;
        }
        try {
            AppChatCompatibilityService.Decision decision = compatibilityService.decideForGeneration(
                    conversationId,
                    null,
                    bundle == null ? null : bundle.detail(),
                    binding,
                    worldNames,
                    runtimePresetBundle
            );
            if (decision != null && "blocked".equals(decision.effectiveMode())) {
                throw new BusinessException(
                        ErrorCode.UPSTREAM_ERROR,
                        "high compatibility frontend bridge is required but not ready"
                );
            }
            return decision;
        } catch (Exception ex) {
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            log.warn("chat compatibility decision skipped conversationId={} cause={}",
                    conversationId, rootCauseMessage(ex));
            return null;
        }
    }

    private boolean tryFrontendBridge(
            ChatGenerateRequest request,
            AppChatCompatibilityService.Decision decision,
            UserModelOverride userModelOverride,
            boolean hasPromptMessages,
            String tailSystemPrompt,
            Consumer<ChatGenerateChunk> onChunk,
            StStreamControl control
    ) {
        if (decision == null || !"frontend_bridge".equals(decision.effectiveMode())) {
            return false;
        }
        String skipReason = frontendBridgeSkipReason(userModelOverride, hasPromptMessages, tailSystemPrompt);
        if (!skipReason.isBlank()) {
            return frontendBridgeSkipped(decision, request, skipReason);
        }
        if (frontendBridgeService == null || !frontendBridgeService.enabled()) {
            return frontendBridgeSkipped(decision, request, "bridge_disabled");
        }
        if (!frontendBridgeService.hasOnlineWorker()) {
            return frontendBridgeSkipped(decision, request, "bridge_worker_offline");
        }
        try {
            frontendBridgeService.streamGenerate(request, onChunk, control);
            return true;
        } catch (BusinessException ex) {
            if (!decision.fallbackToRuntime()) {
                throw ex;
            }
            log.warn("frontend bridge failed and runtime fallback will be used conversationId={} mode={} cause={}",
                    request.conversationId(), request.mode(), rootCauseMessage(ex));
            return false;
        } catch (Exception ex) {
            if (!decision.fallbackToRuntime()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "frontend bridge failed", ex);
            }
            log.warn("frontend bridge failed and runtime fallback will be used conversationId={} mode={} cause={}",
                    request.conversationId(), request.mode(), rootCauseMessage(ex));
            return false;
        }
    }

    private boolean frontendBridgeSkipped(
            AppChatCompatibilityService.Decision decision,
            ChatGenerateRequest request,
            String reason
    ) {
        if (!decision.fallbackToRuntime()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "frontend bridge required but skipped: " + reason);
        }
        log.info("frontend bridge skipped conversationId={} mode={} reason={} fallback=runtime",
                request.conversationId(), request.mode(), reason);
        return false;
    }

    private String frontendBridgeSkipReason(
            UserModelOverride userModelOverride,
            boolean hasPromptMessages,
            String tailSystemPrompt
    ) {
        if (userModelOverride != null) {
            return "user_model_override";
        }
        if (hasPromptMessages) {
            return "spring_prompt_messages";
        }
        if (StringUtils.hasText(tailSystemPrompt)) {
            return "tail_system_prompt";
        }
        return "";
    }

    private List<String> parseWorldNames(AppConversationStBinding binding, long characterId) {
        if (binding != null) {
            String rawConversationWorldNames = binding.getStWorldNamesJson();
            List<String> conversationWorldNames = parseWorldNamesJson(rawConversationWorldNames);
            if (!conversationWorldNames.isEmpty()) {
                return conversationWorldNames;
            }
            if (StringUtils.hasText(rawConversationWorldNames)) {
                return List.of();
            }
        }
        if (characterId > 0) {
            AppCharacter character = characterMapper.findById(characterId);
            if (character != null) {
                List<String> characterWorldNames = parseWorldNamesJson(character.getStWorldNamesJson());
                if (!characterWorldNames.isEmpty()) {
                    return characterWorldNames;
                }
                List<String> sameNameWorldNames = inferSameNameWorldbook(character);
                if (!sameNameWorldNames.isEmpty()) {
                    return sameNameWorldNames;
                }
            }
        }
        return List.of();
    }

    private List<String> inferSameNameWorldbook(AppCharacter character) {
        if (character == null || worldbookCatalogService == null) {
            return List.of();
        }
        List<String> candidates = sameNameWorldbookCandidates(character);
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            List<String> matched = worldbookCatalogService.normalizeAndFilterAvailableWorldNames(candidates);
            if (matched == null || matched.isEmpty()) {
                return List.of();
            }
            String worldName = matched.get(0);
            log.info("same-name worldbook attached characterId={} worldName={} candidates={}",
                    character.getId(), worldName, candidates);
            return List.of(worldName);
        } catch (Exception ex) {
            log.debug("same-name worldbook inference skipped characterId={} candidates={} cause={}",
                    character.getId(), candidates, rootCauseMessage(ex));
            return List.of();
        }
    }

    private List<String> sameNameWorldbookCandidates(AppCharacter character) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addWorldbookCandidate(candidates, avatarBaseName(character.getStAvatarUrl()));
        addWorldbookCandidate(candidates, character.getName());
        return List.copyOf(candidates);
    }

    private void addWorldbookCandidate(Set<String> candidates, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (StringUtils.hasText(value)) {
            candidates.add(value);
        }
    }

    private String avatarBaseName(String rawAvatarUrl) {
        String value = rawAvatarUrl == null ? "" : rawAvatarUrl.trim();
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int queryAt = value.indexOf('?');
        if (queryAt >= 0) {
            value = value.substring(0, queryAt);
        }
        int hashAt = value.indexOf('#');
        if (hashAt >= 0) {
            value = value.substring(0, hashAt);
        }
        value = value.replace('\\', '/');
        int slashAt = value.lastIndexOf('/');
        if (slashAt >= 0 && slashAt + 1 < value.length()) {
            value = value.substring(slashAt + 1);
        }
        int dotAt = value.lastIndexOf('.');
        if (dotAt > 0) {
            value = value.substring(0, dotAt);
        }
        return value.trim();
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

    private long resolveRuntimeBranchId(AppConversation conversation, String messageRefOrId) {
        Long branchId = branchIdFromMessageRef(conversation.getId(), messageRefOrId);
        if (branchId != null && branchId > 0) {
            return branchId;
        }
        AppConversationBranch activeBranch = branchService.requireActiveBranch(conversation);
        return activeBranch.getId();
    }

    private Long branchIdFromMessageRef(long conversationId, String messageRefOrId) {
        String raw = messageRefOrId == null ? "" : messageRefOrId.trim();
        if (raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("root:")) {
            raw = raw.substring("root:".length());
        } else if (raw.startsWith("db_")) {
            raw = raw.substring(3);
        } else if (raw.startsWith("client:")) {
            return null;
        }
        try {
            long messageId = Long.parseLong(raw);
            return branchIdFromMessageId(conversationId, messageId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long branchIdFromMessageId(long conversationId, long messageId) {
        if (messageId <= 0) {
            return null;
        }
        AppMessage message = messageMapper.findById(messageId);
        if (message == null
                || message.getConversationId() == null
                || message.getConversationId().longValue() != conversationId
                || message.getBranchId() == null
                || message.getBranchId() <= 0) {
            return null;
        }
        return message.getBranchId();
    }

    private AppMessage findLastVisibleAssistant(long conversationId) {
        Long branchId = null;
        try {
            AppConversation conversation = conversationMapper.findById(conversationId);
            if (conversation != null) {
                AppConversationBranch branch = branchService.requireActiveBranch(conversation);
                branchId = branch == null ? null : branch.getId();
            }
        } catch (Exception ignored) {
        }
        return branchId == null || branchId <= 0
                ? findLastVisibleAssistantFromRows(messageMapper.listByConversation(conversationId, 2000))
                : findLastVisibleAssistant(conversationId, branchId);
    }

    private AppMessage findLastVisibleAssistant(long conversationId, long branchId) {
        return findLastVisibleAssistantFromRows(messageMapper.listByConversationBranch(conversationId, branchId, 2000));
    }

    private AppMessage findLastVisibleAssistantFromRows(List<AppMessage> rows) {
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
        AppConversationBranch activeBranch = branchService.requireActiveBranch(c);
        long activeBranchId = activeBranch.getId();

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
        if (target.getBranchId() == null || target.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (!"assistant".equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "only assistant messages support swipe");
        }

        String stRef = ensureSwipeRootRef(target, traceIdSafe());
        List<AppMessage> rows = messageMapper.listByStMessageRefAndBranch(stRef, activeBranchId);
        return rows.stream()
                .filter(m -> "SUCCESS".equalsIgnoreCase(m.getStatus()))
                .filter(m -> m.getSwipeIndex() != null)
                .filter(m -> m.getContent() != null)
                .map(m -> new SwipeVariant(
                        String.valueOf(mid),
                        m.getSwipeIndex(),
                        m.getContent(),
                        m.getCreatedAt() == null ? Instant.now() : m.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
                ))
                .toList();
    }

    public String getAssistantMessageContent(long conversationId, long messageId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationBranch activeBranch = branchService.requireActiveBranch(conversation);
        AppMessage message = messageMapper.findById(messageId);
        if (message == null
                || message.getConversationId() == null
                || message.getConversationId() != conversationId
                || message.getBranchId() == null
                || message.getBranchId().longValue() != activeBranch.getId()
                || !"assistant".equalsIgnoreCase(message.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "assistant message not found");
        }
        return message.getContent() == null ? "" : message.getContent();
    }

    @Transactional
    public SwipeVariant switchSwipe(long conversationId, String messageId, int variantIndex, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        AppConversationBranch activeBranch = branchService.requireActiveBranch(c);
        long activeBranchId = activeBranch.getId();

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
        if (target.getBranchId() == null || target.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (!"assistant".equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "only assistant messages support swipe");
        }

        String stRef = ensureSwipeRootRef(target, traceIdSafe());
        persistDisplayedSwipeVariant(target);
        AppMessage chosen = messageMapper.findByStMessageRefAndSwipeIndexAndBranch(stRef, variantIndex, activeBranchId);
        if (chosen == null && target.getSwipeIndex() != null && target.getSwipeIndex() == variantIndex) {
            chosen = target;
        }
        if (chosen == null || chosen.getContent() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "swipe variant not found");
        }

        Integer previousSwipeIndex = target.getSwipeIndex();
        messageMapper.updateStatusAndContent(mid, "SUCCESS", chosen.getContent(), null, traceIdSafe());
        messageMapper.updateVariantMeta(mid, stRef, variantIndex, traceIdSafe());
        snapshotService.saveSnapshotFromDb(conversationId, activeBranchId, 800);
        try {
            syncSwipeSelectionToSt(conversationId, mid, token);
        } catch (Exception ignored) {
        }
        if (previousSwipeIndex == null || previousSwipeIndex != variantIndex) {
            branchService.incrementMemorySourceRevision(conversationId, activeBranchId);
            memoryAutoRefreshService.triggerAfterHistoryChange(conversationId, activeBranchId);
        }
        return new SwipeVariant(String.valueOf(mid), variantIndex, chosen.getContent(), Instant.now());
    }

    /**
     * regenerate闁挎稒鑹鹃惃銏ゅ嫉椤掍緡鍋ч柣銏㈠枑閸ㄦ氨绱掗幘瀵镐函闁谎勬椤斿洦绋?target 闁汇劌瀚弻?variant闁挎稑鑻懟鐔割渶濡鍚囬柛鎺戞处瀹曟煡宕氶幏宀婂殙 variant闁?     *
     * @param conversationId 濞村吋淇洪惁?
     * @param targetMessageId 閻炴凹鍋婇崳鎼佹偨閻旂儤鐣?assistant 婵炴垵鐗婃导?id
     * @param generatedMessageId 闁哄牜鍓氶濂告偨閻旂鐏囬柛鎰懃閸欏棝鎯?assistant 婵炴垵鐗婃导?id闁挎稑鐗忛弫杈┾偓瀛ゃ値鍚€闂佺偓宕橀惌楣冨礆濞戞绱﹂柨?     */
    @Transactional
    public SwipeVariant promoteRegenerateVariant(long conversationId, long targetMessageId, long generatedMessageId, String token) {
        return promoteRegenerateVariant(conversationId, targetMessageId, generatedMessageId, token, true, false);
    }

    @Transactional
    public SwipeVariant promoteRegenerateVariant(
            long conversationId,
            long targetMessageId,
            long generatedMessageId,
            String token,
            boolean syncRuntimeStateToSt
    ) {
        return promoteRegenerateVariant(
                conversationId,
                targetMessageId,
                generatedMessageId,
                token,
                syncRuntimeStateToSt,
                false
        );
    }

    @Transactional
    public SwipeVariant promoteRegenerateVariant(
            long conversationId,
            long targetMessageId,
            long generatedMessageId,
            String token,
            boolean syncRuntimeStateToSt,
            boolean outputRegexApplied
    ) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        AppConversationBranch activeBranch = branchService.requireActiveBranch(c);
        long activeBranchId = activeBranch.getId();

        AppMessage target = messageMapper.findById(targetMessageId);
        if (target == null || target.getConversationId() == null || target.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "target message not found");
        }
        if (target.getBranchId() == null || target.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "target message not found");
        }
        if (!"assistant".equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "target must be assistant");
        }
        AppMessage generated = messageMapper.findById(generatedMessageId);
        if (generated == null || generated.getConversationId() == null || generated.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.CONFLICT, "generated message not found");
        }
        if (generated.getBranchId() == null || generated.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.CONFLICT, "generated message not found");
        }
        if (generated.getContent() == null
                || (generated.getContent().isBlank() && !outputRegexApplied)) {
            throw new BusinessException(ErrorCode.CONFLICT, "generated message is empty");
        }

        String stRef = ensureSwipeRootRef(target, traceIdSafe());
        persistDisplayedSwipeVariant(target);
        Integer max = messageMapper.findMaxSwipeIndexAndBranch(stRef, activeBranchId);
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
        branchService.incrementMemorySourceRevision(conversationId, activeBranchId);
        if (syncRuntimeStateToSt) {
            runAfterCommit(() -> {
                try {
                    snapshotService.saveSnapshotFromDb(conversationId, activeBranchId, 800);
                } catch (Exception ex) {
                    log.warn("regenerate snapshot sync failed conversationId={} branchId={}",
                            conversationId, activeBranchId, ex);
                }
                try {
                    syncSwipeSelectionToSt(conversationId, targetMessageId, token, outputRegexApplied);
                } catch (Exception ex) {
                    log.warn("regenerate swipe sync failed conversationId={} messageId={}",
                            conversationId, targetMessageId, ex);
                }
            });
        }
        memoryAutoRefreshService.triggerAfterHistoryChange(conversationId, activeBranchId);
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
        finalizeContinueAsMessage(
                conversationId,
                anchorAssistantId,
                provisionalAssistantId,
                taskId,
                suffixContent,
                token,
                true,
                false
        );
    }

    @Transactional
    public void finalizeContinueAsMessage(
            long conversationId,
            long anchorAssistantId,
            long provisionalAssistantId,
            long taskId,
            String suffixContent,
            String token,
            boolean syncRuntimeStateToSt
    ) {
        finalizeContinueAsMessage(
                conversationId,
                anchorAssistantId,
                provisionalAssistantId,
                taskId,
                suffixContent,
                token,
                syncRuntimeStateToSt,
                false,
                false
        );
    }

    @Transactional
    public void finalizeContinueAsMessage(
            long conversationId,
            long anchorAssistantId,
            long provisionalAssistantId,
            long taskId,
            String suffixContent,
            String token,
            boolean syncRuntimeStateToSt,
            boolean outputRegexApplied
    ) {
        finalizeContinueAsMessage(
                conversationId,
                anchorAssistantId,
                provisionalAssistantId,
                taskId,
                suffixContent,
                token,
                syncRuntimeStateToSt,
                outputRegexApplied,
                false
        );
    }

    @Transactional
    public void finalizeContinueAsMessage(
            long conversationId,
            long anchorAssistantId,
            long provisionalAssistantId,
            long taskId,
            String suffixContent,
            String token,
            boolean syncRuntimeStateToSt,
            boolean outputRegexApplied,
            boolean cancelled
    ) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        AppConversationBranch activeBranch = branchService.requireActiveBranch(c);
        long activeBranchId = activeBranch.getId();

        AppMessage anchor = messageMapper.findById(anchorAssistantId);
        if (anchor == null || anchor.getConversationId() == null || anchor.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "continue anchor not found");
        }
        if (anchor.getBranchId() == null || anchor.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "continue anchor not found");
        }
        if (!"assistant".equalsIgnoreCase(anchor.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "continue anchor must be assistant");
        }
        AppMessage provisional = messageMapper.findById(provisionalAssistantId);
        if (provisional == null || provisional.getConversationId() == null || provisional.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provisional message not found");
        }
        if (provisional.getBranchId() == null || provisional.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provisional message not found");
        }

        String rawContent = suffixContent == null ? "" : suffixContent;
        String content = rawContent.trim();
        messageMapper.updateContinuationMeta(provisionalAssistantId, "CONTINUATION", anchorAssistantId, traceIdSafe());
        String finalStatus = cancelled ? "STOPPED" : "SUCCESS";
        String ref = "root:" + provisionalAssistantId;
        messageMapper.updateStatusAndContent(provisionalAssistantId, finalStatus, content, null, traceIdSafe());
        messageMapper.updateVariantMeta(provisionalAssistantId, ref, 0, traceIdSafe());
        taskMapper.updateStatus(taskId, cancelled ? "STOPPED" : "SUCCESS", null, null, traceIdSafe(), null);
        if (syncRuntimeStateToSt) {
            runAfterCommit(() -> {
                if (!content.isBlank()) {
                    try {
                        syncContinuationReplyToSt(
                                conversationId,
                                anchorAssistantId,
                                rawContent,
                                token,
                                outputRegexApplied
                        );
                    } catch (Exception ex) {
                        log.warn("continuation ST sync failed conversationId={} anchorMessageId={}",
                                conversationId, anchorAssistantId, ex);
                    }
                }
                try {
                    snapshotService.saveSnapshotFromDb(conversationId, activeBranchId, 800);
                } catch (Exception ex) {
                    log.warn("continuation snapshot sync failed conversationId={} branchId={}",
                            conversationId, activeBranchId, ex);
                }
            });
        }
        chatAuditService.touchAfterAssistantContentUpdate(provisionalAssistantId);
        triggerMemoryRefreshAfterCommit(conversationId, activeBranchId);
    }

    public void markMemorySourceChanged(long conversationId, long branchId) {
        branchService.incrementMemorySourceRevision(conversationId, branchId);
    }

    @Transactional
    public void abortContinueEmpty(long conversationId, long provisionalAssistantId, long taskId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        AppConversationBranch activeBranch = branchService.requireActiveBranch(c);
        AppMessage prov = messageMapper.findById(provisionalAssistantId);
        if (prov == null || prov.getConversationId() == null || prov.getConversationId() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provisional message not found");
        }
        if (prov.getBranchId() == null || prov.getBranchId().longValue() != activeBranch.getId()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provisional message not found");
        }
        taskMapper.updateStatus(taskId, "STOPPED", null, null, traceIdSafe(), null);
        messageMapper.deleteById(provisionalAssistantId);
    }

    /**
     * 缁绢収鍠曠换姘跺触鐏炶偐顏遍柡?assistant 闁?swipe 闁炽儲绮嶉悧鏉戭嚕閺囩姵鏆忛柍銉︾箓閻°劑宕烽妸锝傚亾?     * 缂佹拝绠戦悾楣冩晬濮濈_message_ref = "root:<targetMessageId>"闁?     */
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
     * 闁煎浜埀顒傚Т瀹曡鲸绂掗崨顓犳憼 app_character闁挎稑顔抰AvatarUrl 濠㈣埖鐭拹鐔煎础閻樿京绉?png闁?api/characters/get 闁哄啰濮甸弸鍐╃鐠佸湱骞?
     * 闊洤鎳橀妴蹇旂鎼达紕姘ㄩ柛?persona/scenario/system_prompt 缂佹稑顦扮€?system闁挎稑鑻幆渚€宕氬▎鎯镐線宕圭€ｎ亞鏆氶柛蹇嬪妺缁楀鎸ч幏宀婂晭閻庤鍝庨埀?     */
    private RoleplayBundle resolveRoleplayBundle(long characterId, long userId) {
        H5MyCharacter mine = h5MyCharacterMapper.findById(characterId);
        if (mine != null
                && mine.getOwnerUserId() != null
                && mine.getOwnerUserId().longValue() == userId) {
            String desc = nz(mine.getDescription());
            String mex = nz(mine.getMesExample());
            if (!mex.isBlank()) {
                desc = desc.isBlank() ? ("闁靛棙鍔曢顔炬嫚濠靛牄浠涘〒姘儍閳ь剚鍙歯" + mex) : desc + "\n\n闁靛棙鍔曢顔炬嫚濠靛牄浠涘〒姘儍閳ь剚鍙歯" + mex;
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
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
        }
        Long ownerId = character.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(character.getPrivateCard())) {
            if (ownerId == null || ownerId.longValue() != userId) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
            }
            return;
        }
        if (Boolean.FALSE.equals(character.getClientVisible())
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

    private List<String> openingVariants(RoleplayBundle bundle) {
        if (bundle == null || bundle.detail() == null) {
            return List.of();
        }
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String first = normalizeOpeningVariant(bundle.detail().firstMes());
        if (!first.isBlank()) {
            variants.add(first);
        }
        List<String> alternates = bundle.detail().alternateGreetings();
        if (alternates != null) {
            for (String alt : alternates) {
                String normalized = normalizeOpeningVariant(alt);
                if (!normalized.isBlank()) {
                    variants.add(normalized);
                }
            }
        }
        return new ArrayList<>(variants);
    }

    private void insertOpeningSwipeVariants(AppMessage root, List<String> variants, String openingRef) {
        if (root == null || root.getId() == null || root.getId() <= 0 || variants == null || variants.size() <= 1) {
            return;
        }
        long userId = root.getUserId() == null ? 0L : root.getUserId();
        long conversationId = root.getConversationId() == null ? 0L : root.getConversationId();
        long branchId = root.getBranchId() == null ? 0L : root.getBranchId();
        if (userId <= 0 || conversationId <= 0 || branchId <= 0) {
            return;
        }
        for (int i = 1; i < variants.size(); i++) {
            String variant = variants.get(i);
            if (variant.isBlank()) {
                continue;
            }
            AppMessage row = new AppMessage();
            row.setUserId(userId);
            row.setConversationId(conversationId);
            row.setBranchId(branchId);
            row.setParentMessageId(root.getId());
            row.setRole("assistant");
            row.setClientMessageId("opening_" + root.getId() + "_idx_" + i);
            row.setContent(variant);
            row.setStatus("SUCCESS");
            row.setStMessageRef(openingRef);
            row.setSwipeIndex(i);
            row.setTraceId(traceIdSafe());
            messageMapper.insert(row);
            messageMapper.incrementTotalMessageCounter();
        }
    }

    private void ensureOpeningSwipeVariantsForExisting(AppMessage root, List<String> variants) {
        if (root == null || root.getId() == null || root.getId() <= 0 || variants == null || variants.size() <= 1) {
            return;
        }
        int activeIndex = 0;
        String current = normalizeOpeningVariant(root.getContent());
        for (int i = 0; i < variants.size(); i++) {
            if (normalizeOpeningVariant(variants.get(i)).equals(current)) {
                activeIndex = i;
                break;
            }
        }
        String ref = root.getStMessageRef();
        String expectedRef = "root:" + root.getId();
        if (ref == null
                || ref.isBlank()
                || !ref.equals(expectedRef)
                || root.getSwipeIndex() == null
                || root.getSwipeIndex() != activeIndex) {
            ref = expectedRef;
            messageMapper.updateVariantMeta(root.getId(), ref, activeIndex, traceIdSafe());
            root.setStMessageRef(ref);
            root.setSwipeIndex(activeIndex);
        }
        for (int i = 0; i < variants.size(); i++) {
            if (i == activeIndex) {
                continue;
            }
            AppMessage existing = root.getBranchId() == null
                    ? messageMapper.findByStMessageRefAndSwipeIndex(ref, i)
                    : messageMapper.findByStMessageRefAndSwipeIndexAndBranch(ref, i, root.getBranchId());
            if (existing != null) {
                continue;
            }
            AppMessage row = new AppMessage();
            row.setUserId(root.getUserId());
            row.setConversationId(root.getConversationId());
            row.setBranchId(root.getBranchId());
            row.setParentMessageId(root.getId());
            row.setRole("assistant");
            row.setClientMessageId("opening_" + root.getId() + "_idx_" + i);
            row.setContent(variants.get(i));
            row.setStatus("SUCCESS");
            row.setStMessageRef(ref);
            row.setSwipeIndex(i);
            row.setTraceId(traceIdSafe());
            messageMapper.insert(row);
            messageMapper.incrementTotalMessageCounter();
        }
    }

    private static boolean isAssistantOpeningCandidate(AppMessage row) {
        return row != null && "assistant".equalsIgnoreCase(row.getRole());
    }

    private static AppMessage findOpeningAssistantMessage(List<AppMessage> visibleMessages) {
        if (visibleMessages == null || visibleMessages.isEmpty()) {
            return null;
        }
        for (AppMessage row : visibleMessages) {
            String clientMessageId = row == null || row.getClientMessageId() == null
                    ? ""
                    : row.getClientMessageId();
            if (isAssistantOpeningCandidate(row) && clientMessageId.startsWith("opening_")) {
                return row;
            }
        }
        return visibleMessages.size() == 1 && isAssistantOpeningCandidate(visibleMessages.get(0))
                ? visibleMessages.get(0)
                : null;
    }

    private static String normalizeOpeningVariant(String value) {
        return nz(value).replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private boolean repairBogusOpeningMessageIfNeeded(
            long conversationId,
            long branchId,
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
        snapshotService.saveSnapshotFromDb(conversationId, branchId, 800);
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

    private static String buildReplySuggestionInstruction(
            String currentDraft,
            String userName,
            String charName,
            String tailMemoryPrompt,
            List<String> deferredSystemPrompts
    ) {
        StringBuilder builder = new StringBuilder();
        String memoryPrompt = nz(tailMemoryPrompt);
        if (!memoryPrompt.isBlank()) {
            builder.append("Relevant conversation memory:\n")
                    .append(memoryPrompt)
                    .append("\n\n");
        }
        if (deferredSystemPrompts != null && !deferredSystemPrompts.isEmpty()) {
            builder.append("Additional active roleplay instructions:\n");
            for (String prompt : deferredSystemPrompts) {
                String text = nz(prompt);
                if (!text.isBlank()) {
                    builder.append(text).append("\n\n");
                }
            }
        }
        builder.append("SillyTavern-style user impersonation prompt:\n")
                .append(ST_IMPERSONATION_PROMPT
                        .replace("{{user}}", nz(userName).isBlank() ? "the user" : nz(userName))
                        .replace("{{char}}", nz(charName).isBlank() ? "the character" : nz(charName))
                        .strip())
                .append("\n\n")
                .append(REPLY_SUGGESTION_SYSTEM_PROMPT.strip())
                .append("\n\n")
                .append(buildReplySuggestionPrompt(currentDraft, userName, charName));
        return builder.toString().strip();
    }

    private static String buildReplySuggestionPrompt(String currentDraft, String userName, String charName) {
        String draft = nz(currentDraft);
        String participants = """
                User display name: %s
                Character name: %s
                """.formatted(nz(userName), nz(charName));
        if (draft.isBlank()) {
            return participants + """
                    This is equivalent to SillyTavern user impersonation.
                    Based on the full roleplay context above, generate 4 distinct candidate replies that the USER could send next.
                    Focus on the most recent character message, the current emotional tension, and the relationship dynamic.
                    Make each option feel like a real user message in this exact scene, not a generic suggestion.
                    Do not continue as the character. Do not be generic. Do not explain.
                    """;
        }
        return participants + """
                The USER is currently drafting an idea for their next message.
                This is equivalent to SillyTavern user impersonation.
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
                .replaceFirst("^[\\-•*\\d\\s.、:：)）]+", "")
                .replaceFirst("^['\"“”‘’]+", "")
                .replaceFirst("['\"“”‘’]+$", "")
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
                .replaceFirst("^\\s*(?:用户|玩家|人类|我|建议\\s*\\d*|回复\\s*\\d*|选项\\s*\\d*)\\s*[:\\uff1a-]\\s*", "")
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
        Long branchId = target.getBranchId();
        AppMessage existing = branchId == null || branchId <= 0
                ? messageMapper.findByStMessageRefAndSwipeIndex(ref, currentIndex)
                : messageMapper.findByStMessageRefAndSwipeIndexAndBranch(ref, currentIndex, branchId);
        if (existing == null || existing.getId() == null || existing.getId().equals(target.getId())) {
            AppMessage clone = new AppMessage();
            clone.setUserId(target.getUserId());
            clone.setConversationId(target.getConversationId());
            clone.setBranchId(target.getBranchId());
            clone.setParentMessageId(target.getParentMessageId());
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
        if (m == null || m.getContent() == null) {
            return false;
        }
        String status = m.getStatus() == null ? "" : m.getStatus();
        if ("FAILED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status)) {
            return false;
        }
        if ("user".equalsIgnoreCase(m.getRole())) {
            return !m.getContent().isBlank();
        }
        if (!"assistant".equalsIgnoreCase(m.getRole())) {
            return false;
        }
        if (!"SUCCESS".equalsIgnoreCase(status) && !"STOPPED".equalsIgnoreCase(status)) {
            return false;
        }
        if (m.getContent().isBlank() && !"SUCCESS".equalsIgnoreCase(status)) {
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
