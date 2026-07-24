<template>
  <div class="app-container risk-page">
    <div class="summary">
      <div v-for="card in cards" :key="card.label">
        <span>{{ card.label }}</span>
        <b>{{ card.value }}</b>
      </div>
    </div>

    <el-form inline class="toolbar">
      <el-form-item>
        <el-input
          v-model="query.keyword"
          clearable
          maxlength="64"
          placeholder="clientUid / 用户 / IP"
          style="width: 280px"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="query.riskOnly" active-text="只看异常" @change="search" />
      </el-form-item>
      <el-button type="primary" icon="Search" @click="search">查询</el-button>
      <el-button icon="Refresh" @click="load">刷新</el-button>
      <el-button
        v-hasPermi="['ops:visitor-risk:delete']"
        type="danger"
        plain
        icon="Delete"
        :disabled="selectedIds.length === 0 || deleting"
        :loading="deleting"
        @click="deleteSelected()"
      >批量硬删除</el-button>
    </el-form>

    <el-table
      v-loading="loading"
      :data="rows"
      row-key="id"
      highlight-current-row
      @row-click="openEvents"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" align="center" />
      <el-table-column label="风险" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.riskScore ? 'danger' : 'success'">
            {{ scope.row.riskScore ? '关注' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="设备" prop="deviceToken" min-width="150" />
      <el-table-column label="clientUid" prop="clientUid" min-width="170" show-overflow-tooltip />
      <el-table-column label="用户" prop="userId" width="90" />
      <el-table-column label="IP" prop="ip" width="140" />
      <el-table-column label="设备环境" prop="userAgent" min-width="220" show-overflow-tooltip />
      <el-table-column label="匿名聊天" prop="anonymousChats" width="100" />
      <el-table-column label="最后活跃" prop="lastSeenAt" width="170" />
      <el-table-column label="24h事件" prop="riskScore" width="90" />
      <el-table-column label="操作" fixed="right" width="96">
        <template #default="scope">
          <el-button
            v-hasPermi="['ops:visitor-risk:delete']"
            link
            type="danger"
            icon="Delete"
            :loading="deletingId === scope.row.id"
            @click.stop="deleteSelected(scope.row)"
          >硬删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="query.pageNum"
      v-model:limit="query.pageSize"
      :page-sizes="[10, 20, 30, 50, 100, 500, 1000]"
      @pagination="load"
    />

    <el-drawer v-model="drawerVisible" title="设备事件时间线" :size="drawerSize">
      <div v-loading="eventLoading" class="event-list">
        <el-timeline v-if="events.length">
          <el-timeline-item
            v-for="event in events"
            :key="eventKey(event)"
            :timestamp="event.createdAt"
            placement="top"
          >
            <b>{{ eventLabels[event.eventType] || event.eventType }}</b>
            <div class="event-meta">{{ event.clientUid || '-' }} · {{ event.ip || '-' }}</div>
            <div v-if="event.endpointGroup" class="event-meta">{{ event.endpointGroup }}</div>
            <div v-if="event.detail" class="event-detail">{{ event.detail }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else-if="!eventLoading" description="暂无异常事件" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="JgVisitorRisk">
import {
  getVisitorRiskEvents,
  getVisitorRiskOverview,
  hardDeleteVisitorRisk,
  listVisitorRisk
} from '@/api/jiugai/visitorRisk'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const eventLoading = ref(false)
const rows = ref([])
const total = ref(0)
const overview = ref({})
const drawerVisible = ref(false)
const events = ref([])
const eventRequestId = ref(0)
const deleting = ref(false)
const deletingId = ref(null)
const selectedIds = ref([])
const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', riskOnly: false })
const drawerSize = window.innerWidth < 640 ? '92%' : '520px'

const eventLabels = {
  DEVICE_BOUND: '设备绑定',
  CLIENT_UID_CHANGED: '身份标识切换',
  USER_CHANGED: '登录账号切换',
  IP_CHANGED: '网络变化',
  RATE_LIMIT_HIT: '触发限流'
}

const cards = computed(() => [
  { label: '设备总数', value: overview.value.totalDevices || 0 },
  { label: '24h 活跃', value: overview.value.activeDay || 0 },
  { label: '24h 新设备', value: overview.value.newDay || 0 },
  { label: '限流命中', value: overview.value.limitHitsDay || 0 },
  { label: '需关注设备', value: overview.value.riskyDay || 0 }
])

async function load() {
  loading.value = true
  try {
    const [overviewResponse, listResponse] = await Promise.all([
      getVisitorRiskOverview(),
      listVisitorRisk(query)
    ])
    overview.value = overviewResponse.data || {}
    rows.value = listResponse.rows || []
    total.value = listResponse.total || 0
  } catch (error) {
    proxy.$modal.msgError(error?.msg || error?.message || '访客风险数据加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map((row) => row.id)
}

async function deleteSelected(row) {
  const ids = row?.id ? [row.id] : [...selectedIds.value]
  if (!ids.length) {
    proxy.$modal.msgWarning('请先勾选要删除的访客设备')
    return
  }
  const label = row?.id ? `设备 #${row.id}` : `选中的 ${ids.length} 个设备`
  try {
    await proxy.$modal.confirm(
      `确认永久删除${label}吗？对应安全事件、匿名次数和可信设备绑定都会清除；用户、会话和聊天消息不会删除。设备再次访问时会重新注册。此操作不可恢复。`
    )
  } catch (error) {
    return
  }

  deleting.value = true
  deletingId.value = row?.id || null
  try {
    const response = await hardDeleteVisitorRisk(ids)
    proxy.$modal.msgSuccess(response.msg || '硬删除完成')
    selectedIds.value = []
    drawerVisible.value = false
    events.value = []
    await load()
  } catch (error) {
    if (!isMessageBoxCancelled(error)) {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '硬删除访客设备失败'))
    }
  } finally {
    deleting.value = false
    deletingId.value = null
  }
}

async function openEvents(row) {
  const requestId = eventRequestId.value + 1
  eventRequestId.value = requestId
  events.value = []
  drawerVisible.value = true
  eventLoading.value = true
  try {
    const response = await getVisitorRiskEvents(row.id)
    if (eventRequestId.value === requestId) events.value = response.data || []
  } catch (error) {
    if (eventRequestId.value === requestId) {
      proxy.$modal.msgError(error?.msg || error?.message || '设备事件加载失败')
    }
  } finally {
    if (eventRequestId.value === requestId) eventLoading.value = false
  }
}

function eventKey(event) {
  return [event.createdAt, event.eventType, event.clientUid, event.endpointGroup].join('|')
}

onMounted(load)
</script>

<style scoped>
.risk-page { min-height: 100%; background: #f5f7f8; }
.summary { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.summary > div { padding: 16px; background: #fff; border: 1px solid #e1e7e9; border-left: 3px solid #2b7a78; border-radius: 5px; }
.summary span { display: block; color: #758188; font-size: 13px; }
.summary b { display: block; margin-top: 7px; color: #193f46; font-size: 26px; }
.toolbar { padding: 14px 16px 0; background: #fff; border: 1px solid #e1e7e9; border-bottom: 0; }
.event-list { min-height: 140px; padding-right: 12px; }
.event-meta { margin-top: 5px; color: #78868c; font-size: 13px; overflow-wrap: anywhere; }
.event-detail { margin-top: 8px; padding: 8px 10px; color: #53636a; background: #f4f7f8; border-radius: 4px; overflow-wrap: anywhere; }
@media (max-width: 1000px) { .summary { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
