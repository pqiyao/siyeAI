package com.example.sillyspringboot.ops.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppVoiceStatEventMapper {

    int insertEvent(
            @Param("userId") Long userId,
            @Param("scope") String scope,
            @Param("providerSource") String providerSource,
            @Param("modelName") String modelName,
            @Param("voiceName") String voiceName,
            @Param("templateCode") String templateCode,
            @Param("status") String status,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("textChars") int textChars,
            @Param("audioBytes") int audioBytes,
            @Param("latencyMs") int latencyMs,
            @Param("cacheHit") boolean cacheHit,
            @Param("duplicateRequest") boolean duplicateRequest,
            @Param("errorCode") String errorCode
    );

    long countSuccessfulFingerprint(@Param("scope") String scope, @Param("requestFingerprint") String requestFingerprint);
}
