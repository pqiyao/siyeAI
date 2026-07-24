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

    /** 免费额度用尽后是否允许用钱包（钻金币）继续消*/
    private boolean overQuotaBillingEnabled = true;
    /** 超额聊天：钻石（score）消耗，0 表示不扣钻石 */
    private int chatScoreCost = 1;
    /** 超额聊天：金币消耗，0 表示不扣金币 */
    private int chatGoldCost = 0;
    /** 超额生图：每张图钻石消*/
    private int imageScoreCost = 5;
    private int imageGoldCost = 0;
    /**
     * TTS 语音合成：无免费日额度；cost&gt;0 时走钱包，均0 则语音功能开启后不限次免费     */
    private int ttsScoreCost = 2;
    private int ttsGoldCost = 0;
    /** STT 语音识别：默认免费；配置非零单价后每段录音成功识别扣一次。 */
    private int sttScoreCost = 0;
    private int sttGoldCost = 0;

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

    public boolean isOverQuotaBillingEnabled() {
        return overQuotaBillingEnabled;
    }

    public void setOverQuotaBillingEnabled(boolean overQuotaBillingEnabled) {
        this.overQuotaBillingEnabled = overQuotaBillingEnabled;
    }

    public int getChatScoreCost() {
        return chatScoreCost;
    }

    public void setChatScoreCost(int chatScoreCost) {
        this.chatScoreCost = chatScoreCost;
    }

    public int getChatGoldCost() {
        return chatGoldCost;
    }

    public void setChatGoldCost(int chatGoldCost) {
        this.chatGoldCost = chatGoldCost;
    }

    public int getImageScoreCost() {
        return imageScoreCost;
    }

    public void setImageScoreCost(int imageScoreCost) {
        this.imageScoreCost = imageScoreCost;
    }

    public int getImageGoldCost() {
        return imageGoldCost;
    }

    public void setImageGoldCost(int imageGoldCost) {
        this.imageGoldCost = imageGoldCost;
    }

    public int getTtsScoreCost() {
        return ttsScoreCost;
    }

    public void setTtsScoreCost(int ttsScoreCost) {
        this.ttsScoreCost = ttsScoreCost;
    }

    public int getTtsGoldCost() {
        return ttsGoldCost;
    }

    public void setTtsGoldCost(int ttsGoldCost) {
        this.ttsGoldCost = ttsGoldCost;
    }

    public int getSttScoreCost() {
        return sttScoreCost;
    }

    public void setSttScoreCost(int sttScoreCost) {
        this.sttScoreCost = sttScoreCost;
    }

    public int getSttGoldCost() {
        return sttGoldCost;
    }

    public void setSttGoldCost(int sttGoldCost) {
        this.sttGoldCost = sttGoldCost;
    }
}
