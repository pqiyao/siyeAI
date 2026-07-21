package com.example.sillyspringboot.billing.mapper;

import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppPaymentChannelConfigMapper {

    List<AppPaymentChannelConfig> listAll();

    AppPaymentChannelConfig findByChannelCode(@Param("channelCode") String channelCode);

    void insert(AppPaymentChannelConfig row);

    void updateById(AppPaymentChannelConfig row);
}
