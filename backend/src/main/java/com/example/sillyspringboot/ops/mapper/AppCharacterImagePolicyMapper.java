package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppCharacterImagePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppCharacterImagePolicyMapper {

    AppCharacterImagePolicy findByCharacterId(@Param("characterId") long characterId);

    void upsert(AppCharacterImagePolicy row);

    void deleteByCharacterId(@Param("characterId") long characterId);

    long countAdminCharacters(@Param("keyword") String keyword);

    List<Map<String, Object>> listAdminCharacters(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
