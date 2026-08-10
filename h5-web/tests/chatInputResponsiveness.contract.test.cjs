const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');
const composer = read('components/tavern/chat-composer.vue');
const page = read('pages/tavern/tavernChat.vue');

assert(composer.includes('const INPUT_SYNC_DELAY_MS = 260;'), 'composer must batch rapid input updates');
assert(composer.includes(':value="localValue"'), 'native textarea must render from the lightweight local buffer');
assert(composer.includes('localValue: this.value == null ? \'\' : String(this.value)'), 'local buffer must initialize from the parent draft');
assert(composer.includes('pendingInputEcho'), 'parent echoes must not overwrite newer local typing');
assert(composer.includes('this.scheduleInputSync();'), 'ordinary input must use trailing synchronization');
assert(!/emitInput\(event\)[\s\S]{0,180}this\.\$emit\('input', value\)/.test(composer), 'ordinary input must not synchronously rerender the full chat page');
assert(/emitBlur\(event\)[\s\S]{0,100}this\.flushInputValue\(\)/.test(composer), 'blur must flush the latest input');
assert(/emitConfirm\(event\)[\s\S]{0,100}this\.flushInputValue\(\)/.test(composer), 'keyboard confirmation must flush before send');
assert(/emitPrimaryAction\(\)[\s\S]{0,100}this\.flushInputValue\(\)/.test(composer), 'send button must flush before send');
assert(/emitOpenExpressionPanel\(\)[\s\S]{0,100}this\.flushInputValue\(\)/.test(composer), 'expression actions must see the latest draft');

assert(page.includes('ref="chatComposer"'), 'chat page must retain a composer reference for lifecycle flushing');
assert(page.includes('flushComposerInput()'), 'chat page must expose lifecycle-safe input flushing');
assert(/onHide\(\)[\s\S]{0,180}this\.flushComposerInput\(\);[\s\S]{0,80}this\.flushDraftSave\(\)/.test(page), 'backgrounding must flush local input before draft persistence');
assert(/onUnload\(\)[\s\S]{0,180}this\.flushComposerInput\(\)/.test(page), 'unloading must flush local input before teardown');

console.log('chat input responsiveness contract tests passed');
