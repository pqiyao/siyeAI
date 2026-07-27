const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

const aiSettings = read('pages/user/aiSettings.vue');
const chat = read('pages/tavern/tavernChat.vue');
const memberEditor = read('components/tavern/character-members-editor.vue');
const icon = read('static/chat/voice-config.svg');

assert.match(aiSettings, /form\.mode === 'system' && showVoiceConfig/);
assert.match(aiSettings, /officialTtsVoiceTemplates/);
assert.match(aiSettings, /跟随平台默认/);
assert.match(aiSettings, /ttsVoicePresets\.length[\s\S]{0,500}selectOfficialTtsVoicePreset/);
assert.match(aiSettings, /ttsVoicePresets:\s*\[\]/);
assert.doesNotMatch(aiSettings, /selectTtsVoiceTemplate\(item\)[\s\S]{0,180}form\.mode !== 'custom'/);

assert.match(chat, /v-if="characterVoiceGlobalState\.mode === 'custom'" class="character-voice-field"[\s\S]{0,220}角色级 TTS 模型覆盖/);
assert.match(chat, /<view class="character-voice-field"[^>]*>[\s\S]{0,220}character_voice_voice/);
assert.match(chat, /if \(config\.ttsVoiceTemplateCode\) \{\s*payload\.ttsVoiceTemplateCode/);
assert.match(chat, /else if \(config\.ttsVoiceName\) \{\s*payload\.ttsVoiceName/);
assert.match(chat, /source\.ttsVoicePresets/);
assert.match(chat, /official_voice_preset_/);
assert.match(chat, /String\(this\.characterVoiceGlobalState\.mode \|\| ''\)\.trim\(\) === 'custom' &&[\s\S]{0,220}recommendedModelName/);

assert.match(memberEditor, /v-if="voiceCatalogPrivateVoices\.length"/);
assert.match(memberEditor, /切换到自己的 TTS API 后可用/);
assert.match(memberEditor, /providerState\.ttsVoicePresets/);
assert.doesNotMatch(memberEditor, /voicePresets\(member\) \{\s*if \(!this\.voiceCatalogCustomMode\) return \[\]/);
assert.match(icon, /<circle cx="24" cy="22"/);
assert.match(icon, /M50 17c7 6\.6/);

console.log('official voice selection contract passed');
