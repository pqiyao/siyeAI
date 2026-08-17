import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const panel = fs.readFileSync(
  new URL('../src/views/jiugai/openrouter/UnifiedAiRoutingPanel.vue', import.meta.url),
  'utf8'
)

test('统一供应商页面允许清空并删除已有执行路由', () => {
  assert.match(panel, /deleteAiRoute/)
  assert.match(panel, /routeDraft\.length === 0 \? '删除执行路由' : '保存执行顺序'/)
  assert.doesNotMatch(panel, /:disabled="routeDraft\.length === 0"/)
})

test('旧聊天路由入口只在聊天供应商工作区显示', () => {
  assert.match(
    panel,
    /v-if="activeCapability === 'CHAT' && chatWorkspace === 'providers'"[\s\S]{0,220}@click="importLegacy"/
  )
})

test('可能计费的真实探测展示费用提醒', () => {
  assert.match(panel, /\['IMAGE', 'TTS', 'STT'\]\.includes\(providerForm\.capability\)/)
  assert.match(panel, /可能产生费用/)
})
