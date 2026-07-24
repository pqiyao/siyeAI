package com.example.sillyspringboot.compat.h5.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface H5UploadAssetMapper {

    int insertOwnedAsset(
            @Param("assetUrl") String assetUrl,
            @Param("relativePath") String relativePath,
            @Param("ownerUserId") long ownerUserId
    );
}
