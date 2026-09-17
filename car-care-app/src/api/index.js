import http from './http'

/* 通用图片上传（阿里云 OSS）：formData 字段名 file，返回 URL 字符串 */
export const uploadApi = (formData) =>
  http.post('/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })

/* 认证 */
export const loginApi = (data) => http.post('/auth/login', data)
export const registerApi = (data) => http.post('/auth/register', data)

/* 个人中心 */
export const myProfileApi = () => http.get('/users/me')
export const updateAvatarApi = (avatar) => http.put('/users/me', { avatar })

/* 首页：热榜 + 附近门店（GEO） */
export const hotStoresApi = (limit = 5) => http.get('/nearby/hot', { params: { limit } })
export const nearbyStoresApi = (lng, lat, radiusKm = 20) => http.get('/nearby/stores', { params: { lng, lat, radiusKm } })
export const storesApi = () => http.get('/stores/nearby')

/* 门店浏览 */
export const storeDetailApi = (id) => http.get(`/stores/${id}`)
export const storeItemsApi = (id) => http.get(`/stores/${id}/items`)
export const storePackagesApi = (id) => http.get(`/stores/${id}/packages`)
export const storeReviewsApi = (id) => http.get(`/stores/${id}/reviews`, { params: { pageSize: 5 } })

/* 秒杀 */
export const activeCouponsApi = () => http.get('/coupons/active')
export const seckillApi = (couponId) => http.post(`/seckill/coupons/${couponId}`)
export const seckillStockApi = (couponId) => http.get(`/seckill/coupons/${couponId}/stock`)
export const seckillResultApi = (id) => http.get(`/seckill/orders/${id}`)
export const myCouponsApi = () => http.get('/coupons/my')

/* 订单 */
export const createOrderApi = (data) => http.post('/orders', data)
export const payOrderApi = (id) => http.post(`/orders/${id}/pay`)
export const myOrdersApi = () => http.get('/orders/my')

/* 车辆档案 */
export const myVehiclesApi = () => http.get('/vehicles/my')
export const createVehicleApi = (data) => http.post('/vehicles', data)
export const updateVehicleApi = (id, data) => http.put(`/vehicles/${id}`, data)
export const deleteVehicleApi = (id) => http.delete(`/vehicles/${id}`)

/* Feed */
export const feedApi = (params) => http.get('/feed', { params })
export const publishApi = (data) => http.post('/reviews', data)
export const likeApi = (id) => http.post(`/reviews/${id}/like`)
export const unlikeApi = (id) => http.delete(`/reviews/${id}/like`)
export const followApi = (id) => http.post(`/follow/${id}`)
export const unfollowApi = (id) => http.delete(`/follow/${id}`)
