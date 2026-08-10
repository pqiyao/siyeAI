const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const page = fs.readFileSync(path.join(root, 'pages', 'tavern', 'inboxAds.vue'), 'utf8');

assert.doesNotMatch(page, /class="card"[\s\S]{0,120}@tap="openAd\(item\)"/);
assert.match(page, /@tap\.stop="previewAdImage\(item\)"/);
assert.match(page, /@tap\.stop="openAd\(item\)"/);
assert.match(page, /previewAdImage\(item\)[\s\S]*uni\.previewImage\(\{/);
assert.match(page, /fetchInboxAds\(50\)/);
assert.match(page, /markInboxAdsReadAll\(tavernApi\.getClientUid\(\)\)/);

console.log('inbox ads interaction contract passed');
