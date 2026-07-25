const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const api = fs.readFileSync(path.join(root, 'common/tavernApi.js'), 'utf8');
const settings = fs.readFileSync(path.join(root, 'pages/tavern/chatPersona.vue'), 'utf8');
const chat = fs.readFileSync(path.join(root, 'pages/tavern/tavernChat.vue'), 'utf8');

assert.match(api, /\/api\/v1\/tavern\/chat-presets\/copy/);
assert.match(api, /putTavernPrivateChatPreset/);
assert.match(api, /deleteTavernPrivateChatPreset/);
assert.match(settings, /presetTab === 'official'/);
assert.match(settings, /presetTab === 'mine'/);
assert.match(settings, /postTavernChatPresetCopy/);
assert.match(settings, /temperature: Number\(e\.temperature\)/);
assert.match(settings, /topP: Number\(e\.topP\)/);
assert.match(settings, /maxTokens: Number\(e\.maxTokens\)/);
assert.match(settings, /maxContext: Number\(e\.maxContext\)/);
assert.doesNotMatch(settings, /api.?key|reverse.?proxy|provider.?url|prompt.?manager/i);
assert.doesNotMatch(chat, /postTavernChatPresetCopy|putTavernPrivateChatPreset|deleteTavernPrivateChatPreset/);

console.log('chat settings and private preset contract passed');
