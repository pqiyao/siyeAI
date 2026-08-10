const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const chat = fs.readFileSync(path.join(root, 'pages/tavern/tavernChat.vue'), 'utf8');
const settings = fs.readFileSync(path.join(root, 'pages/user/aiSettings.vue'), 'utf8');

assert.match(chat, /visionRequestId:\s*imageUrls\.length\s*\?\s*'vision_'\s*\+\s*uid/);
assert.match(chat, /next\.visionOfficialEnabled === true && next\.visionOfficialReady === true/);
assert.match(chat, /请先在 AI 设置中配置自己的 API Key 和视觉模型/);
assert.match(chat, /if \(mode === 'custom'\)[\s\S]*?else \{[\s\S]*?visionOfficialEnabled/);
assert.match(settings, /使用你的 API Key，不扣平台识图费用/);
assert.match(settings, /角色回复仍按正常聊天规则结算/);

console.log('vision routing contract tests passed');
