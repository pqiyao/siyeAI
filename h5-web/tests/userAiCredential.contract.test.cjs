const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const rules = require('../common/userAiCredentialRules.js');

assert.equal(
	rules.normalizeCustomApiBaseUrl('https://example.com/'),
	'https://example.com/v1'
);
assert.equal(
	rules.normalizeCustomApiBaseUrl('https://example.com/v1/chat/completions'),
	'https://example.com/v1'
);
assert.equal(
	rules.sameProviderCredentialTarget(
		'custom',
		'https://example.com/v1/chat/completions',
		'custom',
		'https://example.com/'
	),
	true
);
assert.equal(
	rules.sameProviderCredentialTarget('openai', '', 'openrouter', ''),
	false
);

const page = fs.readFileSync(
	path.join(__dirname, '../pages/user/aiSettings.vue'),
	'utf8'
);

assert.match(page, /needChatApiKey/);
assert.match(page, /needTtsApiKey/);
assert.match(page, /needImageApiKey/);
assert.match(page, /this\.form\.imageUseSeparateConfig[\s\S]*shouldRunTtsConnectionTest/);
assert.match(page, /this\.form\.ttsUseSeparateConfig[\s\S]*normalizeConnectionScopeLabel/);
assert.match(page, /String\(this\.form\.apiKey \|\| ''\)\.trim\(\) \|\| this\.effectiveSavedKeyAvailable/);
assert.match(page, /incompleteSeparateScopes|incompleteScopes/);
assert.match(page, /this\.form\.ttsUseSeparateConfig = false/);
assert.match(page, /this\.form\.imageUseSeparateConfig = false/);
assert.match(page, /<view v-if="form\.mode === 'custom'" class="custom-settings">/);
assert.doesNotMatch(page, /<view v-else class="custom-settings">/);
assert.match(page, /selectSystemMode\(\)\s*\{[\s\S]*?this\.form\.mode = 'system'/);

console.log('user AI credential contract tests passed');
