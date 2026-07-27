const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const page = fs.readFileSync(path.resolve(__dirname, '..', 'pages', 'user', 'aboutmy.vue'), 'utf8');

assert.ok(page.includes('友情链接'));
assert.ok(page.includes('https://hub.aivis-project.com/'));
assert.ok(page.includes('https://voicevox.hiroshiba.jp/'));
assert.ok(page.includes('https://coeiroink.com/'));
assert.ok(page.includes('plus.runtime.openURL(link'));
assert.ok(page.includes("window.open(link, '_blank', 'noopener,noreferrer')"));
assert.ok(page.includes('this.copyExternalLink(link)'));
assert.ok(page.includes('请确认对应许可、署名和商用规则'));

console.log('friend links contract tests passed');
