<template>
  <div>
    <van-nav-bar title="我的订单">
      <template #right><van-icon name="replay" size="18" @click="load" /></template>
    </van-nav-bar>
    <div class="page">
      <van-empty v-if="!orders.length" description="还没有订单，去首页逛逛" />
      <div v-for="o in orders" :key="o.id" class="order">
        <div class="head">
          <b>{{ o.storeName }}</b>
          <van-tag :type="tagType(o.status)">{{ statusText(o.status) }}</van-tag>
        </div>
        <div class="no">订单号 {{ o.orderNo }}</div>
        <div class="row">
          <span>实付 <span class="price">¥{{ o.actualAmount }}</span></span>
          <span class="time">{{ fmt(o.orderTime) }}</span>
        </div>
        <div class="row" v-if="o.remark"><span class="remark">备注：{{ o.remark }}</span></div>
        <div class="acts" v-if="o.status === 1">
          <van-button size="small" round @click.stop="cancel(o)">取消</van-button>
          <van-button size="small" round type="primary" color="#0d9488" @click.stop="pay(o)">去支付</van-button>
        </div>
        <div class="acts" v-else-if="o.status === 4">
          <van-button size="small" round type="primary" color="#0d9488" @click.stop="review(o)">去评价</van-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onActivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { myOrdersApi, payOrderApi } from '../api'
import http from '../api/http'

const router = useRouter()
const orders = ref([])

const statusText = (s) => ({ 1: '待支付', 2: '已支付', 3: '施工中', 4: '已完工', 5: '已取消', 6: '已评价' }[s] || s)
const tagType = (s) => ({ 1: 'warning', 2: 'primary', 3: 'primary', 4: 'success', 5: 'default', 6: 'success' }[s])
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')

async function load() {
  const res = await myOrdersApi()
  orders.value = res.data.records
}
onActivated(load)
load()

async function pay(o) {
  const res = await payOrderApi(o.id)
  // 打开支付宝沙箱收银台；支付成功后后端异步回调置订单已支付
  window.open(res.data, '_blank')
  showToast('请在打开的页面完成支付')
  load()
}

/** 完工订单可评价：把门店信息一并带过去，发布页锁定 orderId 并只读展示门店 */
function review(o) {
  router.push({
    path: '/publish',
    query: { orderId: o.id, orderNo: o.orderNo, storeId: o.storeId, storeName: o.storeName }
  })
}

async function cancel(o) {
  await http.delete(`/orders/${o.id}`)
  showToast('已取消')
  load()
}
</script>

<style scoped>
.order { background: #fff; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.head { display: flex; justify-content: space-between; align-items: center; }
.no { color: #aaa; font-size: 11px; margin: 4px 0; }
.row { display: flex; justify-content: space-between; font-size: 13px; margin-top: 4px; }
.time { color: #999; font-size: 12px; }
.remark { color: #777; font-size: 12px; }
.acts { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
</style>
