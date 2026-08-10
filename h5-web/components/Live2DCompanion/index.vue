<template>
	<view
		v-show="shouldRender"
		class="live2d-companion"
		:class="{ 'live2d-companion--dragging': dragging, 'live2d-companion--left': config.side === 'left' }"
		:style="containerStyle"
		@touchstart.stop="onTouchStart"
		@touchmove.stop.prevent="onTouchMove"
		@touchend.stop="onTouchEnd"
		@tap.stop="onTapCompanion"
		@longpress.stop="toggleMenu"
	>
		<view v-if="bubbleVisible && config.showBubble" class="live2d-companion__bubble">
			<text class="live2d-companion__bubble-text">{{ bubbleText }}</text>
		</view>
		<view v-if="!ready || failed" class="live2d-companion__placeholder" @tap.stop="retryLive2D">
			<image class="live2d-companion__placeholder-icon" src="/static/live2d/models/ug/icon.png" mode="aspectFill"></image>
			<text class="live2d-companion__placeholder-text">{{ failed ? '加载失败' : '加载中' }}</text>
		</view>
		<view ref="stage" class="live2d-companion__stage"></view>
		<!-- #ifdef APP-PLUS -->
		<view
			class="live2d-companion__app-stage"
			:prop="renderState"
			:change:prop="live2dRender.update"
		></view>
		<!-- #endif -->
		<view v-if="menuVisible" class="live2d-companion__menu" @touchstart.stop @tap.stop>
			<view class="live2d-companion__menu-btn" @tap.stop="openSetting">设置</view>
			<view class="live2d-companion__menu-btn" @tap.stop="hideCompanion">隐藏</view>
		</view>
	</view>
</template>

<script>
const companionStore = require('@/common/companionStore.js');

let runtimePromise = null;

function resolveAssetUrl(src) {
	const value = String(src || '');
	if (!value) return value;
	if (/^(https?:|file:|data:|blob:)/i.test(value)) return value;
	if (typeof window === 'undefined' || !window.location) return value;
	const clean = value.replace(/^\/+/, '');
	const plusApi = window.plus || (typeof plus !== 'undefined' ? plus : null);
	if (plusApi && plusApi.io && typeof plusApi.io.convertLocalFileSystemURL === 'function') {
		try {
			return plusApi.io.convertLocalFileSystemURL('_www/' + clean);
		} catch (e) {}
	}
	const protocol = String(window.location.protocol || '').toLowerCase();
	if (protocol === 'file:' || (protocol && protocol !== 'http:' && protocol !== 'https:')) {
		try {
			return new URL(clean, window.location.href).toString();
		} catch (e) {
			return clean;
		}
	}
	return value.charAt(0) === '/' ? value : '/' + clean;
}

function loadScript(src, ready) {
	if (typeof window === 'undefined' || typeof document === 'undefined') {
		return Promise.reject(new Error('Live2D requires WebView DOM'));
	}
	if (ready && ready()) return Promise.resolve();
	const scriptUrl = resolveAssetUrl(src);
	const existing = document.querySelector('script[data-live2d-src="' + scriptUrl + '"]');
	if (existing) {
		if (existing.getAttribute('data-loaded') === '1') return Promise.resolve();
		return new Promise((resolve, reject) => {
			existing.addEventListener('load', resolve, { once: true });
			existing.addEventListener('error', reject, { once: true });
		});
	}
	return new Promise((resolve, reject) => {
		const script = document.createElement('script');
		script.src = scriptUrl;
		script.async = true;
		script.setAttribute('data-live2d-src', scriptUrl);
		script.onload = () => {
			script.setAttribute('data-loaded', '1');
			resolve();
		};
		script.onerror = () => reject(new Error('Failed to load ' + scriptUrl));
		document.head.appendChild(script);
	});
}

function ensureRuntime() {
	if (runtimePromise) return runtimePromise;
	runtimePromise = loadScript('/static/live2d/vendor/pixi.min.js', () => window.PIXI)
		.then(() => loadScript('/static/live2d/vendor/live2dcubismcore.min.js', () => window.Live2DCubismCore))
		.then(() =>
			loadScript('/static/live2d/vendor/pixi-live2d-cubism4.min.js', () => {
				return window.PIXI && window.PIXI.live2d && window.PIXI.live2d.Live2DModel;
			})
		)
		.then(() => {
			return {
				PIXI: window.PIXI,
				Live2DModel: window.PIXI.live2d.Live2DModel,
				MotionPriority: window.PIXI.live2d.MotionPriority || {}
			};
		});
	return runtimePromise;
}

