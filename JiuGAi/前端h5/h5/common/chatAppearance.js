const tavernApi = require('@/common/tavernApi.js');
const structuredContent = require('@/common/chatStructuredContent.js');
const chatMarkdown = require('@/common/chatMarkdown.js');

const STORAGE_PREFIX = 'tavern_chat_appearance_v2_';
const LEGACY_STORAGE_PREFIX = 'tavern_chat_appearance_v1_';
const CHARACTER_STORAGE_PREFIX = 'tavern_chat_appearance_character_v1_';
const SYNC_STORAGE_PREFIX = 'tavern_chat_appearance_sync_v1_';
const CUSTOM_PRESET_CODE = 'custom';
const BUBBLE_SCHEMA_VERSION = 3;
const CURRENT_PRESET_VERSION = 4;
const syncRequestVersions = Object.create(null);
const TEXT_COLOR_KEYS = [
	'baseTextColor',
	'userTextColor',
	'speechColor',
	'actionColor',
	'thoughtColor',
	'narrationColor'
];
const BUBBLE_CONFIG_KEYS = [
	'schemaVersion',
	'presetVersion',
	'fontSize',
	'lineHeight',
	'baseFontWeight',
	'userFontWeight',
	'speechFontWeight',
	'actionFontWeight',
	'thoughtFontWeight',
	'narrationFontWeight',
	'thoughtItalic',
	'radius',
	'opacity',
	'bubblePaddingY',
	'bubblePaddingX',
	'charMaxWidth',
	'userMaxWidth',
	'imagePadding',
	'backdropStrength',
	'surfaceMode',
	'surfaceBorderOpacity',
	'sideBorderWidth',
	'sideBorderOpacity',
	'shadowStrength',
	'blurRadius',
	'contentTone',
	'charBubbleColor',
	'userBubbleColor',
	'charBorderColor',
	'userBorderColor',
	'baseTextColor',
	'userTextColor',
	'speechColor',
	'actionColor',
	'thoughtColor',
	'narrationColor'
];

const LEGACY_CLASSIC_BUBBLE_CONFIG = Object.freeze({
	preset: 'system',
	schemaVersion: BUBBLE_SCHEMA_VERSION,
	presetVersion: CURRENT_PRESET_VERSION,
	fontSize: 28,
	lineHeight: 1.66,
	baseFontWeight: 560,
	userFontWeight: 560,
	speechFontWeight: 700,
	actionFontWeight: 540,
	thoughtFontWeight: 500,
	narrationFontWeight: 500,
	thoughtItalic: true,
	radius: 20,
	opacity: 62,
	bubblePaddingY: 15,
	bubblePaddingX: 20,
	charMaxWidth: 78,
	userMaxWidth: 72,
	imagePadding: 8,
	backdropStrength: 0,
	surfaceMode: 'legacyGlass',
	surfaceBorderOpacity: 18,
	sideBorderWidth: 4,
	sideBorderOpacity: 50,
	shadowStrength: 70,
	blurRadius: 8,
	contentTone: 'dark',
	charBubbleColor: '#20222a',
	userBubbleColor: '#264148',
	charBorderColor: '#ffc1dc',
	userBorderColor: '#c8f5df',
	baseTextColor: '#f2f4f7',
	userTextColor: '#f2f4f7',
	speechColor: '#f4b8cf',
	actionColor: '#bfe8d2',
	thoughtColor: '#d4caef',
	narrationColor: '#f2f4f7'
});

const FENGYUE_BUBBLE_CONFIG = Object.freeze({
	preset: 'system',
	schemaVersion: BUBBLE_SCHEMA_VERSION,
	presetVersion: CURRENT_PRESET_VERSION,
	fontSize: 32,
	lineHeight: 1.7,
	baseFontWeight: 500,
	userFontWeight: 500,
	speechFontWeight: 600,
	actionFontWeight: 500,
	thoughtFontWeight: 500,
	narrationFontWeight: 500,
	thoughtItalic: false,
	radius: 22,
	opacity: 58,
	bubblePaddingY: 15,
	bubblePaddingX: 20,
	charMaxWidth: 84,
	userMaxWidth: 76,
	imagePadding: 6,
	backdropStrength: 16,
	surfaceMode: 'flat',
	surfaceBorderOpacity: 14,
	sideBorderWidth: 2,
	sideBorderOpacity: 45,
	shadowStrength: 8,
	blurRadius: 8,
	contentTone: 'dark',
	charBubbleColor: '#111318',
	userBubbleColor: '#12333a',
	charBorderColor: '#efb2c8',
	userBorderColor: '#91ded2',
	baseTextColor: '#ffffff',
	userTextColor: '#ffffff',
	speechColor: '#fff4f8',
	actionColor: '#ffffff',
	thoughtColor: '#ffffff',
	narrationColor: '#ffffff'
});

// System mode is the frozen pre-redesign baseline. Fengyue remains an opt-in preset.
const BASE_BUBBLE_CONFIG = LEGACY_CLASSIC_BUBBLE_CONFIG;

const DEFAULT_CONFIG = Object.freeze(Object.assign({}, BASE_BUBBLE_CONFIG, {
	bubbleCustomized: false,
	customized: false,
	textColorOverrides: Object.freeze({}),
	readMode: 'original',
	showSegmentLabels: false,
	replySplitMode: 'none'
}));

const PRESETS = Object.freeze([
	{
		code: 'system',
		name: '跟随默认',
		desc: '保持聊天页原本气泡，不额外覆盖外观。',
		system: true,
		patch: {}
	},
	{
		code: 'classic',
		name: '原版基准',
		desc: '冻结升级前的原聊天页外观。',
		patch: {}
	},
	{
		code: 'fengyue',
		name: '风月轻语',
		desc: '清晰高对比文字与轻透磨砂底框。',
		patch: {}
	},
	{
		code: 'night',
		name: '深夜玻璃',
		desc: '低饱和深色玻璃，边缘更柔和。',
		patch: {
			fontSize: 28,
			lineHeight: 1.72,
			radius: 26,
			opacity: 54,
			bubblePaddingY: 16,
			bubblePaddingX: 21,
			charMaxWidth: 80,
			userMaxWidth: 74,
			backdropStrength: 14,
			charBubbleColor: '#18262e',
			userBubbleColor: '#233d46',
			charBorderColor: '#f0a9c8',
			userBorderColor: '#83d7cb',
			baseTextColor: '#f1f5f7',
			userTextColor: '#f5ffff',
			speechColor: '#ffc6dc',
			actionColor: '#bcebd8',
			thoughtColor: '#d8cdf2',
			narrationColor: '#e7edf0'
		}
	},
	{
		code: 'soft',
		name: '晨雾浅色',
		desc: '浅色清晰气泡。',
		patch: {
			fontSize: 28,
			lineHeight: 1.72,
			radius: 24,
			opacity: 92,
			contentTone: 'auto',
			backdropStrength: 6,
			charBubbleColor: '#edf3f5',
			userBubbleColor: '#dceff0',
			charBorderColor: '#668896',
			userBorderColor: '#3f8d91',
			baseTextColor: '#263845',
			userTextColor: '#23414a',
			speechColor: '#a53f6a',
			actionColor: '#287a64',
			thoughtColor: '#7650a6',
			narrationColor: '#344955'
		}
	},
	{
		code: 'novel',
		name: '沉浸阅读',
		desc: '宽版长文气泡。',
		patch: {
			fontSize: 30,
			lineHeight: 1.84,
			radius: 16,
			opacity: 82,
			bubblePaddingY: 18,
			bubblePaddingX: 22,
			charMaxWidth: 86,
			userMaxWidth: 76,
			backdropStrength: 18,
			charBubbleColor: '#272a31',
			userBubbleColor: '#203d43',
			charBorderColor: '#d7bde2',
			userBorderColor: '#8dd8d0',
			baseTextColor: '#f5f3ff',
			userTextColor: '#f8feff',
			speechColor: '#fff3f8',
			actionColor: '#d5f6e9',
			thoughtColor: '#e1d9ff',
			narrationColor: '#eceff4'
		}
	},
	{
		code: 'contrast',
		name: '极致清晰',
		desc: '高对比实色气泡。',
		patch: {
			fontSize: 29,
			lineHeight: 1.7,
			radius: 14,
			opacity: 90,
			backdropStrength: 34,
			charBubbleColor: '#0b1020',
			userBubbleColor: '#063c45',
			charBorderColor: '#ffffff',
			userBorderColor: '#67e8f9',
			baseTextColor: '#ffffff',
			userTextColor: '#ffffff',
			speechColor: '#ffffff',
			actionColor: '#b9fbc0',
			thoughtColor: '#f0abfc',
			narrationColor: '#e5e7eb'
		}
	},
	{
		code: 'clear',
		name: '轻透氛围',
		desc: '低浓度透明气泡。',
		patch: {
			fontSize: 28,
			lineHeight: 1.66,
			opacity: 42,
			radius: 24,
			backdropStrength: 0,
			charBubbleColor: '#263238',
			userBubbleColor: '#27515a',
			charBorderColor: '#ffc1dc',
			userBorderColor: '#80e6de',
			baseTextColor: '#f8fafc',
			userTextColor: '#f8feff',
			speechColor: '#ffe4f1',
			actionColor: '#bdf7e5',
			thoughtColor: '#ddd6fe',
			narrationColor: '#f2f4f7'
		}
	}
]);

function clampNumber(value, min, max, fallback) {
	var number = Number(value);
	if (!isFinite(number)) {
		return fallback;
	}
	return Math.min(max, Math.max(min, number));
}

function clampInt(value, min, max, fallback) {
	return Math.round(clampNumber(value, min, max, fallback));
}

function normalizeHexColor(value, fallback) {
	var text = String(value == null ? '' : value).trim();
	if (/^#[0-9a-fA-F]{6}$/.test(text)) {
		return text.toLowerCase();
	}
	return fallback;
}

