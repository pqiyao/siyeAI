package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.conversation.service.ConversationBranchService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern/branches")
public class ApiV1TavernBranchController {

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AppConversationService conversationService;
    private final AppConversationMapper conversationMapper;
    private final ConversationBranchService branchService;
    private final AppMessageMapper messageMapper;
    private final ChatSnapshotService snapshotService;
    private final AppChatService chatService;

    public ApiV1TavernBranchController(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppConversationService conversationService,
            AppConversationMapper conversationMapper,
            ConversationBranchService branchService,
            AppMessageMapper messageMapper,
            ChatSnapshotService snapshotService,
            AppChatService chatService
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.conversationService = conversationService;
        this.conversationMapper = conversationMapper;
        this.branchService = branchService;
        this.messageMapper = messageMapper;
        this.snapshotService = snapshotService;
        this.chatService = chatService;
    }

    @PostMapping("/list")
    public ApiV1Result<Map<String, Object>> list(@RequestBody Map<String, Object> payload) {
        BranchRequestContext context = requireContext(payload, true);
        List<String> openingVariants = loadOpeningVariants(context);
        return ApiV1Result.ok(buildBranchEnvelope(context.conversationId(), context.userId(), openingVariants));
    }

    @PostMapping("/opening/select")
    public ApiV1Result<Map<String, Object>> selectOpening(@RequestBody Map<String, Object> payload) {
        BranchRequestContext context = requireContext(payload, false);
        int variantIndex = requireVariantIndex(payload);
        List<String> openingVariants = loadOpeningVariants(context);
        AppConversation conversation = requireConversation(context.conversationId(), context.userId());
        AppConversationBranch branch = branchService.selectOpeningBranch(conversation, variantIndex, openingVariants);
        snapshotService.saveSnapshotFromDb(context.conversationId(), branch.getId(), 800);
        return ApiV1Result.ok(buildBranchEnvelope(context.conversationId(), context.userId(), openingVariants));
    }

    @PostMapping("/switch")
    public ApiV1Result<Map<String, Object>> switchBranch(@RequestBody Map<String, Object> payload) {
        BranchRequestContext context = requireContext(payload, false);
        long branchId = requireLong(payload, "branchId");
        AppConversation conversation = requireConversation(context.conversationId(), context.userId());
        AppConversationBranch target = branchService.switchActiveBranch(conversation, branchId);
        snapshotService.saveSnapshotFromDb(context.conversationId(), target.getId(), 800);
        return ApiV1Result.ok(buildBranchEnvelope(
                context.conversationId(),
                context.userId(),
                loadOpeningVariants(context)
        ));
    }

    @PostMapping("/fork")
    public ApiV1Result<Map<String, Object>> fork(@RequestBody Map<String, Object> payload) {
        BranchRequestContext context = requireContext(payload, false);
        long messageId = parseDbMessageId(requireString(payload, "messageId"));
        Integer variantIndex = optionalInteger(payload == null ? null : payload.get("variantIndex"));
        String title = optionalString(payload == null ? null : payload.get("title"));
        AppConversation conversation = requireConversation(context.conversationId(), context.userId());
        AppConversationBranch branch = branchService.forkFromMessage(conversation, messageId, variantIndex, title);
        snapshotService.saveSnapshotFromDb(context.conversationId(), branch.getId(), 800);
        return ApiV1Result.ok(buildBranchEnvelope(
                context.conversationId(),
                context.userId(),
                loadOpeningVariants(context)
        ));
    }

    private Map<String, Object> buildBranchEnvelope(
            long conversationId,
            long userId,
            List<String> openingVariants
    ) {
        AppConversation conversation = requireConversation(conversationId, userId);
        AppConversationBranch active = branchService.requireActiveBranch(conversation);
        List<AppConversationBranch> branchEntities = branchService.reconcileOpeningVariantBindings(
                conversation,
                openingVariants
        );
        List<Map<String, Object>> branches = branchEntities.stream()
                .map(branch -> toBranchRow(branch, active.getId()))
                .toList();
        Map<String, Object> out = new HashMap<>();
        out.put("conversationId", conversationId);
        out.put("activeBranchId", active.getId());
        out.put("branches", branches);
        out.put("openings", toOpeningRows(conversationId, branchEntities, openingVariants, active.getId()));
        return out;
    }

