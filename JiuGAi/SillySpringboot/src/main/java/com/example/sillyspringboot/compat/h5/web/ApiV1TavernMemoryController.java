package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshResult;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/tavern/memory")
public class ApiV1TavernMemoryController {

    private static final String DEFAULT_ENTRY_FILTER = "all";
    private static final int DEFAULT_ENTRY_PAGE = 1;
    private static final int DEFAULT_ENTRY_PAGE_SIZE = 20;
    private static final int MAX_ENTRY_PAGE_SIZE = 50;
    private static final Set<String> ENTRY_FILTERS = Set.of("all", "enabled", "disabled", "archived");

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AppConversationService conversationService;
    private final AppConversationMapper conversationMapper;
    private final AppConversationBranchMapper branchMapper;
    private final AppConversationMemoryService conversationMemoryService;

    public ApiV1TavernMemoryController(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppConversationService conversationService,
            AppConversationMapper conversationMapper,
            AppConversationBranchMapper branchMapper,
            AppConversationMemoryService conversationMemoryService
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.conversationService = conversationService;
        this.conversationMapper = conversationMapper;
        this.branchMapper = branchMapper;
        this.conversationMemoryService = conversationMemoryService;
    }

    @PostMapping("/refresh")
    public ApiV1Result<ConversationMemoryRefreshResult> refresh(@RequestBody Map<String, Object> payload) {
        BranchContext context = requireBranchContext(payload);
        return ApiV1Result.ok(conversationMemoryService.refreshConversationMemoryManual(context.conversationId(), context.branchId()));
    }

    @PostMapping("/entries")
    public ApiV1Result<Map<String, Object>> entries(@RequestBody Map<String, Object> payload) {
        BranchContext context = requireBranchContext(payload);
        String entryFilter = requireEntryFilter(payload);
        int page = requirePositiveInt(payload, "page", DEFAULT_ENTRY_PAGE, Integer.MAX_VALUE);
        int pageSize = requirePositiveInt(payload, "pageSize", DEFAULT_ENTRY_PAGE_SIZE, MAX_ENTRY_PAGE_SIZE);
        return ApiV1Result.ok(conversationMemoryService.toH5MemoryDetailMap(
                context.conversationId(),
                context.branchId(),
                entryFilter,
                page,
                pageSize
        ));
    }

    @PostMapping("/disable-entry")
    public ApiV1Result<Map<String, Object>> disableEntry(@RequestBody Map<String, Object> payload) {
        BranchContext context = requireBranchContext(payload);
        long entryId = requireEntryId(payload);
        return ApiV1Result.ok(conversationMemoryService.disableMemoryEntry(context.conversationId(), context.branchId(), entryId));
    }

    @PostMapping("/set-entry-enabled")
    public ApiV1Result<Map<String, Object>> setEntryEnabled(@RequestBody Map<String, Object> payload) {
        BranchContext context = requireBranchContext(payload);
        long entryId = requireEntryId(payload);
        Boolean enabled = payload == null ? null : asBoolean(payload.get("enabled"));
        if (enabled == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "enabled missing");
        }
        return ApiV1Result.ok(conversationMemoryService.setMemoryEntryEnabled(
                context.conversationId(),
                context.branchId(),
                entryId,
                enabled
        ));
    }

    @PostMapping("/delete-entry")
    public ApiV1Result<Map<String, Object>> deleteEntry(@RequestBody Map<String, Object> payload) {
        BranchContext context = requireBranchContext(payload);
        long entryId = requireEntryId(payload);
        return ApiV1Result.ok(conversationMemoryService.deleteMemoryEntry(
                context.conversationId(),
                context.branchId(),
                entryId
        ));
    }

    @PostMapping("/sync")
    public ApiV1Result<Map<String, Object>> sync(@RequestBody Map<String, Object> payload) {
        BranchContext context = requireBranchContext(payload);
        return ApiV1Result.ok(conversationMemoryService.retryWorldbookSync(
                context.conversationId(),
                context.branchId()
        ));
    }

    private static long requireEntryId(Map<String, Object> payload) {
        Long entryId = payload == null ? null : asLong(payload.get("entryId"));
        if (entryId == null || entryId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "entryId missing");
        }
        return entryId;
    }

    private static String requireEntryFilter(Map<String, Object> payload) {
        String raw = payload == null ? null : asString(payload.get("entryFilter"));
        String filter = raw == null || raw.isBlank()
                ? DEFAULT_ENTRY_FILTER
                : raw.trim().toLowerCase(Locale.ROOT);
        if (!ENTRY_FILTERS.contains(filter)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "entryFilter invalid");
        }
        return filter;
    }

    private static int requirePositiveInt(
            Map<String, Object> payload,
            String field,
            int defaultValue,
            int maxValue
    ) {
        Object raw = payload == null ? null : payload.get(field);
        if (raw == null || raw instanceof String s && s.isBlank()) {
            return defaultValue;
        }
        Long value = asWholeLong(raw);
        if (value == null || value <= 0 || value > maxValue) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " invalid");
        }
        return value.intValue();
    }

    private BranchContext requireBranchContext(Map<String, Object> payload) {
        String clientUid = payload == null ? null : asString(payload.get("clientUid"));
        Long characterId = payload == null ? null : asLong(payload.get("characterId"));
        Long conversationId = payload == null ? null : asLong(payload.get("conversationId"));
        Long branchId = payload == null ? null : asLong(payload.get("branchId"));
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");
        }
        if (characterId == null || characterId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId missing");
        }
        if (conversationId == null || conversationId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "conversationId missing");
        }
        if (branchId == null || branchId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "branchId missing");
        }
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        ConversationDetailDto detail = conversationService.findDetailByH5Character(clientUid, characterId, token);
        if (detail == null || detail.conversationId() == null || detail.conversationId().longValue() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null
                || conversation.getCharacterId() == null
                || conversation.getCharacterId().longValue() != characterId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationBranch branch = branchMapper.findByIdForConversation(conversationId, branchId);
        if (branch == null || branch.getUserId() == null || branch.getUserId().longValue() != userId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "branch not found");
        }
        return new BranchContext(conversationId, branchId);
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Long asWholeLong(Object o) {
        if (o instanceof Byte || o instanceof Short || o instanceof Integer || o instanceof Long) {
            return ((Number) o).longValue();
        }
        if (o instanceof Number n) {
            double value = n.doubleValue();
            if (!Double.isFinite(value) || value != Math.rint(value)) {
                return null;
            }
            if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
                return null;
            }
            return (long) value;
        }
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean asBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        if (o instanceof String s) {
            String value = s.trim();
            if ("true".equalsIgnoreCase(value) || "1".equals(value)) return true;
            if ("false".equalsIgnoreCase(value) || "0".equals(value)) return false;
        }
        return null;
    }

    private record BranchContext(long conversationId, long branchId) {
    }
}
