/**
 * 自研 JG 酒馆后端：角色列表 / 详情 / 聊天记录 / 持久化对话（无登录态，用 clientUid 区分设备）。
 */
var api = require('./api.js');
var CHARACTER_ACCESS_REFRESH_FLAG_KEY = 'tavern_character_access_refresh_needed';
var VIEWER_MEMBERSHIP_SNAPSHOT_KEY = 'tavern_viewer_membership_snapshot';
var RUNTIME_FEATURE_CONFIG_KEY = 'tavern_runtime_feature_config';
var SOCIAL_FEATURE_CONFIG_KEY = 'tavern_social_feature_config';
var VISITOR_DEVICE_TOKEN_KEY = 'tavern_device_token';
var RUNTIME_FEATURE_CONFIG_CACHE_MS = 15000;
var SOCIAL_FEATURE_CONFIG_CACHE_MS = 15000;
var RESOLVED_ASSET_URL_CACHE_LIMIT = 800;
var H5_UPLOAD_MAX_FILE_BYTES = 28 * 1024 * 1024;
var H5_AUDIO_UPLOAD_MAX_FILE_BYTES = 15 * 1024 * 1024;
var H5_BROWSER_UPLOAD_TIMEOUT = 120000;
var LOCAL_CHAT_IMAGE_CACHE_PREFIX = 'tavern_local_chat_images_';
var LOCAL_USER_VOICE_CACHE_PREFIX = 'tavern_local_user_voice_';
var LOCAL_EXPRESSION_LIBRARY_PREFIX = 'tavern_local_expressions_';
var LOCAL_CHARACTER_VOICE_CONFIG_PREFIX = 'tavern_character_voice_cfg_';
var LOCAL_CHARACTER_IMAGE_CONFIG_PREFIX = 'tavern_character_image_cfg_';
var viewerMembershipSnapshotPromise = null;
var runtimeFeatureConfigPromise = null;
var runtimeFeatureConfigFetchedAt = 0;
var socialFeatureConfigPromise = null;
var socialFeatureConfigFetchedAt = 0;
var resolvedAssetUrlCache = Object.create(null);
var resolvedAssetUrlCacheKeys = [];

function baseUrl() {
	return String(api.jgApiBase || '').replace(/\/$/, '');
}

function wsBaseUrl() {
	var b = baseUrl();
	if (!b) {
		return '';
	}
	if (b.indexOf('https://') === 0) {
		return 'wss://' + b.slice(8);
	}
	if (b.indexOf('http://') === 0) {
		return 'ws://' + b.slice(7);
	}
	return b;
}

function jgEnabled() {
	return api.jgChatEnabled !== false && baseUrl() !== '';
}

function getCachedResolvedAssetUrl(cacheKey) {
	if (!cacheKey) {
		return null;
	}
	if (Object.prototype.hasOwnProperty.call(resolvedAssetUrlCache, cacheKey)) {
		return resolvedAssetUrlCache[cacheKey];
	}
	return null;
}

function setCachedResolvedAssetUrl(cacheKey, value) {
	if (!cacheKey) {
		return value;
	}
	if (!Object.prototype.hasOwnProperty.call(resolvedAssetUrlCache, cacheKey)) {
		resolvedAssetUrlCacheKeys.push(cacheKey);
		if (resolvedAssetUrlCacheKeys.length > RESOLVED_ASSET_URL_CACHE_LIMIT) {
			var oldestKey = resolvedAssetUrlCacheKeys.shift();
			if (oldestKey) {
				delete resolvedAssetUrlCache[oldestKey];
			}
		}
	}
	resolvedAssetUrlCache[cacheKey] = value;
	return value;
}

/** 角色头像/封面：站内相对路径 /uploads/... 补全为 jgApiBase */
function resolveJgAssetUrl(u) {
	if (u == null || String(u).trim() === '') {
		return '';
	}
	var s = String(u).trim();
	var currentBaseUrl = baseUrl();
	var cacheKey = currentBaseUrl + '|' + s;
	var cachedUrl = getCachedResolvedAssetUrl(cacheKey);
	if (cachedUrl) {
		return cachedUrl;
	}
	if (s.indexOf('http://') === 0 || s.indexOf('https://') === 0 || s.indexOf('data:') === 0 || s.indexOf('//') === 0) {
		return setCachedResolvedAssetUrl(cacheKey, s);
	}
	if (s.indexOf('/') === 0) {
		return setCachedResolvedAssetUrl(cacheKey, currentBaseUrl + s);
	}
	return setCachedResolvedAssetUrl(cacheKey, s);
}

function getStoredUserId(user) {
	if (!user || typeof user !== 'object') {
		return '';
	}
	var raw = user.user_id != null && user.user_id !== ''
		? user.user_id
		: (user.id != null && user.id !== ''
			? user.id
			: (user.userId != null && user.userId !== ''
				? user.userId
				: user.appUserId));
	if (raw == null || raw === '') {
		return '';
	}
	return String(raw);
}

function normalizeLocalScopeText(value) {
	return value == null ? '' : String(value).trim();
}

function localConversationCacheKey(prefix, clientUid, conversationId) {
	var safeClientUid = normalizeLocalScopeText(clientUid || getClientUid());
	var safeConversationId = normalizeLocalScopeText(conversationId);
	return safeClientUid && safeConversationId ? prefix + safeClientUid + '_' + safeConversationId : '';
}

function localLegacyConversationImageKey(conversationId) {
	var safeConversationId = normalizeLocalScopeText(conversationId);
	return safeConversationId ? LOCAL_CHAT_IMAGE_CACHE_PREFIX + safeConversationId : '';
}

function localCharacterCacheKey(prefix, clientUid, characterId) {
	var safeClientUid = normalizeLocalScopeText(clientUid || getClientUid());
	var safeCharacterId = normalizeLocalScopeText(characterId);
	return safeClientUid && safeCharacterId ? prefix + safeClientUid + '_' + safeCharacterId : '';
}

function getStoredValueQuietly(key) {
	if (!key || typeof uni === 'undefined' || typeof uni.getStorageSync !== 'function') {
		return null;
	}
	try {
		return uni.getStorageSync(key);
	} catch (e) {
		return null;
	}
}

function removeStoredValueQuietly(key) {
	if (!key || typeof uni === 'undefined' || typeof uni.removeStorageSync !== 'function') {
		return;
	}
	try {
		uni.removeStorageSync(key);
	} catch (e) {}
}

function isManagedLocalVoiceAudioUrl(url) {
	var safeUrl = normalizeLocalScopeText(url);
	if (!safeUrl) {
		return false;
	}
	if (safeUrl.indexOf('data:') === 0 || safeUrl.indexOf('blob:') === 0) {
		return false;
	}
	if (/^https?:\/\//i.test(safeUrl)) {
		return false;
	}
	if (safeUrl.indexOf('/uploads/h5/') === 0) {
		return false;
	}
	return true;
}

function cleanupStoredVoiceFiles(raw) {
	if (typeof uni === 'undefined' || typeof uni.removeSavedFile !== 'function') {
		return;
	}
	var entries =
		raw && typeof raw === 'object' && Array.isArray(raw.entries)
			? raw.entries
			: Array.isArray(raw)
				? raw
				: [];
	entries.forEach(function (item) {
		var audioUrl = normalizeLocalScopeText(item && item.audioUrl);
		if (!isManagedLocalVoiceAudioUrl(audioUrl)) {
			return;
		}
		try {
			uni.removeSavedFile({ filePath: audioUrl });
		} catch (e) {}
	});
}

function isManagedLocalChatImageUrl(url) {
	var safeUrl = normalizeLocalScopeText(url);
	if (!safeUrl) {
		return false;
	}
	if (safeUrl.indexOf('data:') === 0 || safeUrl.indexOf('blob:') === 0) {
		return false;
	}
	if (/^https?:\/\//i.test(safeUrl)) {
		return false;
	}
	if (safeUrl.indexOf('/uploads/') === 0) {
		return false;
	}
	return true;
}

function removeManagedLocalFile(filePath) {
	var safePath = normalizeLocalScopeText(filePath);
	if (!safePath) {
		return;
	}
	if (typeof uni !== 'undefined' && typeof uni.removeSavedFile === 'function') {
		try {
			uni.removeSavedFile({ filePath: safePath });
		} catch (e) {}
	}
	if (typeof plus !== 'undefined' && plus.io && typeof plus.io.resolveLocalFileSystemURL === 'function') {
		try {
			plus.io.resolveLocalFileSystemURL(
				safePath,
				function (entry) {
					try {
						entry.remove(function () {}, function () {});
					} catch (e) {}
				},
				function () {}
			);
		} catch (e) {}
	}
}

function cleanupStoredImageFiles(raw) {
	var entries =
		raw && typeof raw === 'object' && Array.isArray(raw.entries)
			? raw.entries
			: Array.isArray(raw)
				? raw
				: [];
	entries.forEach(function (item) {
		var imageUrls = Array.isArray(item && item.imageUrls) ? item.imageUrls : [];
		imageUrls.forEach(function (imageUrl) {
			var safeUrl = normalizeLocalScopeText(imageUrl);
			if (!isManagedLocalChatImageUrl(safeUrl)) {
				return;
			}
			removeManagedLocalFile(safeUrl);
		});
	});
}

function cleanupLocalConversationArtifacts(context) {
	var source = context && typeof context === 'object' ? context : {};
	var clientUid = normalizeLocalScopeText(source.clientUid || getClientUid());
	var conversationId = normalizeLocalScopeText(source.conversationId);
	if (!clientUid || !conversationId) {
		return false;
	}
	var imageKey = localConversationCacheKey(LOCAL_CHAT_IMAGE_CACHE_PREFIX, clientUid, conversationId);
	var legacyImageKey = localLegacyConversationImageKey(conversationId);
	var voiceKey = localConversationCacheKey(LOCAL_USER_VOICE_CACHE_PREFIX, clientUid, conversationId);
	cleanupStoredImageFiles(getStoredValueQuietly(imageKey));
	cleanupStoredImageFiles(getStoredValueQuietly(legacyImageKey));
	cleanupStoredVoiceFiles(getStoredValueQuietly(voiceKey));
	removeStoredValueQuietly(imageKey);
	removeStoredValueQuietly(legacyImageKey);
	removeStoredValueQuietly(voiceKey);
	return true;
}

function cleanupLocalCharacterArtifacts(context) {
	var source = context && typeof context === 'object' ? context : {};
	var clientUid = normalizeLocalScopeText(source.clientUid || getClientUid());
	var characterId = normalizeLocalScopeText(source.characterId);
	if (!clientUid) {
		return false;
	}
	cleanupLocalConversationArtifacts(source);
	removeStoredValueQuietly(localCharacterCacheKey(LOCAL_CHARACTER_VOICE_CONFIG_PREFIX, clientUid, characterId));
	removeStoredValueQuietly(localCharacterCacheKey(LOCAL_CHARACTER_IMAGE_CONFIG_PREFIX, clientUid, characterId));
	removeStoredValueQuietly(localCharacterCacheKey(LOCAL_EXPRESSION_LIBRARY_PREFIX, clientUid, characterId));
	return true;
}

function normalizeDeviceToken(raw) {
	if (raw == null) {
		return '';
	}
	var token = String(raw).trim();
	if (!token || token.length > 80) {
		return '';
	}
	if (!/^[A-Za-z0-9_-]+$/.test(token)) {
		return '';
	}
	return token;
}

function getDeviceToken() {
	try {
		return normalizeDeviceToken(uni.getStorageSync(VISITOR_DEVICE_TOKEN_KEY));
	} catch (e) {
		return '';
	}
}

function saveDeviceToken(rawToken) {
	var token = normalizeDeviceToken(rawToken);
	if (!token) {
		return '';
	}
	try {
		uni.setStorageSync(VISITOR_DEVICE_TOKEN_KEY, token);
	} catch (e) {}
	return token;
}

function readHeaderCaseInsensitive(headers, name) {
	if (!headers || !name) {
		return '';
	}
	var expected = String(name).toLowerCase();
	if (typeof headers.get === 'function') {
		var fromGetter = headers.get(name) || headers.get(expected);
		return fromGetter == null ? '' : String(fromGetter).trim();
	}
	var keys = Object.keys(headers);
	for (var i = 0; i < keys.length; i++) {
		var key = keys[i];
		if (String(key).toLowerCase() === expected) {
			var value = headers[key];
			return value == null ? '' : String(value).trim();
		}
	}
	return '';
}

function captureResponseDeviceToken(res) {
	if (!res) {
		return '';
	}
	var headers = res.header || res.headers || null;
	return saveDeviceToken(readHeaderCaseInsensitive(headers, 'X-Device-Token'));
}

