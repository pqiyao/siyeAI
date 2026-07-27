const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const chat = fs.readFileSync(path.join(root, 'pages/tavern/tavernChat.vue'), 'utf8');
const settings = fs.readFileSync(path.join(root, 'pages/user/aiSettings.vue'), 'utf8');
const api = fs.readFileSync(path.join(root, 'common/tavernApi.js'), 'utf8');

assert.match(chat, /<tavern-nav-bar[\s\S]*?class="chat-model-bar"[\s\S]*?class="tool-bar"/);
assert.match(chat, /平台模型[\s\S]*?我的 API/);
assert.match(chat, /if \(this\.sending\)[\s\S]*?生成结束后再切换模型/);
assert.match(chat, /fields\.chatModelSource = this\.currentChatModel\.source/);
assert.match(chat, /fields\.chatModelRef = String\(this\.currentChatModel\.ref\)/);
assert.match(chat, /if \(this\.chatModelCatalog\.loading\) return fields/);
assert.match(chat, /fields\.chatModelSelectionVersion = Number\(this\.currentChatModel\.selectionVersion\)/);
assert.match(chat, /createChatGenerationRequestId\('regen'/);
assert.match(chat, /createChatGenerationRequestId\('continue'/);
assert.match(chat, /createChatGenerationRequestId\('send'/);
assert.match(settings, /class="field chat-model-library"/);
assert.match(settings, /putTavernUserAiSettings\(clientUid/);
assert.match(settings, /defaultModelName:/);
assert.match(api, /function getTavernChatModels/);
assert.match(api, /function selectTavernChatModel/);
assert.match(api, /function saveTavernUserChatModels/);
assert.match(api, /function putTavernUserAiSettings/);

console.log('chat model selection contract tests passed');
