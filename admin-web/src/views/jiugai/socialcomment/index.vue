<template>
  <div class="app-container social-page">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="这里治理社区评论与回复。第一版支持评论检索、评论详情查看、回复串查看，以及后台删除评论。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="评论内容 / 用户昵称 / 用户名"
          clearable
          style="width: 260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="帖子ID">
        <el-input v-model="queryParams.postId" clearable placeholder="所属帖子ID" style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model="queryParams.userId" clearable placeholder="评论用户ID" style="width: 150px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['social:community:delete']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">
          批量删除
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="评论ID" prop="commentId" width="90" />
      <el-table-column label="帖子ID" prop="postId" width="88" />
      <el-table-column label="评论用户" min-width="170" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.nickname || `用户${scope.row.userId}` }}</div>
          <div class="sub-line">UID {{ scope.row.userId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="评论内容" prop="content" min-width="320" show-overflow-tooltip />
      <el-table-column label="回复数" prop="replyCount" width="88" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'hidden' ? 'warning' : 'success'">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createdAt" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
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

    <el-dialog v-model="open" title="社区评论详情" width="920px" append-to-body destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="评论ID">{{ detail.commentId }}</el-descriptions-item>
          <el-descriptions-item label="帖子ID">{{ detail.postId }}</el-descriptions-item>
          <el-descriptions-item label="评论用户">{{ detail.nickname || `用户${detail.userId}` }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="回复数">{{ detail.replyCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="评论内容" :span="2">
            <div class="pre-wrap">{{ detail.content || '--' }}</div>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">回复串</div>
        <div v-if="detail.replies?.length" class="reply-list">
          <div v-for="reply in detail.replies" :key="reply.replyId" class="reply-card">
            <div class="reply-head">
              <div>
                <strong>{{ reply.fromNickname || `用户${reply.fromUserId}` }}</strong>
                <span class="sub-line">UID {{ reply.fromUserId }}</span>
                <template v-if="reply.toUserId">
                  <span class="sub-line">回复给 {{ reply.toNickname || `用户${reply.toUserId}` }}</span>
                </template>
              </div>
              <span class="sub-line">{{ reply.createdAt }}</span>
            </div>
            <div class="pre-wrap">{{ reply.content || '--' }}</div>
          </div>
        </div>
        <el-empty v-else description="当前评论还没有回复" />
      </template>
      <template #footer>
        <el-button @click="open = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgSocialComment">
import { delSocialComment, getSocialComment, getSocialCommentMeta, listSocialComment } from '@/api/jiugai/socialComment'
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

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    postId: undefined,
    userId: undefined
  }
})

const { queryParams } = toRefs(data)

function loadMeta() {
  return getSocialCommentMeta()
}

function getList() {
  loading.value = true
  listSocialComment(normalizeQuery(queryParams.value))
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载评论列表失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function normalizeQuery(query) {
  const next = { ...query }
  next.postId = toLongOrUndefined(next.postId)
  next.userId = toLongOrUndefined(next.userId)
  return next
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value.keyword = undefined
  queryParams.value.postId = undefined
  queryParams.value.userId = undefined
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.commentId)
  multiple.value = !selection.length
}

function handleView(row) {
  getSocialComment(row.commentId)
    .then((res) => {
      detail.value = res.data || null
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载评论详情失败'))
    })
}

function handleDelete(row) {
  const targetIds = row ? String(row.commentId) : ids.value.join(',')
  if (!targetIds) return
  proxy.$modal
    .confirm('确认删除选中的评论吗？该评论下回复会一并软删除。')
    .then(() => delSocialComment(targetIds))
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
  margin-left: 6px;
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

.reply-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reply-card {
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.reply-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
</style>
