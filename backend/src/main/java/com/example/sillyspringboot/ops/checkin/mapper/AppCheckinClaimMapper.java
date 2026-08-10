package com.example.sillyspringboot.ops.checkin.mapper;

import com.example.sillyspringboot.ops.checkin.entity.AppCheckinClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AppCheckinClaimMapper {

    int insert(AppCheckinClaim row);

    AppCheckinClaim findByUserActivityDate(
            @Param("userId") long userId,
            @Param("activityId") long activityId,
            @Param("bizDate") LocalDate bizDate
    );

    List<AppCheckinClaim> listByUserActivityDateRange(
            @Param("userId") long userId,
            @Param("activityId") long activityId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    long countAdmin(
            @Param("keyword") String keyword,
            @Param("bizDate") LocalDate bizDate,
            @Param("activityId") Long activityId
    );

    List<AppCheckinClaim> listAdminPage(
            @Param("keyword") String keyword,
            @Param("bizDate") LocalDate bizDate,
            @Param("activityId") Long activityId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
