<template>
  <div class="jg-dashboard app-container" v-loading="loading">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">SillySpringboot Admin Console</p>
        <h1>H5 酒馆运营大屏</h1>
        <p class="hero-text">
          这里集中查看角色、用户、当前有效会话、消息、生成请求、订单和公告等核心指标，方便运营快速判断酒馆当前是否稳定、是否在增长。
        </p>
      </div>
      <div class="hero-badge">
        <span>生成成功率</span>
        <strong>{{ successRateText }}</strong>
        <small>统计范围基于生成任务执行结果</small>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="card in metricCards" :key="card.key" class="metric-card">
        <span class="metric-label">{{ card.label }}</span>
        <strong class="metric-value">{{ card.value }}</strong>
        <span class="metric-tip">{{ card.tip }}</span>
      </article>
    </section>

    <section class="chart-grid">
      <el-card class="panel trend-panel" shadow="never">
        <template #header>
          <div class="panel-header panel-header--spread">
            <div>
              <span>生成趋势</span>
              <small>Success / Failure</small>
            </div>
            <el-radio-group v-model="trendRange" size="small" class="trend-actions" @change="load">
              <el-radio-button v-for="item in trendRangeOptions" :key="item.value" :label="item.value">
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <div ref="trendRef" class="chart"></div>
      </el-card>

      <el-card class="panel scope-panel" shadow="never">
        <template #header>
          <div class="panel-header">
            <span>角色结构</span>
            <small>系统卡 / 用户卡</small>
          </div>
        </template>
        <div ref="scopeRef" class="chart small"></div>
      </el-card>
    </section>

    <section class="list-grid">
      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-header">
            <span>活跃用户</span>
            <small>按当前有效角色会话排序</small>
          </div>
        </template>
        <div class="list-table">
          <div v-for="(row, index) in topActiveUsers" :key="String(row.userId || index)" class="list-row">
            <span class="rank">{{ index + 1 }}</span>
            <div class="list-main">
              <span class="main">{{ row.displayName || row.label || '用户' }}</span>
              <small v-if="row.subLabel" class="sub">{{ row.subLabel }}</small>
            </div>
            <span class="value">{{ row.conversationCount || 0 }}</span>
          </div>
          <el-empty v-if="!topActiveUsers.length" description="暂无数据" :image-size="72" />
        </div>
      </el-card>

      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-header">
            <span>热门角色</span>
            <small>按真实点赞与收藏排序</small>
          </div>
        </template>
        <div class="list-table">
          <div v-for="row in hotCharacters" :key="row.id" class="list-row dual">
            <div class="hot-main">
              <strong class="main">{{ row.name }}</strong>
              <small class="sub">{{ row.privateCard ? '用户卡' : '系统卡' }}</small>
            </div>
            <div class="hot-metrics">
              <span class="metric-chip metric-chip--like">赞 {{ row.likeCount || 0 }}</span>
              <span class="metric-chip">收藏 {{ row.favoriteCount || 0 }}</span>
              <span class="metric-chip metric-chip--muted">踩 {{ row.dislikeCount || 0 }}</span>
            </div>
          </div>
          <el-empty v-if="!hotCharacters.length" description="暂无数据" :image-size="72" />
        </div>
      </el-card>
    </section>

    <section class="list-grid">
      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-header">
            <span>用户创作分布</span>
            <small>Top 8 创作者</small>
          </div>
        </template>
        <div class="summary-strip">
          <div>
            <span class="summary-label">用户卡总量</span>
            <strong>{{ ownerSummary.totalUserCreated || 0 }}</strong>
          </div>
          <div>
            <span class="summary-label">创作者数量</span>
            <strong>{{ ownerSummary.ownerCount || 0 }}</strong>
          </div>
        </div>
        <div class="list-table">
          <div v-for="row in ownerTopList" :key="(row.ownerDisplayName || '') + (row.ownerClientUid || '')" class="list-row">
            <div class="list-main">
              <span class="main">{{ row.ownerDisplayName || row.ownerClientUid || '未命名用户' }}</span>
              <small v-if="row.ownerSubLabel" class="sub">{{ row.ownerSubLabel }}</small>
            </div>
            <span class="value">{{ row.count || 0 }}</span>
          </div>
          <el-empty v-if="!ownerTopList.length" description="暂无数据" :image-size="72" />
        </div>
      </el-card>

      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-header">
            <span>最新公告</span>
            <small>后台公告与首页联动</small>
          </div>
        </template>
        <div class="notice-list">
          <div v-for="row in latestNotices" :key="row.id" class="notice-row">
            <div>
              <strong>{{ row.title }}</strong>
              <small>{{ row.createTime }}</small>
            </div>
            <el-tag size="small" :type="row.level === 'warn' ? 'warning' : 'success'">
              {{ row.level || 'info' }}
            </el-tag>
          </div>
          <el-empty v-if="!latestNotices.length" description="暂无公告" :image-size="72" />
        </div>
      </el-card>
    </section>
  </div>
</template>

