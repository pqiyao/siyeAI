const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), 'utf8');
const voices = read('pages', 'user', 'myVoices.vue');
const settings = read('pages', 'user', 'aiSettings.vue');
const moreSettings = read('pages', 'user', 'set.vue');
const chat = read('pages', 'tavern', 'tavernChat.vue');
const memberEditor = read('components', 'tavern', 'character-members-editor.vue');
const api = read('common', 'tavernApi.js');
const pages = read('pages.json');

// Global voice management has one stable entry under More Settings. It does not depend on API mode or vendor.
assert.ok(settings.includes('userVoiceCreationEnabled'));
assert.ok(!settings.includes('canShowUserVoiceEntry'));
assert.ok(moreSettings.includes('v-if="showUserVoiceEntry"'));
assert.ok(moreSettings.includes("this.util.urlTo('/pages/user/myVoices')"));
assert.ok(moreSettings.includes('return this.hasLogin && this.voiceFeatureEnabled !== false'));
assert.ok(moreSettings.includes('fetchAppRuntimeConfig(true)'));

// Character/member management remains contextual and BYOK-only.
assert.ok(chat.includes('v-if="canManageCharacterUserVoices()"'));
assert.ok(chat.includes('state.userVoiceCreationEnabled === true'));
assert.ok(chat.includes("String(state.mode || '').trim() === 'custom'"));
assert.ok(chat.includes("String(state.providerSource || '').trim().toLowerCase() === 'siliconflow'"));
assert.ok(memberEditor.includes('v-if="voiceCatalogPrivateVoices.length"'));
assert.ok(memberEditor.includes("if (!this.voiceCatalogCustomMode || !voice || !voice.available) return;"));
assert.ok(memberEditor.includes('settle(tavernApi.getUserTtsVoices(clientUid))'));
assert.ok(pages.includes('"path": "pages/user/myVoices"'));

// Creation is presented as BYOK-only and sends one bounded multipart request.
assert.ok(voices.includes('克隆、试听和管理都只使用你的硅基流动 API'));
assert.ok(voices.includes('官方平台模式不提供私有音色克隆'));
assert.ok(voices.includes('8 * 1024 * 1024'));
assert.ok(api.includes('function createUserTtsVoice'));
assert.ok(api.includes('/api/v1/tavern/user-voices'));
assert.ok(api.includes('uni.uploadFile'));
assert.ok(api.includes('new FormData()'));
assert.ok(voices.includes("requestId: this.createRequestId"));
assert.ok(voices.includes("if (!this.createRequestId)"));
assert.ok(voices.includes('this.resetCreateRequestId(true)'));

// Unavailable creation states remain manageable and guide users back to their BYOK TTS configuration.
assert.ok(voices.includes('v-if="needsAiSetup || !providerState"'));
assert.ok(voices.includes('请先完成硅基流动 BYOK 配置，或稍后刷新页面'));
assert.ok(voices.includes("uni.navigateTo({ url: '/pages/user/aiSettings' })"));
assert.ok(voices.includes('getTavernUserAiProvider(this.clientUid())'));
assert.ok(voices.includes('v-if="overview.canCreate"'));
assert.ok(voices.includes('this.overview.featureEnabled === false'));
assert.ok(voices.includes('used >= limit'));

// Recording is temporary, duration-bounded, converted to mono WAV on H5, and discarded on unload.
assert.ok(voices.includes('duration: 60000'));
assert.ok(voices.includes('duration < 5000'));
assert.ok(voices.includes('view.setUint16(22, 1, true)'));
assert.ok(voices.includes('discardRecordingResult = true'));
assert.ok(voices.includes('if (!this.pageActive)'));
assert.ok(voices.includes('stream.getTracks()'));
assert.ok(voices.includes('this.pageActive = false;'));
assert.ok(voices.includes('this.releaseRecording();'));
assert.ok(voices.includes('this.releasePreviewPlayer();'));
assert.ok(voices.includes('fetchAppRuntimeConfig(true)'));
assert.ok(voices.includes('voiceFeatureEnabled === false'));

// Binding is scoped and reversible. The server resolves the private voice for the TTS-only endpoint.
assert.ok(voices.includes('scopeType: this.scopeType'));
assert.ok(voices.includes('characterId: this.characterId'));
assert.ok(voices.includes('memberId: this.memberId'));
assert.ok(voices.includes('voiceId: null'));
assert.ok(chat.includes('postTavernSpeech(this.buildCharacterVoiceTtsPayload('));
assert.ok(api.includes("'/api/v1/tavern/chat/tts'"));
assert.equal((chat.match(/ttsUserVoiceId/g) || []).length, 0);
assert.ok(chat.includes('getUserTtsVoices(clientUid)'));
assert.ok(chat.includes('getUserTtsVoiceBinding(clientUid, {'));
assert.ok(chat.includes('putUserTtsVoiceBinding(clientUid, {'));
assert.ok(chat.includes("scopeType: 'CHARACTER'"));
assert.ok(chat.includes('voiceId: desiredPrivateVoiceId > 0 ? desiredPrivateVoiceId : null'));
assert.ok(chat.includes('我的自建音色'));
assert.ok(chat.includes('跟随全局音色'));

// BYOK provider management reuses AI settings and never introduces a second API-key input.
assert.ok(voices.includes('硅基流动音色库'));
assert.ok(voices.includes('当前：我的硅基流动 API'));
assert.ok(!voices.includes('v-model="apiKey"'));
assert.ok(!voices.includes('供应商余额'));
assert.ok(!voices.includes('providerBalanceText'));
assert.ok(!voices.includes('account-balance'));
assert.ok(api.includes('function getUserTtsProviderStatus'));
assert.ok(api.includes('function getUserTtsProviderVoices'));
assert.ok(api.includes('function importUserTtsProviderVoice'));
assert.ok(api.includes("'/api/v1/tavern/user-voices/provider/import'"));

// Preview is short-lived, BYOK billed, and isolated from the chat TTS endpoint.
assert.ok(voices.includes('previewUserTtsVoice(this.clientUid()'));
assert.ok(voices.includes('this.releasePreviewPlayer()'));
assert.ok(voices.includes('试听使用你的硅基流动额度'));
assert.ok(api.includes('function previewUserTtsVoice'));
assert.ok(api.includes("+ '/preview' + buildUserVoiceQuery(clientUid)"));

// Voice resources are visible on the studio page and open without sharing the user's API key.
assert.ok(voices.includes('声线工作室'));
assert.ok(voices.includes('音色资源站'));
assert.ok(voices.includes('https://voice.gbkgov.cn/'));
assert.ok(voices.includes('https://hub.aivis-project.com/'));
assert.ok(voices.includes('https://voicevox.hiroshiba.jp/'));
assert.ok(voices.includes('https://coeiroink.com/'));
assert.ok(voices.includes("window.open(link, '_blank', 'noopener,noreferrer')"));
assert.ok(voices.includes('plus.runtime.openURL(link'));
assert.ok(voices.includes('this.copyVoiceResourceLink(link)'));
assert.ok(voices.includes('不会携带你的 API Key'));
assert.ok(voices.includes('使用第三方音色前，请确认已获得授权；公开发布或商业使用时，请遵守对应平台规则'));

// Deletion offers remote-first cleanup or local-only removal.
assert.ok(voices.includes('同时删除硅基流动资源'));
assert.ok(voices.includes('仅从本应用移除'));
assert.ok(api.includes('{ deleteProvider: deleteProvider === true }'));

console.log('user TTS voice contract tests passed');
