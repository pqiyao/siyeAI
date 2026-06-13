<template>
  <div class="app-container social-page">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里治理社区动态内容。第一版支持帖子检索、详情查看、隐藏/恢复、删除，以及媒体内容审查。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="内容 / 用户昵称 / 用户名"
          clearable
          style="width: 260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 140px">
          <el-option v-for="item in meta.statusOptions" :key="item" :label="statusLabel(item)" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model="queryParams.userId" clearable placeholder="发帖用户ID" style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['social:community:update-status']" type="warning" plain icon="Hide" :disabled="multiple" @click="handleBatchStatus('hidden')">
          批量隐藏
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['social:community:update-status']" type="success" plain icon="View" :disabled="multiple" @click="handleBatchStatus('normal')">
          批量恢复
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['social:community:delete']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">
          批量删除
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="帖子ID" prop="postId" width="88" />
      <el-table-column label="发布用户" min-width="170" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.nickname || `用户${scope.row.userId}` }}</div>
          <div class="sub-line">UID {{ scope.row.userId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="内容" prop="content" min-width="280" show-overflow-tooltip />
      <el-table-column label="来源" prop="sourceType" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'hidden' ? 'warning' : 'success'">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="点赞" prop="likeCount" width="80" />
      <el-table-column label="评论" prop="commentCount" width="80" />
      <el-table-column label="浏览" prop="viewCount" width="80" />
      <el-table-column label="图片数" width="88">
        <template #default="scope">
          {{ Array.isArray(scope.row.mediaList) ? scope.row.mediaList.length : 0 }}
        </template>
      </el-table-column>
      <el-table-column label="发布时间" prop="createdAt" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
          <el-button
            v-hasPermi="['social:community:update-status']"
            link
            :type="scope.row.status === 'hidden' ? 'success' : 'warning'"
            :icon="scope.row.status === 'hidden' ? 'Check' : 'Hide'"
            @click="handleUpdateStatus(scope.row, scope.row.status === 'hidden' ? 'normal' : 'hidden')"
          >
            {{ scope.row.status === 'hidden' ? '恢复' : '隐藏' }}
          </el-button>
          <el-button v-hasPermi="['social:community:delete']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog v-model="open" title="社区帖子详情" width="900px" append-to-body destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="帖子ID">{{ detail.postId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.nickname || `用户${detail.userId}` }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.sourceType || '--' }}</el-descriptions-item>
          <el-descriptions-item label="开放评论">{{ truthy(detail.openComments) ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="点赞数">{{ detail.likeCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="评论数">{{ detail.commentCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="浏览数">{{ detail.viewCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">
            <div class="pre-wrap">{{ detail.content || '--' }}</div>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">媒体内容</div>
        <div v-if="detail.mediaList?.length" class="media-grid">
          <a v-for="item in detail.mediaList" :key="item.id || item.mediaKey" class="media-item" :href="item.mediaUrl || '#'" target="_blank" rel="noreferrer">
            <img :src="item.mediaUrl" :alt="item.mediaKey || 'image'" />
            <div class="media-meta">{{ item.mediaKey }}</div>
          </a>
        </div>
        <el-empty v-else description="当前帖子没有图片媒体" />
      </template>
      <template #footer>
        <el-button @click="open = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgSocialPost">
import { delSocialPost, getSocialPost, getSocialPostMeta, listSocialPost, updateSocialPostStatus } from '@/api/jiugai/socialPost'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref([])
const ids = ref([])
const multiple = ref(true)
const open = ref(false)
const detail = ref(null)
const meta = reactive({
  statusOptions: []
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    status: undefined,
    userId: undefined
  }
})

const { queryParams } = toRefs(data)

function loadMeta() {
  return getSocialPostMeta().then((res) => {
    meta.statusOptions = res.data?.statusOptions || []
  })
}

function getList() {
  loading.value = true
  listSocialPost(normalizeQuery(queryParams.value))
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载社区帖子失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function normalizeQuery(query) {
  const next = { ...query }
  next.userId = toLongOrUndefined(next.userId)
  return next
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.keyword = undefined
  queryParams.value.status = undefined
  queryParams.value.userId = undefined
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.postId)
  multiple.value = !selection.length
}

function handleView(row) {
  getSocialPost(row.postId)
    .then((res) => {
      detail.value = res.data || null
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载帖子详情失败'))
    })
}

function handleUpdateStatus(row, status) {
  const postIds = row ? [row.postId] : ids.value.slice()
  if (!postIds.length) return
  const action = status === 'hidden' ? '隐藏' : '恢复'
  proxy.$modal
    .confirm(`确认${action}选中的社区帖子吗？`)
    .then(async () => {
      for (const postId of postIds) {
        await updateSocialPostStatus({ postId, status })
      }
      proxy.$modal.msgSuccess(`${action}成功`)
      if (detail.value && row && detail.value.postId === row.postId) {
        detail.value.status = status
      }
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, `${action}失败`))
    })
}

function handleBatchStatus(status) {
  handleUpdateStatus(null, status)
}

function handleDelete(row) {
  const targetIds = row ? String(row.postId) : ids.value.join(',')
  if (!targetIds) return
  proxy.$modal
    .confirm('确认删除选中的社区帖子吗？删除后前台将不可见。')
    .then(() => delSocialPost(targetIds))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      open.value = false
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除失败'))
    })
}

function statusLabel(value) {
  if (value === 'hidden') return '隐藏'
  return '正常'
}

function truthy(value) {
  return value === true || value === 1 || value === '1'
}

function toLongOrUndefined(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return undefined
  }
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

loadMeta().finally(() => getList())
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.sub-line {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.section-title {
  margin: 18px 0 12px;
  font-size: 15px;
  font-weight: 700;
}

.pre-wrap {
  white-space: pre-wrap;
  line-height: 1.7;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}

.media-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
  text-decoration: none;
}

.media-item img {
  width: 100%;
  height: 180px;
  object-fit: cover;
  border-radius: 10px;
  background: var(--el-fill-color);
}

.media-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  word-break: break-all;
}
</style>
