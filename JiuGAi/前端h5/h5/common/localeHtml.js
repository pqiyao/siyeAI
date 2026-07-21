/**
 * H5：按语言索引切换 html 的 data-lang-idx / class，驱动全局 font-family
 */
export function applyLocaleToDocument(languageIndex) {
	// #ifdef H5
	try {
		if (typeof document === 'undefined') return;
		const html = document.documentElement;
		const idx = Number(languageIndex);
		html.setAttribute('data-lang-idx', String(idx));
		const latin = [2, 5, 6];
		const isLatin = latin.indexOf(idx) !== -1;
		html.classList.remove('locale-cjk', 'locale-latin');
		html.classList.add(isLatin ? 'locale-latin' : 'locale-cjk');
		const langMap = { 0: 'zh-HK', 1: 'zh-CN', 2: 'en', 3: 'ko', 4: 'ja', 5: 'de', 6: 'fr' };
		html.setAttribute('lang', langMap[idx] || 'zh-CN');
	} catch (e) {}
	// #endif
}
