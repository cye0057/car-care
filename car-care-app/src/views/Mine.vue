<template>
  <div>
    <van-nav-bar title="我的" />
    <div class="page">
      <van-cell-group inset>
        <van-cell>
          <template #icon>
            <div class="mine-ava" @click="pickAvatar">
              <img v-if="avatar" :src="avatar" alt="头像" />
              <span v-else>{{ (name || '车')[0] }}</span>
            </div>
          </template>
          <template #title>
            <div class="mine-name">{{ name }}</div>
            <div class="mine-id">ID: {{ uid }} · 点击头像可更换</div>
          </template>
        </van-cell>
      </van-cell-group>
      <input ref="fileInput" type="file" accept="image/*" class="hidden-file" @change="onAvatarPick" />

      <div class="card-title">
        <span>我的车辆</span>
        <span class="add-btn" @click="openVehicle()">＋ 添加</span>
      </div>
      <van-cell v-for="v in vehicles" :key="v.id" :title="v.plateNumber"
                :label="`${v.brand || ''} ${v.model || ''} · ${v.mileage}km`"
                is-link @click="openVehicle(v)">
        <template #value><span style="font-size:12px;color:#0d9488">下次保养 {{ v.nextMaintainDate || '未设置' }}</span></template>
      </van-cell>
      <van-empty v-if="!vehicles.length" image-size="40" description="暂无车辆档案，点击右上角添加" />

      <!-- 车辆新增/编辑弹窗 -->
      <van-popup v-model:show="vehicleShow" position="bottom" round>
        <div class="vehicle-pop">
          <div class="pop-title">{{ editingVehicle ? '编辑车辆' : '添加车辆' }}</div>
          <van-form @submit="saveVehicle">
            <van-cell-group inset>
              <van-field v-model="vform.plateNumber" label="车牌号" placeholder="如 浙A·12345" :rules="[{ required: true, message: '请填写车牌号' }]" />
              <van-field v-model="vform.brand" label="品牌" placeholder="如 大众" />
              <van-field v-model="vform.model" label="型号" placeholder="如 迈腾 380TSI" />
              <van-field v-model="vform.color" label="颜色" placeholder="如 黑色" />
              <van-field v-model="vform.mileage" type="number" label="里程(km)" placeholder="0" />
              <van-field v-model="vform.registerDate" type="date" label="上牌日期" />
              <van-field v-model="vform.nextMaintainDate" type="date" label="下次保养" />
            </van-cell-group>
            <div style="margin: 16px">
              <van-button round block type="primary" native-type="submit" color="#0d9488">保 存</van-button>
              <van-button v-if="editingVehicle" round block plain type="danger" style="margin-top: 10px" @click="removeVehicle">删除车辆</van-button>
            </div>
          </van-form>
        </div>
      </van-popup>

      <div class="card-title">我的优惠券</div>
      <van-cell v-for="c in coupons" :key="c.id" icon="coupon-o" :title="`券 #${c.couponId}`"
                :label="`${c.endTime ? String(c.endTime).slice(0, 10) + ' 前有效' : ''}`">
        <template #value>
          <van-tag :type="c.status === 1 ? 'success' : 'default'">{{ c.status === 1 ? '未使用' : c.status === 2 ? '已使用' : '已过期' }}</van-tag>
        </template>
      </van-cell>
      <van-empty v-if="!coupons.length" image-size="40" description="还没抢到券，去首页看看" />

      <div style="margin: 24px 12px">
        <van-button block round plain type="danger" @click="logout">退出登录</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onActivated, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { myCouponsApi, myVehiclesApi, createVehicleApi, updateVehicleApi, deleteVehicleApi, myProfileApi, updateAvatarApi, uploadApi } from '../api'

