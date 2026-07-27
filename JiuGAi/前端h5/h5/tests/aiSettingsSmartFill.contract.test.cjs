const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const page = fs.readFileSync(
	path.join(__dirname, '../pages/user/aiSettings.vue'),
	'utf8'
);

assert.match(page, /smartFillModels\(\)[\s\S]*smartFillFieldSnapshot\(\)/);
assert.match(page, /finishSmartFillModels\(before\)[\s\S]*uni\.showToast/);
assert.match(page, /smartFillSuccess/);
assert.match(page, /smartFillNoChange/);
assert.match(page, /smartFillUnavailable/);
assert.match(page, /this\.canLoadModels \|\|[\s\S]*this\.modelPresets\.length/);
assert.match(page, /this\.matchCapabilityModel\(optionDefault, 'tts'\)/);
assert.match(page, /return filled;/);

console.log('AI settings smart fill contract tests passed');
