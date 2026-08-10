<template>
  <div class="app-container illustration-key-page">
    <el-alert
      class="mb12"
      type="warning"
      :closable="false"
      show-icon
      title="这里管理插画网站 18+ 临时密钥。默认 10 分钟有效，用户输入密钥后才会请求并显示 R18 内容。"
    />

    <el-card class="key-generate-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>生成临时密钥</span>
          <el-tag type="danger" effect="plain">18+</el-tag>
        </div>
      </template>

      <el-form :model="generateForm" inline>
        <el-form-item label="有效分钟">
          <el-input-number v-model="generateForm.ttlMinutes" :min="1" :max="1440" controls-position="right" />
        </el-form-item>
        <el-form-item label="使用上限">
          <el-input-number v-model="generateForm.maxUses" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="generateForm.note" clearable placeholder="例如：群内临时放行" style="width: 260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Key" :loading="generating" @click="handleGenerate">生成密钥</el-button>
        </el-form-item>
      </el-form>

      <div v-if="latestKey.accessCode" class="latest-key">
        <div>
          <div class="latest-label">最新密钥</div>
          <div class="latest-code">{{ latestKey.accessCode }}</div>
          <div class="latest-meta">有效期至：{{ latestKey.expiresAt || '-' }}</div>
        </div>
        <el-button icon="DocumentCopy" @click="copyKey(latestKey.accessCode)">复制</el-button>
      </div>
    </el-card>

    <el-form :model="queryParams" inline v-show="showSearch" class="query-form">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          clearable
          placeholder="密钥 / 备注 / 创建人"
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.active" clearable placeholder="全部" style="width: 120px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" row-key="id">
      <el-table-column label="ID" prop="id" width="80" />
      <el-table-column label="密钥" min-width="180">
        <template #default="scope">
          <div class="code-cell">
            <span>{{ scope.row.accessCode }}</span>
            <el-button link type="primary" icon="DocumentCopy" @click="copyKey(scope.row.accessCode)">复制</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="分级" prop="contentLevel" width="90">
        <template #default="scope">
          <el-tag type="danger">{{ scope.row.contentLevel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="scope">
          <el-tag :type="scope.row.valid ? 'success' : 'info'">{{ scope.row.valid ? '有效' : '失效' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.active ? 'success' : 'info'">{{ scope.row.active ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="使用次数" width="110">
        <template #default="scope">
          {{ scope.row.usedCount || 0 }} / {{ scope.row.maxUses || '不限' }}
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="note" min-width="180" show-overflow-tooltip />
      <el-table-column label="创建人" prop="createdBy" width="120" show-overflow-tooltip />
      <el-table-column label="过期时间" prop="expiresAt" width="180" show-overflow-tooltip />
      <el-table-column label="创建时间" prop="createdAt" width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="danger"
            icon="Close"
            :disabled="!scope.row.active"
            @click="handleDisable(scope.row)"
          >
            停用
          </el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup name="JgIllustrationAccessKey">
import {
  deleteIllustrationAccessKey,
  disableIllustrationAccessKey,
  generateIllustrationAccessKey,
  listIllustrationAccessKey
} from '@/api/jiugai/illustrationAccessKey'
import { ElMessage, ElMessageBox } from 'element-plus'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const dataList = ref([])
const loading = ref(false)
const generating = ref(false)
const showSearch = ref(true)
const total = ref(0)
const latestKey = ref({})

const generateForm = reactive({
  ttlMinutes: 10,
  maxUses: 0,
  note: ''
})

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  active: undefined
})

function getList() {
  loading.value = true
  listIllustrationAccessKey(queryParams)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载临时密钥失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.keyword = undefined
  queryParams.active = undefined
  handleQuery()
}

function handleGenerate() {
  generating.value = true
  generateIllustrationAccessKey({
    ttlMinutes: generateForm.ttlMinutes || 10,
    maxUses: generateForm.maxUses || undefined,
    note: generateForm.note
  })
    .then((res) => {
      latestKey.value = res.data || {}
      proxy.$modal.msgSuccess('密钥已生成')
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '生成密钥失败'))
    })
    .finally(() => {
      generating.value = false
    })
}

async function handleDisable(row) {
  try {
    await ElMessageBox.confirm(`确认停用密钥 ${row.accessCode} 吗？`, '停用密钥', {
      confirmButtonText: '停用',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    if (isMessageBoxCancelled(e)) return
    throw e
  }

  disableIllustrationAccessKey(row.id)
    .then(() => {
      proxy.$modal.msgSuccess('密钥已停用')
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '停用密钥失败'))
    })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认硬删除密钥 ${row.accessCode} 吗？删除后输入这个密钥将立即失效。`, '删除密钥', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    if (isMessageBoxCancelled(e)) return
    throw e
  }

  deleteIllustrationAccessKey(row.id)
    .then(() => {
      proxy.$modal.msgSuccess('密钥已删除')
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除密钥失败'))
    })
}

async function copyKey(code) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

getList()
</script>

<style scoped>
.key-generate-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.latest-key {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
  padding: 16px;
  border: 1px solid #f3d19e;
  border-radius: 8px;
  background: #fdf6ec;
}

.latest-label {
  color: #909399;
  font-size: 12px;
}

.latest-code {
  margin-top: 4px;
  color: #303133;
  font-family: Consolas, Monaco, monospace;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 2px;
}

.latest-meta {
  margin-top: 4px;
  color: #606266;
  font-size: 13px;
}

.query-form {
  margin-top: 6px;
}

.code-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: Consolas, Monaco, monospace;
  font-weight: 700;
}
</style>
