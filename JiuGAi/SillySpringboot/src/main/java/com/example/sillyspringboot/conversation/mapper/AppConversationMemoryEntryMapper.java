package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppConversationMemoryEntryMapper {

    List<AppConversationMemoryEntry> listEnabledByConversationId(@Param("conversationId") long conversationId);

    List<AppConversationMemoryEntry> listEnabledByConversationBranchId(@Param("conversationId") long conversationId,
                                                                       @Param("branchId") long branchId);

    List<AppConversationMemoryEntry> listAllByConversationId(@Param("conversationId") long conversationId);

    List<AppConversationMemoryEntry> listAllByConversationBranchId(@Param("conversationId") long conversationId,
                                                                   @Param("branchId") long branchId);

    List<AppConversationMemoryEntry> listPanelByConversationBranchId(@Param("conversationId") long conversationId,
                                                                      @Param("branchId") long branchId);

    List<AppConversationMemoryEntry> listManualDeletedByConversationId(@Param("conversationId") long conversationId);

    List<AppConversationMemoryEntry> listManualDeletedByConversationBranchId(@Param("conversationId") long conversationId,
                                                                             @Param("branchId") long branchId);

    List<AppConversationMemoryEntry> listCapacityEntriesForUpdate(@Param("conversationId") long conversationId,
                                                                   @Param("branchId") long branchId);

    List<AppConversationMemoryEntry> listAllIncludingDeletedForUpdate(@Param("conversationId") long conversationId,
                                                                       @Param("branchId") long branchId);

    AppConversationMemoryEntry findByIdForConversationBranch(@Param("id") long id,
                                                              @Param("conversationId") long conversationId,
                                                              @Param("branchId") long branchId);

    void upsert(AppConversationMemoryEntry entry);

    void disableByKey(@Param("conversationId") long conversationId, @Param("entryKey") String entryKey);

    void disableByKeyForBranch(@Param("conversationId") long conversationId,
                               @Param("branchId") long branchId,
                               @Param("entryKey") String entryKey);

    void disableById(@Param("id") long id);

    int setManualEnabledById(@Param("id") long id,
                             @Param("conversationId") long conversationId,
                             @Param("branchId") long branchId,
                             @Param("enabled") boolean enabled,
                             @Param("manualDisabled") boolean manualDisabled);

    int retireAutomaticById(@Param("id") long id,
                            @Param("conversationId") long conversationId,
                            @Param("branchId") long branchId,
                            @Param("retiredReason") String retiredReason);

    int setConstantInjectionById(@Param("id") long id,
                                 @Param("conversationId") long conversationId,
                                 @Param("branchId") long branchId,
                                 @Param("constantInjection") boolean constantInjection);

    int deleteAutomaticRetiredByIds(@Param("conversationId") long conversationId,
                                    @Param("branchId") long branchId,
                                    @Param("ids") List<Long> ids);

    int softDeleteManualById(@Param("id") long id,
                             @Param("conversationId") long conversationId,
                             @Param("branchId") long branchId);

    int softDeleteGeneratedByConversationBranchId(@Param("conversationId") long conversationId,
                                                   @Param("branchId") long branchId);

    void softDeleteByConversationId(@Param("conversationId") long conversationId);

    int countAllByConversationId(@Param("conversationId") long conversationId);

    int countAllByConversationBranchId(@Param("conversationId") long conversationId,
                                       @Param("branchId") long branchId);

    int countEnabledByConversationId(@Param("conversationId") long conversationId);

    int countEnabledByConversationBranchId(@Param("conversationId") long conversationId,
                                           @Param("branchId") long branchId);
}
