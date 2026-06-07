import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/views/Layout.vue'),
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/Dashboard.vue'),
        },
        // M1 档案 CRUD
        {
          path: 'projects',
          name: 'project-list',
          component: () => import('@/views/ProjectList.vue'),
        },
        {
          path: 'projects/new',
          name: 'project-form',
          component: () => import('@/views/ProjectForm.vue'),
        },
        {
          path: 'projects/:id',
          name: 'project-detail',
          component: () => import('@/views/ProjectDetail.vue'),
        },
        {
          path: 'projects/:id/edit',
          name: 'project-edit',
          component: () => import('@/views/ProjectForm.vue'),
        },
        {
          path: 'proposals/:id',
          name: 'proposal-detail',
          component: () => import('@/views/ProposalDetail.vue'),
        },
        // 后续模块加在这里
      ],
    },
  ],
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!auth.token) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router
