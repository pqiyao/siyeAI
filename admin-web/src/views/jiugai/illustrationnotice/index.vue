<template>
  <div class="app-container illustration-notice-page">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里管理插画网站自己的官方通知，和四叶原系统公告完全独立。"
    />

    <el-form v-show="showSearch" :model="queryParams" inline>
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          clearable
          placeholder="标题 / 内容 / 类型"
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="queryParams.category" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.enabled" clearable placeholder="全部" style="width: 120px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="success" icon="Plus" @click="handleAdd">新增通知</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" row-key="id">
      <el-table-column label="ID" prop="id" width="72" />
      <el-table-column label="标题" min-width="240" show-overflow-tooltip>
        <template #default="scope">
          <div class="notice-title-row">
            <span class="notice-title">{{ scope.row.title }}</span>
            <el-tag v-if="scope.row.important" type="danger" size="small">重要</el-tag>
          </div>
          <div class="notice-content-preview">{{ scope.row.content }}</div>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="110">
        <template #default="scope">
          <el-tag effect="plain">{{ categoryLabel(scope.row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" prop="typeLabel" width="100" />
      <el-table-column label="启用" width="86">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="80" />
      <el-table-column label="创建时间" prop="createdAt" width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />

    <el-dialog :title="dialogTitle" v-model="open" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" style="width: 100%" @change="handleCategoryChange">
                <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型文案" prop="typeLabel">
              <el-input v-model="form.typeLabel" placeholder="例如：审核 / 更新 / 规则" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="160" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="要点">
          <el-input
            v-model="form.pointsText"
            type="textarea"
            :rows="4"
            placeholder="一行一个要点，用户端会显示为列表"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="重要">
              <el-switch v-model="form.important" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确定</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgIllustrationNotice">
import {
  addIllustrationNotice,
  deleteIllustrationNotice,
  getIllustrationNotice,
  listIllustrationNotice,
  updateIllustrationNotice
} from '@/api/jiugai/illustrationNotice'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const categoryOptions = [
  { value: 'review', label: '审核相关', type: '审核' },
  { value: 'update', label: '网站更新', type: '更新' },
  { value: 'rule', label: '内容规则', type: '规则' }
]

const dataList = ref([])
const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const open = ref(false)
const dialogTitle = ref('')

const emptyForm = () => ({
  id: undefined,
  category: 'update',
  typeLabel: '更新',
  title: '',
  content: '',
  points: [],
  pointsText: '',
  important: false,
  enabled: true,
  sortOrder: 0
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    category: undefined,
    enabled: undefined
  },
  form: emptyForm(),
  rules: {
    category: [{ required: true, message: '分类不能为空', trigger: 'change' }],
    typeLabel: [{ required: true, message: '类型文案不能为空', trigger: 'blur' }],
    title: [{ required: true, message: '标题不能为空', trigger: 'blur' }],
    content: [{ required: true, message: '内容不能为空', trigger: 'blur' }]
  }
})
const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listIllustrationNotice(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载插画通知失败'))
    })
    .finally(() => {
      loading.value = false
    })
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

function reset() {
  form.value = emptyForm()
  proxy.resetForm('formRef')
}

function handleAdd() {
  reset()
  dialogTitle.value = '新增插画通知'
  open.value = true
}

function handleUpdate(row) {
  reset()
  getIllustrationNotice(row.id)
    .then((res) => {
      const data = res.data || {}
      form.value = {
        ...emptyForm(),
        ...data,
        pointsText: (data.points || []).join('\n')
      }
      dialogTitle.value = '编辑插画通知'
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '获取插画通知失败'))
    })
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    const payload = {
      ...form.value,
      points: splitPoints(form.value.pointsText)
    }
    const api = payload.id ? updateIllustrationNotice : addIllustrationNotice
    api(payload)
      .then(() => {
        proxy.$modal.msgSuccess(payload.id ? '修改成功' : '新增成功')
        open.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存插画通知失败'))
      })
  })
}

function handleDelete(row) {
  proxy.$modal
    .confirm(`确认硬删除插画通知「${row.title}」吗？`)
    .then(() => deleteIllustrationNotice(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除插画通知失败'))
    })
}

function handleCategoryChange(value) {
  const option = categoryOptions.find((item) => item.value === value)
  form.value.typeLabel = option ? option.type : '更新'
}

function splitPoints(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function categoryLabel(value) {
  return categoryOptions.find((item) => item.value === value)?.label || value || '-'
}

getList()
</script>

<style scoped>
.notice-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notice-title {
  font-weight: 600;
  color: #303133;
}

.notice-content-preview {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
