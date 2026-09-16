<template>
  <div>
    <van-nav-bar title="优惠券秒杀" left-arrow @click-left="$router.back()" />
    <div class="page">
      <van-count-down v-if="closesAt" :time="closesAt - Date.now()" format="距本场结束 HH:mm:ss" class="cd" />
      <div v-for="c in coupons" :key="c.id" class="coupon">
        <div class="left">
          <div class="amt" v-if="c.type === 2">¥{{ c.cashPrice }}</div>
          <div class="amt" v-else>减{{ c.discountPrice }}</div>
          <div class="cond">满{{ c.minPrice }}可用</div>
        </div>
        <div class="mid">
          <b>{{ c.title }}</b>
          <div class="desc">{{ c.description }}</div>
          <div class="stock">剩余库存：{{ stockMap[c.id] ?? c.stock }}</div>
        </div>
        <van-button size="small" round type="danger" :loading="loadingMap[c.id]" @click="grab(c)">抢！</van-button>
      </div>
      <van-empty v-if="!coupons.length" description="暂无在售优惠券" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showToast, showDialog } from 'vant'
import { activeCouponsApi, seckillApi, seckillStockApi, seckillResultApi } from '../api'

const coupons = ref([])
const stockMap = reactive({})
const loadingMap = reactive({})
const closesAt = ref(0)

async function load() {
  const res = await activeCouponsApi()
  coupons.value = res.data
  if (res.data.length) {
    closesAt.value = new Date(res.data[0].validEndTime).getTime()
    res.data.forEach(async (c) => {
      const s = await seckillStockApi(c.id)
      stockMap[c.id] = s.data // Redis 预减视图，实时余量
    })
  }
}

async function grab(c) {
  loadingMap[c.id] = true
  try {
    const id = await seckillApi(c.id)
    // 异步链路：轮询领券结果（MQ 落库完成）
    poll(id.data, c.id)
  } catch { /* 售罄/重复领取的 toast 已由拦截器弹出 */ }
  finally { loadingMap[c.id] = false }
}

function poll(id, couponId) {
  let times = 0
  const timer = setInterval(async () => {
    const r = await seckillResultApi(id)
    if (r.data === 1 || ++times > 5) {
      clearInterval(timer)
      if (r.data === 1) {
        showDialog({ title: '🎉 抢购成功', message: '已放入「我的-优惠券」，异步落库完成' })
        seckillStockApi(couponId).then((s) => { stockMap[couponId] = s.data })
      } else {
        showToast('排队中，请稍后到「我的券」查看')
      }
    }
  }, 400)
}

onMounted(load)
</script>

<style scoped>
.cd { text-align: center; color: #ee0a24; font-size: 13px; margin-bottom: 10px; }
.coupon { display: flex; align-items: center; background: #fff; border-radius: 10px; padding: 14px 12px; margin-bottom: 10px; box-shadow: 0 1px 4px rgba(0,0,0,.05); }
.left { width: 84px; text-align: center; border-right: 1px dashed #eee; }
.amt { color: #ee0a24; font-size: 22px; font-weight: 800; }
.cond { font-size: 11px; color: #999; }
.mid { flex: 1; padding: 0 12px; font-size: 13px; }
.desc { color: #999; font-size: 11px; margin: 2px 0; }
.stock { color: #ee0a24; font-size: 11px; }
</style>