function firstTouch(event) {
	const touches = event && (event.touches || event.changedTouches);
	return touches && touches.length ? touches[0] : event;
}

function shortText(text) {
	const clean = String(text || '').replace(/\s+/g, ' ').trim();
	if (!clean) return '';
	return clean.length > 46 ? clean.slice(0, 46) + '...' : clean;
}

export default {
	name: 'Live2DCompanion',
	props: {
		avoidBottom: {
			type: Number,
			default: 92
		},
		compact: {
			type: Boolean,
			default: false
		},
		active: {
			default: null
		}
	},
	data() {
		return {
			config: companionStore.getConfig(),
			ready: false,
			failed: false,
			x: 0,
			y: 0,
			dragging: false,
			menuVisible: false,
			bubbleVisible: false,
			bubbleText: '',
			touchStartX: 0,
			touchStartY: 0,
			startX: 0,
			startY: 0,
			moved: false,
			app: null,
			model: null,
			bubbleTimer: null,
			idleTimer: null,
			eventHandlers: null,
			renderExpression: '',
			renderActionTick: 0,
			renderRetryTick: 0
		};
	},
	computed: {
		resolvedEnabled() {
			return this.active === null || this.active === undefined ? this.config.enabled : this.active === true;
		},
		shouldRender() {
			return this.resolvedEnabled;
		},
		containerStyle() {
			const width = this.compact ? Math.round(this.config.width * 0.72) : this.config.width;
			const height = this.compact ? Math.round(this.config.height * 0.72) : this.config.height;
			return {
				width: width + 'px',
				height: height + 'px',
				left: this.x + 'px',
				top: this.y + 'px',
				opacity: this.config.opacity
			};
		},
		renderState() {
			const width = this.compact ? Math.round(this.config.width * 0.72) : this.config.width;
			const height = this.compact ? Math.round(this.config.height * 0.72) : this.config.height;
			return {
				enabled: this.shouldRender,
				width,
				height,
				scale: this.config.scale,
				modelPath: this.config.modelPath,
				expression: this.renderExpression,
				actionTick: this.renderActionTick,
				retryTick: this.renderRetryTick
			};
		}
	},
	watch: {
		compact() {
			this.resetDefaultPosition(false);
			this.resizeRenderer();
		},
		active(next, prev) {
			const wasEnabled = prev === null || prev === undefined ? this.config.enabled : prev === true;
			this.syncEnabledState(wasEnabled);
		}
	},
	mounted() {
		this.resetDefaultPosition(false);
		this.bindEvents();
		if (this.shouldRender) {
			this.$nextTick(() => this.initLive2D());
		}
	},
	beforeDestroy() {
		this.unbindEvents();
		this.clearTimers();
		this.destroyLive2D();
	},
	methods: {
		bindEvents() {
			this.eventHandlers = {
				thinking: (text) => this.handleThinking(text),
				replying: (text) => this.handleReplying(text),
				reply: (text) => this.handleReply(text),
				error: (text) => this.handleError(text),
				configChanged: (config) => this.handleConfigChanged(config)
			};
			uni.$on(companionStore.EVENTS.thinking, this.eventHandlers.thinking);
			uni.$on(companionStore.EVENTS.replying, this.eventHandlers.replying);
			uni.$on(companionStore.EVENTS.reply, this.eventHandlers.reply);
			uni.$on(companionStore.EVENTS.error, this.eventHandlers.error);
			uni.$on(companionStore.EVENTS.configChanged, this.eventHandlers.configChanged);
		},
		unbindEvents() {
			if (!this.eventHandlers) return;
			uni.$off(companionStore.EVENTS.thinking, this.eventHandlers.thinking);
			uni.$off(companionStore.EVENTS.replying, this.eventHandlers.replying);
			uni.$off(companionStore.EVENTS.reply, this.eventHandlers.reply);
			uni.$off(companionStore.EVENTS.error, this.eventHandlers.error);
			uni.$off(companionStore.EVENTS.configChanged, this.eventHandlers.configChanged);
			this.eventHandlers = null;
		},
		resetDefaultPosition(persist) {
			const info = uni.getSystemInfoSync();
			const width = this.compact ? Math.round(this.config.width * 0.72) : this.config.width;
			const height = this.compact ? Math.round(this.config.height * 0.72) : this.config.height;
			const margin = 10;
			let x = this.config.x;
			let y = this.config.y;
			if (x === null || y === null) {
				x = this.config.side === 'left' ? margin : info.windowWidth - width - margin;
				y = info.windowHeight - height - this.avoidBottom;
			}
			this.x = Math.max(margin, Math.min(info.windowWidth - width - margin, Number(x)));
			this.y = Math.max(margin, Math.min(info.windowHeight - height - margin, Number(y)));
			if (persist) this.persistPosition();
		},
		persistPosition() {
			this.config = companionStore.saveConfig({
				x: Math.round(this.x),
				y: Math.round(this.y)
			});
		},
		getStageEl() {
			const ref = this.$refs.stage;
			return ref && (ref.$el || ref);
		},
		initLive2D() {
			// #ifdef APP-PLUS
			if (!this.shouldRender) return;
			this.failed = false;
			this.ready = false;
			return;
			// #endif
			if (this.app || !this.shouldRender) return;
			const stageEl = this.getStageEl();
			if (!stageEl) return;
			ensureRuntime()
				.then(({ PIXI, Live2DModel, MotionPriority }) => {
					if (!this.shouldRender || this.app) return null;
					this.app = new PIXI.Application({
						width: this.compact ? Math.round(this.config.width * 0.72) : this.config.width,
						height: this.compact ? Math.round(this.config.height * 0.72) : this.config.height,
						transparent: true,
						backgroundAlpha: 0,
						antialias: true,
						autoDensity: true,
						resolution: Math.min(window.devicePixelRatio || 1, 2)
					});
					stageEl.innerHTML = '';
					stageEl.appendChild(this.app.view);
					return Live2DModel.from(resolveAssetUrl(this.config.modelPath), {
						autoInteract: false,
						autoUpdate: true
					}).then((model) => {
						this.model = model;
						this.app.stage.addChild(model);
						this.fitModel();
						this.ready = true;
						this.playIdle(MotionPriority);
						this.say('我在这里。', 2200);
						return model;
					});
				})
				.catch((e) => {
					console.error('[Live2DCompanion] load failed', e);
					this.destroyLive2D();
					this.failed = true;
				});
		},
		fitModel() {
			if (!this.model || !this.app) return;
			const width = (this.app.screen && this.app.screen.width) || (this.compact ? Math.round(this.config.width * 0.72) : this.config.width);
			const height = (this.app.screen && this.app.screen.height) || (this.compact ? Math.round(this.config.height * 0.72) : this.config.height);
			if (this.model.anchor && this.model.anchor.set) {
				this.model.anchor.set(0.5, 0.5);
				this.model.scale.set(this.config.scale);
				this.model.x = width / 2;
				this.model.y = height / 2 + 10;
				return;
			}
			this.model.scale.set(this.config.scale);
			this.model.x = Math.max(0, (width - this.model.width) / 2);
			this.model.y = Math.max(0, (height - this.model.height) / 2);
		},
		resizeRenderer() {
			if (!this.app) return;
			const width = this.compact ? Math.round(this.config.width * 0.72) : this.config.width;
			const height = this.compact ? Math.round(this.config.height * 0.72) : this.config.height;
			this.app.renderer.resize(width, height);
			this.fitModel();
		},
		playIdle(MotionPriority) {
			if (!this.model || !this.model.motion) return;
			const priority = MotionPriority && MotionPriority.IDLE ? MotionPriority.IDLE : undefined;
			this.model.motion('Idle', 0, priority).catch(() => {});
		},
		clearTimers() {
			if (this.bubbleTimer) clearTimeout(this.bubbleTimer);
			if (this.idleTimer) clearTimeout(this.idleTimer);
			this.bubbleTimer = null;
			this.idleTimer = null;
		},
		destroyLive2D() {
			// #ifdef APP-PLUS
			this.ready = false;
			this.renderActionTick += 1;
			return;
			// #endif
			try {
				if (this.model && this.model.destroy) {
					this.model.destroy({ children: true, texture: true, baseTexture: true });
				}
			} catch (e) {}
			try {
				if (this.app && this.app.destroy) {
					this.app.destroy(true, { children: true, texture: true, baseTexture: true });
				}
			} catch (e) {}
			this.model = null;
			this.app = null;
			this.ready = false;
		},
		say(text, duration) {
			const next = shortText(text);
			if (!next || !this.config.showBubble) return;
			if (this.bubbleTimer) clearTimeout(this.bubbleTimer);
			this.bubbleText = next;
			this.bubbleVisible = true;
			this.bubbleTimer = setTimeout(() => {
				this.bubbleVisible = false;
				this.bubbleTimer = null;
			}, duration || 4200);
		},
		setExpression(name) {
			// #ifdef APP-PLUS
			this.renderExpression = name || '';
			this.renderActionTick += 1;
			return;
			// #endif
			if (!this.model || !this.model.expression || !name) return;
			this.model.expression(name).catch(() => {});
		},
		pickReplyExpression(text) {
			const value = String(text || '');
			if (/哈哈|开心|喜欢|谢谢|真棒|太好|可以|好呀|nice|great/i.test(value)) return '3clever';
			if (/哭|难过|失败|抱歉|对不起|QAQ|呜|痛|sad/i.test(value)) return '5QAQ';
			if (/[?？]|什么|怎么|为什么|疑惑|诶|欸/.test(value)) return '4OAO';
			return '1desk';
		},
		handleThinking() {
			if (!this.config.chatLink) return;
			this.say('正在思考...', 2600);
			this.setExpression('2mic');
		},
		handleReplying(text) {
			if (!this.config.chatLink) return;
			if (!this.bubbleVisible) this.say(text || '正在回复...', 2200);
			this.setExpression('7keyboard');
		},
		handleReply(text) {
			if (!this.config.chatLink) return;
			this.say(text || '回复完成啦。', 5200);
			this.setExpression(this.pickReplyExpression(text));
		},
		handleError(text) {
			if (!this.config.chatLink) return;
			this.say(text || '好像出错了。', 3600);
			this.setExpression('5QAQ');
		},
		handleConfigChanged(config) {
			const wasEnabled = this.shouldRender;
			this.config = Object.assign({}, config);
			this.menuVisible = false;
			this.resetDefaultPosition(false);
			this.resizeRenderer();
			this.syncEnabledState(wasEnabled);
		},
		syncEnabledState(wasEnabled) {
			if (!wasEnabled && this.shouldRender) {
				this.failed = false;
				this.$nextTick(() => this.initLive2D());
			}
			if (wasEnabled && !this.shouldRender) {
				this.destroyLive2D();
			}
		},
		onTouchStart(event) {
			const touch = firstTouch(event);
			this.dragging = true;
			this.moved = false;
			this.touchStartX = touch.clientX || touch.pageX || 0;
			this.touchStartY = touch.clientY || touch.pageY || 0;
			this.startX = this.x;
			this.startY = this.y;
		},
		onTouchMove(event) {
			if (!this.dragging) return;
			const touch = firstTouch(event);
			const tx = touch.clientX || touch.pageX || 0;
			const ty = touch.clientY || touch.pageY || 0;
			const dx = tx - this.touchStartX;
			const dy = ty - this.touchStartY;
			if (Math.abs(dx) > 4 || Math.abs(dy) > 4) this.moved = true;
			const info = uni.getSystemInfoSync();
			const width = this.compact ? Math.round(this.config.width * 0.72) : this.config.width;
			const height = this.compact ? Math.round(this.config.height * 0.72) : this.config.height;
			this.x = Math.max(6, Math.min(info.windowWidth - width - 6, this.startX + dx));
			this.y = Math.max(6, Math.min(info.windowHeight - height - 6, this.startY + dy));
		},
		onTouchEnd() {
			if (this.dragging && this.moved) this.persistPosition();
			this.dragging = false;
		},
		onTapCompanion() {
			if (this.moved) return;
			if (!this.config.clickAction) return;
			this.menuVisible = !this.menuVisible;
			const expressions = ['1desk', '3clever', '4OAO', '5QAQ', '8punch', '9'];
			this.setExpression(expressions[Math.floor(Math.random() * expressions.length)]);
		},
		toggleMenu() {
			this.menuVisible = !this.menuVisible;
		},
		openSetting() {
			this.menuVisible = false;
			uni.navigateTo({ url: '/pages/user/live2dSetting' });
		},
		hideCompanion() {
			this.config = companionStore.saveConfig({ enabled: false });
			this.destroyLive2D();
		},
		retryLive2D() {
			if (!this.failed) return;
			this.failed = false;
			this.renderRetryTick += 1;
			this.$nextTick(() => this.initLive2D());
		},
		handleRenderReady() {
			this.failed = false;
			this.ready = true;
		},
		handleRenderFailed() {
			this.ready = false;
			this.failed = true;
		},
		handleRenderTap() {
			this.onTapCompanion();
		},
		handleRenderLongPress() {
			this.toggleMenu();
		}
	}
};
</script>

