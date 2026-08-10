package com.example.sillyspringboot.ops.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppGenerationStatEventMapper {

    int upsertTaskStatus(@Param("taskId") long taskId, @Param("status") String status);
}