const router = useRouter()
const name = localStorage.getItem('app_name') || '车主'
const uid = localStorage.getItem('app_uid') || ''
const avatar = ref(localStorage.getItem('app_avatar') || '')
const fileInput = ref(null)
const vehicles = ref([])
const coupons = ref([])
const vehicleShow = ref(false)
const editingVehicle = ref(null)
const vform = reactive({ plateNumber: '', brand: '', model: '', color: '', mileage: '', registerDate: '', nextMaintainDate: '' })

async function load() {
  const [v, c] = await Promise.all([myVehiclesApi(), myCouponsApi()])
  vehicles.value = v.data
  coupons.value = c.data
  // 头像以服务端为准，本地缓存兜底
  const me = await myProfileApi()
  if (me.data?.avatar) {
    avatar.value = me.data.avatar
    localStorage.setItem('app_avatar', avatar.value)
  }
}
onActivated(load)
load()

function pickAvatar() {
  fileInput.value?.click()
}

/** 选图 → 上传 OSS → 更新用户头像，同步到本地缓存 */
async function onAvatarPick(e) {
  const file = e.target.files?.[0]
  e.target.value = '' // 允许连续选择同一张图
  if (!file) return
  if (!file.type.startsWith('image/')) return showToast('仅支持图片')
  if (file.size > 5 * 1024 * 1024) return showToast('图片不能超过 5MB')
  const fd = new FormData()
  fd.append('file', file)
  showToast('上传中...')
  try {
    const up = await uploadApi(fd)
    await updateAvatarApi(up.data)
    avatar.value = up.data
    localStorage.setItem('app_avatar', up.data)
    showToast('头像已更新')
  } catch (err) {
    /* 错误提示由 http 拦截器统一弹出 */
  }
}

function logout() {
  localStorage.clear()
  router.push('/login')
}

/** 打开车辆弹窗：不带参为新增，带车辆对象为编辑回填 */
function openVehicle(v) {
  editingVehicle.value = v || null
  vform.plateNumber = v?.plateNumber || ''
  vform.brand = v?.brand || ''
  vform.model = v?.model || ''
  vform.color = v?.color || ''
  vform.mileage = v?.mileage ?? ''
  vform.registerDate = v?.registerDate || ''
  vform.nextMaintainDate = v?.nextMaintainDate || ''
  vehicleShow.value = true
}

/** 保存（新增/编辑共用）：空字符串转 null 再提交，避免存空串 */
async function saveVehicle() {
  const body = { ...vform }
  Object.keys(body).forEach((k) => { if (body[k] === '') body[k] = null })
  if (body.mileage !== null) body.mileage = Number(body.mileage)
  try {
    if (editingVehicle.value) {
      await updateVehicleApi(editingVehicle.value.id, body)
      showToast('已保存')
    } else {
      await createVehicleApi(body)
      showToast('已添加')
    }
    vehicleShow.value = false
    load()
  } catch (err) {
    /* 错误提示由 http 拦截器统一弹出 */
  }
}

async function removeVehicle() {
  try {
    await showConfirmDialog({ title: '删除车辆', message: `确定删除 ${editingVehicle.value.plateNumber} 吗？` })
    await deleteVehicleApi(editingVehicle.value.id)
    showToast('已删除')
    vehicleShow.value = false
    load()
  } catch (err) {
    /* 用户取消或请求失败均静默 */
  }
}
</script>

<style scoped>
.hidden-file { display: none; }
.mine-ava {
  width: 46px; height: 46px; border-radius: 50%; overflow: hidden; margin-right: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #0d9488, #14b8a6); color: #fff; font-size: 18px; font-weight: 600;
  cursor: pointer;
}
.mine-ava img { width: 100%; height: 100%; object-fit: cover; }
.mine-name { font-size: 16px; font-weight: 600; color: #222; }
.mine-id { font-size: 12px; color: #999; margin-top: 2px; }
.add-btn { font-size: 13px; color: #0d9488; cursor: pointer; }
.vehicle-pop { padding: 16px 0 24px; }
.pop-title { text-align: center; font-size: 16px; font-weight: 600; margin-bottom: 12px; }
</style>
