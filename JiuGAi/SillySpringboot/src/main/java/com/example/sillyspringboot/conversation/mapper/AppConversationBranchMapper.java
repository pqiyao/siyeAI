package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppConversationBranchMapper {

    void insert(AppConversationBranch branch);

    AppConversationBranch findById(@Param("branchId") long branchId);

    AppConversationBranch findByIdForConversation(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId
    );

    AppConversationBranch findByIdForConversationForUpdate(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId
    );

    AppConversationBranch findDefaultByConversationId(@Param("conversationId") long conversationId);

    AppConversationBranch findByConversationIdAndOpeningVariantIndex(
            @Param("conversationId") long conversationId,
            @Param("openingVariantIndex") int openingVariantIndex
    );

    List<AppConversationBranch> listByConversationId(@Param("conversationId") long conversationId);

    List<Long> listAllIdsByConversationId(@Param("conversationId") long conversationId);

    int countByConversationId(@Param("conversationId") long conversationId);

    void setOpeningVariantIndex(
            @Param("branchId") long branchId,
            @Param("openingVariantIndex") int openingVariantIndex
    );


    void touch(@Param("branchId") long branchId);

    int updateTitle(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("title") String title
    );

    int reparentChildren(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId,
            @Param("parentBranchId") Long parentBranchId
    );

    int softDelete(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId
    );

    int incrementMemorySourceRevision(
            @Param("conversationId") long conversationId,
            @Param("branchId") long branchId
    );

    int incrementMemorySourceRevisionForConversation(@Param("conversationId") long conversationId);
}
