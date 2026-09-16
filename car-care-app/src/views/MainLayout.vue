<template>
  <!-- 主框架：四个 Tab + 路由缓存切换 -->
  <div class="main">
    <keep-alive>
      <router-view />
    </keep-alive>
    <van-tabbar v-model="active" active-color="#0d9488" @change="onChange">
      <van-tabbar-item name="home" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item name="feed" icon="notes-o">笔记</van-tabbar-item>
      <van-tabbar-item name="orders" icon="balance-list-o">订单</van-tabbar-item>
      <van-tabbar-item name="mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const active = ref(route.meta.tab || 'home')

watch(() => route.path, () => { active.value = route.meta.tab || active.value })

function onChange(name) {
  router.push('/' + (name === 'home' ? 'home' : name))
}
</script>

<style scoped>
.main { min-height: 100%; }
</style>
