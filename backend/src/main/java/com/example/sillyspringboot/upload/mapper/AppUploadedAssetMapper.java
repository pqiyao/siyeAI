package com.example.sillyspringboot.upload.mapper;

import com.example.sillyspringboot.upload.entity.AppUploadedAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUploadedAssetMapper {

    int upsert(AppUploadedAsset asset);

    AppUploadedAsset findByMediaKey(@Param("mediaKey") String mediaKey);

    int countOwnedAsset(
            @Param("ownerUserId") long ownerUserId,
            @Param("mediaKey") String mediaKey,
            @Param("mediaType") String mediaType
    );
}
