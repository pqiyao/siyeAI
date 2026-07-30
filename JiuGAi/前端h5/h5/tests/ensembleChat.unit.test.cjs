const assert = require('node:assert/strict');
const path = require('node:path');

const ensembleChat = require(path.resolve(__dirname, '../common/ensembleChat.js'));

const normalized = ensembleChat.normalizeAssistantSegments([
	{ index: 2, type: 'NARRATOR', speakerMemberId: 99, content: '  夜色渐深。  ' },
	{ index: 0, type: 'CHARACTER', speakerMemberId: '21', speakerName: ' 林夏 ', speakerAvatarUrl: ' /a.png ', content: ' “回来啦。” ' },
	{ index: 1, type: 'CHARACTER', speakerMemberId: 22, speakerName: '苏雨', content: '“嗯。”' },
	{ index: 3, type: 'CHARACTER', speakerMemberId: 0, content: '不能确认身份' },
	{ index: 4, type: 'TOOL', speakerMemberId: 21, content: '未知类型' },
	{ index: 5, type: 'CHARACTER', speakerMemberId: 21, content: '   ' }
]);

assert.deepEqual(normalized, [
	{ index: 0, type: 'CHARACTER', speakerMemberId: 21, speakerName: '林夏', speakerAvatarUrl: '/a.png', content: '“回来啦。”' },
	{ index: 1, type: 'CHARACTER', speakerMemberId: 22, speakerName: '苏雨', speakerAvatarUrl: '', content: '“嗯。”' },
	{ index: 2, type: 'NARRATOR', speakerMemberId: 0, speakerName: '旁白', speakerAvatarUrl: '', content: '夜色渐深。' }
]);
assert.equal(ensembleChat.hasStructuredAssistantSegments({ role: 'char', segments: normalized }), true);
assert.equal(ensembleChat.hasStructuredAssistantSegments({ role: 'user', segments: normalized }), false);
assert.equal(ensembleChat.hasStructuredAssistantSegments({ role: 'char', segments: [] }), false);
assert.deepEqual(ensembleChat.assistantVoiceBlocksFromSegments(normalized), [
	{ content: '“回来啦。”', speakerMemberId: 21 },
	{ content: '“嗯。”', speakerMemberId: 22 }
]);
assert.equal(ensembleChat.normalizeAssistantSegments(new Array(30).fill(null)).length, 0);

assert.equal(ensembleChat.assistantProtocolDisplayText('<'), '');
assert.equal(ensembleChat.assistantProtocolDisplayText('<|sp'), '');
assert.equal(ensembleChat.assistantProtocolDisplayText('<|speaker:M1|'), '');
assert.equal(ensembleChat.assistantProtocolDisplayText('<|narra'), '');
assert.equal(
	ensembleChat.assistantProtocolDisplayText('<|speaker:M1|>你好<|speaker:M2'),
	'你好'
);
assert.equal(
	ensembleChat.assistantProtocolDisplayText('<|speaker:M1|>你好<|narrator|>夜色渐深。'),
	'你好\n\n夜色渐深。'
);
assert.equal(ensembleChat.assistantProtocolDisplayText('正常正文 <不是协议>'), '正常正文 <不是协议>');

console.log('ensemble chat unit tests passed');
