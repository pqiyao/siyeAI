import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const media = fs.readFileSync(path.join(root, 'src/views/jiugai/media/index.vue'), 'utf8')
const api = fs.readFileSync(path.join(root, 'src/api/jiugai/media.js'), 'utf8')
const entitlement = fs.readFileSync(path.join(root, 'src/views/jiugai/entitlement/index.vue'), 'utf8')
const routing = fs.readFileSync(path.join(root, 'src/views/jiugai/openrouter/UnifiedAiRoutingPanel.vue'), 'utf8')
const walletLedger = fs.readFileSync(path.join(root, 'src/views/jiugai/walletledger/index.vue'), 'utf8')
const entitlementLog = fs.readFileSync(path.join(root, 'src/views/jiugai/entitlementlog/index.vue'), 'utf8')

test('AI 媒体中心共用一个菜单并提供三个独立管理页签', () => {
  assert.match(media, /<el-tab-pane name="image">/)
  assert.match(media, /<el-tab-pane name="voice">/)
  assert.match(media, /<el-tab-pane name="templates">/)
  assert.match(media, /生图策略/)
  assert.match(media, /语音服务/)
  assert.match(media, /音色模板/)
})

test('生图全局策略和单角色覆盖具有独立权限与完整来源约束', () => {
  assert.match(media, /ops:media:image:edit/)
  assert.match(media, /allowedConsistencyModes/)
  assert.match(media, /allowedReferenceSourceModes/)
  assert.match(media, /defaultReferenceSourceMode/)
  assert.match(api, /image-policy\/characters\/\$\{characterId\}/)
  assert.match(api, /method: 'delete'/)
})

test('语音运行策略和音色模板使用各自写权限', () => {
  assert.match(media, /ops:media:voice:edit/)
  assert.match(media, /content:voice-template:edit/)
  assert.match(media, /voiceForm\.runtime\.tts/)
  assert.match(media, /voiceForm\.runtime\.stt/)
  assert.match(api, /\/admin\/jiugai\/media\/voice-policy/)
})

test('用户自建音色页签按权限加载并支持结束异常创建任务', () => {
  assert.match(media, /v-if="canViewUserVoices" name="userVoices"/)
  assert.match(media, /canManageUserVoices/)
  assert.match(media, /if \(!canViewUserVoices\.value\)/)
  assert.match(media, /if \(canViewUserVoices\.value\) loadUserVoices\(\)/)
  assert.match(media, /结束异常任务/)
  assert.match(api, /finish-provisioning/)
})

test('媒体中心明确系统 NovelAI、实验性自定义通道与显式 Comfy 接管', () => {
  assert.match(media, /用户自定义 API/)
  assert.match(media, /系统 NovelAI/)
  assert.match(media, /novelAiStatus\.tokenConfigured/)
  assert.match(media, /Token 已配置/)
  assert.match(media, /缺少 Token/)
  assert.match(media, /本地 Comfy 兼容通道/)
  assert.match(media, /comfyFallbackEnabled/)
  assert.doesNotMatch(media, /配置 IMAGE 路由/)
  assert.doesNotMatch(media, /openModelRouting\('IMAGE'\)/)
  assert.doesNotMatch(media, /v-model="imageForm\.managed(?:ProviderSource|ImageModelName|ApiKey|CustomUrl)"/)
})

test('IMAGE、TTS、STT 都能直达对应模型路由能力', () => {
  assert.match(media, /openModelRouting\(item\.capability\)/)
  assert.match(media, /query: \{ capability \}/)
})

test('VISION 作为第五种独立能力提供供应商池、运行开关和独立计费', () => {
  assert.match(routing, /value: 'VISION', label: '视觉理解'/)
  assert.match(routing, /runtimeDraft\.visionEnabled/)
  assert.match(entitlement, /form\.visionScoreCost/)
  assert.match(entitlement, /form\.visionGoldCost/)
  assert.match(entitlement, /按原规则结算正常聊天/)
})

test('五类模型共用能力匹配和多字段搜索筛选', () => {
  assert.match(routing, /:filter-method="filterModelOptions"/)
  assert.match(routing, /filterDiscoveredModels/)
  assert.match(routing, /matchedOnly\.value = true/)
  assert.match(routing, /chat\|vision\|image\|tts\|stt/)
  assert.match(routing, /未识别到当前能力模型/)
})

test('钱包流水在原页面分开充值收益与其他资金变动', () => {
  assert.match(walletLedger, /label="充值收益" name="REVENUE"/)
  assert.match(walletLedger, /label="其他资金变动" name="OTHER"/)
  assert.match(walletLedger, /groupType: 'REVENUE'/)
})

test('权益日志在原页面分开后台操作与功能权益消耗', () => {
  assert.match(entitlementLog, /label="后台权益操作" name="OPERATIONS"/)
  assert.match(entitlementLog, /label="功能权益消耗" name="CONSUMPTION"/)
  assert.match(entitlementLog, /label="次数额度" name="QUOTA"/)
  assert.match(entitlementLog, /label="钻石\/金币" name="WALLET"/)
  assert.match(entitlementLog, /listEntitlementWalletConsumption/)
})

test('功能权益消耗可筛选并展示 VISION 及既有媒体消费退款类型', () => {
  for (const bizType of [
    'IMAGE_CONSUME',
    'IMAGE_REFUND',
    'TTS_CONSUME',
    'TTS_REFUND',
    'STT_CONSUME',
    'STT_REFUND',
    'VISION_CONSUME',
    'VISION_REFUND'
  ]) {
    assert.match(entitlementLog, new RegExp(`'${bizType}'`))
    assert.match(walletLedger, new RegExp(`${bizType}: labels\\.`))
  }
})

test('旧权益页不再保留重复媒体配置代码', () => {
  assert.doesNotMatch(entitlement, /v-model="runtimeSettings\.(?:imageGenerationEnabled|voiceFeatureEnabled)"/)
  assert.doesNotMatch(entitlement, /managedProviderSource|managedImageModelName|managedApiKey|managedCustomUrl/)
  assert.doesNotMatch(entitlement, /getImageGenerationSettings|updateImageGenerationSettings/)
  assert.doesNotMatch(entitlement, /voiceTemplateDialogOpen|listTtsVoiceTemplates/)
})
