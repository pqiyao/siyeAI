const assert = require('assert');
const fs = require('fs');
const path = require('path');

function read(relativePath) {
	return fs.readFileSync(path.join(__dirname, '..', relativePath), 'utf8');
}

const main = read('main.js');
assert(!/setInterval[\s\S]{0,120}350/.test(main), 'Live2D must not poll every 350ms');
assert(main.includes("addEventListener('hashchange'"), 'Live2D must refresh from route events');

const api = read('common/tavernApi.js');
assert(api.includes('xhr.timeout = configuredTimeout > 0 ? configuredTimeout : CHAT_GENERATION_TIMEOUT'), 'SSE XHR must have a real timeout');
assert(api.includes('/api/app/auth/h5/password-reset/request'), 'password reset request endpoint must be wired');
assert(api.includes('/api/app/auth/h5/password-reset/confirm'), 'password reset confirm endpoint must be wired');
assert(api.includes('viewerIdentity.buildViewerIdentitySignature'), 'viewer identity signature must use the tested pure builder');
const identitySignatureSource = api.slice(api.indexOf('function getViewerIdentitySignature()'), api.indexOf('function captureRequestSession()'));
assert(!identitySignatureSource.includes("'|token:'"), 'viewer identity signature must not expose auth tokens');
assert(/!forceRefresh\s*&&[\s\S]{0,120}runtimeFeatureConfigFetchedAt > 0[\s\S]{0,160}RUNTIME_FEATURE_CONFIG_CACHE_MS/.test(api), 'runtime feature config cache must expire instead of trusting stored data forever');
assert(api.includes('illustrationEntryEnabled: raw.illustrationEntryEnabled !== false'), 'illustration entry must default to visible for backward compatibility');

const home = read('pages/index/index.vue');
assert(home.includes('v-if="isJgDiscover && illustrationEntryEnabled"'), 'home illustration entry must obey the runtime switch');
assert(/onShow\(\)[\s\S]{0,500}syncIllustrationEntryVisibility\(true\)/.test(home), 'home must refresh the illustration entry switch when shown');

const userCenter = read('pages/user/user.vue');
assert(userCenter.includes('v-if="illustrationEntryEnabled" class="illustration-site-card"'), 'user-center illustration entry must obey the runtime switch');
assert(/onShow\(\)[\s\S]{0,350}syncIllustrationEntryVisibility\(true\)/.test(userCenter), 'user center must refresh the illustration entry switch when shown');

const forget = read('pages/login/forget.vue');
assert(!forget.includes('index/forgetPwd') && !forget.includes('/ems/send'), 'legacy missing password-reset endpoints must not be used');
assert(forget.includes('onUnload()') && forget.includes('clearInterval'), 'password reset countdown must be disposed');

