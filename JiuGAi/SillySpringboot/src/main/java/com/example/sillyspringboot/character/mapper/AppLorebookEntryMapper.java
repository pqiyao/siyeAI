package com.example.sillyspringboot.character.mapper;

import com.example.sillyspringboot.character.entity.AppLorebookEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppLorebookEntryMapper {

    long countByCharacterId(@Param("characterId") long characterId);

    List<AppLorebookEntry> listPageByCharacterId(@Param("characterId") long characterId, @Param("offset") int offset, @Param("limit") int limit);

    List<AppLorebookEntry> listEnabledByCharacterId(@Param("characterId") long characterId);

    List<Map<String, Object>> summarizeByCharacterIds(@Param("characterIds") List<Long> characterIds);

    AppLorebookEntry findById(@Param("id") long id);

    int insert(AppLorebookEntry row);

    int updateById(AppLorebookEntry row);

    int deleteById(@Param("id") long id);

    int deleteByCharacterId(@Param("characterId") long characterId);

    int deleteImportedByCharacterId(@Param("characterId") long characterId);

    int batchEnabled(@Param("ids") List<Long> ids, @Param("enabled") boolean enabled);
}
