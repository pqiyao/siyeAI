<template>
  <div class="app-container">
    <el-form inline>
      <el-form-item label="角色">
        <el-select
          v-model="queryParams.characterId"
          placeholder="选择角色"
          filterable
          clearable
          style="width: 280px"
          @change="handleQuery"
        >
          <el-option v-for="c in characterOptions" :key="c.id" :label="c.name + ' (#' + c.id + ')'" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" :disabled="!queryParams.characterId" @click="handleQuery">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" :disabled="!queryParams.characterId" @click="handleAdd">新增条目</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="!queryParams.characterId || single" @click="handleUpdate()">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!queryParams.characterId || multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain :disabled="!queryParams.characterId || multiple" @click="batchEnabled(true)">批量启用</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain :disabled="!queryParams.characterId || multiple" @click="batchEnabled(false)">批量禁用</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="来源" width="100">
        <template #default="scope">
          <el-tag :type="isEmbeddedLorebook(scope.row) ? 'success' : 'info'">
            {{ isEmbeddedLorebook(scope.row) ? '卡内同步' : '手工' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关键词" prop="keywordsCsv" min-width="160" show-overflow-tooltip />
      <el-table-column label="优先级" prop="priority" width="80" />
      <el-table-column label="常驻" prop="constantInjection" width="70">
        <template #default="scope">
          {{ scope.row.constantInjection ? '是' : '否' }}
        </template>
      </el-table-column>
      <el-table-column label="扫描深度" prop="scanDepth" width="90" />
      <el-table-column label="启用" prop="enabled" width="70">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容预览" prop="content" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0 && queryParams.characterId"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" v-model="open" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="角色 ID" prop="characterId">
          <el-input-number v-model="form.characterId" :min="1" :disabled="!!form.id" style="width: 100%" />
        </el-form-item>
        <el-alert
          v-if="isEmbeddedLorebook(form)"
          class="mb8"
          type="warning"
          :closable="false"
          title="这是从角色卡 character_book 同步出来的本地副本；重新导入角色卡时会自动刷新。"
        />
        <el-form-item label="关键词 CSV" prop="keywordsCsv">
          <el-input v-model="form.keywordsCsv" placeholder="英文逗号分隔，命中任意一个即触发" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" />
        </el-form-item>
        <el-form-item label="常驻注入">
          <el-switch v-model="form.constantInjection" />
        </el-form-item>
        <el-form-item label="扫描深度">
          <el-input-number v-model="form.scanDepth" :min="1" :max="64" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确定</el-button>
        <el-button @click="open = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgLorebook">
import { listCharacter } from '@/api/jiugai/character'
import { listLorebook, getLorebook, addLorebook, updateLorebook, delLorebook, batchLorebookEnabled } from '@/api/jiugai/lorebook'
import { isMessageBoxCancelled, jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()
const route = useRoute()

const characterOptions = ref([])
const dataList = ref([])
const loading = ref(false)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)

const emptyForm = () => ({
  id: undefined,
  characterId: undefined,
  keywordsCsv: '',
  content: '',
  priority: 0,
  constantInjection: false,
  scanDepth: 4,
  enabled: true,
  source: 'manual'
})

const data = reactive({
  form: emptyForm(),
  queryParams: { characterId: undefined, pageNum: 1, pageSize: 10 },
  rules: {
    characterId: [{ required: true, message: '请选择角色', trigger: 'change' }],
    content: [{ required: true, message: '内容不能为空', trigger: 'blur' }]
  }
})
const { queryParams, form, rules } = toRefs(data)

function loadCharacters() {
  listCharacter({ pageNum: 1, pageSize: 500, scope: 'all' })
    .then((res) => {
      characterOptions.value = res.rows || []
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载角色列表失败'))
    })
}

function applyRouteQuery() {
  const raw = route.query?.characterId
  let id = Number(Array.isArray(raw) ? raw[0] : raw)
  if (!Number.isFinite(id) || id <= 0) {
    id = Number(sessionStorage.getItem('jgLorebookCharacterId'))
    sessionStorage.removeItem('jgLorebookCharacterId')
  }
  if (Number.isFinite(id) && id > 0) {
    queryParams.value.characterId = id
  }
}

watch(
  () => route.query,
  () => {
    applyRouteQuery()
    handleQuery()
  }
)

onActivated(() => {
  applyRouteQuery()
  if (queryParams.value.characterId) {
    handleQuery()
  }
})

function getList() {
  if (!queryParams.value.characterId) {
    dataList.value = []
    total.value = 0
    return
  }
  loading.value = true
  listLorebook(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载世界书失败'))
    })
    .finally(() => {
      loading.value = false
    })
}

function isEmbeddedLorebook(row) {
  return row?.source === 'embedded_character_book'
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function reset() {
  form.value = emptyForm()
  if (queryParams.value.characterId) {
    form.value.characterId = queryParams.value.characterId
  }
  proxy.resetForm('formRef')
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增世界书条目'
}

function handleUpdate(row) {
  reset()
  const id = row?.id || ids.value[0]
  if (!id) return
  getLorebook(id)
    .then((res) => {
      form.value = { ...emptyForm(), ...(res.data || {}) }
      open.value = true
      title.value = '修改世界书条目'
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '获取条目失败'))
    })
}

function submitForm() {
  proxy.$refs.formRef.validate((valid) => {
    if (!valid) return
    const api = form.value.id ? updateLorebook : addLorebook
    api(form.value)
      .then(() => {
        proxy.$modal.msgSuccess(form.value.id ? '修改成功' : '新增成功')
        open.value = false
        getList()
      })
      .catch((e) => {
        proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '保存失败'))
      })
  })
}

function handleDelete(row) {
  const delIds = row?.id || ids.value.join(',')
  if (!delIds) return
  proxy.$modal
    .confirm('是否确认删除？')
    .then(() => delLorebook(delIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess('删除成功')
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '删除失败'))
    })
}

function batchEnabled(enabled) {
  if (!ids.value.length) {
    proxy.$modal.msgWarning('请先勾选条目')
    return
  }
  const label = enabled ? '启用' : '禁用'
  proxy.$modal
    .confirm(`确定批量${label}选中的 ${ids.value.length} 条世界书吗？`)
    .then(() => batchLorebookEnabled(ids.value, enabled))
    .then(() => {
      proxy.$modal.msgSuccess(`已批量${label}`)
      getList()
    })
    .catch((e) => {
      if (isMessageBoxCancelled(e)) return
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '批量操作失败'))
    })
}

applyRouteQuery()
loadCharacters()
if (queryParams.value.characterId) {
  getList()
}
</script>
