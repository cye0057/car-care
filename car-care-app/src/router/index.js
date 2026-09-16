import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/MainLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', component: () => import('../views/Home.vue'), meta: { tab: 'home', title: '车管家' } },
      { path: 'feed', component: () => import('../views/Feed.vue'), meta: { tab: 'feed', title: '养车笔记' } },
      { path: 'orders', component: () => import('../views/Orders.vue'), meta: { tab: 'orders', title: '我的订单' } },
      { path: 'mine', component: () => import('../views/Mine.vue'), meta: { tab: 'mine', title: '我的' } }
    ]
  },
  { path: '/store/:id', component: () => import('../views/Store.vue') },
  { path: '/seckill', component: () => import('../views/Seckill.vue') },
  { path: '/order-create', component: () => import('../views/OrderCreate.vue') },
  { path: '/publish', component: () => import('../views/Publish.vue') }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (!localStorage.getItem('app_token') && to.path !== '/login') return '/login'
  if (localStorage.getItem('app_token') && to.path === '/login') return '/home'
  document.title = to.meta?.title || '车管家'
  return true
})

export default router
