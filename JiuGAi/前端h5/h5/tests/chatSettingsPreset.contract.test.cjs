const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const api = fs.readFileSync(path.join(root, 'common/tavernApi.js'), 'utf8');
const settings = fs.readFileSync(path.join(root, 'pages/tavern/chatPersona.vue'), 'utf8');
const chat = fs.readFileSync(path.join(root, 'pages/tavern/tavernChat.vue'), 'utf8');

assert.match(api, /\/api\/v1\/tavern\/chat-presets\/copy/);
assert.match(api, /postTavernPrivateChatPreset/);
assert.match(api, /putTavernPrivateChatPreset/);
assert.match(api, /deleteTavernPrivateChatPreset/);
assert.match(api, /systemChatPresetEntryVisible: raw\.systemChatPresetEntryVisible !== false/);
assert.match(api, /userChatPresetEntryVisible: raw\.userChatPresetEntryVisible !== false/);
assert.match(settings, /conversationId && presetFeatureConfigReady && showPresetSection/);
assert.match(settings, /showSystemPresets\(\)/);
assert.match(settings, /showUserPresets\(\)/);
assert.match(settings, /showSystemPresets && showUserPresets/);
assert.match(settings, /v-if="showUserPresets" class="copy-button"/);
assert.match(settings, /fetchAppRuntimeConfig\(true\)/);
assert.match(settings, /presetTab === 'official'/);
assert.match(settings, /presetTab === 'mine'/);
assert.match(settings, /postTavernChatPresetCopy/);
assert.match(settings, /form\.fieldText\(s\.temperature, 1\)/);
assert.match(settings, /form\.fieldText\(s\.frequencyPenalty, 0\)/);
assert.match(settings, /form\.fieldText\(s\.presencePenalty, 0\)/);
assert.match(settings, /form\.requiredNumber\(e\.temperature\)/);
assert.match(settings, /最大输出 Token 范围为 800–8192/);
assert.doesNotMatch(settings, /presetEditor\.maxContext|v-model="presetEditor\.maxContext"/);
assert.match(settings, /if \(!saved\) this\.officialDraftId = previousOfficialId/);
assert.match(settings, /if \(!this\.showSystemPresets \|\| this\.presetSaving\) return;/);
assert.match(settings, /复制生成参数到我的预设/);
assert.match(settings, /新建我的预设/);
assert.match(settings, /restoreSystemDefault/);
assert.doesNotMatch(settings, /api.?key|reverse.?proxy|provider.?url|prompt.?manager/i);
assert.doesNotMatch(chat, /postTavernChatPresetCopy|putTavernPrivateChatPreset|deleteTavernPrivateChatPreset/);

console.log('chat settings and private preset contract passed');
