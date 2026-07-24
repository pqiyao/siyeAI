package com.example.sillyspringboot.ops.generation.mapper;

import com.example.sillyspringboot.ops.generation.entity.GenerationModelPricing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GenerationModelPricingMapper {

    List<GenerationModelPricing> listAll();

    List<GenerationModelPricing> listEffective(
            @Param("providerKey") String providerKey,
            @Param("effectiveAt") LocalDateTime effectiveAt
    );

    GenerationModelPricing findById(@Param("id") long id);

    GenerationModelPricing findByIdentity(GenerationModelPricing row);

    void insert(GenerationModelPricing row);

    int updateById(GenerationModelPricing row);

    int deleteById(@Param("id") long id);
}
