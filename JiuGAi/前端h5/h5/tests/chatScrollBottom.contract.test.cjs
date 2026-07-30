const assert = require('assert');
const fs = require('fs');
const path = require('path');

const projectRoot = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(projectRoot, relativePath), 'utf8');
const page = read('pages/tavern/tavernChat.vue');
const composer = read('components/tavern/chat-composer.vue');

assert(page.includes("const chatScrollBehavior = require('@/common/chatScrollBehavior.js');"), 'chat page must use the tested scroll state helper');
assert(page.includes(':at-chat-bottom="atChatBottom"'), 'the composer must receive the actual viewport bottom state');
assert(page.includes('@scroll-bottom="requestChatScrollToBottom"'), 'bottom button must use the animated arrival flow');
assert(page.includes('this.scrollChatToBottom({ immediate: false, deferArrival: true });'), 'manual return must animate and defer state settlement');
assert(page.includes('chatScrollBehavior.decideChatScrollState('), 'scroll events must use hysteresis and accumulated intent');
assert(!/onPrimaryAction\(\)\s*\{\s*if \(!this\.atChatBottom\)/.test(page), 'viewport state must not leak back into the send command implementation');
assert(!/submitOutgoingMessage\(rawText, rawImageUrls, options\)[\s\S]{0,500}if \(!this\.atChatBottom/.test(page), 'viewport state must not become a chat submission business rule');

assert(composer.includes('v-if="atChatBottom" class="composer-stack"'), 'the input row must be removed after leaving the bottom');
assert(composer.includes('v-else class="scroll-bottom-pill"'), 'the original full-width return pill must replace the input row');
assert(composer.includes('v-if="atChatBottom"\n\t\t\tclass="send send--icon"'), 'the send button must be removed while the return pill is visible');
assert(composer.includes('.chat-composer .scroll-bottom-pill {'), 'the original pill styling must remain in the composer');
assert(!composer.includes('scroll-bottom-float'), 'the clipped floating implementation must not return');
assert(page.includes('const manualTowardBottom = downwardDelta > 2'), 'only an actual downward return gesture may restore the input row');
assert(page.includes('const preserveDetachedState = nextState.arrivedAtBottom && !this.followBottom;'), 'layout reflow must not cancel the detached bottom action');
assert(page.includes(".select('.chat-scroll')"), 'the page must measure the real scroll viewport height');
assert(page.includes('const measuredClientHeight = Number(this.chatViewportHeight);'), 'missing event clientHeight must use the measured viewport');
assert(page.includes('const firstManualAway = hasTop && !hasPreviousTop'), 'the first manual scroll event must not be discarded');
assert(page.includes('distanceToBottom >= chatScrollBehavior.BOTTOM_SHOW_DISTANCE'), 'the first scroll must respect the outer display threshold');
assert(page.includes("jgChatLoadState(value)"), 'the viewport must be measured after the async chat view is rendered');
assert(page.includes("if (value === 'ready')"), 'chat readiness must trigger the deferred viewport measurement');

console.log('chat scroll bottom contract tests passed');