function buildRequestHeaders(extraHeaders) {
	var headers = Object.assign({}, extraHeaders || {});
	var clientUid = getClientUid();
	if (clientUid) {
		headers['X-Client-Uid'] = clientUid;
	}
	var deviceToken = getDeviceToken();
	if (deviceToken) {
		headers['X-Device-Token'] = deviceToken;
	}
	var authToken = getStoredAuthToken();
	if (authToken) {
		if (!headers.Authorization && !headers.authorization) {
			headers.Authorization = 'Bearer ' + authToken;
		}
		if (!headers.token && !headers.Token) {
			headers.token = authToken;
		}
	}
	return headers;
}

function getUploadMaxFileBytes() {
	return H5_UPLOAD_MAX_FILE_BYTES;
}

function canUseBrowserFilePicker() {
	return typeof window !== 'undefined'
		&& typeof document !== 'undefined'
		&& typeof FormData !== 'undefined'
		&& typeof XMLHttpRequest !== 'undefined';
}

function isBrowserFileObject(file) {
	if (!file || typeof file !== 'object') {
		return false;
	}
	if (typeof File !== 'undefined' && file instanceof File) {
		return true;
	}
	return typeof file.name === 'string'
		&& typeof file.size === 'number'
		&& typeof file.type === 'string'
		&& typeof file.slice === 'function';
}

function createUploadTooLargeError(limitBytes) {
	var limitMb = Math.max(1, Math.round(Number(limitBytes || H5_UPLOAD_MAX_FILE_BYTES) / 1024 / 1024));
	var err = new Error('\u6587\u4ef6\u8fc7\u5927\uff0c\u5f53\u524d\u5355\u6587\u4ef6\u4e0a\u9650\u4e3a ' + limitMb + 'MB\uff0c\u8bf7\u538b\u7f29\u540e\u518d\u8bd5');
	err.statusCode = 413;
	return err;
}

function ensureBrowserUploadFileSize(file, limitBytes) {
	if (!isBrowserFileObject(file)) {
		return;
	}
	var maxBytes = Number(limitBytes);
	if (!isFinite(maxBytes) || maxBytes <= 0) {
		maxBytes = H5_UPLOAD_MAX_FILE_BYTES;
	}
	if (Number(file.size || 0) > maxBytes) {
		throw createUploadTooLargeError(maxBytes);
	}
}

function extractUploadResponseMessage(data, fallbackMessage) {
	if (data && typeof data === 'object') {
		if (data.msg != null && String(data.msg).trim()) {
			return String(data.msg).trim();
		}
		if (data.message != null && String(data.message).trim()) {
			return String(data.message).trim();
		}
	}
	return fallbackMessage;
}

function createUploadHttpError(statusCode, responseData, fallbackMessage) {
	var message = fallbackMessage;
	if (Number(statusCode) === 413) {
		message = createUploadTooLargeError(H5_UPLOAD_MAX_FILE_BYTES).message;
	} else {
		message = extractUploadResponseMessage(responseData, fallbackMessage);
	}
	var err = new Error(message);
	err.statusCode = Number(statusCode) || 0;
	err.response = {
		status: Number(statusCode) || 0,
		data: responseData
	};
	return err;
}

function pickBrowserFile(accept) {
	return new Promise(function (resolve, reject) {
		if (!canUseBrowserFilePicker()) {
			reject(new Error('browser_picker_unavailable'));
			return;
		}
		var input = document.createElement('input');
		var settled = false;

		function cleanup() {
			try {
				window.removeEventListener('focus', handleFocus, true);
			} catch (e) {}
			if (input) {
				input.onchange = null;
				if (input.parentNode) {
					input.parentNode.removeChild(input);
				}
			}
		}

		function resolveOnce(file) {
			if (settled) {
				return;
			}
			settled = true;
			cleanup();
			resolve(file);
		}

		function rejectOnce(err) {
			if (settled) {
				return;
			}
			settled = true;
			cleanup();
			reject(err);
		}

		function readCurrentFile() {
			return input && input.files && input.files[0] ? input.files[0] : null;
		}

		function handleFocus() {
			window.setTimeout(function () {
				var file = readCurrentFile();
				if (file) {
					resolveOnce(file);
					return;
				}
				rejectOnce(new Error('cancelled'));
			}, 280);
		}

		input.type = 'file';
		if (accept) {
			input.accept = accept;
		}
		input.style.position = 'fixed';
		input.style.left = '-9999px';
		input.style.top = '-9999px';
		input.style.width = '1px';
		input.style.height = '1px';
		input.style.opacity = '0';
		input.setAttribute('aria-hidden', 'true');
		input.onchange = function () {
			var file = readCurrentFile();
			if (file) {
				resolveOnce(file);
			}
		};
		document.body.appendChild(input);
		window.addEventListener('focus', handleFocus, true);
		input.click();
	});
}

function pickBrowserImageFile() {
	return pickBrowserFile('image/*');
}

function pickBrowserPngFile() {
	return pickBrowserFile('.png,image/png');
}

function notifyUploadProgress(callback, rawPercent) {
	if (typeof callback !== 'function') {
		return;
	}
	var percent = Number(rawPercent);
	if (!isFinite(percent)) {
		return;
	}
	if (percent < 0) {
		percent = 0;
	} else if (percent > 100) {
		percent = 100;
	}
	callback(Math.round(percent));
}

function uploadBrowserMultipart(path, file, fields, timeoutMs, fallbackMessage, onProgress, limitBytes) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !isBrowserFileObject(file)) {
			reject(new Error('invalid'));
			return;
		}
		try {
			ensureBrowserUploadFileSize(file, limitBytes);
		} catch (e) {
			reject(e);
			return;
		}

		var xhr = new XMLHttpRequest();
		var formData = new FormData();
		var headers = buildRequestHeaders();
		var safeTimeout = Number(timeoutMs);
		safeTimeout = safeTimeout > 0 ? safeTimeout : H5_BROWSER_UPLOAD_TIMEOUT;

		xhr.open('POST', baseUrl() + path, true);
		xhr.timeout = safeTimeout;
		if (xhr.upload && typeof xhr.upload.addEventListener === 'function') {
			xhr.upload.addEventListener('progress', function (event) {
				if (!event || !event.lengthComputable) {
					return;
				}
				notifyUploadProgress(onProgress, event.loaded / event.total * 100);
			});
		}

		Object.keys(headers).forEach(function (key) {
			var value = headers[key];
			if (value != null && String(value).trim() !== '') {
				xhr.setRequestHeader(key, String(value));
			}
		});

		Object.keys(fields || {}).forEach(function (key) {
			var value = fields[key];
			if (value != null && value !== '') {
				formData.append(key, value);
			}
		});
		formData.append('file', file, file.name || 'upload.bin');

		xhr.onreadystatechange = function () {
			if (xhr.readyState !== 4) {
				return;
			}
			captureResponseDeviceToken({
				headers: {
					'X-Device-Token': xhr.getResponseHeader('X-Device-Token')
				}
			});
			if (xhr.status === 0) {
				return;
			}
			var responseText = xhr.responseText || '';
			var responseData = responseText;
			if (responseText) {
				try {
					responseData = JSON.parse(responseText);
				} catch (e) {}
			}
			if (xhr.status >= 200 && xhr.status < 300) {
				if (responseData && typeof responseData === 'object' && Number(responseData.code) === 1) {
					notifyUploadProgress(onProgress, 100);
					resolve(responseData.data);
					return;
				}
				reject(createUploadHttpError(xhr.status, responseData, fallbackMessage));
				return;
			}
			reject(createUploadHttpError(xhr.status, responseData, fallbackMessage));
		};

		xhr.onerror = function () {
			var err = new Error('\u7f51\u7edc\u8fde\u63a5\u5f02\u5e38\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u540e\u91cd\u8bd5');
			err.isNetworkError = true;
			reject(err);
		};

		xhr.ontimeout = function () {
			var err = new Error('\u4e0a\u4f20\u7b49\u5f85\u8d85\u65f6\uff0c\u8bf7\u4fdd\u6301\u7f51\u7edc\u7a33\u5b9a\u540e\u91cd\u8bd5');
			err.isNetworkError = true;
			reject(err);
		};

		xhr.send(formData);
	});
}

/**
 * 已登录：与 H5 用户表主键绑定，聊天/人设/记忆与游客隔离；未登录：匿名设备号。
 */
function getClientUid() {
	try {
		var user = uni.getStorageSync('user');
		var userId = getStoredUserId(user);
		if (userId) {
			return 'h5u_' + userId;
		}
	} catch (e) {}
	var u = uni.getStorageSync('tavern_client_uid');
	if (!u) {
		u = 'tc_' + Date.now() + '_' + Math.random().toString(36).slice(2, 14);
		uni.setStorageSync('tavern_client_uid', u);
	}
	return u;
}

function getStoredUser() {
	try {
		var user = uni.getStorageSync('user');
		return user && typeof user === 'object' ? user : null;
	} catch (e) {
		return null;
	}
}

function getStoredAuthToken() {
	var user = getStoredUser();
	if (!user || typeof user !== 'object' || user.token == null) {
		return '';
	}
	return String(user.token).trim();
}

function hasLoggedInUser() {
	var user = getStoredUser();
	return !!(getStoredUserId(user) && user && user.token);
}

function getViewerStateSignature() {
	var user = getStoredUser();
	var userId = getStoredUserId(user);
	if (userId) {
		return 'user:' + userId + '|token:' + String((user && user.token) || '');
	}
	return 'guest:' + getClientUid();
}

function getProfileAccessSignature(profile) {
	if (!profile || typeof profile !== 'object') {
		return 'vip:0|active:0|exp:';
	}
	var vipType = Number(profile.vipType);
	if (!isFinite(vipType) || vipType < 0) {
		vipType = 0;
	}
	var vipActive = profile.vipActive ? '1' : '0';
	var vipExpiresAt = profile.vipExpiresAt == null ? '' : String(profile.vipExpiresAt).trim();
	return 'vip:' + String(Math.floor(vipType)) + '|active:' + vipActive + '|exp:' + vipExpiresAt;
}

function normalizeRuntimeFeatureConfig(source) {
	var raw = source && typeof source === 'object' ? source : {};
	return {
		loginEnabled: raw.loginEnabled !== false,
		registerEnabled: raw.registerEnabled !== false,
		userCharacterCreationEnabled: raw.userCharacterCreationEnabled !== false,
		userByokEnabled: raw.userByokEnabled === true,
		voiceFeatureEnabled: raw.voiceFeatureEnabled !== false,
		userByokVipMinLevel: normalizeNonNegativeInt(raw.userByokVipMinLevel, 0)
	};
}

function saveRuntimeFeatureConfig(source) {
	var config = normalizeRuntimeFeatureConfig(source);
	try {
		uni.setStorageSync(RUNTIME_FEATURE_CONFIG_KEY, config);
	} catch (e) {}
	runtimeFeatureConfigFetchedAt = Date.now();
	return config;
}

function readStoredRuntimeFeatureConfig() {
	try {
		var raw = uni.getStorageSync(RUNTIME_FEATURE_CONFIG_KEY);
		if (raw && typeof raw === 'object') {
			return raw;
		}
	} catch (e) {}
	return null;
}

function getRuntimeFeatureConfig() {
	return normalizeRuntimeFeatureConfig(readStoredRuntimeFeatureConfig());
}

function fetchAppRuntimeConfig(forceRefresh) {
	if (!jgEnabled()) {
		return Promise.resolve(getRuntimeFeatureConfig());
	}
	var stored = readStoredRuntimeFeatureConfig();
	if (!forceRefresh) {
		if (stored) {
			return Promise.resolve(normalizeRuntimeFeatureConfig(stored));
		}
	}
	if (
		forceRefresh &&
		stored &&
		runtimeFeatureConfigFetchedAt > 0 &&
		Date.now() - runtimeFeatureConfigFetchedAt < RUNTIME_FEATURE_CONFIG_CACHE_MS
	) {
		return Promise.resolve(normalizeRuntimeFeatureConfig(stored));
	}
	if (runtimeFeatureConfigPromise) {
		return runtimeFeatureConfigPromise;
	}
	runtimeFeatureConfigPromise = requestJson('GET', '/api/v1/app/runtime-config', null, 12000)
		.then(function (data) {
			return saveRuntimeFeatureConfig(data);
		})
		.catch(function () {
			return getRuntimeFeatureConfig();
		})
		.finally(function () {
			runtimeFeatureConfigPromise = null;
		});
	return runtimeFeatureConfigPromise;
}

