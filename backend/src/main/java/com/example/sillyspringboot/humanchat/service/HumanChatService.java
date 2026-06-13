package com.example.sillyspringboot.humanchat.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.humanchat.entity.HumanChatMessage;
import com.example.sillyspringboot.humanchat.mapper.HumanChatMapper;
import com.example.sillyspringboot.humanchat.ws.SocialChatRealtimeNotifier;
import com.example.sillyspringboot.ops.service.SocialFeatureSettingsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.upload.service.AppUploadedAssetService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class HumanChatService {

    private static final Logger log = LoggerFactory.getLogger(HumanChatService.class);
    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_RECALLED = "recalled";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_IMAGE = "image";
    private static final int MAX_TEXT_LENGTH = 2000;

    private final HumanChatMapper mapper;
    private final ObjectMapper objectMapper;
    private final SocialChatRealtimeNotifier realtimeNotifier;
    private final AppUploadedAssetService uploadedAssetService;
    private final HumanChatDeliveryLogService deliveryLogService;
    private final SocialFeatureSettingsService socialSettingsService;

    public HumanChatService(
            HumanChatMapper mapper,
            ObjectMapper objectMapper,
            SocialChatRealtimeNotifier realtimeNotifier,
            AppUploadedAssetService uploadedAssetService,
            HumanChatDeliveryLogService deliveryLogService,
            SocialFeatureSettingsService socialSettingsService
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.realtimeNotifier = realtimeNotifier;
        this.uploadedAssetService = uploadedAssetService;
        this.deliveryLogService = deliveryLogService;
        this.socialSettingsService = socialSettingsService;
    }

    @Transactional
    public Map<String, Object> sendMessage(AppUser user, Map<String, Object> body) {
        long fromUserId = requireUserId(user);
        long peerId = longRequired(body == null ? null : body.get("peerId"), "请选择聊天对象");
        if (peerId == fromUserId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能给自己发消息");
        }
        requireActiveUser(peerId);
        boolean existingConversation = mapper.countConversationBetween(fromUserId, peerId) > 0;

        String clientMsgId = normalizeClientMsgId(body == null ? null : body.get("clientMsgId"));
        if (clientMsgId != null) {
            HumanChatMessage existing = mapper.findMessageByClientMsgId(fromUserId, clientMsgId);
            if (existing != null) {
                Map<String, Object> dto = mapper.findMessageDto(existing.getId(), fromUserId);
                if (dto == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
                }
                return normalizeMessageDto(dto);
            }
        }

        String messageType = normalizeMessageType(body == null ? null : body.get("messageType"));
        socialSettingsService.ensureCanSendMessage(
                messageType,
                existingConversation,
                isPrivateChatRelationAllowed(fromUserId, peerId)
        );
        Map<String, Object> payload = normalizePayload(fromUserId, messageType, body == null ? null : body.get("payload"));
        String payloadJson = toJson(payload);
        String preview = buildPreview(messageType, payload);
        String conversationKey = conversationKey(fromUserId, peerId);

        HumanChatMessage message = new HumanChatMessage();
        message.setConversationKey(conversationKey);
        message.setFromUserId(fromUserId);
        message.setToUserId(peerId);
        message.setMessageType(messageType);
        message.setPayloadJson(payloadJson);
        message.setContentPreview(preview);
        message.setStatus(STATUS_NORMAL);
        message.setIsRead(0);
        message.setClientMsgId(clientMsgId == null ? "srv_" + UUID.randomUUID().toString().replace("-", "") : clientMsgId);
        message.setProvider("local_ws");
        mapper.insertMessage(message);

        mapper.upsertConversationForSender(conversationKey, fromUserId, peerId, message.getId(), messageType, payloadJson, preview);
        mapper.upsertConversationForReceiver(conversationKey, peerId, fromUserId, message.getId(), messageType, payloadJson, preview);

        Map<String, Object> senderDto = normalizeMessageDto(mapper.findMessageDto(message.getId(), fromUserId));
        Map<String, Object> receiverDto = normalizeMessageDto(mapper.findMessageDto(message.getId(), peerId));
        pushMessageSentAfterCommit(fromUserId, peerId, senderDto, receiverDto);
        return senderDto;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listConversations(AppUser user, int pageNum, int pageSize) {
        long userId = requireUserId(user);
        socialSettingsService.ensureChatEntryEnabled();
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(50, pageSize));
        long total = mapper.countConversations(userId);
        List<Map<String, Object>> rows = mapper.listConversations(userId, (safePage - 1) * safeSize, safeSize).stream()
                .map(this::normalizeConversationDto)
                .toList();
        return Map.of("total", total, "rows", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listMessages(AppUser user, long peerId, Long beforeMessageId, int limit) {
        long userId = requireUserId(user);
        if (peerId == userId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "聊天对象不正确");
        }
        requireActiveUser(peerId);
        socialSettingsService.ensureChatReadable();
        int safeLimit = Math.max(1, Math.min(100, limit));
        String conversationKey = conversationKey(userId, peerId);
        List<Map<String, Object>> rows = mapper.listMessages(conversationKey, userId, beforeMessageId, safeLimit).stream()
                .map(this::normalizeMessageDto)
                .collect(ArrayList::new, (list, item) -> list.add(0, item), ArrayList::addAll);
        return Map.of(
                "conversationKey", conversationKey,
                "peer", requireActiveUser(peerId),
                "rows", rows
        );
    }

    @Transactional
    public Map<String, Object> markRead(AppUser user, long peerId) {
        long userId = requireUserId(user);
        if (peerId == userId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "聊天对象不正确");
        }
        requireActiveUser(peerId);
        socialSettingsService.ensureChatReadable();
        String conversationKey = conversationKey(userId, peerId);
        int count = mapper.markRead(conversationKey, userId, peerId);
        mapper.resetUnread(userId, peerId);
        Map<String, Object> data = Map.of(
                "readCount", count,
                "peerId", peerId,
                "readerUserId", userId,
                "conversationKey", conversationKey
        );
        pushReadAfterCommit(userId, peerId, data);
        return data;
    }

    @Transactional
    public Map<String, Object> recall(AppUser user, long messageId) {
        long userId = requireUserId(user);
        socialSettingsService.ensureRecallEnabled();
        HumanChatMessage message = mapper.findMessageById(messageId);
        if (message == null || !userIdEquals(message.getFromUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (STATUS_RECALLED.equalsIgnoreCase(blank(message.getStatus()))) {
            Map<String, Object> dto = mapper.findMessageDto(messageId, userId);
            return normalizeMessageDto(dto == null ? Map.of() : dto);
        }
        if (mapper.recallMessage(messageId, userId) <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "消息已不可撤回");
        }
        syncConversationPreviewAfterRecall(message.getConversationKey());
        Map<String, Object> senderDto = normalizeMessageDto(mapper.findMessageDto(messageId, userId));
        Map<String, Object> receiverDto = message.getToUserId() == null
                ? Map.of()
                : normalizeMessageDto(mapper.findMessageDto(messageId, message.getToUserId()));
        pushRecallAfterCommit(message.getFromUserId(), message.getToUserId(), senderDto, receiverDto);
        return senderDto;
    }

    @Transactional(readOnly = true)
    public long countAdminConversations(String keyword, Long userId, Long peerUserId) {
        return mapper.countAdminConversations(trimToNull(keyword), userId, peerUserId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminConversations(String keyword, Long userId, Long peerUserId, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return mapper.listAdminConversations(
                        trimToNull(keyword),
                        userId,
                        peerUserId,
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::normalizeAdminConversationDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminConversation(long conversationId) {
        Map<String, Object> row = mapper.findAdminConversation(conversationId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return normalizeAdminConversationDto(row);
    }

    @Transactional(readOnly = true)
    public long countAdminMessages(
            String keyword,
            String conversationKey,
            Long fromUserId,
            Long toUserId,
            String messageType,
            String status
    ) {
        return mapper.countAdminMessages(
                trimToNull(keyword),
                trimToNull(conversationKey),
                fromUserId,
                toUserId,
                normalizeMessageTypeOptional(messageType),
                normalizeMessageStatusOptional(status)
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminMessages(
            String keyword,
            String conversationKey,
            Long fromUserId,
            Long toUserId,
            String messageType,
            String status,
            int pageNum,
            int pageSize
    ) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return mapper.listAdminMessages(
                        trimToNull(keyword),
                        trimToNull(conversationKey),
                        fromUserId,
                        toUserId,
                        normalizeMessageTypeOptional(messageType),
                        normalizeMessageStatusOptional(status),
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::normalizeMessageDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminMessage(long messageId) {
        Map<String, Object> row = mapper.findAdminMessage(messageId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        return normalizeMessageDto(row);
    }

    @Transactional
    public Map<String, Object> adminRecall(long messageId) {
        HumanChatMessage message = mapper.findMessageById(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (STATUS_RECALLED.equalsIgnoreCase(blank(message.getStatus()))) {
            Map<String, Object> dto = mapper.findAdminMessage(messageId);
            return dto == null ? Map.of() : normalizeMessageDto(dto);
        }
        if (mapper.adminRecallMessage(messageId) <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "消息当前不可撤回");
        }
        syncConversationPreviewAfterRecall(message.getConversationKey());
        Map<String, Object> senderDto = message.getFromUserId() == null
                ? Map.of()
                : normalizeMessageDto(mapper.findMessageDto(messageId, message.getFromUserId()));
        Map<String, Object> receiverDto = message.getToUserId() == null
                ? Map.of()
                : normalizeMessageDto(mapper.findMessageDto(messageId, message.getToUserId()));
        pushRecallAfterCommit(message.getFromUserId(), message.getToUserId(), senderDto, receiverDto);
        Map<String, Object> adminDto = mapper.findAdminMessage(messageId);
        return adminDto == null ? Map.of() : normalizeMessageDto(adminDto);
    }

    private boolean isPrivateChatRelationAllowed(long userId, long peerId) {
        if (mapper.countActiveBlockBetween(userId, peerId) > 0) {
            return false;
        }
        if (socialSettingsService.requiresFriendOnly()) {
            return mapper.countFriendPair(userId, peerId) > 0;
        }
        if (socialSettingsService.requiresPrivateChatRelation()) {
            return mapper.countMutualFollow(userId, peerId) >= 2;
        }
        return true;
    }

    private void pushMessageSentAfterCommit(
            long fromUserId,
            Long toUserId,
            Map<String, Object> senderMessage,
            Map<String, Object> receiverMessage
    ) {
        if (toUserId == null) {
            return;
        }
        runAfterCommit(() -> {
            SocialChatRealtimeNotifier.SendResult senderResult =
                    realtimeNotifier.sendResultToUser(fromUserId, "private_message_sent", safeMap(senderMessage));
            SocialChatRealtimeNotifier.SendResult receiverResult =
                    realtimeNotifier.sendResultToUser(toUserId, "private_message_received", safeMap(receiverMessage));
            Long messageId = longOrNull(senderMessage == null ? null : senderMessage.get("messageId"));
            String conversationKey = senderMessage == null ? null : str(senderMessage.get("conversationKey"));
            recordDeliveryLog(messageId, conversationKey, fromUserId, senderResult, safeMap(senderMessage));
            recordDeliveryLog(messageId, conversationKey, toUserId, receiverResult, safeMap(receiverMessage));
            log.debug(
                    "social chat realtime message push: fromUserId={}, toUserId={}, senderCount={}, receiverCount={}, messageId={}",
                    fromUserId,
                    toUserId,
                    senderResult.sentCount(),
                    receiverResult.sentCount(),
                    senderMessage == null ? null : senderMessage.get("messageId")
            );
        });
    }

    private void pushReadAfterCommit(long readerUserId, long peerId, Map<String, Object> data) {
        runAfterCommit(() -> {
            SocialChatRealtimeNotifier.SendResult readerResult =
                    realtimeNotifier.sendResultToUser(readerUserId, "messages_read", data);
            SocialChatRealtimeNotifier.SendResult peerResult =
                    realtimeNotifier.sendResultToUser(peerId, "messages_read", data);
            recordDeliveryLog(null, str(data == null ? null : data.get("conversationKey")), readerUserId, readerResult, data);
            recordDeliveryLog(null, str(data == null ? null : data.get("conversationKey")), peerId, peerResult, data);
            log.debug(
                    "social chat realtime read push: readerUserId={}, peerId={}, readerCount={}, peerCount={}, readCount={}",
                    readerUserId,
                    peerId,
                    readerResult.sentCount(),
                    peerResult.sentCount(),
                    data == null ? null : data.get("readCount")
            );
        });
    }

    private void pushRecallAfterCommit(
            Long fromUserId,
            Long toUserId,
            Map<String, Object> senderMessage,
            Map<String, Object> receiverMessage
    ) {
        if (fromUserId == null || toUserId == null) {
            return;
        }
        runAfterCommit(() -> {
            SocialChatRealtimeNotifier.SendResult senderResult =
                    realtimeNotifier.sendResultToUser(fromUserId, "message_recalled", safeMap(senderMessage));
            SocialChatRealtimeNotifier.SendResult receiverResult =
                    realtimeNotifier.sendResultToUser(toUserId, "message_recalled", safeMap(receiverMessage));
            Long messageId = longOrNull(senderMessage == null ? null : senderMessage.get("messageId"));
            String conversationKey = senderMessage == null ? null : str(senderMessage.get("conversationKey"));
            recordDeliveryLog(messageId, conversationKey, fromUserId, senderResult, safeMap(senderMessage));
            recordDeliveryLog(messageId, conversationKey, toUserId, receiverResult, safeMap(receiverMessage));
            log.debug(
                    "social chat realtime recall push: fromUserId={}, toUserId={}, senderCount={}, receiverCount={}, messageId={}",
                    fromUserId,
                    toUserId,
                    senderResult.sentCount(),
                    receiverResult.sentCount(),
                    senderMessage == null ? null : senderMessage.get("messageId")
            );
        });
    }

    private static Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private void recordDeliveryLog(
            Long messageId,
            String conversationKey,
            Long targetUserId,
            SocialChatRealtimeNotifier.SendResult result,
            Object requestPayload
    ) {
        if (result == null) {
            return;
        }
        Map<String, Object> responsePayload = Map.of(
                "online", result.online(),
                "sentCount", result.sentCount(),
                "staleCount", result.staleCount(),
                "sessionCount", result.sessionCount(),
                "deliveryStatus", result.deliveryStatus()
        );
        deliveryLogService.record(
                messageId,
                conversationKey,
                targetUserId,
                "local_ws",
                result.type(),
                result.deliveryStatus(),
                requestPayload,
                responsePayload
        );
    }

    private static void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
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

    private void syncConversationPreviewAfterRecall(String conversationKey) {
        HumanChatMessage latest = mapper.findLatestVisibleMessage(conversationKey);
        if (latest == null) {
            mapper.updateConversationLastMessage(conversationKey, null, null, null, null);
            return;
        }
        String preview = STATUS_RECALLED.equalsIgnoreCase(blank(latest.getStatus()))
                ? "消息已撤回"
                : latest.getContentPreview();
        mapper.updateConversationLastMessage(
                conversationKey,
                latest.getId(),
                latest.getMessageType(),
                latest.getPayloadJson(),
                preview
        );
    }

    private Map<String, Object> requireActiveUser(long userId) {
        Map<String, Object> user = mapper.findUserCard(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if ("disabled".equalsIgnoreCase(blank(user.get("userStatus")))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户已停用");
        }
        return user;
    }

    private Map<String, Object> normalizeMessageDto(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("payload", parseJson(row.get("payload")));
        out.put("mine", truthy(row.get("mine")));
        out.put("isRead", truthy(row.get("isRead")));
        normalizeTimeFields(out, "createdAt", "readAt", "recalledAt");
        out.put("fromUser", Map.of(
                "userId", row.get("fromUserId"),
                "nickname", blank(row.get("fromNickname")),
                "avatar", blank(row.get("fromAvatar"))
        ));
        out.put("toUser", Map.of(
                "userId", row.get("toUserId"),
                "nickname", blank(row.get("toNickname")),
                "avatar", blank(row.get("toAvatar"))
        ));
        return out;
    }

    private Map<String, Object> normalizeConversationDto(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("lastMessagePayload", parseJson(row.get("lastMessagePayload")));
        normalizeTimeFields(out, "lastMessageAt", "updatedAt");
        out.put("peerUser", Map.of(
                "userId", row.get("peerUserId"),
                "nickname", blank(row.get("peerNickname")),
                "avatar", blank(row.get("peerAvatar")),
                "bio", blank(row.get("peerBio"))
        ));
        return out;
    }

    private Map<String, Object> normalizeAdminConversationDto(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("lastMessagePayload", parseJson(row.get("lastMessagePayload")));
        normalizeTimeFields(out, "createdAt", "lastMessageAt", "updatedAt");
        out.put("user", Map.of(
                "userId", row.get("userId"),
                "nickname", blank(row.get("userNickname")),
                "avatar", blank(row.get("userAvatar"))
        ));
        out.put("peerUser", Map.of(
                "userId", row.get("peerUserId"),
                "nickname", blank(row.get("peerNickname")),
                "avatar", blank(row.get("peerAvatar"))
        ));
        return out;
    }

    private Map<String, Object> normalizePayload(long fromUserId, String messageType, Object raw) {
        Map<String, Object> source = objectToMap(raw);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (TYPE_TEXT.equals(messageType)) {
            String text = limit(requiredText(source.get("text"), "消息不能为空"), MAX_TEXT_LENGTH);
            payload.put("text", text);
            return payload;
        }
        String mediaKey = trimToNull(str(firstNonNull(source.get("mediaKey"), source.get("url"), source.get("path"))));
        if (mediaKey == null || mediaKey.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "图片地址不正确");
        }
        uploadedAssetService.requireOwnedImage(fromUserId, mediaKey);
        payload.put("mediaKey", limit(mediaKey, 512));
        String width = trimToNull(str(source.get("width")));
        String height = trimToNull(str(source.get("height")));
        if (width != null) {
            payload.put("width", width);
        }
        if (height != null) {
            payload.put("height", height);
        }
        return payload;
    }

    private String buildPreview(String messageType, Map<String, Object> payload) {
        if (TYPE_IMAGE.equals(messageType)) {
            return "[图片]";
        }
        return limit(blank(payload.get("text")).replaceAll("\\s+", " "), 80);
    }

    private String normalizeMessageType(Object raw) {
        String value = blank(raw).toLowerCase(Locale.ROOT);
        if (TYPE_TEXT.equals(value) || TYPE_IMAGE.equals(value)) {
            return value;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "消息类型不支持");
    }

    private String normalizeMessageTypeOptional(String raw) {
        String value = trimToNull(raw);
        return value == null ? null : normalizeMessageType(value);
    }

    private String normalizeMessageStatusOptional(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (STATUS_NORMAL.equals(normalized) || STATUS_RECALLED.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "消息状态不支持");
    }

    private String normalizeClientMsgId(Object raw) {
        String value = trimToNull(str(raw));
        if (value == null) {
            return null;
        }
        if (value.length() > 80) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientMsgId 过长");
        }
        return value;
    }

    private Map<String, Object> objectToMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, value) -> out.put(String.valueOf(key), value));
            return out;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    return objectMapper.readValue(trimmed, new TypeReference<>() {});
                } catch (Exception ignored) {
                    return Map.of("text", text);
                }
            }
            return Map.of("text", text);
        }
        return Map.of();
    }

    private static void normalizeTimeFields(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof LocalDateTime localDateTime) {
                map.put(key, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime));
            } else if (value instanceof OffsetDateTime offsetDateTime) {
                map.put(key, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offsetDateTime));
            } else if (value instanceof java.util.Date date) {
                map.put(key, date.toInstant().toString());
            }
        }
    }

    private Object parseJson(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> || raw instanceof List<?>) {
            return raw;
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "消息保存失败", e);
        }
    }

    public static String conversationKey(long a, long b) {
        long left = Math.min(a, b);
        long right = Math.max(a, b);
        return left + ":" + right;
    }

    private static long requireUserId(AppUser user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return user.getId();
    }

    private static long longRequired(Object value, String message) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = value == null ? "" : String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return Long.parseLong(text);
            }
        } catch (Exception ignored) {
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static Long longOrNull(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = value == null ? "" : String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String requiredText(Object value, String message) {
        String text = trimToNull(str(value));
        if (text == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
        return text;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean userIdEquals(Long value, long expected) {
        return value != null && value == expected;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return value != null && ("true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)));
    }

    private static String blank(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String limit(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
