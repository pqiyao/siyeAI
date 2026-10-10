import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = readFileSync(resolve('src/views/index.vue'), 'utf8')
const serviceSource = readFileSync(
  resolve('../../SillySpringboot/src/main/java/com/example/sillyspringboot/admin/service/AdminDashboardService.java'),
  'utf8'
)
const mapperSource = readFileSync(
  resolve('../../SillySpringboot/src/main/resources/mapper/admin/AdminDashboardMapper.xml'),
  'utf8'
)

test('首页生成指标和趋势使用同一个时间范围', () => {
  assert.match(serviceSource, /minusDays\(Math\.max\(1, trendDays\) - 1L\)/)
  assert.match(serviceSource, /totalTasks\(trendStartDate\)/)
  assert.match(serviceSource, /successTasks\(trendStartDate\)/)
  assert.match(serviceSource, /generationTrend\(trendStartDate\)/)
  assert.match(mapperSource, /<sql id="GenerationTaskFacts">/)
  assert.match(mapperSource, /<include refid="GenerationTaskFacts"\/>/)
  assert.match(mapperSource, /d\.stat_day &gt;= #\{startDate\}/)
})

test('首页明确区分累计指标与当前范围生成指标', () => {
  for (const label of ['有效角色', '累计用户', '累计成功 AI 响应', '累计已支付订单']) {
    assert.match(source, new RegExp(label))
  }
  assert.match(source, /activeRangeLabel/)
  assert.match(source, /latestGenerationText/)
})

test('当前范围没有生成数据时展示最近生成时间而不是零折线', () => {
  assert.match(source, /v-show="hasGenerationTrendData"/)
  assert.match(source, /v-if="!hasGenerationTrendData"/)
  assert.match(serviceSource, /latestGenerationAt\(\)/)
})