function hexToRgb(hex) {
	var safe = normalizeHexColor(hex, '#000000').slice(1);
	return {
		r: parseInt(safe.slice(0, 2), 16),
		g: parseInt(safe.slice(2, 4), 16),
		b: parseInt(safe.slice(4, 6), 16)
	};
}

function rgba(hex, alpha) {
	var rgb = hexToRgb(hex);
	var safeAlpha = clampNumber(alpha, 0, 1, 1);
	return 'rgba(' + rgb.r + ',' + rgb.g + ',' + rgb.b + ',' + safeAlpha.toFixed(3) + ')';
}

function relativeLuminance(hex) {
	var rgb = hexToRgb(hex);
	var channel = function (value) {
		var normalized = value / 255;
		return normalized <= 0.04045
			? normalized / 12.92
			: Math.pow((normalized + 0.055) / 1.055, 2.4);
	};
	return 0.2126 * channel(rgb.r) + 0.7152 * channel(rgb.g) + 0.0722 * channel(rgb.b);
}

function resolveContentTone(config, isUser) {
	var c = normalizeConfig(config);
	if (c.contentTone === 'light' || c.contentTone === 'dark') return c.contentTone;
	return relativeLuminance(isUser ? c.userBubbleColor : c.charBubbleColor) > 0.48 ? 'light' : 'dark';
}

function buildEmbeddedContentTokens(config, isUser) {
	var c = normalizeConfig(config);
	var tone = resolveContentTone(c, isUser);
	var primary = isUser ? c.userTextColor : c.baseTextColor;
	var accent = isUser ? c.userBorderColor : c.charBorderColor;
	if (tone === 'light') {
		return {
			primary: primary,
			muted: 'rgba(63,55,60,0.72)',
			surface: 'rgba(45,35,41,0.055)',
			surfaceStrong: 'rgba(45,35,41,0.095)',
			border: 'rgba(63,48,56,0.16)',
			accent: accent,
			danger: '#a85864'
		};
	}
	return {
		primary: primary,
		muted: 'rgba(245,242,244,0.84)',
		surface: 'rgba(255,255,255,0.07)',
		surfaceStrong: 'rgba(255,255,255,0.12)',
		border: 'rgba(255,255,255,0.18)',
		accent: accent,
		danger: '#dfa3ab'
	};
}

function runtimeRpx(value) {
	var number = Number(value);
	if (!isFinite(number)) {
		number = 0;
	}
	var result = number + 'rpx';
	// #ifdef H5
	try {
		if (typeof uni !== 'undefined' && typeof uni.upx2px === 'function') {
			var viewport = typeof window !== 'undefined' && window.innerWidth ? Math.min(window.innerWidth, 430) : 375;
			result = (number * viewport / 750).toFixed(3).replace(/\.0+$/, '') + 'px';
		} else if (typeof window !== 'undefined' && window.innerWidth) {
			result = (number * Math.min(window.innerWidth, 430) / 750).toFixed(3).replace(/\.0+$/, '') + 'px';
		}
	} catch (e) {}
	// #endif
	return result;
}

function runtimeTextSize(value) {
	var number = Number(value);
	if (!isFinite(number)) number = DEFAULT_CONFIG.fontSize;
	var result = number + 'rpx';
	// #ifdef H5
	try {
		if (typeof window !== 'undefined') {
			result = Math.max(12, Math.min(18, number / 2)).toFixed(2).replace(/\.00$/, '') + 'px';
		}
	} catch (e) {}
	// #endif
	return result;
}

function runtimeBubbleMaxWidth(percent, isUser) {
	var safePercent = clampInt(percent, 40, 100, isUser ? DEFAULT_CONFIG.userMaxWidth : DEFAULT_CONFIG.charMaxWidth);
	var result = safePercent + '%';
	// #ifdef H5
	try {
		if (typeof window !== 'undefined' && window.innerWidth >= 768) {
			result = 'min(' + safePercent + '%, ' + (isUser ? '34em' : '38em') + ')';
		}
	} catch (e) {}
	// #endif
	return result;
}

function bubbleConfigMatches(source, expected) {
	for (var i = 0; i < BUBBLE_CONFIG_KEYS.length; i += 1) {
		var key = BUBBLE_CONFIG_KEYS[i];
		if (typeof expected[key] === 'number') {
			if (!approxEqualNumber(source[key], expected[key])) return false;
		} else if (String(source[key] || '').toLowerCase() !== String(expected[key] || '').toLowerCase()) {
			return false;
		}
	}
	return true;
}

function bubbleStructureMatches(source, expected) {
	for (var i = 0; i < BUBBLE_CONFIG_KEYS.length; i += 1) {
		var key = BUBBLE_CONFIG_KEYS[i];
		if (TEXT_COLOR_KEYS.indexOf(key) >= 0) {
			continue;
		}
		if (typeof expected[key] === 'number') {
			if (!approxEqualNumber(source[key], expected[key])) return false;
		} else if (String(source[key] || '').toLowerCase() !== String(expected[key] || '').toLowerCase()) {
			return false;
		}
	}
	return true;
}

function isPreviousFengyueDefault(source) {
	var raw = source && typeof source === 'object' ? source : null;
	if (!raw || raw.bubbleCustomized !== true || raw.customized !== true) return false;
	var preset = String(raw.preset || '').toLowerCase();
	if (preset !== 'system' && preset !== 'fengyue') return false;
	var retiredBaselines = [{
		presetVersion: 3,
		fontSize: 32,
		lineHeight: 1.7,
		baseFontWeight: 500,
		userFontWeight: 500,
		speechFontWeight: 600,
		actionFontWeight: 500,
		thoughtFontWeight: 500,
		narrationFontWeight: 500,
		thoughtItalic: false,
		radius: 22,
		opacity: 58,
		bubblePaddingY: 15,
		bubblePaddingX: 20,
		charMaxWidth: 84,
		userMaxWidth: 76,
		imagePadding: 6,
		backdropStrength: 16,
		surfaceMode: 'flat',
		surfaceBorderOpacity: 14,
		sideBorderWidth: 2,
		sideBorderOpacity: 45,
		shadowStrength: 8,
		blurRadius: 8,
		contentTone: 'dark',
		charBubbleColor: '#111318',
		userBubbleColor: '#12333a',
		charBorderColor: '#efb2c8',
		userBorderColor: '#91ded2',
		baseTextColor: '#ffffff',
		userTextColor: '#ffffff',
		speechColor: '#fff4f8',
		actionColor: '#ffffff',
		thoughtColor: '#ffffff',
		narrationColor: '#ffffff'
	}, {
		fontSize: 32,
		lineHeight: 1.68,
		baseFontWeight: 400,
		userFontWeight: 500,
		speechFontWeight: 500,
		actionFontWeight: 400,
		thoughtFontWeight: 400,
		narrationFontWeight: 400,
		thoughtItalic: false,
		radius: 22,
		opacity: 86,
		bubblePaddingY: 15,
		bubblePaddingX: 20,
		charMaxWidth: 84,
		userMaxWidth: 76,
		imagePadding: 6,
		backdropStrength: 14,
		surfaceMode: 'softGradient',
		surfaceBorderOpacity: 18,
		sideBorderWidth: 3,
		sideBorderOpacity: 40,
		shadowStrength: 26,
		blurRadius: 4,
		contentTone: 'dark',
		charBubbleColor: '#211b23',
		userBubbleColor: '#203337',
		charBorderColor: '#dca7b9',
		userBorderColor: '#8fcbc2',
		baseTextColor: '#eee9ec',
		userTextColor: '#f0f7f5',
		speechColor: '#f3c2d4',
		actionColor: '#b8d8cd',
		thoughtColor: '#cfc3e7',
		narrationColor: '#e9e3e6'
	}];
	for (var baselineIndex = 0; baselineIndex < retiredBaselines.length; baselineIndex += 1) {
		var previous = retiredBaselines[baselineIndex];
		var compared = 0;
		var matched = true;
		var keys = Object.keys(previous);
		for (var i = 0; i < keys.length; i += 1) {
			var key = keys[i];
			if (raw[key] == null) continue;
			compared += 1;
			if (typeof previous[key] === 'number') {
				if (!approxEqualNumber(raw[key], previous[key])) matched = false;
			} else if (typeof previous[key] === 'boolean') {
				if (raw[key] !== previous[key]) matched = false;
			} else if (String(raw[key]).toLowerCase() !== String(previous[key]).toLowerCase()) {
				matched = false;
			}
			if (!matched) break;
		}
		if (matched && compared >= 8) return true;
	}
	return false;
}

function presetByCode(code) {
	return PRESETS.find(function (item) { return item.code === code; }) || null;
}

function presetBubbleConfig(code) {
	var preset = presetByCode(code);
	if (preset && preset.code === 'fengyue') {
		return Object.assign({}, FENGYUE_BUBBLE_CONFIG, preset && preset.patch ? preset.patch : {});
	}
	if (!preset || preset.system) {
		return Object.assign({}, LEGACY_CLASSIC_BUBBLE_CONFIG, preset && preset.patch ? preset.patch : {});
	}
	return Object.assign({}, LEGACY_CLASSIC_BUBBLE_CONFIG, preset.patch || {});
}

function normalizeFontWeight(value, fallback) {
	var number = clampInt(value, 300, 700, fallback);
	if (number === 540 || number === 560) return number;
	return Math.round(number / 100) * 100;
}

function normalizeTextColorOverrides(source) {
	var raw = source && typeof source === 'object' ? source : {};
	var result = {};
	TEXT_COLOR_KEYS.forEach(function (key) {
		var value = String(raw[key] == null ? '' : raw[key]).trim();
		if (/^#[0-9a-fA-F]{6}$/.test(value)) {
			result[key] = value.toLowerCase();
		}
	});
	return result;
}

function applyTextColorOverrides(target, overrides) {
	var result = target;
	TEXT_COLOR_KEYS.forEach(function (key) {
		if (overrides[key]) {
			result[key] = overrides[key];
		}
	});
	return result;
}

function normalizeMode(value, allowed, fallback) {
	var text = String(value == null ? '' : value).trim();
	return allowed.indexOf(text) >= 0 ? text : fallback;
}

