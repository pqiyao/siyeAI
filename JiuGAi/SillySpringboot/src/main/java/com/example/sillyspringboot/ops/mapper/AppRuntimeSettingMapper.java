package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppRuntimeSettingMapper {

    AppRuntimeSetting findByKey(@Param("settingKey") String settingKey);

    void upsert(@Param("settingKey") String settingKey, @Param("settingValue") String settingValue);
}
