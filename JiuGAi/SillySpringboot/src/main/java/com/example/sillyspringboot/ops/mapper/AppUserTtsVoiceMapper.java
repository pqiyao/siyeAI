package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppUserTtsVoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppUserTtsVoiceMapper {

    AppUserTtsVoice findById(@Param("id") long id);

    AppUserTtsVoice findOwnedById(@Param("userId") long userId, @Param("id") long id);

    AppUserTtsVoice findByUserIdAndRequestId(
            @Param("userId") long userId,
            @Param("requestId") String requestId
    );

    List<AppUserTtsVoice> listByUserId(@Param("userId") long userId);

    List<AppUserTtsVoice> listAdmin(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAdmin(@Param("keyword") String keyword, @Param("status") String status);

    int countOccupyingByUserId(@Param("userId") long userId);

    void insert(AppUserTtsVoice row);

    void updateProvisionResult(AppUserTtsVoice row);

    void updateDisplayName(
            @Param("userId") long userId,
            @Param("id") long id,
            @Param("displayName") String displayName
    );

    void updateDisabled(@Param("id") long id, @Param("disabled") boolean disabled);

    void softDelete(@Param("userId") long userId, @Param("id") long id);
}
