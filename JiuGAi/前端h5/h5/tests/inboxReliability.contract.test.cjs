const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.join(__dirname, '..');

function readBytes(relativePath) {
	return fs.readFileSync(path.join(root, relativePath));
}

function readUtf8Strict(relativePath, requireNoBom = false) {
	const bytes = readBytes(relativePath);
	if (requireNoBom) {
		assert(
			!(bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf),
			`${relativePath} must be UTF-8 without BOM`
		);
	}
	return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
}

for (const file of [
	'pages/tavern/sessionManage.vue',
	'pages/tavern/inboxAds.vue',
	'common/tavernInboxBadge.js',
	'pages/tavern/tavernInbox.vue',
	'pages/index/index.vue',
	'pages/chat/systemmsg.vue'
]) {
	readUtf8Strict(file);
}
readUtf8Strict('pages/tavern/sessionManage.vue', true);
readUtf8Strict('common/tavernInboxBadge.js', true);

const sessionManage = readUtf8Strict('pages/tavern/sessionManage.vue');
assert(!sessionManage.includes('\ufffd'), 'session manager must not contain Unicode replacement characters');
assert(sessionManage.includes('class="check-mark">✓</text>'), 'selected sessions must render a check mark');
assert(sessionManage.includes('return this.allText.酒馆页 || {};'), 'session manager must retain its locale lookup');
assert(sessionManage.includes('postTavernSessionDelete'), 'session manager must retain its delete API integration');

const sessionScriptMatch = sessionManage.match(/<script>([\s\S]*?)<\/script>/);
assert(sessionScriptMatch, 'session manager script block must exist');
const compilableSessionScript = sessionScriptMatch[1]
	.replace(/^\s*import[^\n]+;\s*$/gm, '')
	.replace(/\bexport default\b/, 'const sessionManageComponent =');
assert.doesNotThrow(
	() => new Function(compilableSessionScript),
	'session manager JavaScript must be syntactically valid'
);

for (const file of [
	'pages/tavern/inboxAds.vue',
	'pages/tavern/tavernInbox.vue',
	'pages/index/index.vue',
	'pages/chat/systemmsg.vue'
]) {
	const source = readUtf8Strict(file);
	const scriptMatch = source.match(/<script>([\s\S]*?)<\/script>/);
	assert(scriptMatch, `${file} script block must exist`);
	const compilableScript = scriptMatch[1]
		.replace(/^\s*import[^\n]+;\s*$/gm, '')
		.replace(/\bexport default\b/, 'const pageComponent =');
	assert.doesNotThrow(() => new Function(compilableScript), `${file} JavaScript must be syntactically valid`);
}

