const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');

function read(relativePath) {
	const bytes = fs.readFileSync(path.join(root, relativePath));
	return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
}

function assertVueScriptSyntax(relativePath) {
	const source = read(relativePath);
	const scriptMatch = source.match(/<script>([\s\S]*?)<\/script>/);
	assert(scriptMatch, `${relativePath} script block must exist`);
	const compilableScript = scriptMatch[1]
		.replace(/^\s*import[^\n]+;\s*$/gm, '')
		.replace(/\bexport default\b/, 'const pageComponent =');
	assert.doesNotThrow(
		() => new Function(compilableScript),
		`${relativePath} JavaScript must be syntactically valid`
	);
	return source;
}

function loadVueComponent(relativePath, dependencies) {
	const source = read(relativePath);
	const scriptMatch = source.match(/<script>([\s\S]*?)<\/script>/);
	assert(scriptMatch, `${relativePath} script block must exist`);
	const executableScript = scriptMatch[1]
		.replace(/^\s*import[^\n]+;\s*$/gm, '')
		.replace(/\bexport default\b/, 'const pageComponent =');
	const factory = new Function(
		'require',
		`const TavernNavBar = {};\n${executableScript}\nreturn pageComponent;`
	);
	return factory((request) => {
		if (Object.prototype.hasOwnProperty.call(dependencies, request)) return dependencies[request];
		throw new Error(`unexpected dependency: ${request}`);
	});
}

const api = read('common/tavernApi.js');
assert(
	api.includes('rechargeEntryVisible: raw.rechargeEntryVisible !== false'),
	'a missing runtime field must keep recharge entries visible for backward compatibility'
);
assert(api.includes('function isRechargeEntryVisible()'), 'the runtime switch must expose an accessor');
assert(api.includes('isRechargeEntryVisible: isRechargeEntryVisible'), 'the recharge accessor must be exported');

const user = assertVueScriptSyntax('pages/user/user.vue');
assert(
	user.includes('v-if="rechargeEntryReady && rechargeEntryVisible" class="wallet-main-btn"'),
	'the user-center recharge button must wait for the runtime switch and obey it'
);
assert(user.includes('syncRechargeEntryVisibility(true)'), 'the user center must refresh the switch on show');
assert(
	user.includes('if (!this.rechargeEntryReady || !this.rechargeEntryVisible) return;'),
	'the hidden user-center entry must also have an action guard'
);

const money = assertVueScriptSyntax('pages/user/mymoney.vue');
assert(
	money.includes('v-if="rechargeEntryReady && rechargeEntryVisible"'),
	'wallet purchase actions must stay hidden until the enabled state is confirmed'
);
assert(
	money.includes('if (!this.rechargeEntryReady || !this.rechargeEntryVisible) return;'),
	'wallet navigation must reject hidden purchase actions'
);
assert(
	/class="balance-card"[\s\S]*?profile\.score[\s\S]*?profile\.goldCoin/.test(money),
	'wallet balances must remain visible independently of purchase actions'
);

