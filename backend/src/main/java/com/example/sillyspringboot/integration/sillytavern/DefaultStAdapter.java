package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateResult;
import com.example.sillyspringboot.integration.sillytavern.dto.ConversationIdentity;
import com.example.sillyspringboot.integration.sillytavern.dto.SwipeVariant;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterSummary;
import com.example.sillyspringboot.integration.sillytavern.dto.StChatGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StChatSaveRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookSaveRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookOptionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
public class DefaultStAdapter implements StAdapter {

    private static final Logger log = LoggerFactory.getLogger(DefaultStAdapter.class);

    private final StClient stClient;
    private final StRuntimeMessagesCapture runtimeMessagesCapture;
    private final StRuntimeObservationCapture runtimeObservationCapture;

    public DefaultStAdapter(
            StClient stClient,
            StRuntimeMessagesCapture runtimeMessagesCapture,
            StRuntimeObservationCapture runtimeObservationCapture
    ) {
        this.stClient = stClient;
        this.runtimeMessagesCapture = runtimeMessagesCapture;
        this.runtimeObservationCapture = runtimeObservationCapture;
    }

    @Override
    public void checkConnectivity() {
        stClient.checkConnectivity();
    }

    @Override
    public List<StCharacterSummary> listCharactersAll() {
        return stClient.listCharactersAll();
    }

    @Override
    public List<StWorldbookOptionDto> listWorldbooks() {
        return stClient.listWorldbooks();
    }