function normalizeSocialFeatureConfig(source) {
	var raw = source && typeof source === 'object' ? source : {};
	return {
		communityEnabled: raw.communityEnabled !== false,
		communityEntryVisible: raw.communityEntryVisible !== false,
		guestCommunityReadEnabled: raw.guestCommunityReadEnabled !== false,
		postCreateEnabled: raw.postCreateEnabled !== false,
		postImageEnabled: raw.postImageEnabled !== false,
		likeEnabled: raw.likeEnabled !== false,
		commentEnabled: raw.commentEnabled !== false,
		followEnabled: raw.followEnabled !== false,
		postMessageEntryVisible: raw.postMessageEntryVisible !== false,
		postPublishMode: normalizeSocialKey(raw.postPublishMode, 'direct'),
		chatEnabled: raw.chatEnabled !== false,
		chatEntryVisible: raw.chatEntryVisible !== false,
		newChatEnabled: raw.newChatEnabled !== false,
		existingChatEnabled: raw.existingChatEnabled !== false,
		textMessageEnabled: raw.textMessageEnabled !== false,
		imageMessageEnabled: raw.imageMessageEnabled !== false,
		messageRecallEnabled: raw.messageRecallEnabled !== false,
		onlineStatusVisible: raw.onlineStatusVisible !== false,
		privateChatPolicy: normalizeSocialKey(raw.privateChatPolicy, 'all'),
		friendEnabled: raw.friendEnabled !== false,
		friendRequestEnabled: raw.friendRequestEnabled !== false,
		friendEntryVisible: raw.friendEntryVisible !== false,
		nonFriendChatEnabled: raw.nonFriendChatEnabled !== false,
		friendRequestApprovalRequired: raw.friendRequestApprovalRequired !== false,
		blockEnabled: raw.blockEnabled === true
	};
}

function normalizeSocialKey(value, fallback) {
	var text = value == null ? '' : String(value).trim().toLowerCase().replace(/-/g, '_');
	return text || fallback;
}

function saveSocialFeatureConfig(source) {
	var config = normalizeSocialFeatureConfig(source);
	try {
		uni.setStorageSync(SOCIAL_FEATURE_CONFIG_KEY, config);
	} catch (e) {}
	socialFeatureConfigFetchedAt = Date.now();
	return config;
}

function readStoredSocialFeatureConfig() {
	try {
		var raw = uni.getStorageSync(SOCIAL_FEATURE_CONFIG_KEY);
		if (raw && typeof raw === 'object') {
			return raw;
		}
	} catch (e) {}
	return null;
}

function getSocialFeatureConfig() {
	return normalizeSocialFeatureConfig(readStoredSocialFeatureConfig());
}

function fetchSocialFeatureConfig(forceRefresh) {
	if (!jgEnabled()) {
		return Promise.resolve(getSocialFeatureConfig());
	}
	var stored = readStoredSocialFeatureConfig();
	if (!forceRefresh && stored) {
		return Promise.resolve(normalizeSocialFeatureConfig(stored));
	}
	if (
		forceRefresh &&
		stored &&
		socialFeatureConfigFetchedAt > 0 &&
		Date.now() - socialFeatureConfigFetchedAt < SOCIAL_FEATURE_CONFIG_CACHE_MS
	) {
		return Promise.resolve(normalizeSocialFeatureConfig(stored));
	}
	if (socialFeatureConfigPromise) {
		return socialFeatureConfigPromise;
	}
	socialFeatureConfigPromise = requestJson('GET', '/api/social/config', null, 12000)
		.then(function (data) {
			return saveSocialFeatureConfig(data);
		})
		.catch(function () {
			return getSocialFeatureConfig();
		})
		.finally(function () {
			socialFeatureConfigPromise = null;
		});
	return socialFeatureConfigPromise;
}

function canShowCommunityEntry() {
	var config = getSocialFeatureConfig();
	return config.communityEnabled !== false && config.communityEntryVisible !== false;
}

function canShowChatEntry() {
	var config = getSocialFeatureConfig();
	return config.chatEnabled !== false && config.chatEntryVisible !== false;
}

function isLoginEnabled() {
	return getRuntimeFeatureConfig().loginEnabled !== false;
}

function isRegisterEnabled() {
	return getRuntimeFeatureConfig().registerEnabled !== false;
}

function isUserCharacterCreationEnabled() {
	return getRuntimeFeatureConfig().userCharacterCreationEnabled !== false;
}

function isUserByokEnabled() {
	return getRuntimeFeatureConfig().userByokEnabled === true;
}

function isVoiceFeatureEnabled() {
	return getRuntimeFeatureConfig().voiceFeatureEnabled !== false;
}

function normalizeNonNegativeInt(value, fallback) {
	var number = Number(value);
	if (!isFinite(number)) {
		return fallback;
	}
	return Math.max(0, Math.floor(number));
}

function hasOwn(source, key) {
	return !!source && Object.prototype.hasOwnProperty.call(source, key);
}

function normalizeVipLevel(value) {
	var vipType = Number(value);
	if (!isFinite(vipType) || vipType <= 0) {
		return 0;
	}
	return vipType >= 2 ? 2 : 1;
}

function parseDateTimeMs(value) {
	if (value == null || value === '') {
		return 0;
	}
	if (value instanceof Date) {
		var dateValue = value.getTime();
		return isFinite(dateValue) ? dateValue : 0;
	}
	var text = String(value).trim();
	if (!text) {
		return 0;
	}
	var normalizedText = text.replace(/-/g, '/');
	var timestamp = new Date(normalizedText).getTime();
	return isFinite(timestamp) ? timestamp : 0;
}

function normalizeMembershipSnapshot(source) {
	if (!source || typeof source !== 'object') {
		return null;
	}
	var userId = getStoredUserId(source);
	if (!userId && source.userId != null && source.userId !== '') {
		userId = String(source.userId);
	}
	if (!userId && source.uid != null && source.uid !== '') {
		userId = String(source.uid);
	}
	var vipType = normalizeVipLevel(
		pickDefined(
			source.vipType,
			source.vip_type,
			source.vipLevel,
			source.vip_level,
			0
		)
	);
	var vipExpiresAt = pickDefined(source.vipExpiresAt, source.vip_expires_at, '');
	vipExpiresAt = vipExpiresAt == null ? '' : String(vipExpiresAt).trim();
	var vipExpiresAtMs = parseDateTimeMs(vipExpiresAt);
	var hasMembershipFields =
		hasOwn(source, 'vipType') ||
		hasOwn(source, 'vip_type') ||
		hasOwn(source, 'vipActive') ||
		hasOwn(source, 'vip_active') ||
		hasOwn(source, 'vipExpiresAt') ||
		hasOwn(source, 'vip_expires_at') ||
		hasOwn(source, 'vipLevel') ||
		hasOwn(source, 'vip_level');
	var vipActiveRaw = pickDefined(source.vipActive, source.vip_active, null);
	var vipActive;
	if (vipType <= 0) {
		vipActive = false;
	} else if (vipExpiresAtMs > 0) {
		vipActive = vipExpiresAtMs > Date.now();
	} else if (vipActiveRaw === true || vipActiveRaw === 1 || vipActiveRaw === '1') {
		vipActive = true;
	} else if (vipActiveRaw === false || vipActiveRaw === 0 || vipActiveRaw === '0') {
		vipActive = false;
	} else {
		vipActive = false;
	}
	return {
		userId: userId ? String(userId) : '',
		vipType: vipType,
		vipActive: !!vipActive,
		vipExpiresAt: vipExpiresAt,
		hasMembershipFields: !!hasMembershipFields
	};
}

function syncStoredUserMembership(snapshot) {
	var normalized = normalizeMembershipSnapshot(snapshot);
	if (!normalized || !normalized.userId) {
		return normalized;
	}
	var user = getStoredUser();
	var currentUserId = getStoredUserId(user);
	if (!user || !currentUserId || currentUserId !== normalized.userId) {
		return normalized;
	}
	var changed = false;
	if (String(user.user_id == null ? '' : user.user_id) !== normalized.userId) {
		user.user_id = normalized.userId;
		changed = true;
	}
	if (String(user.id == null ? '' : user.id) !== normalized.userId) {
		user.id = normalized.userId;
		changed = true;
	}
	if (String(user.appUserId == null ? '' : user.appUserId) !== normalized.userId) {
		user.appUserId = normalized.userId;
		changed = true;
	}
	if (normalizeVipLevel(user.vipType) !== normalized.vipType) {
		user.vipType = normalized.vipType;
		changed = true;
	}
	if (!!user.vipActive !== normalized.vipActive) {
		user.vipActive = normalized.vipActive;
		changed = true;
	}
	var currentExpiresAt = user.vipExpiresAt == null ? '' : String(user.vipExpiresAt).trim();
	if (currentExpiresAt !== normalized.vipExpiresAt) {
		user.vipExpiresAt = normalized.vipExpiresAt;
		changed = true;
	}
	if (changed) {
		try {
			uni.setStorageSync('user', user);
		} catch (e) {}
	}
	return normalized;
}

function saveMembershipSnapshot(source) {
	var snapshot = normalizeMembershipSnapshot(source);
	if (!snapshot || !snapshot.userId || !snapshot.hasMembershipFields) {
		return null;
	}
	try {
		uni.setStorageSync(VIEWER_MEMBERSHIP_SNAPSHOT_KEY, snapshot);
	} catch (e) {}
	return syncStoredUserMembership(snapshot);
}

function getMembershipSnapshot() {
	var user = getStoredUser();
	var currentUserId = getStoredUserId(user);
	if (!currentUserId) {
		return null;
	}
	var userSnapshot = normalizeMembershipSnapshot(user);
	if (userSnapshot && userSnapshot.userId === currentUserId && userSnapshot.hasMembershipFields) {
		return saveMembershipSnapshot(userSnapshot);
	}
	try {
		var stored = normalizeMembershipSnapshot(uni.getStorageSync(VIEWER_MEMBERSHIP_SNAPSHOT_KEY));
		if (stored && stored.userId === currentUserId && stored.hasMembershipFields) {
			return syncStoredUserMembership(stored);
		}
	} catch (e) {}
	return null;
}

function requestStoreOverview(clientUid) {
	var q = '?clientUid=' + encodeURIComponent(clientUid || '');
	return requestJson('GET', '/api/v1/store/overview' + q, null, 20000);
}

function ensureViewerMembershipSnapshot() {
	if (!hasLoggedInUser()) {
		return Promise.resolve(null);
	}
	var snapshot = getMembershipSnapshot();
	if (snapshot) {
		return Promise.resolve(snapshot);
	}
	if (viewerMembershipSnapshotPromise) {
		return viewerMembershipSnapshotPromise;
	}
	viewerMembershipSnapshotPromise = requestStoreOverview(getClientUid())
		.then(function (data) {
			return saveMembershipSnapshot(data && data.profile);
		})
		.catch(function () {
			return getMembershipSnapshot();
		})
		.then(function (resolved) {
			return resolved || null;
		})
		.finally(function () {
			viewerMembershipSnapshotPromise = null;
		});
	return viewerMembershipSnapshotPromise;
}

function applyMembershipPreviewOverride(card, snapshot) {
	if (!card || typeof card !== 'object') {
		return card;
	}
	var resolvedSnapshot = normalizeMembershipSnapshot(snapshot) || getMembershipSnapshot();
	if (!resolvedSnapshot || !resolvedSnapshot.vipActive) {
		return card;
	}
	var requiredLevel = normalizeVipLevel(
		pickDefined(card.preview_blur_vip_level, card.previewBlurVipLevel, 0)
	);
	if (requiredLevel <= 0 || resolvedSnapshot.vipType < requiredLevel) {
		return card;
	}
	var next = Object.assign({}, card);
	next.preview_blur_active = false;
	next.previewBlurActive = false;
	return next;
}

function applyMembershipPreviewOverrideList(list, snapshot) {
	if (!Array.isArray(list) || !list.length) {
		return Array.isArray(list) ? list : [];
	}
	return list.map(function (item) {
		return applyMembershipPreviewOverride(item, snapshot);
	});
}

function markCharacterAccessRefreshNeeded(reason) {
	try {
		uni.setStorageSync(CHARACTER_ACCESS_REFRESH_FLAG_KEY, reason || '1');
	} catch (e) {}
}

function consumeCharacterAccessRefreshNeeded() {
	try {
		var value = uni.getStorageSync(CHARACTER_ACCESS_REFRESH_FLAG_KEY);
		if (value) {
			uni.removeStorageSync(CHARACTER_ACCESS_REFRESH_FLAG_KEY);
		}
		return !!value;
	} catch (e) {
		return false;
	}
}

function buildLoginUrl(redirectUrl) {
	var url = '/pages/login/login';
	if (redirectUrl) {
		url += '?redirect=' + encodeURIComponent(String(redirectUrl));
	}
	return url;
}

function requestJson(method, path, data, timeout) {
	return new Promise(function (resolve, reject) {
		var opts = {
			url: baseUrl() + path,
			method: method,
			timeout: timeout || 20000,
			header: buildRequestHeaders(),
			success: function (res) {
				captureResponseDeviceToken(res);
				var ok =
					res.statusCode >= 200 &&
					res.statusCode < 300 &&
					res.data &&
					Number(res.data.code) === 1;
				if (ok) {
					resolve(res.data.data);
				} else {
					var msg = (res.data && res.data.msg) || 'request failed';
					var error = new Error(msg);
					error.statusCode = res.statusCode;
					error.response = {
						status: res.statusCode,
						data: res.data
					};
					error.data = res.data;
					reject(error);
				}
			},
			fail: function (err) {
				reject(err || new Error('network'));
			}
		};
		if (method === 'POST' || method === 'PUT') {
			opts.header = buildRequestHeaders({ 'Content-Type': 'application/json' });
			opts.data = data || {};
		}
		uni.request(opts);
	});
}

