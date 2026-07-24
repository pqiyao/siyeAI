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

console.log('local media store contract tests passed');
