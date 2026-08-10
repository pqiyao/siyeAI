package com.example.sillyspringboot.auth.mapper;

import com.example.sillyspringboot.auth.entity.AppPasswordResetToken;
import com.example.sillyspringboot.auth.entity.AppPasswordResetThrottle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AppPasswordResetTokenMapper {

    AppPasswordResetToken findByRequestIdForUpdate(@Param("requestId") String requestId);

    int insertThrottleIfAbsent(@Param("accountKey") String accountKey, @Param("requestId") String requestId);

    AppPasswordResetThrottle findThrottleForUpdate(@Param("accountKey") String accountKey);

    int updateThrottle(@Param("accountKey") String accountKey, @Param("requestId") String requestId);

    int deleteThrottleBefore(@Param("cutoff") LocalDateTime cutoff);

    int insert(AppPasswordResetToken row);

    int invalidateActiveByUserId(@Param("userId") long userId);

    int incrementAttemptCount(@Param("id") long id, @Param("maxAttempts") int maxAttempts);

    int markConsumed(@Param("id") long id);

    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
