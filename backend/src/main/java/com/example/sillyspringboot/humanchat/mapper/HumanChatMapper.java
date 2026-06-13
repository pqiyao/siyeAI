package com.example.sillyspringboot.humanchat.mapper;

import com.example.sillyspringboot.humanchat.entity.HumanChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface HumanChatMapper {

    Map<String, Object> findUserCard(@Param("userId") long userId);

    int countConversationBetween(@Param("userId") long userId, @Param("peerUserId") long peerUserId);

    int countMutualFollow(@Param("userId") long userId, @Param("peerUserId") long peerUserId);

    int countFriendPair(@Param("userId") long userId, @Param("peerUserId") long peerUserId);

    int countActiveBlockBetween(@Param("userId") long userId, @Param("peerUserId") long peerUserId);

    HumanChatMessage findMessageByClientMsgId(@Param("fromUserId") long fromUserId, @Param("clientMsgId") String clientMsgId);

    int insertMessage(HumanChatMessage message);

    HumanChatMessage findMessageById(@Param("messageId") long messageId);

    Map<String, Object> findMessageDto(@Param("messageId") long messageId, @Param("viewerId") long viewerId);

    List<Map<String, Object>> listMessages(
            @Param("conversationKey") String conversationKey,
            @Param("viewerId") long viewerId,
            @Param("beforeMessageId") Long beforeMessageId,
            @Param("limit") int limit
    );

    long countConversations(@Param("userId") long userId);

    List<Map<String, Object>> listConversations(
            @Param("userId") long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int upsertConversationForSender(
            @Param("conversationKey") String conversationKey,
            @Param("userId") long userId,
            @Param("peerUserId") long peerUserId,
            @Param("lastMessageId") long lastMessageId,
            @Param("messageType") String messageType,
            @Param("payloadJson") String payloadJson,
            @Param("preview") String preview
    );

    int upsertConversationForReceiver(
            @Param("conversationKey") String conversationKey,
            @Param("userId") long userId,
            @Param("peerUserId") long peerUserId,
            @Param("lastMessageId") long lastMessageId,
            @Param("messageType") String messageType,
            @Param("payloadJson") String payloadJson,
            @Param("preview") String preview
    );

    int markRead(
            @Param("conversationKey") String conversationKey,
            @Param("readerUserId") long readerUserId,
            @Param("peerUserId") long peerUserId
    );

    int resetUnread(@Param("userId") long userId, @Param("peerUserId") long peerUserId);

    int recallMessage(@Param("messageId") long messageId, @Param("userId") long userId);

    HumanChatMessage findLatestVisibleMessage(@Param("conversationKey") String conversationKey);

    int updateConversationLastMessage(
            @Param("conversationKey") String conversationKey,
            @Param("lastMessageId") Long lastMessageId,
            @Param("messageType") String messageType,
            @Param("payloadJson") String payloadJson,
            @Param("preview") String preview
    );

    long countAdminConversations(
            @Param("keyword") String keyword,
            @Param("userId") Long userId,
            @Param("peerUserId") Long peerUserId
    );

    List<Map<String, Object>> listAdminConversations(
            @Param("keyword") String keyword,
            @Param("userId") Long userId,
            @Param("peerUserId") Long peerUserId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    Map<String, Object> findAdminConversation(@Param("conversationId") long conversationId);

    long countAdminMessages(
            @Param("keyword") String keyword,
            @Param("conversationKey") String conversationKey,
            @Param("fromUserId") Long fromUserId,
            @Param("toUserId") Long toUserId,
            @Param("messageType") String messageType,
            @Param("status") String status
    );

    List<Map<String, Object>> listAdminMessages(
            @Param("keyword") String keyword,
            @Param("conversationKey") String conversationKey,
            @Param("fromUserId") Long fromUserId,
            @Param("toUserId") Long toUserId,
            @Param("messageType") String messageType,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    Map<String, Object> findAdminMessage(@Param("messageId") long messageId);

    int adminRecallMessage(@Param("messageId") long messageId);
}
