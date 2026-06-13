package com.example.sillyspringboot.upload.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.upload.entity.AppUploadedAsset;
import com.example.sillyspringboot.upload.mapper.AppUploadedAssetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUploadedAssetService {

    private final AppUploadedAssetMapper mapper;

    public AppUploadedAssetService(AppUploadedAssetMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void recordOwnedAsset(long ownerUserId, String mediaKey, String mediaType, String sourceModule) {
        if (ownerUserId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        String safeMediaKey = normalizeMediaKey(mediaKey);
        AppUploadedAsset asset = new AppUploadedAsset();
        asset.setOwnerUserId(ownerUserId);
        asset.setMediaKey(safeMediaKey);
        asset.setStorageProvider("local");
        asset.setMediaType(normalizeMediaType(mediaType));
        asset.setSourceModule(normalizeSourceModule(sourceModule));
        mapper.upsert(asset);
    }

    @Transactional(readOnly = true)
    public void requireOwnedImage(long ownerUserId, String mediaKey) {
        String safeMediaKey = normalizeMediaKey(mediaKey);
        if (mapper.countOwnedAsset(ownerUserId, safeMediaKey, "image") > 0) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "图片不属于当前用户");
    }

    private static String normalizeMediaKey(String mediaKey) {
        String safe = mediaKey == null ? "" : mediaKey.trim();
        if (safe.isBlank() || safe.length() > 512 || safe.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "图片地址不正确");
        }
        if (!safe.startsWith("/uploads/h5/")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "图片地址不正确");
        }
        return safe;
    }

    private static String normalizeMediaType(String mediaType) {
        String safe = mediaType == null ? "" : mediaType.trim().toLowerCase();
        return safe.isBlank() ? "file" : safe;
    }

    private static String normalizeSourceModule(String sourceModule) {
        String safe = sourceModule == null ? "" : sourceModule.trim().toLowerCase();
        return safe.isBlank() ? "h5_common" : safe;
    }
}
