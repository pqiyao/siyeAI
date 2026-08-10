package com.example.sillyspringboot.billing.mapper;

import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppStoreProductMapper {
    AppStoreProduct findById(@Param("id") long id);

    AppStoreProduct findByCode(@Param("code") String code);

    List<AppStoreProduct> listEnabled(@Param("productType") String productType);

    long countAdminList(@Param("keyword") String keyword, @Param("productType") String productType);

    List<AppStoreProduct> listAdminPage(
            @Param("keyword") String keyword,
            @Param("productType") String productType,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    void insert(AppStoreProduct row);

    void updateById(AppStoreProduct row);
}
