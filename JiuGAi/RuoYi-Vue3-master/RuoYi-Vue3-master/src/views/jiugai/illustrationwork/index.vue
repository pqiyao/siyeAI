<template>
  <div class="app-container illustration-work-page">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里管理插画网站作品。用户上传会先进入待审核，管理员可以直接看图、通过、隐藏、驳回或硬删除。"
    />

    <el-form v-show="showSearch" :model="queryParams" inline>
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          clearable
          placeholder="标题 / 标识 / 上传者"
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="queryParams.category" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="分级">
        <el-select v-model="queryParams.contentLevel" clearable placeholder="全部" style="width: 120px">
          <el-option v-for="item in contentLevelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="queryParams.source" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button icon="Tickets" @click="filterPending">待审核</el-button>
        <el-button icon="Files" @click="filterAll">全部作品</el-button>
        <el-button type="success" icon="Plus" @click="handleAdd">新增作品</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <div v-loading="loading" class="review-board">
      <div v-if="dataList.length" class="review-grid">
        <article v-for="item in dataList" :key="item.id" class="review-card">
          <div class="review-image-wrap">
            <el-image
              class="review-image"
              :src="displayUploadUrl(item.imageUrl || item.coverUrl)"
              :preview-src-list="[displayUploadUrl(item.imageUrl || item.coverUrl)]"
              preview-teleported
              fit="contain"
            >
              <template #error>
                <div class="review-image review-image-empty">图片不可用</div>
              </template>
            </el-image>
            <div class="review-badges">
              <el-tag size="small" :type="statusTagType(item.status)">{{ statusLabel(item.status) }}</el-tag>
              <el-tag size="small" :type="levelTagType(item.contentLevel)">{{ levelLabel(item.contentLevel) }}</el-tag>
            </div>
          </div>

          <div class="review-info">
            <div class="review-title-row">
              <h3 class="review-title">{{ item.title }}</h3>
              <span class="review-id">#{{ item.id }}</span>
            </div>
            <div class="review-meta">
              <span>{{ item.category || '-' }}</span>
              <span>{{ sourceLabel(item.source) }}</span>
              <span>{{ item.submitterName || '匿名提交' }}</span>
            </div>
            <div class="review-tags">
              <el-tag v-for="tag in item.tags || []" :key="tag" class="tag" size="small" effect="plain">{{ tag }}</el-tag>
              <span v-if="!(item.tags || []).length" class="muted">无标签</span>
            </div>
            <p class="review-desc">{{ item.description || '暂无说明' }}</p>
          </div>

          <div class="review-actions">
            <el-button type="success" icon="Check" @click="handleStatusCommand(item, 'PUBLISHED')">通过</el-button>
            <el-button type="warning" icon="Hide" @click="handleStatusCommand(item, 'HIDDEN')">隐藏</el-button>
            <el-button type="info" icon="Close" @click="handleStatusCommand(item, 'REJECTED')">驳回</el-button>
            <el-button icon="Edit" @click="handleUpdate(item)">编辑</el-button>
            <el-button type="danger" icon="Delete" @click="handleDelete(item)">硬删除</el-button>
          </div>
        </article>
      </div>
      <el-empty v-else description="暂无作品" />
    </div>

    <div class="table-title">列表明细</div>
    <el-table v-loading="loading" :data="dataList" row-key="id">
      <el-table-column label="ID" prop="id" width="72" />
      <el-table-column label="封面" width="92">
        <template #default="scope">
          <el-image
            class="cover"
            :src="displayUploadUrl(scope.row.coverUrl)"
            :preview-src-list="[displayUploadUrl(scope.row.imageUrl || scope.row.coverUrl)]"
            preview-teleported
            fit="cover"
          >
            <template #error>
              <div class="cover cover-empty">无图</div>
            </template>
          </el-image>
        </template>
      </el-table-column>
      <el-table-column label="作品" min-width="220" show-overflow-tooltip>
        <template #default="scope">
          <div class="work-title">{{ scope.row.title }}</div>
          <div class="work-sub">{{ scope.row.slug }}</div>
        </template>
      </el-table-column>
      <el-table-column label="分类" prop="category" width="100" />
      <el-table-column label="标签" min-width="180">
        <template #default="scope">
          <el-tag v-for="tag in scope.row.tags || []" :key="tag" class="tag" effect="plain">{{ tag }}</el-tag>
          <span v-if="!(scope.row.tags || []).length" class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="分级" width="92">
        <template #default="scope">
          <el-tag :type="levelTagType(scope.row.contentLevel)">{{ levelLabel(scope.row.contentLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="推荐" width="78">
        <template #default="scope">
          <el-tag :type="scope.row.recommended ? 'success' : 'info'">{{ scope.row.recommended ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="96">
        <template #default="scope">{{ sourceLabel(scope.row.source) }}</template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="78" />
      <el-table-column label="创建时间" prop="createdAt" width="170" show-overflow-tooltip />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-dropdown @command="(command) => handleStatusCommand(scope.row, command)">
            <el-button link type="primary">状态</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="PUBLISHED">发布</el-dropdown-item>
                <el-dropdown-item command="PENDING">待审核</el-dropdown-item>
                <el-dropdown-item command="HIDDEN">隐藏</el-dropdown-item>
                <el-dropdown-item command="REJECTED">驳回</el-dropdown-item>
                <el-dropdown-item command="DRAFT">草稿</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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

    <el-dialog :title="title" v-model="open" width="920px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作品标题" prop="title">
              <el-input v-model="form.title" placeholder="例如：活力满满的巫女" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="访问标识" prop="slug">
              <el-input v-model="form.slug" placeholder="留空时后端按标题生成" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="分类">
              <el-select v-model="form.category" filterable allow-create default-first-option style="width: 100%">
                <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="作品分级" prop="contentLevel">
              <el-select v-model="form.contentLevel" style="width: 100%">
                <el-option v-for="item in contentLevelOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="发布状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车添加"
            style="width: 100%"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="封面图片" prop="coverUrl">
              <div class="image-upload-card">
                <div class="image-preview">
                  <el-image
                    v-if="form.coverUrl"
                    :src="displayUploadUrl(form.coverUrl)"
                    :preview-src-list="[displayUploadUrl(form.coverUrl)]"
                    preview-teleported
                    fit="contain"
                  />
                  <span v-else>上传封面后预览</span>
                </div>
                <el-upload
                  class="image-uploader"
                  drag
                  :action="uploadAction"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  accept=".png,.jpg,.jpeg,.webp,.gif,image/png,image/jpeg,image/webp,image/gif"
                  :before-upload="beforeIllustrationImageUpload"
                  :on-success="onCoverUploadSuccess"
                >
                  <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                  <div class="el-upload__text">拖拽封面到这里，或<em>点击选择</em></div>
                  <template #tip>
                    <div class="el-upload__tip">支持 JPG / PNG / WEBP / GIF，最大 12MB</div>
                  </template>
                </el-upload>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原图文件" prop="imageUrl">
              <div class="image-upload-card">
                <div class="image-preview">
                  <el-image
                    v-if="form.imageUrl"
                    :src="displayUploadUrl(form.imageUrl)"
                    :preview-src-list="[displayUploadUrl(form.imageUrl)]"
                    preview-teleported
                    fit="contain"
                  />
                  <span v-else>上传原图后预览</span>
                </div>
                <el-upload
                  class="image-uploader"
                  drag
                  :action="uploadAction"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  accept=".png,.jpg,.jpeg,.webp,.gif,image/png,image/jpeg,image/webp,image/gif"
                  :before-upload="beforeIllustrationImageUpload"
                  :on-success="onImageUploadSuccess"
                >
                  <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                  <div class="el-upload__text">拖拽原图到这里，或<em>点击选择</em></div>
                  <template #tip>
                    <div class="el-upload__tip">封面为空时会自动同步为封面</div>
                  </template>
                </el-upload>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="推荐展示">
              <el-switch v-model="form.recommended" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源">
              <el-select v-model="form.source" style="width: 100%">
                <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上传者ID">
              <el-input-number v-model="form.submitterUserId" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上传者名称">
              <el-input v-model="form.submitterName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="作品说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input v-model="form.auditNote" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">保存</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgIllustrationWork">
import {
  addIllustrationWork,
  deleteIllustrationWork,
  getIllustrationWork,
  getIllustrationWorkMeta,
  listIllustrationWork,
  updateIllustrationWork,
  updateIllustrationWorkStatus
} from '@/api/jiugai/illustrationWork'
import { ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const baseApi = import.meta.env.VITE_SILLY_API || '/silly-api'
const uploadAction = baseApi + '/admin/jiugai/upload/image'
const uploadHeaders = ref({ Authorization: 'Bearer ' + getToken() })
const useProxyUploadsInAdmin = import.meta.env.DEV

const dataList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const contentLevelOptions = ref([
  { value: 'NORMAL', label: '全年龄' },
  { value: 'R15', label: '15+' },
  { value: 'R18', label: '18+' }
])
const statusOptions = ref([
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待审核' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'HIDDEN', label: '已隐藏' }
])
const sourceOptions = ref([
  { value: 'ADMIN', label: '后台录入' },
  { value: 'USER', label: '用户上传' }
])
const categoryOptions = ref(['动画', '漫画', '原创', '插画', '壁纸'])

const emptyForm = () => ({
  id: undefined,
  title: '',
  slug: '',
  category: '原创',
  tags: [],
  description: '',
  coverUrl: '',
  imageUrl: '',
  contentLevel: 'NORMAL',
  status: 'PUBLISHED',
  source: 'ADMIN',
  submitterUserId: undefined,
  submitterName: '',
  auditNote: '',
  recommended: false,
  sortOrder: 0
})

const data = reactive({
  form: emptyForm(),
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    category: undefined,
    contentLevel: undefined,
    status: undefined,
    source: undefined
  },
  rules: {
    title: [{ required: true, message: '作品标题不能为空', trigger: 'blur' }],
    coverUrl: [{ required: true, message: '请上传封面图片', trigger: 'change' }],
    imageUrl: [{ required: true, message: '请上传原图文件', trigger: 'change' }],
    contentLevel: [{ required: true, message: '作品分级不能为空', trigger: 'change' }],
    status: [{ required: true, message: '发布状态不能为空', trigger: 'change' }]
  }
})

const { form, queryParams, rules } = toRefs(data)

function getMeta() {
  getIllustrationWorkMeta()
    .then((res) => {
      const meta = res.data || {}
      contentLevelOptions.value = meta.contentLevels || contentLevelOptions.value
      statusOptions.value = meta.statuses || statusOptions.value
      sourceOptions.value = meta.sources || sourceOptions.value
      categoryOptions.value = (meta.categories || categoryOptions.value).filter((item) => item !== '全部')
    })
    .catch(() => {})
}

function getList() {
  loading.value = true
  listIllustrationWork(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载插画作品失败'))
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
  queryParams.value.contentLevel = undefined
  queryParams.value.status = undefined
  queryParams.value.source = undefined
  handleQuery()
}

function filterPending() {
  queryParams.value.status = 'PENDING'
  queryParams.value.pageNum = 1
  getList()
}

function filterAll() {
  queryParams.value.status = undefined
  queryParams.value.source = undefined
  queryParams.value.pageNum = 1
  getList()
}

function handleAdd() {
  form.value = emptyForm()
  title.value = '新增插画作品'
  open.value = true
}

function handleUpdate(row) {
  getIllustrationWork(row.id)
    .then((res) => {
      form.value = { ...emptyForm(), ...(res.data || {}) }
      title.value = '编辑插画作品'
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '获取作品详情失败'))
    })
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    const payload = { ...form.value, submitterUserId: form.value.submitterUserId || undefined }
    const action = payload.id ? updateIllustrationWork : addIllustrationWork
    action(payload)
      .then(() => {
        proxy.$modal.msgSuccess('保存成功')
        open.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存插画作品失败'))
      })
  })
}

async function handleStatusCommand(row, status) {
  let auditNote = row.auditNote || ''
  if (status === 'REJECTED' || status === 'HIDDEN') {
    try {
      const result = await ElMessageBox.prompt(
        `请填写「${statusLabel(status)}」原因，方便后续追踪。`,
        `${statusActionLabel(status)}作品`,
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputValue: auditNote,
          inputPlaceholder: '例如：图片清晰度不足 / 内容暂不适合公开展示',
          inputValidator: (value) => Boolean(value && value.trim()),
          inputErrorMessage: '请填写审核备注'
        }
      )
      auditNote = result.value || ''
    } catch (e) {
      if (isMessageBoxCancelled(e)) return
      throw e
    }
  } else if (status === 'PUBLISHED' && !auditNote) {
    auditNote = '审核通过'
  }

  updateIllustrationWorkStatus({ id: row.id, status, auditNote })
    .then(() => {
      proxy.$modal.msgSuccess('状态已更新')
      getList()
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '更新状态失败'))
    })
}

