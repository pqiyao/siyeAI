<template>
  <section class="generation-ops" v-loading="loading" aria-label="生成运营看板">
    <header class="ops-header">
      <div>
        <div class="title-line">
          <el-icon><DataAnalysis /></el-icon>
          <h2>生成运营</h2>
        </div>
        <p>成本、延迟与路由观测均为旁路记录，不参与模型选择和聊天生成。</p>
      </div>
      <div class="coverage" :class="{ 'coverage--warn': unpricedAttempts > 0 }">
        <span class="coverage-dot"></span>
        <span>{{ coverageText }}</span>
      </div>
    </header>

    <div class="summary-grid">
      <article v-for="item in summaryItems" :key="item.key" class="summary-item">
        <div class="summary-icon" :class="`summary-icon--${item.tone}`">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="summary-copy">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.detail }}</small>
        </div>
      </article>
    </div>

    <el-tabs v-model="activeTab" class="ops-tabs">
      <el-tab-pane label="运行概览" name="overview">
        <div class="chart-layout">
          <section class="surface chart-surface">
            <div class="surface-head">
              <div>
                <h3>延迟趋势</h3>
                <p>首 Token 与完整响应平均耗时</p>
              </div>
              <el-tag size="small" type="info" effect="plain">毫秒</el-tag>
            </div>
            <div v-show="latencyTrend.length" ref="latencyRef" class="ops-chart"></div>
            <el-empty v-if="!latencyTrend.length" description="暂无延迟样本" :image-size="64" />
          </section>

          <section class="surface chart-surface">
            <div class="surface-head">
              <div>
                <h3>每日消耗</h3>
                <p>Token 与已定价成本</p>
              </div>
              <el-tag size="small" type="warning" effect="plain">USD</el-tag>
            </div>
            <div v-show="latencyTrend.length" ref="costRef" class="ops-chart"></div>
            <el-empty v-if="!latencyTrend.length" description="暂无成本样本" :image-size="64" />
          </section>
        </div>

        <section class="surface error-surface">
          <div class="surface-head">
            <div>
              <h3>失败聚类</h3>
              <p>按错误码和 HTTP 状态合并，便于定位供应商异常</p>
            </div>
          </div>
          <el-table :data="errorStats" size="small" max-height="300" empty-text="当前范围内没有失败记录">
            <el-table-column prop="errorCode" label="错误码" min-width="180">
              <template #default="{ row }">{{ row.errorCode || 'UNKNOWN' }}</template>
            </el-table-column>
            <el-table-column prop="httpStatus" label="HTTP" width="90">
              <template #default="{ row }">{{ row.httpStatus ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="count" label="次数" width="100" sortable />
            <el-table-column prop="lastOccurredAt" label="最近发生" min-width="180">
              <template #default="{ row }">{{ formatDateTime(row.lastOccurredAt) }}</template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="供应商与路由" name="providers">
        <section class="surface table-surface">
          <div class="surface-head">
            <div>
              <h3>供应商质量</h3>
              <p>一次 fallback 会形成独立尝试，成功率按尝试计算</p>
            </div>
          </div>
          <el-table :data="providerStats" size="small" empty-text="暂无供应商观测数据">
            <el-table-column prop="providerKey" label="供应商" min-width="160" fixed="left" />
            <el-table-column prop="attempts" label="尝试" width="90" sortable />
            <el-table-column label="成功率" width="120" sortable :sort-method="sortBySuccessRate">
              <template #default="{ row }">
                <span :class="rateClass(row.successRate)">{{ formatRate(row.successRate) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fallbackAttempts" label="Fallback" width="105" sortable />
            <el-table-column label="平均耗时" width="120">
              <template #default="{ row }">{{ formatDuration(row.avgDurationMs) }}</template>
            </el-table-column>
            <el-table-column label="P95" width="110">
              <template #default="{ row }">{{ formatDuration(row.p95DurationMs) }}</template>
            </el-table-column>
            <el-table-column label="首 Token" width="120">
              <template #default="{ row }">{{ formatDuration(row.avgTtftMs) }}</template>
            </el-table-column>
            <el-table-column label="Token" min-width="110" align="right">
              <template #default="{ row }">{{ formatInteger(row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column label="成本" min-width="120" align="right">
              <template #default="{ row }">{{ formatUsd(row.totalCostUsd) }}</template>
            </el-table-column>
          </el-table>
        </section>

        <section class="surface table-surface route-surface">
          <div class="surface-head">
            <div>
              <h3>路由与熔断</h3>
              <p>展示实际命中、回退和当前健康状态</p>
            </div>
          </div>
          <el-table :data="routeHealth" size="small" empty-text="暂无路由观测数据">
            <el-table-column prop="routeKey" label="路由" min-width="140" />
            <el-table-column prop="providerKey" label="供应商" min-width="150" />
            <el-table-column prop="attempts" label="尝试" width="90" />
            <el-table-column label="成功率" width="110">
              <template #default="{ row }">{{ formatRate(row.successRate) }}</template>
            </el-table-column>
            <el-table-column prop="fallbackAttempts" label="Fallback" width="100" />
            <el-table-column prop="consecutiveFailures" label="连续失败" width="100" />
            <el-table-column label="健康" width="120">
              <template #default="{ row }">
                <el-tag :type="healthTagType(row)" size="small" effect="plain">
                  {{ healthLabel(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="熔断至" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.circuitOpenUntil) }}</template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="模型与角色" name="consumption">
        <div class="table-layout">
          <section class="surface table-surface">
            <div class="surface-head">
              <div>
                <h3>模型消耗</h3>
                <p>按供应商和最终模型聚合</p>
              </div>
            </div>
            <el-table :data="modelStats" size="small" max-height="440" empty-text="暂无模型消耗数据">
              <el-table-column prop="model" label="模型" min-width="190" show-overflow-tooltip />
              <el-table-column prop="providerKey" label="供应商" min-width="130" />
              <el-table-column prop="attempts" label="尝试" width="80" />
              <el-table-column label="成功率" width="100">
                <template #default="{ row }">{{ formatRate(row.successRate) }}</template>
              </el-table-column>
              <el-table-column label="Token" width="110" align="right">
                <template #default="{ row }">{{ formatInteger(row.totalTokens) }}</template>
              </el-table-column>
              <el-table-column label="成本" width="115" align="right">
                <template #default="{ row }">{{ formatUsd(row.totalCostUsd) }}</template>
              </el-table-column>
            </el-table>
          </section>

          <section class="surface table-surface">
            <div class="surface-head">
              <div>
                <h3>角色消耗</h3>
                <p>识别高消耗角色与异常上下文增长</p>
              </div>
            </div>
            <el-table :data="characterStats" size="small" max-height="440" empty-text="暂无角色消耗数据">
              <el-table-column prop="characterName" label="角色" min-width="170">
                <template #default="{ row }">{{ row.characterName || `角色 #${row.characterId}` }}</template>
              </el-table-column>
              <el-table-column prop="attempts" label="尝试" width="80" />
              <el-table-column label="成功率" width="100">
                <template #default="{ row }">{{ formatRate(row.successRate) }}</template>
              </el-table-column>
              <el-table-column label="Token" width="110" align="right">
                <template #default="{ row }">{{ formatInteger(row.totalTokens) }}</template>
              </el-table-column>
              <el-table-column label="成本" width="115" align="right">
                <template #default="{ row }">{{ formatUsd(row.totalCostUsd) }}</template>
              </el-table-column>
            </el-table>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="模型价格" name="pricing">
        <section class="surface table-surface pricing-surface">
          <div class="surface-head surface-head--actions">
            <div>
              <h3>价格版本</h3>
              <p>价格按生效时间匹配，历史尝试会固化当时的单价与成本</p>
            </div>
            <el-button v-hasPermi="['ops:openrouter:edit']" type="primary" :icon="Plus" @click="openPriceEditor()">
              新增价格
            </el-button>
          </div>
          <el-table :data="pricing" size="small" empty-text="尚未配置模型价格，成本将显示为未定价">
            <el-table-column prop="providerKey" label="供应商" min-width="140" />
            <el-table-column prop="modelPattern" label="模型匹配" min-width="180" show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="100" />
            <el-table-column label="输入 / 1M" width="125" align="right">
              <template #default="{ row }">{{ formatPrice(row.inputUsdPerMillionTokens) }}</template>
            </el-table-column>
            <el-table-column label="输出 / 1M" width="125" align="right">
              <template #default="{ row }">{{ formatPrice(row.outputUsdPerMillionTokens) }}</template>
            </el-table-column>
            <el-table-column label="生效时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.effectiveFrom) }}</template>
            </el-table-column>
            <el-table-column label="失效时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.effectiveTo) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled === false ? 'info' : 'success'" size="small" effect="plain">
                  {{ row.enabled === false ? '停用' : '启用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button v-hasPermi="['ops:openrouter:edit']" link type="primary" @click="openPriceEditor(row)">
                  编辑
                </el-button>
                <el-button
                  v-hasPermi="['ops:openrouter:delete', 'ops:openrouter:edit']"
                  link
                  type="danger"
                  @click="removePrice(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="priceDialogVisible" :title="priceForm.id ? '编辑模型价格' : '新增模型价格'" width="620px" destroy-on-close>
      <el-form ref="priceFormRef" :model="priceForm" :rules="priceRules" label-position="top">
        <div class="form-grid">
          <el-form-item label="供应商标识" prop="providerKey">
            <el-select v-model="priceForm.providerKey" filterable allow-create default-first-option placeholder="例如 openrouter">
              <el-option v-for="key in providerOptions" :key="key" :label="key" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型匹配" prop="modelPattern">
            <el-input v-model="priceForm.modelPattern" placeholder="精确模型名或通配符，例如 gpt-4o-*" />
          </el-form-item>
          <el-form-item label="价格版本" prop="version">
            <el-input v-model="priceForm.version" placeholder="例如 2026-07" />
          </el-form-item>
          <el-form-item label="币种">
            <el-select v-model="priceForm.currency" disabled>
              <el-option label="USD" value="USD" />
            </el-select>
          </el-form-item>
          <el-form-item label="输入价格 / 1M Token" prop="inputUsdPerMillionTokens">
            <el-input-number v-model="priceForm.inputUsdPerMillionTokens" :min="0" :precision="6" :step="0.1" controls-position="right" />
          </el-form-item>
          <el-form-item label="输出价格 / 1M Token" prop="outputUsdPerMillionTokens">
            <el-input-number v-model="priceForm.outputUsdPerMillionTokens" :min="0" :precision="6" :step="0.1" controls-position="right" />
          </el-form-item>
          <el-form-item label="生效时间" prop="effectiveFrom">
            <el-date-picker v-model="priceForm.effectiveFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择生效时间" />
          </el-form-item>
          <el-form-item label="失效时间">
            <el-date-picker v-model="priceForm.effectiveTo" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="长期有效可留空" clearable />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="priceForm.note" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="priceForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceDialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" :loading="priceSaving" @click="submitPrice">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { init, use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  Check,
  Coin,
  DataAnalysis,
  Plus,
  Stopwatch,
  Tickets,
  Warning
} from '@element-plus/icons-vue'
import {
  deleteGenerationModelPricing,
  saveGenerationModelPricing
} from '@/api/jiugai/dashboard'

use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  data: {
    type: Object,
    default: () => ({})
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['refresh'])
const { proxy } = getCurrentInstance()

const activeTab = ref('overview')
const latencyRef = ref(null)
const costRef = ref(null)
const priceDialogVisible = ref(false)
const priceSaving = ref(false)
const priceFormRef = ref(null)
const priceForm = reactive(emptyPriceForm())

let latencyChart
let costChart

const summary = computed(() => props.data?.summary || {})
const latencyTrend = computed(() => props.data?.latencyTrend || [])
const providerStats = computed(() => props.data?.providerStats || [])
const modelStats = computed(() => props.data?.modelStats || [])
const characterStats = computed(() => props.data?.characterStats || [])
const errorStats = computed(() => props.data?.errorStats || [])
const routeHealth = computed(() => props.data?.routeHealth || [])
const pricing = computed(() => props.data?.pricing || [])
const unpricedAttempts = computed(() => Number(summary.value.unpricedAttempts || 0))

const providerOptions = computed(() => {
  const keys = new Set(providerStats.value.map((item) => item.providerKey).filter(Boolean))
  pricing.value.forEach((item) => item.providerKey && keys.add(item.providerKey))
  return Array.from(keys).sort()
})

const coverageText = computed(() => {
  const total = Number(summary.value.totalAttempts || 0)
  if (!total) return '等待新的生成样本'
  if (unpricedAttempts.value > 0) return `${unpricedAttempts.value} 次尝试尚未定价`
  return `${total} 次尝试已纳入观测`
})

const summaryItems = computed(() => [
  {
    key: 'attempts',
    label: '生成尝试',
    value: formatInteger(summary.value.totalAttempts),
    detail: `成功 ${formatInteger(summary.value.successAttempts)} / 失败 ${formatInteger(summary.value.failedAttempts)}`,
    icon: Tickets,
    tone: 'blue'
  },
  {
    key: 'success',
    label: '尝试成功率',
    value: formatRate(summary.value.successRate),
    detail: `取消 ${formatInteger(summary.value.cancelledAttempts)}`,
    icon: DataAnalysis,
    tone: 'green'
  },
  {
    key: 'ttft',
    label: 'P95 首 Token',
    value: formatDuration(summary.value.p95TtftMs),
    detail: `平均 ${formatDuration(summary.value.avgTtftMs)}`,
    icon: Stopwatch,
    tone: 'amber'
  },
  {
    key: 'duration',
    label: 'P95 完整响应',
    value: formatDuration(summary.value.p95DurationMs),
    detail: `平均 ${formatDuration(summary.value.avgDurationMs)}`,
    icon: Stopwatch,
    tone: 'slate'
  },
  {
    key: 'tokens',
    label: 'Token',
    value: formatInteger(totalTokens(summary.value)),
    detail: estimatedTokenDetail(summary.value),
    icon: DataAnalysis,
    tone: 'cyan'
  },
  {
    key: 'cost',
    label: '已定价成本',
    value: formatUsd(summary.value.totalCostUsd),
    detail: costCoverageDetail(summary.value),
    icon: Coin,
    tone: 'gold'
  },
  {
    key: 'unpriced',
    label: '未定价尝试',
    value: formatInteger(summary.value.unpricedAttempts),
    detail: unpricedAttempts.value ? '配置价格后新请求开始计费' : '价格覆盖正常',
    icon: Warning,
    tone: unpricedAttempts.value ? 'red' : 'green'
  }
])

const priceRules = {
  providerKey: [{ required: true, message: '请输入供应商标识', trigger: 'blur' }],
  modelPattern: [{ required: true, message: '请输入模型匹配规则', trigger: 'blur' }],
  version: [{ required: true, message: '请输入价格版本', trigger: 'blur' }],
  inputUsdPerMillionTokens: [{ required: true, message: '请输入输入价格', trigger: 'change' }],
  outputUsdPerMillionTokens: [{ required: true, message: '请输入输出价格', trigger: 'change' }],
  effectiveFrom: [{ required: true, message: '请选择生效时间', trigger: 'change' }]
}

function emptyPriceForm() {
  return {
    id: null,
    providerKey: '',
    modelPattern: '',
    version: '',
    currency: 'USD',
    inputUsdPerMillionTokens: 0,
    outputUsdPerMillionTokens: 0,
    effectiveFrom: '',
    effectiveTo: '',
    enabled: true,
    note: ''
  }
}

function totalTokens(value) {
  const prompt = Number(value?.promptTokens || 0)
  const completion = Number(value?.completionTokens || 0)
  return prompt + completion
}

function estimatedTokenDetail(value) {
  const count = Number(value?.estimatedTokenAttempts || 0)
  return count > 0 ? `${count} 次包含明确标记的估算值` : '仅汇总已采集用量'
}

function costCoverageDetail(value) {
  const priced = formatInteger(value?.pricedAttempts)
  const partial = Number(value?.partialCostAttempts || 0)
  return partial > 0 ? `已定价 ${priced} 次，其中 ${partial} 次为部分成本` : `已定价 ${priced} 次`
}

function formatInteger(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(number)
}

function formatRate(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  const normalized = number > 1 ? number : number * 100
  return `${normalized.toFixed(normalized < 10 ? 1 : 0)}%`
}

function formatDuration(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0) return '-'
  if (number >= 1000) return `${(number / 1000).toFixed(number >= 10000 ? 1 : 2)} s`
  return `${Math.round(number)} ms`
}

function formatUsd(value) {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  return `$${number.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 })}`
}

function formatPrice(value) {
  const number = Number(value)
  return Number.isFinite(number) ? `$${number.toFixed(6).replace(/0+$/, '').replace(/\.$/, '')}` : '-'
}

function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').replace(/\.\d+$/, '')
}

function sortBySuccessRate(a, b) {
  return Number(a?.successRate || 0) - Number(b?.successRate || 0)
}

function rateClass(value) {
  const number = Number(value || 0)
  const rate = number > 1 ? number : number * 100
  if (rate >= 98) return 'rate rate--good'
  if (rate >= 90) return 'rate rate--warn'
  return 'rate rate--bad'
}

function healthLabel(row) {
  if (row?.circuitOpenUntil) return '熔断中'
  const status = String(row?.lastHealthStatus || '').toLowerCase()
  if (['ok', 'healthy', 'success'].includes(status)) return '正常'
  if (!status || status === 'unknown') return '未知'
  return row.lastHealthStatus
}

function healthTagType(row) {
  if (row?.circuitOpenUntil) return 'danger'
  const status = String(row?.lastHealthStatus || '').toLowerCase()
  return ['ok', 'healthy', 'success'].includes(status) ? 'success' : 'info'
}

function renderCharts() {
  nextTick(() => {
    renderLatencyChart()
    renderCostChart()
  })
}

function renderLatencyChart() {
  if (!latencyRef.value || !latencyTrend.value.length) return
  latencyChart = latencyChart || init(latencyRef.value)
  latencyChart.setOption({
    animationDuration: 320,
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${Math.round(Number(value || 0))} ms` },
    legend: { top: 0, textStyle: { color: '#64748b' } },
    grid: { left: 14, right: 14, top: 42, bottom: 12, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: latencyTrend.value.map((item) => item.date || item.dayKey),
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#e2e8f0' } }
    },
    series: [
      {
        name: '完整响应',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: latencyTrend.value.map((item) => item.avgDurationMs),
        lineStyle: { width: 2, color: '#2563eb' },
        itemStyle: { color: '#2563eb' }
      },
      {
        name: '首 Token',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: latencyTrend.value.map((item) => item.avgTtftMs),
        lineStyle: { width: 2, color: '#0f766e' },
        itemStyle: { color: '#0f766e' }
      }
    ]
  })
}

function renderCostChart() {
  if (!costRef.value || !latencyTrend.value.length) return
  costChart = costChart || init(costRef.value)
  costChart.setOption({
    animationDuration: 320,
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#64748b' } },
    grid: { left: 14, right: 14, top: 42, bottom: 12, containLabel: true },
    xAxis: {
      type: 'category',
      data: latencyTrend.value.map((item) => item.date || item.dayKey),
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: [
      {
        type: 'value',
        axisLabel: { color: '#64748b', formatter: (value) => formatCompactNumber(value) },
        splitLine: { lineStyle: { color: '#e2e8f0' } }
      },
      {
        type: 'value',
        axisLabel: { color: '#a16207', formatter: (value) => `$${value}` },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: 'Token',
        type: 'bar',
        barMaxWidth: 18,
        data: latencyTrend.value.map((item) => item.totalTokens || 0),
        itemStyle: { color: '#0891b2', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '成本 USD',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        data: latencyTrend.value.map((item) => Number(item.totalCostUsd || 0)),
        lineStyle: { width: 2, color: '#ca8a04' },
        itemStyle: { color: '#ca8a04' }
      }
    ]
  })
}

function formatCompactNumber(value) {
  const number = Number(value || 0)
  if (Math.abs(number) >= 1000000) return `${(number / 1000000).toFixed(1)}M`
  if (Math.abs(number) >= 1000) return `${(number / 1000).toFixed(1)}K`
  return String(Math.round(number))
}

function openPriceEditor(row) {
  Object.assign(priceForm, emptyPriceForm(), row || {})
  if (row) {
    priceForm.inputUsdPerMillionTokens = Number(row.inputUsdPerMillionTokens || 0)
    priceForm.outputUsdPerMillionTokens = Number(row.outputUsdPerMillionTokens || 0)
    priceForm.enabled = row.enabled !== false
  }
  priceDialogVisible.value = true
  nextTick(() => priceFormRef.value?.clearValidate())
}

function submitPrice() {
  priceFormRef.value?.validate((valid) => {
    if (!valid || priceSaving.value) return
    priceSaving.value = true
    const payload = {
      ...priceForm,
      providerKey: priceForm.providerKey.trim(),
      modelPattern: priceForm.modelPattern.trim(),
      version: priceForm.version.trim(),
      effectiveTo: priceForm.effectiveTo || null,
      note: priceForm.note.trim()
    }
    saveGenerationModelPricing(payload)
      .then(() => {
        proxy.$modal.msgSuccess('模型价格已保存')
        priceDialogVisible.value = false
        emit('refresh')
      })
      .catch((error) => proxy.$modal.msgError(error?.message || '保存模型价格失败'))
      .finally(() => {
        priceSaving.value = false
      })
  })
}

function removePrice(row) {
  if (!row?.id) return
  proxy.$modal
    .confirm(`确认删除 ${row.providerKey} / ${row.modelPattern} 的价格版本吗？`)
    .then(() => deleteGenerationModelPricing(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('模型价格已删除')
      emit('refresh')
    })
    .catch((error) => {
      if (error && error !== 'cancel' && error !== 'close') {
        proxy.$modal.msgError(error?.message || '删除模型价格失败')
      }
    })
}

function handleResize() {
  latencyChart?.resize()
  costChart?.resize()
}

watch(() => props.data, renderCharts, { deep: true })
watch(activeTab, (value) => {
  if (value === 'overview') renderCharts()
})

onMounted(() => {
  renderCharts()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  latencyChart?.dispose()
  costChart?.dispose()
})
</script>

<style scoped lang="scss">
.generation-ops {
  --ops-ink: #172033;
  --ops-muted: #64748b;
  --ops-border: #dbe3ec;
  --ops-surface: #ffffff;
  margin-bottom: 18px;
  padding: 20px;
  border: 1px solid var(--ops-border);
  border-radius: 8px;
  background: #f7f9fc;

  .ops-header,
  .title-line,
  .surface-head,
  .coverage {
    display: flex;
    align-items: center;
  }

  .ops-header {
    justify-content: space-between;
    gap: 18px;
    margin-bottom: 16px;
  }

  .title-line {
    gap: 9px;
    color: var(--ops-ink);
  }

  h2,
  h3,
  p {
    margin: 0;
  }

  h2 {
    font-size: 20px;
    line-height: 1.25;
  }

  h3 {
    color: var(--ops-ink);
    font-size: 15px;
    line-height: 1.35;
  }

  .ops-header p,
  .surface-head p {
    margin-top: 5px;
    color: var(--ops-muted);
    font-size: 12px;
    line-height: 1.5;
  }

  .coverage {
    flex: 0 0 auto;
    gap: 8px;
    min-height: 32px;
    padding: 0 11px;
    border: 1px solid #bbf7d0;
    border-radius: 6px;
    background: #f0fdf4;
    color: #166534;
    font-size: 12px;
  }

  .coverage--warn {
    border-color: #fde68a;
    background: #fffbeb;
    color: #92400e;
  }

  .coverage-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: currentColor;
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(7, minmax(0, 1fr));
    border: 1px solid var(--ops-border);
    border-radius: 8px;
    overflow: hidden;
    background: var(--ops-surface);
  }

  .summary-item {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    min-width: 0;
    min-height: 112px;
    padding: 15px 13px;
    border-right: 1px solid var(--ops-border);

    &:last-child {
      border-right: 0;
    }
  }

  .summary-icon {
    display: grid;
    flex: 0 0 30px;
    width: 30px;
    height: 30px;
    place-items: center;
    border-radius: 6px;
    background: #eff6ff;
    color: #2563eb;
  }

  .summary-icon--green { background: #ecfdf5; color: #047857; }
  .summary-icon--amber { background: #fffbeb; color: #b45309; }
  .summary-icon--slate { background: #f1f5f9; color: #475569; }
  .summary-icon--cyan { background: #ecfeff; color: #0e7490; }
  .summary-icon--gold { background: #fefce8; color: #a16207; }
  .summary-icon--red { background: #fef2f2; color: #b91c1c; }

  .summary-copy {
    min-width: 0;

    span,
    small {
      display: block;
      color: var(--ops-muted);
    }

    span {
      font-size: 12px;
    }

    strong {
      display: block;
      margin: 5px 0;
      overflow-wrap: anywhere;
      color: var(--ops-ink);
      font-size: 21px;
      line-height: 1.25;
    }

    small {
      font-size: 11px;
      line-height: 1.45;
    }
  }

  .ops-tabs {
    margin-top: 14px;
  }

  .chart-layout,
  .table-layout {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  .surface {
    min-width: 0;
    border: 1px solid var(--ops-border);
    border-radius: 8px;
    background: var(--ops-surface);
  }

  .surface-head {
    justify-content: space-between;
    gap: 12px;
    padding: 13px 15px;
    border-bottom: 1px solid #edf1f5;
  }

  .surface-head--actions {
    align-items: flex-start;
  }

  .ops-chart {
    width: 100%;
    height: 286px;
  }

  .error-surface,
  .route-surface {
    margin-top: 14px;
  }

  .rate {
    font-weight: 700;
  }

  .rate--good { color: #047857; }
  .rate--warn { color: #b45309; }
  .rate--bad { color: #b91c1c; }

  .form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 16px;
  }

  :deep(.el-tabs__header) {
    margin-bottom: 14px;
  }

  :deep(.el-table) {
    --el-table-header-bg-color: #f8fafc;
    --el-table-border-color: #edf1f5;
    color: #334155;
  }

  :deep(.el-input-number),
  :deep(.el-date-editor.el-input),
  :deep(.el-select) {
    width: 100%;
  }

  :deep(.el-dialog) {
    max-width: calc(100vw - 24px);
  }
}

@media (max-width: 1180px) {
  .generation-ops .summary-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));

    .summary-item {
      border-bottom: 1px solid var(--ops-border);
    }
  }
}

@media (max-width: 1100px) {
  .generation-ops {
    .chart-layout,
    .table-layout {
      grid-template-columns: 1fr;
    }
  }
}

@media (max-width: 760px) {
  .generation-ops .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .generation-ops {
    padding: 14px;

    .ops-header {
      align-items: flex-start;
      flex-direction: column;
    }

    .summary-grid,
    .form-grid {
      grid-template-columns: 1fr;
    }

    .summary-item {
      min-height: 92px;
      border-right: 0;
      border-bottom: 1px solid var(--ops-border);
    }
  }
}
</style>
