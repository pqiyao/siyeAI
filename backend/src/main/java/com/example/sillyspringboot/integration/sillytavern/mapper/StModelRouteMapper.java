package com.example.sillyspringboot.integration.sillytavern.mapper;

import com.example.sillyspringboot.integration.sillytavern.entity.StModelRoute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StModelRouteMapper {

    List<StModelRoute> listAll();

    StModelRoute findById(@Param("id") Long id);

    StModelRoute findBySceneKey(@Param("sceneKey") String sceneKey);

    int insert(StModelRoute row);

    int updateById(StModelRoute row);

    int deleteById(@Param("id") Long id);

    int countReferencingProvider(@Param("providerKey") String providerKey);
}
