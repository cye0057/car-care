<template>
  <div>
    <van-nav-bar title="车管家">
      <template #right><span style="font-size:12px">{{ name }}</span></template>
    </van-nav-bar>
    <div class="page">
      <!-- 抢券入口横幅：Vant 4 已移除 van-banner 组件，用自定义卡片实现 -->
      <div class="banner" @click="$router.push('/seckill')">
        <span>🎫 保养神券限量秒杀 · 手慢无</span><span class="arrow">→</span>
      </div>

      <div class="card-title">🔥 人气门店</div>
      <van-cell v-for="(s, i) in hot" :key="s.id" :title="`${i + 1}. ${s.name}`" :label="s.address" is-link
               :value="`热度 ${s.hotScore}`" @click="$router.push(`/store/${s.id}`)" />

      <div class="card-title">📍 附近门店（GEO 定位）</div>
      <van-cell v-for="s in nearby" :key="s.id" :title="s.name" :label="s.address" is-link
               :value="`${s.distanceKm} km`" @click="$router.push(`/store/${s.id}`)" />
      <van-empty v-if="!nearby.length" description="当前定位 20km 内暂无门店" image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { onActivated, ref } from 'vue'
import { hotStoresApi, nearbyStoresApi } from '../api'

const name = localStorage.getItem('app_name') || ''
const hot = ref([])
const nearby = ref([])

async function load() {
  // 演示坐标：西湖文化广场；真实场景 navigator.geolocation 获取后换算 GCJ-02
  const [h, n] = await Promise.all([hotStoresApi(), nearbyStoresApi(120.16, 30.27)])
  hot.value = h.data
  nearby.value = n.data
}
onActivated(load)
load()
</script>

<style scoped>
.banner { display: flex; align-items: center; justify-content: space-between; background: linear-gradient(90deg, #0d9488, #14b8a6); color: #fff; border-radius: 10px; padding: 13px 14px; font-size: 15px; font-weight: 600; box-shadow: 0 2px 8px rgba(13, 148, 136, .28); }
.banner .arrow { font-size: 18px; opacity: .9; }
</style>
