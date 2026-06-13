<template>
  <div class="tag-library-page app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="标签库是发现页、详情页和角色编辑页统一的标签源。这里建议按题材、场景、关系、玩法、背景、来源、风格、成人向这些常用分类来维护。"
    />

    <div class="surface-guide">
      <div class="surface-card">
        <strong>发现推荐</strong>
        <span>优先出现在 H5 发现页顶部标签栏和标签弹窗前排，适合放高频运营标签。</span>
      </div>
      <div class="surface-card">
        <strong>发现展示</strong>
        <span>控制这个标签是否允许显示在发现页卡片和顶部筛选区。</span>
      </div>
      <div class="surface-card">
        <strong>详情展示</strong>
        <span>控制角色详情页和聊天前置信息区是否显示这个标签。</span>
      </div>
      <div class="surface-card">
        <strong>排序权重</strong>
        <span>数值越小越靠前，发现页推荐和标签弹窗都会参考这个顺序。</span>
      </div>
    </div>

    <el-form :model="queryParams" inline v-show="showSearch" class="query-form">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="标签编码 / 标签名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="queryParams.category" clearable placeholder="全部" style="width: 150px">
          <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="启用状态">
        <el-select v-model="queryParams.enabled" clearable placeholder="全部" style="width: 120px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="success" icon="Plus" @click="handleAdd">新建标签</el-button>
        <el-button type="warning" icon="RefreshRight" @click="handleSync">同步角色标签</el-button>
        <el-button type="danger" icon="Delete" :disabled="multiple" @click="handleBatchDelete">批量删除</el-button>
      </el-form-item>
    </el-form>

    <div class="preset-strip">
      <span class="preset-label">推荐预设：</span>
      <el-tag
        v-for="item in presetTags"
        :key="item.name"
        :style="{ borderColor: item.color, color: item.color }"
        effect="plain"
      >
        {{ item.name }}
      </el-tag>
    </div>

    <el-row :gutter="10" class="mb8 toolbar-row">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" class="tag-table" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="ID" prop="id" width="72" />
      <el-table-column label="标签名称" prop="name" min-width="120" show-overflow-tooltip />
      <el-table-column label="编码" prop="code" min-width="120" show-overflow-tooltip />
      <el-table-column label="分类" prop="category" width="110" />
      <el-table-column label="颜色" width="120">
        <template #default="scope">
          <div class="color-cell">
            <span class="color-dot" :style="{ background: scope.row.color || '#8b5cf6' }"></span>
            <span>{{ scope.row.color || '-' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="发现推荐" width="110">
        <template #default="scope">
          <el-switch
            v-model="scope.row.discoverRecommended"
            inline-prompt
            active-text="开"
            inactive-text="关"
            :loading="isSwitchLoading(scope.row, 'discoverRecommended')"
            @change="() => toggleRowSwitch(scope.row, 'discoverRecommended', '发现推荐')"
          />
        </template>
      </el-table-column>
      <el-table-column label="发现展示" width="110">
        <template #default="scope">
          <el-switch
            v-model="scope.row.discoverVisible"
            inline-prompt
            active-text="开"
            inactive-text="关"
            :loading="isSwitchLoading(scope.row, 'discoverVisible')"
            @change="() => toggleRowSwitch(scope.row, 'discoverVisible', '发现展示')"
          />
        </template>
      </el-table-column>
      <el-table-column label="详情展示" width="110">
        <template #default="scope">
          <el-switch
            v-model="scope.row.detailVisible"
            inline-prompt
            active-text="开"
            inactive-text="关"
            :loading="isSwitchLoading(scope.row, 'detailVisible')"
            @change="() => toggleRowSwitch(scope.row, 'detailVisible', '详情展示')"
          />
        </template>
      </el-table-column>
      <el-table-column label="会员限定" width="110">
        <template #default="scope">
          <el-switch
            v-model="scope.row.vipOnly"
            inline-prompt
            active-text="开"
            inactive-text="关"
            :loading="isSwitchLoading(scope.row, 'vipOnly')"
            @change="() => toggleRowSwitch(scope.row, 'vipOnly', '会员限定')"
          />
        </template>
      </el-table-column>
      <el-table-column label="成人向" width="100">
        <template #default="scope">
          <el-switch
            v-model="scope.row.adultOnly"
            inline-prompt
            active-text="开"
            inactive-text="关"
            :loading="isSwitchLoading(scope.row, 'adultOnly')"
            @change="() => toggleRowSwitch(scope.row, 'adultOnly', '成人向')"
          />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="100">
        <template #default="scope">
          <el-switch
            v-model="scope.row.enabled"
            inline-prompt
            active-text="开"
            inactive-text="关"
            :loading="isSwitchLoading(scope.row, 'enabled')"
            @change="() => toggleRowSwitch(scope.row, 'enabled', '启用状态')"
          />
        </template>
      </el-table-column>
      <el-table-column label="排序权重" prop="sortOrder" width="100" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
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

    <el-dialog :title="title" v-model="open" width="760px" append-to-body destroy-on-close>
      <el-alert
        class="mb12"
        type="info"
        :closable="false"
        show-icon
        title="标签编码可留空，系统会按标签名称自动生成；建议优先维护统一命名，避免“校园 / 校园向 / 校园恋爱”这类重复语义。"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标签名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签编码" prop="code">
              <el-input v-model="form.code" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标签分类">
              <el-select v-model="form.category" clearable style="width: 100%" placeholder="请选择">
                <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签颜色">
              <el-input v-model="form.color" placeholder="例如：#8b5cf6" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section">
          <div class="form-section-title">前台展示控制</div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="发现展示">
                <el-switch v-model="form.discoverVisible" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="发现推荐">
                <el-switch v-model="form.discoverRecommended" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="详情展示">
                <el-switch v-model="form.detailVisible" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-section">
          <div class="form-section-title">权限与状态</div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="会员限定">
                <el-switch v-model="form.vipOnly" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="成人向">
                <el-switch v-model="form.adultOnly" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="启用">
                <el-switch v-model="form.enabled" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <el-form-item label="排序权重">
          <div class="sort-row">
            <el-input-number v-model="form.sortOrder" :min="0" />
            <span class="sort-tip">数值越小越靠前，发现页标签栏、推荐位和详情标签都会参考这个顺序。</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">保存</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgTagLibrary">
import {
  addTagLibrary,
  batchDelTagLibrary,
  delTagLibrary,
  getTagLibrary,
  listTagLibrary,
  syncExistingTagLibrary,
  updateTagLibrary
} from '@/api/jiugai/taglibrary'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const categoryOptions = ['题材', '场景', '关系', '玩法', '背景', '来源', '风格', '成人向']
const presetTags = [
  { name: '奇幻', color: '#7c3aed' },
  { name: '校园', color: '#2563eb' },
  { name: '恋爱', color: '#ec4899' },
  { name: '冒险', color: '#f97316' },
  { name: '日常', color: '#14b8a6' },
  { name: '悬疑', color: '#475569' },
  { name: '科幻', color: '#0ea5e9' },
  { name: '古代', color: '#b45309' },
  { name: '治愈', color: '#22c55e' }
]

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const dataList = ref([])
const ids = ref([])
const multiple = ref(true)
const switchLoadingMap = reactive({})

const emptyForm = () => ({
  id: undefined,
  code: '',
  name: '',
  category: '',
  color: '',
  vipOnly: false,
  adultOnly: false,
  enabled: true,
  discoverVisible: true,
  discoverRecommended: false,
  detailVisible: true,
  sortOrder: 0
})

const data = reactive({
  form: emptyForm(),
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    category: undefined,
    enabled: undefined
  },
  rules: {
    name: [{ required: true, message: '标签名称不能为空', trigger: 'blur' }]
  }
})

const { form, queryParams, rules } = toRefs(data)

function normalizeRow(row = {}) {
  return {
    ...emptyForm(),
    ...row,
    vipOnly: !!row.vipOnly,
    adultOnly: !!row.adultOnly,
    enabled: row.enabled === undefined ? true : !!row.enabled,
    discoverVisible: row.discoverVisible === undefined ? true : !!row.discoverVisible,
    discoverRecommended: !!row.discoverRecommended,
    detailVisible: row.detailVisible === undefined ? true : !!row.detailVisible
  }
}

function toPayload(row = {}) {
  const normalized = normalizeRow(row)
  return {
    id: normalized.id,
    code: normalized.code,
    name: normalized.name,
    category: normalized.category,
    color: normalized.color,
    vipOnly: normalized.vipOnly,
    adultOnly: normalized.adultOnly,
    enabled: normalized.enabled,
    discoverVisible: normalized.discoverVisible,
    discoverRecommended: normalized.discoverRecommended,
    detailVisible: normalized.detailVisible,
    sortOrder: normalized.sortOrder
  }
}

function switchKey(row, field) {
  return `${row.id}:${field}`
}

function isSwitchLoading(row, field) {
  return !!switchLoadingMap[switchKey(row, field)]
}

function setSwitchLoading(row, field, value) {
  switchLoadingMap[switchKey(row, field)] = value
}

function getList() {
  loading.value = true
  listTagLibrary(queryParams.value)
    .then((res) => {
      dataList.value = (res.rows || []).map((item) => normalizeRow(item))
      total.value = res.total || 0
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '加载标签库失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.id)
  multiple.value = !ids.value.length
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.keyword = undefined
  queryParams.value.category = undefined
  queryParams.value.enabled = undefined
  handleQuery()
}

function handleAdd() {
  form.value = emptyForm()
  title.value = '新建标签'
  open.value = true
}

function handleUpdate(row) {
  getTagLibrary(row.id)
    .then((res) => {
      form.value = normalizeRow(res.data || {})
      title.value = '编辑标签'
      open.value = true
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '获取标签详情失败'))
    })
}

function toggleRowSwitch(row, field, label) {
  const previous = !row[field]
  setSwitchLoading(row, field, true)
  updateTagLibrary(toPayload(row))
    .then(() => {
      proxy.$modal.msgSuccess(`${label}已更新`)
    })
    .catch((error) => {
      row[field] = previous
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, `${label}更新失败`))
    })
    .finally(() => {
      setSwitchLoading(row, field, false)
    })
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    const action = form.value.id ? updateTagLibrary : addTagLibrary
    action(toPayload(form.value))
      .then(() => {
        proxy.$modal.msgSuccess('保存成功')
        open.value = false
        getList()
      })
      .catch((error) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '保存标签失败'))
      })
  })
}

