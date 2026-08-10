/**
 * 会话 Tab 角标 = 系统公告/站内信未读 + 活动推荐未读。
 * 全站写角标必须走这里，避免某一页只写公告未读把广告未读冲掉。
 */
const tavernNoticeState = require('@/common/tavernNoticeState.js');
const { syncTavernInboxBadge } = require('@/common/tavernTabBar.js');

let activeIdentityKey = '';
let lastKnownNoticeUnread = null;
let lastKnownAdUnread = null;

function toCount(value) {
	return Math.max(0, Number(value) || 0);
}

function getIdentityKey(tavernApi) {
	if (!tavernApi) return 'none';
	try {
		if (typeof tavernApi.getViewerIdentitySignature === 'function') {
			const signature = String(tavernApi.getViewerIdentitySignature() || '').trim();
			if (signature) return signature;
		}
		if (typeof tavernApi.getClientUid === 'function') {
			return 'client:' + String(tavernApi.getClientUid() || '');
		}
	} catch (e) {}
	return 'unknown';
}

function activateIdentity(identityKey) {
	if (activeIdentityKey === identityKey) return;
	activeIdentityKey = identityKey;
	lastKnownNoticeUnread = null;
	lastKnownAdUnread = null;
}

function rememberUnread(identityKey, part, value) {
	if (activeIdentityKey !== identityKey) return;
	if (part === 'notice') {
		lastKnownNoticeUnread = toCount(value);
		return;
	}
	lastKnownAdUnread = toCount(value);
}

function getLastKnownUnread(identityKey, part) {
	if (activeIdentityKey !== identityKey) return 0;
	const value = part === 'notice' ? lastKnownNoticeUnread : lastKnownAdUnread;
	return value == null ? 0 : toCount(value);
}

function fetchNoticeUnread(tavernApi, limit) {
	const identityKey = getIdentityKey(tavernApi);
	activateIdentity(identityKey);
	if (!tavernApi || typeof tavernNoticeState.fetchUnreadState !== 'function') {
		rememberUnread(identityKey, 'notice', 0);
		return Promise.resolve(0);
	}
	const safeLimit = Number(limit) > 0 ? Math.floor(Number(limit)) : 30;
	return tavernNoticeState
		.fetchUnreadState(tavernApi, safeLimit)
		.then(function (state) {
			const unreadCount = toCount(state && state.unreadCount);
			if (getIdentityKey(tavernApi) === identityKey) {
				rememberUnread(identityKey, 'notice', unreadCount);
			}
			return unreadCount;
		})
		.catch(function () {
			return getLastKnownUnread(identityKey, 'notice');
		});
}

function fetchAdUnread(tavernApi) {
	const identityKey = getIdentityKey(tavernApi);
	activateIdentity(identityKey);
	if (
		!tavernApi ||
		typeof tavernApi.jgEnabled !== 'function' ||
		!tavernApi.jgEnabled() ||
		typeof tavernApi.fetchInboxAdsUnread !== 'function'
	) {
		rememberUnread(identityKey, 'ad', 0);
		return Promise.resolve(0);
	}
	return tavernApi
		.fetchInboxAdsUnread(tavernApi.getClientUid())
		.then(function (data) {
			const unreadCount = toCount(data && data.unreadCount);
			if (getIdentityKey(tavernApi) === identityKey) {
				rememberUnread(identityKey, 'ad', unreadCount);
			}
			return unreadCount;
		})
		.catch(function () {
			return getLastKnownUnread(identityKey, 'ad');
		});
}

/**
 * @param {object} vm 页面实例（可空，仅更新本地 storage / 原生 tab 时）
 * @param {object} tavernApi
 * @param {object} [options]
 * @param {number} [options.noticeUnread] 已知公告未读，跳过再拉
 * @param {number} [options.adUnread] 已知广告未读，跳过再拉
 * @param {number} [options.noticeLimit]
 * @param {boolean} [options.commitStore=true] 是否写入 vuex unreadTotal（写入合计）
 * @returns {Promise<{noticeUnread:number,adUnread:number,total:number}>}
 */
function refreshCombinedInboxBadge(vm, tavernApi, options) {
	const opts = options && typeof options === 'object' ? options : {};
	const identityKey = getIdentityKey(tavernApi);
	activateIdentity(identityKey);
	const noticePromise =
		opts.noticeUnread != null
			? Promise.resolve(toCount(opts.noticeUnread))
			: fetchNoticeUnread(tavernApi, opts.noticeLimit);
	const adPromise =
		opts.adUnread != null ? Promise.resolve(toCount(opts.adUnread)) : fetchAdUnread(tavernApi);

	return Promise.all([noticePromise, adPromise]).then(function (parts) {
		const currentIdentityKey = getIdentityKey(tavernApi);
		if (currentIdentityKey !== identityKey) {
			activateIdentity(currentIdentityKey);
			const currentNoticeUnread = getLastKnownUnread(currentIdentityKey, 'notice');
			const currentAdUnread = getLastKnownUnread(currentIdentityKey, 'ad');
			return {
				noticeUnread: currentNoticeUnread,
				adUnread: currentAdUnread,
				total: currentNoticeUnread + currentAdUnread,
				stale: true
			};
		}
		const noticeUnread = toCount(parts[0]);
		const adUnread = toCount(parts[1]);
		const total = noticeUnread + adUnread;
		rememberUnread(identityKey, 'notice', noticeUnread);
		rememberUnread(identityKey, 'ad', adUnread);
		if (opts.commitStore !== false && vm && vm.$store && typeof vm.$store.commit === 'function') {
			try {
				vm.$store.commit('setUnreadTotal', total);
			} catch (e) {}
		}
		syncTavernInboxBadge(vm, total);
		return {
			noticeUnread: noticeUnread,
			adUnread: adUnread,
			total: total
		};
	});
}

module.exports = {
	toCount: toCount,
	fetchNoticeUnread: fetchNoticeUnread,
	fetchAdUnread: fetchAdUnread,
	refreshCombinedInboxBadge: refreshCombinedInboxBadge
};
