package com.example.sillyspringboot.admin.mapper;

import com.example.sillyspringboot.admin.entity.AppAdminRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppAdminRoleMapper {

    AppAdminRole findById(@Param("id") long id);

    AppAdminRole findByRoleKey(@Param("roleKey") String roleKey);

    List<AppAdminRole> listAll();

    List<AppAdminRole> listEnabled();

    long countList(@Param("keyword") String keyword, @Param("enabled") Boolean enabled);

    List<AppAdminRole> listPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int insert(AppAdminRole row);

    int update(AppAdminRole row);

    int updateStatus(@Param("id") long id, @Param("enabled") boolean enabled, @Param("updatedBy") String updatedBy);

    int deleteById(@Param("id") long id);
}