function normalizeReplySplitMode(value) {
	var text = String(value == null ? '' : value).trim();
	if (text === 'paragraph' || text === 'speech') {
		return 'bubble';
	}
	return text === 'bubble' ? 'bubble' : 'none';
}

function approxEqualNumber(a, b) {
	return Math.abs(Number(a) - Number(b)) < 0.001;
}

function isDefaultLikeColor(key, value) {
	var normalized = normalizeHexColor(value, LEGACY_CLASSIC_BUBBLE_CONFIG[key]);
	if (normalized === LEGACY_CLASSIC_BUBBLE_CONFIG[key]) {
		return true;
	}
	var legacyColors = {
		userBorderColor: ['#80e6de']
	};
	return (legacyColors[key] || []).indexOf(normalized) >= 0;
}

function isLegacyDefaultBubble(raw) {
	if (!raw || typeof raw !== 'object') {
		return false;
	}
	var keys = [
		'fontSize',
		'lineHeight',
		'radius',
		'opacity',
		'bubblePaddingY',
		'bubblePaddingX',
		'charMaxWidth',
		'userMaxWidth',
		'imagePadding',
		'backdropStrength'
	];
	for (var i = 0; i < keys.length; i++) {
		var key = keys[i];
		if (raw[key] != null && !approxEqualNumber(raw[key], LEGACY_CLASSIC_BUBBLE_CONFIG[key])) {
			return false;
		}
	}
	var colorKeys = [
		'charBubbleColor',
		'userBubbleColor',
		'charBorderColor',
		'userBorderColor',
		'baseTextColor',
		'userTextColor',
		'speechColor',
		'actionColor',
		'thoughtColor',
		'narrationColor'
	];
	for (var j = 0; j < colorKeys.length; j++) {
		var colorKey = colorKeys[j];
		if (raw[colorKey] != null && !isDefaultLikeColor(colorKey, raw[colorKey])) {
			return false;
		}
	}
	return true;
}

function resolveBubbleCustomized(raw) {
	if (!raw || typeof raw !== 'object') {
		return false;
	}
	if (raw.bubbleCustomized === true) {
		return true;
	}
	if (raw.bubbleCustomized === false) {
		return false;
	}
	if (raw.customized === true) {
		return !isLegacyDefaultBubble(raw);
	}
	return false;
}

function normalizeConfig(source) {
	var raw = source && typeof source === 'object' ? source : {};
	var allowedPresets = PRESETS.map(function (item) { return item.code; }).concat([CUSTOM_PRESET_CODE]);
	var bubbleCustomized = resolveBubbleCustomized(raw);
	if (bubbleCustomized && isPreviousFengyueDefault(raw)) {
		raw = Object.assign({}, raw, { bubbleCustomized: false, customized: false, preset: 'system' });
		bubbleCustomized = false;
	}
	var preset = normalizeMode(raw.preset, allowedPresets, bubbleCustomized ? 'classic' : DEFAULT_CONFIG.preset);
	if (!bubbleCustomized) {
		preset = 'system';
	}
	var presetItem = presetByCode(preset);
	var rawSchemaVersion = Math.max(0, Number(raw.schemaVersion || 0));
	var rawPresetVersion = Math.max(0, Number(raw.presetVersion || 0));
	var fallback = BASE_BUBBLE_CONFIG;
	if (bubbleCustomized) {
		if (presetItem && !presetItem.system && preset !== CUSTOM_PRESET_CODE) {
			fallback = presetBubbleConfig(preset);
		} else if (rawSchemaVersion < BUBBLE_SCHEMA_VERSION) {
			fallback = LEGACY_CLASSIC_BUBBLE_CONFIG;
		}
	}
	var bubbleSource = bubbleCustomized ? raw : {};
	var value = function (key) {
		return bubbleSource[key] != null ? bubbleSource[key] : fallback[key];
	};
	var textColorOverrides = bubbleCustomized
		? normalizeTextColorOverrides(raw.textColorOverrides)
		: {};
	var normalized = {
		bubbleCustomized: bubbleCustomized,
		customized: bubbleCustomized,
		preset: preset,
		textColorOverrides: textColorOverrides,
		schemaVersion: BUBBLE_SCHEMA_VERSION,
		presetVersion: clampInt(value('presetVersion'), 1, 100, CURRENT_PRESET_VERSION),
		fontSize: clampInt(value('fontSize'), 24, 36, fallback.fontSize),
		lineHeight: clampNumber(value('lineHeight'), 1.35, 2.1, fallback.lineHeight),
		baseFontWeight: normalizeFontWeight(value('baseFontWeight'), fallback.baseFontWeight),
		userFontWeight: normalizeFontWeight(value('userFontWeight'), fallback.userFontWeight),
		speechFontWeight: normalizeFontWeight(value('speechFontWeight'), fallback.speechFontWeight),
		actionFontWeight: normalizeFontWeight(value('actionFontWeight'), fallback.actionFontWeight),
		thoughtFontWeight: normalizeFontWeight(value('thoughtFontWeight'), fallback.thoughtFontWeight),
		narrationFontWeight: normalizeFontWeight(value('narrationFontWeight'), fallback.narrationFontWeight),
		thoughtItalic: value('thoughtItalic') === true,
		radius: clampInt(value('radius'), 8, 32, fallback.radius),
		opacity: clampInt(value('opacity'), 30, 96, fallback.opacity),
		bubblePaddingY: clampInt(value('bubblePaddingY'), 8, 26, fallback.bubblePaddingY),
		bubblePaddingX: clampInt(value('bubblePaddingX'), 12, 34, fallback.bubblePaddingX),
		charMaxWidth: clampInt(value('charMaxWidth'), 62, 92, fallback.charMaxWidth),
		userMaxWidth: clampInt(value('userMaxWidth'), 58, 88, fallback.userMaxWidth),
		imagePadding: clampInt(value('imagePadding'), 0, 18, fallback.imagePadding),
		backdropStrength: clampInt(value('backdropStrength'), 0, 55, fallback.backdropStrength),
		surfaceMode: normalizeMode(value('surfaceMode'), ['flat', 'softGradient', 'legacyGlass'], fallback.surfaceMode),
		surfaceBorderOpacity: clampInt(value('surfaceBorderOpacity'), 0, 60, fallback.surfaceBorderOpacity),
		sideBorderWidth: clampInt(value('sideBorderWidth'), 0, 6, fallback.sideBorderWidth),
		sideBorderOpacity: clampInt(value('sideBorderOpacity'), 0, 100, fallback.sideBorderOpacity),
		shadowStrength: clampInt(value('shadowStrength'), 0, 100, fallback.shadowStrength),
		blurRadius: clampInt(value('blurRadius'), 0, 16, fallback.blurRadius),
		contentTone: normalizeMode(value('contentTone'), ['auto', 'light', 'dark'], fallback.contentTone),
		charBubbleColor: normalizeHexColor(value('charBubbleColor'), fallback.charBubbleColor),
		userBubbleColor: normalizeHexColor(value('userBubbleColor'), fallback.userBubbleColor),
		charBorderColor: normalizeHexColor(value('charBorderColor'), fallback.charBorderColor),
		userBorderColor: normalizeHexColor(value('userBorderColor'), fallback.userBorderColor),
		baseTextColor: normalizeHexColor(value('baseTextColor'), fallback.baseTextColor),
		userTextColor: normalizeHexColor(value('userTextColor'), fallback.userTextColor),
		speechColor: normalizeHexColor(value('speechColor'), fallback.speechColor),
		actionColor: normalizeHexColor(value('actionColor'), fallback.actionColor),
		thoughtColor: normalizeHexColor(value('thoughtColor'), fallback.thoughtColor),
		narrationColor: normalizeHexColor(value('narrationColor'), fallback.narrationColor),
		readMode: normalizeMode(raw.readMode, ['original', 'novel', 'speechOnly', 'hideThought', 'softAction'], DEFAULT_CONFIG.readMode),
		showSegmentLabels: raw.showSegmentLabels === true,
		replySplitMode: normalizeReplySplitMode(raw.replySplitMode)
	};
	if (bubbleCustomized && preset !== CUSTOM_PRESET_CODE) {
		if (presetItem && !presetItem.system) {
			var expected = presetBubbleConfig(preset);
			var shouldUpgradePreset = rawPresetVersion < CURRENT_PRESET_VERSION;
			if (!shouldUpgradePreset && !bubbleStructureMatches(normalized, expected)) {
				normalized.preset = CUSTOM_PRESET_CODE;
			} else {
				if (!raw.textColorOverrides || typeof raw.textColorOverrides !== 'object') {
					TEXT_COLOR_KEYS.forEach(function (key) {
						if (raw[key] != null && normalizeHexColor(raw[key], expected[key]) !== expected[key]) {
							textColorOverrides[key] = normalizeHexColor(raw[key], expected[key]);
						}
					});
				}
				BUBBLE_CONFIG_KEYS.forEach(function (key) {
					if (TEXT_COLOR_KEYS.indexOf(key) < 0) {
						normalized[key] = expected[key];
					}
				});
				TEXT_COLOR_KEYS.forEach(function (key) {
					normalized[key] = expected[key];
				});
				normalized.textColorOverrides = normalizeTextColorOverrides(textColorOverrides);
				applyTextColorOverrides(normalized, normalized.textColorOverrides);
			}
		}
	}
	return normalized;
}

function ownerKey(requestSession) {
	if (requestSession && requestSession.ownerKey) {
		return String(requestSession.ownerKey);
	}
	var user = tavernApi.getStoredUser ? tavernApi.getStoredUser() : null;
	var userId = tavernApi.getStoredUserId ? tavernApi.getStoredUserId(user) : '';
	if (userId) {
		return 'user_' + userId;
	}
	return 'guest_' + (tavernApi.getClientUid ? tavernApi.getClientUid() : 'local');
}

function storageKey(owner) {
	return STORAGE_PREFIX + (owner || ownerKey());
}

function legacyStorageKey(owner) {
	return LEGACY_STORAGE_PREFIX + (owner || ownerKey());
}

