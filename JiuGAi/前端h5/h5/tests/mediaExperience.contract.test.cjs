const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const chat = fs.readFileSync(path.join(root, 'pages', 'tavern', 'tavernChat.vue'), 'utf8');

assert.match(chat, /characterId,\s*characterName:/s);
assert.match(chat, /referenceImageUrl:\s*referenceImageUrl \|\| ''/);
assert.match(chat, /referenceMode:\s*consistencyMode/);
assert.match(chat, /referenceSourceMode,/);
assert.match(chat, /recentSceneHint:\s*this\.resolveCharacterImageRecentSceneHint/);
assert.match(
	chat,
	/consistencyMode === 'free' \|\| \(!customMode && consistencyMode === 'balanced'\)[\s\S]{0,80}\? Promise\.resolve\(''\)/
);

assert.match(chat, /state:\s*firstAudioDataUrl \? 'partial' : 'generation_error'/);
assert.match(chat, /errorKind:\s*'generation'/);
assert.match(chat, /missingIndexes:\s*this\.assistantVoiceMissingSegmentIndexes/);
assert.match(chat, /const requestKey = String\(entry\.taskId \|\| \('tts_' \+ messageId\)\)/);
assert.match(chat, /resumeAssistantVoiceSegments\(row, \{ autoplay: true \}\)/);
assert.match(chat, /entry\.state === 'partial'/);

console.log('media experience contract tests passed');
