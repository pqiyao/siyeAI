package com.example.sillyspringboot.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppWalletLedgerMapper {
    void insert(
            @Param("userId") long userId,
            @Param("bizType") String bizType,
            @Param("orderNo") String orderNo,
            @Param("deltaScore") int deltaScore,
            @Param("deltaGoldCoin") int deltaGoldCoin,
            @Param("note") String note
    );
}
