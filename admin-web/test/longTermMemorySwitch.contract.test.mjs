import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const source = fs.readFileSync(new URL('../src/views/jiugai/entitlement/index.vue', import.meta.url), 'utf8')

test('站点规则提供默认关闭的长期记忆全局开关', () => {
  assert.match(source, /v-model="runtimeSettings\.longTermMemoryEnabled"/)
  assert.match(source, /longTermMemoryEnabled:\s*false/)
  assert.match(source, /runtimeSettings\.longTermMemoryEnabled = data\.longTermMemoryEnabled === true/)
  assert.match(source, /longTermMemoryEnabled:\s*runtimeSettings\.longTermMemoryEnabled/)
  assert.match(source, /禁止已有记忆进入聊天 Prompt/)
})