<!-- #ifdef APP-PLUS -->
<script module="live2dRender" lang="renderjs">
let runtimePromise = null;
let app = null;
let model = null;
let rootEl = null;
let latestState = null;
let owner = null;
let touchState = null;

function resolveAssetUrl(src) {
	const value = String(src || '');
	if (!value) return value;
	if (/^(https?:|file:|data:|blob:)/i.test(value)) return value;
	if (typeof window === 'undefined' || !window.location) return value;
	const clean = value.replace(/^\/+/, '');
	const plusApi = window.plus || (typeof plus !== 'undefined' ? plus : null);
	if (plusApi && plusApi.io && typeof plusApi.io.convertLocalFileSystemURL === 'function') {
		try {
			return plusApi.io.convertLocalFileSystemURL('_www/' + clean);
		} catch (e) {}
	}
	const protocol = String(window.location.protocol || '').toLowerCase();
	if (protocol === 'file:' || (protocol && protocol !== 'http:' && protocol !== 'https:')) {
		try {
			return new URL(clean, window.location.href).toString();
		} catch (e) {
			return clean;
		}
	}
	return value.charAt(0) === '/' ? value : '/' + clean;
}

function loadScript(src, ready) {
	if (ready && ready()) return Promise.resolve();
	const scriptUrl = resolveAssetUrl(src);
	const existing = document.querySelector('script[data-live2d-render-src="' + scriptUrl + '"]');
	if (existing) {
		if (existing.getAttribute('data-loaded') === '1') return Promise.resolve();
		return new Promise((resolve, reject) => {
			existing.addEventListener('load', resolve, { once: true });
			existing.addEventListener('error', reject, { once: true });
		});
	}
	return new Promise((resolve, reject) => {
		const script = document.createElement('script');
		script.src = scriptUrl;
		script.async = true;
		script.setAttribute('data-live2d-render-src', scriptUrl);
		script.onload = () => {
			script.setAttribute('data-loaded', '1');
			resolve();
		};
		script.onerror = () => reject(new Error('Failed to load ' + scriptUrl));
		document.head.appendChild(script);
	});
}

