package com.example.sillyspringboot.integration.sillytavern.mapper;

import com.example.sillyspringboot.integration.sillytavern.entity.OpenRouterGenerationSettings;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpenRouterGenerationSettingsMapper {

    OpenRouterGenerationSettings findSingleton();

    int insert(OpenRouterGenerationSettings row);

    int updateById(OpenRouterGenerationSettings row);
}
