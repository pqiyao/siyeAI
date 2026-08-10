package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.compat.h5.entity.AppUserMessage;
import com.example.sillyspringboot.compat.h5.mapper.AppUserMessageMapper;
import org.springframework.stereotype.Service;

@Service
public class AppUserMessageService {

    private final AppUserMessageMapper userMessageMapper;

    public AppUserMessageService(AppUserMessageMapper userMessageMapper) {
        this.userMessageMapper = userMessageMapper;
    }

    public void sendCharacterRejectedMessage(AppCharacter character, String reason) {
        if (character == null || character.getOwnerUserId() == null) {
            return;
        }
        String characterName = character.getName() == null || character.getName().isBlank()
                ? "未命名角色卡"
                : character.getName().trim();
        String safeReason = reason == null ? "" : reason.trim();

        StringBuilder content = new StringBuilder();
        content.append("你创建的角色卡《").append(characterName).append("》未通过审核。");
        if (!safeReason.isBlank()) {
            content.append("原因：").append(safeReason).append("。");
        }
        content.append("审核期间该角色仍可自用；如需重新提交，请修改后再次保存。");

        AppUserMessage row = new AppUserMessage();
        row.setUserId(character.getOwnerUserId());
        row.setMessageType("CHARACTER_REVIEW");
        row.setTitle("角色卡审核未通过");
        row.setContent(content.toString());
        row.setRelatedType("CHARACTER");
        row.setRelatedId(character.getId());
        row.setReadFlag(Boolean.FALSE);
        userMessageMapper.insert(row);
    }
}
