package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.compat.h5.mapper.H5UploadAssetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class H5UploadAssetOwnershipService {

    private final H5UploadAssetMapper mapper;

    public H5UploadAssetOwnershipService(H5UploadAssetMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerOwnedAsset(long ownerUserId, String assetUrl, String relativePath) {
        if (ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        if (assetUrl == null || assetUrl.isBlank() || relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("owned upload reference is blank");
        }
        int inserted = mapper.insertOwnedAsset(assetUrl.trim(), relativePath.trim(), ownerUserId);
        if (inserted != 1) {
            throw new IllegalStateException("owned upload metadata was not persisted");
        }
    }
}
