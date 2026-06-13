package com.example.sillyspringboot.admin.mapper;

import com.example.sillyspringboot.admin.entity.AppAdminRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppAdminAccountRoleMapper {

    List<Long> listRoleIdsByAccountId(@Param("accountId") long accountId);

    List<AppAdminRole> listRolesByAccountId(@Param("accountId") long accountId);

    List<Map<String, Object>> listAccountRoleRows(@Param("accountId") long accountId);

    int insert(@Param("accountId") long accountId, @Param("roleId") long roleId);

    int deleteByAccountId(@Param("accountId") long accountId);

    long countAccountsByRoleId(@Param("roleId") long roleId);

    long countActiveAccountsByRoleKey(@Param("roleKey") String roleKey);
}
