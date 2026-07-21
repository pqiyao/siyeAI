package com.example.sillyspringboot.ops.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppFeatureSettings {

    private boolean loginEnabled = true;
    private boolean registerEnabled = true;
    private boolean userCharacterCreationEnabled = true;
    private boolean userByokEnabled = false;
    private boolean imageGenerationEnabled = true;
    private boolean voiceFeatureEnabled = true;
    private int userByokVipMinLevel = 0;
    private int anonymousTrialChatLimit = 30;
    private int anonymousTrialConversationLimit = 6;
    private int anonymousTrialCharacterCreationLimit = 2;

    public boolean isLoginEnabled() {
        return loginEnabled;
    }

    public void setLoginEnabled(boolean loginEnabled) {
        this.loginEnabled = loginEnabled;
    }

    public boolean isRegisterEnabled() {
        return registerEnabled;
    }

    public void setRegisterEnabled(boolean registerEnabled) {
        this.registerEnabled = registerEnabled;
    }

    public boolean isUserCharacterCreationEnabled() {
        return userCharacterCreationEnabled;
    }

    public void setUserCharacterCreationEnabled(boolean userCharacterCreationEnabled) {
        this.userCharacterCreationEnabled = userCharacterCreationEnabled;
    }

    public boolean isUserByokEnabled() {
        return userByokEnabled;
    }

    public void setUserByokEnabled(boolean userByokEnabled) {
        this.userByokEnabled = userByokEnabled;
    }

    public boolean isImageGenerationEnabled() {
        return imageGenerationEnabled;
    }

    public void setImageGenerationEnabled(boolean imageGenerationEnabled) {
        this.imageGenerationEnabled = imageGenerationEnabled;
    }

    public boolean isVoiceFeatureEnabled() {
        return voiceFeatureEnabled;
    }

    public void setVoiceFeatureEnabled(boolean voiceFeatureEnabled) {
        this.voiceFeatureEnabled = voiceFeatureEnabled;
    }

    public int getUserByokVipMinLevel() {
        return userByokVipMinLevel;
    }

    public void setUserByokVipMinLevel(int userByokVipMinLevel) {
        this.userByokVipMinLevel = userByokVipMinLevel;
    }

    public int getAnonymousTrialChatLimit() {
        return anonymousTrialChatLimit;
    }

    public void setAnonymousTrialChatLimit(int anonymousTrialChatLimit) {
        this.anonymousTrialChatLimit = anonymousTrialChatLimit;
    }

    public int getAnonymousTrialConversationLimit() {
        return anonymousTrialConversationLimit;
    }

    public void setAnonymousTrialConversationLimit(int anonymousTrialConversationLimit) {
        this.anonymousTrialConversationLimit = anonymousTrialConversationLimit;
    }

    public int getAnonymousTrialCharacterCreationLimit() {
        return anonymousTrialCharacterCreationLimit;
    }

    public void setAnonymousTrialCharacterCreationLimit(int anonymousTrialCharacterCreationLimit) {
        this.anonymousTrialCharacterCreationLimit = anonymousTrialCharacterCreationLimit;
    }
}