function ensureRuntime() {
	if (runtimePromise) return runtimePromise;
	runtimePromise = loadScript('/static/live2d/vendor/pixi.min.js', () => window.PIXI)
		.then(() => loadScript('/static/live2d/vendor/live2dcubismcore.min.js', () => window.Live2DCubismCore))
		.then(() =>
			loadScript('/static/live2d/vendor/pixi-live2d-cubism4.min.js', () => {
				return window.PIXI && window.PIXI.live2d && window.PIXI.live2d.Live2DModel;
			})
		)
		.then(() => ({
			PIXI: window.PIXI,
			Live2DModel: window.PIXI.live2d.Live2DModel,
			MotionPriority: window.PIXI.live2d.MotionPriority || {}
		}));
	return runtimePromise;
}

function destroyLive2D() {
	try {
		if (model && model.destroy) model.destroy({ children: true, texture: true, baseTexture: true });
	} catch (e) {}
	try {
		if (app && app.destroy) app.destroy(true, { children: true, texture: true, baseTexture: true });
	} catch (e) {}
	model = null;
	app = null;
	if (rootEl) rootEl.innerHTML = '';
}

function notifyReady() {
	if (!owner || !owner.callMethod) return;
	try {
		owner.callMethod('handleRenderReady');
	} catch (e) {}
}

