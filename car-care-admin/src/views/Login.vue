<template>
  <!--
    登录页：桌面端左侧品牌区 + 右侧表单卡片；<768px 隐藏品牌区只留表单。
    品牌区使用纯 CSS 渐变与光斑，无外部图片依赖
  -->
  <div class="login-page">
    <div class="brand-panel">
      <div class="brand-top">
        <div class="brand-logo">🚗 车管家</div>
        <h1>汽车维修保养服务系统</h1>
        <p class="brand-sub">门店运营 · 工单流转 · 营销券管理 一体化管理台</p>
      </div>
      <ul class="brand-points">
        <li><el-icon><CircleCheck /></el-icon> 订单全状态机流转，异常操作即时拦截</li>
        <li><el-icon><CircleCheck /></el-icon> 套餐与服务项联动约束，防止配置死角</li>
        <li><el-icon><CircleCheck /></el-icon> 缓存 · 秒杀 · GEO · Feed 流持续演进中</li>
      </ul>
    </div>

    <div class="form-panel">
      <div class="login-card">
        <h2>欢迎回来</h2>
        <p class="sub">请使用管理员账号登录</p>
        <el-form :model="form" @keyup.enter="doLogin">
          <el-form-item>
            <el-input v-model="form.username" placeholder="账号" size="large" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
            登 录
          </el-button>
        </el-form>
        <p class="demo-tip">演示账号：admin / 123456</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { loginApi } from '../api'
import { useUserStore } from '../store/user'

const router = useRouter()
const user = useUserStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

async function doLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const res = await loginApi(form)
    if (res.data.role !== 0) {
      ElMessage.error('该账号不是管理员，无法登录管理台')
      return
    }
    user.setLogin(res.data)
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { height: 100%; display: flex; }

/* ---------- 左侧品牌区 ---------- */
.brand-panel {
  flex: 1.1;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
  color: #fff;
  background: linear-gradient(150deg, #042f2e 0%, #0f766e 60%, #14b8a6 100%);
  overflow: hidden;
}
.brand-panel::before,
.brand-panel::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}
.brand-panel::before { width: 420px; height: 420px; right: -140px; top: -120px; }
.brand-panel::after { width: 260px; height: 260px; left: -80px; bottom: -60px; }
.brand-logo { font-size: 22px; font-weight: 700; letter-spacing: 1px; }
h1 { font-size: 30px; line-height: 1.4; margin: 16px 0 8px; font-weight: 700; }
.brand-sub { color: rgba(255, 255, 255, 0.75); margin: 0; font-size: 14px; }
.brand-points { list-style: none; padding: 0; margin: 0; font-size: 14px; color: rgba(255, 255, 255, 0.85); }
.brand-points li { display: flex; align-items: center; gap: 8px; margin-top: 14px; }

/* ---------- 右侧表单区 ---------- */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cc-bg-page);
}
.login-card {
  width: 360px;
  padding: 40px 36px;
  background: var(--cc-bg-card);
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-radius-xl);
  box-shadow: var(--cc-shadow-pop);
}
h2 { margin: 0 0 4px; font-size: 22px; color: var(--cc-text-primary); }
.sub { margin: 0 0 28px; color: var(--cc-text-secondary); font-size: 13px; }
.demo-tip { text-align: center; color: var(--cc-text-placeholder); font-size: 12px; margin: 18px 0 0; }

/* ---------- 响应式：平板收窄、手机隐藏品牌区 ---------- */
@media (max-width: 1023px) {
  .brand-panel { padding: 32px; }
  h1 { font-size: 24px; }
}
@media (max-width: 767px) {
  .brand-panel { display: none; }
  .form-panel { padding: 16px; }
  .login-card { width: 100%; box-shadow: var(--cc-shadow-card); }
}
</style>