function buildCharacterListQuery(params, includeClientUid) {
	params = params || {};
	var q = [];
	var limit = Number(params.limit);
	var offset = Number(params.offset);
	if (params.q) q.push('q=' + encodeURIComponent(params.q));
	if (params.tag) q.push('tag=' + encodeURIComponent(params.tag));
	if (params.gameplay) q.push('gameplay=' + encodeURIComponent(params.gameplay));
	if (params.sort && params.sort !== 'default') q.push('sort=' + encodeURIComponent(params.sort));
	if (isFinite(limit) && limit > 0) {
		limit = Math.min(500, Math.max(1, Math.floor(limit)));
		q.push('limit=' + encodeURIComponent(String(limit)));
	}
	if (isFinite(offset) && offset > 0) {
		offset = Math.max(0, Math.floor(offset));
		q.push('offset=' + encodeURIComponent(String(offset)));
	}
	if (includeClientUid !== false) {
		q.push('clientUid=' + encodeURIComponent(getClientUid()));
	}
	return q.length ? '?' + q.join('&') : '';
}

/**
 * @param {{ q?: string, tag?: string, gameplay?: string, sort?: string, limit?: number, offset?: number }} [params]
 */
function fetchCharacterList(params) {
	var withClientUid = buildCharacterListQuery(params, true);
	var snapshotTask =
		hasLoggedInUser() && !getMembershipSnapshot()
			? ensureViewerMembershipSnapshot()
			: Promise.resolve(getMembershipSnapshot());
	return requestJson('GET', '/api/v1/characters' + withClientUid, null, 20000)
		.catch(function (error) {
			if (hasLoggedInUser()) {
				throw error;
			}
			var publicQuery = buildCharacterListQuery(params, false);
			return requestJson('GET', '/api/v1/characters' + publicQuery, null, 20000);
		})
		.then(function (list) {
			var normalizedList = normalizeCharacterCardList(list).filter(function (item) {
				return item && item.client_visible !== false;
			});
			return Promise.resolve(snapshotTask).then(function (snapshot) {
				return applyMembershipPreviewOverrideList(normalizedList, snapshot);
			});
		});
}

function fetchCharacterTags() {
	return requestJson('GET', '/api/v1/characters/tags', null, 15000);
}

function fetchAppNotices() {
	return requestJson('GET', '/api/v1/app/notices', null, 15000);
}

function fetchUserMessages(clientUid, limit) {
	var l = Number(limit);
	if (!isFinite(l) || l <= 0) l = 20;
	if (l > 50) l = 50;
	var q =
		'?clientUid=' +
		encodeURIComponent(clientUid || getClientUid()) +
		'&limit=' +
		encodeURIComponent(String(l));
	return requestJson('GET', '/api/v1/app/messages' + q, null, 15000);
}

function normalizeInboxUnreadState(data) {
	data = data && typeof data === 'object' ? data : {};
	var noticeUnread = Math.max(0, Number(data.noticeUnread) || 0);
	var messageUnread = Math.max(0, Number(data.messageUnread) || 0);
	var unreadCount =
		data.unreadCount == null
			? noticeUnread + messageUnread
			: Math.max(0, Number(data.unreadCount) || 0);
	return {
		noticeUnread: noticeUnread,
		messageUnread: messageUnread,
		unreadCount: unreadCount
	};
}

function fetchInboxUnreadState(clientUid) {
	var q = '?clientUid=' + encodeURIComponent(clientUid || getClientUid());
	return requestJson('GET', '/api/v1/app/inbox/unread' + q, null, 15000).then(normalizeInboxUnreadState);
}

function markInboxReadAll(clientUid) {
	return requestJson(
		'POST',
		'/api/v1/app/inbox/read-all',
		{ clientUid: clientUid || getClientUid() },
		15000
	).then(normalizeInboxUnreadState);
}

function markNoticeRead(clientUid, noticeId) {
	return requestJson(
		'POST',
		'/api/v1/app/inbox/notice-read',
		{
			clientUid: clientUid || getClientUid(),
			noticeId: noticeId
		},
		15000
	).then(normalizeInboxUnreadState);
}

function fetchMeStats(clientUid) {
	var q = '?clientUid=' + encodeURIComponent(clientUid || '');
	return requestJson('GET', '/api/v1/app/me/stats' + q, null, 15000);
}

function fetchStoreOverview(clientUid) {
	return requestStoreOverview(clientUid).then(function (data) {
		saveMembershipSnapshot(data && data.profile);
		return data;
	});
}

function fetchStoreProducts(type) {
	var q = type ? '?type=' + encodeURIComponent(type) : '';
	return requestJson('GET', '/api/v1/store/products' + q, null, 20000);
}

function fetchStoreOrders(clientUid, limit) {
	var l = Number(limit);
	if (!isFinite(l) || l <= 0) l = 20;
	if (l > 50) l = 50;
	var q =
		'?clientUid=' +
		encodeURIComponent(clientUid || '') +
		'&limit=' +
		encodeURIComponent(String(l));
	return requestJson('GET', '/api/v1/store/orders' + q, null, 20000);
}

function postStoreOrderCreate(payload) {
	return requestJson('POST', '/api/v1/store/orders/create', payload, 20000);
}

function postStoreOrderPay(payload) {
	return requestJson('POST', '/api/v1/store/orders/pay', payload, 20000);
}

function postStoreOrderMockPay(payload) {
	return requestJson('POST', '/api/v1/store/orders/mock-pay', payload, 20000);
}

function fetchSupportMeta() {
	return requestJson('GET', '/api/v1/support/meta', null, 15000);
}

function fetchSupportTickets(clientUid, status, limit) {
	var l = Number(limit);
	if (!isFinite(l) || l <= 0) l = 20;
	if (l > 50) l = 50;
	var q =
		'?clientUid=' +
		encodeURIComponent(clientUid || '') +
		(status ? '&status=' + encodeURIComponent(status) : '') +
		'&limit=' +
		encodeURIComponent(String(l));
	return requestJson('GET', '/api/v1/support/tickets' + q, null, 20000);
}

function fetchSupportTicketDetail(clientUid, ticketNo) {
	var q = '?clientUid=' + encodeURIComponent(clientUid || '');
	return requestJson('GET', '/api/v1/support/tickets/' + encodeURIComponent(ticketNo) + q, null, 20000);
}

function postSupportTicketCreate(payload) {
	return requestJson('POST', '/api/v1/support/tickets/create', payload, 30000);
}

function postSupportTicketReply(payload) {
	return requestJson('POST', '/api/v1/support/tickets/reply', payload, 30000);
}

function postSupportCharacterReport(payload) {
	return requestJson('POST', '/api/v1/support/tickets/report-character', payload, 30000);
}

function buildCommunityListQuery(params) {
	params = params || {};
	var q = [];
	var feed = String(params.feed || '').trim();
	var userId = params.userId != null && params.userId !== '' ? String(params.userId).trim() : '';
	var pageNum = Number(params.pageNum || params.page || 1);
	var pageSize = Number(params.pageSize || params.limit || 10);
	if (feed) q.push('feed=' + encodeURIComponent(feed));
	if (userId) q.push('userId=' + encodeURIComponent(userId));
	if (!isFinite(pageNum) || pageNum < 1) pageNum = 1;
	if (!isFinite(pageSize) || pageSize < 1) pageSize = 10;
	pageSize = Math.min(50, Math.max(1, Math.floor(pageSize)));
	q.push('pageNum=' + encodeURIComponent(String(Math.floor(pageNum))));
	q.push('pageSize=' + encodeURIComponent(String(pageSize)));
	return q.length ? '?' + q.join('&') : '';
}

function fetchCommunityPosts(params) {
	return requestJson('GET', '/api/community/posts' + buildCommunityListQuery(params), null, 20000);
}

function fetchCommunityPost(postId) {
	return requestJson('GET', '/api/community/posts/' + encodeURIComponent(String(postId)), null, 20000);
}

function createCommunityPost(payload) {
	return requestJson('POST', '/api/community/posts', payload || {}, 20000);
}

function likeCommunityPost(postId) {
	return requestJson('POST', '/api/community/posts/' + encodeURIComponent(String(postId)) + '/like', {}, 15000);
}

function unlikeCommunityPost(postId) {
	return requestJson('DELETE', '/api/community/posts/' + encodeURIComponent(String(postId)) + '/like', null, 15000);
}

function addCommunityComment(postId, content) {
	return requestJson('POST', '/api/community/posts/' + encodeURIComponent(String(postId)) + '/comments', {
		content: content
	}, 15000);
}

function addCommunityReply(commentId, payload) {
	return requestJson('POST', '/api/community/comments/' + encodeURIComponent(String(commentId)) + '/replies', payload || {}, 15000);
}

function followCommunityUser(userId) {
	return requestJson('POST', '/api/community/users/' + encodeURIComponent(String(userId)) + '/follow', {}, 15000);
}

function unfollowCommunityUser(userId) {
	return requestJson('DELETE', '/api/community/users/' + encodeURIComponent(String(userId)) + '/follow', null, 15000);
}

function buildCommunityFriendsQuery(params) {
	params = params || {};
	var q = [];
	var relation = String(params.relation || 'all').trim();
	var pageNum = Number(params.pageNum || params.page || 1);
	var pageSize = Number(params.pageSize || params.limit || 20);
	if (!relation) relation = 'all';
	if (!isFinite(pageNum) || pageNum < 1) pageNum = 1;
	if (!isFinite(pageSize) || pageSize < 1) pageSize = 20;
	pageSize = Math.min(50, Math.max(1, Math.floor(pageSize)));
	q.push('relation=' + encodeURIComponent(relation));
	q.push('pageNum=' + encodeURIComponent(String(Math.floor(pageNum))));
	q.push('pageSize=' + encodeURIComponent(String(pageSize)));
	return '?' + q.join('&');
}

function fetchCommunityFriends(params) {
	return requestJson('GET', '/api/community/friends' + buildCommunityFriendsQuery(params), null, 20000);
}

function buildCommunityFriendRequestsQuery(params) {
	params = params || {};
	var q = [];
	var box = String(params.box || 'received').trim();
	var status = String(params.status || '').trim();
	var pageNum = Number(params.pageNum || params.page || 1);
	var pageSize = Number(params.pageSize || params.limit || 20);
	if (!box) box = 'received';
	if (!isFinite(pageNum) || pageNum < 1) pageNum = 1;
	if (!isFinite(pageSize) || pageSize < 1) pageSize = 20;
	pageSize = Math.min(50, Math.max(1, Math.floor(pageSize)));
	q.push('box=' + encodeURIComponent(box));
	if (status) q.push('status=' + encodeURIComponent(status));
	q.push('pageNum=' + encodeURIComponent(String(Math.floor(pageNum))));
	q.push('pageSize=' + encodeURIComponent(String(pageSize)));
	return '?' + q.join('&');
}

function requestCommunityFriend(targetUserId, message) {
	return requestJson('POST', '/api/community/friends/requests', {
		targetUserId: targetUserId,
		message: message || ''
	}, 15000);
}

function fetchCommunityFriendRequests(params) {
	return requestJson('GET', '/api/community/friends/requests' + buildCommunityFriendRequestsQuery(params), null, 20000);
}

function acceptCommunityFriendRequest(requestId) {
	return requestJson('POST', '/api/community/friends/requests/' + encodeURIComponent(String(requestId)) + '/accept', {}, 15000);
}

function rejectCommunityFriendRequest(requestId) {
	return requestJson('POST', '/api/community/friends/requests/' + encodeURIComponent(String(requestId)) + '/reject', {}, 15000);
}

function removeCommunityFriend(friendUserId) {
	return requestJson('DELETE', '/api/community/friends/' + encodeURIComponent(String(friendUserId)), null, 15000);
}

function blockCommunityUser(userId, reason) {
	return requestJson('POST', '/api/community/blocks/' + encodeURIComponent(String(userId)), {
		reason: reason || ''
	}, 15000);
}

function unblockCommunityUser(userId) {
	return requestJson('DELETE', '/api/community/blocks/' + encodeURIComponent(String(userId)), null, 15000);
}

