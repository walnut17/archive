# 投委会档案管理系统 — 前端架构

> 撰写人：Sisyphus | 日期：2026-06-10 | 版本：v1.0 + v1.1 (2026-06-11)

## 1. 技术选型

| 技术 | 版本 | 选择理由 |
|------|------|---------|
| Vue 3 | 3.5.12 | Composition API + 响应式系统 |
| Vite 5 | 5.4.8 | 极速开发服务器 + ESM 构建 |
| TypeScript | 5.6.2 | 严格模式 (strict: true) |
| Element Plus | 2.8.4 | 成熟的企业级 UI 组件库 |
| Pinia | 2.2.4 | 官方推荐状态管理 |
| Vue Router 4 | 4.4.5 | SPA 路由 + 导航守卫 |
| Axios | 1.7.7 | HTTP 客户端 (120s 超时) |

---

## 2. 项目结构

```
frontend/
├── index.html                     # HTML 入口
├── package.json                   # 依赖管理
├── vite.config.ts                  # Vite 配置
├── tsconfig.json                   # TypeScript 配置
└── src/
    ├── main.ts                     # 应用启动入口
    ├── App.vue                     # 根组件 (RouterView)
    ├── assets/main.css             # 全局样式
    ├── api/
    │   ├── http.ts                 # Axios 实例 + 拦截器
    │   ├── auth.ts                 # 认证 API
    │   └── archive.ts              # 业务 API (~50 端点)
    ├── store/auth.ts               # Pinia auth store
    ├── router/index.ts             # 路由定义 + 导航守卫
    ├── views/                      # 18 个页面组件 (v1.1 +5)
    │   ├── Login.vue
    │   ├── Layout.vue
    │   ├── Dashboard.vue
    │   ├── ProjectList.vue / ProjectDetail.vue / ProjectForm.vue
    │   ├── ProposalDetail.vue
    │   ├── Knowledge.vue
    │   ├── LlmUsage.vue
    │   └── AdminDict / AdminExtraction / AdminComparison / AdminTrigger
    └── components/
        ├── AgentStepsPanel.vue     # Agent 推理步骤展示
        ├── ConfidenceBadge.vue     # v1.1 置信度徽章 (RI-46)
        ├── FactDiffViewer.vue      # v1.1 事实变更对比 (RI-66)
        ├── MaterialPreview.vue     # v1.1 附件预览 (RI-65)
        └── NotificationBell.vue    # v1.1 通知铃铛 (RI-63)
```

### 文件统计

| 类型 | 数量 |
|------|------|
| .vue (页面) | 13 |
| .vue (组件) | 1 |
| .ts (API) | 3 |
| .ts (store) | 1 |
| .ts (router) | 1 |
| .ts (入口) | 1 |
| .css | 1 |

---

## 3. 组件树

```
App.vue
└── <RouterView />
    │
    ├── Login.vue
    │   ├── 登录卡片
    │   ├── 用户名输入
    │   └── 密码输入
    │
    └── Layout.vue
        ├── el-header (顶栏: 标题 + 退出)
        ├── el-aside (侧边栏导航)
        │   ├── 工作台 (/)
        │   ├── 项目管理 (/projects)
        │   ├── 知识库问答 (/knowledge)
        │   ├── AI 用量 (/llm-usage)
        │   ├── 时点日程 (占位)
        │   ├── 规则引擎 (占位)
        │   └── 参数管理 (/admin/*, admin only)
        │       ├── 字典管理
        │       ├── 抽取方法
        │       ├── 对比方法
        │       └── 触发规则
        │
        └── el-main (内容区 → <RouterView />)
            ├── Dashboard.vue
            │   ├── 健康检查状态 card
            │   └── 路线图时间线
            │
            ├── ProjectList.vue
            │   ├── 搜索表单 (关键字 + 状态)
            │   ├── 项目表格 + 分页
            │   └── 新建/编辑/删除对话框
            │
            ├── ProjectDetail.vue
            │   ├── 项目信息 card
            │   └── 议案表格 + CRUD dialog
            │
            ├── ProjectForm.vue
            │   └── 项目编辑表单
            │
            ├── ProposalDetail.vue
            │   ├── 议案信息 card
            │   ├── 材料表格 + 版本管理 dialog
            │   └── 章节列表 dialog
            │
            ├── Knowledge.vue
            │   ├── 问题输入框 + 控制开关
            │   ├── 答案卡片 (含 AgentStepsPanel)
            │   └── 参考来源折叠面板
            │
            ├── LlmUsage.vue
            │   ├── 统计数字 cards
            │   └── 分类/用户排行表格
            │
            ├── AdminDict.vue (双面板: 类型列表 + 条目列表)
            ├── AdminExtraction.vue (表格 + dialog)
            ├── AdminComparison.vue (表格 + dialog)
            └── AdminTrigger.vue (表格 + dialog)
```

---

## 4. 路由定义

