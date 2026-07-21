package com.example.sillyspringboot.admin.mapper;

import com.example.sillyspringboot.admin.entity.AppAdminAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppAdminAccountMapper {

    AppAdminAccount findById(@Param("id") long id);

    AppAdminAccount findByUsername(@Param("username") String username);

    long countList(@Param("keyword") String keyword, @Param("status") String status);

    List<Map<String, Object>> listPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int insert(AppAdminAccount row);

    int updateProfile(AppAdminAccount row);

    int updateStatus(@Param("id") long id, @Param("status") String status, @Param("updatedBy") String updatedBy);

    int updatePassword(
            @Param("id") long id,
            @Param("encodedPassword") String encodedPassword,
            @Param("mustResetPassword") boolean mustResetPassword,
            @Param("updatedBy") String updatedBy
    );

    int updateLastLogin(@Param("id") long id, @Param("lastLoginIp") String lastLoginIp);

    int deleteById(@Param("id") long id);

    long countByStatus(@Param("status") String status);
}
