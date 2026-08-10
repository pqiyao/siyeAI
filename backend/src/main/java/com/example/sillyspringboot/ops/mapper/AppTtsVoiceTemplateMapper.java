package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppTtsVoiceTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppTtsVoiceTemplateMapper {

    long countAdminList(@Param("keyword") String keyword, @Param("enabled") Boolean enabled);

    List<AppTtsVoiceTemplate> listAdminPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<AppTtsVoiceTemplate> listEnabled();

    AppTtsVoiceTemplate findById(@Param("id") long id);

    AppTtsVoiceTemplate findByCode(@Param("templateCode") String templateCode);

    void insert(AppTtsVoiceTemplate row);

    void updateById(AppTtsVoiceTemplate row);

    void deleteById(@Param("id") long id);
}
