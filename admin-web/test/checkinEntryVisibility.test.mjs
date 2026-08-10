import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = fs.readFileSync(
  path.join(root, 'src/views/jiugai/entitlement/index.vue'),
  'utf8'
)

test('每日签到入口开关完整接入站点规则', () => {
  assert.match(source, /显示每日签到入口/)
  assert.match(source, /v-model="runtimeSettings\.checkinEntryVisible"/)
  assert.match(source, /checkinEntryVisible:\s*true/)
  assert.match(
    source,
    /runtimeSettings\.checkinEntryVisible\s*=\s*data\.checkinEntryVisible\s*!==\s*false/
  )
  assert.match(
    source,
    /checkinEntryVisible:\s*runtimeSettings\.checkinEntryVisible/
  )
})