function uploadCommunityImage(filePath, onProgress) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !filePath) {
			reject(new Error('invalid'));
			return;
		}
		if (isBrowserFileObject(filePath)) {
			uploadBrowserMultipart(
				'/api/common/upload',
				filePath,
				{ token: getStoredAuthToken() },
				120000,
				'社区图片上传失败',
				onProgress
			).then(function (data) {
				resolve(data && (data.url || data.mediaKey || data.path) ? (data.url || data.mediaKey || data.path) : data);
			}).catch(reject);
			return;
		}
		var uploadTask = uni.uploadFile({
			url: baseUrl() + '/api/common/upload',
			filePath: filePath,
			name: 'file',
			header: buildRequestHeaders(),
			formData: {
				token: getStoredAuthToken()
			},
			success: function (res) {
				captureResponseDeviceToken(res);
				try {
					var data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
					if (data && Number(data.code) === 1 && data.data) {
						notifyUploadProgress(onProgress, 100);
						resolve(data.data.url || data.data.mediaKey || data.data.path || data.data);
					} else {
						reject(new Error((data && data.msg) || '社区图片上传失败'));
					}
				} catch (e) {
					reject(e);
				}
			},
			fail: function (err) {
				reject(err || new Error('upload'));
			}
		});
		if (uploadTask && typeof uploadTask.onProgressUpdate === 'function') {
			uploadTask.onProgressUpdate(function (event) {
				if (event) notifyUploadProgress(onProgress, event.progress);
			});
		}
	});
}

function buildSocialChatListQuery(params) {
	params = params || {};
	var q = [];
	var pageNum = Number(params.pageNum || params.page || 1);
	var pageSize = Number(params.pageSize || params.limit || 20);
	if (!isFinite(pageNum) || pageNum < 1) pageNum = 1;
	if (!isFinite(pageSize) || pageSize < 1) pageSize = 20;
	pageSize = Math.min(50, Math.max(1, Math.floor(pageSize)));
	q.push('pageNum=' + encodeURIComponent(String(Math.floor(pageNum))));
	q.push('pageSize=' + encodeURIComponent(String(pageSize)));
	return '?' + q.join('&');
}

function buildSocialChatMessagesQuery(params) {
	params = params || {};
	var q = [];
	var limit = Number(params.limit || params.pageSize || 30);
	if (!isFinite(limit) || limit < 1) limit = 30;
	limit = Math.min(100, Math.max(1, Math.floor(limit)));
	q.push('limit=' + encodeURIComponent(String(limit)));
	if (params.beforeMessageId != null && String(params.beforeMessageId).trim() !== '') {
		q.push('beforeMessageId=' + encodeURIComponent(String(params.beforeMessageId).trim()));
	}
	return '?' + q.join('&');
}

function fetchSocialChatConversations(params) {
	return requestJson('GET', '/api/social-chat/conversations' + buildSocialChatListQuery(params), null, 20000);
}

function fetchSocialChatMessages(peerId, params) {
	return requestJson(
		'GET',
		'/api/social-chat/conversations/' + encodeURIComponent(String(peerId)) + '/messages' + buildSocialChatMessagesQuery(params),
		null,
		20000
	);
}

function markSocialChatRead(peerId) {
	return requestJson('POST', '/api/social-chat/conversations/' + encodeURIComponent(String(peerId)) + '/read', {}, 15000);
}

function sendSocialChatMessage(payload) {
	return requestJson('POST', '/api/social-chat/messages', payload || {}, 30000);
}

function recallSocialChatMessage(messageId) {
	return requestJson('POST', '/api/social-chat/messages/' + encodeURIComponent(String(messageId)) + '/recall', {}, 15000);
}

function uploadSocialChatImage(filePath, onProgress) {
	return uploadCommunityImage(filePath, onProgress);
}

function uploadSupportImage(filePath, clientUid) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !filePath) {
			reject(new Error('invalid'));
			return;
		}
		uni.uploadFile({
			url: baseUrl() + '/api/v1/support/upload-image',
			filePath: filePath,
			name: 'file',
			header: buildRequestHeaders(),
			formData: {
				clientUid: clientUid || getClientUid()
			},
			success: function (res) {
				captureResponseDeviceToken(res);
				try {
					var data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
					if (data && Number(data.code) === 1 && data.data) {
						resolve(data.data);
					} else {
						reject(new Error((data && data.msg) || '图片上传失败'));
					}
				} catch (e) {
					reject(e);
				}
			},
			fail: function (err) {
				reject(err || new Error('upload'));
			}
		});
	});
}

function uploadChatImage(filePath, onProgress) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !filePath) {
			reject(new Error('invalid'));
			return;
		}
		if (isBrowserFileObject(filePath)) {
			uploadBrowserMultipart(
				'/api/app/chat/upload-image',
				filePath,
				null,
				120000,
				'\u804a\u5929\u56fe\u7247\u4e0a\u4f20\u5931\u8d25',
				onProgress
			).then(resolve).catch(reject);
			return;
		}
		var uploadTask = uni.uploadFile({
			url: baseUrl() + '/api/app/chat/upload-image',
			filePath: filePath,
			name: 'file',
			header: buildRequestHeaders(),
			success: function (res) {
				captureResponseDeviceToken(res);
				try {
					var data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
					if (data && Number(data.code) === 1 && data.data) {
						notifyUploadProgress(onProgress, 100);
						resolve(data.data);
					} else {
						reject(new Error((data && data.msg) || '聊天图片上传失败'));
					}
				} catch (e) {
					reject(e);
				}
			},
			fail: function (err) {
				reject(err || new Error('upload'));
			}
		});
		if (uploadTask && typeof uploadTask.onProgressUpdate === 'function') {
			uploadTask.onProgressUpdate(function (event) {
				if (!event) {
					return;
				}
				notifyUploadProgress(onProgress, event.progress);
			});
		}
	});
}

function transcribeTavernAudio(filePath, clientUid, onProgress) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !filePath) {
			reject(new Error('invalid'));
			return;
		}
		var safeClientUid = clientUid || getClientUid();
		if (isBrowserFileObject(filePath)) {
			uploadBrowserMultipart(
				'/api/v1/tavern/chat/transcribe-audio',
				filePath,
				{ clientUid: safeClientUid },
				H5_BROWSER_UPLOAD_TIMEOUT,
				'语音识别失败',
				onProgress,
				H5_AUDIO_UPLOAD_MAX_FILE_BYTES
			).then(resolve).catch(reject);
			return;
		}
		var uploadTask = uni.uploadFile({
			url: baseUrl() + '/api/v1/tavern/chat/transcribe-audio',
			filePath: filePath,
			name: 'file',
			header: buildRequestHeaders(),
			formData: {
				clientUid: safeClientUid
			},
			success: function (res) {
				captureResponseDeviceToken(res);
				try {
					var data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
					if (data && Number(data.code) === 1 && data.data) {
						notifyUploadProgress(onProgress, 100);
						resolve(data.data);
					} else {
						reject(new Error((data && data.msg) || '语音识别失败'));
					}
				} catch (e) {
					reject(e);
				}
			},
			fail: function (err) {
				reject(err || new Error('upload'));
			}
		});
		if (uploadTask && typeof uploadTask.onProgressUpdate === 'function') {
			uploadTask.onProgressUpdate(function (event) {
				if (!event) {
					return;
				}
				notifyUploadProgress(onProgress, event.progress);
			});
		}
	});
}

function prepareLocalChatImage(filePath, onProgress) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !filePath) {
			reject(new Error('invalid'));
			return;
		}
		function finishWithDataUrl(dataUrl) {
			var safeDataUrl = dataUrl == null ? '' : String(dataUrl).trim();
			if (!safeDataUrl || safeDataUrl.indexOf('data:image/') !== 0) {
				reject(new Error('\u804a\u5929\u56fe\u7247\u5904\u7406\u5931\u8d25'));
				return;
			}
			notifyUploadProgress(onProgress, 100);
			resolve({ url: safeDataUrl });
		}
		function inferMimeTypeFromPath(pathValue) {
			var raw = pathValue == null ? '' : String(pathValue).trim().toLowerCase();
			if (raw.indexOf('.png') >= 0) return 'image/png';
			if (raw.indexOf('.webp') >= 0) return 'image/webp';
			if (raw.indexOf('.gif') >= 0) return 'image/gif';
			return 'image/jpeg';
		}
		function isRemoteHttpUrl(pathValue) {
			var raw = pathValue == null ? '' : String(pathValue).trim().toLowerCase();
			return raw.indexOf('http://') === 0 || raw.indexOf('https://') === 0;
		}
		function readBlobAsDataUrl(blobLike) {
			return new Promise(function (resolveBlob, rejectBlob) {
				try {
					var reader = new FileReader();
					reader.onload = function (event) {
						resolveBlob(event && event.target ? event.target.result : reader.result);
					};
					reader.onerror = function () {
						rejectBlob(new Error('\u804a\u5929\u56fe\u7247\u8bfb\u53d6\u5931\u8d25'));
					};
					reader.onprogress = function (event) {
						if (!event || !event.lengthComputable) return;
						notifyUploadProgress(onProgress, Math.max(1, Math.min(99, Math.round((event.loaded / event.total) * 100))));
					};
					reader.readAsDataURL(blobLike);
				} catch (err) {
					rejectBlob(err);
				}
			});
		}
		function readLocalPathAsDataUrl(pathValue) {
			var safePath = pathValue == null ? '' : String(pathValue).trim();
			if (!safePath) {
				return Promise.reject(new Error('\u804a\u5929\u56fe\u7247\u5904\u7406\u5931\u8d25'));
			}
			if (safePath.indexOf('data:image/') === 0) {
				return Promise.resolve(safePath);
			}
			if (canUseBrowserFilePicker() && (safePath.indexOf('blob:') === 0 || isRemoteHttpUrl(safePath))) {
				return fetch(safePath).then(function (res) {
					return res.blob();
				}).then(readBlobAsDataUrl);
			}
			if (isRemoteHttpUrl(safePath) && typeof uni !== 'undefined' && typeof uni.downloadFile === 'function') {
				return new Promise(function (resolveDownload, rejectDownload) {
					notifyUploadProgress(onProgress, 12);
					uni.downloadFile({
						url: safePath,
						success: function (res) {
							var tempPath = res && res.tempFilePath ? String(res.tempFilePath).trim() : '';
							if (!tempPath || !(res.statusCode >= 200 && res.statusCode < 300)) {
								rejectDownload(new Error('\u804a\u5929\u56fe\u7247\u8bfb\u53d6\u5931\u8d25'));
								return;
							}
							readLocalPathAsDataUrl(tempPath).then(resolveDownload).catch(rejectDownload);
						},
						fail: function (err) {
							rejectDownload(err || new Error('\u804a\u5929\u56fe\u7247\u8bfb\u53d6\u5931\u8d25'));
						}
					});
				});
			}
			if (typeof uni !== 'undefined' && typeof uni.getFileSystemManager === 'function') {
				var fs = uni.getFileSystemManager();
				if (fs && typeof fs.readFile === 'function') {
					return new Promise(function (resolveFs, rejectFs) {
						notifyUploadProgress(onProgress, 20);
						fs.readFile({
							filePath: safePath,
							encoding: 'base64',
							success: function (res) {
								notifyUploadProgress(onProgress, 92);
								resolveFs('data:' + inferMimeTypeFromPath(safePath) + ';base64,' + String(res && res.data ? res.data : ''));
							},
							fail: function (err) {
								rejectFs(err || new Error('\u804a\u5929\u56fe\u7247\u8bfb\u53d6\u5931\u8d25'));
							}
						});
					});
				}
			}
			if (typeof plus !== 'undefined' && plus.io && typeof plus.io.resolveLocalFileSystemURL === 'function') {
				return new Promise(function (resolvePlus, rejectPlus) {
					notifyUploadProgress(onProgress, 20);
					plus.io.resolveLocalFileSystemURL(
						safePath,
						function (entry) {
							entry.file(
								function (file) {
									try {
										var reader = new plus.io.FileReader();
										reader.onload = function (event) {
											notifyUploadProgress(onProgress, 92);
											resolvePlus(event && event.target ? event.target.result : reader.result);
										};
										reader.onerror = function (err) {
											rejectPlus(err || new Error('\u804a\u5929\u56fe\u7247\u8bfb\u53d6\u5931\u8d25'));
										};
										reader.readAsDataURL(file);
									} catch (err) {
										rejectPlus(err);
									}
								},
								function (err) {
									rejectPlus(err || new Error('\u804a\u5929\u56fe\u7247\u8bfb\u53d6\u5931\u8d25'));
								}
							);
						},
						function (err) {
							rejectPlus(err || new Error('\u804a\u5929\u56fe\u7247\u8bfb\u53d6\u5931\u8d25'));
						}
					);
				});
			}
			return Promise.reject(new Error('\u5f53\u524d\u73af\u5883\u4e0d\u652f\u6301\u672c\u5730\u56fe\u7247\u5904\u7406'));
		}
		notifyUploadProgress(onProgress, 1);
		if (isBrowserFileObject(filePath)) {
			try {
				ensureBrowserUploadFileSize(filePath);
			} catch (error) {
				reject(error);
				return;
			}
			readBlobAsDataUrl(filePath).then(finishWithDataUrl).catch(reject);
			return;
		}
		readLocalPathAsDataUrl(filePath).then(finishWithDataUrl).catch(reject);
	});
}

