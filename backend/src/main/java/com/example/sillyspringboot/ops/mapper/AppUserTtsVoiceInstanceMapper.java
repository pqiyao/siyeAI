package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppUserTtsVoiceInstanceMapper {

    AppUserTtsVoiceInstance findByUserIdAndTemplateCode(
            @Param("userId") long userId,
            @Param("templateCode") String templateCode
    );

    List<AppUserTtsVoiceInstance> listByUserId(@Param("userId") long userId);

    void insert(AppUserTtsVoiceInstance row);

    void updateById(AppUserTtsVoiceInstance row);

    void deleteByTemplateCode(@Param("templateCode") String templateCode);
}
