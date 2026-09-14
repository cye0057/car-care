<template>
  <div class="cc-card">
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="券标题" style="width:200px" clearable @clear="load" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="openDialog()">新增优惠券</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" empty-text="暂无数据，可点击右上角按钮新建">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" minwidth="160" />
      <el-table-column prop="typeDesc" label="类型" width="120" />
      <el-table-column label="优惠内容" minwidth="160">
        <template #default="{ row }">
          <span v-if="row.type === 1">满 ¥{{ row.minPrice }} 减 ¥{{ row.discountPrice }}</span>
          <span v-else>无门槛代金 ¥{{ row.cashPrice }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column label="有效期" minwidth="260">
        <template #default="{ row }">{{ fmt(row.validStartTime) }} ~ {{ fmt(row.validEndTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除？" @confirm="onDelete(row.id)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
                   :page-size="query.pageSize" @current-change="(p) => { query.pageNum = p; load() }" />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑优惠券' : '新增优惠券'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">满减券</el-radio>
            <el-radio :value="2">代金券</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.type === 1">
          <el-form-item label="使用门槛"><el-input-number v-model="form.minPrice" :min="0" /></el-form-item>
          <el-form-item label="减免金额"><el-input-number v-model="form.discountPrice" :min="0" /></el-form-item>
        </template>
        <el-form-item v-else label="代金面额"><el-input-number v-model="form.cashPrice" :min="0" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="有效期">
          <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss"
                          start-placeholder="开始" end-placeholder="结束" style="width:100%" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { couponPageApi, couponSaveApi, couponUpdateApi, couponDeleteApi } from '../api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, name: '' })
const dialogVisible = ref(false)
const range = ref([])
const emptyForm = { id: null, storeId: 1, title: '', type: 2, typeDesc: '无门槛代金券', stock: 20, minPrice: 0, discountPrice: 0, cashPrice: 50, description: '', validStartTime: '', validEndTime: '' }
const form = reactive({ ...emptyForm })

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

async function load() {
  loading.value = true
  try {
    const res = await couponPageApi(query)
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : { ...emptyForm })
  range.value = row?.validStartTime ? [row.validStartTime, row.validEndTime] : []
  form.typeDesc = form.type === 1 ? '满减券' : '无门槛代金券'
  dialogVisible.value = true
}

async function onSave() {
  if (!form.title) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!range.value?.length) {
    ElMessage.warning('请选择有效期')
    return
  }
  form.validStartTime = range.value[0]
  form.validEndTime = range.value[1]
  form.typeDesc = form.type === 1 ? '满减券' : '无门槛代金券'
  form.id ? await couponUpdateApi(form) : await couponSaveApi(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function onDelete(id) {
  await couponDeleteApi(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
</style>
