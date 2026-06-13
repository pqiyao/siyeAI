package com.example.sillyspringboot.billing.mapper;

import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppPaymentOrderMapper {
    void insert(AppPaymentOrder row);

    AppPaymentOrder findByOrderNo(@Param("orderNo") String orderNo);

    AppPaymentOrder findByOrderNoAndUserId(@Param("orderNo") String orderNo, @Param("userId") long userId);

    List<AppPaymentOrder> listByUserId(@Param("userId") long userId, @Param("limit") int limit);

    void markPaid(@Param("id") long id);

    long countAdminList(@Param("keyword") String keyword, @Param("status") String status);

    List<AppPaymentOrder> listAdminPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
