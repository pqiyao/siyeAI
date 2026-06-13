var app = getApp();
/**
 * Self-hosted JG Tavern backend config.
 * Local development：SillySpringboot 默认 http://127.0.0.1:8080（与旧 JG 8081 区分）。
 * Production H5 defaults to same-origin reverse proxy mode.
 */
var LOCAL_API_ORIGIN = 'http://127.0.0.1:8080';
var APP_API_ORIGIN = '';
var REMOTE_API_ORIGIN = '';
var REMOTE_IMG_ORIGIN = '';
var REMOTE_UPLOAD_ORIGIN = '';
var REMOTE_SOCKET_URL = '';

function trimText(value) {
	return value == null ? '' : String(value).trim();
}

function trimTrailingSlash(value) {
	return trimText(value).replace(/\/+$/, '');
}

function browserHost() {
	if (typeof window === 'undefined' || !window.location) {
		return '';
	}
	return trimText(window.location.hostname).toLowerCase();
}

function browserOrigin() {
	if (typeof window === 'undefined' || !window.location) {
		return '';
	}
	return trimTrailingSlash(window.location.origin || '');
}

function isLocalBrowserHost(host) {
	return !host || host === 'localhost' || host === '127.0.0.1' || host === '0.0.0.0';
}

function isAppRuntime() {
	if (typeof plus !== 'undefined') {
		return true;
	}
	if (typeof window !== 'undefined' && window.plus) {
		return true;
	}
	if (typeof navigator !== 'undefined') {
		return /Html5Plus|uni-app|HBuilderX/i.test(String(navigator.userAgent || ''));
	}
	return false;
}

function resolveApiOrigin() {
	if (isAppRuntime() && trimText(APP_API_ORIGIN)) {
		return trimTrailingSlash(APP_API_ORIGIN);
	}
	var host = browserHost();
	if (isLocalBrowserHost(host)) {
		return LOCAL_API_ORIGIN;
	}
	if (trimText(REMOTE_API_ORIGIN)) {
		return trimTrailingSlash(REMOTE_API_ORIGIN);
	}
	var origin = browserOrigin();
	if (origin) {
		return origin;
	}
	return LOCAL_API_ORIGIN;
}

function resolveImgBase(apiOrigin) {
	if (trimText(REMOTE_IMG_ORIGIN)) {
		return trimTrailingSlash(REMOTE_IMG_ORIGIN) + '/';
	}
	return trimTrailingSlash(apiOrigin) + '/';
}

function resolveUploadBase(apiOrigin) {
	if (trimText(REMOTE_UPLOAD_ORIGIN)) {
		return trimTrailingSlash(REMOTE_UPLOAD_ORIGIN) + '/';
	}
	return trimTrailingSlash(apiOrigin) + '/uploads/';
}

function resolveSocketUrl(apiOrigin) {
	if (trimText(REMOTE_SOCKET_URL)) {
		return trimText(REMOTE_SOCKET_URL);
	}
	if (typeof window !== 'undefined' && window.location && !isLocalBrowserHost(browserHost())) {
		var protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
		return protocol + window.location.host + '/ws';
	}
	if (apiOrigin.indexOf('https://') === 0) {
		return 'wss://' + apiOrigin.substring('https://'.length) + '/ws';
	}
	if (apiOrigin.indexOf('http://') === 0) {
		return 'ws://' + apiOrigin.substring('http://'.length) + '/ws';
	}
	return '';
}

var resolvedApiOrigin = resolveApiOrigin();
var jgApiBase = resolvedApiOrigin;
var jgChatEnabled = true;
var jgChatStream = true;
var path = trimTrailingSlash(resolvedApiOrigin) + '/api/';
var img_url = resolveImgBase(resolvedApiOrigin);
var uploadpath = resolveUploadBase(resolvedApiOrigin);
var socket = resolveSocketUrl(resolvedApiOrigin);
var versionName = '1.0.0';
var version = 100;
var appType = 1; // 1=android 2=ios

/** 会话页顶部促销条：站内路径（与 pages.json 一致，勿带 .vue） */
var inboxPromoInternalPath = '/pages/chat/systemmsg';
/** 若填写 http(s) 链接，H5 将在新标签页打开（优先于 internal） */
var inboxPromoExternalUrl = '';
function paths() {
	return path;
}
module.exports = {
	path: path,
	jgApiBase: jgApiBase,
	jgChatEnabled: jgChatEnabled,
	jgChatStream: jgChatStream,
	inboxPromoInternalPath: inboxPromoInternalPath,
	inboxPromoExternalUrl: inboxPromoExternalUrl,
	img_url: img_url,
	version: version,
	versionName: versionName,
	appType: appType,
	paths: paths,
	uploadpath: uploadpath,
	socket: socket
}
