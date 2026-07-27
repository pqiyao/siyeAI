import assert from 'node:assert/strict'
import test from 'node:test'
import { filterDiscoveredModels, matchesModelSearch } from '../src/views/jiugai/openrouter/modelFilter.js'

const models = [
  { id: 'Qwen/Qwen3-VL-32B-Instruct', label: 'Qwen3 VL 32B', ownedBy: 'Qwen', capabilityMatch: true },
  { id: 'anthropic/claude-3-haiku', label: 'Anthropic: Claude 3 Haiku', ownedBy: 'Anthropic', capabilityMatch: true },
  { id: 'Qwen/Qwen3-Reranker-4B', label: '通义千问重排序模型', ownedBy: 'Qwen', capabilityMatch: false }
]

test('模型搜索支持 ID、显示名称、厂商和多关键词', () => {
  assert.equal(matchesModelSearch(models[0], 'qwen3 vl'), true)
  assert.equal(matchesModelSearch(models[1], 'Anthropic Claude 3 Haiku'), true)
  assert.equal(matchesModelSearch(models[1], 'claude-3-haiku'), true)
  assert.equal(matchesModelSearch(models[2], '千问 重排序'), true)
  assert.equal(matchesModelSearch(models[0], 'claude'), false)
})

test('能力筛选和文本搜索同时生效', () => {
  assert.deepEqual(
    filterDiscoveredModels(models, { matchedOnly: true, query: 'qwen' }).map((item) => item.id),
    ['Qwen/Qwen3-VL-32B-Instruct']
  )
  assert.deepEqual(
    filterDiscoveredModels(models, { matchedOnly: false, query: 'reranker' }).map((item) => item.id),
    ['Qwen/Qwen3-Reranker-4B']
  )
})
