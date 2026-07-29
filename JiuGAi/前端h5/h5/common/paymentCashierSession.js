const STORAGE_PREFIX = 'jg_payment_cashier_';
const SESSION_TTL_MS = 30 * 60 * 1000;

function normalizeOrderNo(orderNo) {
	return String(orderNo || '').trim();
}

function storageKey(orderNo) {
	return STORAGE_PREFIX + normalizeOrderNo(orderNo);
}

function isSafePaymentUrl(value) {
	const url = String(value || '').trim();
	if (!/^https:\/\//i.test(url) || /[\r\n]/.test(url)) return false;
	try {
		const parsed = new URL(url);
		return parsed.protocol === 'https:' && !!parsed.hostname && !parsed.username && !parsed.password;
	} catch (e) {
		return /^https:\/\/[^\s/?#]+(?:[/?#][^\s]*)?$/i.test(url);
	}
}

function save(orderNo, paymentUrl) {
	const normalizedOrderNo = normalizeOrderNo(orderNo);
	const normalizedPaymentUrl = String(paymentUrl || '').trim();
	if (!normalizedOrderNo) throw new Error('Missing payment order number');
	if (!isSafePaymentUrl(normalizedPaymentUrl)) throw new Error('Invalid payment URL');
	const session = {
		orderNo: normalizedOrderNo,
		paymentUrl: normalizedPaymentUrl,
		createdAt: Date.now()
	};
	uni.setStorageSync(storageKey(normalizedOrderNo), session);
	return session;
}

function load(orderNo) {
	const normalizedOrderNo = normalizeOrderNo(orderNo);
	if (!normalizedOrderNo) return null;
	const session = uni.getStorageSync(storageKey(normalizedOrderNo));
	if (!session || typeof session !== 'object') return null;
	if (normalizeOrderNo(session.orderNo) !== normalizedOrderNo || !isSafePaymentUrl(session.paymentUrl)) {
		clear(normalizedOrderNo);
		return null;
	}
	const createdAt = Number(session.createdAt) || 0;
	if (!createdAt || Date.now() - createdAt > SESSION_TTL_MS) {
		clear(normalizedOrderNo);
		return null;
	}
	return {
		orderNo: normalizedOrderNo,
		paymentUrl: String(session.paymentUrl),
		createdAt
	};
}

function clear(orderNo) {
	const normalizedOrderNo = normalizeOrderNo(orderNo);
	if (normalizedOrderNo) uni.removeStorageSync(storageKey(normalizedOrderNo));
}

module.exports = {
	clear,
	isSafePaymentUrl,
	load,
	save,
	storageKey
};
