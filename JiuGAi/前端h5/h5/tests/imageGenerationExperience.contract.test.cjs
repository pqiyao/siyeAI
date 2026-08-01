const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const chat = fs.readFileSync(path.join(root, 'pages', 'tavern', 'tavernChat.vue'), 'utf8');
const api = fs.readFileSync(path.join(root, 'common', 'tavernApi.js'), 'utf8');

assert.match(chat, /characterImagePanel\.consistencyMode === 'free'/);
assert.match(chat, /characterImagePanel\.consistencyMode === 'balanced'/);
assert.match(chat, /characterImagePanel\.consistencyMode === 'strong'/);
assert.match(chat, /image-quick-segment--disabled': !isCharacterImageStrongModeAvailable\(\)/);
assert.match(chat, /characterImagePanel\.aspectRatio === 'portrait'/);
assert.match(chat, /characterImagePanel\.aspectRatio === 'square'/);
assert.match(chat, /characterImagePanel\.aspectRatio === 'landscape'/);
assert.match(chat, /if \(!customMode && consistencyMode === 'strong'\)/);
assert.match(chat, /requestFingerprint/);
assert.match(chat, /previousRequestId && previousFingerprint === requestFingerprint/);
assert.match(chat, /postCharacterImageWithRecovery\(tavernApi, payload\)/);
assert.match(chat, /fetchImageGenerateResult\(tavernApi\.getClientUid\(\), imageRequestId\)/);
assert.match(api, /requestJson\('POST', '\/api\/v1\/image\/generate', payload, 135000\)/);
assert.match(api, /requestJson\('GET', '\/api\/v1\/image\/result' \+ query, null, 15000\)/);
assert.match(api, /persistGeneratedRemoteImageViaUni\(resolvedRemoteUrl, persistOptions\)/);
assert.match(api, /var resolvedRemoteUrl = resolveJgAssetUrl\(safeUrl\)/);
assert.match(api, /persistOptions\.requirePersisted === true/);
assert.match(chat, /requirePersisted: this\.isAppPlus/);
assert.match(chat, /@preview-image="previewChatMessageImages\(m, \$event\)"/);
assert.match(chat, /previewChatMessageImages\(message, index\)[\s\S]*?uni\.previewImage\(/);
assert.match(chat, /characterImageGlobalState\.imageCanUse === false/);

console.log('image generation experience contract tests passed');
