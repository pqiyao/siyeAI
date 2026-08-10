package com.example.sillyspringboot.ops.generation.mapper;

import com.example.sillyspringboot.ops.generation.model.GenerationAttemptContext;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationAttemptMapper {

    GenerationAttemptContext findContext(
            @Param("conversationId") long conversationId,
            @Param("clientMessageId") String clientMessageId
    );

    void insert(GenerationAttemptRow row);
}
