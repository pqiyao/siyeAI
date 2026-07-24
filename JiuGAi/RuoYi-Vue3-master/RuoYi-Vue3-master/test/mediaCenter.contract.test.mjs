import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const media = fs.readFileSync(path.join(root, 'src/views/jiugai/media/index.vue'), 'utf8')
const api = fs.readFileSync(path.join(root, 'src/api/jiugai/media.js'), 'utf8')
const entitlement = fs.readFileSync(path.join(root, 'src/views/jiugai/entitlement/index.vue'), 'utf8')

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

test('媒体中心保留用户自定义通道并将官方平台配置统一交给模型路由', () => {
  assert.match(media, /用户自定义 API/)
  assert.match(media, /配置 IMAGE 路由/)
  assert.match(media, /openModelRouting\('IMAGE'\)/)
  assert.match(media, /comfyFallbackEnabled/)
  assert.doesNotMatch(media, /v-model="imageForm\.managed(?:ProviderSource|ImageModelName|ApiKey|CustomUrl)"/)
})

test('IMAGE、TTS、STT 都能直达对应模型路由能力', () => {
  assert.match(media, /openModelRouting\(item\.capability\)/)
  assert.match(media, /query: \{ capability \}/)
})

test('旧权益页不再保留重复媒体配置代码', () => {
  assert.doesNotMatch(entitlement, /v-model="runtimeSettings\.(?:imageGenerationEnabled|voiceFeatureEnabled)"/)
  assert.doesNotMatch(entitlement, /managedProviderSource|managedImageModelName|managedApiKey|managedCustomUrl/)
  assert.doesNotMatch(entitlement, /getImageGenerationSettings|updateImageGenerationSettings/)
  assert.doesNotMatch(entitlement, /voiceTemplateDialogOpen|listTtsVoiceTemplates/)
})