<script setup name="Index">
import * as echarts from 'echarts'
import { getJgDashboardOverview } from '@/api/jiugai/dashboard'

const { proxy } = getCurrentInstance()

const loading = ref(true)
const trendRef = ref(null)
const scopeRef = ref(null)
const trendRange = ref('14d')
const trendRangeOptions = Object.freeze([
  { label: '全部', value: 'all' },
  { label: '1年', value: '365d' },
  { label: '半年', value: '180d' },
  { label: '1个月', value: '30d' },
  { label: '14天', value: '14d' }
])

const metrics = reactive({
  totalCharacters: 0,
  systemCharacters: 0,
  userCharacters: 0,
  totalUsers: 0,
  totalConversations: 0,
  activeConversations7d: 0,
  totalMessages: 0,
  totalTasks: 0,
  totalPaidOrders: 0,
  totalRevenueYuan: '0.00',
  successRate: 0
})

const topActiveUsers = ref([])
const hotCharacters = ref([])
const latestNotices = ref([])
const generationTrend = ref([])
const ownerSummary = ref({})

let trendChart
let scopeChart

const metricCards = computed(() => [
  {
    key: 'characters',
    label: '角色总量',
    value: metrics.totalCharacters,
    tip: `系统 ${metrics.systemCharacters} / 用户 ${metrics.userCharacters}`
  },
  {
    key: 'users',
    label: '用户总量',
    value: metrics.totalUsers,
    tip: '按当前 app_user 统计'
  },
  {
    key: 'conversations',
    label: '当前有效角色会话',
    value: metrics.totalConversations,
    tip: `近 7 天活跃 ${metrics.activeConversations7d}`
  },
  {
    key: 'messages',
    label: '消息总量',
    value: metrics.totalMessages,
    tip: '业务事实消息表'
  },
  {
    key: 'tasks',
    label: '生成任务',
    value: metrics.totalTasks,
    tip: '包含发送、续写、重生'
  },
  {
    key: 'orders',
    label: '已支付订单',
    value: metrics.totalPaidOrders,
    tip: '模拟支付和后续真实支付共用'
  },
  {
    key: 'revenue',
    label: '累计流水',
    value: `￥ ${metrics.totalRevenueYuan}`,
    tip: '当前以支付成功订单汇总'
  }
])

const successRateText = computed(() => `${Math.round((metrics.successRate || 0) * 100)}%`)
const ownerTopList = computed(() => ownerSummary.value?.topOwners || [])

function load() {
  loading.value = true
  getJgDashboardOverview(trendRange.value)
    .then((res) => {
      const data = res.data || {}
      Object.assign(metrics, data.metrics || {})
      generationTrend.value = data.generationTrend || []
      topActiveUsers.value = data.topActiveUsers || []
      hotCharacters.value = data.hotCharacters || []
      latestNotices.value = data.latestNotices || []
      ownerSummary.value = data.userCreatedStats || {}
      nextTick(() => {
        renderTrendChart()
        renderScopeChart()
      })
    })
    .catch((error) => {
      proxy.$modal.msgError(error?.message || '加载大屏数据失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function renderTrendChart() {
  if (!trendRef.value) return
  trendChart = trendChart || echarts.init(trendRef.value)
  const dates = generationTrend.value.map((item) => item.date)
  const success = generationTrend.value.map((item) => item.successCount || 0)
  const failure = generationTrend.value.map((item) => item.failureCount || 0)
  trendChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      textStyle: { color: '#6b7280' }
    },
    grid: { left: 12, right: 12, top: 48, bottom: 12, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#d1d5db' } },
      axisLabel: { color: '#6b7280' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } },
      axisLabel: { color: '#6b7280' }
    },
    series: [
      {
        name: 'Success',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: success,
        lineStyle: { width: 3, color: '#059669' },
        itemStyle: { color: '#059669' },
        areaStyle: { color: 'rgba(5, 150, 105, 0.10)' }
      },
      {
        name: 'Failure',
        type: 'bar',
        barMaxWidth: 18,
        data: failure,
        itemStyle: { color: '#f97316', borderRadius: [6, 6, 0, 0] }
      }
    ]
  })
}

function renderScopeChart() {
  if (!scopeRef.value) return
  scopeChart = scopeChart || echarts.init(scopeRef.value)
  scopeChart.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['54%', '78%'],
        label: {
          color: '#475569',
          formatter: '{b}\n{c}'
        },
        data: [
          { name: '系统卡', value: metrics.systemCharacters || 0, itemStyle: { color: '#2563eb' } },
          { name: '用户卡', value: metrics.userCharacters || 0, itemStyle: { color: '#f97316' } }
        ]
      }
    ]
  })
}

function handleResize() {
  trendChart?.resize()
  scopeChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  scopeChart?.dispose()
})
</script>

