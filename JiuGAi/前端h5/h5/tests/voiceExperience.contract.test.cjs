const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

const chat = read('pages/tavern/tavernChat.vue');
const template = chat.slice(0, chat.indexOf('<script>'));
const storeSource = read('common/localMediaStore.js');
const cachePage = read('pages/user/voiceCache.vue');
const settingsPage = read('pages/user/set.vue');
const pages = read('pages.json');

function extractMethod(source, methodName, nextMethodName) {
	const startToken = methodName + '() {';
	const endToken = nextMethodName + '() {';
	const start = source.indexOf(startToken);
	const end = source.indexOf(endToken, start + startToken.length);
	assert.ok(start >= 0 && end > start, methodName + ' method missing');
	return source.slice(start, end).replace(/,\s*$/, '');
}

function assertScriptSyntax(source, label) {
	const match = source.match(/<script>([\s\S]*?)<\/script>/);
	assert.ok(match, label + ' script block missing');
	const parseable = match[1]
		.replace(/^\s*import\s+[^\n]+$/gm, '')
		.replace(/\bexport\s+default\b/, 'return');
	assert.doesNotThrow(() => new Function(parseable), label + ' script syntax invalid');
}

assertScriptSyntax(chat, 'tavernChat.vue');
assertScriptSyntax(cachePage, 'voiceCache.vue');
assertScriptSyntax(settingsPage, 'set.vue');

assert.equal((template.match(/@tap="openCharacterVoicePanel"/g) || []).length, 1);
assert.doesNotMatch(template, /@tap="toggleAssistantVoiceAuto"/);
assert.match(template, /nav-voice-config--single/);
assert.match(template, /只朗读识别到的角色台词，不朗读动作、心理和状态栏/);
assert.match(template, /character-voice-control-panel/);
assert.match(template, /character-voice-source/);

const autoPrepare = chat.match(/shouldAutoPrepareAssistantVoice\(\) \{[\s\S]*?\n\s*\},/)?.[0] || '';
const autoPlay = chat.match(/shouldAutoPlayAssistantVoice\(\) \{[\s\S]*?\n\s*\},/)?.[0] || '';
assert.match(autoPrepare, /isCharacterVoiceAutoPlayEnabled\(\)/);
assert.match(autoPlay, /isCharacterVoiceAutoPlayEnabled\(\)/);
assert.doesNotMatch(autoPrepare, /assistantVoiceAutoEnabled/);
assert.doesNotMatch(autoPlay, /assistantVoiceAutoEnabled/);

assert.match(chat, /characterVoiceAudioProfileKey\(config, catalogState, globalState\)/);
const profileMethod = chat.match(/characterVoiceAudioProfileKey\(config, catalogState, globalState\) \{[\s\S]*?\n\s*\},\n\s*invalidateAssistantVoiceCacheForCurrentConversation/)?.[0] || '';
['modelName:', 'voiceName:', 'voiceTemplateCode,', 'voiceTemplateRevision:', 'boundVoiceId,', 'boundVoiceRevision:']
	.forEach((field) => assert.ok(profileMethod.includes(field), 'voice profile missing ' + field));
