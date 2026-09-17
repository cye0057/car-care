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
      <van-popup v-model:show="showAppoint" position="bottom" round>
        <van-date-picker @confirm="onAppoint" @cancel="showAppoint = false" :min-date="new Date()" />
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

const goodsName = computed(() => item.value?.name || pkg.value?.name || '服务')
const unitPrice = computed(() => item.value?.price ?? pkg.value?.price ?? 0)
const number = computed(() => (itemId ? 1 : 1))
const amount = computed(() => (unitPrice.value * number.value).toFixed(2))
const vehicleText = computed(() => vehicle.value ? `${vehicle.value.plateNumber} ${vehicle.value.model || ''}` : '选择车辆')
const appointText = computed(() => appoint.value)
const vehicleColumns = computed(() => vehicles.value.map(v => ({ text: `${v.plateNumber}（${v.model || ''}）`, value: v })))

onMounted(async () => {
  if (!storeId || (!itemId && !packageId)) return // 缺参时不发起空请求
  const [v, items, packages] = await Promise.all([myVehiclesApi(), storeItemsApi(storeId), storePackagesApi(storeId)])
  vehicles.value = v.data
  if (itemId) item.value = items.data.find((x) => String(x.id) === String(itemId))
  if (packageId) pkg.value = packages.data.find((x) => String(x.pkg.id) === String(packageId))?.pkg
})

function onVehicle({ selectedValues }) { vehicle.value = selectedValues[0]; showVehicle.value = false }
function onAppoint({ selectedValues }) { appoint.value = selectedValues.join('-'); showAppoint.value = false }

async function submit() {
  if (!storeId || (!itemId && !packageId)) return showToast('请先从门店详情选择项目或套餐')
  submitting.value = true
  try {
    const body = { storeId: Number(storeId), vehicleId: vehicle.value?.id, remark: remark.value,
      appointmentTime: appoint.value ? `${appoint.value} 10:00:00` : null }
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
