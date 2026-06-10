# 投委会档案管理系统 — 系统总体架构

> 撰写人：Sisyphus | 日期：2026-06-10 | 版本：v1.0

## 1. 系统定位

投委会档案管理系统是一套面向投资委员会（投委会）的**轻量级档案管理与智能分析 Web 应用**。核心使命是解决投委会在日常运作中遇到的"文档分散、检索困难、分析耗时"问题。

### 核心能力

| 能力 | 说明 |
|------|------|
| 档案存储 | 全生命周期档案管理（立项→申请→贷后→结清），文件上传 + Tika 解析 |
| 全文检索 | MySQL 8.0 FULLTEXT + ngram 中文分词，不依赖向量数据库 |
| LLM 增强 | 基于智谱 GLM-4-Flash 的字段抽取、时点提取、语义对比、智能问答 |
| 规则引擎 | Aviator 表达式驱动的触发规则（材料上传/分类变更/状态变更/时点临近） |
| 智能 Agent | Spring AI 1.1 + 手写 5 步 ReAct 循环的智能问答 Agent（Plan I） |

### 设计哲学

- **单机优先**：面向 Windows Server 单机部署，3 进程即可运行（Caddy + Spring Boot + MySQL）
- **轻量无向量**：全部检索基于 MySQL FULLTEXT + ngram，不引入 Elasticsearch/向量数据库
- **渐进增强**：LLM 作为增强层而非核心依赖，LLM 挂掉后仍有基础 CRUD + FULLTEXT 检索可用
- **扁平架构**：无微服务，单体 Spring Boot JAR + Spring Data JPA，减少运维复杂度

---

## 2. 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 LTS | 运行时 |
| Spring Boot | 3.3.5 | Web 框架 |
| Spring Data JPA | 3.3.5 | ORM + Repository |
| Spring Security | 3.3.5 | 认证授权 |
| Spring AI | 1.1.0 | LLM 集成框架 |
| MySQL | 8.0.16 | 数据库 |
| Apache Tika | 2.9.2 | 文档解析 |
| HanLP | portable-1.8.4 | NER 命名实体识别 |
| Aviator | 5.4.2 | 规则表达式引擎 |
| JJWT | 0.12.6 | JWT 生成/验证 |
| Hutool | 5.8.27 | 工具类 |
| Lombok | latest | 代码生成 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.12 | 前端框架 |
| Vite | 5.4.8 | 构建工具 |
| TypeScript | 5.6.2 | 类型系统 |
| Element Plus | 2.8.4 | UI 组件库 |
| Pinia | 2.2.4 | 状态管理 |
| Vue Router | 4.4.5 | 路由 |
| Axios | 1.7.7 | HTTP 客户端 |

### 部署基础设施

| 组件 | 版本 | 用途 |
|------|------|------|
| Windows Server | 2012 R2+ | 操作系统 |
| Caddy | 1.x | 反向代理 + TLS |
| WinSW | latest | Windows 服务化管理 |
| MySQL | 8.0.16 | 数据库引擎 |

---

## 3. 进程拓扑

```
┌─────────────────────────────────────────────────────────┐
│                    浏览器 (Chrome/Edge)                    │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTPS :443 / HTTP :80
                       ▼
┌─────────────────────────────────────────────────────────┐
│  Caddy (反向代理 + TLS + 限速 600r/m + 前端静态资源)     │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP :8080 (内网)
                       ▼
┌─────────────────────────────────────────────────────────┐
│          Spring Boot 3.3 (WinSW 管理)                     │
│  JVM: -Xms512m -Xmx1536m -Dfile.encoding=UTF-8           │
└──────┬──────────────┬──────────────┬────────────────────┘
       │              │              │
       ▼              ▼              ▼
   MySQL :3306   智谱 GLM API   本地文件系统
   (内网)        (出站 :443)   D:\archive\files\
                                D:\archive\parsed\
```

**服务管理**：后端 + Caddy 均注册为 Windows 服务（WinSW），开机自启，后端故障 30 秒自动重启。

---

## 4. 后端分层架构