```typescript
const routes = [
  { path: '/login', component: Login, meta: { public: true } },
  {
    path: '/',
    component: Layout,
    children: [
      { path: '',                   component: Dashboard },
      { path: 'projects',           component: ProjectList },
      { path: 'projects/new',       component: ProjectForm },
      { path: 'projects/:id',       component: ProjectDetail },
      { path: 'projects/:id/edit',  component: ProjectForm },
      { path: 'proposals/:id',      component: ProposalDetail },
      { path: 'knowledge',          component: Knowledge },
      { path: 'llm-usage',          component: LlmUsage },
      { path: 'admin/dict',         component: AdminDict,    meta: { requiresAdmin: true } },
      { path: 'admin/extraction',   component: AdminExtraction, meta: { requiresAdmin: true } },
      { path: 'admin/comparison',   component: AdminComparison, meta: { requiresAdmin: true } },
      { path: 'admin/triggers',     component: AdminTrigger,    meta: { requiresAdmin: true } },
    ]
  }
]
```

**导航守卫逻辑**：
1. 无 token + 非公开路由 → 重定向 `/login`
2. `requiresAdmin` + 非 admin 角色 → 重定向 `/`

---

## 5. API 层架构

### 分层

```
views/*.vue (组件调用)
    │
    ▼
api/archive.ts / api/auth.ts (API 函数)
    │
    ▼
api/http.ts (Axios 实例 + 拦截器)
    │
    ▼
Spring Boot /api/*
```

### HTTP 客户端配置

```typescript
const http = axios.create({
  baseURL: '/api',
  timeout: 120000,       // Agent 模式 5 步循环最坏情况
})

// 请求拦截器: 自动注入 Bearer token
http.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器: 业务错误判断 + 401 自动跳转
http.interceptors.response.use(response => {
  if (response.data.code !== 0) {
    // 业务错误 (如 token 过期)
  }
})
```

### 辅助函数

```typescript
// 从 axios 响应中提取业务数据
function getData<T>(response: ApiResponse<T>): T
// 泛型请求包装
function request<T>(config): Promise<T>
```

---

## 6. API 端点统计

| 模块 | 端点数 | 说明 |
|------|--------|------|
| Auth | 2 | login, me |
| Health | 1 | health |
| Projects | 5 | CRUD + list |
| Proposals | 5 | CRUD + list + regenerateSummary |
| Materials | 9 | CRUD + list + batchUpload |
| Versions | 6 | CRUD + switchCurrent + reparse + sections |
| Dict | 5 | types CRUD + items CRUD |
| Extraction | 4 | CRUD |
| Comparison | 4 | CRUD |
| Triggers | 4 | CRUD |
| LLM Stats | 2 | my-usage, stats (admin) |
| Q&A | 2 | ask, turn/{sessionId} (多轮) |
| **合计** | **~49** | |

---

## 7. 状态管理

当前只使用一个 Pinia store：

```typescript
useAuthStore {
  state: {
    token: string         // localStorage 持久化
    user: {               // null | { id, username, role }
      id: number | null
      username: string | null
      role: string | null
    }
  },
  getters: {
    isLoggedIn: boolean   // !!token
    isAdmin: boolean      // role === 'admin'
  },
  actions: {
    login(credentials)    // 调用 authApi.login → 存 token → fetchMe
    logout()              // 清除 token + user → 跳转 /login
    fetchMe()             // 调用 authApi.me → 更新 user
  }
}
```

**设计决策**: 页面级状态使用 `ref()` 局部管理。没有引入全局 store 框架（如 Vuex/pinia-plugin），因为当前页面间共享状态极少。

---

## 8. 构建配置

### Vite

```typescript
// vite.config.ts
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
  resolve: { alias: { '@': 'src/' } },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'vue-vendor': ['vue', 'vue-router', 'pinia', 'axios'],
        }
      }
    }
  }
})
```

### TypeScript

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "strict": true,
    "moduleResolution": "bundler",
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] }
  }
}
```

---

## 9. 与后端 Agent 模式的交互

```
Knowledge.vue
  │
  ├── agentMode = on (默认)
  │   └── POST /api/qa/ask { question, topN, rerank }
  │       └── 后端 AgentEngine.run() → 5 步 ReAct
  │           └── 响应: { answer, steps[], agentMode: true, ... }
  │               ├── answer → 答案卡片
  │               └── steps → AgentStepsPanel (折叠展示)
  │
  └── agentMode = off
      └── POST /api/qa/ask { question, topN, rerank }
          └── 后端老路径 GlmService
              └── 响应: { answer, sources[], agentMode: null, ... }
```

**注意事项**：
- Axios 120s 超时是为 Agent 模式 5 步循环预留的
- Agent 模式响应会多出 `steps`、`agentMode`、`toolCalls` 字段
- 老前端不会崩——新字段是可选的

---

## v1.1 前端增量 (MOD-05, 2026-06-11)

| 类型 | 文件 | 说明 |
|---|---|---|
| 新 View | ProjectBoard.vue, AdminImport.vue, RecycleBin.vue | 看板 / 导入 / 回收站 |
| 改 View | Knowledge.vue, Home.vue, ProjectForm.vue, ProjectDetail.vue, Layout.vue | 徽章 / 双模动画 / 失败 banner / 导出 / 通知铃铛 |
| 新 Component | 5 个 (见 §2 结构) | 从 1→5 |
| 新依赖 | pdfjs-dist, mammoth | 纯前端预览 (D-5) |
