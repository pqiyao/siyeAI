const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

const api = read('common/tavernApi.js');
const pay = read('pages/user/pay.vue');
const money = read('pages/user/mymoney.vue');
const user = read('pages/user/user.vue');
const vip = read('pages/user/myvip.vue');

assert.match(api, /function postStoreOrderRemove\(payload\)/);
assert.match(api, /postStoreOrderRemove: postStoreOrderRemove/);

const loadPageBlock = pay.match(/loadPage\(\) \{([\s\S]*?)\n\t\t\},\n\t\tpickDefaultChannel/);
assert(loadPageBlock, 'payment loadPage block must exist');
assert.doesNotMatch(loadPageBlock[1], /createOrder\(/, 'opening payment page must not create an order');

const selectChannelBlock = pay.match(/selectChannel\(code\) \{([\s\S]*?)\n\t\t\},\n\t\tcreateOrder/);
assert(selectChannelBlock, 'selectChannel block must exist');
assert.doesNotMatch(selectChannelBlock[1], /createOrder\(/, 'switching channels must not create an order');
assert.match(pay, /if \(!this\.order\.orderNo\) \{\s*this\.createOrder\(true\);/);
assert.doesNotMatch(pay, /@tap="rebuildOrder"/);

assert.match(money, /@tap\.stop="confirmRemoveOrders\(\[item\.orderNo\]\)"/);
assert.match(money, /toggleAllRemovableOrders/);
assert.match(money, /postStoreOrderRemove\(\{ clientUid, orderNo \}\)/);
assert.match(money, /order-list-scroll--limited': orderList\.length > 4/);
assert.match(money, /String\(item\.status \|\| ''\)\.toUpperCase\(\) !== 'PAID'/);

for (const source of [user, money, vip, pay]) {
	const syncBlock = source.match(/syncRechargeEntryVisibility\(forceRefresh\) \{([\s\S]*?)fetchAppRuntimeConfig/);
	assert(syncBlock, 'recharge visibility sync block must exist');
	assert.doesNotMatch(syncBlock[1], /rechargeEntryReady\s*=\s*false/, 'background refresh must not hide the recharge entry');
}

console.log('payment order management contract passed');
