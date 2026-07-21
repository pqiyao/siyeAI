package com.example.sillyspringboot.support.mapper;

import com.example.sillyspringboot.support.entity.AppSupportTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppSupportTicketMapper {
    int insert(AppSupportTicket row);

    AppSupportTicket findByTicketNo(@Param("ticketNo") String ticketNo);

    AppSupportTicket findByTicketNoAndUserId(@Param("ticketNo") String ticketNo, @Param("userId") long userId);

    List<AppSupportTicket> listByUserId(
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("limit") int limit
    );

    int updateThreadState(AppSupportTicket row);

    long countAdminList(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("ticketType") String ticketType,
            @Param("priority") String priority
    );

    List<AppSupportTicket> listAdminPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("ticketType") String ticketType,
            @Param("priority") String priority,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
