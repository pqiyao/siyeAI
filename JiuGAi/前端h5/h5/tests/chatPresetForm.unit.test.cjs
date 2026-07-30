const assert = require('node:assert/strict');
const { fieldText, requiredNumber } = require('../common/chatPresetForm.js');

assert.equal(fieldText(0, 1), '0');
assert.equal(fieldText(null, 1), '1');
assert.equal(fieldText('', 512), '512');

assert.deepEqual(requiredNumber('0'), { valid: true, value: 0 });
assert.deepEqual(requiredNumber('-1.5'), { valid: true, value: -1.5 });
assert.equal(requiredNumber('').valid, false);
assert.equal(requiredNumber('   ').valid, false);
assert.equal(requiredNumber('not-a-number').valid, false);

console.log('chat preset form unit tests passed');
