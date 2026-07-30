const assert = require('assert');
const fs = require('fs');
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
let uniRequestHandler = null;
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
	},
	request(options) {
		if (!uniRequestHandler) throw new Error('Unexpected uni.request');
		return uniRequestHandler(options);
	},
	$emit() {}
};

const tavernApi = require(path.join(projectRoot, 'common', 'tavernApi.js'));
const appearance = require(path.join(projectRoot, 'common', 'chatAppearance.js'));
const chatMarkdown = require(path.join(projectRoot, 'common', 'chatMarkdown.js'));
const structuredContent = require(path.join(projectRoot, 'common', 'chatStructuredContent.js'));
const chatSource = fs.readFileSync(path.join(projectRoot, 'pages', 'tavern', 'tavernChat.vue'), 'utf8');
const settingSource = fs.readFileSync(path.join(projectRoot, 'pages', 'user', 'chatAppearanceSetting.vue'), 'utf8');
const bubbleSource = fs.readFileSync(path.join(projectRoot, 'components', 'tavern', 'message-bubble.vue'), 'utf8');
const messageComponentSources = [
	'message-actions.vue',
	'message-content.vue',
	'message-swipe-controls.vue',
	'assistant-voice-pill.vue',
	'voice-message-card.vue'
].map((name) => fs.readFileSync(path.join(projectRoot, 'components', 'tavern', name), 'utf8'));
const appearanceI18n = require(path.join(projectRoot, 'common', 'chatAppearanceI18n.js'));
const presets = appearance.PRESETS.filter((item) => !item.system);
const presetBackgrounds = new Set();
const defaultBubble = appearance.buildBubbleStyleObject({ role: 'assistant' }, appearance.DEFAULT_CONFIG);

function hexRgb(hex) {
	const value = String(hex || '').replace('#', '');
	return [0, 2, 4].map((offset) => parseInt(value.slice(offset, offset + 2), 16));
}