function readStored(key) {
	try {
		return uni.getStorageSync(key);
	} catch (e) {
		return null;
	}
}

function normalizeCharacterId(characterId) {
	var value = Number(characterId || 0);
	return isFinite(value) && value > 0 ? Math.floor(value) : 0;
}

function characterStorageKey(characterId, owner) {
	return CHARACTER_STORAGE_PREFIX + (owner || ownerKey()) + '_' + String(normalizeCharacterId(characterId));
}

function syncStorageKey(characterId, owner) {
	var scope = normalizeCharacterId(characterId) > 0 ? 'character_' + normalizeCharacterId(characterId) : 'global';
	return SYNC_STORAGE_PREFIX + (owner || ownerKey()) + '_' + scope;
}

function captureOwnerSession() {
	if (tavernApi.captureRequestSession) {
		return tavernApi.captureRequestSession();
	}
	var currentOwner = ownerKey();
	return {
		captured: true,
		ownerKey: currentOwner,
		viewerSignature: tavernApi.getViewerStateSignature ? tavernApi.getViewerStateSignature() : currentOwner,
		clientUid: tavernApi.getClientUid ? tavernApi.getClientUid() : '',
		authToken: tavernApi.getStoredAuthToken ? tavernApi.getStoredAuthToken() : '',
		deviceToken: tavernApi.getDeviceToken ? tavernApi.getDeviceToken() : ''
	};
}

function createRequestGuard() {
	return {
		active: true,
		cancel: function () { this.active = false; }
	};
}

function beginSyncContext(characterId, options) {
	var opts = options && typeof options === 'object' ? options : {};
	var session = opts.session && opts.session.captured === true ? opts.session : captureOwnerSession();
	var safeCharacterId = normalizeCharacterId(characterId);
	var requestKey = ownerKey(session) + '|' + (safeCharacterId > 0 ? 'character_' + safeCharacterId : 'global');
	var version = Number(syncRequestVersions[requestKey] || 0) + 1;
	syncRequestVersions[requestKey] = version;
	return {
		owner: ownerKey(session),
		characterId: safeCharacterId,
		requestKey: requestKey,
		version: version,
		session: session,
		guard: opts.guard || null
	};
}

function isSyncContextActive(context) {
	if (!context || (context.guard && context.guard.active === false)) return false;
	if (Number(syncRequestVersions[context.requestKey] || 0) !== Number(context.version)) return false;
	if (tavernApi.isRequestSessionCurrent) return tavernApi.isRequestSessionCurrent(context.session);
	return ownerKey() === context.owner;
}

function defaultSyncState() {
	return { revision: 0, pending: null, conflict: null, lastError: '', updatedAt: 0 };
}

function readSyncStateFor(owner, characterId) {
	var raw = readStored(syncStorageKey(characterId, owner));
	var state = raw && typeof raw === 'object' ? raw : {};
	return {
		revision: Math.max(0, Number(state.revision || 0)),
		pending: state.pending && typeof state.pending === 'object' ? state.pending : null,
		conflict: state.conflict && typeof state.conflict === 'object' ? state.conflict : null,
		lastError: String(state.lastError || ''),
		updatedAt: Math.max(0, Number(state.updatedAt || 0))
	};
}

function writeSyncStateFor(owner, characterId, nextState) {
	var state = Object.assign(defaultSyncState(), nextState || {}, { updatedAt: Date.now() });
	uni.setStorageSync(syncStorageKey(characterId, owner), state);
	return state;
}

function publicSyncState(state) {
	var current = state || defaultSyncState();
	return Object.assign({}, current, {
		status: current.conflict ? 'conflict' : (current.pending ? 'pending' : (current.lastError ? 'offline' : 'synced'))
	});

}

function getSyncState(characterId) {
	return publicSyncState(readSyncStateFor(ownerKey(), normalizeCharacterId(characterId)));
}

function splitSections(config) {
	var c = normalizeConfig(config);
	var bubble = {};
	['bubbleCustomized', 'customized', 'preset', 'textColorOverrides'].concat(BUBBLE_CONFIG_KEYS).forEach(function (key) {
		bubble[key] = c[key];
	});
	return {
		bubble: bubble,
		reading: { readMode: c.readMode, showSegmentLabels: c.showSegmentLabels },
		replyFormat: { replySplitMode: c.replySplitMode }
	};
}

function mergeSections(baseConfig, sections) {
	var base = normalizeConfig(baseConfig);
	var source = sections && typeof sections === 'object' ? sections : {};
	return normalizeConfig(Object.assign({}, base, source.bubble || {}, source.reading || {}, source.replyFormat || {}));
}

function loadCharacterSectionsForOwner(owner, characterId) {
	if (normalizeCharacterId(characterId) <= 0) return null;
	return readStored(characterStorageKey(characterId, owner));
}

function loadCharacterSections(characterId) {
	return loadCharacterSectionsForOwner(ownerKey(), characterId);
}

function loadConfigForOwner(owner, characterId) {
	try {
		var raw = readStored(storageKey(owner));
		if (!raw) {
			raw = readStored(legacyStorageKey(owner));
		}
		var globalConfig = normalizeConfig(raw);
		return mergeSections(globalConfig, loadCharacterSectionsForOwner(owner, characterId));
	} catch (e) {
		return normalizeConfig(null);
	}
}

function loadConfig(characterId) {
	return loadConfigForOwner(ownerKey(), characterId);
}

function scopePayload(source, characterScope) {
	var raw = source && typeof source === 'object' ? source : {};
	var result = {};
	['bubble', 'reading', 'replyFormat'].forEach(function (key) {
		if (raw[key] && typeof raw[key] === 'object') result[key] = raw[key];
		else if (characterScope) result[key] = null;
	});
	return result;
}

function stableValue(value) {
	if (Array.isArray(value)) return value.map(stableValue);
	if (!value || typeof value !== 'object') return value;
	var result = {};
	Object.keys(value).sort().forEach(function (key) { result[key] = stableValue(value[key]); });
	return result;
}

function payloadsEqual(left, right, characterScope) {
	return JSON.stringify(stableValue(scopePayload(left, characterScope))) === JSON.stringify(stableValue(scopePayload(right, characterScope)));
}

function writeScopePayload(context, payload) {
	if (context.characterId > 0) {
		var characterSections = scopePayload(payload, true);
		var stored = {};
		['bubble', 'reading', 'replyFormat'].forEach(function (key) {
			if (characterSections[key] && typeof characterSections[key] === 'object') stored[key] = characterSections[key];
		});
		if (Object.keys(stored).length) uni.setStorageSync(characterStorageKey(context.characterId, context.owner), stored);
		else uni.removeStorageSync(characterStorageKey(context.characterId, context.owner));
	} else {
		uni.setStorageSync(storageKey(context.owner), mergeSections(normalizeConfig(null), payload));
	}
	return loadConfigForOwner(context.owner, context.characterId);
}

function emitCurrentAppearance(context, config) {
	if (isSyncContextActive(context) && uni && typeof uni.$emit === 'function') {
		uni.$emit('tavern-chat-appearance-changed', normalizeConfig(config));
	}
}

function applyCloudPayload(payload, context) {
	if (!isSyncContextActive(context)) return loadConfigForOwner(context.owner, context.characterId);
	var data = payload && typeof payload === 'object' ? payload : {};
	var globalSections = data.global && typeof data.global === 'object' ? data.global : {};
	var globalState = readSyncStateFor(context.owner, 0);
	var globalRevision = Math.max(0, Number(globalSections.revision || 0));
	if (!globalState.pending && globalRevision >= globalState.revision) {
		uni.setStorageSync(storageKey(context.owner), mergeSections(normalizeConfig(null), globalSections));
		writeSyncStateFor(context.owner, 0, Object.assign({}, globalState, {
			revision: globalRevision,
			conflict: null,
			lastError: ''
		}));
	}
	if (context.characterId > 0) {
		var characterSections = data.character && typeof data.character === 'object' ? data.character : {};
		var characterState = readSyncStateFor(context.owner, context.characterId);
		var characterRevision = Math.max(0, Number(characterSections.revision || 0));
		if (!characterState.pending && characterRevision >= characterState.revision) {
			writeScopePayload(context, characterSections);
			writeSyncStateFor(context.owner, context.characterId, Object.assign({}, characterState, {
				revision: characterRevision,
				conflict: null,
				lastError: ''
			}));
		}
	}
	return loadConfigForOwner(context.owner, context.characterId);
}

function queuePending(context, payload) {
	var state = readSyncStateFor(context.owner, context.characterId);
	var expectedRevision = state.pending && state.pending.expectedRevision != null
		? Math.max(0, Number(state.pending.expectedRevision || 0))
		: state.revision;
	return writeSyncStateFor(context.owner, context.characterId, Object.assign({}, state, {
		pending: {
			payload: scopePayload(payload, context.characterId > 0),
			expectedRevision: expectedRevision,
			updatedAt: Date.now()
		},
		conflict: null,
		lastError: ''
	}));
}

function isConflictError(error) {
	return !!(error && (Number(error.statusCode) === 409 || Number(error.response && error.response.status) === 409 || error.code === 'CONFLICT'));
}

function markPendingError(context, errorCode) {
	var state = readSyncStateFor(context.owner, context.characterId);
	if (!state.pending) return state;
	return writeSyncStateFor(context.owner, context.characterId, Object.assign({}, state, { lastError: errorCode || 'network' }));
}

