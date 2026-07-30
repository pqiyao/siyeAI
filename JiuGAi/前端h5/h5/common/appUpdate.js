var api = require('./api.js');

var FALLBACK_APP_ID = '__UNI__200F612';
var FALLBACK_VERSION_NAME = '1.3.8';
var FALLBACK_VERSION_CODE = 103;
var OFFICIAL_CHANNEL = 'official';
var CHECK_SUCCESS_INTERVAL = 6 * 60 * 60 * 1000;
var CHECK_FAILURE_INTERVAL = 2 * 60 * 1000;
var LAUNCH_DELAY = 1400;
var STATE_KEY = 'app_android_update_state_v1';
var PENDING_KEY = 'app_android_update_pending_v1';
var UPDATE_PAGE = '/pages/system/app-update';

var launchTimer = null;
var inFlight = null;
var inFlightManual = false;
var routeOpening = false;

function isAndroidApp() {
	try {
		return typeof plus !== 'undefined' && plus.os && String(plus.os.name || '').toLowerCase() === 'android';
	} catch (e) {
		return false;
	}
}

function safeGetStorage(key, fallback) {
	try {
		var value = uni.getStorageSync(key);
		return value && typeof value === 'object' ? value : fallback;
	} catch (e) {
		return fallback;
	}
}

function safeSetStorage(key, value) {
	try { uni.setStorageSync(key, value); } catch (e) {}
}

function safeRemoveStorage(key) {
	try { uni.removeStorageSync(key); } catch (e) {}
}

function toPositiveInt(value, fallback) {
	var parsed = Number(value);
	return isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : fallback;
}

function getPackageName() {
	try {
		if (plus.android && typeof plus.android.runtimeMainActivity === 'function') {
			var activity = plus.android.runtimeMainActivity();
			var value = plus.android.invoke(activity, 'getPackageName');
			if (value) return String(value).trim();
		}
	} catch (e) {}
	return '';
}

function readInstalledInfo() {
	return new Promise(function (resolve) {
		var fallback = {
			appId: FALLBACK_APP_ID,
			packageName: isAndroidApp() ? getPackageName() : '',
			channel: OFFICIAL_CHANNEL,
			versionName: FALLBACK_VERSION_NAME,
			versionCode: FALLBACK_VERSION_CODE
		};
		if (!isAndroidApp() || !plus.runtime || typeof plus.runtime.getProperty !== 'function') {
			resolve(fallback);
			return;
		}
		var settled = false;
		var finish = function (property) {
			if (settled) return;
			settled = true;
			var runtimeVersionName = String((property && property.version) || plus.runtime.version || fallback.versionName).trim();
			var runtimeVersionCode = toPositiveInt(
				(property && (property.versionCode || property.versioncode)) || plus.runtime.versionCode,
				fallback.versionCode
			);
			resolve({
				appId: String(plus.runtime.appid || fallback.appId).trim(),
				packageName: getPackageName() || fallback.packageName,
				channel: OFFICIAL_CHANNEL,
				versionName: runtimeVersionName || fallback.versionName,
				versionCode: runtimeVersionCode
			});
		};
		var timeout = setTimeout(function () { finish(null); }, 1500);
		try {
			plus.runtime.getProperty(plus.runtime.appid, function (property) {
				clearTimeout(timeout);
				finish(property || null);
			});
		} catch (e) {
			clearTimeout(timeout);
			finish(null);
		}
	});
}

function apiOrigin() {
	var origin = String(api.jgApiBase || '').trim().replace(/\/+$/, '');
	if (origin) return origin;
	var legacy = String(api.path || '').trim().replace(/\/+$/, '');
	return legacy.replace(/\/api$/i, '');
}

function requestUpdate(info) {
	return new Promise(function (resolve, reject) {
		var origin = apiOrigin();
		if (!origin || !info.packageName) {
			reject(new Error('安装包信息不完整'));
			return;
		}
		uni.request({
			url: origin + '/api/v1/app/update/check',
			method: 'POST',
			timeout: 12000,
			header: { 'Content-Type': 'application/json' },
			data: info,
			success: function (res) {
				if (res && res.statusCode >= 200 && res.statusCode < 300 && res.data && Number(res.data.code) === 1) {
					resolve(res.data.data || { hasUpdate: false });
					return;
				}
				reject(new Error((res && res.data && res.data.msg) || '检查更新失败'));
			},
			fail: function (error) { reject(error || new Error('检查更新失败')); }
		});
	});
}