    private List<Map<String, Object>> toOpeningRows(
            long conversationId,
            List<AppConversationBranch> branches,
            List<String> openingVariants,
            long activeBranchId
    ) {
        Map<Integer, AppConversationBranch> branchesByVariant = new HashMap<>();
        for (AppConversationBranch branch : branches) {
            if (branch != null && branch.getOpeningVariantIndex() != null) {
                branchesByVariant.putIfAbsent(branch.getOpeningVariantIndex(), branch);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < openingVariants.size(); i++) {
            AppConversationBranch branch = branchesByVariant.get(i);
            long branchId = branch == null || branch.getId() == null ? 0L : branch.getId();
            Map<String, Object> row = new HashMap<>();
            row.put("variantIndex", i);
            row.put("branchId", branchId > 0 ? branchId : "");
            row.put("active", branchId > 0 && branchId == activeBranchId);
            row.put("created", branchId > 0);
            row.put("preview", preview(openingVariants.get(i)));
            row.put("messageCount", branchId > 0
                    ? messageMapper.countVisibleByConversationBranch(conversationId, branchId)
                    : 0);
            row.put("updatedAt", branch == null ? null : branch.getUpdatedAt());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toBranchRow(AppConversationBranch branch, long activeBranchId) {
        long branchId = branch.getId() == null ? 0L : branch.getId();
        AppMessage latest = branchId <= 0
                ? null
                : messageMapper.findLatestVisibleByConversationBranch(branch.getConversationId(), branchId);
        Map<String, Object> row = new HashMap<>();
        row.put("id", branchId);
        row.put("title", branch.getTitle() == null ? "" : branch.getTitle());
        row.put("active", branchId == activeBranchId);
        row.put("defaultBranch", branch.isDefaultBranch());
        row.put("parentBranchId", branch.getParentBranchId());
        row.put("forkMessageId", branch.getForkMessageId() == null ? "" : "db_" + branch.getForkMessageId());
        row.put("openingVariantIndex", branch.getOpeningVariantIndex());
        row.put("messageCount", branchId <= 0 ? 0 : messageMapper.countVisibleByConversationBranch(branch.getConversationId(), branchId));
        row.put("lastMessagePreview", preview(latest == null ? "" : latest.getContent()));
        row.put("lastMessageRole", latest == null ? "" : latest.getRole());
        row.put("updatedAt", branch.getUpdatedAt());
        row.put("createdAt", branch.getCreatedAt());
        return row;
    }

    private BranchRequestContext requireContext(Map<String, Object> payload, boolean createIfMissing) {
        String clientUid = requireString(payload, "clientUid");
        long characterId = requireLong(payload, "characterId");
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        ConversationDetailDto detail = createIfMissing
                ? conversationService.ensureDetailByH5Character(clientUid, characterId, token)
                : conversationService.findDetailByH5Character(clientUid, characterId, token);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        chatService.requireActiveBranchId(detail.conversationId(), token);
        return new BranchRequestContext(detail.conversationId(), userId, token);
    }

    private List<String> loadOpeningVariants(BranchRequestContext context) {
        chatService.ensureOpeningAssistantMessage(context.conversationId(), context.token());
        return chatService.listOpeningVariants(context.conversationId(), context.token());
    }

    private AppConversation requireConversation(long conversationId, long userId) {
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        return conversation;
    }

    private static String preview(String raw) {
        String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        return value.length() <= 96 ? value : value.substring(0, 96);
    }

    private static String requireString(Map<String, Object> payload, String key) {
        if (payload == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, key + " missing");
        }
        String value = optionalString(payload.get(key));
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, key + " missing");
        }
        return value;
    }

    private static String optionalString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long requireLong(Map<String, Object> payload, String key) {
        Long value = optionalLong(payload == null ? null : payload.get(key));
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, key + " missing");
        }
        return value;
    }

    private static Long optionalLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer optionalInteger(Object value) {
        Long parsed = optionalLong(value);
        if (parsed == null) {
            return null;
        }
        if (parsed < 0 || parsed > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "variantIndex invalid");
        }
        return parsed.intValue();
    }

    private static int requireVariantIndex(Map<String, Object> payload) {
        Integer value = optionalInteger(payload == null ? null : payload.get("variantIndex"));
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "variantIndex missing");
        }
        return value;
    }

    private static long parseDbMessageId(String messageId) {
        String value = messageId == null ? "" : messageId.trim();
        if (value.startsWith("db_")) {
            value = value.substring(3);
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (Exception ignored) {
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "messageId invalid");
    }

    private record BranchRequestContext(long conversationId, long userId, String token) {
    }
}
