package com.example.sillyspringboot.billing.mapper;

import com.example.sillyspringboot.billing.entity.AppWalletLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    int insertFull(
            @Param("userId") long userId,
            @Param("bizType") String bizType,
            @Param("orderNo") String orderNo,
            @Param("bizRef") String bizRef,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("deltaScore") int deltaScore,
            @Param("deltaGoldCoin") int deltaGoldCoin,
            @Param("note") String note
    );

    AppWalletLedger findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    long countAdminList(
            @Param("keyword") String keyword,
            @Param("bizType") String bizType,
            @Param("groupType") String groupType
    );

    List<AppWalletLedger> listAdminPage(
            @Param("keyword") String keyword,
            @Param("bizType") String bizType,
            @Param("groupType") String groupType,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
