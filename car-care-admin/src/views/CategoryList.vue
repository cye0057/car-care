<template>
  <div class="cc-card">
    <div class="toolbar">
      <el-button type="success" @click="openDialog()">新增分类</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" empty-text="暂无数据，可点击右上角按钮新建">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="分类名称" minwidth="200" />
      <el-table-column prop="sort" label="排序" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑分类' : '新增分类'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
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
import { categoryListApi, categorySaveApi, categoryUpdateApi, categoryDeleteApi } from '../api'

const loading = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const form = reactive({ id: null, name: '', sort: 0, status: 1 })

async function load() {
  loading.value = true
  try {
    const res = await categoryListApi()
    rows.value = res.data
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : { id: null, name: '', sort: 0, status: 1 })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  form.id ? await categoryUpdateApi(form) : await categorySaveApi(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function onDelete(id) {
  await categoryDeleteApi(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 14px; }
</style>
