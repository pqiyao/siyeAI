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

import java.util.List;
import java.util.function.Consumer;

/**
 * 对业务层暴露的稳定契约；实现类不得将 ST 原始 JSON 直接泄漏给上层。
 *
 * <p>方法清单与 docs/phase0 中 StAdapter 边界一致；未实现方法在 {@link DefaultStAdapter} 中抛出
 * {@link StUnsupportedFeatureException}，直至后续阶段接入。
 */
public interface StAdapter {

    void checkConnectivity();

    List<StCharacterSummary> listCharactersAll();

    List<StWorldbookOptionDto> listWorldbooks();

    void saveWorldbook(StWorldbookSaveRequest request);

    boolean deleteWorldbook(String name);

    StCharacterDetail getCharacter(StCharacterGetRequest request);

    String syncCharacterCard(StCharacterDetail detail, String preferredAvatarUrl);

    Object importCharacterPng(byte[] bytes, String originalFilename, StCharacterImportRequest request);

    boolean deleteCharacter(String avatarUrl, boolean deleteChats);

    boolean deleteChat(String avatarUrl, String chatFileName);

    ConversationIdentity ensureCharacterAssets(ConversationIdentity identity);

    void ensureWorldInfoBindings(ConversationIdentity identity);

    ConversationIdentity createConversation(ConversationIdentity draft);

    void loadConversation(ConversationIdentity identity);

    List<java.util.Map<String, String>> buildRuntimeMessages(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> groupNames,
            List<String> worldNames
    );

    void appendUserMessage(ChatGenerateRequest request);

    /**
     * 方案一（StepA）：将 assistant 最终回复写回 ST chat（保证 ST 为运行时事实源）。
     */
    void appendAssistantMessage(ChatGenerateRequest request, String assistantContent);

    /**
     * A：swipe 选中某个版本后，同步替换 ST chat 的最后一条 assistant（商用一致性）。
     */
    void replaceLastAssistantMessage(ChatGenerateRequest request, String assistantContent);

    /**
     * 阶段 4：流式生成（统一事件由上层桥接；此处只负责把 ST 流转为受控 chunk 回调）。
     * <p>
     * 约束：实现不得把 ST 原始错误/响应体直出给业务层。
     */
    void streamGenerateAssistantReply(ChatGenerateRequest request, Consumer<ChatGenerateChunk> onChunk, StStreamControl control);

    ChatGenerateResult generateAssistantReply(ChatGenerateRequest request);

    ChatGenerateResult continueAssistantReply(ChatGenerateRequest request);

    ChatGenerateResult regenerateAssistantReply(ChatGenerateRequest request);

    boolean stopGeneration(ConversationIdentity identity);

    List<SwipeVariant> listConversationVariants(ConversationIdentity identity, String messageId);

    SwipeVariant switchSwipeVariant(ConversationIdentity identity, String messageId, int variantIndex);

    void syncConversationSnapshot(ConversationIdentity identity);

    Object getChatSnapshot(StChatGetRequest request);

    void saveChatSnapshot(StChatSaveRequest request);

    List<float[]> queryMemoryVectors(ConversationIdentity identity, String queryText);

    void upsertMemoryVectors(ConversationIdentity identity, List<float[]> vectors);
}
