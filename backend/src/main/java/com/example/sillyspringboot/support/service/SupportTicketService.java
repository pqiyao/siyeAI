package com.example.sillyspringboot.support.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.entity.AppUserMessage;
import com.example.sillyspringboot.compat.h5.mapper.AppUserMessageMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.support.entity.AppSupportTicket;
import com.example.sillyspringboot.support.entity.AppSupportTicketMessage;
import com.example.sillyspringboot.support.mapper.AppSupportTicketMapper;
import com.example.sillyspringboot.support.mapper.AppSupportTicketMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SupportTicketService {

    public static final String TYPE_PAYMENT = "PAYMENT";
    public static final String TYPE_ACCOUNT = "ACCOUNT";
    public static final String TYPE_BUG = "BUG";
    public static final String TYPE_REPORT = "REPORT";
    public static final String TYPE_OTHER = "OTHER";

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_WAIT_USER = "WAIT_USER";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";

    private static final DateTimeFormatter TICKET_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_SUBJECT_LEN = 120;
    private static final int MAX_CONTENT_LEN = 5000;
    private static final int MAX_ATTACHMENTS = 6;

    private final AppSupportTicketMapper ticketMapper;
    private final AppSupportTicketMessageMapper messageMapper;
    private final AppPaymentOrderMapper paymentOrderMapper;
    private final AppCharacterMapper characterMapper;
    private final AppUserMapper userMapper;
    private final AppUserMessageMapper userMessageMapper;
    private final ObjectMapper objectMapper;

    public SupportTicketService(
            AppSupportTicketMapper ticketMapper,
            AppSupportTicketMessageMapper messageMapper,
            AppPaymentOrderMapper paymentOrderMapper,
            AppCharacterMapper characterMapper,
            AppUserMapper userMapper,
            AppUserMessageMapper userMessageMapper,
            ObjectMapper objectMapper
    ) {
        this.ticketMapper = ticketMapper;
        this.messageMapper = messageMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.characterMapper = characterMapper;
        this.userMapper = userMapper;
        this.userMessageMapper = userMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createUserTicket(long userId, String clientUid, CreateTicketCommand command) {
        CreateTicketCommand normalized = normalizeCreateCommand(command);
        LocalDateTime now = LocalDateTime.now();

        AppPaymentOrder order = validateOrderIfNeeded(userId, normalized.orderNo());
        AppCharacter character = validateCharacterIfNeeded(normalized.characterId(), normalized.ticketType());
        String characterName = normalized.characterName();
        if ((characterName == null || characterName.isBlank()) && character != null) {
            characterName = character.getName();
        }

        String ticketNo = generateTicketNo();
        String attachmentsJson = serializeAttachments(normalized.attachments());
        AppSupportTicket row = new AppSupportTicket();
        row.setTicketNo(ticketNo);
        row.setUserId(userId);
        row.setClientUidSnapshot(trimToNull(clientUid));
        row.setTicketType(normalized.ticketType());
        row.setSubject(buildSubject(normalized.ticketType(), normalized.subject(), order, characterName));
        row.setContent(normalized.content());
        row.setOrderNo(order == null ? trimToNull(normalized.orderNo()) : order.getOrderNo());
        row.setCharacterId(character == null ? normalized.characterId() : character.getId());
        row.setCharacterName(trimToNull(characterName));
        row.setStatus(STATUS_OPEN);
        row.setPriority(normalized.priority());
        row.setSource("H5");
        row.setLatestMessagePreview(buildPreview(normalized.content(), normalized.attachments()));
        row.setMessageCount(1);
        row.setLastUserReplyAt(now);
        row.setLastAdminReplyAt(null);
        row.setLastMessageAt(now);
        row.setClosedAt(null);
        ticketMapper.insert(row);

        insertMessage(row.getId(), "USER", displayUserName(userId), normalized.content(), attachmentsJson);
        return buildTicketEnvelope(row, order, character, true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUserTickets(long userId, String status, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String safeStatus = normalizeStatusOptional(status);
        return ticketMapper.listByUserId(userId, safeStatus, safeLimit).stream()
                .map(this::toTicketSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserTicket(long userId, String ticketNo) {
        AppSupportTicket ticket = requireUserTicket(userId, ticketNo);
        AppPaymentOrder order = ticket.getOrderNo() == null ? null : paymentOrderMapper.findByOrderNo(ticket.getOrderNo());
        AppCharacter character = ticket.getCharacterId() == null ? null : characterMapper.findById(ticket.getCharacterId());
        return buildTicketEnvelope(ticket, order, character, true);
    }

    @Transactional
    public Map<String, Object> replyAsUser(long userId, String ticketNo, String content, List<String> attachments) {
        AppSupportTicket ticket = requireUserTicket(userId, ticketNo);
        if (STATUS_CLOSED.equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "工单已关闭，请重新创建新工单");
        }
        String safeContent = validateContent(content);
        List<String> safeAttachments = normalizeAttachments(attachments);
        LocalDateTime now = LocalDateTime.now();
        insertMessage(ticket.getId(), "USER", displayUserName(userId), safeContent, serializeAttachments(safeAttachments));

        ticket.setStatus(STATUS_OPEN);
        ticket.setLatestMessagePreview(buildPreview(safeContent, safeAttachments));
        ticket.setMessageCount((ticket.getMessageCount() == null ? 0 : ticket.getMessageCount()) + 1);
        ticket.setLastUserReplyAt(now);
        ticket.setLastMessageAt(now);
        ticket.setClosedAt(null);
        ticketMapper.updateThreadState(ticket);
        return getUserTicket(userId, ticketNo);
    }

    @Transactional
    public Map<String, Object> createCharacterReport(
            long userId,
            String clientUid,
            Long characterId,
            String characterName,
            String subject,
            String content,
            List<String> attachments
    ) {
        return createUserTicket(
                userId,
                clientUid,
                new CreateTicketCommand(
                        TYPE_REPORT,
                        subject,
                        content,
                        null,
                        characterId,
                        characterName,
                        attachments,
                        PRIORITY_HIGH
                )
        );
    }

    @Transactional(readOnly = true)
    public long countAdminTickets(String keyword, String status, String ticketType, String priority) {
        return ticketMapper.countAdminList(
                trimToNull(keyword),
                normalizeStatusOptional(status),
                normalizeTypeOptional(ticketType),
                normalizePriorityOptional(priority)
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminTickets(
            String keyword,
            String status,
            String ticketType,
            String priority,
            int pageNum,
            int pageSize
    ) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePageNum - 1) * safePageSize;
        return ticketMapper.listAdminPage(
                        trimToNull(keyword),
                        normalizeStatusOptional(status),
                        normalizeTypeOptional(ticketType),
                        normalizePriorityOptional(priority),
                        offset,
                        safePageSize
                ).stream()
                .map(this::toAdminTicketSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminTicket(String ticketNo) {
        AppSupportTicket ticket = requireTicket(ticketNo);
        AppPaymentOrder order = ticket.getOrderNo() == null ? null : paymentOrderMapper.findByOrderNo(ticket.getOrderNo());
        AppCharacter character = ticket.getCharacterId() == null ? null : characterMapper.findById(ticket.getCharacterId());
        return buildTicketEnvelope(ticket, order, character, false);
    }

    @Transactional
    public Map<String, Object> replyAsAdmin(
            String ticketNo,
            String adminName,
            String content,
            String nextStatus,
            String nextPriority
    ) {
        AppSupportTicket ticket = requireTicket(ticketNo);
        String safeContent = validateContent(content);
        String status = nextStatus == null || nextStatus.isBlank() ? STATUS_WAIT_USER : normalizeStatus(nextStatus);
        String priority = nextPriority == null || nextPriority.isBlank()
                ? normalizePriority(ticket.getPriority())
                : normalizePriority(nextPriority);
        LocalDateTime now = LocalDateTime.now();
        insertMessage(ticket.getId(), "ADMIN", trimToNull(adminName) == null ? "客服" : adminName.trim(), safeContent, "[]");

        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setLatestMessagePreview(buildPreview(safeContent, List.of()));
        ticket.setMessageCount((ticket.getMessageCount() == null ? 0 : ticket.getMessageCount()) + 1);
        ticket.setLastAdminReplyAt(now);
        ticket.setLastMessageAt(now);
        ticket.setClosedAt(isTerminalStatus(status) ? now : null);
        ticketMapper.updateThreadState(ticket);
        notifyUser(ticket.getUserId(), ticket.getId(), ticket.getTicketNo(), safeContent);
        return getAdminTicket(ticketNo);
    }

    @Transactional
    public Map<String, Object> updateAdminTicket(String ticketNo, String nextStatus, String nextPriority) {
        AppSupportTicket ticket = requireTicket(ticketNo);
        ticket.setStatus(nextStatus == null || nextStatus.isBlank() ? ticket.getStatus() : normalizeStatus(nextStatus));
        ticket.setPriority(nextPriority == null || nextPriority.isBlank() ? normalizePriority(ticket.getPriority()) : normalizePriority(nextPriority));
        ticket.setClosedAt(isTerminalStatus(ticket.getStatus()) ? LocalDateTime.now() : null);
        ticketMapper.updateThreadState(ticket);
        return getAdminTicket(ticketNo);
    }

    public Map<String, Object> buildMeta() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("statusOptions", statusOptions());
        data.put("typeOptions", typeOptions());
        data.put("priorityOptions", priorityOptions());
        return data;
    }

    public List<Map<String, String>> statusOptions() {
        return List.of(
                option(STATUS_OPEN, "待处理"),
                option(STATUS_WAIT_USER, "待用户回复"),
                option(STATUS_RESOLVED, "已解决"),
                option(STATUS_CLOSED, "已关闭")
        );
    }

    public List<Map<String, String>> typeOptions() {
        return List.of(
                option(TYPE_PAYMENT, "支付问题"),
                option(TYPE_ACCOUNT, "账号问题"),
                option(TYPE_BUG, "Bug反馈"),
                option(TYPE_REPORT, "举报角色"),
                option(TYPE_OTHER, "其他")
        );
    }

    public List<Map<String, String>> priorityOptions() {
        return List.of(
                option(PRIORITY_LOW, "低"),
                option(PRIORITY_NORMAL, "普通"),
                option(PRIORITY_HIGH, "高"),
                option(PRIORITY_URGENT, "紧急")
        );
    }

    public record CreateTicketCommand(
            String ticketType,
            String subject,
            String content,
            String orderNo,
            Long characterId,
            String characterName,
            List<String> attachments,
            String priority
    ) {}

    private CreateTicketCommand normalizeCreateCommand(CreateTicketCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工单内容不能为空");
        }
        return new CreateTicketCommand(
                normalizeType(command.ticketType()),
                trimToNull(command.subject()),
                validateContent(command.content()),
                trimToNull(command.orderNo()),
                command.characterId(),
                trimToNull(command.characterName()),
                normalizeAttachments(command.attachments()),
                command.priority() == null || command.priority().isBlank()
                        ? defaultPriorityForType(command.ticketType())
                        : normalizePriority(command.priority())
        );
    }

    private AppPaymentOrder validateOrderIfNeeded(long userId, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        AppPaymentOrder order = paymentOrderMapper.findByOrderNoAndUserId(orderNo, userId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关联订单不存在");
        }
        return order;
    }

    private AppCharacter validateCharacterIfNeeded(Long characterId, String ticketType) {
        if (characterId == null) {
            if (TYPE_REPORT.equals(ticketType)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "举报工单需要关联角色");
            }
            return null;
        }
        AppCharacter character = characterMapper.findById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关联角色不存在");
        }
        return character;
    }

    private Map<String, Object> buildTicketEnvelope(
            AppSupportTicket ticket,
            AppPaymentOrder order,
            AppCharacter character,
            boolean userView
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", userView ? toTicketSummary(ticket) : toAdminTicketSummary(ticket));
        data.put("messages", messageMapper.listByTicketId(ticket.getId()).stream().map(this::toMessageMap).toList());
        if (order != null) {
            data.put("order", toOrderMap(order));
        }
        if (character != null) {
            data.put("character", toCharacterMap(character));
        } else if (ticket.getCharacterId() != null || ticket.getCharacterName() != null) {
            Map<String, Object> brief = new LinkedHashMap<>();
            brief.put("id", ticket.getCharacterId());
            brief.put("name", ticket.getCharacterName());
            data.put("character", brief);
        }
        return data;
    }

    private Map<String, Object> toTicketSummary(AppSupportTicket ticket) {
        Map<String, Object> data = toTicketBase(ticket);
        data.put("canReply", !STATUS_CLOSED.equals(ticket.getStatus()));
        return data;
    }

    private Map<String, Object> toAdminTicketSummary(AppSupportTicket ticket) {
        Map<String, Object> data = toTicketBase(ticket);
        data.put("userId", ticket.getUserId());
        data.put("clientUidSnapshot", ticket.getClientUidSnapshot());
        data.put("username", displayUserName(ticket.getUserId()));
        return data;
    }

    private Map<String, Object> toTicketBase(AppSupportTicket ticket) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", ticket.getId());
        data.put("ticketNo", ticket.getTicketNo());
        data.put("ticketType", ticket.getTicketType());
        data.put("ticketTypeLabel", labelForType(ticket.getTicketType()));
        data.put("subject", ticket.getSubject());
        data.put("content", ticket.getContent());
        data.put("orderNo", ticket.getOrderNo());
        data.put("characterId", ticket.getCharacterId());
        data.put("characterName", ticket.getCharacterName());
        data.put("status", ticket.getStatus());
        data.put("statusLabel", labelForStatus(ticket.getStatus()));
        data.put("priority", ticket.getPriority());
        data.put("priorityLabel", labelForPriority(ticket.getPriority()));
        data.put("source", ticket.getSource());
        data.put("latestMessagePreview", ticket.getLatestMessagePreview());
        data.put("messageCount", ticket.getMessageCount());
        data.put("lastUserReplyAt", ticket.getLastUserReplyAt());
        data.put("lastAdminReplyAt", ticket.getLastAdminReplyAt());
        data.put("lastMessageAt", ticket.getLastMessageAt());
        data.put("closedAt", ticket.getClosedAt());
        data.put("createdAt", ticket.getCreatedAt());
        data.put("updatedAt", ticket.getUpdatedAt());
        return data;
    }

    private Map<String, Object> toMessageMap(AppSupportTicketMessage row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("senderType", row.getSenderType());
        data.put("senderLabel", "ADMIN".equals(row.getSenderType()) ? "客服" : "用户");
        data.put("senderName", row.getSenderName());
        data.put("content", row.getContent());
        data.put("attachments", parseAttachments(row.getAttachmentsJson()));
        data.put("createdAt", row.getCreatedAt());
        return data;
    }

    private Map<String, Object> toOrderMap(AppPaymentOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("productCode", order.getProductCode());
        data.put("productName", order.getProductName());
        data.put("status", order.getStatus());
        data.put("paymentChannel", order.getPaymentChannel());
        data.put("amountCents", order.getAmountCents());
        data.put("createdAt", order.getCreatedAt());
        data.put("paidAt", order.getPaidAt());
        return data;
    }

    private Map<String, Object> toCharacterMap(AppCharacter character) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", character.getId());
        data.put("name", character.getName());
        data.put("coverUrl", character.getCoverUrl());
        data.put("avatarUrl", character.getAvatarUrl());
        return data;
    }

    private void insertMessage(long ticketId, String senderType, String senderName, String content, String attachmentsJson) {
        AppSupportTicketMessage message = new AppSupportTicketMessage();
        message.setTicketId(ticketId);
        message.setSenderType(senderType);
        message.setSenderName(trimToNull(senderName));
        message.setContent(content);
        message.setAttachmentsJson(attachmentsJson);
        messageMapper.insert(message);
    }

    private void notifyUser(long userId, long ticketId, String ticketNo, String replyContent) {
        AppUserMessage notice = new AppUserMessage();
        notice.setUserId(userId);
        notice.setMessageType("SUPPORT_TICKET");
        notice.setTitle("客服已回复");
        notice.setContent("工单 " + ticketNo + " 有新的客服回复：" + trimPreview(replyContent, 80));
        notice.setRelatedType("support_ticket");
        notice.setRelatedId(ticketId);
        notice.setReadFlag(Boolean.FALSE);
        userMessageMapper.insert(notice);
    }

    private AppSupportTicket requireUserTicket(long userId, String ticketNo) {
        AppSupportTicket ticket = ticketMapper.findByTicketNoAndUserId(ticketNo, userId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        return ticket;
    }

    private AppSupportTicket requireTicket(String ticketNo) {
        AppSupportTicket ticket = ticketMapper.findByTicketNo(ticketNo);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        return ticket;
    }

    private String validateContent(String raw) {
        String content = trimToNull(raw);
        if (content == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写问题描述");
        }
        if (content.length() > MAX_CONTENT_LEN) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "问题描述过长");
        }
        return content;
    }

    private List<String> normalizeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (String item : attachments) {
            String value = trimToNull(item);
            if (value == null) {
                continue;
            }
            if (value.length() > 500) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "附件地址过长");
            }
            list.add(value);
        }
        if (list.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "附件数量过多");
        }
        return List.copyOf(list);
    }

    private String serializeAttachments(List<String> attachments) {
        try {
            return objectMapper.writeValueAsString(attachments == null ? List.of() : attachments);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件保存失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseAttachments(String attachmentsJson) {
        if (attachmentsJson == null || attachmentsJson.isBlank()) {
            return List.of();
        }
        try {
            Object raw = objectMapper.readValue(attachmentsJson, List.class);
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildPreview(String content, List<String> attachments) {
        String preview = trimPreview(content, 90);
        if (attachments != null && !attachments.isEmpty()) {
            preview = preview + " [附件" + attachments.size() + "]";
        }
        return preview;
    }

    private String buildSubject(String ticketType, String subject, AppPaymentOrder order, String characterName) {
        String preferred = trimToNull(subject);
        if (preferred != null) {
            if (preferred.length() > MAX_SUBJECT_LEN) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工单标题过长");
            }
            return preferred;
        }
        return switch (ticketType) {
            case TYPE_PAYMENT -> order == null ? "支付问题" : "支付问题 - " + order.getOrderNo();
            case TYPE_ACCOUNT -> "账号问题";
            case TYPE_BUG -> "Bug反馈";
            case TYPE_REPORT -> characterName == null ? "举报角色" : "举报角色 - " + characterName;
            default -> "其他问题";
        };
    }

    private String displayUserName(Long userId) {
        if (userId == null) {
            return "用户";
        }
        AppUser user = userMapper.findById(userId);
        if (user == null) {
            return "用户";
        }
        String firstName = trimToNull(user.getFirstName());
        if (firstName != null) {
            return firstName;
        }
        String username = trimToNull(user.getUsername());
        return username == null ? "用户" : username;
    }

    private String generateTicketNo() {
        return "CS"
                + LocalDateTime.now().format(TICKET_TIME_FORMAT)
                + String.format(Locale.ROOT, "%04d", ThreadLocalRandom.current().nextInt(10_000));
    }

    private String normalizeType(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return TYPE_OTHER;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case TYPE_PAYMENT, TYPE_ACCOUNT, TYPE_BUG, TYPE_REPORT, TYPE_OTHER -> value.toUpperCase(Locale.ROOT);
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工单类型不支持");
        };
    }

    private String normalizeTypeOptional(String raw) {
        String value = trimToNull(raw);
        return value == null ? null : normalizeType(value);
    }

    private String normalizeStatus(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工单状态不能为空");
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case STATUS_OPEN, STATUS_WAIT_USER, STATUS_RESOLVED, STATUS_CLOSED -> value.toUpperCase(Locale.ROOT);
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工单状态不支持");
        };
    }

    private String normalizeStatusOptional(String raw) {
        String value = trimToNull(raw);
        return value == null ? null : normalizeStatus(value);
    }

    private String normalizePriority(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return PRIORITY_NORMAL;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case PRIORITY_LOW, PRIORITY_NORMAL, PRIORITY_HIGH, PRIORITY_URGENT -> value.toUpperCase(Locale.ROOT);
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "优先级不支持");
        };
    }

    private String normalizePriorityOptional(String raw) {
        String value = trimToNull(raw);
        return value == null ? null : normalizePriority(value);
    }

    private String defaultPriorityForType(String rawType) {
        String type = normalizeType(rawType);
        if (TYPE_PAYMENT.equals(type) || TYPE_REPORT.equals(type)) {
            return PRIORITY_HIGH;
        }
        return PRIORITY_NORMAL;
    }

    private String labelForType(String type) {
        return switch (normalizeType(type)) {
            case TYPE_PAYMENT -> "支付问题";
            case TYPE_ACCOUNT -> "账号问题";
            case TYPE_BUG -> "Bug反馈";
            case TYPE_REPORT -> "举报角色";
            default -> "其他";
        };
    }

    private String labelForStatus(String status) {
        return switch (normalizeStatus(status)) {
            case STATUS_OPEN -> "待处理";
            case STATUS_WAIT_USER -> "待用户回复";
            case STATUS_RESOLVED -> "已解决";
            case STATUS_CLOSED -> "已关闭";
            default -> status;
        };
    }

    private String labelForPriority(String priority) {
        return switch (normalizePriority(priority)) {
            case PRIORITY_LOW -> "低";
            case PRIORITY_NORMAL -> "普通";
            case PRIORITY_HIGH -> "高";
            case PRIORITY_URGENT -> "紧急";
            default -> priority;
        };
    }

    private boolean isTerminalStatus(String status) {
        String safe = normalizeStatus(status);
        return STATUS_RESOLVED.equals(safe) || STATUS_CLOSED.equals(safe);
    }

    private String trimPreview(String text, int maxLen) {
        String value = trimToNull(text);
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private Map<String, String> option(String value, String label) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put("label", label);
        return row;
    }
}
