import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const preset = fs.readFileSync(path.join(root, 'src/views/jiugai/chatpreset/index.vue'), 'utf8')
const entitlement = fs.readFileSync(path.join(root, 'src/views/jiugai/entitlement/index.vue'), 'utf8')
const presetApi = fs.readFileSync(path.join(root, 'src/api/jiugai/chatPreset.js'), 'utf8')

test('聊天预设后台明确展示 ST 来源状态并阻止启用失效来源', () => {
  assert.match(preset, /label="ST 来源"/)
  assert.match(preset, /row\.sourceAvailable === false \? '已失效' : '正常'/)
  assert.match(preset, /:disabled="row\.sourceAvailable === false"/)
})

test('ST 同步结果展示导入、跳过、失效数量和有界警告详情', () => {
  assert.match(preset, /data\.imported \|\| 0/)
  assert.match(preset, /data\.skipped \|\| 0/)
  assert.match(preset, /data\.unavailable \|\| 0/)
  assert.match(preset, /Array\.isArray\(data\.warnings\)/)
  assert.match(preset, /warnings\.slice\(0, 5\)/)
  assert.match(preset, /proxy\.\$modal\.msgWarning\(/)
})

test('系统默认参数从独立接口读取保存并沿用预设编辑权限', () => {
  assert.match(preset, /系统默认聊天参数/)
  assert.match(preset, /getChatPresetGlobalDefault\(\)/)
  assert.match(preset, /saveChatPresetGlobalDefault\(\{ maxTokens, maxContext \}\)/)
  assert.match(preset, /v-hasPermi="\['content:chat-preset:edit'\]"/)
  assert.match(presetApi, /url: '\/admin\/jiugai\/chat-preset\/global-default'/)
  assert.match(presetApi, /method: 'put'/)
})

test('单个 ST 预设支持后台覆盖、恢复原始配置并显示覆盖状态', () => {
  assert.match(preset, /row\.customized/)
  assert.match(preset, /configDraft/)
  assert.match(preset, /handleSaveConfig/)
  assert.match(preset, /handleRestoreConfig/)
  assert.match(preset, /恢复 ST 配置/)
  assert.match(preset, /保存预设配置/)
  assert.match(presetApi, /\/config/)
  assert.match(presetApi, /method: 'delete'/)
})

test('站点规则提供系统预设和我的预设两个独立显示开关', () => {
  assert.match(entitlement, /显示系统预设选择/)
  assert.match(entitlement, /v-model="runtimeSettings\.systemChatPresetEntryVisible"/)
  assert.match(entitlement, /显示我的预设设置/)
  assert.match(entitlement, /v-model="runtimeSettings\.userChatPresetEntryVisible"/)
  assert.match(entitlement, /systemChatPresetEntryVisible: runtimeSettings\.systemChatPresetEntryVisible/)
  assert.match(entitlement, /userChatPresetEntryVisible: runtimeSettings\.userChatPresetEntryVisible/)
})
