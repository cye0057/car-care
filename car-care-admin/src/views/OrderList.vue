<template>
  <div class="cc-card">
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="订单号" style="width:180px" clearable @clear="load" @keyup.enter="load" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:130px" @change="load">
        <el-option v-for="(t, k) in statusMap" :key="k" :label="t.text" :value="Number(k)" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" empty-text="暂无数据，可点击右上角按钮新建">
      <el-table-column prop="orderNo" label="订单号" width="120" />
      <el-table-column prop="userName" label="车主" width="90" />
      <el-table-column prop="plateNumber" label="车牌" width="110" />
      <el-table-column prop="storeName" label="门店" minwidth="180" show-overflow-tooltip />
      <el-table-column prop="actualAmount" label="实付" width="90">
        <template #default="{ row }">¥{{ row.actualAmount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">{{ statusMap[row.status]?.text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" width="150">
        <template #default="{ row }">{{ fmt(row.orderTime) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" minwidth="140" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" link type="primary" @click="onStatus(row, 3)">开始施工</el-button>
          <el-button v-if="row.status === 1" link type="danger" @click="onCancel(row)">取消</el-button>
          <el-button v-if="row.status === 2" link type="primary" @click="onStatus(row, 3)">开始施工</el-button>
          <el-button v-if="row.status === 3" link type="success" @click="onStatus(row, 4)">完工</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
                   :page-size="query.pageSize" @current-change="(p) => { query.pageNum = p; load() }" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderPageApi, orderStatusApi } from '../api'

const statusMap = {
  1: { text: '待支付', type: 'warning' },
  2: { text: '已支付', type: 'primary' },
  3: { text: '施工中', type: 'primary' },
  4: { text: '已完工', type: 'success' },
  5: { text: '已取消', type: 'info' },
  6: { text: '已评价', type: 'success' }
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, name: '', status: null })

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

async function load() {
  loading.value = true
  try {
    const res = await orderPageApi(query)
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function onStatus(row, status) {
  await orderStatusApi(row.id, status)
  ElMessage.success('状态已更新')
  load()
}

async function onCancel(row) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消订单', { inputPlaceholder: '如：车主主动取消' })
  await orderStatusApi(row.id, 5, value || '管理员取消')
  ElMessage.success('已取消')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
</style>
