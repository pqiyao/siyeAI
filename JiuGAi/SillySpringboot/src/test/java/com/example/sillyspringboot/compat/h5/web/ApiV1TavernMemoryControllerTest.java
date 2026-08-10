package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiV1TavernMemoryControllerTest {

    private static final String CLIENT_UID = "h5u_10";
    private static final String TOKEN = "token";
    private static final long USER_ID = 10L;
    private static final long CHARACTER_ID = 20L;
    private static final long CONVERSATION_ID = 30L;
    private static final long BRANCH_ID = 40L;

    @Test
    void disabledFeature_shouldRejectEveryMemoryEndpointBeforeAuthenticationOrDataAccess() {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        AppConversationService conversationService = mock(AppConversationService.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppConversationMemoryService memoryService = mock(AppConversationMemoryService.class);
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "当前已关闭长期记忆功能"))
                .when(featureSettingsService).ensureLongTermMemoryEnabled();
        ApiV1TavernMemoryController controller = new ApiV1TavernMemoryController(
                h5Auth,
                tokenService,
                conversationService,
                conversationMapper,
                branchMapper,
                memoryService,
                featureSettingsService
        );

        List<Runnable> calls = List.of(
                () -> controller.refresh(Map.of()),
                () -> controller.entries(Map.of()),
                () -> controller.saveEntry(Map.of()),
                () -> controller.disableEntry(Map.of()),
                () -> controller.setEntryEnabled(Map.of()),
                () -> controller.deleteEntry(Map.of()),
                () -> controller.sync(Map.of())
        );
        calls.forEach(call -> assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN)));

        verify(featureSettingsService, times(calls.size())).ensureLongTermMemoryEnabled();
        verifyNoInteractions(h5Auth, tokenService, conversationService, conversationMapper, branchMapper, memoryService);
    }

    @Test
    void entriesUsesExplicitPanelBranchEvenWhenItIsNotTheCurrentActiveBranch() {
        Fixture fixture = fixture();
        AppConversation conversation = conversation(USER_ID, CHARACTER_ID);
        conversation.setActiveBranchId(99L);
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID)).thenReturn(conversation);
        when(fixture.branchMapper.findByIdForConversation(CONVERSATION_ID, BRANCH_ID))
                .thenReturn(branch(USER_ID));
        when(fixture.memoryService.toH5MemoryDetailMap(CONVERSATION_ID, BRANCH_ID, "all", 1, 20))
                .thenReturn(Map.of("branchId", BRANCH_ID));

        ApiV1Result<Map<String, Object>> result = fixture.controller.entries(payload(CONVERSATION_ID, BRANCH_ID));

        assertThat(result.code()).isEqualTo(1);
        assertThat(result.data()).containsEntry("branchId", BRANCH_ID);
        verify(fixture.memoryService).toH5MemoryDetailMap(CONVERSATION_ID, BRANCH_ID, "all", 1, 20);
    }

    @Test
    void entriesPassesValidatedFilterAndPaginationToTheExplicitBranchQuery() {
        Fixture fixture = fixture();
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID))
                .thenReturn(conversation(USER_ID, CHARACTER_ID));
        when(fixture.branchMapper.findByIdForConversation(CONVERSATION_ID, BRANCH_ID))
                .thenReturn(branch(USER_ID));
        when(fixture.memoryService.toH5MemoryDetailMap(CONVERSATION_ID, BRANCH_ID, "archived", 3, 50))
                .thenReturn(Map.of("entryFilter", "archived"));
        Map<String, Object> request = new HashMap<>(payload(CONVERSATION_ID, BRANCH_ID));
        request.put("entryFilter", " ARCHIVED ");
        request.put("page", "3");
        request.put("pageSize", 50L);

        ApiV1Result<Map<String, Object>> result = fixture.controller.entries(request);

        assertThat(result.code()).isEqualTo(1);
        assertThat(result.data()).containsEntry("entryFilter", "archived");
        verify(fixture.memoryService).toH5MemoryDetailMap(CONVERSATION_ID, BRANCH_ID, "archived", 3, 50);
    }

    @Test
    void entriesRejectsInvalidFilterPageAndPageSizeBeforeQueryingMemory() {
        Fixture fixture = fixture();
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID))
                .thenReturn(conversation(USER_ID, CHARACTER_ID));
        when(fixture.branchMapper.findByIdForConversation(CONVERSATION_ID, BRANCH_ID))
                .thenReturn(branch(USER_ID));
        Map<String, Object> invalidFilter = new HashMap<>(payload(CONVERSATION_ID, BRANCH_ID));
        invalidFilter.put("entryFilter", "deleted");
        Map<String, Object> invalidPage = new HashMap<>(payload(CONVERSATION_ID, BRANCH_ID));
        invalidPage.put("page", 0);
        Map<String, Object> fractionalPage = new HashMap<>(payload(CONVERSATION_ID, BRANCH_ID));
        fractionalPage.put("page", 1.5d);
        Map<String, Object> oversizedPageSize = new HashMap<>(payload(CONVERSATION_ID, BRANCH_ID));
        oversizedPageSize.put("pageSize", 51);

        assertValidationFailed(() -> fixture.controller.entries(invalidFilter));
        assertValidationFailed(() -> fixture.controller.entries(invalidPage));
        assertValidationFailed(() -> fixture.controller.entries(fractionalPage));
        assertValidationFailed(() -> fixture.controller.entries(oversizedPageSize));

        verify(fixture.memoryService, never()).toH5MemoryDetailMap(
                anyLong(),
                anyLong(),
                anyString(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void entryMutationUsesTheSameExplicitPanelBranch() {
        Fixture fixture = fixture();
        AppConversation conversation = conversation(USER_ID, CHARACTER_ID);
        conversation.setActiveBranchId(99L);
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID)).thenReturn(conversation);
        when(fixture.branchMapper.findByIdForConversation(CONVERSATION_ID, BRANCH_ID))
                .thenReturn(branch(USER_ID));
        when(fixture.memoryService.setMemoryEntryEnabled(CONVERSATION_ID, BRANCH_ID, 50L, false))
                .thenReturn(Map.of("branchId", BRANCH_ID));
        Map<String, Object> payload = new HashMap<>(payload(CONVERSATION_ID, BRANCH_ID));
        payload.put("entryId", 50L);
        payload.put("enabled", false);

        ApiV1Result<Map<String, Object>> result = fixture.controller.setEntryEnabled(payload);

        assertThat(result.code()).isEqualTo(1);
        verify(fixture.memoryService).setMemoryEntryEnabled(CONVERSATION_ID, BRANCH_ID, 50L, false);
    }

    @Test
    void saveEntryPassesValidatedManualMemoryFieldsToTheExplicitBranch() {
        Fixture fixture = fixture();
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID))
                .thenReturn(conversation(USER_ID, CHARACTER_ID));
        when(fixture.branchMapper.findByIdForConversation(CONVERSATION_ID, BRANCH_ID))
                .thenReturn(branch(USER_ID));
        when(fixture.memoryService.saveManualMemoryEntry(
                CONVERSATION_ID, BRANCH_ID, 50L, "relationship", "关系进展", "双方已经建立信任",
                List.of("信任"), List.of("同伴"), 180, true, true
        )).thenReturn(Map.of("savedEntryId", 50L));
        Map<String, Object> request = new HashMap<>(payload(CONVERSATION_ID, BRANCH_ID));
        request.put("entryId", 50L);
        request.put("memoryType", " RELATIONSHIP " );
        request.put("title", " 关系进展 " );
        request.put("content", " 双方已经建立信任 " );
        request.put("keywords", List.of(" 信任 "));
        request.put("secondaryKeywords", List.of(" 同伴 "));
        request.put("priority", 180);
        request.put("constantInjection", true);
        request.put("manualPinned", true);

        ApiV1Result<Map<String, Object>> result = fixture.controller.saveEntry(request);

        assertThat(result.code()).isEqualTo(1);
        assertThat(result.data()).containsEntry("savedEntryId", 50L);
        verify(fixture.memoryService).saveManualMemoryEntry(
                CONVERSATION_ID, BRANCH_ID, 50L, "relationship", "关系进展", "双方已经建立信任",
                List.of("信任"), List.of("同伴"), 180, true, true
        );
    }

    @Test
    void saveEntryRejectsMalformedFieldsAndUnsupportedConstantInjection() {
        Fixture fixture = fixture();
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID))
                .thenReturn(conversation(USER_ID, CHARACTER_ID));
        when(fixture.branchMapper.findByIdForConversation(CONVERSATION_ID, BRANCH_ID))
                .thenReturn(branch(USER_ID));
        Map<String, Object> request = new HashMap<>(payload(CONVERSATION_ID, BRANCH_ID));
        request.put("memoryType", "event");
        request.put("content", "已经完成第一阶段任务");
        request.put("keywords", List.of("任务"));
        request.put("constantInjection", true);

        assertValidationFailed(() -> fixture.controller.saveEntry(request));

        request.put("constantInjection", false);
        request.put("keywords", "任务");
        assertValidationFailed(() -> fixture.controller.saveEntry(request));

        request.put("keywords", List.of("任务"));
        request.put("priority", 201);
        assertValidationFailed(() -> fixture.controller.saveEntry(request));
    }

    @Test
    void rejectsConversationIdThatDoesNotMatchTheAuthenticatedCharacterConversation() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.controller.entries(payload(31L, BRANCH_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(fixture.conversationMapper, never()).findByIdForUser(31L, USER_ID);
        verify(fixture.memoryService, never()).toH5MemoryDetailMap(31L, BRANCH_ID, "all", 1, 20);
    }

    @Test
    void rejectsBranchThatDoesNotBelongToTheRequestedConversation() {
        Fixture fixture = fixture();
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID))
                .thenReturn(conversation(USER_ID, CHARACTER_ID));
        when(fixture.branchMapper.findByIdForConversation(CONVERSATION_ID, BRANCH_ID)).thenReturn(null);

        assertThatThrownBy(() -> fixture.controller.entries(payload(CONVERSATION_ID, BRANCH_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(fixture.memoryService, never()).toH5MemoryDetailMap(CONVERSATION_ID, BRANCH_ID, "all", 1, 20);
    }

    @Test
    void rejectsConversationThatIsNotOwnedByTheAuthenticatedUser() {
        Fixture fixture = fixture();
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> fixture.controller.entries(payload(CONVERSATION_ID, BRANCH_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(fixture.branchMapper, never()).findByIdForConversation(CONVERSATION_ID, BRANCH_ID);
        verify(fixture.memoryService, never()).toH5MemoryDetailMap(CONVERSATION_ID, BRANCH_ID, "all", 1, 20);
    }

    @Test
    void rejectsBranchOwnedByAnotherUser() {
        Fixture fixture = fixture();
        when(fixture.conversationMapper.findByIdForUser(CONVERSATION_ID, USER_ID))
                .thenReturn(conversation(USER_ID, CHARACTER_ID));
        when(fixture.branchMapper.findByIdForConversation(CONVERSATION_ID, BRANCH_ID))
                .thenReturn(branch(11L));

        assertThatThrownBy(() -> fixture.controller.entries(payload(CONVERSATION_ID, BRANCH_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(fixture.memoryService, never()).toH5MemoryDetailMap(CONVERSATION_ID, BRANCH_ID, "all", 1, 20);
    }

    @Test
    void requiresExplicitConversationAndBranchIds() {
        Fixture fixture = fixture();
        Map<String, Object> withoutScope = Map.of(
                "clientUid", CLIENT_UID,
                "characterId", CHARACTER_ID
        );

        assertThatThrownBy(() -> fixture.controller.entries(withoutScope))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(fixture.memoryService, never()).toH5MemoryDetailMap(CONVERSATION_ID, BRANCH_ID, "all", 1, 20);
    }

    private static void assertValidationFailed(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private static Fixture fixture() {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        AppConversationService conversationService = mock(AppConversationService.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppConversationMemoryService memoryService = mock(AppConversationMemoryService.class);
        AppUser user = new AppUser();
        user.setId(USER_ID);
        when(h5Auth.requireAuthenticatedTokenForClientUid(CLIENT_UID)).thenReturn(TOKEN);
        when(tokenService.validateAndLoadUser(TOKEN)).thenReturn(user);
        when(conversationService.findDetailByH5Character(CLIENT_UID, CHARACTER_ID, TOKEN))
                .thenReturn(new ConversationDetailDto(CONVERSATION_ID, CHARACTER_ID, null, null));
        ApiV1TavernMemoryController controller = new ApiV1TavernMemoryController(
                h5Auth,
                tokenService,
                conversationService,
                conversationMapper,
                branchMapper,
                memoryService
        );
        return new Fixture(controller, conversationMapper, branchMapper, memoryService);
    }

    private static Map<String, Object> payload(long conversationId, long branchId) {
        return Map.of(
                "clientUid", CLIENT_UID,
                "characterId", CHARACTER_ID,
                "conversationId", conversationId,
                "branchId", branchId
        );
    }

    private static AppConversation conversation(long userId, long characterId) {
        AppConversation conversation = new AppConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(userId);
        conversation.setCharacterId(characterId);
        return conversation;
    }

    private static AppConversationBranch branch(long userId) {
        AppConversationBranch branch = new AppConversationBranch();
        branch.setId(BRANCH_ID);
        branch.setConversationId(CONVERSATION_ID);
        branch.setUserId(userId);
        return branch;
    }

    private record Fixture(
            ApiV1TavernMemoryController controller,
            AppConversationMapper conversationMapper,
            AppConversationBranchMapper branchMapper,
            AppConversationMemoryService memoryService
    ) {
    }
}
