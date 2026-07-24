package com.example.sillyspringboot.ops.checkin.mapper;

import com.example.sillyspringboot.ops.checkin.entity.AppCheckinActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppCheckinActivityMapper {

    AppCheckinActivity findByCode(@Param("code") String code);

    AppCheckinActivity findById(@Param("id") long id);

    int updateFull(AppCheckinActivity row);

    int insertFull(AppCheckinActivity row);
}
