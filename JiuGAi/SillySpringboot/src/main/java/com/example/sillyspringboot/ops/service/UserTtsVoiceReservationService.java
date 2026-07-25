package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoice;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserTtsVoiceReservationService {

    private final AppUserTtsVoiceMapper voiceMapper;
    private final AppH5UserProfileExtMapper profileMapper;

    public UserTtsVoiceReservationService(
            AppUserTtsVoiceMapper voiceMapper,
            AppH5UserProfileExtMapper profileMapper
    ) {
        this.voiceMapper = voiceMapper;
        this.profileMapper = profileMapper;
    }

    @Transactional
    public void reserve(long userId, int limit, AppUserTtsVoice row) {
        profileMapper.insertDefaultIfAbsent(userId);
        if (profileMapper.findByUserIdForUpdate(userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在或无权创建音色");
        }
        int used = Math.max(0, voiceMapper.countOccupyingByUserId(userId));
        if (limit <= 0 || used >= limit) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前权益最多可创建 " + limit + " 个音色，已达到上限");
        }
        voiceMapper.insert(row);
    }
}
