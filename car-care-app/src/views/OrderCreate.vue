<template>
  <div>
    <van-nav-bar title="确认订单" left-arrow @click-left="$router.back()" />
    <div class="page">
      <van-cell-group inset>
        <van-cell title="选择车辆" :value="vehicleText" is-link @click="showVehicle = true" />
        <van-cell title="预约到店" :value="appointText || '选填'" is-link @click="showAppoint = true" />
        <van-field v-model="remark" label="备注" placeholder="如：异响检查、洗车" />
      </van-cell-group>

      <!-- 无商品参数进入（未从门店详情跳转）时给出提示，避免空请求与 ¥0 误导 -->
      <van-empty v-if="!itemId && !packageId" description="请先从门店详情选择项目或套餐" />
      <div class="card-title">费用</div>
      <van-cell-group inset>
        <van-cell :title="goodsName" :label="`¥${unitPrice} × ${number}`" :value="`¥${amount}`" />
        <van-cell title="合计"><template #value><span class="price">¥{{ amount }}</span></template></van-cell>
      </van-cell-group>

      <div style="margin: 20px 12px">
        <van-button block round color="#0d9488" :loading="submitting" @click="submit">提交订单</van-button>
      </div>

      <van-popup v-model:show="showVehicle" position="bottom" round>
        <van-picker :columns="vehicleColumns" @confirm="onVehicle" @cancel="showVehicle = false" />
      </van-popup>

      <!-- 日期 + 时间两步选择：原先只选日期、时间被写死成 10:00，
           导致用户选不出实际到店时间，AI 草稿里的时间也没法如实带过来 -->
      <van-popup v-model:show="showAppoint" position="bottom" round>
        <van-picker-group
          title="预约到店"
          :tabs="['选择日期', '选择时间']"
          next-step-text="下一步"
          @confirm="onAppoint"
          @cancel="showAppoint = false"
        >
          <van-date-picker v-model="pickDate" :min-date="minDate" />
          <van-time-picker v-model="pickTime" />
        </van-picker-group>
      </van-popup>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { createOrderApi, myVehiclesApi, storeItemsApi, storePackagesApi } from '../api'

const route = useRoute()
const router = useRouter()
const storeId = route.query.storeId
const itemId = route.query.itemId
const packageId = route.query.packageId

const vehicles = ref([])
const vehicle = ref(null)
const item = ref(null)
const pkg = ref(null)
const remark = ref('')
const appoint = ref('')
const showVehicle = ref(false)
const showAppoint = ref(false)
const submitting = ref(false)

// 日期/时间选择器各自绑定一个字符串数组，确认时拼成 "yyyy-MM-dd HH:mm"
const today = new Date()
today.setHours(0, 0, 0, 0)
const minDate = today
const pad = (n) => String(n).padStart(2, '0')
const pickDate = ref([String(today.getFullYear()), pad(today.getMonth() + 1), pad(today.getDate())])
const pickTime = ref(['09', '00'])

const goodsName = computed(() => item.value?.name || pkg.value?.name || '服务')
const unitPrice = computed(() => item.value?.price ?? pkg.value?.price ?? 0)
const number = computed(() => (itemId ? 1 : 1))
const amount = computed(() => (unitPrice.value * number.value).toFixed(2))
const vehicleText = computed(() => vehicle.value ? `${vehicle.value.plateNumber} ${vehicle.value.model || ''}` : '选择车辆')
const appointText = computed(() => appoint.value)
const vehicleColumns = computed(() => vehicles.value.map(v => ({ text: `${v.plateNumber}（${v.model || ''}）`, value: v })))

// AI 草稿给的到店时间是模型从用户话里抽的自由文本，格式不可信，
// 直接塞进下单请求会被后端的 @JsonFormat("yyyy-MM-dd HH:mm:ss") 反序列化失败 → 提交直接 400。
// 所以只认 yyyy-MM-dd HH:mm（允许末尾多带秒），其余一律当没传。
const APPOINT_RE = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/

function parseAppoint(raw) {
  if (typeof raw !== 'string') return null
  const m = APPOINT_RE.exec(raw.trim())
  if (!m) return null
  const [, y, mo, d, h, mi] = m
  const date = new Date(Number(y), Number(mo) - 1, Number(d))
  if (Number.isNaN(date.getTime())) return null
  // 早于今天的日期不预填：选择器的下限就是今天，塞进去会选中一个非法值
  if (date < minDate) return null
  return { date: [y, mo, d], time: [h, mi], text: `${y}-${mo}-${d} ${h}:${mi}` }
}

function applyPrefill() {
  const vid = Number(route.query.vehicleId)
  if (vid) {
    // 车辆可能已被删除（AI 上下文是请求时刻的快照），找不到就留空让用户自己选
    const found = vehicles.value.find((x) => Number(x.id) === vid)
    if (found) vehicle.value = found
  }
  const parsed = parseAppoint(route.query.appointmentTime)
  if (parsed) {
    pickDate.value = parsed.date
    pickTime.value = parsed.time
    appoint.value = parsed.text
  }
}

onMounted(async () => {
  if (!storeId || (!itemId && !packageId)) return // 缺参时不发起空请求
  const [v, items, packages] = await Promise.all([myVehiclesApi(), storeItemsApi(storeId), storePackagesApi(storeId)])
  vehicles.value = v.data
  if (itemId) item.value = items.data.find((x) => String(x.id) === String(itemId))
  if (packageId) pkg.value = packages.data.find((x) => String(x.pkg.id) === String(packageId))?.pkg
  applyPrefill()
})

function onVehicle({ selectedValues }) { vehicle.value = selectedValues[0]; showVehicle.value = false }

// picker-group 的 confirm 回传的是每个子选择器的结果数组：[{selectedValues:['2026','10','08']}, {selectedValues:['09','00']}]
function onAppoint(payload) {
  const [d, t] = payload || []
  const date = d?.selectedValues || pickDate.value
  const time = t?.selectedValues || pickTime.value
  appoint.value = `${date.join('-')} ${time.join(':')}`
  showAppoint.value = false
}

async function submit() {
  if (!storeId || (!itemId && !packageId)) return showToast('请先从门店详情选择项目或套餐')
  submitting.value = true
  try {
    const body = { storeId: Number(storeId), vehicleId: vehicle.value?.id, remark: remark.value,
      appointmentTime: appoint.value ? `${appoint.value}:00` : null }
    if (itemId) Object.assign(body, { itemId: Number(itemId), number: 1 })
    if (packageId) Object.assign(body, { packageId: Number(packageId) })
    const res = await createOrderApi(body)
    showToast('下单成功，请在订单列表完成支付')
    router.push('/orders')
  } finally {
    submitting.value = false
  }
}
</script>
