const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

const chat = read('pages/tavern/tavernChat.vue');
const template = chat.slice(0, chat.indexOf('<script>'));
const storeSource = read('common/localMediaStore.js');
const apiSource = read('common/tavernApi.js');
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

function extractMethodWithArgs(source, methodName, nextMethodName) {
	const scriptStart = source.indexOf('<script>');
	const startToken = '\n\t\t\t' + methodName + '(';
	const endToken = '\n\t\t\t' + nextMethodName + '(';
	const startLine = source.indexOf(startToken, scriptStart);
	assert.ok(startLine >= 0, methodName + ' method missing');
	const start = startLine + startToken.indexOf(methodName);
	const end = source.indexOf(endToken, start + startToken.length);
	assert.ok(start >= 0 && end > start, methodName + ' method missing');
	return source.slice(start, end).replace(/,\s*$/, '').trim();
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
assert.doesNotMatch(autoPlay, /isAppPlus/);

assert.match(chat, /characterVoiceAudioProfileKey\(config, catalogState, globalState\)/);
const profileMethod = chat.match(/characterVoiceAudioProfileKey\(config, catalogState, globalState\) \{[\s\S]*?\n\s*\},\n\s*invalidateAssistantVoiceCacheForCurrentConversation/)?.[0] || '';
['modelName:', 'voiceName:', 'voiceTemplateCode,', 'voiceTemplateRevision:', 'boundVoiceId,', 'boundVoiceRevision:',
	'globalBindingVoiceId,', 'globalBindingVoiceRevision:', 'memberBindings,', 'bindingSnapshotComplete:']
	.forEach((field) => assert.ok(profileMethod.includes(field), 'voice profile missing ' + field));
assert.match(chat, /scopeType: 'MEMBER'[\s\S]{0,160}memberId: member\.id/);
assert.match(chat, /roleOverrideActive = !configuredProviderSource/);
assert.match(chat, /setCharacterVoicePanelAutoPlay\(enabled\)[\s\S]{0,320}primeAssistantVoicePlayback\(\)/);
assert.match(chat, /send\(\) \{[\s\S]{0,180}shouldAutoPlayAssistantVoice\(\)[\s\S]{0,100}primeAssistantVoicePlayback\(\)/);
assert.doesNotMatch(chat, /\.catch\(\(\) => audioDataUrl\)/);
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
assert.match(storeSource, /function reserveStorageCapacity\(key, requiredBytes\)/);
assert.match(storeSource, /function evictOldestForRetry\(kind, excludedKey\)/);
assert.match(storeSource, /function serializeMediaWrite\(action\)/);
assert.match(storeSource, /function rollbackStoredEntry\(entry\)/);
assert.match(storeSource, /return serializeMediaWrite\(function \(\) \{ return putDataUrlNow\(meta, dataUrl\); \}\);/);
assert.match(apiSource, /var TTS_REQUEST_TIMEOUT = 120000/);
assert.match(apiSource, /postTavernSpeech\(payload\)[\s\S]{0,180}TTS_REQUEST_TIMEOUT/);
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

	const setAutoPlayMethod = extractMethodWithArgs(
		chat, 'setCharacterVoicePanelAutoPlay', 'characterVoiceAudioProfileKey'
	);
	const setAutoPlay = new Function(
		'return ({' + setAutoPlayMethod + '}).setCharacterVoicePanelAutoPlay;'
	)();
	let primeCount = 0;
	const autoPlayContext = {
		characterVoicePanel: { saving: false, enabled: true, autoPlayEnabled: false },
		primeAssistantVoicePlayback() { primeCount += 1; }
	};
	setAutoPlay.call(autoPlayContext, true);
	assert.equal(primeCount, 1, 'enabling autoplay must unlock H5 audio in the switch gesture');

	const persistMethod = extractMethodWithArgs(
		chat, 'persistAssistantVoiceSegment', 'restoreAssistantVoiceEntry'
	);
	const persist = new Function(
		'require',
		'return ({' + persistMethod + '}).persistAssistantVoiceSegment;'
	)(() => ({ putDataUrl: () => Promise.reject(new Error('disk full')) }));
	const persistContext = {
		isAppPlus: true,
		normalizeDbMessageId: (value) => String(value || ''),
		resolveLocalExpressionViewerKey: () => 'owner-a',
		resolveLocalChatConversationId: () => 'chat-a',
		assistantVoiceLocalMediaKey: () => 'tts:owner-a:chat-a:db_1:0',
		assistantVoiceSegmentSignature: () => 'signature-a',
		tx: (key, fallback) => fallback
	};
	const audioDataUrl = 'data:audio/mpeg;base64,SUQz';
	await assert.rejects(
		persist.call(persistContext, 'db_1', 0, { text: 'hello' }, 'task-a', audioDataUrl, 'profile-a'),
		/保存到本机失败/,
		'App storage failures must not fall back to a Data URL'
	);
	persistContext.isAppPlus = false;
	assert.equal(
		await persist.call(persistContext, 'db_1', 0, { text: 'hello' }, 'task-a', audioDataUrl, 'profile-a'),
		audioDataUrl,
		'H5 may safely use the generated Data URL when IndexedDB is unavailable'
	);

	const profileSource = extractMethodWithArgs(
		chat, 'characterVoiceAudioProfileKey', 'invalidateAssistantVoiceCacheForCurrentConversation'
	);
	const profileKey = new Function(
		'return ({' + profileSource + '}).characterVoiceAudioProfileKey;'
	)();
	const profileContext = {
		characterVoiceConfig: {},
		characterVoiceCatalogState: {},
		characterVoiceGlobalState: {},
		normalizeCharacterVoiceConfig: (value) => value || {},
		resolveCharacterVoiceCharacterId: () => 9,
		localMediaSignature: (value) => value
	};
	const staleRoleConfig = {
		ttsProviderSource: 'siliconflow',
		ttsModelName: 'old-role-model',
		ttsVoiceName: 'old-role-voice',
		ttsVoiceTemplateCode: ''
	};
	const catalog = {
		bindingVoiceId: 31,
		globalBindingVoiceId: 32,
		memberBindingVoiceIds: { 71: 33 },
		bindingSnapshotComplete: true,
		privateVoices: [
			{ id: 31, updatedAt: 'character-r1' },
			{ id: 32, updatedAt: 'global-r1' },
			{ id: 33, updatedAt: 'member-r1' },
			{ id: 34, updatedAt: 'member-r2' }
		]
	};
	const globalProfile = {
		mode: 'custom',
		providerSource: 'openai',
		ttsModelName: 'active-global-model',
		ttsVoiceName: 'nova',
		ttsVoiceTemplateCode: '',
		ttsVoiceTemplates: []
	};
	const profileJson = profileKey.call(profileContext, staleRoleConfig, catalog, globalProfile);
	const profile = JSON.parse(profileJson);
	assert.equal(profile.providerSource, 'openai');
	assert.equal(profile.roleOverrideActive, false);
	assert.equal(profile.modelName, 'active-global-model');
	assert.equal(profile.globalBindingVoiceId, 32);
	assert.deepEqual(profile.memberBindings, [{ memberId: 71, voiceId: 33, voiceRevision: 'member-r1' }]);
	const officialProfile = JSON.parse(profileKey.call(
		profileContext,
		staleRoleConfig,
		catalog,
		Object.assign({}, globalProfile, { mode: 'system' })
	));
	assert.equal(officialProfile.roleOverrideActive, false);
	assert.equal(officialProfile.modelName, 'active-global-model');
	assert.equal(officialProfile.voiceName, 'nova');
	const changedMemberProfile = profileKey.call(
		profileContext,
		staleRoleConfig,
		Object.assign({}, catalog, { memberBindingVoiceIds: { 71: 34 } }),
		globalProfile
	);
	assert.notEqual(changedMemberProfile, profileJson, 'member voice changes must invalidate cached TTS');

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
