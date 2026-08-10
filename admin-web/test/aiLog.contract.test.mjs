import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const page = fs.readFileSync(path.join(root, 'src/views/jiugai/ailog/index.vue'), 'utf8')
const api = fs.readFileSync(path.join(root, 'src/api/jiugai/ailog.js'), 'utf8')

test('AI 日志在原页面分开聊天任务和独立能力', () => {
  assert.match(page, /label="聊天任务" name="TASK"/)
  assert.match(page, /label="独立能力" name="STANDALONE"/)
  assert.doesNotMatch(page, /逐供应商调用。任务详情/)
})

test('AI 日志支持精确状态和统一调用筛选', () => {
  for (const status of ['QUEUED', 'GENERATING', 'SUCCESS', 'FAILED', 'STOPPED', 'CANCELLED']) {
    assert.match(page, new RegExp("value: '" + status + "'"))
  }
  for (const field of ['traceId', 'providerKey', 'model', 'httpStatus']) {
    assert.match(page, new RegExp('queryParams\\.' + field))
  }
  assert.match(page, /type="datetimerange"/)
})

test('独立能力覆盖 VISION、IMAGE、TTS、STT 并按请求展示 fallback', () => {
  for (const capability of ['VISION', 'IMAGE', 'TTS', 'STT']) {
    assert.match(page, new RegExp('value="' + capability + '"'))
  }
  assert.match(page, /scope\.row\.attemptCount/)
  assert.match(page, /scope\.row\.wasFallback/)
  assert.match(page, /供应商调用链/)
})

test('聊天任务和独立能力均提供真实尝试链详情接口', () => {
  assert.match(api, /\/admin\/jiugai\/ai-log\/attempts\//)
  assert.match(api, /\/admin\/jiugai\/ai-log\/standalone\/attempts\//)
  assert.match(page, /listAiTaskAttempts/)
  assert.match(page, /listStandaloneAiRequestAttempts/)
  for (const metric of ['ttftMs', 'promptTokens', 'completionTokens', 'totalCostUsd']) {
    assert.match(page, new RegExp(metric))
  }
})
