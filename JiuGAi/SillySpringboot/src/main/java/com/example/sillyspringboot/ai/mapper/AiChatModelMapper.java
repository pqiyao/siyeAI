package com.example.sillyspringboot.ai.mapper;

import com.example.sillyspringboot.ai.entity.AiChatModelSettings;
import com.example.sillyspringboot.ai.entity.AiChatOffering;
import com.example.sillyspringboot.ai.entity.AiChatOfferingPrice;
import com.example.sillyspringboot.ai.entity.ChatGenerationContext;
import com.example.sillyspringboot.ai.entity.ChatModelPreference;
import com.example.sillyspringboot.ai.entity.UserAiChatModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface AiChatModelMapper {
    AiChatModelSettings findSettings();
    int updateSettings(AiChatModelSettings row);

    List<AiChatOffering> listOfferings();
    List<AiChatOffering> listPublishedOfferings();
    AiChatOffering findOfferingById(@Param("id") long id);
    AiChatOffering findOfferingByCode(@Param("offeringCode") String offeringCode);
    AiChatOffering findDefaultOffering();
    int insertOffering(AiChatOffering row);
    int updateOffering(AiChatOffering row);
    int clearDefaultOffering(@Param("excludeId") Long excludeId);
    int deleteOffering(@Param("id") long id);

    List<AiChatOfferingPrice> listPrices(@Param("offeringId") long offeringId);
    int deletePrices(@Param("offeringId") long offeringId);
    int insertPrice(AiChatOfferingPrice row);

    List<UserAiChatModel> listUserModels(@Param("userId") long userId);
    UserAiChatModel findUserModel(@Param("userId") long userId, @Param("id") long id);
    UserAiChatModel findUserModelByName(@Param("userId") long userId, @Param("modelName") String modelName);
    UserAiChatModel findDefaultUserModel(@Param("userId") long userId);
    int insertUserModel(UserAiChatModel row);
    int updateUserModel(UserAiChatModel row);
    int clearDefaultUserModel(@Param("userId") long userId, @Param("excludeId") Long excludeId);
    int deleteUserModelsNotIn(@Param("userId") long userId, @Param("ids") List<Long> ids);
    int deleteAllUserModels(@Param("userId") long userId);

    ChatModelPreference findPreference(
            @Param("userId") long userId,
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId
    );
    int insertPreference(ChatModelPreference row);
    int updatePreference(ChatModelPreference row);

    ChatGenerationContext findGenerationContext(
            @Param("userId") long userId,
            @Param("generationRequestId") String generationRequestId,
            @Param("actionType") String actionType
    );
    int insertGenerationContext(ChatGenerationContext row);
    int updateGenerationChargeStatus(
            @Param("id") long id,
            @Param("chargeStatus") String chargeStatus,
            @Param("consumeBizRef") String consumeBizRef
    );
    int claimGenerationRefund(@Param("id") long id);
    int claimStaleGenerationRefund(
            @Param("id") long id,
            @Param("cutoff") LocalDateTime cutoff
    );
    int completeGenerationReservation(@Param("id") long id);
    int completeGenerationIfContentEmitted(@Param("id") long id);
    int markFirstContentEmitted(@Param("id") long id);
    List<ChatGenerationContext> listStaleChargeContexts(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );
    int deleteTerminalGenerationContexts(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );
}
