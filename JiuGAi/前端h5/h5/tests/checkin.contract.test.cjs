const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { TextDecoder } = require('node:util');

const root = path.resolve(__dirname, '..');
const readUtf8 = (relativePath) => {
  const bytes = fs.readFileSync(path.join(root, relativePath));
  return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
};

const checkin = readUtf8('pages/user/checkin.vue');
const user = readUtf8('pages/user/user.vue');
const index = readUtf8('pages/index/index.vue');
const pages = JSON.parse(readUtf8('pages.json'));
const api = readUtf8('common/tavernApi.js');

assert.ok(checkin.includes('每日签到'), '签到页必须保持有效 UTF-8 中文');
assert.ok(checkin.includes('class="track-check">✓</text>'), '已签到日期必须显示完成符号');
assert.ok(!checkin.includes('class="track-check">?</text>'), '签到页不能保留损坏的问号符号');
assert.ok(pages.pages.some((item) => item.path === 'pages/user/checkin'), '签到页必须注册到 pages.json');
assert.match(api, /function fetchCheckinStatus\(/, '签到状态 API 必须存在');
assert.match(api, /function claimCheckin\(/, '签到领取 API 必须存在');

const userSkinEntryOverride = user.lastIndexOf('.checkin-entry {');
assert.ok(userSkinEntryOverride > user.indexOf('/* Final user page skin'), '签到入口必须沿用当前用户中心皮肤');
assert.match(user.slice(userSkinEntryOverride), /background:\s*rgba\(255, 255, 255, 0\.34\)/, '签到入口应沿用用户中心的轻量玻璃背景');
assert.ok(user.includes("'is-claimable': checkinClaimable"), '待签到入口必须有明显的可领取状态');
assert.ok(user.includes('src="/static/user/checkin-calendar.png"'), '签到入口必须使用日历勾选图标');
assert.ok(!user.includes('class="checkin-entry-icon"><text>签</text>'), '签到入口不能再使用文字充当图标');
assert.ok(fs.existsSync(path.join(root, 'static/user/checkin-calendar.png')), '签到日历图标资源必须存在');
assert.match(checkin, /mode="dark"/, '签到页必须恢复原来的深色导航模式');
assert.ok(checkin.includes('class="hero-head"'), '签到页必须有清晰的奖励标题与连续签到状态');
assert.ok(checkin.includes('class="checkin-hero"'), '签到主视觉必须使用签到页独立类名');
assert.ok(!checkin.includes('class="hero-card"'), '签到主视觉不能被全局浅色 hero-card 样式覆盖');
assert.ok(checkin.includes('class="checkin-section"'), '签到内容区必须使用签到页独立类名');
assert.ok(!checkin.includes('class="section-card"'), '签到内容区不能被全局 section-card 颜色覆盖');
assert.ok(checkin.includes('background: linear-gradient(145deg, #ffffff 0%, #eef8fd 58%, #fff4f7 100%)'), '签到主视觉必须使用清爽浅色配色');
assert.ok(checkin.includes('color: var(--checkin-ink)'), '浅色签到主视觉必须使用深色高对比文字');
assert.ok(checkin.includes('--checkin-ink: #173042'), '签到页必须使用高对比正文颜色');
assert.ok(!checkin.includes('color: rgba(241, 245, 249, 0.82)'), '浅色卡片不能继续使用接近白色的低对比文字');
assert.match(checkin, /max-width:\s*760px/, '签到页桌面端内容必须限制宽度');
assert.ok(checkin.includes('src="/static/home/zs.png"'), '钻石奖励必须显示钻石图标');
assert.ok(checkin.includes('src="/static/user/qian.png"'), '金币与资产区域必须显示钱包图标');
assert.ok(checkin.includes('src="/static/home/sendchat.png"'), '聊天次数必须显示聊天图标');
assert.ok(checkin.includes('src="/static/chat/image-generate.png"'), '生图次数必须显示生图图标');
assert.ok(!checkin.includes('reward-icon-wrap'), '奖励图标不能再套彩色盒子');
assert.ok(!checkin.includes('hero-icon-wrap'), '签到标题图标不能再套彩色盒子');
assert.ok(!checkin.includes('section-icon-wrap'), '分区图标不能再套彩色盒子');
assert.ok(!checkin.includes('note-icon-wrap'), '明日奖励图标不能再套彩色盒子');
assert.ok(!checkin.includes('state-icon-wrap'), '状态图标不能再套彩色盒子');
assert.match(checkin, /\.reward-label\s*\{[\s\S]*?font-size:\s*24rpx;/, '奖励名称字号不能小于 24rpx');
assert.match(checkin, /\.section-subtitle\s*\{[\s\S]*?font-size:\s*24rpx;/, '分区说明字号不能小于 24rpx');
assert.match(checkin, /\.quota-item\s*\{[\s\S]*?font-size:\s*24rpx;/, '额度说明字号不能小于 24rpx');
assert.ok(!checkin.includes('class="quota-line"'), '签到页额度说明不能拆成额外盒子');
assert.match(checkin, /\.track-state\s*\{[^}]*font-size:\s*23rpx;/, '签到轨迹状态字号不能小于 23rpx');
assert.match(checkin, /\.note-text\s*\{[\s\S]*?font-size:\s*24rpx;/, '签到说明文字不能小于 24rpx');
assert.ok(api.includes('checkinEntryVisible: raw.checkinEntryVisible !== false'), '签到入口开关必须默认显示并兼容旧配置');
assert.match(api, /function isCheckinEntryVisible\(\)/, '签到入口开关读取方法必须存在');
assert.ok(api.includes('isCheckinEntryVisible: isCheckinEntryVisible'), '签到入口开关读取方法必须导出');
assert.ok(
  user.includes('v-if="hasToken && checkinEntryReady && checkinEntryVisible" class="checkin-entry"'),
  '签到入口必须遵循登录态和显示开关'
);
assert.ok(user.includes('checkinEntryReady: true'), '签到入口必须使用本地配置立即渲染');
assert.ok(!user.includes('this.checkinEntryReady = false;'), '远程配置刷新期间不能隐藏签到入口');
assert.ok(user.includes('this.syncCheckinEntryVisibility(false);'), '用户页应复用运行配置缓存并静默刷新签到开关');
assert.ok(
  user.includes('if (!this.checkinEntryReady || !this.checkinEntryVisible) return;'),
  '签到入口隐藏或配置未就绪时必须阻止跳转'
);
assert.ok(user.includes("const checkinUrl = '/pages/user/checkin';"), '签到入口必须使用已注册的固定页面路由');
assert.ok(user.includes('uni.redirectTo({'), 'navigateTo 失败时必须提供页面栈兜底跳转');
assert.ok(user.includes("title: '无法打开每日签到'"), '签到路由不可用时必须向用户明确提示更新 APP');
assert.match(index, /\.tag-link\s*\{[\s\S]*?font-size:\s*26rpx;/, '首页筛选标签字号不能小于 26rpx');
assert.match(index, /\.mini-tag,[\s\S]*?font-size:\s*20rpx;/, '角色卡标签字号不能小于 20rpx');

console.log('check-in frontend contract: OK');
