var DEFAULT_INITIAL_VISIBLE = 8;
var DEFAULT_BATCH_SIZE = 8;

function toPositiveInt(value, fallback) {
	var n = Number(value);
	if (!isFinite(n) || n <= 0) {
		return fallback;
	}
	return Math.floor(n);
}

function getInitialVisibleCount(total, initialVisible) {
	var safeTotal = Math.max(0, Math.floor(Number(total) || 0));
	if (!safeTotal) {
		return 0;
	}
	var safeInitial = toPositiveInt(initialVisible, DEFAULT_INITIAL_VISIBLE);
	return Math.min(safeTotal, safeInitial);
}

function syncVisibleCount(currentVisible, total, initialVisible) {
	var safeTotal = Math.max(0, Math.floor(Number(total) || 0));
	if (!safeTotal) {
		return 0;
	}
	var safeInitial = getInitialVisibleCount(safeTotal, initialVisible);
	var safeCurrent = toPositiveInt(currentVisible, safeInitial);
	if (safeCurrent < safeInitial) {
		safeCurrent = safeInitial;
	}
	if (safeCurrent > safeTotal) {
		safeCurrent = safeTotal;
	}
	return safeCurrent;
}

function sliceVisibleList(list, currentVisible, initialVisible) {
	if (!Array.isArray(list) || !list.length) {
		return [];
	}
	var visibleCount = syncVisibleCount(currentVisible, list.length, initialVisible);
	return list.slice(0, visibleCount);
}

function hasMoreItems(list, currentVisible, initialVisible) {
	if (!Array.isArray(list) || !list.length) {
		return false;
	}
	var visibleCount = syncVisibleCount(currentVisible, list.length, initialVisible);
	return visibleCount < list.length;
}

function expandVisibleCount(currentVisible, total, batchSize, initialVisible) {
	var safeTotal = Math.max(0, Math.floor(Number(total) || 0));
	if (!safeTotal) {
		return 0;
	}
	var safeBatch = toPositiveInt(batchSize, DEFAULT_BATCH_SIZE);
	var visibleCount = syncVisibleCount(currentVisible, safeTotal, initialVisible);
	return Math.min(safeTotal, visibleCount + safeBatch);
}

module.exports = {
	DEFAULT_INITIAL_VISIBLE: DEFAULT_INITIAL_VISIBLE,
	DEFAULT_BATCH_SIZE: DEFAULT_BATCH_SIZE,
	getInitialVisibleCount: getInitialVisibleCount,
	syncVisibleCount: syncVisibleCount,
	sliceVisibleList: sliceVisibleList,
	hasMoreItems: hasMoreItems,
	expandVisibleCount: expandVisibleCount
};