function reconcileConflict(context, pending, originalError, rejectConflict) {
	return tavernApi.fetchChatPreferences(context.characterId || null, context.session).then(function (latest) {
		if (!isSyncContextActive(context)) return loadConfigForOwner(context.owner, context.characterId);
		var scope = context.characterId > 0 ? (latest && latest.character) : (latest && latest.global);
		var serverScope = scope && typeof scope === 'object' ? scope : {};
		var serverRevision = Math.max(0, Number(serverScope.revision || 0));
		var state = readSyncStateFor(context.owner, context.characterId);
		if (!state.pending || state.pending.updatedAt !== pending.updatedAt) {
			return loadConfigForOwner(context.owner, context.characterId);
		}
		if (payloadsEqual(serverScope, pending.payload, context.characterId > 0)) {
			writeSyncStateFor(context.owner, context.characterId, Object.assign({}, state, {
				revision: serverRevision,
				pending: null,
				conflict: null,
				lastError: ''
			}));
			writeScopePayload(context, serverScope);
			applyCloudPayload(latest, context);
			emitCurrentAppearance(context, loadConfigForOwner(context.owner, context.characterId));
			return loadConfigForOwner(context.owner, context.characterId);
		}
		writeSyncStateFor(context.owner, context.characterId, Object.assign({}, state, {
			conflict: {
				serverRevision: serverRevision,
				serverPayload: scopePayload(serverScope, context.characterId > 0),
				detectedAt: Date.now()
			},
			lastError: 'conflict'
		}));
		if (rejectConflict) {
			originalError.preferenceConflict = true;
			throw originalError;
		}
		return loadConfigForOwner(context.owner, context.characterId);
	}).catch(function (error) {
		if (error && error.preferenceConflict) throw error;
		if (isSyncContextActive(context)) markPendingError(context, 'conflict-refresh-failed');
		if (rejectConflict && isConflictError(originalError)) {
			originalError.preferenceConflict = true;
			throw originalError;
		}
		return loadConfigForOwner(context.owner, context.characterId);
	});
}

function processPending(context, rejectConflict) {
	var state = readSyncStateFor(context.owner, context.characterId);
	var pending = state.pending;
	if (!pending) return Promise.resolve(loadConfigForOwner(context.owner, context.characterId));
	var body = Object.assign({ clientSchemaVersion: BUBBLE_SCHEMA_VERSION }, pending.payload || {}, {
		expectedRevision: Math.max(0, Number(pending.expectedRevision || 0))
	});
	return tavernApi.saveChatPreferences(context.characterId || null, body, context.session).then(function (cloud) {
		if (!isSyncContextActive(context)) return loadConfigForOwner(context.owner, context.characterId);
		var current = readSyncStateFor(context.owner, context.characterId);
		if (!current.pending || current.pending.updatedAt !== pending.updatedAt) {
			return loadConfigForOwner(context.owner, context.characterId);
		}
		var savedScope = context.characterId > 0 ? (cloud && cloud.character) : (cloud && cloud.global);
		var revision = Math.max(0, Number(savedScope && savedScope.revision || 0));
		writeSyncStateFor(context.owner, context.characterId, Object.assign({}, current, {
			revision: revision,
			pending: null,
			conflict: null,
			lastError: ''
		}));
		var config = applyCloudPayload(cloud, context);
		emitCurrentAppearance(context, config);
		return config;
	}).catch(function (error) {
		if (!isSyncContextActive(context) || (error && error.staleSession)) {
			return loadConfigForOwner(context.owner, context.characterId);
		}
		if (isConflictError(error)) return reconcileConflict(context, pending, error, rejectConflict === true);
		markPendingError(context, 'network');
		return loadConfigForOwner(context.owner, context.characterId);
	});
}

function ensureLegacyPending(context) {
	var state = readSyncStateFor(context.owner, context.characterId);
	if (state.pending || state.revision > 0) return state;
	var local = context.characterId > 0
		? loadCharacterSectionsForOwner(context.owner, context.characterId)
		: (readStored(storageKey(context.owner)) || readStored(legacyStorageKey(context.owner)));
	if (!local) return state;
	var payload = context.characterId > 0 ? scopePayload(local, true) : splitSections(local);
	return queuePending(context, payload);
}

function syncFromCloud(characterId, options) {
	var context = beginSyncContext(characterId, options);
	if (!tavernApi.hasLoggedInUser || !tavernApi.hasLoggedInUser() || !tavernApi.fetchChatPreferences || !tavernApi.saveChatPreferences) {
		return Promise.resolve(loadConfigForOwner(context.owner, context.characterId));
	}
	var state = ensureLegacyPending(context);
	if (state.pending) return processPending(context, false);
	return tavernApi.fetchChatPreferences(context.characterId || null, context.session).then(function (payload) {
		if (!isSyncContextActive(context)) return loadConfigForOwner(context.owner, context.characterId);
		return applyCloudPayload(payload, context);
	}).catch(function (error) {
		if (isSyncContextActive(context) && !(error && error.staleSession)) {
			var current = readSyncStateFor(context.owner, context.characterId);
			writeSyncStateFor(context.owner, context.characterId, Object.assign({}, current, { lastError: 'network' }));
		}
		return loadConfigForOwner(context.owner, context.characterId);
	});
}

function saveCloudConfig(config, options) {
	var opts = options && typeof options === 'object' ? options : {};
	var context = beginSyncContext(opts.characterId, opts);
	var characterId = context.characterId;
	var sections = splitSections(config);
	var payload = sections;
	if (characterId > 0) {
		payload = {
			bubble: opts.inheritBubble ? null : sections.bubble,
			reading: opts.inheritReading ? null : sections.reading,
			replyFormat: opts.inheritReplyFormat ? null : sections.replyFormat
		};
		var localSections = {};
		if (payload.bubble) localSections.bubble = payload.bubble;
		if (payload.reading) localSections.reading = payload.reading;
		if (payload.replyFormat) localSections.replyFormat = payload.replyFormat;
		writeScopePayload(context, localSections);
	} else {
		writeScopePayload(context, payload);
	}
	queuePending(context, payload);
	var localConfig = loadConfigForOwner(context.owner, characterId);
	emitCurrentAppearance(context, localConfig);
	if (!tavernApi.hasLoggedInUser || !tavernApi.hasLoggedInUser() || !tavernApi.saveChatPreferences) {
		return Promise.resolve(localConfig);
	}
	return processPending(context, true);
}

function retryPending(characterId, options) {
	var context = beginSyncContext(characterId, options);
	if (!tavernApi.hasLoggedInUser || !tavernApi.hasLoggedInUser() || !tavernApi.saveChatPreferences) {
		return Promise.resolve(loadConfigForOwner(context.owner, context.characterId));
	}
	return processPending(context, true);
}

function resolveSyncConflict(characterId, strategy, options) {
	var context = beginSyncContext(characterId, options);
	var state = readSyncStateFor(context.owner, context.characterId);
	if (!state.conflict || !state.pending) return Promise.resolve(loadConfigForOwner(context.owner, context.characterId));
	if (strategy === 'cloud') {
		writeScopePayload(context, state.conflict.serverPayload || {});
		writeSyncStateFor(context.owner, context.characterId, Object.assign({}, state, {
			revision: Math.max(0, Number(state.conflict.serverRevision || 0)),
			pending: null,
			conflict: null,
			lastError: ''
		}));
		var cloudConfig = loadConfigForOwner(context.owner, context.characterId);
		emitCurrentAppearance(context, cloudConfig);
		return Promise.resolve(cloudConfig);
	}
	var pending = Object.assign({}, state.pending, {
		expectedRevision: Math.max(0, Number(state.conflict.serverRevision || 0)),
		updatedAt: Date.now()
	});
	writeSyncStateFor(context.owner, context.characterId, Object.assign({}, state, {
		pending: pending,
		conflict: null,
		lastError: ''
	}));
	return processPending(context, true);
}

function saveConfig(config) {
	var next = normalizeConfig(config);
	uni.setStorageSync(storageKey(), next);
	if (uni && typeof uni.$emit === 'function') {
		uni.$emit('tavern-chat-appearance-changed', next);
	}
	return next;
}

function resetConfig() {
	var next = normalizeConfig(null);
	uni.removeStorageSync(storageKey());
	uni.removeStorageSync(legacyStorageKey());
	if (uni && typeof uni.$emit === 'function') {
		uni.$emit('tavern-chat-appearance-changed', next);
	}
	return next;
}

function applyPreset(config, presetCode) {
	var base = normalizeConfig(config);
	var preset = presetByCode(presetCode) || PRESETS[0];
	if (preset.system) {
		return normalizeConfig(Object.assign({}, LEGACY_CLASSIC_BUBBLE_CONFIG, {
			preset: 'system',
			bubbleCustomized: false,
			customized: false,
			textColorOverrides: {},
			readMode: base.readMode,
			showSegmentLabels: base.showSegmentLabels,
			replySplitMode: base.replySplitMode
		}));
	}
	var next = Object.assign({}, presetBubbleConfig(preset.code), {
		readMode: base.readMode,
		showSegmentLabels: base.showSegmentLabels,
		replySplitMode: base.replySplitMode,
		textColorOverrides: {}
	}, {
		preset: preset.code,
		bubbleCustomized: true,
		customized: true
	});
	return normalizeConfig(next);
}

function isTextColorKey(key) {
	return TEXT_COLOR_KEYS.indexOf(String(key || '')) >= 0;
}

function setTextColorOverride(config, key, value) {
	var next = setBubbleCustomized(config, true);
	if (!isTextColorKey(key)) {
		return next;
	}
	var color = normalizeHexColor(value, next[key]);
	var overrides = normalizeTextColorOverrides(next.textColorOverrides);
	overrides[key] = color;
	next[key] = color;
	next.textColorOverrides = overrides;
	return normalizeConfig(next);
}

function clearTextColorOverrides(config) {
	var next = normalizeConfig(config);
	var baseline = next.preset !== CUSTOM_PRESET_CODE
		? presetBubbleConfig(next.preset)
		: BASE_BUBBLE_CONFIG;
	TEXT_COLOR_KEYS.forEach(function (key) {
		next[key] = baseline[key];
	});
	next.textColorOverrides = {};
	return normalizeConfig(next);
}

function hasTextColorOverrides(config) {
	return Object.keys(normalizeTextColorOverrides(normalizeConfig(config).textColorOverrides)).length > 0;
}

function isBubbleCustomized(config) {
	return normalizeConfig(config).bubbleCustomized === true;
}