function luminance(rgb) {
	const channels = rgb.map((value) => {
		const channel = value / 255;
		return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
	});
	return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrastOnWhiteBackdrop(textHex, bubbleHex, opacity, backdropStrength = 0) {
	const backdrop = Math.round(255 * (1 - backdropStrength));
	const surface = hexRgb(bubbleHex).map((value) => Math.round(value * opacity + backdrop * (1 - opacity)));
	const textLuminance = luminance(hexRgb(textHex));
	const surfaceLuminance = luminance(surface);
	return (Math.max(textLuminance, surfaceLuminance) + 0.05) / (Math.min(textLuminance, surfaceLuminance) + 0.05);
}

assert.strictEqual(appearance.DEFAULT_CONFIG.schemaVersion, 3);
assert.strictEqual(appearance.DEFAULT_CONFIG.presetVersion, 4);
assert.strictEqual(appearance.DEFAULT_CONFIG.preset, 'system');
assert.strictEqual(appearance.DEFAULT_CONFIG.bubbleCustomized, false);
assert.strictEqual(appearance.DEFAULT_CONFIG.fontSize, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.fontSize);
assert.strictEqual(appearance.DEFAULT_CONFIG.lineHeight, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.lineHeight);
assert.strictEqual(appearance.DEFAULT_CONFIG.baseFontWeight, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.baseFontWeight);
assert.strictEqual(appearance.DEFAULT_CONFIG.userFontWeight, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.userFontWeight);
assert.strictEqual(appearance.DEFAULT_CONFIG.speechFontWeight, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.speechFontWeight);
assert.strictEqual(appearance.DEFAULT_CONFIG.opacity, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.opacity);
assert.strictEqual(appearance.DEFAULT_CONFIG.surfaceMode, 'legacyGlass');
assert.strictEqual(appearance.DEFAULT_CONFIG.blurRadius, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.blurRadius);
assert.strictEqual(appearance.DEFAULT_CONFIG.charBubbleColor, '#20222a');
assert.strictEqual(appearance.DEFAULT_CONFIG.userBubbleColor, '#264148');
assert.strictEqual(appearance.DEFAULT_CONFIG.speechColor, '#f4b8cf');
assert.strictEqual(appearance.DEFAULT_CONFIG.actionColor, '#bfe8d2');
assert.strictEqual(appearance.DEFAULT_CONFIG.thoughtColor, '#d4caef');
assert.strictEqual(appearance.DEFAULT_CONFIG.narrationColor, '#f2f4f7');
assert.strictEqual(new Set([
	appearance.DEFAULT_CONFIG.speechColor,
	appearance.DEFAULT_CONFIG.actionColor,
	appearance.DEFAULT_CONFIG.thoughtColor,
	appearance.DEFAULT_CONFIG.narrationColor
]).size, 4, 'semantic text categories must retain distinct colors');
assert.notStrictEqual(appearance.DEFAULT_CONFIG.speechColor, appearance.DEFAULT_CONFIG.narrationColor);
assert.notStrictEqual(appearance.DEFAULT_CONFIG.actionColor, appearance.DEFAULT_CONFIG.thoughtColor);
const fengyueForContrast = appearance.applyPreset(appearance.DEFAULT_CONFIG, 'fengyue');
for (const color of [
	fengyueForContrast.baseTextColor,
	fengyueForContrast.speechColor,
	fengyueForContrast.actionColor,
	fengyueForContrast.thoughtColor,
	fengyueForContrast.narrationColor
]) {
	assert(
		contrastOnWhiteBackdrop(color, fengyueForContrast.charBubbleColor, fengyueForContrast.opacity / 100, fengyueForContrast.backdropStrength / 100) >= 4.5,
		`the optional Fengyue preset must stay readable over a bright character background: ${color}`
	);
}
assert(
	contrastOnWhiteBackdrop(fengyueForContrast.userTextColor, fengyueForContrast.userBubbleColor, fengyueForContrast.opacity / 100, fengyueForContrast.backdropStrength / 100) >= 4.5,
	'the optional Fengyue user text must stay readable over a bright character background'
);
assert.strictEqual(defaultBubble['max-width'], `${appearance.DEFAULT_CONFIG.charMaxWidth}%`);
assert(defaultBubble.background.startsWith('linear-gradient('), 'the original baseline must retain its glass gradient');
assert.strictEqual(defaultBubble['--chat-content-accent'], appearance.DEFAULT_CONFIG.charBorderColor);
const defaultUserBubble = appearance.buildBubbleStyleObject({ role: 'user' }, appearance.DEFAULT_CONFIG);
assert.strictEqual(defaultUserBubble['--chat-content-accent'], appearance.DEFAULT_CONFIG.userBorderColor);
assert.notStrictEqual(defaultBubble.background, defaultUserBubble.background, 'assistant and user surfaces must be visibly role-specific');
const splitFirst = appearance.buildSplitBubbleStyleObject(appearance.DEFAULT_CONFIG, 0, 3);
const splitMiddle = appearance.buildSplitBubbleStyleObject(appearance.DEFAULT_CONFIG, 1, 3);
const splitLast = appearance.buildSplitBubbleStyleObject(appearance.DEFAULT_CONFIG, 2, 3);
assert.strictEqual(splitFirst.width, 'auto', 'segmented bubbles must shrink to their content instead of forcing full width');
assert.notStrictEqual(splitFirst['border-radius'], splitMiddle['border-radius'], 'the first segmented bubble must have a distinct linked corner treatment');
assert.notStrictEqual(splitMiddle['box-shadow'], splitFirst['box-shadow'], 'middle segmented bubbles must use a lighter shadow');
assert.notStrictEqual(splitLast['border-radius'], splitMiddle['border-radius'], 'the last segmented bubble must close the linked corner treatment');
global.window = { innerWidth: 375 };
assert.strictEqual(
	appearance.buildMessageTextStyleObject('assistant', appearance.DEFAULT_CONFIG)['font-size'],
	`${appearance.DEFAULT_CONFIG.fontSize / 2}px`
);
delete global.window;
assert.deepStrictEqual(
	appearance.buildAppBubbleStyleObject({ role: 'assistant' }, appearance.DEFAULT_CONFIG),
	defaultBubble,
	'legacy APP API must remain an alias of the shared renderer'
);

assert(presets.length >= 5, 'Expected multiple visual presets');

for (const preset of presets) {
	const config = appearance.applyPreset(appearance.DEFAULT_CONFIG, preset.code);
	const bubbleStyle = appearance.buildBubbleStyleObject({ role: 'assistant' }, config);
	const textStyle = appearance.buildMessageTextStyleObject('assistant', config);

	assert.strictEqual(bubbleStyle['max-width'], `${config.charMaxWidth}%`);
	assert.strictEqual(textStyle.color, config.baseTextColor);
	assert(!JSON.stringify(bubbleStyle).includes('!important'));
	presetBackgrounds.add(bubbleStyle.background);
}

assert.strictEqual(presetBackgrounds.size, presets.length, 'APP presets must render distinct surfaces');
const fengyue = appearance.applyPreset(appearance.DEFAULT_CONFIG, 'fengyue');
assert.strictEqual(fengyue.preset, 'fengyue');
assert.strictEqual(fengyue.fontSize, 32);
assert.strictEqual(fengyue.baseFontWeight, 500);
assert.strictEqual(fengyue.userFontWeight, 500);
assert.strictEqual(fengyue.speechFontWeight, 600);
assert.strictEqual(fengyue.surfaceMode, 'flat');
assert.strictEqual(fengyue.presetVersion, 4);
const softContentVars = appearance.buildCssVars(appearance.applyPreset(appearance.DEFAULT_CONFIG, 'soft'));
assert.strictEqual(softContentVars['--chat-content-accent'], '#668896', 'light preset must derive a readable assistant accent from its role border');
assert.strictEqual(softContentVars['--chat-user-content-accent'], '#3f8d91', 'light preset must derive a distinct user accent from its role border');
assert(chatSource.includes("'chat-message-bubble--assistant'"), 'shared assistant bubble class must be present');
assert(chatSource.includes('buildBubbleStyleObject(message, this.chatAppearanceConfig)'), 'both runtimes must bind the shared bubble style object');
assert(chatSource.includes('buildMessageTextStyleObject(\'assistant\', this.chatAppearanceConfig)'), 'assistant text must use the shared style object');
assert(chatSource.includes('nativeSegmentTextStyle(seg)'), 'APP segment text settings must bind directly');
assert(chatSource.includes('if (this.isStreamingAssistantRow(message))'), 'streaming replies must stay in one stable bubble until generation completes');
assert(bubbleSource.includes('margin-bottom: 14rpx;'), 'segmented bubble spacing must leave visible breathing room');
assert(bubbleSource.includes('gap: 10rpx;'), 'semantic segments inside a bubble must not be visually cramped');
assert(settingSource.includes('margin-bottom: 14rpx;'), 'appearance preview spacing must match the real chat page');
assert(bubbleSource.includes('class="chat-message-bubble"'), 'bubble component must expose one platform-neutral root');
assert(/\.chat-message-row\s*\{[\s\S]*?display:\s*flex;[\s\S]*?width:\s*100%;[\s\S]*?min-width:\s*0;/.test(chatSource), 'the chat page must own message-row layout because the row is outside the bubble component scope');
assert(/\.chat-message-avatar\s*\{[\s\S]*?min-width:\s*72rpx;[\s\S]*?max-width:\s*72rpx;[\s\S]*?object-fit:\s*cover;/.test(chatSource), 'the chat page must own fixed avatar sizing and cropping');
assert(!bubbleSource.includes('.chat-message-avatar {') && !bubbleSource.includes('.chat-message-row {'), 'bubble component styles must not target parent-owned rows or avatars');
assert(!bubbleSource.includes('!important'), 'shared bubble component must not need priority overrides');
assert(!chatSource.includes('bubble--app-plus'), 'legacy APP bubble selectors must stay removed from the chat page');
assert(!chatSource.includes('.msg-row'), 'legacy message row selectors must stay removed from the chat page');
assert(!settingSource.includes('use-app-runtime-class'), 'settings preview must use the same bubble root as the chat page');
assert(settingSource.includes('buildBubbleStyleObject({ role:'), 'settings preview must use the shared bubble renderer');
for (const source of messageComponentSources) {
	assert(!source.includes('bubble--app-plus'), 'message components must not depend on legacy APP bubble ancestors');
	assert(!source.includes('msg-row--app-plus'), 'message components must not depend on legacy APP row ancestors');
	assert(!source.includes('!important'), 'message components must not restore priority-based platform polish layers');
}
assert.strictEqual((chatSource.match(/<style(?:\s|>)/g) || []).length, 2, 'chat page style layers must remain consolidated');
assert((chatSource.match(/!important/g) || []).length < 100, 'chat page priority overrides must remain below the regression ceiling');
assert(!chatSource.includes('Final APP bubble polish'), 'obsolete APP final override layer must stay removed');

const migratedLegacyCustom = appearance.normalizeConfig({
	customized: true,
	preset: 'custom',
	fontSize: 30
});
assert.strictEqual(migratedLegacyCustom.bubbleCustomized, true);
assert.strictEqual(migratedLegacyCustom.fontSize, 30);
assert.strictEqual(migratedLegacyCustom.baseFontWeight, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.baseFontWeight);
assert.strictEqual(migratedLegacyCustom.surfaceMode, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.surfaceMode);
assert.strictEqual(migratedLegacyCustom.charBubbleColor, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.charBubbleColor);

const upgradedFengyuePreset = appearance.normalizeConfig({
	bubbleCustomized: true,
	customized: true,
	preset: 'fengyue',
	schemaVersion: 3,
	presetVersion: 1,
	fontSize: 27,
	lineHeight: 1.6,
	surfaceMode: 'flat'
});
assert.strictEqual(upgradedFengyuePreset.preset, 'fengyue');
assert.strictEqual(upgradedFengyuePreset.presetVersion, 4);
assert.strictEqual(upgradedFengyuePreset.fontSize, 32);
assert.strictEqual(upgradedFengyuePreset.surfaceMode, 'flat');

const adjustedCurrentFengyue = appearance.normalizeConfig(Object.assign({}, fengyue, { fontSize: 31 }));
assert.strictEqual(adjustedCurrentFengyue.preset, appearance.CUSTOM_PRESET_CODE, 'current-version preset adjustments must remain custom');
assert.strictEqual(adjustedCurrentFengyue.fontSize, 31);

const retiredFengyueDefault = appearance.normalizeConfig({
	bubbleCustomized: true,
	customized: true,
	preset: 'fengyue',
	schemaVersion: 3,
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
});
assert.strictEqual(retiredFengyueDefault.bubbleCustomized, false, 'the retired default must return to system mode');
assert.strictEqual(retiredFengyueDefault.preset, 'system');
assert.strictEqual(retiredFengyueDefault.charBubbleColor, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.charBubbleColor);
assert.strictEqual(retiredFengyueDefault.fontSize, appearance.LEGACY_CLASSIC_BUBBLE_CONFIG.fontSize);

const weightedThought = appearance.normalizeConfig(Object.assign({}, fengyue, {
	preset: 'custom',
	thoughtFontWeight: 500,
	thoughtItalic: true
}));
const weightedThoughtStyle = appearance.buildSegmentTextStyleObject('thought', weightedThought);
assert.strictEqual(weightedThoughtStyle.fontWeight, '500');
assert.strictEqual(weightedThoughtStyle.fontStyle, 'italic');
const embeddedVars = appearance.buildCssVars(weightedThought);
for (const key of ['--chat-content-primary', '--chat-content-muted', '--chat-content-surface', '--chat-content-border', '--chat-content-accent', '--chat-content-danger']) {
	assert(String(embeddedVars[key] || '').trim(), `embedded content token missing: ${key}`);
}

let custom = appearance.applyPreset(appearance.DEFAULT_CONFIG, 'soft');
custom = appearance.setTextColorOverride(custom, 'speechColor', '#123456');

const night = appearance.applyPreset(custom, 'night');
assert.strictEqual(night.speechColor, '#ffc6dc');
assert.deepStrictEqual(night.textColorOverrides, {});

const system = appearance.setBubbleCustomized(night, false);
assert.strictEqual(system.radius, appearance.BASE_BUBBLE_CONFIG.radius);
assert.strictEqual(system.lineHeight, appearance.BASE_BUBBLE_CONFIG.lineHeight);
assert.strictEqual(system.charMaxWidth, appearance.BASE_BUBBLE_CONFIG.charMaxWidth);

custom = appearance.normalizeConfig(Object.assign({}, custom, {
	charBubbleColor: '#abcdef',
	charMaxWidth: 91,
	radius: 31,
	fontSize: 35,
	lineHeight: 2.05,
	preset: appearance.CUSTOM_PRESET_CODE,
	bubbleCustomized: true,
	customized: true
}));

const customBubble = appearance.buildBubbleStyleObject({ role: 'assistant' }, custom);
const customUserBubble = appearance.buildBubbleStyleObject({ role: 'user' }, custom);
const customText = appearance.buildMessageTextStyleObject('assistant', custom);

assert.strictEqual(customBubble['max-width'], '91%');
assert(customBubble.background.includes('171,205,239'));
assert(customUserBubble.background.includes('220,239,240'));
assert.strictEqual(customBubble['border-radius'], '31rpx 31rpx 31rpx 31rpx');
assert.strictEqual(customText['font-size'], '35rpx');
assert.strictEqual(customText['line-height'], '2.05');
assert.strictEqual(appearance.segmentColor('speech', custom), '#123456');

const novel = appearance.normalizeConfig(Object.assign({}, appearance.DEFAULT_CONFIG, { readMode: 'novel' }));
const novelBubble = appearance.buildBubbleStyleObject({ role: 'assistant' }, novel);
const novelText = appearance.buildMessageTextStyleObject('assistant', novel);
assert.strictEqual(novelBubble['max-width'], '90%', 'novel geometry must come from the shared renderer');
assert.strictEqual(novelText['font-size'], '34rpx');
assert.strictEqual(novelText['line-height'], '1.92');
assert(
	chatMarkdown.renderChatMarkdown('测试正文', { segmentColors: appearance.buildSegmentColors(custom) }).includes('line-height:inherit'),
	'H5 markdown segments must inherit the shared configured line height'
);

assert.deepStrictEqual(
	appearance.splitReplyBubbleTexts('今晚月色很好。', 'bubble'),
	['今晚月色很好。'],
	'a short semantic beat must remain one bubble'
);
assert.deepStrictEqual(
	appearance.splitReplyBubbleTexts('她抬起头。\n“欢迎回来。”\n（心里仍有些紧张。）', 'bubble'),
	['她抬起头。\n“欢迎回来。”', '（心里仍有些紧张。）'],
	'adjacent action and dialogue should stay together while an inner thought remains independent'
);
assert.deepStrictEqual(
	appearance.splitReplyBubbleTexts('她推开窗。\n\n“风有些凉。”', 'bubble'),
	['她推开窗。', '“风有些凉。”'],
	'blank lines must remain hard bubble boundaries'
);
assert.deepStrictEqual(
	appearance.splitReplyBubbleTexts('“你来了。”\n“我等了很久。”', 'bubble'),
	['“你来了。”', '“我等了很久。”'],
	'consecutive dialogue lines must remain separate beats'
);
assert.deepStrictEqual(
	appearance.splitReplyBubbleTexts(
		'“第一句话说得很慢，也带着一点迟疑。第二句话继续解释她为什么一直站在门口。第三句话终于说出了真正想问的问题。第四句话则轻轻收住了整段情绪。”',
		'bubble'
	),
	[
		'“第一句话说得很慢，也带着一点迟疑。第二句话继续解释她为什么一直站在门口。”',
		'“第三句话终于说出了真正想问的问题。第四句话则轻轻收住了整段情绪。”'
	],
	'long dialogue must split into balanced bubbles without losing quotation wrappers'
);
const longNarrationChunks = appearance.splitReplyBubbleTexts(
	'雨水顺着屋檐一滴一滴落下，石阶已经被彻底打湿。她站在门边没有动，只是把手里的灯又往前举了一些。远处偶尔传来车轮压过水洼的声音，很快又被夜色吞没。屋里那只旧钟依旧缓慢地走着，像是在替两个人计算这段沉默。',
	'bubble'
);
assert.deepStrictEqual(longNarrationChunks, [
	'雨水顺着屋檐一滴一滴落下，石阶已经被彻底打湿。她站在门边没有动，只是把手里的灯又往前举了一些。',
	'远处偶尔传来车轮压过水洼的声音，很快又被夜色吞没。',
	'屋里那只旧钟依旧缓慢地走着，像是在替两个人计算这段沉默。'
], 'long narration must split into readable semantic beats');
const overEightReply = Array.from({ length: 11 }, (_, index) => `“第${index + 1}句独立对白。”`).join('\n');
const limitedChunks = appearance.splitReplyBubbleTexts(overEightReply, 'bubble');
assert.strictEqual(limitedChunks.length, 8, 'one logical reply must keep the eight-bubble display ceiling');
assert(limitedChunks.slice(0, -1).some((chunk) => chunk.includes('\n\n')), 'overflow compaction must merge the shortest adjacent beats instead of only the tail');
assert(!limitedChunks[limitedChunks.length - 1].includes('第8句'), 'overflow compaction must not dump all remaining beats into the final bubble');
const markdownCodeReply = '说明如下：\n```js\nconst value = 1;\n```\n结束。';
assert.deepStrictEqual(
	appearance.splitReplyBubbleTexts(markdownCodeReply, 'bubble'),
	[markdownCodeReply],
	'Markdown code blocks must remain protected in one bubble'
);
assert.deepStrictEqual(
	appearance.splitReplyBubbleTexts('  普通原文  ', 'none'),
	['  普通原文  '],
	'non-bubble mode must continue preserving the original text'
);
assert(!structuredContent.hasStructuredContent('```html\n<div>代码块</div>\n```'));
assert(!structuredContent.hasStructuredContent('行内代码 `<div>示例</div>`'));
assert(!structuredContent.hasStructuredContent('~~~html\n<div>波浪线代码块</div>\n~~~'));
assert(!structuredContent.hasStructuredContent('行内代码 ``<div>双反引号示例</div>``'));
assert(!structuredContent.hasStructuredContent('转义标签 \\<div>不是状态栏</div>'));

const richPanel = [
	'<details class="status-panel" open>',
	'<summary>关系状态</summary>',
	'<section><strong>爱心进度</strong><progress value="72" max="100">72%</progress></section>',
	'<div>剧情阶段：重逢 <meter min="0" max="10" value="7">7/10</meter></div>',
	'</details>'
].join('\n');
const mixedRichReply = '她轻轻握住你的手。\n\n“欢迎回来。”\n\n' + richPanel + '\n\n夜色重新安静下来。';
const richChunks = appearance.splitReplyBubbleTexts(mixedRichReply, 'bubble');
const panelChunks = richChunks.filter((chunk) => chunk.includes('<details'));
assert.strictEqual(panelChunks.length, 1, 'a complete rich status panel must stay in one bubble');
assert(panelChunks[0].includes('</details>'), 'the status panel closing tag must stay with its opening tag');
assert(richChunks.length <= 8, 'structured replies must keep the existing bubble count ceiling');

const nestedPanel = '<div class="outer"><div class="inner">爱心进度：80%</div><p>剧情阶段：推进</p></div>';
const nestedParts = structuredContent.splitStructuredContent(nestedPanel + '\n结尾正文');
assert.strictEqual(nestedParts[0].type, 'rich');
assert.strictEqual(nestedParts[0].text, nestedPanel, 'nested same-name tags must remain one complete block');
const mixedSegments = chatMarkdown.splitChatSegments(mixedRichReply);
assert(mixedSegments.some((item) => item.type === 'speech'));
assert.strictEqual(mixedSegments.filter((item) => item.type === 'rich').length, 1);
assert(!chatMarkdown.splitChatSegments('普通旁白。“普通台词。”').some((item) => item.type === 'rich'));
assert.strictEqual(appearance.segmentLabel('rich'), '状态');

const genericHtmlReply = '<div class="reply"><p>她抬起头。</p><p>“欢迎回来。”</p></div>';
const genericHtmlParts = structuredContent.splitStructuredContent(genericHtmlReply);
assert.strictEqual(genericHtmlParts.length, 1);
assert.strictEqual(genericHtmlParts[0].type, 'rich');
assert.strictEqual(genericHtmlParts[0].kind, 'content', 'generic HTML wrappers must not be mislabeled as status panels');
assert.deepStrictEqual(
	chatMarkdown.splitChatSegments(genericHtmlReply).map((item) => item.type),
	['rich-content']
);
assert.deepStrictEqual(
	chatMarkdown.extractChatSpeechSegments(genericHtmlReply).map((item) => item.text),
	['“欢迎回来。”'],
	'TTS must recover dialogue from generic HTML content'
);
assert.strictEqual(
	chatMarkdown.extractChatSpeechSegments(richPanel).length,
	0,
	'status panels must remain excluded from TTS'
);
const mixedHtmlReply = '<div class="reply"><p>“先看这里。”</p><div class="status-panel">爱心进度：50%<progress value="50" max="100">50%</progress></div></div>';
assert.strictEqual(structuredContent.splitStructuredContent(mixedHtmlReply)[0].kind, 'content');
assert.deepStrictEqual(chatMarkdown.extractChatSpeechSegments(mixedHtmlReply).map((item) => item.text), ['“先看这里。”']);
const speechOnlyGenericHtml = chatMarkdown.renderChatMarkdown(genericHtmlReply, { readMode: 'speechOnly' });
assert(speechOnlyGenericHtml.includes('st-chat-seg--speech'));
assert(!speechOnlyGenericHtml.includes('st-chat-rich-block--content'));

const streamingPrefixes = [
	'正文。\n<div class="status-panel"><p>爱心进度：',
	'正文。\n<div class="status-panel"><p>爱心进度：72%</p>',
	'正文。\n<div class="status-panel"><p>爱心进度：72%</p><p>阶段：重逢</p>',
	'正文。\n<div class="status-panel"><p>爱心进度：72%</p><p>阶段：重逢</p></div>'
];
streamingPrefixes.slice(0, -1).forEach((prefix) => {
	const parts = structuredContent.splitStructuredContent(prefix);
	assert.deepStrictEqual(parts.map((item) => item.type), ['text', 'pending']);
	assert.strictEqual(appearance.splitReplyBubbleTexts(prefix, 'bubble').length, 2);
});
assert.strictEqual(appearance.splitReplyBubbleTexts(streamingPrefixes[streamingPrefixes.length - 1], 'bubble').length, 2);
const pendingRendered = chatMarkdown.renderChatMarkdown(streamingPrefixes[1], { readMode: 'original' });
assert(pendingRendered.includes('st-chat-rich-pending'));
assert(pendingRendered.includes('爱心进度：72%'));
assert(!pendingRendered.includes('&lt;div class='), 'streaming HTML tags must not be exposed as text');

const statusSpan = '<span class="status-panel">爱心进度：72%</span>';
const statusSpanParts = structuredContent.splitStructuredContent(statusSpan);
assert.strictEqual(statusSpanParts[0].kind, 'status', 'status-marked inline roots must be protected too');

const dangerousStylePanel = '<div class="status-panel" style="position:fixed!important;inset:0;width:100vw;height:100vh;color:#ff0000">状态：覆盖</div>';
const safeStylePanel = chatMarkdown.renderChatMarkdown(dangerousStylePanel, { readMode: 'original' });
assert(!/position\s*:\s*fixed/i.test(safeStylePanel));
assert(!/100(?:vw|vh)/i.test(safeStylePanel));
assert(/color\s*:\s*#ff0000/i.test(safeStylePanel), 'safe inline color styling should remain available');
assert(safeStylePanel.includes('contain:layout paint'), 'rich content must stay layout-contained');

const unsafeRichPanel = richPanel.replace(
	'</details>',
	'<style>body{display:none}</style><script>window.bad = true</script><div onclick="window.bad=true">安全正文</div></details>'
);
const renderedRichPanel = chatMarkdown.renderChatMarkdown(unsafeRichPanel, { readMode: 'speechOnly' });
assert(renderedRichPanel.includes('st-chat-rich-block'), 'H5 must render rich content in its isolated wrapper');
for (const tag of ['details', 'summary', 'progress', 'meter']) {
	assert(renderedRichPanel.includes('<' + tag), `${tag} must be retained for status panels`);
}
assert(!/<script\b/i.test(renderedRichPanel), 'scripts must be removed from rich content');
assert(!/<style\b/i.test(renderedRichPanel), 'style blocks must be removed from rich content');
assert(!/\sonclick\s*=/i.test(renderedRichPanel), 'event-handler attributes must be removed from rich content');
const topLevelUnsafeReply = '<style>.status{color:red}.status div{display:block}</style>\n' + richPanel;
const topLevelUnsafeChunks = appearance.splitReplyBubbleTexts(topLevelUnsafeReply, 'bubble');
assert.strictEqual(topLevelUnsafeChunks.length, 1, 'top-level style blocks must not create blank or CSS text bubbles');
assert.strictEqual(topLevelUnsafeChunks[0], richPanel);
assert.deepStrictEqual(
	chatMarkdown.splitChatSegments(topLevelUnsafeReply).map((item) => item.type),
	['rich'],
	'top-level unsafe blocks must be discarded before semantic segmentation'
);

const nativeRichText = structuredContent.stripStructuredMarkupToText(
	'<table><tr><th>状态</th><th>数值</th></tr><tr><td>爱心进度</td><td>72%</td></tr></table>'
);
assert(nativeRichText.includes('爱心进度'));
assert(nativeRichText.includes('72%'));
assert(!/[<>]/.test(nativeRichText), 'APP fallback text must not expose HTML tags');
assert(chatSource.includes("const structuredContent = require('@/common/chatStructuredContent.js');"));
assert(chatSource.includes("return ['speech', 'action', 'thought', 'narration', 'rich'].indexOf(sourceType) >= 0"));
assert(chatSource.includes("return source.filter((item) => item.type === 'speech' || item.type === 'rich');"));
assert(chatSource.includes('structuredContent.stripStructuredMarkupToText(text)'));
assert(
	chatSource.includes('extractChatSpeechSegments(text)'),
	'TTS extraction must use the structured-content-aware speech path'
);
assert(chatSource.includes('expandNativeStructuredSegments(list)'));
assert(chatSource.includes('.st-chat-rich-block--status'));

storage.set('user', { id: 101, token: 'a' });
appearance.saveConfig(custom);
storage.set('user', { id: 202, token: 'b' });
assert.strictEqual(appearance.loadConfig().bubbleCustomized, false);
storage.set('user', { id: 101, token: 'a' });
assert.strictEqual(appearance.loadConfig().charMaxWidth, 91);

function deferred() {
	let resolve;
	let reject;
	const promise = new Promise((onResolve, onReject) => {
		resolve = onResolve;
		reject = onReject;
	});
	return { promise, resolve, reject };
}

function cloudScope(config, revision) {
	return Object.assign({ revision }, appearance.splitSections(config));
}

function cloudPayload(globalConfig, globalRevision, characterConfig, characterRevision) {
	const global = cloudScope(globalConfig || appearance.DEFAULT_CONFIG, globalRevision || 0);
	const character = characterConfig
		? cloudScope(characterConfig, characterRevision || 0)
		: { revision: characterRevision || 0 };
	return { global, character, effective: Object.assign({}, global) };
}

function conflictError() {
	const error = new Error('revision conflict');
	error.statusCode = 409;
	error.code = 'CONFLICT';
	return error;
}

async function runSyncContracts() {
	const realFetch = tavernApi.fetchChatPreferences;
	const realSave = tavernApi.saveChatPreferences;

	storage.clear();
	storage.set('user', { id: 301, token: 'owner-a' });
	const switchedOwnerResponse = deferred();
	tavernApi.fetchChatPreferences = () => switchedOwnerResponse.promise;
	tavernApi.saveChatPreferences = () => Promise.reject(new Error('unexpected save'));
	const switchedOwnerSync = appearance.syncFromCloud(null);
	storage.set('user', { id: 302, token: 'owner-b' });
	switchedOwnerResponse.resolve(cloudPayload(appearance.applyPreset(appearance.DEFAULT_CONFIG, 'night'), 1));
	await switchedOwnerSync;
	assert.strictEqual(storage.has('tavern_chat_appearance_v2_user_301'), false, 'stale owner response must not write old owner storage');
	assert.strictEqual(storage.has('tavern_chat_appearance_v2_user_302'), false, 'stale owner response must not contaminate new owner storage');

	storage.clear();
	storage.set('user', { id: 303, token: 'same-owner' });
	const first = deferred();
	const second = deferred();
	let fetchIndex = 0;
	tavernApi.fetchChatPreferences = () => (++fetchIndex === 1 ? first.promise : second.promise);
	const firstSync = appearance.syncFromCloud(null);
	const secondSync = appearance.syncFromCloud(null);
	const soft = appearance.applyPreset(appearance.DEFAULT_CONFIG, 'soft');
	second.resolve(cloudPayload(soft, 2));
	await secondSync;
	first.resolve(cloudPayload(appearance.applyPreset(appearance.DEFAULT_CONFIG, 'night'), 1));
	await firstSync;
	assert.strictEqual(appearance.loadConfig().charBubbleColor, soft.charBubbleColor, 'older response must not overwrite newer cloud revision');
	assert.strictEqual(appearance.getSyncState().revision, 2);

	storage.clear();
	storage.set('user', { id: 304, token: 'offline-owner' });
	const offlineConfig = appearance.applyPreset(appearance.DEFAULT_CONFIG, 'night');
	tavernApi.saveChatPreferences = () => Promise.reject(new Error('offline'));
	const locallySaved = await appearance.saveCloudConfig(offlineConfig);
	assert.strictEqual(locallySaved.preset, 'night');
	let offlineState = appearance.getSyncState();
	assert(offlineState.pending, 'offline save must retain pending payload');
	assert.strictEqual(offlineState.pending.expectedRevision, 0);
	assert.strictEqual(offlineState.lastError, 'network');
	let reconnectBody = null;
	let reconnectFetches = 0;
	tavernApi.fetchChatPreferences = () => {
		reconnectFetches += 1;
		return Promise.resolve(cloudPayload(appearance.DEFAULT_CONFIG, 0));
	};
	tavernApi.saveChatPreferences = (characterId, body) => {
		reconnectBody = body;
		return Promise.resolve({
			global: Object.assign({ revision: 1 }, {
				bubble: body.bubble,
				reading: body.reading,
				replyFormat: body.replyFormat
			}),
			character: { revision: 0 },
			effective: {}
		});
	};
	await appearance.syncFromCloud(null);
	assert.strictEqual(reconnectFetches, 0, 'reconnect must flush pending before fetching cloud');
	assert.strictEqual(reconnectBody.expectedRevision, 0);
	assert.strictEqual(reconnectBody.clientSchemaVersion, 3, 'cloud writes must identify the v3 appearance schema');
	offlineState = appearance.getSyncState();
	assert.strictEqual(offlineState.pending, null);
	assert.strictEqual(offlineState.revision, 1);

	storage.clear();
	storage.set('user', { id: 305, token: 'conflict-owner' });
	const classic = appearance.applyPreset(appearance.DEFAULT_CONFIG, 'classic');
	tavernApi.fetchChatPreferences = () => Promise.resolve(cloudPayload(classic, 1));
	await appearance.syncFromCloud(null);
	const localNight = appearance.applyPreset(appearance.DEFAULT_CONFIG, 'night');
	const serverSoft = appearance.applyPreset(appearance.DEFAULT_CONFIG, 'soft');
	tavernApi.saveChatPreferences = () => Promise.reject(conflictError());
	tavernApi.fetchChatPreferences = () => Promise.resolve(cloudPayload(serverSoft, 2));
	await assert.rejects(
		appearance.saveCloudConfig(localNight),
		(error) => error && error.preferenceConflict === true
	);
	let conflictState = appearance.getSyncState();
	assert(conflictState.pending, '409 must preserve local pending payload');
	assert(conflictState.conflict, '409 must persist explicit conflict state');
	assert.strictEqual(appearance.loadConfig().preset, 'night', '409 must not apply older/different cloud over local pending');
	await appearance.resolveSyncConflict(null, 'cloud');
	conflictState = appearance.getSyncState();
	assert.strictEqual(conflictState.pending, null);
	assert.strictEqual(conflictState.conflict, null);
	assert.strictEqual(appearance.loadConfig().preset, 'soft');

	storage.clear();
	storage.set('user', { id: 306, token: 'local-wins-owner' });
	tavernApi.fetchChatPreferences = () => Promise.resolve(cloudPayload(classic, 1));
	await appearance.syncFromCloud(null);
	tavernApi.saveChatPreferences = () => Promise.reject(conflictError());
	tavernApi.fetchChatPreferences = () => Promise.resolve(cloudPayload(serverSoft, 2));
	await assert.rejects(appearance.saveCloudConfig(localNight));
	let overwriteBody = null;
	tavernApi.saveChatPreferences = (characterId, body) => {
		overwriteBody = body;
		return Promise.resolve({
			global: Object.assign({ revision: 3 }, body),
			character: { revision: 0 },
			effective: {}
		});
	};
	await appearance.resolveSyncConflict(null, 'local');
	assert.strictEqual(overwriteBody.expectedRevision, 2, 'local conflict resolution must explicitly target latest cloud revision');
	assert.strictEqual(appearance.loadConfig().preset, 'night');

	storage.clear();
	storage.set('user', { id: 307, token: 'inherit-owner' });
	appearance.saveConfig(soft);
	const characterConfig = appearance.normalizeConfig(Object.assign({}, localNight, {
		readMode: 'novel',
		replySplitMode: 'bubble'
	}));
	tavernApi.saveChatPreferences = () => Promise.reject(new Error('offline'));
	await appearance.saveCloudConfig(characterConfig, {
		characterId: 77,
		inheritBubble: true,
		inheritReading: false,
		inheritReplyFormat: true
	});
	const characterState = appearance.getSyncState(77);
	assert.strictEqual(characterState.pending.payload.bubble, null);
	assert(characterState.pending.payload.reading);
	assert.strictEqual(characterState.pending.payload.replyFormat, null);
	assert.deepStrictEqual(Object.keys(appearance.loadCharacterSections(77)), ['reading']);
	assert.strictEqual(appearance.loadConfig(77).preset, 'soft', 'inherited bubble must continue using global config');

	const guardOwner = appearance.createRequestGuard();
	storage.clear();
	storage.set('user', { id: 308, token: 'unload-owner' });
	const unloadedResponse = deferred();
	tavernApi.fetchChatPreferences = () => unloadedResponse.promise;
	const unloadedSync = appearance.syncFromCloud(null, { guard: guardOwner });
	guardOwner.cancel();
	unloadedResponse.resolve(cloudPayload(localNight, 1));
	await unloadedSync;
	assert.strictEqual(storage.has('tavern_chat_appearance_v2_user_308'), false, 'unloaded page guard must prevent late storage writes');

	tavernApi.fetchChatPreferences = realFetch;
	tavernApi.saveChatPreferences = realSave;
	storage.clear();
	storage.set('user', { id: 401, token: 'captured-token' });
	let capturedRequest = null;
	uniRequestHandler = (options) => { capturedRequest = options; };
	const requestSession = tavernApi.captureRequestSession();
	const apiRequest = realFetch(null, requestSession);
	assert(capturedRequest.url.includes('clientUid=h5u_401'));
	assert.strictEqual(capturedRequest.header.Authorization, 'Bearer captured-token');
	storage.set('user', { id: 402, token: 'new-token' });
	capturedRequest.success({ statusCode: 200, header: {}, data: { code: 1, data: cloudPayload(classic, 1) } });
	await assert.rejects(apiRequest, (error) => error && error.staleSession === true);
	uniRequestHandler = null;
}

assert(settingSource.includes('inheritBubble: this.inheritBubble'), 'inheritance flags must participate in the settings draft signature');
assert(settingSource.includes('requestVersion !== this.scopeRequestVersion'), 'settings cloud loads must use request generations');
assert(settingSource.includes('this.currentDraftSignature !== requestDraftSignature'), 'cloud load must not overwrite an edited form');
assert(settingSource.includes('this.appearanceRequestGuard.cancel()'), 'settings unload must invalidate pending requests');
assert(settingSource.includes("previewScenes: [{ code: 'text' }, { code: 'media' }, { code: 'status' }]") , 'settings preview must cover text, media and status/action scenes');
assert(settingSource.includes("setBubbleValue('contentTone', item.code)"), 'embedded content tone must be configurable');
assert(settingSource.includes("light: [400, 500, 500, 400, 400, 400]"), 'the natural typography profile must match the new readable default');
assert(settingSource.includes("presetLineStyle(item, false, 'speech')"), 'preset previews must expose semantic dialogue color');
assert(settingSource.includes('this.applyDraftSection(normalizedSection, this.sectionPayload(this.globalConfig, normalizedSection))'), 'enabling inheritance must preview the global section immediately');
assert(settingSource.includes('this.detachedDrafts[normalizedSection] || this.sectionPayload(this.globalConfig, normalizedSection)'), 'disabling inheritance must restore the detached character draft');
const resetMethod = settingSource.slice(settingSource.indexOf('\n\t\treset() {'), settingSource.indexOf('\n\t\tsave() {'));
assert(!resetMethod.includes('resetConfig()'), 'restore defaults must not mutate saved storage before Save');
assert(!resetMethod.includes('savedConfigSignature ='), 'restore defaults must stay dirty until a real save');
assert(chatSource.includes('chatAppearanceRequestVersion'), 'chat page must use appearance request generations');
assert(chatSource.includes('this.chatAppearanceRequestGuard.cancel()'), 'chat unload must invalidate appearance sync');

for (const language of ['zh-cn', 'zh-hk', 'en', 'ko', 'ja']) {
	const copy = appearanceI18n.getAppearanceCopy(language);
	for (const key of ['globalScope', 'characterScope', 'inheritGlobal', 'characterSpecific', 'syncPending', 'syncConflict', 'keepLocal', 'useCloud', 'contentToneStyle', 'previewScenes', 'typographyProfiles', 'surfaceModes', 'contentTones']) {
		assert(String(copy[key] || '').trim(), `${language} is missing ${key}`);
	}
}

runSyncContracts()
	.then(() => console.log(`chat appearance contract passed (${presets.length} presets + sync reliability)`))
	.catch((error) => {
		console.error(error);
		process.exitCode = 1;
	});
