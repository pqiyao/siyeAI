import { applyTavernTabBarLabels } from '@/common/tavernTabBar.js';

/**
 * 语言包已写入 Vue.prototype.allText 后，刷新当前栈内所有页面视图（修复 prototype 非响应式导致界面不更新）
 */
export function refreshI18nViews(allTextRoot) {
	try {
		applyTavernTabBarLabels(allTextRoot || {});
	} catch (e) {}
	try {
		const pages = getCurrentPages();
		pages.forEach((p) => {
			if (p && p.$vm && typeof p.$vm.$forceUpdate === 'function') {
				p.$vm.$forceUpdate();
			}
		});
	} catch (e) {}
}
