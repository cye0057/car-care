<template>
  <!--
    工作台：问候语 + 四张统计卡（渐变顶条区分色彩语义）+ 最近订单表。
    统计走 /admin/dashboard/stats 聚合接口；订单取前 8 条展示。
    两请求并行发出（Promise.all），骨架屏过渡避免白屏
  -->
  <div class="dash" v-loading="loading">
    <div class="greet">
      <h2>{{ greeting }}，{{ user.name }}</h2>
      <p>今天是 {{ todayLabel }}，祝你工作顺利</p>
    </div>

    <el-row :gutter="16" class="cards">
      <el-col v-for="c in cards" :key="c.label" :xs="12" :sm="12" :md="6">
        <div class="stat-card" :style="{ '--accent': c.color }">
          <div class="stat-icon"><el-icon><component :is="c.icon" /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">{{ c.label }}</div>
            <div class="stat-value cc-num">{{ c.value }}</div>
            <div class="stat-extra">{{ c.extra }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <div class="cc-card table-card">
          <div class="cc-page-head">
            <div>
              <h2>最近订单</h2>
              <p class="cc-page-desc">按下单时间倒序，最多展示 8 条</p>
            </div>
            <el-button text type="primary" @click="$router.push('/orders')">全部订单 →</el-button>
          </div>
          <el-table :data="orders" empty-text="暂无订单数据">
            <el-table-column prop="orderNo" label="订单号" width="110" />
            <el-table-column prop="userName" label="车主" width="80" />
            <el-table-column prop="storeName" label="门店" min-width="160" show-overflow-tooltip />
            <el-table-column label="金额" width="100">
              <template #default="{ row }"><span class="cc-num">¥{{ row.actualAmount }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusMap[row.status]?.type || 'info'" effect="light" round>
                  {{ statusMap[row.status]?.text }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="cc-card">
          <div class="cc-page-head">
            <div>
              <h2>快捷入口</h2>
              <p class="cc-page-desc">高频操作直达</p>
            </div>
          </div>
          <div class="quick-grid">
            <div v-for="q in quickLinks" :key="q.path" class="quick-item" @click="$router.push(q.path)">
              <el-icon><component :is="q.icon" /></el-icon><span>{{ q.title }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { dashboardStatsApi, orderPageApi } from '../api'
import { useUserStore } from '../store/user'

const statusMap = {
  1: { text: '待支付', type: 'warning' },
  2: { text: '已支付', type: 'primary' },
  3: { text: '施工中', type: 'primary' },
  4: { text: '已完工', type: 'success' },
  5: { text: '已取消', type: 'info' },
  6: { text: '已评价', type: 'success' }
}

const user = useUserStore()
const loading = ref(true)
const stats = ref({})
const orders = ref([])

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})
const todayLabel = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const cards = computed(() => [
  { label: '营业门店', value: stats.value.storeCount ?? '-', extra: '门店网络规模', icon: 'Shop', color: 'linear-gradient(90deg,#2dd4bf,#0d9488)' },
  { label: '在售项目', value: stats.value.itemCount ?? '-', extra: '覆盖保养/维修/美容', icon: 'Tools', color: 'linear-gradient(90deg,#22c55e,#16a34a)' },
  { label: '累计订单', value: stats.value.orderCount ?? '-', extra: `今日新增 ${stats.value.todayOrderCount ?? 0}`, icon: 'List', color: 'linear-gradient(90deg,#f59e0b,#d97706)' },
  { label: '待处理', value: (stats.value.pendingCount ?? 0) + (stats.value.workingCount ?? 0), extra: `待支付 ${stats.value.pendingCount ?? 0} · 施工中 ${stats.value.workingCount ?? 0}`, icon: 'Bell', color: 'linear-gradient(90deg,#ef4444,#dc2626)' }
])

const quickLinks = [
  { path: '/stores', title: '管理门店', icon: 'Shop' },
  { path: '/items', title: '上架项目', icon: 'Tools' },
  { path: '/coupons', title: '配置优惠券', icon: 'Ticket' },
  { path: '/orders', title: '处理订单', icon: 'List' }
]

onMounted(async () => {
  try {
    const [s, o] = await Promise.all([
      dashboardStatsApi(),
      orderPageApi({ pageNum: 1, pageSize: 8 })
    ])
    stats.value = s.data
    orders.value = o.data.records
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.greet h2 { margin: 0; font-size: 22px; color: var(--cc-text-primary); }
.greet p { margin: 6px 0 20px; color: var(--cc-text-secondary); font-size: 13px; }

.stat-card {
  position: relative;
  background: var(--cc-bg-card);
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-radius-lg);
  box-shadow: var(--cc-shadow-card);
  padding: 18px;
  display: flex;
  gap: 14px;
  margin-bottom: 16px;
  overflow: hidden;
  cursor: default;
  transition: transform var(--cc-duration-fast) var(--cc-ease), box-shadow var(--cc-duration-fast) var(--cc-ease);
}
.stat-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--accent);
}
.stat-card:hover { transform: translateY(-2px); box-shadow: var(--cc-shadow-pop); }
.stat-icon {
  width: 46px; height: 46px;
  border-radius: var(--cc-radius-md);
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; color: #fff;
  background: var(--accent);
  flex-shrink: 0;
}
.stat-label { font-size: 13px; color: var(--cc-text-secondary); }
.stat-value { font-size: 28px; line-height: 1.2; color: var(--cc-text-primary); }
.stat-extra { font-size: 12px; color: var(--cc-text-placeholder); margin-top: 2px; }

.table-card { margin-bottom: 0; }
.cc-card { margin-bottom: 16px; }

.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.quick-item {
  display: flex; align-items: center; gap: 8px;
  padding: 12px;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-radius-md);
  font-size: 13px;
  color: var(--cc-text-regular);
  cursor: pointer;
  transition: border-color var(--cc-duration-fast), color var(--cc-duration-fast), background var(--cc-duration-fast);
}
.quick-item:hover {
  border-color: var(--cc-primary);
  color: var(--cc-primary);
  background: color-mix(in srgb, var(--cc-primary) 6%, transparent);
}

</style>
