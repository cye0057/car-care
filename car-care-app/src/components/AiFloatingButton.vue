<template>
  <!--
    AI 助手悬浮入口。
    放在需要的位置即可（首页/门店/我的）；因为主框架底部有 tabbar，
    默认 bottom 留出 70px 的避让空间，可用 bottom 属性覆盖。
  -->
  <div class="ai-fab" :style="{ bottom: bottom + 'px' }" @click="$router.push('/ai')">
    <span class="dot"></span>
    <span>AI 养车顾问</span>
  </div>
</template>

<script setup>
defineProps({
  bottom: { type: Number, default: 70 }
})
</script>

<style scoped>
.ai-fab {
  position: fixed; right: 14px; z-index: 100;
  display: flex; align-items: center; gap: 6px;
  padding: 9px 14px; border-radius: 22px;
  background: linear-gradient(135deg, #0d9488, #14b8a6);
  color: #fff; font-size: 13px; font-weight: 600;
  box-shadow: 0 4px 14px rgba(13, 148, 136, .38);
}
.ai-fab:active { transform: scale(.96); }
.dot { width: 6px; height: 6px; border-radius: 50%; background: #a7f3d0; box-shadow: 0 0 6px #a7f3d0; }
</style>
