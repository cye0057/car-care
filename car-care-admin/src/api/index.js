import request from './request'

export const loginApi = (data) => request.post('/auth/login', data)

/* 通用图片上传（阿里云 OSS）：formData 字段名 file，返回 URL 字符串 */
export const uploadApi = (formData) =>
  request.post('/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })

export const dashboardStatsApi = () => request.get('/admin/dashboard/stats')

export const storePageApi = (params) => request.get('/admin/stores', { params })
export const storeListEnabledApi = () => request.get('/admin/stores/enabled')
export const storeSaveApi = (data) => request.post('/admin/stores', data)
export const storeUpdateApi = (data) => request.put('/admin/stores', data)
export const storeDeleteApi = (id) => request.delete(`/admin/stores/${id}`)

export const categoryListApi = () => request.get('/admin/categories')
export const categorySaveApi = (data) => request.post('/admin/categories', data)
export const categoryUpdateApi = (data) => request.put('/admin/categories', data)
export const categoryDeleteApi = (id) => request.delete(`/admin/categories/${id}`)

export const itemPageApi = (params) => request.get('/admin/items', { params })
export const itemSaveApi = (data) => request.post('/admin/items', data)
export const itemUpdateApi = (data) => request.put('/admin/items', data)
export const itemStatusApi = (id, status) => request.put(`/admin/items/${id}/status/${status}`)
export const itemDeleteApi = (id) => request.delete(`/admin/items/${id}`)

export const couponPageApi = (params) => request.get('/admin/coupons', { params })
export const couponSaveApi = (data) => request.post('/admin/coupons', data)
export const couponUpdateApi = (data) => request.put('/admin/coupons', data)
export const couponDeleteApi = (id) => request.delete(`/admin/coupons/${id}`)

export const orderPageApi = (params) => request.get('/admin/orders', { params })
export const orderStatusApi = (id, status, cancelReason) =>
  request.put(`/admin/orders/${id}/status/${status}`, null, { params: { cancelReason } })
