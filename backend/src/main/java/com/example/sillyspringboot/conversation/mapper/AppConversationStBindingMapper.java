package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppConversationStBindingMapper {

    AppConversationStBinding findByConversationId(@Param("conversationId") long conversationId);

    void insert(AppConversationStBinding binding);

    void updateWorldNamesJson(@Param("conversationId") long conversationId, @Param("worldNamesJson") String worldNamesJson);

    void updateStDisplayNameOverride(@Param("conversationId") long conversationId, @Param("stDisplayNameOverride") String stDisplayNameOverride);

    void deleteByConversationId(@Param("conversationId") long conversationId);
}
