package com.example.sillyspringboot.support.mapper;

import com.example.sillyspringboot.support.entity.AppSupportTicketMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppSupportTicketMessageMapper {
    int insert(AppSupportTicketMessage row);

    List<AppSupportTicketMessage> listByTicketId(@Param("ticketId") long ticketId);
}
