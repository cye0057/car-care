<template>
  <div class="login">
    <div class="logo">🚗</div>
    <h2>车管家</h2>
    <p class="sub">维修保养 · 一键预约</p>

    <van-tabs v-model:active="tab" color="#0d9488" title-active-color="#0d9488" class="tabs">
      <van-tab title="登录">
        <van-form @submit="doLogin" class="form">
          <van-cell-group inset>
            <van-field v-model="form.username" label="账号" placeholder="演示 user01~user30" :rules="[{ required: true }]" />
            <van-field v-model="form.password" type="password" label="密码" placeholder="123456" :rules="[{ required: true }]" />
          </van-cell-group>
          <div style="margin: 24px 16px">
            <van-button round block type="primary" native-type="submit" :loading="loading" color="#0d9488">登 录</van-button>
          </div>
        </van-form>
      </van-tab>

      <van-tab title="注册">
        <van-form @submit="doRegister" class="form">
          <van-cell-group inset>
            <van-field v-model="reg.username" label="账号" placeholder="4-20位字母数字" :rules="[{ required: true, message: '请填写账号' }]" />
            <van-field v-model="reg.password" type="password" label="密码" placeholder="6-20位，不能有空格" :rules="[{ required: true, message: '请填写密码' }]" />
            <van-field v-model="reg.name" label="昵称" placeholder="怎么称呼你" :rules="[{ required: true, message: '请填写昵称' }]" />
            <van-field v-model="reg.phone" type="tel" label="手机号" placeholder="选填" />
          </van-cell-group>
          <div style="margin: 24px 16px">
            <van-button round block type="primary" native-type="submit" :loading="registering" color="#0d9488">注 册</van-button>
          </div>
        </van-form>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { loginApi, registerApi } from '../api'

const router = useRouter()
const tab = ref(0)
const loading = ref(false)
const registering = ref(false)
const form = reactive({ username: 'user01', password: '' })
const reg = reactive({ username: '', password: '', name: '', phone: '' })

async function doLogin() {
  loading.value = true
  try {
    const res = await loginApi(form)
    localStorage.setItem('app_token', res.data.token)
    localStorage.setItem('app_uid', res.data.userId)
    localStorage.setItem('app_name', res.data.name)
    localStorage.setItem('app_avatar', res.data.avatar || '')
    router.push('/home')
  } finally {
    loading.value = false
  }
}

/** 注册成功切回登录页签，账号自动带过去方便直接登录 */
async function doRegister() {
  registering.value = true
  try {
    await registerApi(reg)
    showToast('注册成功，请登录')
    form.username = reg.username
    form.password = ''
    tab.value = 0
  } finally {
    registering.value = false
  }
}
</script>

<style scoped>
.login { padding-top: 12vh; text-align: center; }
.logo { font-size: 56px; }
h2 { margin: 8px 0 2px; color: #0d9488; }
.sub { color: #999; font-size: 13px; margin-bottom: 24px; }
.tabs { max-width: 420px; margin: 0 auto; }
.form { padding-top: 16px; }
</style>