function isHttpsUrl(value) {
	return /^https:\/\/[^\s]+$/i.test(String(value || '').trim());
}

function normalizeRelease(raw) {
	if (!raw || raw.hasUpdate !== true) return null;
	var versionCode = toPositiveInt(raw.versionCode != null ? raw.versionCode : raw.version_code, 0);
	var policyRevision = Math.max(1, toPositiveInt(raw.policyRevision != null ? raw.policyRevision : raw.policy_revision, 1));
	var downloadUrl = String(raw.downloadUrl || raw.download_url || '').trim();
	if (!versionCode || !isHttpsUrl(downloadUrl)) return null;
	return {
		hasUpdate: true,
		force: raw.force === true || String(raw.updateMode || raw.update_mode || '').toUpperCase() === 'FORCE',
		updateMode: raw.force === true || String(raw.updateMode || raw.update_mode || '').toUpperCase() === 'FORCE' ? 'FORCE' : 'NORMAL',
		versionName: String(raw.versionName || raw.version_name || '').trim(),
		versionCode: versionCode,
		minSupportedVersionCode: toPositiveInt(raw.minSupportedVersionCode != null ? raw.minSupportedVersionCode : raw.min_supported_version_code, 0),
		policyRevision: policyRevision,
		title: String(raw.title || '').trim(),
		changelog: String(raw.changelog || '').trim(),
		downloadUrl: downloadUrl,
		remindLaterHours: Math.max(1, Math.min(168, toPositiveInt(raw.remindLaterHours != null ? raw.remindLaterHours : raw.remind_later_hours, 6))),
		apkSizeBytes: toPositiveInt(raw.apkSizeBytes != null ? raw.apkSizeBytes : raw.apk_size_bytes, 0),
		apkSha256: String(raw.apkSha256 || raw.apk_sha256 || '').trim().toLowerCase(),
		channel: String(raw.channel || OFFICIAL_CHANNEL).trim()
	};
}

function releaseKey(release) {
	return String(release.versionCode) + ':' + String(release.policyRevision);
}

function shouldPresent(release, manual) {
	if (!release || release.force || manual) return !!release;
	var state = safeGetStorage(STATE_KEY, {});
	var key = releaseKey(release);
	if (state.ignoredKey === key) return false;
	if (state.laterKey === key && toPositiveInt(state.laterUntil, 0) > Date.now()) return false;
	return true;
}

function currentPageRoute() {
	try {
		var pages = getCurrentPages();
		var page = pages && pages.length ? pages[pages.length - 1] : null;
		return String((page && (page.route || (page.$page && page.$page.fullPath))) || '');
	} catch (e) {
		return '';
	}
}

function openUpdatePage(release) {
	if (!release || routeOpening) return;
	safeSetStorage(PENDING_KEY, release);
	if (currentPageRoute().indexOf('pages/system/app-update') >= 0) return;
	routeOpening = true;
	var done = function () { setTimeout(function () { routeOpening = false; }, 800); };
	uni.navigateTo({
		url: UPDATE_PAGE,
		success: done,
		fail: function () {
			uni.redirectTo({
				url: UPDATE_PAGE,
				success: done,
				fail: function () {
					uni.reLaunch({ url: UPDATE_PAGE, complete: done });
				}
			});
		}
	});
}

function persistCheckResult(success) {
	var state = safeGetStorage(STATE_KEY, {});
	if (success) {
		state.lastSuccessAt = Date.now();
		state.lastFailureAt = 0;
	} else {
		state.lastFailureAt = Date.now();
	}
	safeSetStorage(STATE_KEY, state);
}

