const tavernUiModule = require('@/common/tavernUiI18n.js');
const getTavernUiText = tavernUiModule.getTavernUiText;

const STORAGE_KEY = 'jg_tavern_tabbar_state';

function getDefaultLabels() {
	return {
		discover: '发现',
		tavern: '酒馆',
		inbox: '会话',
		user: '我的'
	};
}

function getDefaultBadges() {
	return {
		inbox: 0
	};
}

function buildLabels(allTextRoot) {
	const ui = getTavernUiText('tabbar');
	const legacy = (allTextRoot && allTextRoot.tabbar) || {};
	return {
		discover: ui.discover || legacy.推荐 || '发现',
		tavern: ui.tavern || legacy.动态 || '酒馆',
		inbox: ui.inbox || legacy.消息 || '会话',
		user: ui.user || legacy.我的 || '我的'
	};
}

function readState() {
	try {
		const raw = uni.getStorageSync(STORAGE_KEY);
		if (!raw || typeof raw !== 'object') {
			return {
				activeRoute: 'pages/index/index',
				labels: getDefaultLabels(),
				badges: getDefaultBadges()
			};
		}
		return {
			activeRoute: raw.activeRoute || 'pages/index/index',
			labels: Object.assign({}, getDefaultLabels(), raw.labels || {}),
			badges: Object.assign({}, getDefaultBadges(), raw.badges || {})
		};
	} catch (e) {
		return {
			activeRoute: 'pages/index/index',
			labels: getDefaultLabels(),
			badges: getDefaultBadges()
		};
	}
}

function writeState(patch) {
	const current = readState();
	const nextState = Object.assign({}, current, patch || {});
	if (patch && patch.labels) {
		nextState.labels = Object.assign({}, getDefaultLabels(), current.labels || {}, patch.labels);
	}
	if (patch && patch.badges) {
		nextState.badges = Object.assign({}, getDefaultBadges(), current.badges || {}, patch.badges);
	}
	try {
		uni.setStorageSync(STORAGE_KEY, nextState);
	} catch (e) {}
	return nextState;
}

function updateCustomTabBar(vm, patch) {
	const nextState = writeState(patch);
	if (!vm || typeof vm.getTabBar !== 'function') {
		return nextState;
	}
	try {
		const tabBar = vm.getTabBar();
		if (tabBar && typeof tabBar.applyState === 'function') {
			tabBar.applyState(nextState);
		}
	} catch (e) {}
	return nextState;
}

export function getTavernTabBarState() {
	return readState();
}

export function applyTavernTabBarLabels(allTextRoot, vm) {
	const labels = buildLabels(allTextRoot);
	try {
		uni.setTabBarItem({ index: 0, text: labels.discover });
		uni.setTabBarItem({ index: 1, text: labels.tavern });
		uni.setTabBarItem({ index: 2, text: labels.inbox });
		uni.setTabBarItem({ index: 3, text: labels.user });
	} catch (e) {}
	updateCustomTabBar(vm, { labels: labels });
}

export function syncTavernTabBar(vm, activeRoute, allTextRoot) {
	const patch = {
		activeRoute: activeRoute || 'pages/index/index'
	};
	if (allTextRoot) {
		patch.labels = buildLabels(allTextRoot);
	}
	updateCustomTabBar(vm, patch);
}

export function syncTavernInboxBadge(vm, unreadCount) {
	const count = Math.max(0, Number(unreadCount) || 0);
	try {
		uni.removeTabBarBadge({ index: 2 });
	} catch (e) {}
	updateCustomTabBar(vm, {
		badges: {
			inbox: count
		}
	});
}