const chat = read('pages/tavern/tavernChat.vue');
assert(/onUnload\(\)[\s\S]{0,300}stopGeneration\(\{ silent: true, skipSync: true \}\)/.test(chat), 'chat unload must abort and stop generation');
assert(chat.includes('this.jgViewerIdentitySignature = tavernApi.getViewerIdentitySignature();'), 'chat onLoad must capture viewer identity');
assert(/onShow\(\)[\s\S]{0,700}handleJgIdentityChangeOnShow/.test(chat), 'chat onShow must detect guest/login/account/logout identity changes');
assert(chat.includes('viewerIdentity.shouldReloadViewerIdentity(this.jgViewerIdentitySignature, currentIdentity)'), 'chat identity reload decisions must use the tested pure predicate');
assert(/reloadJgSessionForIdentity\(identitySignature\)[\s\S]{0,2600}this\.messages = \[\][\s\S]{0,1800}identityReload: true/.test(chat), 'identity changes must atomically clear old chat runtime state and reload');
assert(chat.includes(":disabled=\"sending || jgIdentityReloading || jgChatLoadState !== 'ready'\""), 'composer must remain disabled while identity session reloads');
assert(/submitOutgoingMessage\(rawText, rawImageUrls, options\)[\s\S]{0,180}ensureJgIdentityReadyForAction/.test(chat), 'send must reject stale-identity UI state before optimistic mutation');
assert(chat.includes('isJgRuntimeRequestCurrent(runtimeRequestVersion, runtimeIdentitySignature)'), 'old generation callbacks must be invalidated after identity changes');
const regenStart = chat.indexOf('\t\t\tonRegen() {');
const continueStart = chat.indexOf('\t\t\tonContinue() {');
const restartStart = chat.indexOf('\t\t\tonRestart() {');
const outgoingStart = chat.indexOf('\t\t\tsubmitOutgoingMessage(rawText, rawImageUrls, options) {');
const regenSource = chat.slice(regenStart, continueStart);
const continueSource = chat.slice(continueStart, restartStart);
const restartSource = chat.slice(restartStart, outgoingStart);
assert((regenSource.match(/isJgRuntimeRequestCurrent\(runtimeRequestVersion, runtimeIdentitySignature\)/g) || []).length >= 7, 'regenerate callbacks must reject stale identities');
assert((continueSource.match(/isJgRuntimeRequestCurrent\(runtimeRequestVersion, runtimeIdentitySignature\)/g) || []).length >= 7, 'continue callbacks must reject stale identities');
assert((restartSource.match(/isJgRuntimeRequestCurrent\(runtimeRequestVersion, runtimeIdentitySignature\)/g) || []).length >= 4, 'restart callbacks must reject stale identities');
assert(/message === 'vip'[\s\S]{0,120}jgIdentityReloading = false/.test(chat), 'VIP gate must release identity reload state');
assert(chat.includes('finalizeAssistantStreamRequest(streamController)'), 'stream completion must always release its owned reply state');
assert(/onShow\(\)[\s\S]{0,900}refreshVoiceFeatureGlobalState\(true\)/.test(chat), 'chat onShow must refresh voice and image feature switches from the backend');
assert(/onShow\(\)[\s\S]{0,900}refreshCharacterImageGlobalSummary\(true, false\)/.test(chat), 'chat onShow must refresh the user image-generation entitlement summary');
assert(/finalizeAssistantStreamRequest\(controller\)\s*\{\s*if \(!controller \|\| this\.streamAbortController !== controller\) return;[\s\S]*?this\.finishAssistantStreaming\(\);[\s\S]*?this\.finishSendingState\(\);/.test(chat), 'stale stream requests must not clear the state owned by a newer request');
const outgoingStreamFinally = chat.slice(chat.indexOf('postTavernChatStream('), chat.indexOf('postTavernChatStream(') + 5200);
assert(!/\.finally\(\(\) => \{\s*if \(!this\.isJgRuntimeRequestCurrent/.test(outgoingStreamFinally), 'stream cleanup must not be skipped by stale-identity guards');
const stopGenerationSource = chat.slice(chat.indexOf('stopGeneration(options)'), chat.indexOf('openBranchPanel()'));
assert(/const streamController = this\.streamAbortController;[\s\S]*?this\.streamAbortController = null;[\s\S]*?streamController\.abort\(\);[\s\S]*?this\.finishAssistantStreaming\(streamingMessageId\);[\s\S]*?this\.finishSendingState\(\);/.test(stopGenerationSource), 'manual stream stop must release reply state before the old request finally settles');

const composer = read('components/tavern/chat-composer.vue');
assert(/attachmentMenuVisible[\s\S]{0,1200}v-if="showVoiceAction"[\s\S]{0,1200}v-if="showImageGenerationAction"/.test(composer), 'voice and image generation actions must be available in the attachment menu');
const inputActionsSource = composer.slice(composer.indexOf('<view class="input-actions">'), composer.indexOf('</template>'));
assert(!inputActionsSource.includes('showVoiceAction') && !inputActionsSource.includes('showImageGenerationAction'), 'voice and image generation actions must stay inside the plus menu');
const appAttachmentStyles = composer.slice(composer.indexOf('.wrap--app-plus .chat-composer .attach-fab-menu'), composer.indexOf('/* #endif */'));
assert(/right:\s*18rpx;[\s\S]{0,220}max-width:\s*calc\(100vw - 132rpx\);[\s\S]{0,100}gap:\s*12rpx;/.test(appAttachmentStyles), 'APP attachment menu spacing must stay aligned with H5');
assert(/attach-fab-badge[\s\S]{0,100}width:\s*86rpx;[\s\S]{0,80}height:\s*86rpx;/.test(appAttachmentStyles), 'APP attachment action size must stay aligned with H5');

const chatPersona = read('pages/tavern/chatPersona.vue');
assert(chatPersona.includes('<scroll-view class="body-scroll" scroll-y>'), 'chat persona content must use a vertical scroll container');
assert(/\.body-scroll\s*\{[\s\S]{0,180}flex:\s*1;[\s\S]{0,100}min-height:\s*0;/.test(chatPersona), 'chat persona scroll container must fill the remaining viewport');

const characterDetail = read('pages/tavern/charDetail.vue');
assert(characterDetail.includes('this.char && this.char.public_profile'), 'character detail must consume the public profile returned by the API');
assert(characterDetail.includes('this.publicProfile.openingPreview'), 'character detail must show the real opening preview');
assert(characterDetail.includes('this.publicProfile.relationshipHook'), 'character detail must show the extracted relationship hook');
assert(!characterDetail.includes('if (this.char.gameplay_type) list.push({ label: this.detailPatchText.teaserGameplay'), 'gameplay type must not masquerade as a relationship');

const util = read('common/util.js');
const uploadStart = util.indexOf('function uploadFile(img)');
const uploadEnd = util.indexOf('\n/*', uploadStart + 1);
const upload = util.slice(uploadStart, uploadEnd > uploadStart ? uploadEnd : undefined);
assert(upload.includes('fail:'), 'upload must reject transport failures');
assert(upload.includes('complete:'), 'upload must always close loading');
assert(upload.includes('reject('), 'upload failures must settle the promise');

for (const file of [
	'common/chatAppearanceI18n.js',
	'common/util.js',
	'pages/login/forget.vue',
	'pages/user/editziliao.vue',
	'components/tavern/message-content.vue'
]) {
	const source = read(file);
	for (const locale of ['zh-cn', 'zh-hk', 'en', 'ko', 'ja']) {
		assert(source.includes(locale), `${file} must include ${locale}`);
	}
}

console.log('frontend reliability contract passed');
