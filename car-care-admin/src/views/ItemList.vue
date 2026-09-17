<template>
  <div class="cc-card">
    <div class="toolbar">
      <el-select v-model="query.storeId" placeholder="门店" clearable style="width:200px" @change="load">
        <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-select v-model="query.categoryId" placeholder="分类" clearable style="width:160px" @change="load">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-input v-model="query.name" placeholder="项目名称" style="width:180px" clearable @clear="load" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="openDialog()">新增项目</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" empty-text="暂无数据，可点击右上角按钮新建">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="项目名称" minwidth="180" />
      <el-table-column label="门店" width="200">
        <template #default="{ row }">{{ storeName(row.storeId) }}</template>
      </el-table-column>
      <el-table-column label="分类" width="130">
        <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column label="图片" width="80">
        <template #default="{ row }">
          <el-image v-if="row.image" :src="row.image" :preview-src-list="[row.image]" fit="cover"
                    style="width:44px;height:44px;border-radius:6px" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
      <el-table-column prop="description" label="说明" minwidth="180" show-overflow-tooltip />
      <el-table-column label="起售" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="(v) => onStatus(row, v)" />
        </template>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑项目' : '新增项目'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="门店">
          <el-select v-model="form.storeId" style="width:100%">
            <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="图片">
          <el-upload :show-file-list="false" accept="image/*" :http-request="handleImageUpload">
            <img v-if="form.image" :src="form.image" class="image-preview" alt="项目图预览" />
            <div v-else class="image-placeholder">点击上传图片</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
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
import { itemPageApi, itemSaveApi, itemUpdateApi, itemStatusApi, itemDeleteApi, storeListEnabledApi, categoryListApi, uploadApi } from '../api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const stores = ref([])
const categories = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, name: '', storeId: null, categoryId: null })
const dialogVisible = ref(false)
const form = reactive({ id: null, storeId: null, categoryId: null, name: '', price: 100, description: '', image: '' })

/** 项目图上传：走统一 OSS 接口，成功后回填 URL 到表单 */
async function handleImageUpload(options) {
  const fd = new FormData()
  fd.append('file', options.file)
  const res = await uploadApi(fd)
  form.image = res.data
}

const storeName = (id) => stores.value.find((s) => s.id === id)?.name || id
const categoryName = (id) => categories.value.find((c) => c.id === id)?.name || id

async function load() {
  loading.value = true
  try {
    const res = await itemPageApi(query)
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : { id: null, storeId: query.storeId || stores.value[0]?.id || null, categoryId: query.categoryId || null, name: '', price: 100, description: '', image: '' })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.storeId || !form.categoryId || !form.name) {
    ElMessage.warning('门店、分类、名称必填')
    return
  }
  form.id ? await itemUpdateApi(form) : await itemSaveApi(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function onStatus(row, checked) {
  await itemStatusApi(row.id, checked ? 1 : 0)
  ElMessage.success(checked ? '已起售' : '已停售')
  load()
}

async function onDelete(id) {
  await itemDeleteApi(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(async () => {
  const [s, c] = await Promise.all([storeListEnabledApi(), categoryListApi()])
  stores.value = s.data
  categories.value = c.data
  load()
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.image-preview { width: 120px; height: 72px; object-fit: cover; border-radius: 6px; border: 1px solid var(--cc-border); }
.image-placeholder {
  width: 120px; height: 72px; border: 1px dashed var(--cc-border); border-radius: 6px;
  display: flex; align-items: center; justify-content: center; font-size: 12px; color: var(--cc-text-placeholder); cursor: pointer;
}
</style>