function parseImageDataUrl(dataUrl) {
	var safeDataUrl = dataUrl == null ? '' : String(dataUrl).trim();
	var match = safeDataUrl.match(/^data:(image\/[A-Za-z0-9.+-]+);base64,(.+)$/);
	if (!match) {
		return null;
	}
	return {
		mimeType: String(match[1] || '').trim().toLowerCase(),
		base64Data: String(match[2] || '').trim()
	};
}

function imageFormatFromMimeType(mimeType) {
	var safeMimeType = String(mimeType || '').trim().toLowerCase();
	if (safeMimeType.indexOf('jpeg') >= 0 || safeMimeType.indexOf('jpg') >= 0) {
		return 'jpg';
	}
	return 'png';
}

function persistGeneratedChatImageViaPlus(dataUrl, options) {
	return new Promise(function (resolve, reject) {
		if (
			typeof plus === 'undefined' ||
			!plus.nativeObj ||
			typeof plus.nativeObj.Bitmap !== 'function'
		) {
			reject(new Error('unsupported'));
			return;
		}
		var parsed = parseImageDataUrl(dataUrl);
		if (!parsed || !parsed.base64Data) {
			reject(new Error('invalid_data_url'));
			return;
		}
		var format = imageFormatFromMimeType(parsed.mimeType);
		var prefix = normalizeLocalScopeText(options && options.fileNamePrefix) || 'chat_image';
		var fileName =
			prefix +
			'_' +
			Date.now() +
			'_' +
			Math.random().toString(36).slice(2, 8) +
			'.' +
			format;
		var targetPath = '_doc/' + fileName;
		var bitmapId = 'jg_chat_image_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
		var bitmap = new plus.nativeObj.Bitmap(bitmapId);
		function clearBitmap() {
			try {
				bitmap.clear();
			} catch (e) {}
		}
		function saveLoadedBitmap() {
			try {
				bitmap.save(
					targetPath,
					{
						overwrite: true,
						format: format,
						quality: 100
					},
					function (event) {
						clearBitmap();
						var savedPath = normalizeLocalScopeText(event && event.target) || targetPath;
						resolve({
							url: savedPath,
							persisted: true
						});
					},
					function (error) {
						clearBitmap();
						reject(error || new Error('save_failed'));
					}
				);
			} catch (error) {
				clearBitmap();
				reject(error);
			}
		}
		function loadBitmapWith(source, fallbackSource) {
			try {
				bitmap.loadBase64Data(
					source,
					function () {
						saveLoadedBitmap();
					},
					function (error) {
						if (fallbackSource && fallbackSource !== source) {
							loadBitmapWith(fallbackSource, '');
							return;
						}
						clearBitmap();
						reject(error || new Error('load_failed'));
					}
				);
			} catch (error) {
				if (fallbackSource && fallbackSource !== source) {
					loadBitmapWith(fallbackSource, '');
					return;
				}
				clearBitmap();
				reject(error);
			}
		}
		try {
			loadBitmapWith(parsed.base64Data, dataUrl);
		} catch (error) {
			clearBitmap();
			reject(error);
		}
	});
}

function persistGeneratedRemoteImageViaUni(imageUrl, options) {
	return new Promise(function (resolve, reject) {
		if (typeof uni === 'undefined' || typeof uni.downloadFile !== 'function') {
			reject(new Error('unsupported'));
			return;
		}
		var safeUrl = imageUrl == null ? '' : String(imageUrl).trim();
		if (!safeUrl || !/^https?:\/\//i.test(safeUrl)) {
			reject(new Error('invalid_remote_url'));
			return;
		}
		uni.downloadFile({
			url: safeUrl,
			success: function (res) {
				var tempPath = normalizeLocalScopeText(res && res.tempFilePath);
				if (!tempPath || !(res.statusCode >= 200 && res.statusCode < 300)) {
					reject(new Error('download_failed'));
					return;
				}
				if (typeof uni.saveFile === 'function') {
					uni.saveFile({
						tempFilePath: tempPath,
						success: function (saveRes) {
							var savedPath = normalizeLocalScopeText(saveRes && saveRes.savedFilePath) || tempPath;
							resolve({
								url: savedPath,
								persisted: true
							});
						},
						fail: function (error) {
							reject(error || new Error('save_failed'));
						}
					});
					return;
				}
				resolve({
					url: tempPath,
					persisted: true
				});
			},
			fail: function (error) {
				reject(error || new Error('download_failed'));
			}
		});
	});
}

function persistGeneratedChatImage(imageUrl, options) {
	return new Promise(function (resolve, reject) {
		var safeUrl = imageUrl == null ? '' : String(imageUrl).trim();
		if (!safeUrl) {
			reject(new Error('invalid'));
			return;
		}
		if (safeUrl.indexOf('data:image/') !== 0) {
			persistGeneratedRemoteImageViaUni(safeUrl, options || {})
				.then(resolve)
				.catch(function () {
					resolve({
						url: safeUrl,
						persisted: false
					});
				});
			return;
		}
		persistGeneratedChatImageViaPlus(safeUrl, options || {})
			.then(resolve)
			.catch(function (error) {
				reject(error || new Error('persist_failed'));
			});
	});
}

function postMockImageGenerate(payload) {
	return requestJson('POST', '/api/v1/image/generate/mock', payload, 30000);
}

function postImageGenerate(payload) {
	return requestJson('POST', '/api/v1/image/generate', payload, 90000);
}

function fetchMeFavorites(clientUid, limit, sortBy) {
	var l = Number(limit);
	if (!isFinite(l) || l <= 0) l = 50;
	if (l > 200) l = 200;
	var safeClientUid = clientUid || getClientUid();
	var snapshotTask =
		hasLoggedInUser() && !getMembershipSnapshot()
			? ensureViewerMembershipSnapshot()
			: Promise.resolve(getMembershipSnapshot());
	var s = String(sortBy || 'favorite').trim();
	if (s !== 'recent_chat') s = 'favorite';
	var q =
		'?clientUid=' +
		encodeURIComponent(safeClientUid) +
		'&limit=' +
		encodeURIComponent(String(l)) +
		'&sortBy=' +
		encodeURIComponent(s);
	return requestJson('GET', '/api/v1/app/me/favorites' + q, null, 20000).catch(function (error) {
		if (typeof console !== 'undefined' && console.warn) {
			console.warn('fetch favorites fallback to empty', error);
		}
		return [];
	}).then(function (list) {
		var normalizedList = normalizeCharacterCardList(list).filter(function (item) {
			return item && item.client_visible !== false;
		});
		return Promise.resolve(snapshotTask).then(function (snapshot) {
			return applyMembershipPreviewOverrideList(normalizedList, snapshot);
		});
	});
}

function postMeFavoritesUnfavoriteBatch(payload) {
	return requestJson('POST', '/api/v1/app/me/favorites/unfavorite-batch', payload, 20000);
}

function fetchCharacter(id) {
	var q = '?clientUid=' + encodeURIComponent(getClientUid());
	var snapshotTask =
		hasLoggedInUser() && !getMembershipSnapshot()
			? ensureViewerMembershipSnapshot()
			: Promise.resolve(getMembershipSnapshot());
	return requestJson('GET', '/api/v1/characters/' + encodeURIComponent(id) + q, null, 20000).then(function (card) {
		var normalizedCard = normalizeCharacterCard(card);
		return Promise.resolve(snapshotTask).then(function (snapshot) {
			return applyMembershipPreviewOverride(normalizedCard, snapshot);
		});
	});
}

function fetchMyCharacters(clientUid, sortBy) {
	var s = String(sortBy || 'recent').trim();
	if (s !== 'name') s = 'recent';
	var safeClientUid = clientUid || getClientUid();
	var snapshotTask =
		hasLoggedInUser() && !getMembershipSnapshot()
			? ensureViewerMembershipSnapshot()
			: Promise.resolve(getMembershipSnapshot());
	var q =
		'?clientUid=' + encodeURIComponent(safeClientUid) + '&sort=' + encodeURIComponent(s);
	return requestJson('GET', '/api/v1/characters/mine' + q, null, 20000).then(function (list) {
		var normalizedList = normalizeCharacterCardList(list);
		return Promise.resolve(snapshotTask).then(function (snapshot) {
			return applyMembershipPreviewOverrideList(normalizedList, snapshot);
		});
		});
}

function normalizeMyCharacterCreationAccess(source) {
	var raw = source && typeof source === 'object' ? source : {};
	var limit = normalizeNonNegativeInt(raw.limit, 0);
	var used = normalizeNonNegativeInt(raw.used, 0);
	var remaining = normalizeNonNegativeInt(raw.remaining, Math.max(0, limit - used));
	return {
		allowed: raw.allowed === true,
		globalEnabled: raw.globalEnabled !== false,
		limit: limit,
		used: used,
		remaining: remaining,
		message: raw.message == null ? '' : String(raw.message).trim()
	};
}

function fetchMyCharacterCreationAccess(clientUid) {
	var q = '?clientUid=' + encodeURIComponent(clientUid || getClientUid());
	return requestJson('GET', '/api/v1/characters/mine/creation-access' + q, null, 12000)
		.then(normalizeMyCharacterCreationAccess);
}

function fetchMyCharacterEditor(id, clientUid) {
	var q = '?clientUid=' + encodeURIComponent(clientUid || '');
	return requestJson('GET', '/api/v1/characters/mine/editor/' + encodeURIComponent(id) + q, null, 20000);
}

function saveMyCharacter(payload) {
	return requestJson('POST', '/api/v1/characters/mine/save', payload, 30000);
}

function deleteMyCharacter(payload) {
	return requestJson('POST', '/api/v1/characters/mine/delete', payload, 30000);
}

function uploadMyCharacterImage(filePath, clientUid, onProgress) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !filePath) {
			reject(new Error('invalid'));
			return;
		}
		if (isBrowserFileObject(filePath)) {
			uploadBrowserMultipart(
				'/api/v1/characters/mine/upload-image',
				filePath,
				{ clientUid: clientUid || getClientUid() },
				120000,
				'\u56fe\u7247\u4e0a\u4f20\u5931\u8d25',
				onProgress
			).then(resolve).catch(reject);
			return;
		}
		var uploadTask = uni.uploadFile({
			url: baseUrl() + '/api/v1/characters/mine/upload-image',
			filePath: filePath,
			name: 'file',
			header: buildRequestHeaders(),
			formData: {
				clientUid: clientUid || getClientUid()
			},
			success: function (res) {
				captureResponseDeviceToken(res);
				try {
					var data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
					if (data && Number(data.code) === 1 && data.data) {
						notifyUploadProgress(onProgress, 100);
						resolve(data.data);
					} else {
						reject(new Error((data && data.msg) || '图片上传失败'));
					}
				} catch (e) {
					reject(e);
				}
			},
			fail: function (err) {
				reject(err || new Error('upload'));
			}
		});
		if (uploadTask && typeof uploadTask.onProgressUpdate === 'function') {
			uploadTask.onProgressUpdate(function (event) {
				if (!event) {
					return;
				}
				notifyUploadProgress(onProgress, event.progress);
			});
		}
	});
}

function importMyCharacterPng(filePath, clientUid, onProgress) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !filePath) {
			reject(new Error('invalid'));
			return;
		}
		if (isBrowserFileObject(filePath)) {
			uploadBrowserMultipart(
				'/api/v1/characters/mine/import-sillytavern-png',
				filePath,
				{ clientUid: clientUid || getClientUid() },
				120000,
				'PNG \u5bfc\u5165\u5931\u8d25',
				onProgress
			).then(resolve).catch(reject);
			return;
		}
		var uploadTask = uni.uploadFile({
			url: baseUrl() + '/api/v1/characters/mine/import-sillytavern-png',
			filePath: filePath,
			name: 'file',
			header: buildRequestHeaders(),
			formData: {
				clientUid: clientUid || getClientUid()
			},
			success: function (res) {
				captureResponseDeviceToken(res);
				try {
					var data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
					if (data && Number(data.code) === 1 && data.data) {
						notifyUploadProgress(onProgress, 100);
						resolve(data.data);
					} else {
						reject(new Error((data && data.msg) || 'PNG 导入失败'));
					}
				} catch (e) {
					reject(e);
				}
			},
			fail: function (err) {
				reject(err || new Error('upload'));
			}
		});
		if (uploadTask && typeof uploadTask.onProgressUpdate === 'function') {
			uploadTask.onProgressUpdate(function (event) {
				if (!event) {
					return;
				}
				notifyUploadProgress(onProgress, event.progress);
			});
		}
	});
}

function pickDefined() {
	for (var i = 0; i < arguments.length; i++) {
		var value = arguments[i];
		if (value !== undefined && value !== null) {
			return value;
		}
	}
	return undefined;
}

function toSafeNumber(value, fallback) {
	var n = Number(value);
	if (!isFinite(n)) {
		return fallback == null ? 0 : fallback;
	}
	return n;
}

