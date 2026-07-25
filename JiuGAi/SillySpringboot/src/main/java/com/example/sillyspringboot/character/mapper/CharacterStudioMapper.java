package com.example.sillyspringboot.character.mapper;

import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.entity.AppCharacterOpening;
import com.example.sillyspringboot.character.entity.AppCharacterOpeningSegment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CharacterStudioMapper {
    String findCardType(@Param("characterId") long characterId);

    List<AppCharacterMember> listMembers(@Param("characterId") long characterId);

    List<AppCharacterOpening> listOpenings(@Param("characterId") long characterId);

    List<AppCharacterOpeningSegment> listSegmentsByCharacter(@Param("characterId") long characterId);

    int insertMember(AppCharacterMember member);

    int updateMember(AppCharacterMember member);

    int insertOpening(AppCharacterOpening opening);

    int insertOpeningSegment(AppCharacterOpeningSegment segment);

    int deleteMembersByCharacterId(@Param("characterId") long characterId);

    int deleteMemberById(@Param("characterId") long characterId, @Param("memberId") long memberId);

    int deleteOpeningSegmentsByCharacterId(@Param("characterId") long characterId);

    int deleteOpeningsByCharacterId(@Param("characterId") long characterId);

    int updateCardType(@Param("characterId") long characterId, @Param("cardType") String cardType);

    int clearLorebookMemberScopes(@Param("characterId") long characterId);
}
