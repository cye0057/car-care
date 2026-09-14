import { ref, watchEffect } from 'vue'

const KEY = 'cc_theme'
const theme = ref(localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light')

/** 立即应用一次，避免首屏闪烁 */
watchEffect(() => {
  document.documentElement.classList.toggle('dark', theme.value === 'dark')
  localStorage.setItem(KEY, theme.value)
})

/**
 * 全局主题 composable：返回当前主题与切换方法。
 * 单例 ref 定义在模块顶层，所有组件共享同一份状态
 */
export function useTheme() {
  const toggle = () => {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }
  return { theme, toggle }
}