function notifyFailed() {
	if (!owner || !owner.callMethod) return;
	try {
		owner.callMethod('handleRenderFailed');
	} catch (e) {}
}

function notifyTap() {
	if (!owner || !owner.callMethod) return;
	try {
		owner.callMethod('handleRenderTap');
	} catch (e) {}
}

function notifyLongPress() {
	if (!owner || !owner.callMethod) return;
	try {
		owner.callMethod('handleRenderLongPress');
	} catch (e) {}
}

function firstRenderTouch(event) {
	const touches = event && (event.touches || event.changedTouches);
	return touches && touches.length ? touches[0] : event;
}

function bindRenderTouch() {
	if (!rootEl || rootEl.__live2dTouchBound) return;
	rootEl.__live2dTouchBound = true;
	rootEl.addEventListener(
		'touchstart',
		(event) => {
			const touch = firstRenderTouch(event);
			touchState = {
				x: touch ? touch.clientX || touch.pageX || 0 : 0,
				y: touch ? touch.clientY || touch.pageY || 0 : 0,
				startedAt: Date.now(),
				moved: false,
				longPressed: false,
				timer: setTimeout(() => {
					if (!touchState || touchState.moved || touchState.longPressed) return;
					touchState.longPressed = true;
					notifyLongPress();
				}, 520)
			};
		},
		{ passive: true }
	);
	rootEl.addEventListener(
		'touchmove',
		(event) => {
			if (!touchState) return;
			const touch = firstRenderTouch(event);
			const x = touch ? touch.clientX || touch.pageX || 0 : 0;
			const y = touch ? touch.clientY || touch.pageY || 0 : 0;
			if (Math.abs(x - touchState.x) > 8 || Math.abs(y - touchState.y) > 8) {
				touchState.moved = true;
				if (touchState.timer) clearTimeout(touchState.timer);
			}
		},
		{ passive: true }
	);
	rootEl.addEventListener(
		'touchend',
		() => {
			if (!touchState) return;
			if (touchState.timer) clearTimeout(touchState.timer);
			const elapsed = Date.now() - touchState.startedAt;
			if (!touchState.moved && !touchState.longPressed && elapsed < 520) {
				notifyTap();
			}
			touchState = null;
		},
		{ passive: true }
	);
	rootEl.addEventListener(
		'touchcancel',
		() => {
			if (touchState && touchState.timer) clearTimeout(touchState.timer);
			touchState = null;
		},
		{ passive: true }
	);
}

