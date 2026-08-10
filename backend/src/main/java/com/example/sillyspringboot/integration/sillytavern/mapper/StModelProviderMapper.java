package com.example.sillyspringboot.integration.sillytavern.mapper;

import com.example.sillyspringboot.integration.sillytavern.entity.StModelProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StModelProviderMapper {

    List<StModelProvider> listAll();

    List<StModelProvider> listEnabled();

    StModelProvider findById(@Param("id") Long id);

    StModelProvider findByProviderKey(@Param("providerKey") String providerKey);

    int insert(StModelProvider row);

    int updateById(StModelProvider row);

    int deleteById(@Param("id") Long id);

    int markSuccess(@Param("providerKey") String providerKey);

    int markFailure(
            @Param("providerKey") String providerKey,
            @Param("lastError") String lastError,
            @Param("failureThreshold") int failureThreshold,
            @Param("cooldownSeconds") int cooldownSeconds
    );
}
