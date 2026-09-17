<template>
  <div class="cc-card">
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="门店名称" style="width:200px" clearable @clear="load" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="openDialog()">新增门店</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" empty-text="暂无数据，可点击右上角按钮新建">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="门店名称" minwidth="180" />
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image v-if="row.cover" :src="row.cover" :preview-src-list="[row.cover]" fit="cover"
                    style="width:44px;height:44px;border-radius:6px" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="address" label="地址" minwidth="220" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="score" label="评分" width="70" />
      <el-table-column prop="businessHours" label="营业时间" width="120" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '营业' : '休息' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该门店？" @confirm="onDelete(row.id)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
                   :page-size="query.pageSize" @current-change="(p) => { query.pageNum = p; load() }" />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑门店' : '新增门店'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="门店名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="封面图">
          <el-upload :show-file-list="false" accept="image/*" :http-request="handleCoverUpload">
            <img v-if="form.cover" :src="form.cover" class="cover-preview" alt="封面预览" />
            <div v-else class="cover-placeholder">点击上传封面</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="城市"><el-input v-model="form.city" /></el-form-item>
        <el-form-item label="经度/纬度">
          <el-input-number v-model="form.lng" :precision="6" :controls="false" style="width:48%" />
          <el-input-number v-model="form.lat" :precision="6" :controls="false" style="width:48%;margin-left:4%" />
        </el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="评分"><el-input-number v-model="form.score" :min="0" :max="5" :precision="1" /></el-form-item>
        <el-form-item label="营业时间"><el-input v-model="form.businessHours" placeholder="08:30-18:00" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">营业</el-radio>
            <el-radio :value="0">休息</el-radio>
          </el-radio-group>
        </el-form-item>
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
import { storePageApi, storeSaveApi, storeUpdateApi, storeDeleteApi, uploadApi } from '../api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, name: '' })
const dialogVisible = ref(false)
const form = reactive({ id: null, name: '', address: '', city: '杭州', lng: 120.15, lat: 30.28, phone: '', score: 4.5, businessHours: '08:30-18:00', status: 1, cover: '' })

/** 封面图上传：走统一 OSS 接口，成功后回填 URL 到表单 */
async function handleCoverUpload(options) {
  const fd = new FormData()
  fd.append('file', options.file)
  const res = await uploadApi(fd)
  form.cover = res.data
}

async function load() {
  loading.value = true
  try {
    const res = await storePageApi(query)
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : { id: null, name: '', address: '', city: '杭州', lng: 120.15, lat: 30.28, phone: '', score: 4.5, businessHours: '08:30-18:00', status: 1, cover: '' })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name || !form.address) {
    ElMessage.warning('名称和地址必填')
    return
  }
  form.id ? await storeUpdateApi(form) : await storeSaveApi(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function onDelete(id) {
  await storeDeleteApi(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.cover-preview { width: 120px; height: 72px; object-fit: cover; border-radius: 6px; border: 1px solid var(--cc-border); }
.cover-placeholder {
  width: 120px; height: 72px; border: 1px dashed var(--cc-border); border-radius: 6px;
  display: flex; align-items: center; justify-content: center; font-size: 12px; color: var(--cc-text-placeholder); cursor: pointer;
}
</style>