function setBubbleCustomized(config, enabled) {
	var next = normalizeConfig(config);
	if (enabled === true && !next.bubbleCustomized) {
		return applyPreset(next, 'fengyue');
	}
	if (enabled !== true) {
		return applyPreset(next, 'system');
	}
	next.bubbleCustomized = enabled === true;
	next.customized = next.bubbleCustomized;
	next.preset = next.bubbleCustomized ? next.preset : 'system';
	return normalizeConfig(next);
}

function bubbleBackground(hex, opacityPercent, surfaceMode) {
	var alpha = clampNumber(opacityPercent, 0, 100, DEFAULT_CONFIG.opacity) / 100;
	if (surfaceMode === 'flat') return rgba(hex, alpha);
	var delta = surfaceMode === 'softGradient' ? 0.035 : 0.08;
	return 'linear-gradient(180deg,' + rgba(hex, alpha) + ' 0%,' + rgba(hex, Math.max(0.28, alpha - delta)) + ' 100%)';
}

function bubbleSurface(config, isUser) {
	var c = normalizeConfig(config);
	var borderColor = isUser ? c.userBorderColor : c.charBorderColor;
	var shadowAlpha = Math.max(0, Math.min(0.22, c.shadowStrength / 500));
	var shadow = c.shadowStrength > 0
		? '0 ' + runtimeRpx(c.surfaceMode === 'legacyGlass' ? 8 : 4) + ' ' + runtimeRpx(c.surfaceMode === 'legacyGlass' ? 22 : 14) + ' rgba(0,0,0,' + shadowAlpha.toFixed(3) + ')'
		: 'none';
	if (c.surfaceMode === 'legacyGlass') {
		shadow += ',inset 0 ' + runtimeRpx(1) + ' 0 rgba(255,255,255,0.12)';
	}
	return {
		background: bubbleBackground(isUser ? c.userBubbleColor : c.charBubbleColor, c.opacity, c.surfaceMode),
		border: rgba(borderColor, c.surfaceBorderOpacity / 100),
		sideBorder: rgba(borderColor, c.sideBorderOpacity / 100),
		shadow: shadow
	};
}

function bubbleRadius(config) {
	var radius = runtimeRpx(normalizeConfig(config).radius);
	return [radius, radius, radius, radius].join(' ');
}

function buildCssVars(config) {
	var c = normalizeConfig(config);
	var charSurface = bubbleSurface(c, false);
	var userSurface = bubbleSurface(c, true);
	var charContent = buildEmbeddedContentTokens(c, false);
	var userContent = buildEmbeddedContentTokens(c, true);
	return {
		'--chat-bubble-font-size': runtimeTextSize(c.fontSize),
		'--chat-bubble-line-height': String(c.lineHeight),
		'--chat-bubble-base-weight': String(c.baseFontWeight),
		'--chat-bubble-user-weight': String(c.userFontWeight),
		'--chat-bubble-speech-weight': String(c.speechFontWeight),
		'--chat-bubble-action-weight': String(c.actionFontWeight),
		'--chat-bubble-thought-weight': String(c.thoughtFontWeight),
		'--chat-bubble-narration-weight': String(c.narrationFontWeight),
		'--chat-bubble-padding': runtimeRpx(c.bubblePaddingY) + ' ' + runtimeRpx(c.bubblePaddingX),
		'--chat-bubble-radius-char': bubbleRadius(c),
		'--chat-bubble-radius-user': bubbleRadius(c),
		'--chat-bubble-char-max-width': c.charMaxWidth + '%',
		'--chat-bubble-user-max-width': c.userMaxWidth + '%',
		'--chat-bubble-image-padding': runtimeRpx(c.imagePadding),
		'--chat-bubble-char-bg': charSurface.background,
		'--chat-bubble-user-bg': userSurface.background,
		'--chat-bubble-char-border': charSurface.sideBorder,
		'--chat-bubble-user-border': userSurface.sideBorder,
		'--chat-bubble-char-surface-border': charSurface.border,
		'--chat-bubble-user-surface-border': userSurface.border,
		'--chat-bubble-char-shadow': charSurface.shadow,
		'--chat-bubble-user-shadow': userSurface.shadow,
		'--chat-bubble-text': c.baseTextColor,
		'--chat-bubble-user-text': c.userTextColor,
		'--chat-bubble-speech': c.speechColor,
		'--chat-bubble-action': c.actionColor,
		'--chat-bubble-thought': c.thoughtColor,
		'--chat-bubble-narration': c.narrationColor,
		'--chat-content-primary': charContent.primary,
		'--chat-content-muted': charContent.muted,
		'--chat-content-surface': charContent.surface,
		'--chat-content-surface-strong': charContent.surfaceStrong,
		'--chat-content-border': charContent.border,
		'--chat-content-accent': charContent.accent,
		'--chat-content-danger': charContent.danger,
		'--chat-user-content-primary': userContent.primary,
		'--chat-user-content-muted': userContent.muted,
		'--chat-user-content-surface': userContent.surface,
		'--chat-user-content-border': userContent.border,
		'--chat-user-content-accent': userContent.accent,
		'--chat-bg-readable-overlay': 'rgba(0,0,0,' + (c.backdropStrength / 100).toFixed(3) + ')'
	};
}

function buildStyleText(vars) {
	return Object.keys(vars || {}).map(function (key) {
		return key + ':' + vars[key];
	}).join(';') + ';';
}

function buildAppBubbleStyle(message, config) {
	var c = normalizeConfig(config);
	if (!message) {
		return '';
	}
	var isUser = message.role === 'user';
	var surface = bubbleSurface(c, isUser);
	var textColor = isUser ? c.userTextColor : c.baseTextColor;
	var sideBorder = c.sideBorderWidth > 0
		? (isUser
			? 'border-right:' + runtimeRpx(c.sideBorderWidth) + ' solid ' + surface.sideBorder + '!important'
			: 'border-left:' + runtimeRpx(c.sideBorderWidth) + ' solid ' + surface.sideBorder + '!important')
		: (isUser ? 'border-right:0!important' : 'border-left:0!important');
	var blur = c.blurRadius > 0 ? 'blur(' + runtimeRpx(c.blurRadius) + ') saturate(104%)' : 'none';
	return [
		'max-width:' + (isUser ? c.userMaxWidth : c.charMaxWidth) + '% !important',
		'margin:0 ' + runtimeRpx(8) + '!important',
		'padding:' + runtimeRpx(c.bubblePaddingY) + ' ' + runtimeRpx(c.bubblePaddingX) + ' !important',
		'border-radius:' + bubbleRadius(c) + ' !important',
		'background:' + surface.background + '!important',
		'border:' + runtimeRpx(1) + ' solid ' + surface.border + '!important',
		sideBorder,
		'box-shadow:' + surface.shadow + '!important',
		'backdrop-filter:' + blur + '!important',
		'-webkit-backdrop-filter:' + blur + '!important',
		'color:' + textColor + '!important',
		'box-sizing:border-box!important'
	].join(';') + ';';
}

function buildBubbleStyleObject(message, config) {
	var c = normalizeConfig(config);
	if (!message) {
		return {};
	}
	var isUser = message.role === 'user';
	var novelLayout = !isUser && c.readMode === 'novel' && !isBubbleCustomized(c);
	var surface = bubbleSurface(c, isUser);
	var textColor = isUser ? c.userTextColor : c.baseTextColor;
	var content = buildEmbeddedContentTokens(c, isUser);
	var blur = c.blurRadius > 0 ? 'blur(' + runtimeRpx(c.blurRadius) + ') saturate(104%)' : 'none';
	var style = {
		'max-width': novelLayout ? '90%' : runtimeBubbleMaxWidth(isUser ? c.userMaxWidth : c.charMaxWidth, isUser),
		'margin': '0 ' + runtimeRpx(8),
		'padding': novelLayout
			? runtimeRpx(26) + ' ' + runtimeRpx(28)
			: runtimeRpx(c.bubblePaddingY) + ' ' + runtimeRpx(c.bubblePaddingX),
		'border-radius': novelLayout ? runtimeRpx(16) : bubbleRadius(c),
		'background': surface.background,
		'border': runtimeRpx(1) + ' solid ' + surface.border,
		'box-shadow': surface.shadow,
		'backdrop-filter': blur,
		'-webkit-backdrop-filter': blur,
		'color': textColor,
		'--chat-content-primary': content.primary,
		'--chat-content-muted': content.muted,
		'--chat-content-surface': content.surface,
		'--chat-content-surface-strong': content.surfaceStrong,
		'--chat-content-border': content.border,
		'--chat-content-accent': content.accent,
		'--chat-content-danger': content.danger,
		'box-sizing': 'border-box',
		'overflow': 'visible'
	};
	style[isUser ? 'border-right' : 'border-left'] = c.sideBorderWidth > 0
		? runtimeRpx(c.sideBorderWidth) + ' solid ' + surface.sideBorder
		: '0';
	return style;
}

function buildImageBubbleStyleObject(config) {
	var c = normalizeConfig(config);
	return {
		'padding': runtimeRpx(c.imagePadding),
		'background': 'transparent',
		'border': '0',
		'border-radius': runtimeRpx(Math.max(8, Math.round(c.radius * 0.7))),
		'box-shadow': 'none',
		'backdrop-filter': 'none',
		'-webkit-backdrop-filter': 'none',
		'box-sizing': 'border-box',
		'overflow': 'visible'
	};
}

function buildMessageTextStyle(message, config) {
	var c = normalizeConfig(config);
	var role = typeof message === 'string' ? message : (message && message.role);
	var color = role === 'user' ? c.userTextColor : c.baseTextColor;
	var weight = role === 'user' ? c.userFontWeight : c.baseFontWeight;
	return [
		'color:' + color + '!important',
		'-webkit-text-fill-color:' + color + '!important',
		'font-size:' + runtimeTextSize(c.fontSize) + '!important',
		'line-height:' + c.lineHeight + '!important',
		'font-weight:' + weight + '!important',
		'letter-spacing:0!important'
	].join(';') + ';';
}

