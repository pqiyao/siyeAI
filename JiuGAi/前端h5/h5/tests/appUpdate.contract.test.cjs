const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'common/appUpdate.js'), 'utf8');
const appVue = fs.readFileSync(path.join(root, 'App.vue'), 'utf8');
const updatePage = fs.readFileSync(path.join(root, 'pages/system/app-update.vue'), 'utf8');
const aboutPage = fs.readFileSync(path.join(root, 'pages/user/aboutmy.vue'), 'utf8');
const pagesJson = fs.readFileSync(path.join(root, 'pages.json'), 'utf8');

assert.match(source, /CHECK_SUCCESS_INTERVAL\s*=\s*6\s*\*\s*60\s*\*\s*60\s*\*\s*1000/);
assert.match(source, /CHECK_FAILURE_INTERVAL\s*=\s*2\s*\*\s*60\s*\*\s*1000/);
assert.match(source, /LAUNCH_DELAY\s*=\s*1400/);
assert.match(source, /plus\.runtime\.getProperty/);
assert.match(source, /plus\.runtime\.openURL/);
assert.match(appVue, /appUpdate\.onLaunch\(\)/);
assert.match(appVue, /appUpdate\.onShow\(\)/);
assert.match(updatePage, /v-if="!release\.force"/);
assert.match(updatePage, /onBackPress\(\)/);
assert.match(updatePage, /return this\.release\.force === true/);
assert.match(updatePage, /login\.png/);
assert.match(updatePage, /border-radius: 40rpx/);
assert.match(aboutPage, /appUpdate\.checkNow\(\)/);
assert.match(aboutPage, /appUpdate\.readInstalledInfo\(\)/);
assert.match(pagesJson, /pages\/system\/app-update/);

function loadModule({ android = true } = {}) {
  const storage = new Map();
  const navigation = [];
  const requests = [];
  const toasts = [];
  const sandbox = {
    module: { exports: {} },
    exports: {},
    require(request) {
      if (request === './api.js') return { jgApiBase: 'https://api.example.com', path: 'https://api.example.com/api/' };
      throw new Error(`unexpected require: ${request}`);
    },
    console,
    Promise,
    Date,
    setTimeout,
    clearTimeout,
    isFinite,
    getCurrentPages: () => [],
    uni: {
      getStorageSync: key => storage.get(key),
      setStorageSync: (key, value) => storage.set(key, value),
      removeStorageSync: key => storage.delete(key),
      showToast(options) { toasts.push(options); },
      request(options) { requests.push(options); },
      navigateTo(options) { navigation.push(['navigateTo', options.url]); if (options.success) options.success(); },
      redirectTo(options) { navigation.push(['redirectTo', options.url]); if (options.success) options.success(); },
      reLaunch(options) { navigation.push(['reLaunch', options.url]); if (options.complete) options.complete(); }
    },
    plus: android ? {
      os: { name: 'Android' },
      runtime: {
        appid: '__UNI__200F612', version: '1.3.6', versionCode: 101,
        getProperty(appId, callback) { callback({ version: '1.3.6', versionCode: 101 }); },
        openURL() {}
      },
      android: {
        runtimeMainActivity() { return {}; },
        invoke(target, method) { return method === 'getPackageName' ? 'com.example.app' : ''; }
      }
    } : undefined
  };
  vm.runInNewContext(source, sandbox, { filename: 'appUpdate.js' });
  return { api: sandbox.module.exports, storage, navigation, requests, toasts };
}

(async () => {
  const loaded = loadModule();
  assert.strictEqual(loaded.api.isAndroidApp(), true);
  const installed = await loaded.api.readInstalledInfo();
  assert.deepStrictEqual(JSON.parse(JSON.stringify(installed)), {
    appId: '__UNI__200F612', packageName: 'com.example.app', channel: 'official', versionName: '1.3.6', versionCode: 101
  });

  assert.strictEqual(loaded.api.isHttpsUrl('https://download.example/app.apk'), true);
  assert.strictEqual(loaded.api.isHttpsUrl('http://download.example/app.apk'), false);
  assert.strictEqual(loaded.api.normalizeRelease({ hasUpdate: true, versionCode: 102, downloadUrl: 'http://bad' }), null);

  const release = loaded.api.normalizeRelease({
    hasUpdate: true, versionCode: 102, versionName: '1.3.7', policyRevision: 2,
    updateMode: 'FORCE', downloadUrl: 'https://download.example/app.apk'
  });
  assert.strictEqual(release.force, true);
  assert.strictEqual(loaded.api.releaseKey(release), '102:2');

  const check = loaded.api.checkNow();
  await Promise.resolve();
  await Promise.resolve();
  assert.strictEqual(loaded.requests.length, 1);
  assert.strictEqual(loaded.requests[0].data.versionCode, 101);
  assert.strictEqual(loaded.requests[0].data.packageName, 'com.example.app');
  loaded.requests[0].success({ statusCode: 200, data: { code: 1, data: { hasUpdate: false } } });
  assert.strictEqual((await check).hasUpdate, false);

  loaded.api.ignoreRelease({ ...release, force: false });
  const state = loaded.storage.get('app_android_update_state_v1');
  assert.strictEqual(state.ignoredKey, '102:2');

  assert.strictEqual(loaded.api.handleHttp426({ statusCode: 426, data: { force: true, versionCode: 101, downloadUrl: 'https://download.example/app.apk' } }), true);
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.strictEqual(loaded.navigation.length, 0, 'same-version 426 must not open update page');

  loaded.api.handleHttp426({ statusCode: 426, data: { force: true, versionCode: 103, downloadUrl: 'https://download.example/app.apk' } });
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.strictEqual(loaded.navigation[0][1], '/pages/system/app-update');

  const snakeCase = loaded.api.normalizeRelease({ hasUpdate: true, force: true, version_code: 104, policy_revision: 3, download_url: 'https://download.example/app.apk' });
  assert.strictEqual(snakeCase.versionCode, 104);
  assert.strictEqual(snakeCase.policyRevision, 3);

  const concurrent = loadModule();
  concurrent.api.onShow();
  await Promise.resolve();
  await Promise.resolve();
  const manualDuringAuto = concurrent.api.checkNow();
  assert.strictEqual(concurrent.requests.length, 1, 'manual check should reuse an active automatic request');
  concurrent.requests[0].success({ statusCode: 200, data: { code: 1, data: { hasUpdate: false } } });
  await manualDuringAuto;
  assert.strictEqual(concurrent.toasts.at(-1).title, '当前已是最新版本');

  const h5 = loadModule({ android: false });
  assert.strictEqual(h5.api.isAndroidApp(), false);
  assert.deepStrictEqual(JSON.parse(JSON.stringify(await h5.api.checkNow())), { supported: false, hasUpdate: false });

  console.log('app update contract passed');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
