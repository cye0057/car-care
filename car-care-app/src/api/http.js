import axios from 'axios'
import { showToast } from 'vant'
import router from '../router'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('app_token')
  if (token) config.headers.token = token
  return config
})

http.interceptors.response.use(
  (res) => {
    if (res.data.code !== 1) {
      showToast(res.data.msg || '请求失败')
      return Promise.reject(new Error(res.data.msg))
    }
    return res.data
  },
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('app_token')
      router.push('/login')
      showToast('登录已过期')
    } else {
      showToast('网络异常')
    }
    return Promise.reject(err)
  }
)

export default http