function buildMessageTextStyleObject(message, config) {
	var c = normalizeConfig(config);
	var role = typeof message === 'string' ? message : (message && message.role);
	var color = role === 'user' ? c.userTextColor : c.baseTextColor;
	var novelLayout = role !== 'user' && c.readMode === 'novel' && !isBubbleCustomized(c);
	var weight = role === 'user' ? c.userFontWeight : c.baseFontWeight;
	return {
		'display': 'block',
		'max-width': '100%',
		'min-width': '0',
		'color': color,
		'-webkit-text-fill-color': color,
		'font-size': runtimeTextSize(novelLayout ? Math.max(34, c.fontSize) : c.fontSize),
		'line-height': String(novelLayout ? 1.92 : c.lineHeight),
		'font-weight': String(weight),
		'letter-spacing': '0',
		'text-shadow': 'none',
		'word-break': 'break-word',
		'overflow-wrap': 'break-word',
		'white-space': 'pre-wrap'
	};
}

function buildAssistantSplitBubbleStyle(config, index, total) {
	var c = normalizeConfig(config);
	var surface = bubbleSurface(c, false);
	var position = splitBubblePosition(index, total);
	return [
		'display:block',
		'width:auto',
		'max-width:100%',
		'padding:' + runtimeRpx(c.bubblePaddingY) + ' ' + runtimeRpx(c.bubblePaddingX) + ' !important',
		'border-radius:' + splitBubbleRadius(c, position) + ' !important',
		'background:' + surface.background + '!important',
		'border:' + runtimeRpx(1) + ' solid ' + surface.border + '!important',
		'border-left:' + (c.sideBorderWidth > 0 ? runtimeRpx(c.sideBorderWidth) + ' solid ' + surface.sideBorder : '0') + '!important',
		'box-shadow:' + splitBubbleShadow(c, surface.shadow, position) + '!important',
		'color:' + c.baseTextColor + '!important',
		'box-sizing:border-box'
	].join(';') + ';';
}

function splitBubblePosition(index, total) {
	var safeIndex = Math.max(0, Number(index) || 0);
	var safeTotal = Math.max(1, Number(total) || 1);
	if (safeTotal <= 1) return 'single';
	if (safeIndex <= 0) return 'first';
	if (safeIndex >= safeTotal - 1) return 'last';
	return 'middle';
}

function splitBubbleRadius(config, position) {
	var radius = Number(normalizeConfig(config).radius || 20);
	var full = runtimeRpx(radius);
	var linked = runtimeRpx(Math.max(10, Math.round(radius * 0.62)));
	if (position === 'first') return [full, full, full, linked].join(' ');
	if (position === 'last') return [linked, full, full, full].join(' ');
	if (position === 'middle') return [linked, full, full, linked].join(' ');
	return [full, full, full, full].join(' ');
}

function splitBubbleShadow(config, fallback, position) {
	var c = normalizeConfig(config);
	if (position !== 'middle' || c.shadowStrength <= 0) return fallback;
	var alpha = Math.max(0, Math.min(0.12, c.shadowStrength / 760));
	var shadow = '0 ' + runtimeRpx(c.surfaceMode === 'legacyGlass' ? 4 : 2) + ' ' + runtimeRpx(c.surfaceMode === 'legacyGlass' ? 12 : 8) + ' rgba(0,0,0,' + alpha.toFixed(3) + ')';
	if (c.surfaceMode === 'legacyGlass') {
		shadow += ',inset 0 ' + runtimeRpx(1) + ' 0 rgba(255,255,255,0.08)';
	}
	return shadow;
}

function buildSplitBubbleStyleObject(config, index, total) {
	var c = normalizeConfig(config);
	var surface = bubbleSurface(c, false);
	var content = buildEmbeddedContentTokens(c, false);
	var blur = c.blurRadius > 0 ? 'blur(' + runtimeRpx(c.blurRadius) + ') saturate(104%)' : 'none';
	var position = splitBubblePosition(index, total);
	return {
		'display': 'block',
		'width': 'auto',
		'max-width': '100%',
		'min-width': '0',
		'padding': runtimeRpx(c.bubblePaddingY) + ' ' + runtimeRpx(c.bubblePaddingX),
		'border-radius': splitBubbleRadius(c, position),
		'background': surface.background,
		'border': runtimeRpx(1) + ' solid ' + surface.border,
		'border-left': c.sideBorderWidth > 0 ? runtimeRpx(c.sideBorderWidth) + ' solid ' + surface.sideBorder : '0',
		'box-shadow': splitBubbleShadow(c, surface.shadow, position),
		'backdrop-filter': blur,
		'-webkit-backdrop-filter': blur,
		'color': c.baseTextColor,
		'--chat-content-primary': content.primary,
		'--chat-content-muted': content.muted,
		'--chat-content-surface': content.surface,
		'--chat-content-surface-strong': content.surfaceStrong,
		'--chat-content-border': content.border,
		'--chat-content-accent': content.accent,
		'--chat-content-danger': content.danger,
		'box-sizing': 'border-box',
		'overflow': 'visible'
	};
}

function buildSplitHostStyleObject(config) {
	var c = normalizeConfig(config);
	return {
		'display': 'block',
		'width': runtimeBubbleMaxWidth(c.charMaxWidth, false),
		'max-width': runtimeBubbleMaxWidth(c.charMaxWidth, false),
		'min-width': '0',
		'padding': '0',
		'border': '0',
		'border-radius': '0',
		'background': 'transparent',
		'box-shadow': 'none',
		'overflow': 'visible',
		'box-sizing': 'border-box'
	};
}

function segmentColor(type, config) {
	var colors = buildSegmentColors(config);
	return colors[type] || colors.narration;
}

function buildSegmentColors(config) {
	var c = normalizeConfig(config);
	return {
		speech: c.speechColor,
		action: c.actionColor,
		thought: c.thoughtColor,
		narration: c.narrationColor
	};
}

function buildSegmentWeights(config) {
	var c = normalizeConfig(config);
	return {
		speech: c.speechFontWeight,
		action: c.actionFontWeight,
		thought: c.thoughtFontWeight,
		narration: c.narrationFontWeight
	};
}

function segmentFontWeight(type, config) {
	var c = normalizeConfig(config);
	if (type === 'speech') return c.speechFontWeight;
	if (type === 'action') return c.actionFontWeight;
	if (type === 'thought') return c.thoughtFontWeight;
	return c.narrationFontWeight;
}

function buildSegmentTextStyleObject(type, config) {
	var c = normalizeConfig(config);
	var normalizedType = ['speech', 'action', 'thought', 'narration', 'rich'].indexOf(type) >= 0 ? type : 'narration';
	return {
		color: segmentColor(normalizedType, c),
		fontWeight: String(segmentFontWeight(normalizedType, c)),
		fontStyle: normalizedType === 'thought' && c.thoughtItalic ? 'italic' : 'normal',
		opacity: c.readMode === 'softAction' && (normalizedType === 'action' || normalizedType === 'narration') ? 0.58 : 1
	};
}

function buildSegmentAccentSurface(type, config) {
	var color = segmentColor(type, config);
	return {
		border: rgba(color, 0.34),
		background: rgba(color, 0.1),
		text: color,
		fontWeight: '600'
	};
}

function segmentLabel(type) {
	if (type === 'speech') return '台词';
	if (type === 'action') return '动作';
	if (type === 'thought') return '心理';
	if (type === 'rich') return '状态';
	return '旁白';
}

