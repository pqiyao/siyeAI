package com.example.sillyspringboot.conversation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class UpdateConversationWorldbooksRequest {

    /**
     * ST worldinfo 文件名列表（如：["New World (1)"]）。
     */
    @NotNull
    private List<String> worldNames;

    public List<String> getWorldNames() {
        return worldNames;
    }

    public void setWorldNames(List<String> worldNames) {
        this.worldNames = worldNames;
    }
}