function normalizeCharacterCard(card) {
	if (!card || typeof card !== 'object') {
		return card;
	}
	var normalized = Object.assign({}, card);
	normalized.creator_handle = pickDefined(card.creator_handle, card.creatorHandle, '');
	normalized.owner_client_uid = pickDefined(card.owner_client_uid, card.ownerClientUid, '');
	normalized.private_card = !!pickDefined(card.private_card, card.privateCard, false);
	normalized.like_count = Math.max(0, Math.floor(toSafeNumber(pickDefined(card.like_count, card.likeCount), 0)));
	normalized.dislike_count = Math.max(0, Math.floor(toSafeNumber(pickDefined(card.dislike_count, card.dislikeCount), 0)));
	normalized.is_favorite = !!pickDefined(card.is_favorite, card.isFavorite, false);
	normalized.user_vote = pickDefined(card.user_vote, card.userVote, 'none') || 'none';
	normalized.label_array = Array.isArray(card.label_array)
		? card.label_array
		: Array.isArray(card.labelArray)
			? card.labelArray
			: [];
	normalized.occupation_arr = pickDefined(card.occupation_arr, card.occupationArr, '');
	normalized.first_message = pickDefined(card.first_message, card.firstMessage, '');
	normalized.chat_background_url = pickDefined(
		card.chat_background_url,
		card.chatBackgroundUrl,
		''
	);
	normalized.system_prompt = pickDefined(card.system_prompt, card.systemPrompt, '');
	normalized.post_history_instructions = pickDefined(
		card.post_history_instructions,
		card.postHistoryInstructions,
		''
	);
	normalized.mes_example = pickDefined(card.mes_example, card.mesExample, '');
	normalized.token_display = pickDefined(card.token_display, card.tokenDisplay, '');
	normalized.gameplay_type = pickDefined(card.gameplay_type, card.gameplayType, '');
	normalized.avatar_thumb = pickDefined(
		card.avatar_thumb,
		card.avatarThumb,
		card.avatarThumbUrl,
		''
	);
	normalized.cover_thumb = pickDefined(
		card.cover_thumb,
		card.coverThumb,
		card.coverThumbUrl,
		normalized.avatar_thumb
	);
	normalized.cover_detail = pickDefined(
		card.cover_detail,
		card.coverDetail,
		normalized.cover_thumb
	);
	normalized.vip_only = !!pickDefined(card.vip_only, card.vipOnly, false);
	normalized.client_visible = !!pickDefined(card.client_visible, card.clientVisible, true);
	normalized.preview_blur_vip_level = Math.max(
		0,
		Math.min(2, Math.floor(toSafeNumber(pickDefined(card.preview_blur_vip_level, card.previewBlurVipLevel), 0)))
	);
	normalized.preview_blur_active = !!pickDefined(card.preview_blur_active, card.previewBlurActive, false);
	normalized.chat_modes = Array.isArray(card.chat_modes)
		? card.chat_modes
		: Array.isArray(card.chatModes)
			? card.chatModes
			: [];
	normalized.token_cost = toSafeNumber(pickDefined(card.token_cost, card.tokenCost), 0);
	return applyMembershipPreviewOverride(normalized);
}

function normalizeCharacterCardList(list) {
	if (!Array.isArray(list)) {
		return [];
	}
	return list.map(function (item) {
		return normalizeCharacterCard(item);
	});
}

function fetchTavernMessages(characterId, clientUid, options) {
	var opts = options && typeof options === 'object' ? options : {};
	var q =
		'?characterId=' +
		encodeURIComponent(characterId) +
		'&clientUid=' +
		encodeURIComponent(clientUid);
	if (opts.beforeMessageId != null && String(opts.beforeMessageId).trim() !== '') {
		q += '&beforeMessageId=' + encodeURIComponent(String(opts.beforeMessageId).trim());
	}
	if (opts.limit != null && String(opts.limit).trim() !== '') {
		q += '&limit=' + encodeURIComponent(String(opts.limit).trim());
	}
	return requestJson('GET', '/api/v1/tavern/messages' + q, null, 20000);
}

/** @returns {Promise<Array>} */
function fetchTavernSessions(clientUid) {
	var q = '?clientUid=' + encodeURIComponent(clientUid || '');
	return requestJson('GET', '/api/v1/tavern/sessions' + q, null, 20000).then(function (d) {
		return d && d.sessions ? d.sessions : [];
	});
}

function postTavernChat(payload) {
	return requestJson('POST', '/api/v1/tavern/chat', payload, 120000);
}

function postTavernSpeech(payload) {
	return requestJson('POST', '/api/v1/tavern/chat/tts', payload, 120000);
}

function canUseFetchReadableStream() {
	if (typeof fetch !== 'function' || typeof TextDecoder !== 'function') {
		return false;
	}
	if (typeof plus !== 'undefined' && plus && plus.runtime) {
		return false;
	}
	return typeof ReadableStream !== 'undefined';
}

function canUseXhrStream() {
	return typeof XMLHttpRequest !== 'undefined';
}

function jgStreamEnabled() {
	return jgEnabled() && api.jgChatStream !== false && (canUseFetchReadableStream() || canUseXhrStream());
}

/**
 * SSE：事件 delta 的 data 为 {"t":"片段"}，done 的 data 含 content。
 * @param {object} payload characterId, clientUid, content, temperature?, model?
 * @param {{ onDelta?: (t:string)=>void, onDone?: (data:object)=>void, onError?: (e:Error)=>void }} handlers
 */
function buildTavernProfileQuery(clientUid, context) {
	var q = ['clientUid=' + encodeURIComponent(clientUid || '')];
	if (context != null && context !== '') {
		if (typeof context === 'object') {
			if (context.characterId != null && context.characterId !== '') {
				q.push('characterId=' + encodeURIComponent(String(context.characterId)));
			}
			if (context.conversationId != null && context.conversationId !== '') {
				q.push('conversationId=' + encodeURIComponent(String(context.conversationId)));
			}
		} else {
			q.push('characterId=' + encodeURIComponent(String(context)));
		}
	}
	return '?' + q.join('&');
}

function getTavernProfile(clientUid, context) {
	var q = buildTavernProfileQuery(clientUid, context);
	return requestJson('GET', '/api/v1/tavern/profile' + q, null, 15000);
}

function putTavernProfile(clientUid, body, context) {
	var q = buildTavernProfileQuery(clientUid, context);
	return requestJson('PUT', '/api/v1/tavern/profile' + q, body, 15000);
}

function buildUserAiProviderQuery(clientUid) {
	return '?clientUid=' + encodeURIComponent(clientUid || '');
}

function getTavernUserAiProvider(clientUid) {
	return requestJson('GET', '/api/v1/tavern/ai-provider' + buildUserAiProviderQuery(clientUid), null, 15000);
}

function putTavernUserAiProvider(clientUid, body) {
	return requestJson('PUT', '/api/v1/tavern/ai-provider' + buildUserAiProviderQuery(clientUid), body, 15000);
}

function testTavernUserAiProvider(clientUid, body) {
	return requestJson('POST', '/api/v1/tavern/ai-provider/test' + buildUserAiProviderQuery(clientUid), body, 25000);
}

function listTavernUserAiProviderModels(clientUid, body) {
	return requestJson('POST', '/api/v1/tavern/ai-provider/models' + buildUserAiProviderQuery(clientUid), body, 25000);
}

function postTavernRegenerate(payload) {
	return requestJson('POST', '/api/v1/tavern/chat/regenerate', payload, 120000);
}

function postTavernContinue(payload) {
	return requestJson('POST', '/api/v1/tavern/chat/continue', payload, 120000);
}

/** 显式通知服务端取消当前会话的生成任务（与 Abort SSE 配合） */
function postTavernChatStop(payload) {
	return requestJson('POST', '/api/v1/tavern/chat/stop', payload, 15000);
}

function postTavernMemoryRefresh(payload) {
	return requestJson('POST', '/api/v1/tavern/memory/refresh', payload, 120000);
}

/** 删除与某角色的整段会话（消息 + 记忆 + 会话行） */
function postTavernSessionDelete(payload) {
	return requestJson('POST', '/api/v1/tavern/sessions/delete', payload, 30000);
}

/** 聊天页：清空与该角色的会话消息并取消归档（同一 conversation，非删卡） */
function postTavernSessionRestart(payload) {
	return requestJson('POST', '/api/v1/tavern/sessions/restart', payload, 30000);
}

/**
 * 角色互动：action 为 like | dislike | favorite（均为切换）
 * @returns {Promise<{like_count:number, dislike_count:number, is_favorite:boolean, user_vote:string}>}
 */
function postCharacterInteraction(payload) {
	return requestJson('POST', '/api/v1/characters/interaction', payload, 20000);
}

function createCharacterDraft(payload) {
	return requestJson('POST', '/api/v1/characters/create-draft', payload, 20000);
}

/**
 * 上传 PNG 角色卡（小程序/App 本地路径）。H5 需用 input[type=file] + FormData 自行请求同一路径。
 * @returns {Promise<{id:number}>}
 */
function uploadSillyTavernPng(filePath) {
	return new Promise(function (resolve, reject) {
		if (!jgEnabled() || !filePath) {
			reject(new Error('invalid'));
			return;
		}
		if (isBrowserFileObject(filePath)) {
			uploadBrowserMultipart(
				'/api/v1/characters/import-sillytavern-png',
				filePath,
				null,
				120000,
				'PNG \u5bfc\u5165\u5931\u8d25'
			).then(resolve).catch(reject);
			return;
		}
		uni.uploadFile({
			url: baseUrl() + '/api/v1/characters/import-sillytavern-png',
			filePath: filePath,
			name: 'file',
			header: buildRequestHeaders(),
			success: function (res) {
				captureResponseDeviceToken(res);
				try {
					var data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
					if (data && Number(data.code) === 1) {
						resolve(data.data);
					} else {
						reject(new Error((data && data.msg) || 'PNG 导入失败'));
					}
				} catch (e) {
					reject(e);
				}
			},
			fail: function (err) {
				reject(err || new Error('upload'));
			}
		});
	});
}

/**
 * @param {string} path 如 '/api/v1/tavern/chat/stream'
 * @param {{ signal?: AbortSignal }} [opts]
 */
function extractSseHttpErrorMessage(raw) {
	var text = String(raw || '').trim();
	if (!text) {
		return '';
	}
	var fromEventStream = extractSseEventMessage(text);
	if (fromEventStream) {
		return fromEventStream;
	}
	try {
		var obj = JSON.parse(text);
		if (obj) {
			if (obj.msg) return String(obj.msg);
			if (obj.message) return String(obj.message);
			if (obj.data && obj.data.message) return String(obj.data.message);
		}
	} catch (e) {}
	if (/^\s*</.test(text)) {
		return '';
	}
	return text.length > 200 ? text.slice(0, 200) : text;
}

function extractSseEventMessage(raw) {
	var blocks = String(raw || '').split(/\n\n/);
	for (var i = 0; i < blocks.length; i++) {
		var lines = String(blocks[i] || '').split(/\r?\n/);
		var eventName = '';
		var dataStr = '';
		for (var j = 0; j < lines.length; j++) {
			var line = lines[j];
			if (line.indexOf('event:') === 0) {
				eventName = line.slice(6).trim();
			} else if (line.indexOf('data:') === 0) {
				dataStr += line.slice(5).trim();
			}
		}
		if (eventName !== 'error' || !dataStr) {
			continue;
		}
		try {
			var obj = JSON.parse(dataStr);
			if (obj && obj.message) {
				return String(obj.message);
			}
		} catch (e) {}
	}
	return '';
}

function isAbortLikeError(err) {
	if (!err) {
		return false;
	}
	if (err.name === 'AbortError' || err.code === 20) {
		return true;
	}
	var msg = String(err.message || err || '').toLowerCase();
	return msg.indexOf('aborted') >= 0 || msg.indexOf('aborterror') >= 0;
}

function parseSseBlock(block, handlers) {
	if (!block || !String(block).trim()) {
		return;
	}
	var h = handlers || {};
	var ev = 'message';
	var dataStr = '';
	var lines = String(block).split(/\r?\n/);
	for (var i = 0; i < lines.length; i++) {
		var line = lines[i];
		if (line.indexOf('event:') === 0) {
			ev = line.slice(6).trim();
		} else if (line.indexOf('data:') === 0) {
			dataStr += line.slice(5).trim();
		}
	}
	if (!dataStr) {
		return;
	}
	var obj;
	try {
		obj = JSON.parse(dataStr);
	} catch (e) {
		return;
	}
	if (ev === 'delta' && obj.t != null && h.onDelta) {
		h.onDelta(String(obj.t));
	}
	if (ev === 'done' && h.onDone) {
		h.onDone(obj);
	}
	if (ev === 'error' && h.onError) {
		h.onError(new Error(obj.message || 'stream error'));
	}
}