function splitSentences(text) {
	var source = String(text || '').replace(/\r\n?/g, '\n').replace(/[ \t]+/g, ' ').trim();
	if (!source) {
		return [];
	}
	var matches = source.match(/[^。！？!?；;.\n]+[。！？!?；;.]?["”」』]?/g);
	if (!matches || !matches.length) {
		return [source];
	}
	return matches.map(function (item) { return item.trim(); }).filter(Boolean);
}

function bubbleVisualLength(text) {
	var source = String(text || '');
	var length = 0;
	Array.from(source).forEach(function (character) {
		if (/\s/.test(character)) {
			length += 0.2;
		} else if (/[\x00-\x7f]/.test(character)) {
			length += /[A-Za-z0-9]/.test(character) ? 0.55 : 0.4;
		} else if (/[，。！？；：、“”‘’（）【】《》…—]/.test(character)) {
			length += 0.55;
		} else {
			length += 1;
		}
	});
	return length;
}

function semanticWrapper(text, type) {
	var source = String(text || '').trim();
	var pairs = type === 'speech'
		? [['“', '”'], ['"', '"'], ['「', '」'], ['『', '』']]
		: type === 'thought'
			? [['（', '）'], ['(', ')']]
			: type === 'action'
				? [['*', '*']]
				: [];
	for (var i = 0; i < pairs.length; i += 1) {
		var pair = pairs[i];
		if (source.length > 2 && source.charAt(0) === pair[0] && source.charAt(source.length - 1) === pair[1]) {
			return { open: pair[0], body: source.slice(1, -1).trim(), close: pair[1] };
		}
	}
	return { open: '', body: source, close: '' };
}

function groupSemanticSentences(sentences, targetLength, maxCount) {
	var groups = [];
	var current = [];
	var currentLength = 0;
	(sentences || []).forEach(function (sentence) {
		var text = String(sentence || '').trim();
		if (!text) return;
		var nextLength = bubbleVisualLength(text);
		if (current.length && (current.length >= maxCount || currentLength + nextLength > targetLength)) {
			groups.push(current.join(''));
			current = [];
			currentLength = 0;
		}
		current.push(text);
		currentLength += nextLength;
	});
	if (current.length) groups.push(current.join(''));
	return groups;
}

function splitLongSemanticUnit(unit) {
	var source = unit && unit.text ? String(unit.text).trim() : '';
	if (!source || bubbleVisualLength(source) <= 60) return source ? [unit] : [];
	var wrapper = semanticWrapper(source, unit.type);
	var sentences = splitSentences(wrapper.body);
	if (sentences.length <= 1) {
		var clauses = wrapper.body.match(/[^，,、：:\n]+[，,、：:]?/g);
		if (clauses && clauses.length > 1) sentences = clauses.map(function (item) { return item.trim(); }).filter(Boolean);
	}
	if (sentences.length <= 1) return [unit];
	return groupSemanticSentences(sentences, 48, 2).map(function (group, index) {
		return {
			type: unit.type,
			text: wrapper.open + group + wrapper.close,
			lineBreakBefore: index === 0 ? unit.lineBreakBefore === true : true
		};
	});
}

function normalizeBubbleSemanticType(type, text) {
	var normalized = ['speech', 'action', 'thought', 'narration'].indexOf(type) >= 0 ? type : 'narration';
	var source = String(text || '').trim();
	if (
		normalized === 'action'
		&& (/^（[\s\S]+）$/.test(source) || /^\([\s\S]+\)$/.test(source))
		&& /(?:明明|其实|却|不敢|希望|担心|害怕|后悔|为什么|怎么办|觉得|意识到|记得|知道|没想到|想要|心里|内心|心底|脑海|思绪|念头|暗自|默默|think|thought|wonder|hope|wish|afraid)/i.test(source)
	) {
		return 'thought';
	}
	return normalized;
}

function semanticUnitsForBubble(text) {
	var lines = String(text || '').replace(/\r\n?/g, '\n')
		.split(/\n+/)
		.map(function (line) { return line.trim(); })
		.filter(Boolean);
	var units = [];
	lines.forEach(function (line, lineIndex) {
		var parsed = typeof chatMarkdown.splitChatSegments === 'function'
			? chatMarkdown.splitChatSegments(line)
			: [];
		if (!parsed.length) parsed = [{ type: 'narration', text: line }];
		parsed.forEach(function (segment, segmentIndex) {
			if (!segment || !String(segment.text || '').trim()) return;
			var type = normalizeBubbleSemanticType(segment.type, segment.text);
			var expanded = splitLongSemanticUnit({
				type: type,
				text: String(segment.text).trim(),
				lineBreakBefore: lineIndex > 0 && segmentIndex === 0
			});
			units.push.apply(units, expanded);
		});
	});
	return units;
}

function shouldMergeSemanticUnit(current, next) {
	if (!current.length || !next) return false;
	var last = current[current.length - 1];
	if (!last || last.type === 'thought' || next.type === 'thought' || current.length >= 3) return false;
	var currentText = current.map(function (item) { return item.text; }).join('\n');
	var combinedLength = bubbleVisualLength(currentText) + bubbleVisualLength(next.text);
	if (combinedLength > 72) return false;

	var pair = last.type + ':' + next.type;
	if (!next.lineBreakBefore) {
		return ['speech:speech', 'action:action', 'narration:narration'].indexOf(pair) >= 0
			|| ['action:speech', 'speech:action', 'narration:speech', 'speech:narration', 'action:narration', 'narration:action'].indexOf(pair) >= 0;
	}
	if (pair === 'action:speech') return true;
	if (pair === 'speech:action') return bubbleVisualLength(next.text) <= 28;
	if (pair === 'narration:speech') return bubbleVisualLength(last.text) <= 30;
	if (pair === 'speech:narration') return bubbleVisualLength(next.text) <= 22;
	if (pair === 'narration:action' || pair === 'action:narration') return combinedLength <= 52;
	if (last.type === next.type && next.type !== 'speech') return combinedLength <= 46;
	return false;
}

function semanticBubbleChunks(text) {
	var units = semanticUnitsForBubble(text);
	if (!units.length) return [];
	var groups = [];
	var current = [];
	units.forEach(function (unit) {
		if (current.length && !shouldMergeSemanticUnit(current, unit)) {
			groups.push(current);
			current = [];
		}
		current.push(unit);
	});
	if (current.length) groups.push(current);
	return groups.map(function (group) {
		return group.map(function (item) { return item.text; }).join('\n').trim();
	}).filter(Boolean);
}

function limitBubbleChunks(chunks, maxCount) {
	var list = Array.isArray(chunks) ? chunks.slice() : [];
	var limit = Math.max(2, Number(maxCount || 8));
	while (list.length > limit) {
		var mergeIndex = 0;
		var bestScore = Number.POSITIVE_INFINITY;
		for (var i = 0; i < list.length - 1; i += 1) {
			var left = String(list[i] || '');
			var right = String(list[i + 1] || '');
			var structuredPenalty = structuredContent.hasStructuredContent(left) || structuredContent.hasStructuredContent(right) ? 10000 : 0;
			var score = bubbleVisualLength(left) + bubbleVisualLength(right) + structuredPenalty;
			if (score < bestScore) {
				bestScore = score;
				mergeIndex = i;
			}
		}
		list.splice(mergeIndex, 2, String(list[mergeIndex] || '').trim() + '\n\n' + String(list[mergeIndex + 1] || '').trim());
	}
	return list;
}

function splitPlainReplyBubbleTexts(text) {
	var source = String(text == null ? '' : text).replace(/\r\n?/g, '\n').trim();
	if (!source) return [];
	if (source.indexOf('```') >= 0) return [source];

	var paragraphBlocks = source
		.split(/\n\s*\n/)
		.map(function (block) { return block.trim(); })
		.filter(Boolean);

	var chunks = [];
	paragraphBlocks.forEach(function (block) {
		var semanticChunks = semanticBubbleChunks(block);
		chunks.push.apply(chunks, semanticChunks.length ? semanticChunks : [block]);
	});
	chunks = limitBubbleChunks(chunks, 8);
	return chunks.length > 1 ? chunks : [source];
}

function splitReplyBubbleTexts(text, mode) {
	var original = String(text == null ? '' : text);
	var normalizedMode = normalizeReplySplitMode(mode);
	if (normalizedMode !== 'bubble') {
		return original.trim() ? [original] : [];
	}
	var source = original.replace(/\r\n?/g, '\n').trim();
	if (!source) return [];

	var parts = structuredContent.splitStructuredContent(source);
	var hasStructuredPart = parts.some(function (part) { return part && part.type !== 'text'; });
	if (!hasStructuredPart) {
		return splitPlainReplyBubbleTexts(source);
	}

	var chunks = [];
	parts.forEach(function (part) {
		if (!part || !String(part.text || '').trim()) return;
		if (part.type === 'discard') return;
		if (part.type === 'pending') {
			if (structuredContent.stripStructuredMarkupToText(part.text)) {
				chunks.push(String(part.text).trim());
			}
			return;
		}
		if (part.type === 'rich') {
			chunks.push(String(part.text).trim());
			return;
		}
		chunks.push.apply(chunks, splitPlainReplyBubbleTexts(part.text));
	});
	chunks = limitBubbleChunks(chunks, 8);
	return chunks.length ? chunks : [source];
}

function applyReplySplitDisplay(text, mode) {
	var original = String(text == null ? '' : text);
	return original;
}

module.exports = {
	DEFAULT_CONFIG: DEFAULT_CONFIG,
	BASE_BUBBLE_CONFIG: BASE_BUBBLE_CONFIG,
	LEGACY_CLASSIC_BUBBLE_CONFIG: LEGACY_CLASSIC_BUBBLE_CONFIG,
	FENGYUE_BUBBLE_CONFIG: FENGYUE_BUBBLE_CONFIG,
	BUBBLE_SCHEMA_VERSION: BUBBLE_SCHEMA_VERSION,
	PRESETS: PRESETS,
	CUSTOM_PRESET_CODE: CUSTOM_PRESET_CODE,
	TEXT_COLOR_KEYS: TEXT_COLOR_KEYS,
	normalizeConfig: normalizeConfig,
	loadConfig: loadConfig,
	splitSections: splitSections,
	mergeSections: mergeSections,
	loadCharacterSections: loadCharacterSections,
	getSyncState: getSyncState,
	createRequestGuard: createRequestGuard,
	syncFromCloud: syncFromCloud,
	saveCloudConfig: saveCloudConfig,
	retryPending: retryPending,
	resolveSyncConflict: resolveSyncConflict,
	saveConfig: saveConfig,
	resetConfig: resetConfig,
	applyPreset: applyPreset,
	isTextColorKey: isTextColorKey,
	setTextColorOverride: setTextColorOverride,
	clearTextColorOverrides: clearTextColorOverrides,
	hasTextColorOverrides: hasTextColorOverrides,
	isBubbleCustomized: isBubbleCustomized,
	setBubbleCustomized: setBubbleCustomized,
	buildCssVars: buildCssVars,
	buildStyleText: buildStyleText,
	buildAppBubbleStyle: buildAppBubbleStyle,
	buildBubbleStyleObject: buildBubbleStyleObject,
	buildAppBubbleStyleObject: buildBubbleStyleObject,
	buildImageBubbleStyleObject: buildImageBubbleStyleObject,
	buildAppImageBubbleStyleObject: buildImageBubbleStyleObject,
	buildMessageTextStyle: buildMessageTextStyle,
	buildMessageTextStyleObject: buildMessageTextStyleObject,
	buildAppMessageTextStyleObject: buildMessageTextStyleObject,
	buildAssistantSplitBubbleStyle: buildAssistantSplitBubbleStyle,
	buildSplitBubbleStyleObject: buildSplitBubbleStyleObject,
	buildAppSplitBubbleStyleObject: buildSplitBubbleStyleObject,
	buildSplitHostStyleObject: buildSplitHostStyleObject,
	buildAppSplitHostStyleObject: buildSplitHostStyleObject,
	runtimeRpx: runtimeRpx,
	runtimeTextSize: runtimeTextSize,
	runtimeBubbleMaxWidth: runtimeBubbleMaxWidth,
	buildSegmentColors: buildSegmentColors,
	buildSegmentWeights: buildSegmentWeights,
	segmentColor: segmentColor,
	segmentFontWeight: segmentFontWeight,
	buildSegmentTextStyleObject: buildSegmentTextStyleObject,
	buildSegmentAccentSurface: buildSegmentAccentSurface,
	buildEmbeddedContentTokens: buildEmbeddedContentTokens,
	segmentLabel: segmentLabel,
	applyReplySplitDisplay: applyReplySplitDisplay,
	splitReplyBubbleTexts: splitReplyBubbleTexts
};
