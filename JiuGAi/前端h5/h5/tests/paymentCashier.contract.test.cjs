const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const storage = new Map();

global.uni = {
	getStorageSync(key) {
		return storage.get(key);
	},
	setStorageSync(key, value) {
		storage.set(key, value);
	},
	removeStorageSync(key) {
		storage.delete(key);
	}
};

const session = require('../common/paymentCashierSession.js');

assert.strictEqual(session.isSafePaymentUrl('https://zpayz.cn/submit.php?sign=test'), true);
assert.strictEqual(session.isSafePaymentUrl('http://zpayz.cn/submit.php'), false);
assert.strictEqual(session.isSafePaymentUrl('javascript:alert(1)'), false);
assert.strictEqual(session.isSafePaymentUrl('https://user:pass@zpayz.cn/pay'), false);

const saved = session.save('ORDER-1', 'https://zpayz.cn/submit.php?sign=test');
assert.strictEqual(saved.orderNo, 'ORDER-1');
assert.strictEqual(session.load('ORDER-1').paymentUrl, saved.paymentUrl);
session.clear('ORDER-1');
assert.strictEqual(session.load('ORDER-1'), null);

storage.set(session.storageKey('ORDER-2'), {
	orderNo: 'ORDER-2',
	paymentUrl: 'https://zpayz.cn/submit.php?sign=test',
	createdAt: Date.now() - 31 * 60 * 1000
});
assert.strictEqual(session.load('ORDER-2'), null, 'expired payment sessions must be rejected');

const payPage = fs.readFileSync(path.join(root, 'pages/user/pay.vue'), 'utf8');
assert(payPage.includes('/* #ifdef APP-PLUS */\n\t\t\treturn this.openInAppPayment(paymentUrl);'));
assert(payPage.includes("paymentCashierSession.save(orderNo, paymentUrl)"));
assert(payPage.includes("'/pages/user/paymentCashier?orderNo='"));

const cashierPage = fs.readFileSync(path.join(root, 'pages/user/paymentCashier.vue'), 'utf8');
assert(cashierPage.includes('<web-view v-if="paymentUrl" :src="paymentUrl"></web-view>'));
assert(cashierPage.includes('.fetchStoreOrders(tavernApi.getClientUid(), 50)'));
assert(cashierPage.includes("if (status === 'PAID')"));
assert(cashierPage.includes("markCharacterAccessRefreshNeeded('app-payment-paid')"));
assert(cashierPage.includes("match: '^(alipays|alipay|weixin)://.*'"));
assert(cashierPage.includes('plus.runtime.openURL(targetUrl'));

const pages = JSON.parse(fs.readFileSync(path.join(root, 'pages.json'), 'utf8'));
const route = pages.pages.find((item) => item.path === 'pages/user/paymentCashier');
assert(route, 'the in-app cashier route must be registered');
assert.strictEqual(route.style.navigationStyle, 'default');

console.log('payment cashier contract passed');
