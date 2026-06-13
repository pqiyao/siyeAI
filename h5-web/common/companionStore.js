const STORAGE_KEY = 'live2d_companion_config_v1';

const DEFAULT_CONFIG = {
	enabled: false,
	modelId: 'ug',
	modelPath: '/static/live2d/models/ug/ugofficial.model3.json',
	width: 230,
	height: 260,
	scale: 0.22,
	opacity: 1,
	side: 'right',
	x: null,
	y: null,
	showBubble: true,
	chatLink: true,
	clickAction: true
};

const EVENTS = {
	thinking: 'live2d-companion:thinking',
	replying: 'live2d-companion:replying',
	reply: 'live2d-companion:reply',
	error: 'live2d-companion:error',
	configChanged: 'live2d-companion:config-changed',
	layoutChanged: 'live2d-companion:layout-changed'
};

function clampNumber(value, min, max, fallback) {
	const n = Number(value);
	if (!isFinite(n)) return fallback;
	return Math.max(min, Math.min(max, n));
}

function normalizeConfig(raw) {
	const source = raw && typeof raw === 'object' ? raw : {};
	const merged = Object.assign({}, DEFAULT_CONFIG, source);
	merged.enabled = merged.enabled === true;
	merged.width = clampNumber(merged.width, 150, 320, DEFAULT_CONFIG.width);
	merged.height = clampNumber(merged.height, 180, 380, DEFAULT_CONFIG.height);
	merged.scale = clampNumber(merged.scale, 0.12, 0.45, DEFAULT_CONFIG.scale);
	merged.opacity = clampNumber(merged.opacity, 0.35, 1, DEFAULT_CONFIG.opacity);
	merged.side = merged.side === 'left' ? 'left' : 'right';
	merged.showBubble = merged.showBubble !== false;
	merged.chatLink = merged.chatLink !== false;
	merged.clickAction = merged.clickAction !== false;
	merged.modelPath = String(merged.modelPath || DEFAULT_CONFIG.modelPath);
	merged.modelId = String(merged.modelId || DEFAULT_CONFIG.modelId);
	merged.x = merged.x === null || merged.x === undefined ? null : Number(merged.x);
	merged.y = merged.y === null || merged.y === undefined ? null : Number(merged.y);
	if (!isFinite(merged.x)) merged.x = null;
	if (!isFinite(merged.y)) merged.y = null;
	return merged;
}

function getConfig() {
	try {
		return normalizeConfig(uni.getStorageSync(STORAGE_KEY));
	} catch (e) {
		return normalizeConfig();
	}
}

function saveConfig(next) {
	const config = normalizeConfig(Object.assign({}, getConfig(), next || {}));
	try {
		uni.setStorageSync(STORAGE_KEY, config);
		uni.$emit(EVENTS.configChanged, config);
	} catch (e) {}
	return config;
}

function resetConfig() {
	try {
		uni.removeStorageSync(STORAGE_KEY);
		uni.$emit(EVENTS.configChanged, normalizeConfig());
	} catch (e) {}
	return normalizeConfig();
}

function emitThinking(text) {
	uni.$emit(EVENTS.thinking, text || '');
}

function emitReplying(text) {
	uni.$emit(EVENTS.replying, text || '');
}

function emitReply(text) {
	uni.$emit(EVENTS.reply, text || '');
}

function emitError(text) {
	uni.$emit(EVENTS.error, text || '');
}

function emitLayout(layout) {
	uni.$emit(EVENTS.layoutChanged, layout && typeof layout === 'object' ? layout : {});
}

module.exports = {
	STORAGE_KEY,
	DEFAULT_CONFIG,
	EVENTS,
	getConfig,
	saveConfig,
	resetConfig,
	emitThinking,
	emitReplying,
	emitReply,
	emitError,
	emitLayout
};
