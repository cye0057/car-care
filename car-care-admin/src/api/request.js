import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import router from '../router'

const request = axios.create({ baseURL: '/api', timeout: 10000 })

request.interceptors.request.use((config) => {
  const user = useUserStore()
  if (user.token) {
    config.headers.token = user.token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 1) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    if (status === 401 || status === 403) {
      const user = useUserStore()
      user.logout()
      router.push('/login')
      ElMessage.error(status === 401 ? '登录已过期，请重新登录' : '无权限访问')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