function fitModel(state) {
	if (!model || !app || !state) return;
	const width = (app.screen && app.screen.width) || state.width || 230;
	const height = (app.screen && app.screen.height) || state.height || 260;
	if (model.anchor && model.anchor.set) {
		model.anchor.set(0.5, 0.5);
		model.scale.set(state.scale || 0.22);
		model.x = width / 2;
		model.y = height / 2 + 10;
		return;
	}
	model.scale.set(state.scale || 0.22);
	model.x = Math.max(0, (width - model.width) / 2);
	model.y = Math.max(0, (height - model.height) / 2);
}

function initLive2D() {
	if (!rootEl || app || !latestState || !latestState.enabled) return;
	ensureRuntime()
		.then(({ PIXI, Live2DModel, MotionPriority }) => {
			if (!rootEl || app || !latestState || !latestState.enabled) return null;
			app = new PIXI.Application({
				width: latestState.width || 230,
				height: latestState.height || 260,
				transparent: true,
				backgroundAlpha: 0,
				antialias: true,
				autoDensity: true,
				resolution: Math.min(window.devicePixelRatio || 1, 2)
			});
			rootEl.innerHTML = '';
			rootEl.appendChild(app.view);
			app.view.style.display = 'block';
			app.view.style.width = '100%';
			app.view.style.height = '100%';
			return Live2DModel.from(resolveAssetUrl(latestState.modelPath), {
				autoInteract: false,
				autoUpdate: true
			}).then((nextModel) => {
				model = nextModel;
				app.stage.addChild(model);
				fitModel(latestState);
				if (model.motion) {
					const priority = MotionPriority && MotionPriority.IDLE ? MotionPriority.IDLE : undefined;
					model.motion('Idle', 0, priority).catch(() => {});
				}
				notifyReady();
				return model;
			});
		})
		.catch((e) => {
			console.error('[Live2DCompanion renderjs] load failed', e);
			runtimePromise = null;
			destroyLive2D();
			notifyFailed();
		});
}

function applyExpression(name) {
	if (!model || !model.expression || !name) return;
	model.expression(name).catch(() => {});
}