<style scoped lang="scss">
.jg-dashboard {
  --panel-bg: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  --hero-bg: linear-gradient(135deg, #111827 0%, #1d4ed8 42%, #0f766e 100%);
  --hero-text: rgba(255, 255, 255, 0.82);
  --line: rgba(255, 255, 255, 0.12);
  --shadow: 0 20px 40px rgba(15, 23, 42, 0.10);

  .hero {
    display: grid;
    grid-template-columns: 1.8fr 0.8fr;
    gap: 18px;
    margin-bottom: 18px;
    padding: 28px;
    border-radius: 24px;
    background: var(--hero-bg);
    box-shadow: var(--shadow);
    color: #fff;
  }

  .eyebrow {
    margin: 0 0 10px;
    font-size: 12px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: rgba(255, 255, 255, 0.72);
  }

  h1 {
    margin: 0;
    font-size: 34px;
    font-weight: 700;
  }

  .hero-text {
    margin: 14px 0 0;
    max-width: 720px;
    line-height: 1.7;
    color: var(--hero-text);
  }

  .hero-badge {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 10px;
    padding: 20px;
    border: 1px solid var(--line);
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.08);
    backdrop-filter: blur(10px);

    span,
    small {
      color: rgba(255, 255, 255, 0.72);
    }

    strong {
      font-size: 40px;
      font-weight: 700;
    }
  }

  .metric-grid {
    display: grid;
    grid-template-columns: repeat(7, minmax(0, 1fr));
    gap: 14px;
    margin-bottom: 18px;
  }

  .metric-card {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 18px;
    border-radius: 20px;
    background: var(--panel-bg);
    border: 1px solid rgba(148, 163, 184, 0.16);
    box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
  }

  .metric-label {
    color: #64748b;
    font-size: 13px;
  }

  .metric-value {
    font-size: 28px;
    color: #0f172a;
  }

  .metric-tip {
    color: #94a3b8;
    font-size: 12px;
    line-height: 1.5;
  }

  .chart-grid,
  .list-grid {
    display: grid;
    grid-template-columns: 1.5fr 1fr;
    gap: 18px;
    margin-bottom: 18px;
  }

  .panel {
    border-radius: 22px;
    border: 1px solid rgba(148, 163, 184, 0.16);
    background: var(--panel-bg);
    box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
  }

  .panel-header {
    display: flex;
    align-items: baseline;
    justify-content: space-between;

    span {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
    }

    small {
      color: #94a3b8;
    }
  }

  .panel-header--spread {
    align-items: center;
    gap: 12px;
  }

  .trend-actions {
    flex-shrink: 0;
  }

  .chart {
    height: 340px;
  }

  .chart.small {
    height: 280px;
  }

  .list-table,
  .notice-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-height: 180px;
  }

  .list-row,
  .notice-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    padding: 12px 14px;
    border-radius: 14px;
    background: rgba(248, 250, 252, 0.9);
    border: 1px solid rgba(148, 163, 184, 0.12);
  }

  .list-row.dual {
    align-items: flex-start;
  }

  .rank {
    width: 24px;
    color: #2563eb;
    font-weight: 700;
  }

  .list-main {
    min-width: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .main {
    flex: 1;
    min-width: 0;
    color: #0f172a;
    word-break: break-all;
  }

  .hot-main {
    min-width: 0;
    flex: 1;
  }

  .sub {
    display: block;
    margin-top: 4px;
    color: #94a3b8;
  }

  .value {
    color: #0f766e;
    font-weight: 700;
  }

  .hot-metrics {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 8px;
  }

  .metric-chip {
    display: inline-flex;
    align-items: center;
    height: 28px;
    padding: 0 10px;
    border-radius: 999px;
    background: rgba(79, 70, 229, 0.08);
    color: #4f46e5;
    font-size: 12px;
    font-weight: 600;
  }

  .metric-chip--like {
    background: rgba(236, 72, 153, 0.10);
    color: #db2777;
  }

  .metric-chip--muted {
    background: rgba(148, 163, 184, 0.14);
    color: #64748b;
  }

  .summary-strip {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin-bottom: 12px;

    > div {
      padding: 14px;
      border-radius: 14px;
      background: rgba(239, 246, 255, 0.85);
    }

    strong {
      display: block;
      margin-top: 6px;
      font-size: 24px;
      color: #0f172a;
    }
  }

  .summary-label {
    color: #64748b;
    font-size: 13px;
  }

  .notice-row strong {
    display: block;
    color: #0f172a;
  }

  .notice-row small {
    display: block;
    margin-top: 4px;
    color: #94a3b8;
  }
}

@media (max-width: 1600px) {
  .jg-dashboard {
    .metric-grid {
      grid-template-columns: repeat(4, minmax(0, 1fr));
    }
  }
}

@media (max-width: 1280px) {
  .jg-dashboard {
    .metric-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }
}

@media (max-width: 960px) {
  .jg-dashboard {
    .hero,
    .chart-grid,
    .list-grid {
      grid-template-columns: 1fr;
    }

    .metric-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .panel-header--spread {
      align-items: flex-start;
      flex-direction: column;
    }
  }
}

@media (max-width: 640px) {
  .jg-dashboard {
    .hero {
      padding: 22px;
    }

    .metric-grid {
      grid-template-columns: 1fr;
    }

    h1 {
      font-size: 28px;
    }
  }
}
</style>