```
┌─────────────────────────────────────────────────────┐
│  Controller 层 (13 个 REST 控制器)                    │
│  /api/auth/*  /api/projects/*  /api/qa/*  ...         │
├─────────────────────────────────────────────────────┤
│  Service 层 (17 个业务服务)                            │
│  Auth/Project/Proposal/Material/Glm/KnowledgeSearch   │
├─────────────────────────────────────────────────────┤
│  Agent 层 (Plan I 智能问答)                            │
│  AgentEngine → 6 AgentTool + MessageChatMemory         │
│  (5步ReAct循环, LLM + 工具交错执行)                    │
├─────────────────────────────────────────────────────┤
│  Engine 层 (4 个业务引擎)                              │
│  Extraction/Comparison/TimepointExtractor/Trigger      │
│  (@Async异步执行, 事件驱动)                             │
├─────────────────────────────────────────────────────┤
│  Provider 层 (LLM 抽象)                                │
│  LLMProviderFactory → GLMProvider(默认)                  │
├─────────────────────────────────────────────────────┤
│  Repository 层 (16 个 JPA 仓库)                        │
│  JpaRepository + 全文检索NativeQuery                     │
├─────────────────────────────────────────────────────┤
│  Entity 层 (16 个 JPA 实体)                            │
│  Project/Proposal/Material/MaterialVersion/...          │
├─────────────────────────────────────────────────────┤
│  Security 层                                           │
│  JwtAuthFilter → JwtUtil + SecurityConfig + RBAC        │
└─────────────────────────────────────────────────────┘
```

**关键分层决策**：
- Service 层与 Engine 层分离：Service 负责事务编排，Engine 负责异步/事件驱动的复杂业务逻辑
- Provider 层解耦 LLM：通过 LLMProviderFactory 支持多供应商切换，当前仅有 GLMProvider 实现
- Agent 层独立于 Engine 层：Agent 属于交互式 AI 推理，Engine 属于后台自动处理，运行模式不同

---

## 5. 前端架构

```
src/
├── main.ts                    # 应用入口 (Vue + Pinia + Router + Element Plus)
├── App.vue                    # 根组件 (<RouterView />)
├── api/                       # API 层
│   ├── http.ts                # Axios 实例 + 拦截器 (120s超时, Bearer认证)
│   ├── auth.ts                # 认证 API (login, me)
│   └── archive.ts             # 业务 API (~50个端点)
├── store/auth.ts              # Pinia 状态 (token, user, isAdmin)
├── router/index.ts            # 路由表 (11个路由, 嵌套Layout)
├── views/                     # 13个页面组件
│   ├── Login.vue              # 登录页
│   ├── Layout.vue             # 主框架 (侧边栏+顶栏)
│   ├── Dashboard.vue          # 工作台
│   ├── ProjectList.vue        # 项目列表
│   ├── ProjectDetail.vue      # 项目详情
│   ├── ProjectForm.vue        # 项目表单
│   ├── ProposalDetail.vue     # 议案详情
│   ├── Knowledge.vue          # 知识库问答
│   ├── LlmUsage.vue           # AI用量统计
│   ├── AdminDict.vue          # 字典管理 (admin)
│   ├── AdminExtraction.vue    # 抽取方法 (admin)
│   ├── AdminComparison.vue    # 对比方法 (admin)
│   └── AdminTrigger.vue       # 触发规则 (admin)
└── components/
    └── AgentStepsPanel.vue    # Agent推理步骤展示
```

**状态管理**：仅 auth 使用 Pinia（token + user 信息），页面级状态使用 `ref` 局部管理，无需全局 store 框架。

---

## 6. 数据库概览（16 张业务表 + 2 张基础设施表）

| 组 | 表 | 记录数 |
|----|-----|--------|
| 核心业务 | role, user, project, proposal, material, material_version | 6 |
| 智能分析 | chapter_summary, timepoint, todo | 3 |
| 规则引擎 | trigger_rule, trigger_action | 2 |
| 可配置 | extraction_method, comparison_method, dict_type, dict_item | 4 |
| 审计 | audit_log | 1 |
| LLM 调用 | llm_call_log | 1 |
| Agent 记忆 | spring_ai_chat_memory | 1 |

**索引策略**：全部使用 MySQL FULLTEXT + ngram parser（1-2 字最小 token），无外部搜索引擎。

---

## 7. 架构特性评估

### 优势

| 特性 | 说明 |
|------|------|
| 部署极简 | 单机 3 进程，WinSW 管理，开箱即用 |
| 降级路径完善 | LLM/Agent 挂掉后基础检索可用 |
| 解耦良好 | Controller↔Service↔Repository 标准分层，Provider 层隔离 LLM 供应商变更 |
| 审计完备 | llm_call_log 记录每次 LLM 调用，audit_log 记录关键操作 |
| 安全到位 | JWT 8h 过期、BCrypt 密码、LoginRateLimiter 限流、白名单 SQL |

### 注意事项

| 事项 | 说明 |
|------|------|
| 无业务层缓存 | 仅 DictService 有 5s 本地缓存，大量读取会打到 DB |
| Agent 内存问题 | MessageWindowChatMemory 20 条上限，长对话会失忆 |
| Agent Engine 流式未实现 | 当前为阻塞调用，前端 Axios 120s 超时 |
| 进程级降级 | Agent 挂才降级，更细粒度的工具级降级需要额外开发 |
| 无灾备 | 单机部署，无数据库主从/集群 |
