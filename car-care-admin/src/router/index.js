import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'stores', component: () => import('../views/StoreList.vue'), meta: { title: '门店管理' } },
      { path: 'categories', component: () => import('../views/CategoryList.vue'), meta: { title: '服务分类' } },
      { path: 'items', component: () => import('../views/ItemList.vue'), meta: { title: '保养项目' } },
      { path: 'coupons', component: () => import('../views/CouponList.vue'), meta: { title: '优惠券管理' } },
      { path: 'orders', component: () => import('../views/OrderList.vue'), meta: { title: '订单管理' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.path !== '/login' && !user.token) {
    return '/login'
  }
  if (to.meta?.title) {
    document.title = to.meta.title + ' - 车管家管理台'
  }
  return true
})

export default router