export default {
	mounted() {
		rootEl = this.$el;
		owner = this.$ownerInstance || owner;
		bindRenderTouch();
		initLive2D();
	},
	beforeDestroy() {
		destroyLive2D();
		rootEl = null;
		latestState = null;
		owner = null;
	},
	methods: {
		update(state, oldState, ownerInstance) {
			owner = ownerInstance || this.$ownerInstance || owner;
			const prev = latestState || {};
			latestState = Object.assign({}, state || {});
			if (!latestState.enabled) {
				destroyLive2D();
				return;
			}
			if (!app) {
				initLive2D();
				return;
			}
			if (prev.modelPath && latestState.modelPath && prev.modelPath !== latestState.modelPath) {
				destroyLive2D();
				initLive2D();
				return;
			}
			if (app.renderer && (prev.width !== latestState.width || prev.height !== latestState.height)) {
				app.renderer.resize(latestState.width || 230, latestState.height || 260);
				fitModel(latestState);
			}
			if (prev.scale !== latestState.scale) fitModel(latestState);
			if (
				latestState.expression &&
				(prev.expression !== latestState.expression || prev.actionTick !== latestState.actionTick)
			) {
				applyExpression(latestState.expression);
			}
		}
	}
};
</script>
<!-- #endif -->

<style scoped lang="scss">
.live2d-companion {
	position: fixed !important;
	z-index: 2147483000 !important;
	transition: opacity 0.18s ease;
	touch-action: none;
	display: block;
	visibility: visible !important;
	pointer-events: auto !important;
}

.live2d-companion--dragging {
	transition: none;
}

.live2d-companion__stage {
	width: 100%;
	height: 100%;
	overflow: hidden;
	pointer-events: auto;
}

.live2d-companion__app-stage {
	width: 100%;
	height: 100%;
	overflow: hidden;
	pointer-events: auto;
}

/* #ifdef APP-PLUS */
.live2d-companion__stage {
	display: none;
}
/* #endif */

/* #ifndef APP-PLUS */
.live2d-companion__app-stage {
	display: none;
}
/* #endif */

.live2d-companion__stage ::v-deep canvas {
	display: block;
	width: 100% !important;
	height: 100% !important;
}

.live2d-companion__app-stage ::v-deep canvas {
	display: block;
	width: 100% !important;
	height: 100% !important;
}

.live2d-companion__placeholder {
	position: absolute;
	left: 50%;
	bottom: 18px;
	z-index: 1;
	transform: translateX(-50%);
	width: 72px;
	padding: 8px;
	border-radius: 18px;
	background: rgba(255, 255, 255, 0.82);
	border: 1px solid rgba(104, 126, 145, 0.16);
	box-shadow: 0 10px 22px rgba(42, 70, 90, 0.14);
	text-align: center;
	box-sizing: border-box;
}

.live2d-companion__placeholder-icon {
	display: block;
	width: 44px;
	height: 44px;
	margin: 0 auto;
	border-radius: 14px;
}

.live2d-companion__placeholder-text {
	display: block;
	margin-top: 5px;
	font-size: 11px;
	line-height: 1.2;
	color: #2f6f7f;
}

.live2d-companion__bubble {
	position: absolute;
	right: 16px;
	bottom: 78%;
	max-width: 210px;
	padding: 9px 11px;
	border-radius: 14px 14px 4px 14px;
	background: rgba(255, 255, 255, 0.88);
	border: 1px solid rgba(104, 126, 145, 0.18);
	box-shadow: 0 10px 24px rgba(42, 70, 90, 0.16);
	backdrop-filter: blur(12px);
	-webkit-backdrop-filter: blur(12px);
}

.live2d-companion--left .live2d-companion__bubble {
	left: 16px;
	right: auto;
	border-radius: 14px 14px 14px 4px;
}

.live2d-companion__bubble-text {
	display: block;
	font-size: 12px;
	line-height: 1.45;
	color: #244b66;
	word-break: break-word;
}

.live2d-companion__menu {
	position: absolute;
	right: 12px;
	bottom: 16px;
	display: flex;
	gap: 8px;
	padding: 6px;
	border-radius: 999px;
	background: rgba(255, 255, 255, 0.86);
	box-shadow: 0 10px 22px rgba(42, 70, 90, 0.16);
	border: 1px solid rgba(104, 126, 145, 0.16);
}

.live2d-companion--left .live2d-companion__menu {
	left: 12px;
	right: auto;
}

.live2d-companion__menu-btn {
	min-width: 42px;
	height: 28px;
	padding: 0 10px;
	border-radius: 999px;
	background: rgba(79, 147, 163, 0.12);
	color: #2f6f7f;
	font-size: 12px;
	line-height: 28px;
	text-align: center;
}
</style>
