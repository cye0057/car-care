<template>
  <!--
    管理台主布局：深色侧栏（可折叠，移动端变抽屉）+ 顶栏（面包屑/主题切换/用户菜单）+ 内容区。
    折叠状态桌面端持久化，移动端(<768px)强制收起并以遮罩抽屉呈现
  -->
  <el-container class="layout">
    <!-- 移动端抽屉遮罩 -->
    <div v-if="isMobile && drawerVisible" class="drawer-mask" @click="drawerVisible = false" />

    <el-aside :width="asideWidth" class="aside" :class="{ 'aside--mobile': isMobile }">
      <div class="logo" :class="{ 'logo--mini': collapsed && !isMobile }">
        <span class="logo-mark">🚗</span>
        <span v-show="!collapsed || isMobile" class="logo-text">车管家管理台</span>
      </div>
      <el-menu :default-active="$route.path" router :collapse="collapsed && !isMobile"
               background-color="transparent" text-color="var(--cc-sidebar-text)" active-text-color="var(--cc-sidebar-active)">
        <el-tooltip v-for="m in menus" :key="m.path" :content="m.title" placement="right" :disabled="!collapsed || isMobile">
          <el-menu-item :index="m.path" :class="{ 'menu-item--active': $route.path === m.path }">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>{{ m.title }}</template>
          </el-menu-item>
        </el-tooltip>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button class="hamburger" text :aria-label="isMobile ? '打开菜单' : '折叠菜单'" @click="toggleAside">
            <el-icon size="18"><Expand v-if="collapsed && !isMobile" /><Fold v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- 来单提醒：WebSocket 消息铃铛，点击展开通知面板 -->
          <el-popover placement="bottom-end" :width="320" trigger="click" popper-class="notify-pop">
            <template #reference>
              <el-badge :value="unread" :hidden="unread === 0" :max="99">
                <el-button text circle aria-label="通知" class="bell-btn">
                  <el-icon size="17"><Bell /></el-icon>
                </el-button>
              </el-badge>
            </template>
            <div class="notify-head">
              <span>通知（{{ notifications.length }}）</span>
              <el-button v-if="notifications.length" link size="small" @click="clearNotify">清空</el-button>
            </div>
            <el-scrollbar max-height="280px">
              <div v-if="!notifications.length" class="notify-empty">暂无通知，保持页面开启等待来单</div>
              <div v-for="(n, i) in notifications" :key="i" class="notify-item">
                <el-tag size="small" :type="n.type === 'order' ? 'warning' : 'primary'" effect="light">
                  {{ n.type === 'order' ? '新订单' : '工单' }}
                </el-tag>
                <div class="notify-text">{{ n.text }}</div>
                <div class="notify-time">{{ n.time }}</div>
              </div>
            </el-scrollbar>
          </el-popover>
          <el-tooltip :content="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'">
            <el-button text circle :aria-label="'切换主题'" @click="toggle">
              <el-icon size="17"><Sunny v-if="theme === 'dark'" /><Moon v-else /></el-icon>
            </el-button>
          </el-tooltip>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-avatar :size="28" class="user-avatar">{{ user.name.charAt(0) || '管' }}</el-avatar>
              <span class="user-name">{{ user.name }}</span>
              <el-icon size="12"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ user.role === 0 ? '系统管理员' : '车主' }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.path" class="page-enter" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElNotification } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { useTheme } from '../composables/useTheme'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const { theme, toggle } = useTheme()

const menus = [
  { path: '/dashboard', title: '工作台', icon: 'Odometer' },
  { path: '/stores', title: '门店管理', icon: 'Shop' },
  { path: '/categories', title: '服务分类', icon: 'Folder' },
  { path: '/items', title: '保养项目', icon: 'Tools' },
  { path: '/coupons', title: '优惠券', icon: 'Ticket' },
  { path: '/orders', title: '订单管理', icon: 'List' }
]

/* 移动端判定：768px 以下侧栏变抽屉 */
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
const collapsed = ref(localStorage.getItem('cc_aside_collapsed') === '1')
const drawerVisible = ref(false)

function onResize() {
  const mobile = window.innerWidth < MOBILE_BP
  if (mobile !== isMobile.value) {
    isMobile.value = mobile
    drawerVisible.value = false
  }
}
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const asideWidth = computed(() => {
  if (isMobile.value) return drawerVisible.value ? 'var(--cc-sidebar-w)' : '0px'
  return collapsed.value ? 'var(--cc-sidebar-w-collapsed)' : 'var(--cc-sidebar-w)'
})

function toggleAside() {
  if (isMobile.value) {
    drawerVisible.value = !drawerVisible.value
  } else {
    collapsed.value = !collapsed.value
    localStorage.setItem('cc_aside_collapsed', collapsed.value ? '1' : '0')
  }
}