function parseSseChunks(buffer, handlers) {
	var chunks = String(buffer || '').split(/\r?\n\r?\n/);
	var tail = chunks.pop() || '';
	for (var i = 0; i < chunks.length; i++) {
		parseSseBlock(chunks[i], handlers);
	}
	return tail;
}

function postTavernXhrSseStream(path, payload, handlers, opts) {
	var url = baseUrl() + path;
	var h = handlers || {};
	var signal = opts && opts.signal;
	return new Promise(function (resolve) {
		var xhr = new XMLHttpRequest();
		var seenLength = 0;
		var buffer = '';
		var settled = false;
		var aborted = false;

		function cleanup() {
			if (signal && signal.removeEventListener) {
				signal.removeEventListener('abort', onAbort);
			}
		}

		function done() {
			if (settled) {
				return;
			}
			settled = true;
			cleanup();
			resolve();
		}

		function emitError(err) {
			if (isAbortLikeError(err) || aborted) {
				if (h.onAbort) {
					h.onAbort();
				}
				done();
				return;
			}
			if (h.onError) {
				h.onError(err instanceof Error ? err : new Error(String(err || 'stream error')));
			}
			done();
		}

		function onAbort() {
			aborted = true;
			try {
				xhr.abort();
			} catch (e) {}
		}

		function captureXhrDeviceToken() {
			try {
				captureResponseDeviceToken({
					header: {
						'X-Device-Token': xhr.getResponseHeader('X-Device-Token')
					}
				});
			} catch (e) {}
		}

		function consumeProgress() {
			var text = xhr.responseText || '';
			if (text.length <= seenLength) {
				return;
			}
			var chunk = text.slice(seenLength);
			seenLength = text.length;
			buffer += chunk;
			buffer = parseSseChunks(buffer, h);
		}

		xhr.open('POST', url, true);
		var headers = buildRequestHeaders({ 'Content-Type': 'application/json', Accept: 'text/event-stream' });
		Object.keys(headers).forEach(function (key) {
			xhr.setRequestHeader(key, headers[key]);
		});
		xhr.onprogress = function () {
			try {
				consumeProgress();
			} catch (e) {
				emitError(e);
			}
		};
		xhr.onreadystatechange = function () {
			if (xhr.readyState !== 4 || settled) {
				return;
			}
			captureXhrDeviceToken();
			if (aborted || (signal && signal.aborted)) {
				emitError(new Error('aborted'));
				return;
			}
			if (xhr.status === 0) {
				emitError(new Error('network error'));
				return;
			}
			if (xhr.status < 200 || xhr.status >= 300) {
				emitError(new Error(extractSseHttpErrorMessage(xhr.responseText) || 'HTTP ' + xhr.status));
				return;
			}
			try {
				consumeProgress();
				if (buffer.trim()) {
					parseSseBlock(buffer, h);
				}
				done();
			} catch (e) {
				emitError(e);
			}
		};
		xhr.onerror = function () {
			emitError(new Error('network error'));
		};
		xhr.ontimeout = function () {
			emitError(new Error('request timeout'));
		};
		if (signal && signal.addEventListener) {
			if (signal.aborted) {
				onAbort();
				return;
			}
			signal.addEventListener('abort', onAbort);
		}
		xhr.send(JSON.stringify(payload));
	});
}

function postTavernSseStream(path, payload, handlers, opts) {
	if (!canUseFetchReadableStream()) {
		return postTavernXhrSseStream(path, payload, handlers, opts);
	}
	var url = baseUrl() + path;
	var h = handlers || {};
	var signal = opts && opts.signal;
	var fetchOpts = {
		method: 'POST',
		headers: buildRequestHeaders({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
		body: JSON.stringify(payload)
	};
	if (signal) {
		fetchOpts.signal = signal;
	}
	return fetch(url, fetchOpts)
		.then(function (res) {
			captureResponseDeviceToken({ headers: res.headers });
			if (!res.ok) {
				return res.text().then(function (t) {
					throw new Error(extractSseHttpErrorMessage(t) || 'HTTP ' + res.status);
				});
			}
			if (!res.body || !res.body.getReader) {
				return postTavernXhrSseStream(path, payload, handlers, opts);
			}
			var reader = res.body.getReader();
			var dec = new TextDecoder();
			var buf = '';
			function pump() {
				return reader.read().then(function (result) {
					if (result.done) {
						if (buf.trim()) {
							parseSseBlock(buf, h);
						}
						return;
					}
					buf += dec.decode(result.value, { stream: true });
					buf = parseSseChunks(buf, h);
					return pump();
				});
			}
			return pump();
		})
		.catch(function (e) {
			if (isAbortLikeError(e)) {
				if (h.onAbort) {
					h.onAbort();
				}
				return;
			}
			if (h.onError) {
				h.onError(e instanceof Error ? e : new Error(String(e)));
				return;
			}
			return Promise.reject(e);
		});
}

function postTavernChatStream(payload, handlers, opts) {
	return postTavernSseStream('/api/v1/tavern/chat/stream', payload, handlers, opts);
}

function postTavernRegenerateStream(payload, handlers, opts) {
	return postTavernSseStream('/api/v1/tavern/chat/regenerate/stream', payload, handlers, opts);
}

function postTavernContinueStream(payload, handlers, opts) {
	return postTavernSseStream('/api/v1/tavern/chat/continue/stream', payload, handlers, opts);
}

function fetchTavernReplySuggestions(payload) {
	return requestJson('POST', '/api/v1/tavern/reply-suggestions', payload, 45000).then(function (data) {
		return data && Array.isArray(data.suggestions) ? data.suggestions : [];
	});
}

function postTavernSwipeSelect(payload) {
	return requestJson('POST', '/api/v1/tavern/messages/swipe', payload, 15000);
}

function postTavernEditUserBranch(payload) {
	return requestJson('POST', '/api/v1/tavern/messages/edit-user-branch', payload, 15000);
}

function postTavernDeleteMessageBranch(payload) {
	return requestJson('POST', '/api/v1/tavern/messages/delete-branch', payload, 15000);
}

module.exports = {
	jgEnabled: jgEnabled,
	resolveJgAssetUrl: resolveJgAssetUrl,
	wsBaseUrl: wsBaseUrl,
	jgStreamEnabled: jgStreamEnabled,
	getClientUid: getClientUid,
	getDeviceToken: getDeviceToken,
	getUploadMaxFileBytes: getUploadMaxFileBytes,
	canUseBrowserFilePicker: canUseBrowserFilePicker,
	pickBrowserImageFile: pickBrowserImageFile,
	pickBrowserPngFile: pickBrowserPngFile,
	getStoredUser: getStoredUser,
	getStoredUserId: getStoredUserId,
	hasLoggedInUser: hasLoggedInUser,
	getViewerStateSignature: getViewerStateSignature,
	getProfileAccessSignature: getProfileAccessSignature,
	getRuntimeFeatureConfig: getRuntimeFeatureConfig,
	fetchAppRuntimeConfig: fetchAppRuntimeConfig,
	getSocialFeatureConfig: getSocialFeatureConfig,
	fetchSocialFeatureConfig: fetchSocialFeatureConfig,
	canShowCommunityEntry: canShowCommunityEntry,
	canShowChatEntry: canShowChatEntry,
	isLoginEnabled: isLoginEnabled,
	isRegisterEnabled: isRegisterEnabled,
	isUserCharacterCreationEnabled: isUserCharacterCreationEnabled,
	isUserByokEnabled: isUserByokEnabled,
	isVoiceFeatureEnabled: isVoiceFeatureEnabled,
	markCharacterAccessRefreshNeeded: markCharacterAccessRefreshNeeded,
	consumeCharacterAccessRefreshNeeded: consumeCharacterAccessRefreshNeeded,
	buildLoginUrl: buildLoginUrl,
	fetchCharacterList: fetchCharacterList,
	fetchCharacterTags: fetchCharacterTags,
	fetchAppNotices: fetchAppNotices,
	fetchUserMessages: fetchUserMessages,
	fetchInboxUnreadState: fetchInboxUnreadState,
	markInboxReadAll: markInboxReadAll,
	markNoticeRead: markNoticeRead,
	fetchMeStats: fetchMeStats,
	fetchStoreOverview: fetchStoreOverview,
	fetchStoreProducts: fetchStoreProducts,
	fetchStoreOrders: fetchStoreOrders,
	fetchSupportMeta: fetchSupportMeta,
	fetchSupportTickets: fetchSupportTickets,
	fetchSupportTicketDetail: fetchSupportTicketDetail,
	fetchMeFavorites: fetchMeFavorites,
	postStoreOrderCreate: postStoreOrderCreate,
	postStoreOrderPay: postStoreOrderPay,
	postStoreOrderMockPay: postStoreOrderMockPay,
	postSupportTicketCreate: postSupportTicketCreate,
	postSupportTicketReply: postSupportTicketReply,
	postSupportCharacterReport: postSupportCharacterReport,
	fetchCommunityPosts: fetchCommunityPosts,
	fetchCommunityPost: fetchCommunityPost,
	createCommunityPost: createCommunityPost,
	likeCommunityPost: likeCommunityPost,
	unlikeCommunityPost: unlikeCommunityPost,
	addCommunityComment: addCommunityComment,
	addCommunityReply: addCommunityReply,
	followCommunityUser: followCommunityUser,
	unfollowCommunityUser: unfollowCommunityUser,
	fetchCommunityFriends: fetchCommunityFriends,
	requestCommunityFriend: requestCommunityFriend,
	fetchCommunityFriendRequests: fetchCommunityFriendRequests,
	acceptCommunityFriendRequest: acceptCommunityFriendRequest,
	rejectCommunityFriendRequest: rejectCommunityFriendRequest,
	removeCommunityFriend: removeCommunityFriend,
	blockCommunityUser: blockCommunityUser,
	unblockCommunityUser: unblockCommunityUser,
	uploadCommunityImage: uploadCommunityImage,
	fetchSocialChatConversations: fetchSocialChatConversations,
	fetchSocialChatMessages: fetchSocialChatMessages,
	markSocialChatRead: markSocialChatRead,
	sendSocialChatMessage: sendSocialChatMessage,
	recallSocialChatMessage: recallSocialChatMessage,
	uploadSocialChatImage: uploadSocialChatImage,
	postMockImageGenerate: postMockImageGenerate,
	postImageGenerate: postImageGenerate,
	postMeFavoritesUnfavoriteBatch: postMeFavoritesUnfavoriteBatch,
	uploadChatImage: uploadChatImage,
	transcribeTavernAudio: transcribeTavernAudio,
	prepareLocalChatImage: prepareLocalChatImage,
	persistGeneratedChatImage: persistGeneratedChatImage,
	fetchCharacter: fetchCharacter,
	fetchMyCharacters: fetchMyCharacters,
	fetchMyCharacterCreationAccess: fetchMyCharacterCreationAccess,
	fetchMyCharacterEditor: fetchMyCharacterEditor,
	fetchTavernMessages: fetchTavernMessages,
	fetchTavernSessions: fetchTavernSessions,
	postTavernChat: postTavernChat,
	postTavernSpeech: postTavernSpeech,
	postTavernChatStream: postTavernChatStream,
	postTavernSseStream: postTavernSseStream,
	postTavernRegenerateStream: postTavernRegenerateStream,
	postTavernContinueStream: postTavernContinueStream,
	fetchTavernReplySuggestions: fetchTavernReplySuggestions,
	postTavernSwipeSelect: postTavernSwipeSelect,
	postTavernEditUserBranch: postTavernEditUserBranch,
	postTavernDeleteMessageBranch: postTavernDeleteMessageBranch,
	getTavernProfile: getTavernProfile,
	putTavernProfile: putTavernProfile,
	getTavernUserAiProvider: getTavernUserAiProvider,
	putTavernUserAiProvider: putTavernUserAiProvider,
	testTavernUserAiProvider: testTavernUserAiProvider,
	listTavernUserAiProviderModels: listTavernUserAiProviderModels,
	postTavernRegenerate: postTavernRegenerate,
	postTavernContinue: postTavernContinue,
	postTavernChatStop: postTavernChatStop,
	postTavernMemoryRefresh: postTavernMemoryRefresh,
	postTavernSessionDelete: postTavernSessionDelete,
	postTavernSessionRestart: postTavernSessionRestart,
	cleanupLocalConversationArtifacts: cleanupLocalConversationArtifacts,
	cleanupLocalCharacterArtifacts: cleanupLocalCharacterArtifacts,
	postCharacterInteraction: postCharacterInteraction,
	createCharacterDraft: createCharacterDraft,
	saveMyCharacter: saveMyCharacter,
	deleteMyCharacter: deleteMyCharacter,
	uploadMyCharacterImage: uploadMyCharacterImage,
	importMyCharacterPng: importMyCharacterPng,
	uploadSupportImage: uploadSupportImage,
	uploadSillyTavernPng: uploadSillyTavernPng
};
