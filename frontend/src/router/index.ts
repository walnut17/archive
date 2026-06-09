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
        // M2 知识库问答
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/Knowledge.vue'),
        },
        // G-1 LLM 用量统计(任何角色可用,admin 可看全员)
        {
          path: 'llm-usage',
          name: 'llm-usage',
          component: () => import('@/views/LlmUsage.vue'),
        },
        // E-2 Admin pages (requires admin role)
        {
          path: 'admin/dict',
          name: 'admin-dict',
          component: () => import('@/views/AdminDict.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'admin/extraction',
          name: 'admin-extraction',
          component: () => import('@/views/AdminExtraction.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'admin/comparison',
          name: 'admin-comparison',
          component: () => import('@/views/AdminComparison.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'admin/triggers',
          name: 'admin-triggers',
          component: () => import('@/views/AdminTrigger.vue'),
          meta: { requiresAdmin: true },
        },
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
  // Admin role check
  if (to.meta.requiresAdmin && auth.user?.role !== 'admin') {
    next({ name: 'dashboard' })
    return
  }
  next()
})

export default router
