const assert = require('assert');
const behavior = require('../common/chatScrollBehavior.js');

let state = behavior.decideChatScrollState(
	{ atBottom: true, followBottom: true, buttonVisible: false, upwardIntent: 0 },
	{ distanceToBottom: 80, upwardDelta: 4, userControlled: true }
);
assert.strictEqual(state.followBottom, true, 'tiny scroll noise must keep auto-follow enabled');
assert.strictEqual(state.atBottom, true, 'small movement must keep the composer visible');
assert.strictEqual(state.buttonVisible, false, 'tiny scroll noise must not show the bottom button');

state = behavior.decideChatScrollState(state, {
	distanceToBottom: 110,
	upwardDelta: 24,
	userControlled: true
});
assert.strictEqual(state.followBottom, true, 'intent alone must not detach before the outer threshold');
assert.strictEqual(state.atBottom, true, 'the composer must remain visible before the outer threshold');
assert.strictEqual(state.buttonVisible, false, 'the button must wait until the viewport is meaningfully away');

state = behavior.decideChatScrollState(state, {
	distanceToBottom: 175,
	upwardDelta: 772,
	userControlled: true
});
assert.strictEqual(state.buttonVisible, true, 'the button must show beyond the outer threshold');
assert.strictEqual(state.atBottom, false, 'the composer must be replaced only after both conditions are met');

state = behavior.decideChatScrollState(state, {
	distanceToBottom: 100,
	downwardDelta: 75,
	userControlled: true
});
assert.strictEqual(state.buttonVisible, true, 'hysteresis must keep the button stable in the middle band');

state = behavior.decideChatScrollState(state, {
	distanceToBottom: 48,
	downwardDelta: 52,
	userControlled: true
});
assert.deepStrictEqual(
	{ atBottom: state.atBottom, followBottom: state.followBottom, buttonVisible: state.buttonVisible },
	{ atBottom: true, followBottom: true, buttonVisible: false },
	'arriving near the bottom must restore follow mode and hide the button'
);

const reflow = behavior.decideChatScrollState(
	{ atBottom: true, followBottom: true, buttonVisible: false, upwardIntent: 0 },
	{ distanceToBottom: 220, upwardDelta: 80, userControlled: false }
);
assert.strictEqual(reflow.followBottom, true, 'content reflow must not be mistaken for user intent');
assert.strictEqual(reflow.buttonVisible, false, 'content reflow must not flash the bottom button');

const unknownDistance = behavior.decideChatScrollState(
	{ atBottom: true, followBottom: true, buttonVisible: false, upwardIntent: 0 },
	{ upwardDelta: 810, userControlled: true }
);
assert.strictEqual(unknownDistance.buttonVisible, true, 'platforms without height metrics still need a deliberate fallback');

console.log('chat scroll behavior unit tests passed');
