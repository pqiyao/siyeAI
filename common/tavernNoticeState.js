const LAST_READ_AT_KEY = 'jg_notice_last_read_at';
const UNREAD_STATE_CACHE_MS = 12000;

let unreadStateCacheKey = '';
let unreadStateCacheValue = null;
let unreadStateCacheAt = 0;
let unreadStatePendingKey = '';
let unreadStatePendingPromise = null;

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

function getLastReadAt() {
	try {
		const raw = uni.getStorageSync(LAST_READ_AT_KEY);
		const n = Number(raw);
		return isFinite(n) && n > 0 ? n : 0;
	} catch (e) {
		return 0;
	}
}

function setLastReadAt(value) {
	const next = toMillis(value);
	try {
		uni.setStorageSync(LAST_READ_AT_KEY, next);
	} catch (e) {}
	return next;
}

function getLatestCreatedAt(notices, messages) {
	return getRows(notices, messages).reduce((max, item) => {
		const current = toMillis(item && item.createdAt);
		return current > max ? current : max;
	}, 0);
}

function getUnreadCount(notices, messages) {
	const rows = getRows(notices, messages);
	if (!rows.length) return 0;
	const lastReadAt = getLastReadAt();
	if (!lastReadAt) return rows.length;
	return rows.reduce((count, item) => count + (toMillis(item && item.createdAt) > lastReadAt ? 1 : 0), 0);
}

function computeState(notices, messages) {
	const safeNotices = normalizeList(notices);
	const safeMessages = normalizeList(messages);
	return {
		notices: safeNotices,
		messages: safeMessages,
		unreadCount: getUnreadCount(safeNotices, safeMessages),
		latestCreatedAt: getLatestCreatedAt(safeNotices, safeMessages),
		lastReadAt: getLastReadAt()
	};
}

function buildUnreadStateCacheKey(tavernApi, limit) {
	if (!tavernApi) return 'none';
	const safeLimit = Number(limit) > 0 ? Math.floor(Number(limit)) : 30;
	const clientUid = typeof tavernApi.getClientUid === 'function' ? tavernApi.getClientUid() : '';
	return String(clientUid || '') + '|' + String(safeLimit);
}

function markAsRead(notices, messages) {
	const latestCreatedAt = getLatestCreatedAt(notices, messages);
	if (!latestCreatedAt) {
		return getLastReadAt();
	}
	return setLastReadAt(Math.max(getLastReadAt(), latestCreatedAt));
}

function fetchUnreadState(tavernApi, limit) {
	if (!tavernApi || typeof tavernApi.fetchAppNotices !== 'function' || typeof tavernApi.fetchUserMessages !== 'function') {
		return Promise.resolve(computeState([], []));
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
	unreadStatePendingPromise = Promise.all([
		tavernApi.fetchAppNotices(),
		tavernApi.fetchUserMessages(tavernApi.getClientUid(), limit || 30)
	]).then(function ([notices, messages]) {
		const state = computeState(notices, messages);
		unreadStateCacheKey = cacheKey;
		unreadStateCacheValue = state;
		unreadStateCacheAt = Date.now();
		return state;
	}).finally(function () {
		unreadStatePendingKey = '';
		unreadStatePendingPromise = null;
	});
	return unreadStatePendingPromise;
}

module.exports = {
	toMillis: toMillis,
	getLastReadAt: getLastReadAt,
	setLastReadAt: setLastReadAt,
	getLatestCreatedAt: getLatestCreatedAt,
	getUnreadCount: getUnreadCount,
	computeState: computeState,
	markAsRead: markAsRead,
	fetchUnreadState: fetchUnreadState
};
