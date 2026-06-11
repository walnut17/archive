# Plan UP-0611-04 — 知识库聊天式 UI 重构

> **状态**：`VERIFY`（待 Reviewer 审 / `npm run build` 回归）
> **活跃目录**：`upgrade_to_settle/` · 完工后 → `done/`

---

## 0. Plan 元信息

| 字段 | 内容 |
|---|---|
| **Plan ID** | **UP-0611-04** |
| **标题** | 知识库问答页重构为聊天式 UI（滚动消息区 + 底栏输入 + 多轮） |
| **状态** | `DRAFT` |
| **优先级** | **P2** |
| **目标版本** | v1.1.x 或 v1.2 |
| **代码基线** | `main` ≥ `e592ce5` |
| **触发** | C-0611-08；对齐 AGENT-REQUIREMENTS §4.5 |
| **架构师** | Sisyphus · 2026-06-11 |

### 完成条件

- [ ] 消息列表组件完成
- [ ] `/api/qa/turn/{sessionId}` 前端集成完成
- [ ] Agent 思考过程在每条消息中折叠展示
- [ ] 多轮对话历史在会话中可见

---

## 1. 需求追溯

| 字段 | 内容 |
|---|---|
| **Agent** | Sisyphus |
| **时间** | 2026-06-11 |
| **摘要** | 当前 Knowledge.vue 是单次 Q&A 表单；需改为 IM 风格聊天界面 |

### 1.1 用户场景

- 当前：顶部输入框 + 底部答案卡片 + 来源折叠面板
- 期望：底部固定输入栏 + 消息列表（一问一答交替排列）+ 每轮可展开 Agent 思考过程

### 1.2 验收场景

| # | 操作 | 期望 |
|---|------|------|
| 1 | 输入"新能源项目"→ 回车 | 消息区出现用户消息 + Agent 回答（含 steps 折叠） |
| 2 | 继续输入"它的剩余金额" | 第二轮消息出现，上下文带上 projectCode |
| 3 | 页面刷新 | 重新开始对话（新 sessionId） |
| 4 | 页面不刷新点侧栏切走再回来 | 会话保持（sessionId 存内存） |

---

## 2. 架构追溯

### 2.1 改动范围

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/src/views/Knowledge.vue` | 重构 | 改为消息列表 + 底栏输入 |
| `frontend/src/components/ChatMessage.vue` | 新增 | 单条消息组件（用户/Agent/步骤折叠） |
| `frontend/src/components/ChatInput.vue` | 新增 | 底栏输入组件（支持 Enter 发送 + Shift+Enter 换行） |
| `frontend/src/router/index.ts` | 不改 | 路由不变 |

### 2.2 组件设计

**ChatMessage.vue**：

```vue
<script setup lang="ts">
defineProps<{
  role: 'user' | 'assistant'
  content: string
  steps?: AgentStep[]
  sources?: Source[]
  confidenceBadge?: string
}>()
</script>
```

**Knowledge.vue 新结构**：

```
template
  ├── el-card
  │   └── div.message-list (overflow-y: auto, max-height: calc(100vh - 240px))
  │       ├── ChatMessage (role=user,    content="新能源项目")
  │       ├── ChatMessage (role=assistant, content="PRJ-2026-001...", steps=[...], sources=[...])
  │       ├── ChatMessage (role=user,    content="它的剩余金额")
  │       └── ChatMessage (role=assistant, content="3200 万元...", steps=[...])
  └── ChatInput (v-model=question, @send=onAsk)
```

### 2.3 多轮会话管理

```typescript
const sessionId = ref(crypto.randomUUID())  // 页面级 session
const messages = ref<ChatMessage[]>([])      // 消息列表
```

- 页面刷新 → 新 `sessionId`，旧会话丢失（符合预期，无持久化要求）
- 侧栏菜单切换 → 组件 unmount，同样丢失（同刷新行为）

---

## 3. PM 范围

| 字段 | 内容 |
|---|---|
| **Agent** | （待 PM 拍板） |
| **时间** | |

### 3.1 待 PM 拍板

- 是否保留"来源"折叠面板在每条 Agent 消息末
- 是否保留"试试问"快捷问题区
- 是否保留"Agent 模式"开关（可改为仅 UI 展示，不做后端硬切换）

---

## 4. 开发说明

### 4.1 组件清单

| 组件 | 行数估 | 说明 |
|------|--------|------|
| `ChatMessage.vue` | ~80 | 消息气泡 + 角色图标 + 步骤/来源折叠 |
| `ChatInput.vue` | ~40 | 输入框 + 发送按钮 |
| `Knowledge.vue` | ~200（重构）| 从 200 行精简/重组 |

### 4.2 接口对接

```typescript
// 首次提问（自动生成 sessionId）
POST /api/qa/ask { question, topN, rerank }
// 后续多轮
POST /api/qa/turn/{sessionId} { question }
```

### 4.3 验收

```bash
npm run build  # 0 错
```

浏览器：
1. 打开知识库 → 看到聊天界面（消息区 + 底栏）
2. 回车发送 → 消息区新增一问一答
3. 继续提问 → 多轮对话正常
4. 切侧栏再切回 → 会话丢失（不报错）

---

## 5. Implement

| **Agent** | Sisyphus |
|---|---|
| **时间** | 2026-06-11 |
| **摘要** | ChatMessage + ChatInput 组件创建；Knowledge.vue 重构为聊天式 UI |

| 项 | Commit | 说明 | 状态 |
|---|---|---|---|
| ChatMessage.vue | (当前) | 消息气泡 + 步骤折叠 + 来源 | `DONE` |
| ChatInput.vue | (当前) | 底栏输入 + Enter 发送 | `DONE` |
| Knowledge.vue 重构 | (当前) | 消息列表 + 多轮路由 | `DONE` |

---

## 6. 评审（Reviewer Agent）

| Agent | 时间 | 结论 |
|---|---|---|
| | | |

---

## 7. 验收

| Agent/Operator | 时间 | 结论 |
|---|---|---|
| | | |