function handleDelete(row) {
  proxy.$modal.confirm(`确认硬删除作品「${row.title}」吗？删除后数据库记录不会保留。`).then(() => {
    deleteIllustrationWork(row.id)
      .then(() => {
        proxy.$modal.msgSuccess('硬删除成功')
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除作品失败'))
      })
  })
}

function beforeIllustrationImageUpload(file) {
  const allow = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
  if (!allow.includes(file.type)) {
    proxy.$modal.msgError('仅支持 jpg / png / webp / gif 图片')
    return false
  }
  const maxMb = 12
  if (file.size / 1024 / 1024 > maxMb) {
    proxy.$modal.msgError('图片不能超过 ' + maxMb + 'MB')
    return false
  }
  return true
}

function onCoverUploadSuccess(res) {
  if (res.code === 200 && res.fileName) {
    form.value.coverUrl = res.fileName
    proxy.$modal.msgSuccess('封面已上传')
  } else {
    proxy.$modal.msgError(res.msg || '上传失败')
  }
}

function onImageUploadSuccess(res) {
  if (res.code === 200 && res.fileName) {
    form.value.imageUrl = res.fileName
    if (!form.value.coverUrl) {
      form.value.coverUrl = res.fileName
    }
    proxy.$modal.msgSuccess('原图已上传')
  } else {
    proxy.$modal.msgError(res.msg || '上传失败')
  }
}

