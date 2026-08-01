const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const store = fs.readFileSync(path.join(root, 'common', 'localMediaStore.js'), 'utf8');
const chat = fs.readFileSync(path.join(root, 'pages', 'tavern', 'tavernChat.vue'), 'utf8');
const api = fs.readFileSync(path.join(root, 'common', 'tavernApi.js'), 'utf8');

assert.match(store, /indexedDB\.open\(DB_NAME/);
assert.match(store, /_doc\/tavern_media/);
assert.match(store, /resolveLocalFileSystemURL\('_doc\/'/);
assert.match(store, /createWriter/);
assert.match(store, /audio\/aac[^\n]+return 'aac'/);
assert.match(store, /audio\/flac[^\n]+return 'flac'/);
assert.match(store, /audio\/amr[^\n]+return 'amr'/);
assert.match(store, /app_media_write_timeout/);
assert.match(store, /actualSize !== parts\.bytes\.byteLength/);
assert.match(store, /removeByConversation/);
assert.match(store, /lastAccessAt/);
assert.match(store, /URL\.revokeObjectURL/);
assert.match(store, /revokeObjectUrl\(entry\.key\)/);
assert.match(store, /return idbDelete\(entry\.key\)/);
assert.match(store, /taskId: text\(source\.taskId\)/);
assert.match(chat, /current\.taskId \|\| current\.requestKey/);
assert.match(chat, /taskId: requestKey/);
assert.match(chat, /ASSISTANT_VOICE_SEGMENT_HARD_MAX = 900/);
assert.doesNotMatch(chat, /LOCAL_CHAT_IMAGE_CACHE_TTL_MS/);
assert.match(chat, /persistAssistantVoiceSegment/);
assert.match(chat, /hydrateLocalChatImageMedia/);
assert.match(chat, /mediaKeys/);
assert.match(api, /localMediaStore\.js.*removeByConversation/s);

const account = fs.readFileSync(path.join(root, 'pages', 'user', 'numanquan.vue'), 'utf8');
assert.match(account, /removeByOwner\(ownerKey\)/);
assert.ok(account.indexOf('removeByOwner(ownerKey)') < account.indexOf('uni.clearStorageSync()'));

const storage = {};
let writtenFileName = '';
let writtenFileSize = 0;
let activeWrites = 0;
let maxActiveWrites = 0;
global.uni = {
	getStorageSync(key) { return storage[key]; },
	setStorageSync(key, value) { storage[key] = value; }
};
global.plus = {
	io: {
		resolveLocalFileSystemURL(pathValue, success) {
			assert.equal(pathValue, '_doc/');
			success({
				getDirectory(directoryName, options, directorySuccess) {
					assert.equal(directoryName, 'tavern_media');
					assert.equal(options.create, true);
					directorySuccess({
						getFile(fileName, fileOptions, fileSuccess) {
							writtenFileName = fileName;
							assert.equal(fileOptions.create, true);
							const fileEntry = {
								createWriter(writerSuccess) {
					const writer = {
						write(blob) {
							writtenFileSize = blob.size;
							activeWrites += 1;
							maxActiveWrites = Math.max(maxActiveWrites, activeWrites);
							queueMicrotask(() => {
								activeWrites -= 1;
								writer.onwrite();
							});
										}
									};
									writerSuccess(writer);
								},
								file(fileSuccess) { fileSuccess({ size: writtenFileSize }); },
								toLocalURL() { return 'file:///app/doc/tavern_media/' + fileName; }
							};
							fileSuccess(fileEntry);
						}
					});
				}
			});
		}
	}
};

const localMediaStore = require(path.join(root, 'common', 'localMediaStore.js'));

(async () => {
	const stored = await localMediaStore.putDataUrl({
		key: 'tts:owner:chat:db_1:0',
		ownerKey: 'owner',
		conversationId: 'chat',
		messageId: 'db_1',
		kind: 'assistant_tts'
	}, 'data:audio/aac;base64,AQID');
	assert.match(writtenFileName, /\.aac$/);
	assert.equal(writtenFileSize, 3);
	assert.match(stored.url, /^file:\/\/\/app\/doc\/tavern_media\/.*\.aac$/);
	assert.equal(storage.tavern_local_media_index_v1.entries[0].size, 3);
	await Promise.all([
		localMediaStore.putDataUrl({
			key: 'tts:owner:chat:db_2:0', ownerKey: 'owner', conversationId: 'chat',
			messageId: 'db_2', kind: 'assistant_tts'
		}, 'data:audio/aac;base64,AQID'),
		localMediaStore.putDataUrl({
			key: 'tts:owner:chat:db_3:0', ownerKey: 'owner', conversationId: 'chat',
			messageId: 'db_3', kind: 'assistant_tts'
		}, 'data:audio/aac;base64,AQID')
	]);
	assert.equal(maxActiveWrites, 1, 'App media writes must stay serialized');
	console.log('local media store contract tests passed');
})().catch((error) => {
	console.error(error);
	process.exitCode = 1;
});
