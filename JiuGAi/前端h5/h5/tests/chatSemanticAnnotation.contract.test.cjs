const assert = require('assert');
const path = require('path');
const Module = require('module');

const projectRoot = path.resolve(__dirname, '..');
const resolveFilename = Module._resolveFilename;
Module._resolveFilename = function resolveProjectAlias(request, parent, isMain, options) {
	const resolved = request.startsWith('@/') ? path.join(projectRoot, request.slice(2)) : request;
	return resolveFilename.call(this, resolved, parent, isMain, options);
};

const markdown = require(path.join(projectRoot, 'common', 'chatMarkdown.js'));
const text = '她抬起头。\n“欢迎回来😀。”（其实还有些紧张。）';
const actionEnd = '她抬起头。\n'.length;
const speechEnd = actionEnd + '“欢迎回来😀。”'.length;
const semantic = {
	schemaVersion: 1,
	classifierVersion: 'test',
	textFingerprint: markdown.semanticTextFingerprint(text),
	segments: [
		{ type: 'action', start: 0, end: actionEnd, confidence: 0.98 },
		{ type: 'speech', start: actionEnd, end: speechEnd, confidence: 0.97 },
		{ type: 'thought', start: speechEnd, end: text.length, confidence: 0.91 }
	]
};

assert.deepStrictEqual(
	markdown.splitChatSegments(text, { semantic }).map((item) => item.type),
	['action', 'speech', 'thought']
);
assert.strictEqual(markdown.extractChatSpeechSegments(text, { semantic }).map((item) => item.text).join(''), '“欢迎回来😀。”');
assert.ok(markdown.renderChatMarkdown(text, { semantic, readMode: 'speechOnly' }).includes('欢迎回来'));
assert.ok(!markdown.renderChatMarkdown(text, { semantic, readMode: 'speechOnly' }).includes('紧张'));

const stale = Object.assign({}, semantic, { textFingerprint: '00000000' });
assert.strictEqual(markdown.validateSemanticAnnotation(text, stale), null);
assert.ok(markdown.splitChatSegments(text, { semantic: stale }).length > 0, 'invalid annotation must fall back');

const overlapping = Object.assign({}, semantic, {
	segments: [
		{ type: 'action', start: 0, end: actionEnd },
		{ type: 'speech', start: actionEnd - 1, end: text.length }
	]
});
assert.strictEqual(markdown.validateSemanticAnnotation(text, overlapping), null);

const displayTextWithoutExpressionMarker = text.replace('她抬起头。\n', '');
assert.strictEqual(
	markdown.validateSemanticAnnotation(displayTextWithoutExpressionMarker, semantic),
	null,
	'annotation for raw content must not be applied after hidden display markers are removed'
);

const sliced = markdown.sliceSemanticAnnotation(text, semantic, actionEnd, speechEnd);
assert.ok(sliced);
assert.deepStrictEqual(markdown.splitChatSegments(text.slice(actionEnd, speechEnd), { semantic: sliced }).map((item) => item.type), ['speech']);

const rich = '<status>生命值：80</status>';
const richSemantic = {
	schemaVersion: 1,
	textFingerprint: markdown.semanticTextFingerprint(rich),
	segments: [{ type: 'narration', start: 0, end: rich.length }]
};
assert.ok(markdown.splitChatSegments(rich, { semantic: richSemantic }).some((item) => item.type === 'rich'), 'rich content must keep its renderer');

const chatPageSource = require('fs').readFileSync(path.join(projectRoot, 'pages', 'tavern', 'tavernChat.vue'), 'utf8');
assert.ok(chatPageSource.includes('this.semanticPollAttempts >= 9'), 'polling must cover the backend classifier timeout window');

console.log('chat semantic annotation contract tests passed');