function displayUploadUrl(url) {
  const value = (url || '').trim()
  if (!value) return ''
  if (value.startsWith('/uploads/') || value.startsWith('/art/')) {
    return useProxyUploadsInAdmin ? baseApi + value : value
  }
  return value
}

function optionLabel(options, value) {
  return (options.value.find((item) => item.value === value) || {}).label || value || '-'
}

function levelLabel(value) {
  return optionLabel(contentLevelOptions, value)
}

function statusLabel(value) {
  return optionLabel(statusOptions, value)
}

function statusActionLabel(value) {
  if (value === 'PUBLISHED') return '通过'
  if (value === 'REJECTED') return '驳回'
  if (value === 'HIDDEN') return '隐藏'
  if (value === 'PENDING') return '标记待审核'
  if (value === 'DRAFT') return '转为草稿'
  return '更新'
}

function sourceLabel(value) {
  return optionLabel(sourceOptions, value)
}

function levelTagType(value) {
  if (value === 'R18') return 'danger'
  if (value === 'R15') return 'warning'
  return 'success'
}

function statusTagType(value) {
  if (value === 'PUBLISHED') return 'success'
  if (value === 'PENDING') return 'warning'
  if (value === 'REJECTED') return 'danger'
  if (value === 'HIDDEN') return 'info'
  return ''
}