/* 移动端点击菜单后自动收起抽屉 */
watch(() => route.path, () => { drawerVisible.value = false })

/* ---------- 来单提醒 WebSocket ---------- */
const notifications = ref([])
const unread = ref(0)
let ws = null
let heartbeatTimer = null
let reconnectTimer = null
let closedByUs = false

function connectWs() {
  if (!user.token) return
  closedByUs = false
  // 8081 是 vite 端口，后端固定 8082；wss 场景生产由 Nginx 代理
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.hostname}:8082/ws?token=${user.token}`)
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'pong') return
      notifications.value.unshift(msg)
      if (notifications.value.length > 30) notifications.value.pop()
      unread.value++
      ElNotification({ title: msg.type === 'order' ? '📋 新订单' : '🔧 工单动态', message: msg.text, type: 'warning', duration: 4000 })
    } catch { /* 忽略非 JSON 帧 */ }
  }
  ws.onopen = () => {
    heartbeatTimer = setInterval(() => ws?.readyState === WebSocket.OPEN && ws.send('ping'), 30000)
  }
  ws.onclose = () => {
    clearInterval(heartbeatTimer)
    // 非主动断开且仍登录 → 3s 后重连
    if (!closedByUs && user.token && !reconnectTimer) {
      reconnectTimer = setTimeout(() => { reconnectTimer = null; connectWs() }, 3000)
    }
  }
}

function clearNotify() {
  notifications.value = []
  unread.value = 0
}

onMounted(connectWs)
onBeforeUnmount(() => {
  closedByUs = true
  clearInterval(heartbeatTimer)
  clearTimeout(reconnectTimer)
  ws?.close()
})

function onCommand(cmd) {
  if (cmd === 'logout') {
    closedByUs = true
    ws?.close()
    user.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100%; }

/* ---------- 侧栏 ---------- */
.aside {
  background: linear-gradient(180deg, var(--cc-sidebar-bg-top), var(--cc-sidebar-bg-bottom));
  transition: width var(--cc-duration-base) var(--cc-ease);
  overflow: hidden;
  position: relative;
  z-index: 30;
}
.aside--mobile { position: fixed; left: 0; top: 0; bottom: 0; }
.drawer-mask {
  position: fixed; inset: 0; z-index: 20;
  background: rgba(15, 23, 42, 0.45);
}
.logo {
  display: flex; align-items: center; gap: 8px;
  height: var(--cc-header-h); padding: 0 18px;
  color: #fff; white-space: nowrap; overflow: hidden;
}
.logo--mini { justify-content: center; padding: 0; }
.logo-mark { font-size: 20px; }
.logo-text { font-size: 15px; font-weight: 600; letter-spacing: 0.5px; }
.el-menu { border-right: none; }
.menu-item--active { background: var(--cc-sidebar-active-bg) !important; border-radius: 8px; }

/* ---------- 顶栏 ---------- */
.header {
  height: var(--cc-header-h);
  display: flex; align-items: center; justify-content: space-between;
  background: var(--cc-bg-card);
  border-bottom: 1px solid var(--cc-border);
  transition: background-color var(--cc-duration-base) var(--cc-ease);
}
.header-left { display: flex; align-items: center; gap: 8px; }
.header-right { display: flex; align-items: center; gap: 12px; }
.hamburger { color: var(--cc-text-secondary); }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; color: var(--cc-text-regular); outline: none; }
.user-avatar { background: linear-gradient(135deg, var(--cc-primary-light), var(--cc-primary-dark)); color: #fff; font-size: 13px; }
.user-name { font-size: 14px; }

/* ---------- 通知面板 ---------- */
.bell-btn { color: var(--cc-text-secondary); }
.notify-head { display: flex; align-items: center; justify-content: space-between; font-size: 13px; font-weight: 600; color: var(--cc-text-primary); padding-bottom: 8px; border-bottom: 1px solid var(--cc-divider); }
.notify-empty { text-align: center; color: var(--cc-text-placeholder); font-size: 12px; padding: 24px 0; }
.notify-item { padding: 10px 2px; border-bottom: 1px solid var(--cc-divider); }
.notify-item:last-child { border-bottom: none; }
.notify-text { font-size: 13px; color: var(--cc-text-regular); margin-top: 4px; line-height: 1.5; }
.notify-time { font-size: 11px; color: var(--cc-text-placeholder); margin-top: 2px; }

/* ---------- 内容区 ---------- */
.main {
  background: var(--cc-bg-page);
  padding: var(--cc-space-6);
  max-width: calc(var(--cc-content-max) + var(--cc-space-6) * 2);
  margin: 0 auto; width: 100%;
  box-sizing: border-box;
  overflow-x: auto;
}

/* 移动端内边距收窄 */
@media (max-width: 767px) {
  .main { padding: var(--cc-space-3); }
  .user-name { display: none; }
}
</style>
