package com.example.sillyspringboot.ops.mapper;

import com.example.sillyspringboot.ops.entity.AppAndroidRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppAndroidReleaseMapper {
    AppAndroidRelease findEffective(
            @Param("appId") String appId,
            @Param("packageName") String packageName,
            @Param("channelCode") String channelCode
    );

    long countAdminList(
            @Param("keyword") String keyword,
            @Param("status") String status
    );

    List<AppAndroidRelease> listAdminPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    AppAndroidRelease findById(@Param("id") long id);
    int insert(AppAndroidRelease row);
    int updateById(AppAndroidRelease row);
    int updateStatus(@Param("id") long id, @Param("status") String status);
    int bumpPolicyRevision(@Param("id") long id);
    int deleteDraft(@Param("id") long id);
}