getMeta()
getList()
</script>

<style scoped>
.cover {
  width: 56px;
  height: 72px;
  border-radius: 6px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
}

.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 12px;
}

.work-title {
  font-weight: 600;
  color: #303133;
  line-height: 20px;
}

.work-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.tag {
  margin: 2px 4px 2px 0;
}

.muted {
  color: #909399;
}

.review-board {
  min-height: 180px;
  margin-bottom: 18px;
}

.review-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.review-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.review-image-wrap {
  position: relative;
  height: 360px;
  background: #f5f7fa;
}

.review-image {
  width: 100%;
  height: 100%;
}

.review-image-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.review-badges {
  position: absolute;
  left: 10px;
  top: 10px;
  display: flex;
  gap: 6px;
}

.review-info {
  padding: 12px;
}

.review-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.review-title {
  margin: 0;
  color: #303133;
  font-size: 15px;
  line-height: 22px;
}

.review-id {
  flex: 0 0 auto;
  color: #909399;
  font-size: 12px;
}

.review-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 6px;
  color: #606266;
  font-size: 12px;
}

.review-tags {
  min-height: 26px;
  margin-top: 8px;
}

.review-desc {
  min-height: 40px;
  margin: 6px 0 0;
  color: #606266;
  font-size: 13px;
  line-height: 20px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.review-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #ebeef5;
}

.review-actions :deep(.el-button) {
  margin-left: 0;
}

.table-title {
  margin: 8px 0 10px;
  color: #303133;
  font-weight: 600;
}

.image-upload-card {
  width: 100%;
}

.image-preview {
  display: grid;
  place-items: center;
  height: 220px;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #f5f7fa;
  color: #909399;
  overflow: hidden;
}

.image-preview :deep(.el-image) {
  width: 100%;
  height: 100%;
}

.image-uploader {
  width: 100%;
}

.image-uploader :deep(.el-upload) {
  width: 100%;
}

.image-uploader :deep(.el-upload-dragger) {
  width: 100%;
}
</style>
