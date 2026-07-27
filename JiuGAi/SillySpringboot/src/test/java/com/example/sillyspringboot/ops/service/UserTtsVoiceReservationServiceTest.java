package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoice;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTtsVoiceReservationServiceTest {

    @Test
    void locksUserBeforeCountingAndReservingSlot() {
        AppUserTtsVoiceMapper voiceMapper = mock(AppUserTtsVoiceMapper.class);
        AppH5UserProfileExtMapper profileMapper = mock(AppH5UserProfileExtMapper.class);
        AppUserTtsVoice row = new AppUserTtsVoice();
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(new AppH5UserProfileExt());
        when(voiceMapper.countOccupyingByUserId(7L)).thenReturn(2);

        new UserTtsVoiceReservationService(voiceMapper, profileMapper).reserve(7L, 3, row);

        var order = inOrder(profileMapper, voiceMapper);
        order.verify(profileMapper).insertDefaultIfAbsent(7L);
        order.verify(profileMapper).findByUserIdForUpdate(7L);
        order.verify(voiceMapper).countOccupyingByUserId(7L);
        order.verify(voiceMapper).insert(row);
    }

    @Test
    void refusesReservationAtLimit() {
        AppUserTtsVoiceMapper voiceMapper = mock(AppUserTtsVoiceMapper.class);
        AppH5UserProfileExtMapper profileMapper = mock(AppH5UserProfileExtMapper.class);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(new AppH5UserProfileExt());
        when(voiceMapper.countOccupyingByUserId(7L)).thenReturn(3);

        assertThatThrownBy(() -> new UserTtsVoiceReservationService(voiceMapper, profileMapper)
                .reserve(7L, 3, new AppUserTtsVoice()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已达到上限");
        verify(voiceMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void importedVoiceDeduplicatesAfterTakingUserLock() {
        AppUserTtsVoiceMapper voiceMapper = mock(AppUserTtsVoiceMapper.class);
        AppH5UserProfileExtMapper profileMapper = mock(AppH5UserProfileExtMapper.class);
        AppUserTtsVoice incoming = new AppUserTtsVoice();
        incoming.setVoiceUri("speech:owned");
        AppUserTtsVoice existing = new AppUserTtsVoice();
        existing.setId(91L);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(new AppH5UserProfileExt());
        when(voiceMapper.findActiveByUserIdAndVoiceUri(7L, "speech:owned")).thenReturn(existing);

        AppUserTtsVoice result = new UserTtsVoiceReservationService(voiceMapper, profileMapper)
                .reserveImported(7L, 3, incoming);

        assertThat(result).isSameAs(existing);
        var order = inOrder(profileMapper, voiceMapper);
        order.verify(profileMapper).insertDefaultIfAbsent(7L);
        order.verify(profileMapper).findByUserIdForUpdate(7L);
        order.verify(voiceMapper).findActiveByUserIdAndVoiceUri(7L, "speech:owned");
        verify(voiceMapper, never()).countOccupyingByUserId(7L);
        verify(voiceMapper, never()).insert(incoming);
    }
}
