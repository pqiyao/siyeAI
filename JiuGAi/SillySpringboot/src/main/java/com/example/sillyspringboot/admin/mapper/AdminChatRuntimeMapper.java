package com.example.sillyspringboot.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminChatRuntimeMapper {
    long count(@Param("status") String status, @Param("keyword") String keyword);
    List<Map<String, Object>> list(@Param("status") String status, @Param("keyword") String keyword,
                                   @Param("offset") int offset, @Param("limit") int limit);
    Map<String, Object> summary();
    List<Map<String, Object>> findTaskStatuses(@Param("ids") List<Long> ids);
    int deleteAttemptsByTaskIds(@Param("ids") List<Long> ids);
    int deleteStatEventsByTaskIds(@Param("ids") List<Long> ids);
    int hardDeleteTasks(@Param("ids") List<Long> ids);
}
