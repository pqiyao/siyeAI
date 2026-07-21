package com.example.sillyspringboot.character.mapper;

import com.example.sillyspringboot.character.entity.AppCharacterReviewLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppCharacterReviewLogMapper {

    void insert(AppCharacterReviewLog row);

    long countList(
            @Param("reviewStatus") String reviewStatus,
            @Param("keyword") String keyword
    );

    List<Map<String, Object>> listPage(
            @Param("reviewStatus") String reviewStatus,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAll();

    long countByReviewStatus(@Param("reviewStatus") String reviewStatus);

    long countByEventType(@Param("eventType") String eventType);

    long countByScreeningLevel(@Param("screeningLevel") String screeningLevel);

    List<Map<String, Object>> listRecentScreeningFlags(@Param("limit") int limit);

    int deleteByIds(@Param("ids") List<Long> ids);
}