function handleDelete(row) {
  proxy.$modal
    .confirm(`是否确认删除标签“${row.name}”？`)
    .then(() => delTagLibrary(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      getList()
    })
    .catch((error) => {
      if (isMessageBoxCancelled(error)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '删除标签失败'))
    })
}

function handleBatchDelete() {
  if (!ids.value.length) {
    proxy.$modal.msgWarning('请先勾选要删除的标签')
    return
  }
  proxy.$modal
    .confirm(`是否确认批量删除已选中的 ${ids.value.length} 个标签？`)
    .then(() => batchDelTagLibrary(ids.value))
    .then((res) => {
      proxy.$modal.msgSuccess(res.msg || '批量删除成功')
      ids.value = []
      multiple.value = true
      getList()
    })
    .catch((error) => {
      if (isMessageBoxCancelled(error)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '批量删除标签失败'))
    })
}

function handleSync() {
  syncExistingTagLibrary()
    .then((res) => {
      proxy.$modal.msgSuccess(res.msg || '同步完成')
      getList()
    })
    .catch((error) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(error, '同步角色标签失败'))
    })
}

getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.query-form {
  margin-top: 12px;
}

.toolbar-row {
  margin-top: 4px;
}

.surface-guide {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.surface-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 92px;
  padding: 16px 18px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(243, 244, 246, 0.92) 0%, #fff 100%);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.04);
}

.surface-card strong {
  font-size: 15px;
  color: #111827;
}

.surface-card span {
  font-size: 13px;
  line-height: 1.65;
  color: #6b7280;
}

.preset-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.preset-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.tag-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.tag-table :deep(.el-switch) {
  --el-switch-on-color: #7c3aed;
}

.color-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.color-dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.form-section {
  margin-bottom: 8px;
  padding: 16px 18px 4px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.form-section-title {
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 700;
  color: #475569;
}

.sort-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.sort-tip {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

@media (max-width: 1200px) {
  .surface-guide {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .surface-guide {
    grid-template-columns: 1fr;
  }
}
</style>