const vip = assertVueScriptSyntax('pages/user/myvip.vue');
assert(
	vip.includes('v-if="rechargeEntryReady && rechargeEntryVisible"'),
	'VIP purchase actions must stay hidden until the enabled state is confirmed'
);
assert(
	(vip.match(/v-if="rechargeEntryReady && rechargeEntryVisible"/g) || []).length >= 2,
	'the VIP product and purchase sections must both disappear while purchases are disabled'
);
assert(!vip.includes('v-if="rechargeEntryReady && !rechargeEntryVisible" class="project-notice-mask"'), 'current VIP benefits must remain readable while purchases are disabled');
assert(/onShow\(\) \{[\s\S]{0,160}this\.loadPage\(\)/.test(vip), 'the commercial VIP page must load real products');

assert(
	(money.match(/v-if="rechargeEntryReady && rechargeEntryVisible"/g) || []).length >= 3,
	'the wallet package, note, and purchase controls must disappear while purchases are disabled'
);

const pay = assertVueScriptSyntax('pages/user/pay.vue');
assert(
	pay.includes('v-if="rechargeEntryReady && rechargeEntryVisible" class="action-stack"'),
	'payment actions must render only when the switch is confirmed enabled'
);
assert(
	pay.includes('v-if="rechargeEntryReady && !rechargeEntryVisible" class="project-notice-mask"'),
	'the disabled payment route must keep the project-notice fallback'
);
assert(
	/if \(this\.rechargeEntryVisible\) \{[\s\S]{0,100}this\.loadPage\(\);[\s\S]{0,100}else \{[\s\S]{0,100}this\.initProjectShell\(\);/.test(
		pay
	),
	'the enabled state must load the real payment flow while the disabled state clears it'
);
assert((pay.match(/if \(!this\.canPurchase\(\)\) return;/g) || []).length >= 4, 'payment mutations must have runtime guards');
assert(pay.includes('<text class="summary-price">¥{{'), 'the payment price must render with the correct currency symbol');

async function verifyPaymentRuntimeSwitch(config) {
	const apiMock = {
		isRechargeEntryVisible() {
			return true;
		},
		fetchAppRuntimeConfig() {
			return Promise.resolve(config);
		}
	};
	const component = loadVueComponent('pages/user/pay.vue', {
		'@/common/tavernApi.js': apiMock,
		'@/common/tavernUiI18n.js': {
			getLanguageCode: () => 'zh-cn',
			getTavernUiText: () => ({})
		},
		'@/common/tavernProjectNotice.js': {
			getProjectNoticeCopy: () => ({})
		}
	});
	const state = component.data();
	const calls = { load: 0, clear: 0 };
	const page = Object.assign({}, state, {
		loadPage() {
			calls.load += 1;
		},
		initProjectShell() {
			calls.clear += 1;
		}
	});
	const visible = await component.methods.syncRechargeEntryVisibility.call(page, true);
	return { visible, page, calls };
}

const tavernErrors = require('../common/tavernErrors.js');
const enabledPrompt = tavernErrors.resolveCommercialPrompt('生图额度不足');
assert.strictEqual(enabledPrompt.secondaryUrl, '/pages/user/pay', 'enabled prompts may offer recharge');
const disabledPrompt = tavernErrors.resolveCommercialPrompt('生图额度不足', {
	rechargeEntryVisible: false
});
assert.strictEqual(disabledPrompt.secondaryText, '', 'disabled prompts must remove recharge copy');
assert.strictEqual(disabledPrompt.secondaryUrl, '', 'disabled prompts must remove recharge navigation');
assert(!disabledPrompt.message.includes('充值后'), 'disabled prompts must not advertise recharging as a way to continue');
assert(disabledPrompt.message.includes('入口暂未开放'), 'disabled prompts must explain that purchases are unavailable');
assert.strictEqual(disabledPrompt.primaryText, '联系客服', 'disabled prompts must not advertise an unavailable membership purchase');
assert.strictEqual(
	disabledPrompt.primaryUrl,
	'/pages/user/lianxiwomen/lianxiwomen',
	'disabled prompts must route to support instead of an unavailable purchase page'
);

const notice = require('../common/tavernProjectNotice.js').getProjectNoticeCopy('zh-cn');
assert(!notice.message.includes('非商用'), 'the runtime-disabled notice must not contradict commercial deployment');
assert(notice.message.includes('暂不开放充值'), 'the runtime-disabled notice must explain the temporary switch state');
assert.notStrictEqual(
	require('../common/tavernProjectNotice.js').getProjectNoticeCopy('ko').title,
	notice.title,
	'the Korean purchase-disabled notice must not fall back to Chinese'
);
assert.notStrictEqual(
	require('../common/tavernProjectNotice.js').getProjectNoticeCopy('ja').title,
	notice.title,
	'the Japanese purchase-disabled notice must not fall back to Chinese'
);

const chat = assertVueScriptSyntax('pages/tavern/tavernChat.vue');
assert(
	chat.includes('rechargeEntryVisible: this.rechargeEntryVisible !== false'),
	'chat error resolution must receive the live recharge switch'
);
assert(
	chat.includes("showRechargeEntry ? (data.secondaryUrl || '/pages/user/pay') : ''"),
	'chat prompts must strip fallback recharge routes when disabled'
);
assert(
	chat.includes("this.commercialPrompt.kind !== 'chat_quota'") &&
		chat.includes("primaryUrl: '/pages/user/lianxiwomen/lianxiwomen'"),
	'a runtime disable must replace an already-visible purchase action with customer support'
);

Promise.all([
	verifyPaymentRuntimeSwitch({}),
	verifyPaymentRuntimeSwitch({ rechargeEntryVisible: false })
])
	.then(([enabled, disabled]) => {
		assert.strictEqual(enabled.visible, true, 'a missing field must enable the commercial payment flow');
		assert.strictEqual(enabled.calls.load, 1, 'an enabled payment route must load real products and channels');
		assert.strictEqual(enabled.calls.clear, 0, 'an enabled payment route must not activate the fallback shell');
		assert.strictEqual(disabled.visible, false, 'an explicit false value must disable payment entry');
		assert.strictEqual(disabled.calls.load, 0, 'a disabled payment route must not load the payment flow');
		assert.strictEqual(disabled.calls.clear, 1, 'a disabled payment route must activate its fallback shell');
		console.log('recharge entry contract passed');
	})
	.catch((error) => {
		console.error(error);
		process.exitCode = 1;
	});