assert.match(chat, /assistantVoiceSegmentSignature\(segment, voiceProfileKey\)/);
assert.match(chat, /voiceProfileKey !== this\.characterVoiceAudioProfileKey\(\)/);
assert.match(chat, /removeByConversationKind\([\s\S]{0,220}'assistant_tts'/);
assert.match(chat, /state === 'playback_error'[\s\S]{0,120}'再次播放'/);
assert.match(chat, /state === 'generation_error'[\s\S]{0,160}'重新生成'/);
assert.match(chat, /state: hasAudio \? 'playback_error' : 'generation_error'/);
assert.match(chat, /primeAssistantVoicePlayback\(\);[\s\S]{0,500}force: generationFailed/);
assert.doesNotMatch(chat, /force: !!\(entry && entry\.state === 'error'\)/);
assert.match(chat, /unlock\(\) \{[\s\S]{0,1600}silentWavUrl\(\)/);
assert.match(chat, /failedAttempt === safeAttempt/);

assert.match(storeSource, /function summary\(query\)/);
assert.match(storeSource, /function removeByOwnerKind\(ownerKey, kind\)/);
assert.match(cachePage, /summary\(\{ ownerKey: this\.ownerKey\(\), kind: 'assistant_tts' \}\)/);
assert.match(cachePage, /removeByOwnerKind\(this\.ownerKey\(\), 'assistant_tts'\)/);
assert.match(cachePage, /不会保存到服务器/);
assert.match(settingsPage, /openVoiceCache/);
assert.match(pages, /"path": "pages\/user\/voiceCache"/);

const storage = {};
global.uni = {
	getStorageSync(key) { return storage[key]; },
	setStorageSync(key, value) { storage[key] = value; }
};

const indexKey = 'tavern_local_media_index_v1';
storage[indexKey] = {
	version: 1,
	entries: [
		{ key: 'voice-a', ownerKey: 'owner-a', conversationId: 'chat-a', messageId: 'db_1', kind: 'assistant_tts', size: 100, lastAccessAt: 10, storage: 'external', location: 'voice-a' },
		{ key: 'image-a', ownerKey: 'owner-a', conversationId: 'chat-a', messageId: 'db_1', kind: 'generated_image', size: 200, lastAccessAt: 20, storage: 'external', location: 'image-a' },
		{ key: 'voice-b', ownerKey: 'owner-b', conversationId: 'chat-b', messageId: 'db_2', kind: 'assistant_tts', size: 300, lastAccessAt: 30, storage: 'external', location: 'voice-b' }
	]
};

const localMediaStore = require(path.join(root, 'common', 'localMediaStore.js'));

(async () => {
	const playerMethod = extractMethod(chat, 'createAssistantVoiceH5Player', 'stopAssistantVoicePlayback');
	let latestAudio = null;
	let nextObjectUrl = 0;
	class FakeAudio {
		constructor() {
			latestAudio = this;
			this.listeners = {};
			this.playResult = 'resolve';
			this.src = '';
			this.currentTime = 0;
		}
		addEventListener(name, handler) { this.listeners[name] = handler; }
		play() {
			return this.playResult === 'reject'
				? Promise.reject(Object.assign(new Error('blocked'), { name: 'NotAllowedError' }))
				: Promise.resolve();
		}
		pause() {}
		removeAttribute(name) { if (name === 'src') this.src = ''; }
		load() {}
		emit(name, payload) { if (this.listeners[name]) this.listeners[name](payload); }
	}
	const fakeUrl = {
		createObjectURL() { nextObjectUrl += 1; return 'blob:test-' + nextObjectUrl; },
		revokeObjectURL() {}
	};
	const createPlayer = new Function(
		'Audio', 'URL', 'Blob', 'ArrayBuffer', 'DataView',
		'return ({' + playerMethod + '}).createAssistantVoiceH5Player;'
	)(FakeAudio, fakeUrl, Blob, ArrayBuffer, DataView);
	const player = createPlayer();
	let playbackErrors = 0;
	let playbackEnded = 0;
	player.onError(() => { playbackErrors += 1; });
	player.onEnded(() => { playbackEnded += 1; });
	assert.equal(await player.unlock(), true);
	latestAudio.emit('ended');
	assert.equal(playbackErrors, 0, 'unlock must not report a playback failure');
	assert.equal(playbackEnded, 0, 'unlock must not advance the real voice playlist');
	player.src = 'blob:voice-a';
	latestAudio.playResult = 'reject';
	await player.play().catch(() => {});
	latestAudio.emit('error', new Error('decode failed'));
	assert.equal(playbackErrors, 1, 'one failed attempt must be reported once');

	const before = await localMediaStore.summary({ ownerKey: 'owner-a', kind: 'assistant_tts' });
	assert.equal(before.count, 1);
	assert.equal(before.totalBytes, 100);
	assert.equal(before.maxBytes, 128 * 1024 * 1024);

	assert.equal(await localMediaStore.removeByOwnerKind('owner-a', 'assistant_tts'), true);
	const remaining = storage[indexKey].entries;
	assert.deepEqual(remaining.map((item) => item.key).sort(), ['image-a', 'voice-b']);

	const after = await localMediaStore.summary({ ownerKey: 'owner-a', kind: 'assistant_tts' });
	assert.equal(after.count, 0);
	assert.equal(after.totalBytes, 0);
	console.log('voice experience contract tests passed');
})().catch((error) => {
	console.error(error);
	process.exitCode = 1;
});