    @Override
    public void saveWorldbook(StWorldbookSaveRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.data() == null) {
            throw new StUnsupportedFeatureException();
        }
        stClient.saveWorldbook(request.name(), request.data());
    }

    @Override
    public boolean deleteWorldbook(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return stClient.deleteWorldbook(name);
    }

    @Override
    public StCharacterDetail getCharacter(StCharacterGetRequest request) {
        return stClient.getCharacter(request);
    }

    @Override
    public String syncCharacterCard(StCharacterDetail detail, String preferredAvatarUrl) {
        return stClient.syncCharacterCard(detail, preferredAvatarUrl);
    }

    @Override
    public Object importCharacterPng(byte[] bytes, String originalFilename, StCharacterImportRequest request) {
        return stClient.importCharacterPng(bytes, originalFilename, request);
    }

    @Override
    public boolean deleteCharacter(String avatarUrl, boolean deleteChats) {
        return stClient.deleteCharacter(avatarUrl, deleteChats);
    }

    @Override
    public boolean deleteChat(String avatarUrl, String chatFileName) {
        return stClient.deleteChat(avatarUrl, chatFileName);
    }

    @Override
    public ConversationIdentity ensureCharacterAssets(ConversationIdentity identity) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public void ensureWorldInfoBindings(ConversationIdentity identity) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public ConversationIdentity createConversation(ConversationIdentity draft) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public void loadConversation(ConversationIdentity identity) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public List<java.util.Map<String, String>> buildRuntimeMessages(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> groupNames,
            List<String> worldNames
    ) {
        if (avatarUrl == null || avatarUrl.isBlank() || fileName == null || fileName.isBlank()) {
            throw new StUnsupportedFeatureException();
        }
        return stClient.runtimeChatBuildMessages(avatarUrl, fileName, userName, charName, groupNames, worldNames);
    }

    @Override
    public void appendUserMessage(ChatGenerateRequest request) {
        String avatarUrl = request.stAvatarUrl();
        String fileName = request.stChatFileName();
        if (avatarUrl == null || avatarUrl.isBlank() || fileName == null || fileName.isBlank()) {
            throw new StUnsupportedFeatureException();
        }
        String mes = request.userMessage();
        if (mes == null || mes.isBlank()) {
            return;
        }
        stClient.runtimeChatAppend(
                avatarUrl,
                fileName,
                request.userName(),
                request.charName(),
                true,
                request.stMessageRef(),
                mes
        );
    }

    @Override
    public void appendAssistantMessage(ChatGenerateRequest request, String assistantContent) {
        String avatarUrl = request == null ? "" : request.stAvatarUrl();
        String fileName = request == null ? "" : request.stChatFileName();
        if (avatarUrl == null || avatarUrl.isBlank() || fileName == null || fileName.isBlank()) {
            throw new StUnsupportedFeatureException();
        }
        String mes = assistantContent == null ? "" : assistantContent.trim();
        if (mes.isBlank()) {
            return;
        }
        stClient.runtimeChatAppend(
                avatarUrl,
                fileName,
                request.userName(),
                request.charName(),
                false,
                request.stMessageRef(),
                mes
        );
    }

    @Override
    public void replaceLastAssistantMessage(ChatGenerateRequest request, String assistantContent) {
        String avatarUrl = request == null ? "" : request.stAvatarUrl();
        String fileName = request == null ? "" : request.stChatFileName();
        if (avatarUrl == null || avatarUrl.isBlank() || fileName == null || fileName.isBlank()) {
            throw new StUnsupportedFeatureException();
        }
        String mes = assistantContent == null ? "" : assistantContent.trim();
        if (mes.isBlank()) return;
        stClient.runtimeChatReplaceLastAssistant(
                avatarUrl,
                fileName,
                request.userName(),
                request.charName(),
                request.stMessageRef(),
                mes
        );
    }

    @Override
    public void streamGenerateAssistantReply(ChatGenerateRequest request, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        String avatarUrl = request.stAvatarUrl();
        String fileName = request.stChatFileName();
        boolean hasRuntimeRef = avatarUrl != null && !avatarUrl.isBlank() && fileName != null && !fileName.isBlank();
        boolean hasMessages = request.messages() != null && !request.messages().isEmpty();

        if (hasRuntimeRef && !hasMessages) {
            runtimeObservationCapture.capture(request, "st-runtime-generate", "not_compared");
            stClient.streamRuntimeChatGenerate(request, onChunk, control);
            return;
        }

        if (hasMessages) {
            runtimeObservationCapture.capture(request, "spring-forward-generate", "not_compared");
            runtimeMessagesCapture.capture(
                    request.conversationId() == null ? 0L : request.conversationId(),
                    request.mode(),
                    request.messages()
            );
        } else {
            runtimeObservationCapture.capture(request, "spring-direct-generate", "not_compared");
        }
        stClient.streamChatCompletionsGenerate(request, onChunk, control);
    }

    @Override
    public ChatGenerateResult generateAssistantReply(ChatGenerateRequest request) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public ChatGenerateResult continueAssistantReply(ChatGenerateRequest request) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public ChatGenerateResult regenerateAssistantReply(ChatGenerateRequest request) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public boolean stopGeneration(ConversationIdentity identity) {
        String avatarUrl = identity == null ? "" : identity.stAvatarUrl();
        String fileName = identity == null ? "" : identity.stChatFileName();
        if (avatarUrl == null || avatarUrl.isBlank() || fileName == null || fileName.isBlank()) {
            return false;
        }
        return stClient.runtimeChatStop(avatarUrl, fileName);
    }

    @Override
    public List<SwipeVariant> listConversationVariants(ConversationIdentity identity, String messageId) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public SwipeVariant switchSwipeVariant(ConversationIdentity identity, String messageId, int variantIndex) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public void syncConversationSnapshot(ConversationIdentity identity) {
        throw new StUnsupportedFeatureException();
    }

    @Override
    public Object getChatSnapshot(StChatGetRequest request) {
        return stClient.getChatSnapshot(request);
    }

    @Override
    public void saveChatSnapshot(StChatSaveRequest request) {
        stClient.saveChatSnapshot(request);
    }

    @Override
    public List<float[]> queryMemoryVectors(ConversationIdentity identity, String queryText) {
        // TODO：对接 ST /api/vector/query（需在 ST 侧配置向量存储与嵌入模型）
        log.debug("queryMemoryVectors stub: identity={}, queryLen={}", identity, queryText == null ? 0 : queryText.length());
        return List.of();
    }

    @Override
    public void upsertMemoryVectors(ConversationIdentity identity, List<float[]> vectors) {
        // TODO：对接 ST /api/vector/insert
        log.debug("upsertMemoryVectors stub: identity={}, batch={}", identity, vectors == null ? 0 : vectors.size());
    }
}
