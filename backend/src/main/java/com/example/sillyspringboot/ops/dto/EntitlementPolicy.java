package com.example.sillyspringboot.ops.dto;

public class EntitlementPolicy {

    private int guestDailyChatQuota = 100;
    private int vipDailyChatQuota = 300;
    private int svipDailyChatQuota = 1000;
    private int guestDailyByokChatQuota = 100;
    private int vipDailyByokChatQuota = 300;
    private int svipDailyByokChatQuota = 1000;
    private int guestDailyImageQuota = 0;
    private int vipDailyImageQuota = 5;
    private int svipDailyImageQuota = 30;
    private int guestCharacterCreateLimit = 999;
    private int vipCharacterCreateLimit = 999;
    private int svipCharacterCreateLimit = 999;
    private boolean guestCanAccessVipCharacters;
    private boolean vipCanAccessVipCharacters = true;
    private boolean svipCanAccessVipCharacters = true;
    private boolean continueConsumesQuota;
    private boolean regenerateConsumesQuota;
    private boolean byokContinueConsumesQuota = true;
    private boolean byokRegenerateConsumesQuota = true;

    public int getGuestDailyChatQuota() {
        return guestDailyChatQuota;
    }

    public void setGuestDailyChatQuota(int guestDailyChatQuota) {
        this.guestDailyChatQuota = guestDailyChatQuota;
    }

    public int getVipDailyChatQuota() {
        return vipDailyChatQuota;
    }

    public void setVipDailyChatQuota(int vipDailyChatQuota) {
        this.vipDailyChatQuota = vipDailyChatQuota;
    }

    public int getSvipDailyChatQuota() {
        return svipDailyChatQuota;
    }

    public void setSvipDailyChatQuota(int svipDailyChatQuota) {
        this.svipDailyChatQuota = svipDailyChatQuota;
    }

    public int getGuestDailyByokChatQuota() {
        return guestDailyByokChatQuota;
    }

    public void setGuestDailyByokChatQuota(int guestDailyByokChatQuota) {
        this.guestDailyByokChatQuota = guestDailyByokChatQuota;
    }

    public int getVipDailyByokChatQuota() {
        return vipDailyByokChatQuota;
    }

    public void setVipDailyByokChatQuota(int vipDailyByokChatQuota) {
        this.vipDailyByokChatQuota = vipDailyByokChatQuota;
    }

    public int getSvipDailyByokChatQuota() {
        return svipDailyByokChatQuota;
    }

    public void setSvipDailyByokChatQuota(int svipDailyByokChatQuota) {
        this.svipDailyByokChatQuota = svipDailyByokChatQuota;
    }

    public int getGuestDailyImageQuota() {
        return guestDailyImageQuota;
    }

    public void setGuestDailyImageQuota(int guestDailyImageQuota) {
        this.guestDailyImageQuota = guestDailyImageQuota;
    }

    public int getVipDailyImageQuota() {
        return vipDailyImageQuota;
    }

    public void setVipDailyImageQuota(int vipDailyImageQuota) {
        this.vipDailyImageQuota = vipDailyImageQuota;
    }

    public int getSvipDailyImageQuota() {
        return svipDailyImageQuota;
    }

    public void setSvipDailyImageQuota(int svipDailyImageQuota) {
        this.svipDailyImageQuota = svipDailyImageQuota;
    }

    public int getGuestCharacterCreateLimit() {
        return guestCharacterCreateLimit;
    }

    public void setGuestCharacterCreateLimit(int guestCharacterCreateLimit) {
        this.guestCharacterCreateLimit = guestCharacterCreateLimit;
    }

    public int getVipCharacterCreateLimit() {
        return vipCharacterCreateLimit;
    }

    public void setVipCharacterCreateLimit(int vipCharacterCreateLimit) {
        this.vipCharacterCreateLimit = vipCharacterCreateLimit;
    }

    public int getSvipCharacterCreateLimit() {
        return svipCharacterCreateLimit;
    }

    public void setSvipCharacterCreateLimit(int svipCharacterCreateLimit) {
        this.svipCharacterCreateLimit = svipCharacterCreateLimit;
    }

    public boolean isGuestCanAccessVipCharacters() {
        return guestCanAccessVipCharacters;
    }

    public void setGuestCanAccessVipCharacters(boolean guestCanAccessVipCharacters) {
        this.guestCanAccessVipCharacters = guestCanAccessVipCharacters;
    }

    public boolean isVipCanAccessVipCharacters() {
        return vipCanAccessVipCharacters;
    }

    public void setVipCanAccessVipCharacters(boolean vipCanAccessVipCharacters) {
        this.vipCanAccessVipCharacters = vipCanAccessVipCharacters;
    }

    public boolean isSvipCanAccessVipCharacters() {
        return svipCanAccessVipCharacters;
    }

    public void setSvipCanAccessVipCharacters(boolean svipCanAccessVipCharacters) {
        this.svipCanAccessVipCharacters = svipCanAccessVipCharacters;
    }

    public boolean isContinueConsumesQuota() {
        return continueConsumesQuota;
    }

    public void setContinueConsumesQuota(boolean continueConsumesQuota) {
        this.continueConsumesQuota = continueConsumesQuota;
    }

    public boolean isRegenerateConsumesQuota() {
        return regenerateConsumesQuota;
    }

    public void setRegenerateConsumesQuota(boolean regenerateConsumesQuota) {
        this.regenerateConsumesQuota = regenerateConsumesQuota;
    }

    public boolean isByokContinueConsumesQuota() {
        return byokContinueConsumesQuota;
    }

    public void setByokContinueConsumesQuota(boolean byokContinueConsumesQuota) {
        this.byokContinueConsumesQuota = byokContinueConsumesQuota;
    }

    public boolean isByokRegenerateConsumesQuota() {
        return byokRegenerateConsumesQuota;
    }

    public void setByokRegenerateConsumesQuota(boolean byokRegenerateConsumesQuota) {
        this.byokRegenerateConsumesQuota = byokRegenerateConsumesQuota;
    }
}
