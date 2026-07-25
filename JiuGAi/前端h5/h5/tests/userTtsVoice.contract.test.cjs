const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (...parts) => fs.readFileSync(path.join(root, ...parts), 'utf8');
const voices = read('pages', 'user', 'myVoices.vue');
const settings = read('pages', 'user', 'aiSettings.vue');
const chat = read('pages', 'tavern', 'tavernChat.vue');
const api = read('common', 'tavernApi.js');
const pages = read('pages.json');

// The global entitlement switch controls both user entry points.
assert.ok(settings.includes('userVoiceCreationEnabled'));
assert.ok(settings.includes('v-if="canShowUserVoiceEntry"'));
assert.ok(settings.includes("this.form.mode !== 'custom'"));
assert.ok(settings.includes("source.toLowerCase() === 'siliconflow'"));
assert.ok(chat.includes('v-if="canManageCharacterUserVoices()"'));
assert.ok(chat.includes('state.userVoiceCreationEnabled === true'));
assert.ok(chat.includes("String(state.mode || '').trim() === 'custom'"));
assert.ok(chat.includes("String(state.providerSource || '').trim().toLowerCase() === 'siliconflow'"));
assert.ok(pages.includes('"path": "pages/user/myVoices"'));

// Creation is presented as BYOK-only and sends one bounded multipart request.
assert.ok(voices.includes('只使用你的 API Key'));
assert.ok(voices.includes('官方平台模式不提供自建音色服务'));
assert.ok(voices.includes('8 * 1024 * 1024'));
assert.ok(api.includes('function createUserTtsVoice'));
assert.ok(api.includes('/api/v1/tavern/user-voices'));
assert.ok(api.includes('uni.uploadFile'));
assert.ok(api.includes('new FormData()'));

// Recording is temporary, duration-bounded, converted to mono WAV on H5, and discarded on unload.
assert.ok(voices.includes('duration: 20000'));
assert.ok(voices.includes('duration < 5000'));
assert.ok(voices.includes('view.setUint16(22, 1, true)'));
assert.ok(voices.includes('discardRecordingResult = true'));
assert.ok(voices.includes('if (!this.pageActive)'));
assert.ok(voices.includes('stream.getTracks()'));
assert.ok(voices.includes('this.pageActive = false;'));
assert.ok(voices.includes('this.releaseRecording();'));

// Binding is scoped and reversible. The server resolves the private voice for the TTS-only endpoint.
assert.ok(voices.includes('scopeType: this.scopeType'));
assert.ok(voices.includes('characterId: this.characterId'));
assert.ok(voices.includes('memberId: this.memberId'));
assert.ok(voices.includes('voiceId: null'));
assert.ok(chat.includes('postTavernSpeech(this.buildCharacterVoiceTtsPayload('));
assert.ok(api.includes("'/api/v1/tavern/chat/tts'"));
assert.equal((chat.match(/ttsUserVoiceId/g) || []).length, 0);

console.log('user TTS voice contract tests passed');