function runCheck(options) {
	options = options || {};
	var manual = options.manual === true;
	if (!isAndroidApp()) return Promise.resolve({ supported: false, hasUpdate: false });
	if (inFlight) {
		if (!manual || inFlightManual) return inFlight;
		return inFlight.then(function (result) {
			if (result && result.release) {
				openUpdatePage(result.release);
			} else {
				uni.showToast({ title: '当前已是最新版本', icon: 'none' });
			}
			return result;
		}, function () {
			return runCheck({ manual: true });
		});
	}
	if (!manual) {
		var state = safeGetStorage(STATE_KEY, {});
		var now = Date.now();
		if (toPositiveInt(state.lastSuccessAt, 0) && now - state.lastSuccessAt < CHECK_SUCCESS_INTERVAL) {
			return Promise.resolve({ skipped: true, hasUpdate: false });
		}
		if (toPositiveInt(state.lastFailureAt, 0) && now - state.lastFailureAt < CHECK_FAILURE_INTERVAL) {
			return Promise.resolve({ skipped: true, hasUpdate: false });
		}
	}

	inFlightManual = manual;
	inFlight = readInstalledInfo().then(function (installed) {
		return requestUpdate(installed).then(function (raw) {
			persistCheckResult(true);
			var release = normalizeRelease(raw);
			if (!release || release.versionCode <= installed.versionCode) {
				if (manual) uni.showToast({ title: '当前已是最新版本', icon: 'none' });
				return { hasUpdate: false, installed: installed };
			}
			if (shouldPresent(release, manual)) openUpdatePage(release);
			return { hasUpdate: true, release: release, installed: installed };
		});
	}).catch(function (error) {
		persistCheckResult(false);
		if (manual) uni.showToast({ title: '检查失败，请稍后重试', icon: 'none' });
		throw error;
	}).finally(function () {
		inFlight = null;
		inFlightManual = false;
	});
	return inFlight;
}

function onLaunch() {
	if (!isAndroidApp() || launchTimer) return;
	launchTimer = setTimeout(function () {
		launchTimer = null;
		runCheck({ manual: false }).catch(function () {});
	}, LAUNCH_DELAY);
}

function onShow() {
	if (!isAndroidApp() || launchTimer) return;
	runCheck({ manual: false }).catch(function () {});
}

function checkNow() {
	return runCheck({ manual: true });
}

function handleHttp426(response) {
	if (!isAndroidApp() || !response || Number(response.statusCode) !== 426) return false;
	var body = response.data && response.data.data ? response.data.data : response.data;
	if (!body || typeof body !== 'object') return false;
	var release = normalizeRelease(Object.assign({}, body, { hasUpdate: true }));
	if (!release || release.force !== true) return false;
	readInstalledInfo().then(function (installed) {
		if (release.versionCode > installed.versionCode) openUpdatePage(release);
	}).catch(function () {});
	return true;
}

function getPendingUpdate() {
	return normalizeRelease(safeGetStorage(PENDING_KEY, null));
}

function remindLater(release) {
	if (!release || release.force) return;
	var state = safeGetStorage(STATE_KEY, {});
	state.laterKey = releaseKey(release);
	state.laterUntil = Date.now() + release.remindLaterHours * 60 * 60 * 1000;
	safeSetStorage(STATE_KEY, state);
	safeRemoveStorage(PENDING_KEY);
}

function ignoreRelease(release) {
	if (!release || release.force) return;
	var state = safeGetStorage(STATE_KEY, {});
	state.ignoredKey = releaseKey(release);
	state.laterKey = '';
	state.laterUntil = 0;
	safeSetStorage(STATE_KEY, state);
	safeRemoveStorage(PENDING_KEY);
}

function openDownload(release) {
	var url = release && String(release.downloadUrl || '').trim();
	if (!isAndroidApp() || !isHttpsUrl(url) || !plus.runtime || typeof plus.runtime.openURL !== 'function') {
		uni.showToast({ title: '下载地址不可用', icon: 'none' });
		return false;
	}
	try {
		plus.runtime.openURL(url, function () {
			uni.showToast({ title: '无法打开下载页面', icon: 'none' });
		});
		return true;
	} catch (e) {
		uni.showToast({ title: '无法打开下载页面', icon: 'none' });
		return false;
	}
}

module.exports = {
	isAndroidApp: isAndroidApp,
	readInstalledInfo: readInstalledInfo,
	onLaunch: onLaunch,
	onShow: onShow,
	checkNow: checkNow,
	handleHttp426: handleHttp426,
	getPendingUpdate: getPendingUpdate,
	remindLater: remindLater,
	ignoreRelease: ignoreRelease,
	openDownload: openDownload,
	isHttpsUrl: isHttpsUrl,
	normalizeRelease: normalizeRelease,
	releaseKey: releaseKey
};
