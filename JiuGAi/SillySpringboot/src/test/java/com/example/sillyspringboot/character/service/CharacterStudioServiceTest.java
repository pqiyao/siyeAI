package com.example.sillyspringboot.character.service;

import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.compat.h5.web.dto.H5MyCharacterSaveRequest;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceBinding;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceBindingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterStudioServiceTest {

    private final CharacterStudioMapper studioMapper = mock(CharacterStudioMapper.class);
    private final AppLorebookEntryMapper lorebookMapper = mock(AppLorebookEntryMapper.class);
    private final AppUserTtsVoiceBindingMapper voiceBindingMapper = mock(AppUserTtsVoiceBindingMapper.class);
    private final CharacterStudioService service =
            new CharacterStudioService(studioMapper, lorebookMapper, voiceBindingMapper);

    @Test
    void editingMembersKeepsExistingIdsAndCleansRemovedMemberBinding() {
        AppCharacterMember retained = member(11L, "原主角");
        AppCharacterMember removed = member(12L, "待删除成员");
        when(studioMapper.findCardType(99L)).thenReturn("ENSEMBLE");
        when(studioMapper.listMembers(99L)).thenReturn(List.of(retained, removed));
        when(studioMapper.updateMember(any())).thenReturn(1);
        when(studioMapper.insertMember(any())).thenAnswer(invocation -> {
            AppCharacterMember row = invocation.getArgument(0);
            row.setId(31L);
            return 1;
        });

        H5MyCharacterSaveRequest request = request("ENSEMBLE", List.of(
                input(11L, "primary", "更新后的主角"),
                input(null, "new_member", "新成员")
        ));

        service.replaceStudio(7L, 99L, request);

        ArgumentCaptor<AppCharacterMember> updated = ArgumentCaptor.forClass(AppCharacterMember.class);
        verify(studioMapper).updateMember(updated.capture());
        assertEquals(11L, updated.getValue().getId());
        assertEquals("更新后的主角", updated.getValue().getName());
        verify(voiceBindingMapper).deleteMemberScope(7L, 99L, 12L);
        verify(studioMapper).deleteMemberById(99L, 12L);
        verify(studioMapper, never()).deleteMemberById(99L, 11L);
    }

    @Test
    void switchingToEnsembleMovesCharacterVoiceToPrimaryMember() {
        AppCharacterMember primary = member(11L, "主角");
        AppCharacterMember second = member(12L, "成员二");
        when(studioMapper.findCardType(99L)).thenReturn("SINGLE");
        when(studioMapper.listMembers(99L)).thenReturn(List.of(primary), List.of(primary, second));
        when(studioMapper.updateMember(any())).thenReturn(1);
        when(studioMapper.insertMember(any())).thenAnswer(invocation -> {
            AppCharacterMember row = invocation.getArgument(0);
            row.setId(12L);
            return 1;
        });
        AppUserTtsVoiceBinding existing = new AppUserTtsVoiceBinding();
        existing.setVoiceId(55L);
        when(voiceBindingMapper.find(7L, "CHARACTER", 99L, 0L)).thenReturn(existing);
        when(voiceBindingMapper.updateVoice(any())).thenReturn(0);

        H5MyCharacterSaveRequest request = request("ENSEMBLE", List.of(
                input(11L, "primary", "主角"),
                input(null, "second", "成员二")
        ));

        service.replaceStudio(7L, 99L, request);

        ArgumentCaptor<AppUserTtsVoiceBinding> migrated =
                ArgumentCaptor.forClass(AppUserTtsVoiceBinding.class);
        verify(voiceBindingMapper).insert(migrated.capture());
        assertEquals("MEMBER", migrated.getValue().getScopeType());
        assertEquals(99L, migrated.getValue().getCharacterId());
        assertEquals(11L, migrated.getValue().getMemberId());
        assertEquals(55L, migrated.getValue().getVoiceId());
        verify(voiceBindingMapper).deleteScope(7L, "CHARACTER", 99L, 0L);
    }

    @Test
    void switchingToSingleMovesPrimaryMemberVoiceToCharacter() {
        AppCharacterMember primary = member(11L, "主角");
        AppCharacterMember second = member(12L, "成员二");
        when(studioMapper.findCardType(99L)).thenReturn("ENSEMBLE");
        when(studioMapper.listMembers(99L)).thenReturn(List.of(primary, second), List.of(primary));
        when(studioMapper.updateMember(any())).thenReturn(1);
        AppUserTtsVoiceBinding existing = new AppUserTtsVoiceBinding();
        existing.setVoiceId(55L);
        when(voiceBindingMapper.find(7L, "MEMBER", 99L, 11L)).thenReturn(existing);
        when(voiceBindingMapper.updateVoice(any())).thenReturn(0);

        H5MyCharacterSaveRequest request = request("SINGLE", List.of(
                input(11L, "primary", "主角")
        ));

        service.replaceStudio(7L, 99L, request);

        ArgumentCaptor<AppUserTtsVoiceBinding> migrated =
                ArgumentCaptor.forClass(AppUserTtsVoiceBinding.class);
        verify(voiceBindingMapper).insert(migrated.capture());
        assertEquals("CHARACTER", migrated.getValue().getScopeType());
        assertEquals(99L, migrated.getValue().getCharacterId());
        assertEquals(0L, migrated.getValue().getMemberId());
        assertEquals(55L, migrated.getValue().getVoiceId());
        verify(voiceBindingMapper).deleteMemberScopes(7L, 99L);
    }

    private static H5MyCharacterSaveRequest request(
            String cardType,
            List<H5MyCharacterSaveRequest.MemberInput> members
    ) {
        H5MyCharacterSaveRequest request = new H5MyCharacterSaveRequest();
        request.setCardType(cardType);
        request.setMembers(members);
        return request;
    }

    private static H5MyCharacterSaveRequest.MemberInput input(Long id, String clientKey, String name) {
        H5MyCharacterSaveRequest.MemberInput input = new H5MyCharacterSaveRequest.MemberInput();
        input.setId(id);
        input.setClientKey(clientKey);
        input.setName(name);
        input.setPrimaryMember("primary".equals(clientKey));
        return input;
    }

    private static AppCharacterMember member(Long id, String name) {
        AppCharacterMember member = new AppCharacterMember();
        member.setId(id);
        member.setCharacterId(99L);
        member.setName(name);
        member.setPrimaryMember(id == 11L);
        member.setEnabled(true);
        return member;
    }
}
