<template>
  <div class="app-container">
    <el-alert class="mb12" type="info" :closable="false" show-icon title="每日签到是运营发放层：钻石/金币走钱包流水（CHECKIN），聊天/生图次数写入当日 bonus，不改动 VIP 配额底盘与用户隔离链路。" />
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="活动配置" name="config">
        <el-card shadow="never" v-loading="configLoading">
          <template #header>
            <div class="card-header">
              <div>
                <div class="card-title">签到活动</div>
                <div class="card-subtitle">保存后立即影响 H5 签到页</div>
              </div>
              <el-button type="primary" :loading="configSaving" v-hasPermi="['commerce:checkin:edit']" @click="submitConfig">保存配置</el-button>
            </div>
          </template>
          <el-form :model="form" label-width="120px">
            <el-row :gutter="20">
              <el-col :xs="24" :md="8"><el-form-item label="启用活动"><el-switch v-model="form.enabled" /></el-form-item></el-col>
              <el-col :xs="24" :md="16"><el-form-item label="活动名称"><el-input v-model="form.name" maxlength="64" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :xs="24" :md="12"><el-form-item label="开始时间"><el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable style="width:100%" /></el-form-item></el-col>
              <el-col :xs="24" :md="12"><el-form-item label="结束时间"><el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable style="width:100%" /></el-form-item></el-col>
            </el-row>
            <el-divider content-position="left">每日基础奖励</el-divider>
            <el-row :gutter="20">
              <el-col :xs="12" :md="6"><el-form-item label="钻石"><el-input-number v-model="form.rewardScore" :min="0" :max="999999" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :xs="12" :md="6"><el-form-item label="金币"><el-input-number v-model="form.rewardGold" :min="0" :max="999999" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :xs="12" :md="6"><el-form-item label="今日聊天+"><el-input-number v-model="form.rewardChatBonus" :min="0" :max="9999" controls-position="right" style="width:100%" /></el-form-item></el-col>
              <el-col :xs="12" :md="6"><el-form-item label="今日生图+"><el-input-number v-model="form.rewardImageBonus" :min="0" :max="9999" controls-position="right" style="width:100%" /></el-form-item></el-col>
            </el-row>
            <el-divider content-position="left">连续签到加赠</el-divider>
            <div class="streak-toolbar"><el-button type="primary" plain @click="addStreakRule" v-hasPermi="['commerce:checkin:edit']">新增规则</el-button></div>
            <el-table :data="form.streakRules" border>
              <el-table-column label="第 N 天" width="140"><template #default="scope"><el-input-number v-model="scope.row.day" :min="1" :max="365" controls-position="right" style="width:100%" /></template></el-table-column>
              <el-table-column label="额外钻石" min-width="120"><template #default="scope"><el-input-number v-model="scope.row.score" :min="0" :max="999999" controls-position="right" style="width:100%" /></template></el-table-column>
              <el-table-column label="额外金币" min-width="120"><template #default="scope"><el-input-number v-model="scope.row.gold" :min="0" :max="999999" controls-position="right" style="width:100%" /></template></el-table-column>
              <el-table-column label="今日聊天+" min-width="110"><template #default="scope"><el-input-number v-model="scope.row.chatBonus" :min="0" :max="9999" controls-position="right" style="width:100%" /></template></el-table-column>
              <el-table-column label="今日生图+" min-width="110"><template #default="scope"><el-input-number v-model="scope.row.imageBonus" :min="0" :max="9999" controls-position="right" style="width:100%" /></template></el-table-column>
              <el-table-column label="操作" width="90" align="center"><template #default="scope"><el-button link type="danger" @click="removeStreakRule(scope.$index)" v-hasPermi="['commerce:checkin:edit']">删除</el-button></template></el-table-column>
            </el-table>
            <el-form-item label="业务时区" class="mt16">
              <el-select v-model="form.timezone" filterable allow-create default-first-option style="max-width:280px">
                <el-option v-for="item in timezoneOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="备注"><el-input v-model="form.note" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="领取记录" name="claims">
        <el-form :model="queryParams" inline v-show="showSearch">
          <el-form-item label="用户ID"><el-input v-model="queryParams.keyword" clearable style="width:200px" @keyup.enter="handleQuery" /></el-form-item>
          <el-form-item label="业务日"><el-date-picker v-model="queryParams.bizDate" type="date" value-format="YYYY-MM-DD" clearable /></el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-row :gutter="10" class="mb8"><right-toolbar v-model:showSearch="showSearch" @queryTable="getClaims" /></el-row>
        <el-table v-loading="claimsLoading" :data="claimList" max-height="680">
          <el-table-column label="ID" prop="id" width="80" />
          <el-table-column label="用户ID" prop="userId" width="110" />
          <el-table-column label="业务日" prop="bizDate" width="120" />
          <el-table-column label="连续天数" prop="streakDay" width="100" />
          <el-table-column label="钻石" prop="rewardScore" width="90" />
          <el-table-column label="金币" prop="rewardGold" width="90" />
          <el-table-column label="聊天次数" prop="rewardChatBonus" width="100" />
          <el-table-column label="生图次数" prop="rewardImageBonus" width="100" />
          <el-table-column label="钱包幂等键" prop="ledgerIdempotencyKey" min-width="220" show-overflow-tooltip />
          <el-table-column label="领取时间" prop="createdAt" width="170" />
        </el-table>
        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getClaims" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup name="JgCheckin">