const inboxAdsPage = readUtf8Strict('pages/tavern/inboxAds.vue');
assert(
	inboxAdsPage.includes("plus.runtime.openURL(link, () => this.copyExternalLink(link))"),
	'APP external ads must open in the system browser and retain a copy fallback'
);
assert(
	/copyExternalLink\(link\)[\s\S]*?uni\.setClipboardData\(\{[\s\S]*?data:\s*link/.test(inboxAdsPage),
	'external ad fallback must actually copy the link'
);
const zhText = JSON.parse(readUtf8Strict('common/text/zh-cn.json'));
assert.strictEqual(
	zhText.\u9152\u9986\u9875.\u8bf7\u5728\u6d4f\u89c8\u5668\u6253\u5f00,
	'\u94fe\u63a5\u5df2\u590d\u5236\uff0c\u8bf7\u5728\u6d4f\u89c8\u5668\u4e2d\u7c98\u8d34\u6253\u5f00',
	'copy fallback must describe the completed action accurately'
);

function loadBadgeModule(noticeState, syncedTotals) {
	const source = readUtf8Strict('common/tavernInboxBadge.js');
	const module = { exports: {} };
	const context = {
		module,
		exports: module.exports,
		require(request) {
			if (request === '@/common/tavernNoticeState.js') return noticeState;
			if (request === '@/common/tavernTabBar.js') {
				return {
					syncTavernInboxBadge(_page, total) {
						syncedTotals.push(total);
					}
				};
			}
			throw new Error(`unexpected dependency: ${request}`);
		},
		Promise,
		Math,
		Number,
		String
	};
	vm.runInNewContext(source, context, { filename: 'tavernInboxBadge.js' });
	return module.exports;
}

async function runBadgeContracts() {
	let identity = 'user:101|client:h5u_101';
	let noticeUnread = 5;
	let adUnread = 7;
	let noticeFails = false;
	let adFails = false;
	const syncedTotals = [];
	const commits = [];
	const noticeState = {
		fetchUnreadState() {
			return noticeFails
				? Promise.reject(new Error('notice unavailable'))
				: Promise.resolve({ unreadCount: noticeUnread });
		}
	};
	const tavernApi = {
		getViewerIdentitySignature() {
			return identity;
		},
		getClientUid() {
			return identity.includes('202') ? 'h5u_202' : 'h5u_101';
		},
		jgEnabled() {
			return true;
		},
		fetchInboxAdsUnread() {
			return adFails
				? Promise.reject(new Error('ads unavailable'))
				: Promise.resolve({ unreadCount: adUnread });
		}
	};
	const page = {
		$store: {
			commit(name, value) {
				commits.push({ name, value });
			}
		}
	};
	const badge = loadBadgeModule(noticeState, syncedTotals);

	let state = await badge.refreshCombinedInboxBadge(page, tavernApi);
	assert.strictEqual(state.total, 12, 'successful components must be combined');

	noticeFails = true;
	adUnread = 3;
	state = await badge.refreshCombinedInboxBadge(page, tavernApi);
	assert.strictEqual(state.noticeUnread, 5, 'a failed notice request must keep its last trusted value');
	assert.strictEqual(state.adUnread, 3, 'the successful ad component must still refresh');
	assert.strictEqual(state.total, 8, 'a partial failure must not force the combined badge to zero');
	assert.strictEqual(syncedTotals.at(-1), 8, 'the visible badge must keep the trusted partial total');
	assert.strictEqual(commits.at(-1).value, 8, 'Vuex must receive the same trusted partial total');

	let resolveOldNotice;
	let resolveOldAd;
	noticeState.fetchUnreadState = () =>
		new Promise((resolve) => {
			resolveOldNotice = resolve;
		});
	tavernApi.fetchInboxAdsUnread = () =>
		new Promise((resolve) => {
			resolveOldAd = resolve;
		});
	const syncedBeforeStaleResponse = syncedTotals.length;
	const staleRefresh = badge.refreshCombinedInboxBadge(page, tavernApi);
	identity = 'user:202|client:h5u_202';
	resolveOldNotice({ unreadCount: 99 });
	resolveOldAd({ unreadCount: 88 });
	state = await staleRefresh;
	assert.strictEqual(state.stale, true, 'a response from the previous account must be marked stale');
	assert.strictEqual(
		syncedTotals.length,
		syncedBeforeStaleResponse,
		'a response from the previous account must not update the visible badge'
	);

	noticeState.fetchUnreadState = () => Promise.reject(new Error('notice unavailable'));
	tavernApi.fetchInboxAdsUnread = () => Promise.reject(new Error('ads unavailable'));
	identity = 'user:202|client:h5u_202';
	state = await badge.refreshCombinedInboxBadge(page, tavernApi);
	assert.strictEqual(state.total, 0, 'a new account must not inherit another account\'s cached unread state');
}

const inboxPage = readUtf8Strict('pages/tavern/tavernInbox.vue');
const noticeRefresh = inboxPage.slice(
	inboxPage.indexOf('\t\t\trefreshNoticeUnread() {'),
	inboxPage.indexOf('\t\t\trefreshAdUnread() {')
);
const adRefresh = inboxPage.slice(
	inboxPage.indexOf('\t\t\trefreshAdUnread() {'),
	inboxPage.indexOf('\t\t\tdisplayTitle(s) {')
);
assert(!/\.catch\([\s\S]*?this\.noticeUnread\s*=\s*0/.test(noticeRefresh), 'notice failures must preserve page state');
assert(!/\.catch\([\s\S]*?this\.adUnread\s*=\s*0/.test(adRefresh), 'ad failures must preserve page state');
assert(inboxPage.includes('isUnreadRequestCurrent'), 'inbox requests must reject stale identity responses');

const discoverPage = readUtf8Strict('pages/index/index.vue');
const discoverUnreadRefresh = discoverPage.slice(
	discoverPage.indexOf('\t\t\trefreshNoticeUnread() {'),
	discoverPage.indexOf('\t\t\tnormalizeNoticeDisplayType(value) {')
);
assert(
	!/\.catch\([\s\S]*?this\.noticeUnread\s*=\s*0/.test(discoverUnreadRefresh),
	'discover notice failures must preserve the last page value'
);
assert(
	discoverUnreadRefresh.includes('{ noticeUnread: this.noticeUnread }'),
	'discover notice failures must resync with the current trusted page value'
);
assert(
	discoverPage.includes('isNoticeUnreadRequestCurrent'),
	'discover notice requests must reject stale identity responses'
);
assert(
	discoverPage.includes('refreshCombinedInboxBadge(this, null, { noticeUnread: 0, adUnread: 0 })'),
	'an explicitly disabled backend must still clear the discover badge'
);

const systemMessagePage = readUtf8Strict('pages/chat/systemmsg.vue');
assert(
	/markAllAsRead\(tavernApi\)[\s\S]*?\.catch\(\(\) => \{[\s\S]*?refreshCombinedInboxBadge\(this, tavernApi\)/.test(
		systemMessagePage
	),
	'a failed mark-all request must re-read or retain the trusted combined badge'
);
assert(
	!/markAllAsRead\(tavernApi\)[\s\S]{0,700}noticeUnread:\s*0/.test(systemMessagePage),
	'a failed mark-all request must not claim that notices were read'
);
assert(
	systemMessagePage.includes('isLoadRequestCurrent'),
	'system-message requests must reject stale identity responses'
);
assert(
	/!tavernApi\.jgEnabled\(\)[\s\S]{0,500}noticeUnread:\s*0[\s\S]{0,100}adUnread:\s*0/.test(
		systemMessagePage
	),
	'an explicitly disabled backend must still clear the system-message badge'
);

runBadgeContracts()
	.then(() => console.log('inbox reliability contract passed'))
	.catch((error) => {
		console.error(error);
		process.exitCode = 1;
	});
