const UNREAD_STATE_CACHE_MS = 12000;

let unreadStateCacheKey = '';
let unreadStateCacheValue = null;
let unreadStateCacheAt = 0;
let unreadStatePendingKey = '';
let unreadStatePendingPromise = null;
let unreadStateVersion = 0;

function toMillis(value) {
	if (value == null || value === '') return 0;
	if (typeof value === 'number') {
		return isFinite(value) && value > 0 ? value : 0;
	}
	const ts = new Date(value).getTime();
	return isNaN(ts) ? 0 : ts;
}

function normalizeList(list) {
	return Array.isArray(list) ? list.filter(Boolean) : [];
}

function getRows(notices, messages) {
	return normalizeList(notices).concat(normalizeList(messages));
}

function getLatestCreatedAt(notices, messages) {
	return getRows(notices, messages).reduce((max, item) => {
		const current = toMillis(item && item.createdAt);
		return current > max ? current : max;
	}, 0);
}

function normalizeUnreadState(data) {
	data = data && typeof data === 'object' ? data : {};
	const noticeUnread = Math.max(0, Number(data.noticeUnread) || 0);
	const messageUnread = Math.max(0, Number(data.messageUnread) || 0);
	const unreadCount =
		data.unreadCount == null
			? noticeUnread + messageUnread
			: Math.max(0, Number(data.unreadCount) || 0);
	return {
		notices: normalizeList(data.notices),
		messages: normalizeList(data.messages),
		noticeUnread: noticeUnread,
		messageUnread: messageUnread,
		unreadCount: unreadCount,
		latestCreatedAt: toMillis(data.latestCreatedAt),
		lastReadAt: 0
	};
}

function clearUnreadStateCache() {
	unreadStateVersion += 1;
	unreadStateCacheKey = '';
	unreadStateCacheValue = null;
	unreadStateCacheAt = 0;
	unreadStatePendingKey = '';
	unreadStatePendingPromise = null;
}

function buildUnreadStateCacheKey(tavernApi, limit) {
	if (!tavernApi) return 'none';
	const safeLimit = Number(limit) > 0 ? Math.floor(Number(limit)) : 30;
	const clientUid = typeof tavernApi.getClientUid === 'function' ? tavernApi.getClientUid() : '';
	return String(clientUid || '') + '|' + String(safeLimit);
}

function fetchUnreadState(tavernApi, limit) {
	if (!tavernApi || typeof tavernApi.fetchInboxUnreadState !== 'function') {
		return Promise.resolve(normalizeUnreadState({ unreadCount: 0 }));
	}
	const cacheKey = buildUnreadStateCacheKey(tavernApi, limit);
	if (
		unreadStateCacheValue &&
		unreadStateCacheKey === cacheKey &&
		unreadStateCacheAt > 0 &&
		Date.now() - unreadStateCacheAt < UNREAD_STATE_CACHE_MS
	) {
		return Promise.resolve(unreadStateCacheValue);
	}
	if (unreadStatePendingPromise && unreadStatePendingKey === cacheKey) {
		return unreadStatePendingPromise;
	}
	unreadStatePendingKey = cacheKey;
	const requestVersion = unreadStateVersion;
	unreadStatePendingPromise = tavernApi
		.fetchInboxUnreadState(tavernApi.getClientUid())
		.then(function (data) {
			if (requestVersion !== unreadStateVersion) {
				return normalizeUnreadState({ unreadCount: 0 });
			}
			const state = normalizeUnreadState(data);
			unreadStateCacheKey = cacheKey;
			unreadStateCacheValue = state;
			unreadStateCacheAt = Date.now();
			return state;
		})
		.finally(function () {
			unreadStatePendingKey = '';
			unreadStatePendingPromise = null;
		});
	return unreadStatePendingPromise;
}

function markAllAsRead(tavernApi) {
	clearUnreadStateCache();
	if (!tavernApi || typeof tavernApi.markInboxReadAll !== 'function') {
		return Promise.resolve(normalizeUnreadState({ unreadCount: 0 }));
	}
	return tavernApi.markInboxReadAll(tavernApi.getClientUid()).then(function (data) {
		clearUnreadStateCache();
		return normalizeUnreadState(data);
	});
}

function markAsRead() {
	clearUnreadStateCache();
	return 0;
}

module.exports = {
	toMillis: toMillis,
	getLastReadAt: function () {
		return 0;
	},
	setLastReadAt: function () {
		clearUnreadStateCache();
		return 0;
	},
	getLatestCreatedAt: getLatestCreatedAt,
	getUnreadCount: function () {
		return 0;
	},
	computeState: normalizeUnreadState,
	markAsRead: markAsRead,
	markAllAsRead: markAllAsRead,
	clearUnreadStateCache: clearUnreadStateCache,
	fetchUnreadState: fetchUnreadState
};
