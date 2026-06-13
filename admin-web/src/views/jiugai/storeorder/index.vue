<template>
  <div class="app-container">
    <el-alert
      class="mb12"
      type="info"
      :closable="false"
      show-icon
      title="当前支付先走模拟链路，这里主要用于运营排查订单生成、到账与商品配置是否一致。"
    />

    <el-form :model="queryParams" inline v-show="showSearch">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="订单号 / 用户ID / 商品"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="待支付" value="PENDING" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已关闭" value="CLOSED" />
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

    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="订单号" prop="orderNo" min-width="210" show-overflow-tooltip />
      <el-table-column label="用户" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          <div>{{ scope.row.username || '匿名用户' }}</div>
          <div class="sub-line">{{ scope.row.clientUid || ('user#' + scope.row.userId) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="商品" prop="productName" min-width="160" show-overflow-tooltip />
      <el-table-column label="金额" width="110">
        <template #default="scope">
          ¥ {{ Number(scope.row.amountYuan || 0).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="支付方式" prop="paymentChannel" width="130" />
      <el-table-column label="状态" width="92">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'PAID' ? 'success' : scope.row.status === 'CLOSED' ? 'info' : 'warning'">
            {{ scope.row.statusLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createdAt" width="170" />
      <el-table-column label="支付时间" prop="paidAt" width="170" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
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

    <el-dialog title="订单详情" v-model="open" width="680px" append-to-body destroy-on-close>
      <el-descriptions :column="1" border v-if="detail.orderNo">
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.username || '匿名用户' }}</el-descriptions-item>
        <el-descriptions-item label="clientUid">{{ detail.clientUid || '--' }}</el-descriptions-item>
        <el-descriptions-item label="商品">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="商品编码">{{ detail.productCode }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥ {{ Number(detail.amountYuan || 0).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">{{ detail.paymentChannel }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="到账权益">
          +{{ detail.scoreAmount || 0 }} 钻石 / +{{ detail.goldCoinAmount || 0 }} 金币 / {{ detail.vipDays || 0 }} 天 VIP
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt || '--' }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ detail.paidAt || '--' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="open = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="JgStoreOrder">
import { getStoreOrder, listStoreOrder } from '@/api/jiugai/storeorder'
import { jiugaiRequestErrorMessage } from '@/utils/jiugaiRequestError'

const { proxy } = getCurrentInstance()

const dataList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const open = ref(false)
const detail = ref({})

const data = reactive({
  queryParams: { pageNum: 1, pageSize: 10, keyword: undefined, status: undefined }
})

const { queryParams } = toRefs(data)

function getList() {
  loading.value = true
  listStoreOrder(queryParams.value)
    .then((res) => {
      dataList.value = res.rows || []
      total.value = res.total || 0
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '加载订单列表失败'))
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
  queryParams.value.status = undefined
  handleQuery()
}

function handleView(row) {
  getStoreOrder(row.orderNo)
    .then((res) => {
      detail.value = res.data || {}
      open.value = true
    })
    .catch((e) => {
      proxy.$modal.msgError(jiugaiRequestErrorMessage(e, '获取订单详情失败'))
    })
}

getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.sub-line {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
