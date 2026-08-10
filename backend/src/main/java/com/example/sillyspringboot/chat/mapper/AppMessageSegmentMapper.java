package com.example.sillyspringboot.chat.mapper;

import com.example.sillyspringboot.chat.entity.AppMessageSegment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppMessageSegmentMapper {
    int insert(AppMessageSegment segment);

    int deleteByMessageId(@Param("messageId") long messageId);

    List<AppMessageSegment> listByMessageId(@Param("messageId") long messageId);

    List<AppMessageSegment> listByMessageIds(@Param("messageIds") List<Long> messageIds);
}
