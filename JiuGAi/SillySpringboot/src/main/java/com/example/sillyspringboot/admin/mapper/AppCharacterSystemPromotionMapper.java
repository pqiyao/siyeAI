package com.example.sillyspringboot.admin.mapper;

import com.example.sillyspringboot.admin.entity.AppCharacterSystemPromotion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppCharacterSystemPromotionMapper {
    AppCharacterSystemPromotion findBySourceCharacterId(@Param("sourceCharacterId") long sourceCharacterId);

    int insert(AppCharacterSystemPromotion row);
}
