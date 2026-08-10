package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminH5UserCleanupMapper {

    Long lockAppUser(@Param("userId") long userId);

    List<Map<String, Object>> listConversationStRefs(@Param("userId") long userId);

    List<Map<String, Object>> listOwnedCharacterCleanupRows(@Param("userId") long userId);

    List<Map<String, Object>> listConversationMemoryWorldbooks(@Param("userId") long userId);

    List<String> listOwnedUploadRelativePaths(@Param("userId") long userId);

    int deleteSupportTicketMessagesByUser(@Param("userId") long userId);

    int deleteSupportTicketsByUser(@Param("userId") long userId);

    int deleteUserMessages(@Param("userId") long userId);

    int deleteUserNoticeReads(@Param("userId") long userId);

    int deleteUserNoticeReadState(@Param("userId") long userId);

    int deleteUserInboxAdReads(@Param("userId") long userId);

    int deleteCharacterFavorites(@Param("userId") long userId);

    int deleteCharacterFavoritesForOwnedCharacters(@Param("userId") long userId);

    int deleteCharacterVotes(@Param("userId") long userId);

    int deleteCharacterVotesForOwnedCharacters(@Param("userId") long userId);

    int deleteWalletLedger(@Param("userId") long userId);

    int deletePaymentOrders(@Param("userId") long userId);

    int deleteChatGenerationContextsByUser(@Param("userId") long userId);

    int deleteChatModelPreferencesByUser(@Param("userId") long userId);

    int deleteH5UserAiChatModelsByUser(@Param("userId") long userId);

    int deleteGenerationAttemptsByUser(@Param("userId") long userId);

    int deleteGenerationStatEventsByUser(@Param("userId") long userId);

    int deleteConversationBindingsByUser(@Param("userId") long userId);

    int deleteConversationArchivesByUser(@Param("userId") long userId);

    int deleteConversationMemoryEntriesByUser(@Param("userId") long userId);

    int deleteConversationMemoryRefreshMetricsByUser(@Param("userId") long userId);

    int deleteConversationMemoriesByUser(@Param("userId") long userId);

    int deleteConversationIdempotencyByUser(@Param("userId") long userId);

    int deleteMessagesByUser(@Param("userId") long userId);

    int deleteGenerationTasksByUser(@Param("userId") long userId);

    int deleteConversationBranchesByUser(@Param("userId") long userId);

    int deleteConversationsByUser(@Param("userId") long userId);

    int deletePasswordResetTokensByUser(@Param("userId") long userId);

    int deleteUserSessions(@Param("userId") long userId);

    int anonymizeH5SecurityEventsByUser(@Param("userId") long userId);

    int deleteVisitorDevicesByUser(@Param("userId") long userId);

    int deleteClientUidBindings(@Param("userId") long userId);

    int deleteUserIdentities(@Param("userId") long userId);

    int deleteH5UserAiProvider(@Param("userId") long userId);

    int deleteUserTtsVoiceBindings(@Param("userId") long userId);

    int deleteUserTtsVoices(@Param("userId") long userId);

    int deleteUserTtsVoiceInstances(@Param("userId") long userId);

    int deleteCheckinClaimsByUser(@Param("userId") long userId);

    int deleteEntitlementAuditLogsByUser(@Param("userId") long userId);

    int deleteH5Profile(@Param("userId") long userId);

    int deleteH5ProfileExt(@Param("userId") long userId);

    int deleteOwnedUploadAssetsByUser(@Param("userId") long userId);

    int deleteIllustrationWorksBySubmitter(@Param("userId") long userId);

    int deleteCharacterReviewLogsByOwner(@Param("userId") long userId);

    int deleteLorebookEntriesForOwnedCharacters(@Param("userId") long userId);

    int deleteChatPreferencesRelatedToUser(@Param("userId") long userId);

    int deletePrivateChatPresetsByUser(@Param("userId") long userId);

    int deleteCharacterPromotionsBySourceUser(@Param("userId") long userId);

    int deleteOwnedCharacters(@Param("userId") long userId);

    int deleteAppUser(@Param("userId") long userId);
}