import { getCheckinActivity, saveCheckinActivity, listCheckinClaims } from '@/api/jiugai/checkin'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const activeTab = ref('config')
const configLoading = ref(false)
const configSaving = ref(false)
const claimsLoading = ref(false)
const showSearch = ref(true)
const claimList = ref([])
const total = ref(0)
const form = reactive({
  name: '每日签到',
  enabled: true,
  startAt: undefined,
  endAt: undefined,
  rewardScore: 10,
  rewardGold: 0,
  rewardChatBonus: 2,
  rewardImageBonus: 0,
  streakRules: [],
  timezone: 'Asia/Shanghai',
  note: ''
})
const queryParams = reactive({ pageNum: 1, pageSize: 10, keyword: undefined, bizDate: undefined })
const timezoneOptions = ['Asia/Shanghai', 'Asia/Hong_Kong', 'Asia/Tokyo', 'UTC']

function onTabChange(name) { if (name === 'claims') getClaims() }
function addStreakRule() {
  const used = new Set(form.streakRules.map((row) => Number(row.day)))
  const day = [3, 7, ...Array.from({ length: 365 }, (_, index) => index + 1)].find((value) => !used.has(value))
  if (!day) return proxy.$modal.msgError('最多只能配置 365 条连续签到规则')
  form.streakRules.push({ day, score: 5, gold: 0, chatBonus: 0, imageBonus: 0 })
}
function removeStreakRule(index) { form.streakRules.splice(index, 1) }
function applyActivity(data) {
  if (!data) return
  form.name = data.name || '每日签到'
  form.enabled = !!data.enabled
  form.startAt = data.startAt || undefined
  form.endAt = data.endAt || undefined
  form.rewardScore = Number(data.rewardScore || 0)
  form.rewardGold = Number(data.rewardGold || 0)
  form.rewardChatBonus = Number(data.rewardChatBonus || 0)
  form.rewardImageBonus = Number(data.rewardImageBonus || 0)
  form.streakRules = Array.isArray(data.streakRules) ? data.streakRules.map((row) => ({
    day: Number(row.day || 1), score: Number(row.score || 0), gold: Number(row.gold || 0), chatBonus: Number(row.chatBonus || 0), imageBonus: Number(row.imageBonus || 0)
  })) : []
  form.timezone = data.timezone || 'Asia/Shanghai'
  form.note = data.note || ''
}
function loadActivity() {
  configLoading.value = true
  getCheckinActivity().then((res) => applyActivity(res.data || res)).catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, 'load failed'))).finally(() => { configLoading.value = false })
}
function validateConfig() {
  if (!String(form.name || '').trim()) return '活动名称不能为空'
  if (form.startAt && form.endAt && form.startAt >= form.endAt) return '活动开始时间必须早于结束时间'
  if (![form.rewardScore, form.rewardGold, form.rewardChatBonus, form.rewardImageBonus].some((value) => Number(value) > 0)) {
    return '每日基础奖励不能全部为 0'
  }
  if (!String(form.timezone || '').trim()) return '业务时区不能为空'
  const days = new Set()
  for (const row of form.streakRules) {
    const day = Number(row.day)
    if (days.has(day)) return `第 ${day} 天存在重复规则`
    days.add(day)
    if (![row.score, row.gold, row.chatBonus, row.imageBonus].some((value) => Number(value) > 0)) {
      return `第 ${day} 天的额外奖励不能全部为 0`
    }
  }
  return ''
}
function submitConfig() {
  const validationMessage = validateConfig()
  if (validationMessage) return proxy.$modal.msgError(validationMessage)
  configSaving.value = true
  saveCheckinActivity({ ...form }).then((res) => { applyActivity(res.data || res); proxy.$modal.msgSuccess('签到配置已保存') }).catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存签到配置失败'))).finally(() => { configSaving.value = false })
}
function getClaims() {
  claimsLoading.value = true
  listCheckinClaims(queryParams).then((res) => { claimList.value = res.rows || []; total.value = res.total || 0 }).catch((error) => proxy.$modal.msgError(jiugaiRequestErrorMessage(error, 'load failed'))).finally(() => { claimsLoading.value = false })
}
function handleQuery() { queryParams.pageNum = 1; getClaims() }
function resetQuery() { queryParams.keyword = undefined; queryParams.bizDate = undefined; handleQuery() }
loadActivity()
</script>

<style scoped>
.mb12 { margin-bottom: 12px; }
.mb8 { margin-bottom: 8px; }
.mt16 { margin-top: 16px; }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-title { font-size: 16px; font-weight: 600; }
.card-subtitle { margin-top: 4px; color: #909399; font-size: 13px; }
.streak-toolbar { margin-bottom: 12px; }
</style>
