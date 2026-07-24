const assert = require('assert');
const path = require('path');
const Module = require('module');

const projectRoot = path.resolve(__dirname, '..');
const resolveFilename = Module._resolveFilename;

Module._resolveFilename = function resolveProjectAlias(request, parent, isMain, options) {
	const resolvedRequest = request.startsWith('@/')
		? path.join(projectRoot, request.slice(2))
		: request;
	return resolveFilename.call(this, resolvedRequest, parent, isMain, options);
};

const storage = new Map();
global.getApp = () => ({ globalData: {} });
global.uni = {
	getStorageSync(key) {
		return storage.get(key);
	},
	setStorageSync(key, value) {
		storage.set(key, value);
	},
	removeStorageSync(key) {
		storage.delete(key);
	}
};

const tavernApi = require(path.join(projectRoot, 'common', 'tavernApi.js'));

function setAuth(user, legacyToken) {
	storage.clear();
	if (user !== undefined) storage.set('user', user);
	if (legacyToken !== undefined) storage.set('token', legacyToken);
}

setAuth({ id: 101, token: ' current-user-token ' }, 'stale-token');
assert.strictEqual(tavernApi.getStoredAuthToken(), 'current-user-token');
assert.strictEqual(tavernApi.hasLoggedInUser(), true);

setAuth({ userId: 202 }, ' legacy-user-token ');
assert.strictEqual(tavernApi.getStoredAuthToken(), 'legacy-user-token');
assert.strictEqual(tavernApi.hasLoggedInUser(), true);
assert.strictEqual(tavernApi.getViewerStateSignature(), 'user:202|token:legacy-user-token');

setAuth({}, 'guest-must-not-inherit-this');
assert.strictEqual(tavernApi.getStoredAuthToken(), '');
assert.strictEqual(tavernApi.hasLoggedInUser(), false);

setAuth(undefined, 'guest-must-not-inherit-this');
assert.strictEqual(tavernApi.getStoredAuthToken(), '');
assert.strictEqual(tavernApi.hasLoggedInUser(), false);

setAuth({ id: '   ', token: '' }, 'guest-must-not-inherit-this');
assert.strictEqual(tavernApi.getStoredAuthToken(), '');
assert.strictEqual(tavernApi.hasLoggedInUser(), false);

for (const invalidUserId of [0, -1, 'guest']) {
	setAuth({ id: invalidUserId }, 'guest-must-not-inherit-this');
	assert.strictEqual(tavernApi.getStoredAuthToken(), '');
	assert.strictEqual(tavernApi.hasLoggedInUser(), false);
}

storage.set('tavern_runtime_feature_config', { illustrationEntryEnabled: false });
assert.strictEqual(tavernApi.isIllustrationEntryEnabled(), false);
storage.set('tavern_runtime_feature_config', {});
assert.strictEqual(tavernApi.isIllustrationEntryEnabled(), true);
assert.strictEqual(tavernApi.isRechargeEntryVisible(), true);
storage.set('tavern_runtime_feature_config', { rechargeEntryVisible: false });
assert.strictEqual(tavernApi.isRechargeEntryVisible(), false);

console.log('auth storage contract passed');
