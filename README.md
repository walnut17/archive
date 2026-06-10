# 投委会档案管理系统

> 投委会专属档案管理与智能分析 Web 应用 (Spring Boot 3.3 + Vue 3)
>
> **项目一句话**: 让投委会秘书/委员 5 分钟内回答"这个项目抵押物处理到哪一步了 / 江苏地区空债权平均回收率多少"
>
> **当前阶段**: **v1.1 业务需求评审** (2026-06-10) — Plan I v1.0 (智能问答 Agent) 已完工, 现在按 `docs/requirements/ARCH-DECOMPOSITION.md` 的 **RI-1~45** 推进 v1.1 增量
> **基线 commit**: `0c6325f` (v1.0 + v1.1 §5.6 评审修)
> **生产服务器**: 182.168.1.125 (单机, Windows + Caddy + Spring Boot)
>
> 🚨 **接手方必读**: 先看下面 **§1 角色导航**, 找到自己是哪类人, 跟着对应章节开工, 5 分钟即可认识项目 + 找到入口.

---

## 📑 目录

- [§0. 项目是什么](#-0-项目是什么)
- [§1. 角色导航 (核心)](#-1-角色导航-核心)
- [§2. 需求文档位置](#-2-需求文档位置)
- [§3. 项目背景与版本路线](#-3-项目背景与版本路线)
- [§4. 必读文档 (按角色分类)](#-4-必读文档-按角色分类)
- [§5. 程序员开工: 抢任务 SOP](#-5-程序员开工-抢任务-sop)
- [§6. 卡住怎么办 / 找谁](#-6-卡住怎么办--找谁)
- [§7. 仓库结构 + 文档演进](#-7-仓库结构--文档演进)

---

## 🎯 0. 项目是什么

### 0.1 解决什么问题

投委会 (投资决策委员会) 每个项目从立项到结清会产生几百份材料 (合同 / 律师函 / 回款凭证 / 抵押物证明 / 议案 / 备忘录 ...) 和几十次投委会会议决议. **现状**: 业务方查"这个项目抵押物 A 处置到哪了"要翻 Excel + 翻历史 PDF, 几天才能拼出来. **目标**: 输入"这个项目抵押物 A 处置到哪了", **5 秒返回** + 证据链 + 时间线.

### 0.2 5 个核心能力

| 能力 | 简述 | 文档 |
|---|---|---|
| 📂 项目档案 | 一个项目从立项到结清的全周期材料, 软删/回收站/版本控制 | §5.11 |
| 🤖 智能问答 (Agent) | 多轮对话, LLM 工具调用 (find_project / search_fulltext / query_mysql), 支持"问这个项目"+"问全公司"两种模式 | §5.6 + Plan I |
| 📊 关键事实抽取 | LLM 自动从材料里抽"抵押物 / 担保人 / 处置和解"等 32 项业务维度, 存到 `project_fact` + 不可变事件流 `project_fact_event` | §5.8 |
| 🔍 跨项目聚合 | 业务方问"江苏地区空债权回收率多少", LLM 调 SQL 聚合 (5 个批量工具) | §5.9 |
| 📚 业务术语字典 | 投委会业务专有术语 (空债权 / 回收率 / 复议 ...), 维护 + 网络查候选 | §5.10 |

### 0.3 仓库规模 (2026-06-10)

- **后端**: Spring Boot 3.3 + JPA + Spring AI 1.1 (Agent), ~50 文件, 200+ 测试
- **前端**: Vue 3 + Vite + Element Plus, ~30 文件
- **数据库**: MySQL 8 + FULLTEXT 索引
- **LLM**: 智谱 GLM-4 (主) + OpenAI 兼容接口 (备)
- **部署**: 单机 (182.168.1.125) + Caddy + WinSW

---

## 👥 1. 角色导航 (核心)

> **本文档核心目的**: 4 类角色, 各 5 行说清楚你是谁 / 读什么 / 写什么 / 怎么开工 / 不能干什么.

| 角色 | 你是谁 | 你要读 | 你要写 | 你怎么开工 | 边界 |
|---|---|---|---|---|---|
| **[需求开发人员](#11-需求开发人员)** (产品/业务分析师) | 把业务方嘴里"我们希望 XX"翻译成结构化需求 §X.Y | `docs/requirements/REQUIREMENTS.md` (现有) + 业务方访谈 | 在 `docs/requirements/REQUIREMENTS.md` 加 §X.Y 新章节, 含 5 字段 (业务/数据/角色/验收/依赖) | [§1.1](#11-需求开发人员) | ❌ 不写代码, 不写 RI 拆解 |
| **[需求审核人员](#12-需求审核人员)** (PM/业务方代表) | 审新需求 / 拍板模糊点 / 维护术语 | `docs/requirements/REQUIREMENTS.md` + 业务背景资料 | 在 PR 评审里批 +/- 反馈, 维护 `docs/requirements/REQUIREMENTS.md` §13 决策记录 | [§1.2](#12-需求审核人员) | ❌ 不写代码, 不审 RI 拆解 |
| **[架构师](#13-架构师)** (技术 Lead) | 把需求 §X.Y 拆成可落地的 RI (Requirement Item) | `docs/requirements/REQUIREMENTS.md` + 现有 `docs/ARCHITECTURE-v2.md` + `docs/DB-SCHEMA-v2.md` | 在 `docs/requirements/ARCH-DECOMPOSITION.md` 加 RI-N: 业务/影响表/角色/验收/依赖/估算 | [§1.3](#13-架构师) | ❌ 不直接写代码 (除非示范) |
| **[程序员](#14-程序员)** (接手 AI / 后端 / 前端 / DBA) | 按 RI 抢任务 + 写代码 + 跑测试 + 提 PR | 必读 [§4.1](#41-程序员必读基线包) + 抢到的 RI 节 | 代码 + 单元测试 + 1 commit / 任务, push 到 main | [§5 抢任务 SOP](#-5-程序员开工-抢任务-sop) | ❌ 不擅自改需求, 不擅自拆别人的 RI |

### 1.1 需求开发人员

**你是谁**: 投委会业务方 (frisker) 嘴里"我想要 XX", 你翻译成结构化需求. **不写代码, 不写 RI 拆解** (那是架构师的事).

**你要读** (按顺序):
1. `docs/requirements/REQUIREMENTS.md` (1342 行, 业务全貌) — **5 分钟扫目录, 1 小时精读**
2. 业务方 4 轮访谈纪要 (在 `docs/AGENT-REQUIREMENTS.md` + git history) — 看"原话"vs"结构化"的差距
3. `docs/LESSONS-LEARNED.md` (15+ 踩坑) — 避免重复别人错过的需求

**你要写** (4 件事):
- **新需求 §X.Y 章节** (在 `docs/requirements/REQUIREMENTS.md` 末尾 `## 14. <标题>` 之后, 1 节 1 个业务功能) — 含 5 字段:
  - **业务**: 一句话业务目标
  - **数据**: 涉及哪些表 / 新增哪些字段
  - **角色**: 谁有权使用
  - **验收**: 3-5 条 Given/When/Then
  - **依赖**: 跟哪些已有 §X.Y 关联
- **业务术语定义** (在 §5.10) — 业务方嘴里的"空债权" / "复议" / "维护" 等
- **更新 §3 业务流程图** (如新增环节)
- **更新 §9 性能 / §10 验收 / §12 版本规划**

**你怎么开工** (5 步):
1. **读** `docs/requirements/REQUIREMENTS.md` §0~§3 (认识系统)
2. **采访业务方** (投委会秘书/委员), 记录"原话"
3. **翻** 已有章节 (避免重复)
4. **写** 新 §X.Y 章节 (1 节 1 PR)
5. **提 PR** 给需求审核人员 + 架构师 (架构师会基于你的 §X.Y 写 RI)

**你不能**:
- ❌ 写代码 (交架构师拆 RI 后给程序员)
- ❌ 自己拍板"阈值 0.6" (这要架构师拆 RI 时定)
- ❌ 改 `ARCH-DECOMPOSITION.md` (那是架构师维护)
- ❌ 跨章节改老需求 (那要单独立 §14.1 "修订记录")

### 1.2 需求审核人员

**你是谁**: PM / 业务方代表, 拍板"这个需求做不做 / 怎么算做完" / 维护业务术语 / 审 RI 拆解. **不写代码**.

**你要读** (按顺序):
1. `docs/requirements/REQUIREMENTS.md` (全) — 跟需求开发人员**同一份**, 你是审
2. `docs/requirements/ARCH-DECOMPOSITION.md` (RI-1~45) — 审架构师拆的 RI 是否落地
3. 业务背景资料 (`docs/SIMILAR-PRODUCTS.md` + 内部 wiki)

**你要写** (3 件事):
- **PR 评审 +/- 反馈** — 业务逻辑通不通 / 验收标准清不清楚 / 术语准不准确
- **§13 决策记录** — 在 `REQUIREMENTS.md` §13 末尾追加"PM 拍板: XXX", 锁住已决定的事项
- **业务术语字典维护** — `docs/requirements/REQUIREMENTS.md` §5.10 引用业务术语, 你审术语定义

**你怎么开工** (每天做的事):
1. **早上** — 看 GitHub PR (需求 + RI), 标 +/- 反馈
2. **按需** — 业务方有"新需求碎片"找你, 你先做 30 分钟访谈, 转交需求开发人员
3. **每周** — 维护一次 `REQUIREMENTS.md` §13 决策记录 (锁住当周拍板)
4. **每月** — 审 `ARCH-DECOMPOSITION.md` RI 进度 (P0/P1/P2 优先级是否调整)

**你不能**:
- ❌ 写代码 (你的活儿是**说清楚**, 不是实现)
- ❌ 改 RI 拆解 (那是架构师 + 程序员)
- ❌ 不审就放行 (PR 流转: 需求审 → 架构审 → 实现)

### 1.3 架构师

**你是谁**: 技术 Lead, 把需求 §X.Y **拆成可落地的 RI (Requirement Item)**. 不直接写代码 (除非示范).

**你要读** (按顺序):
1. `docs/requirements/REQUIREMENTS.md` (全) — 跟需求开发/审核**同一份**, 你是拆
2. `docs/ARCHITECTURE-v2.md` (685 行) — 现有架构基线
3. `docs/DB-SCHEMA-v2.md` (1060 行) — 现有 schema (你要加表/字段看这里)
4. `docs/architecture/01~06-arch-*.md` (另一个 AI 写的分章架构) — 现代化拆解参考
5. `docs/AGENT-FRAMEWORK-DECISION.md` (885 行) — 关键决策 (Spring AI 1.1 + 不引 spring-ai-alibaba)

**你要写** (在 `docs/requirements/ARCH-DECOMPOSITION.md` 加 RI):
- **RI-N: <标题> (§X.Y 引用)** — 每节 6 字段:
  - **业务**: 引用需求 §X.Y
  - **影响表**: 涉及 / 新增 / 修改哪些表
  - **角色**: 谁能调
  - **验收**: 3-5 条 Given/When/Then
  - **依赖**: 跟哪些已有 RI 关联
  - **估算**: BE 1d / FE 0.5d / 测试 0.5d (人天)
- **优先级** P0/P1/P2 (在文档末尾"优先级"表里)
- **关键路径** + **风险** (业务方 / 业务术语 / 法律法规约束)

**你怎么开工** (流程):
1. **需求开发人员 PR 提了新 §X.Y** → 你 1 小时内读完, 拆 1-3 个 RI
2. **加到** `docs/requirements/ARCH-DECOMPOSITION.md` (追加 RI-N 节)
3. **在 PR 评审里** @ 程序员, 标"可抢" / "需先做 RI-K"
4. **每周** — 看 `TASKS.md` (程序员抢的进度), 调整 RI 优先级 / 砍需求

**你不能**:
- ❌ 写代码 (你的活儿是**拆清楚**, 实现交程序员)
- ❌ 直接动 `TASKS.md` (那是程序员自己维护的)
- ❌ 不审就放行程序员开工 (PR 流转: 需求审 → 架构审 → 实现)

### 1.4 程序员

**你是谁**: 后端 / 前端 / DBA / 测试 / 接手 AI. 你的**唯一任务** = 抢 RI + 写代码 + 提 PR. **不写需求, 不拆 RI** (那是需求开发/架构师).

**你要读** (按角色细分, 见 [§4.1 必读基线包](#41-程序员必读基线包)):
- **后端 / 接手 AI** — `docs/AGENT-IMPL-PLAN.md` + `docs/DB-SCHEMA-v2.md` + `docs/ARCHITECTURE-v2.md` + 抢到的 RI
- **前端** — `frontend/README.md` + `docs/AGENT-IMPL-PLAN.md` §I-12 + 抢到的 RI
- **DBA** — `docs/DB-SCHEMA-v2.md` + 抢到的 RI
- **测试** — `docs/M1-TEST-TASKS.md` + `docs/V2-TEST-TASKS.md` + 抢到的 RI

**你要写**:
- **代码** (后端 Java / 前端 Vue / SQL 迁移 / 单元测试)
- **1 commit / 任务** (不囤)
- **commit message** 格式: `feat(<scope>,RI-N): <description>` 或 `fix(<scope>): <description>`
- **更新 `TASKS.md`** — 标"占用-X (时间)" / "已完成 (X / 日期)"

**你怎么开工** ([§5 抢任务 SOP](#-5-程序员开工-抢任务-sop)):
1. **看 `TASKS.md` 找 `可并行: ✅` + `未开发` + 跟你技术栈匹配的 RI** (或新需求里没拆 RI 的, 自己拆)
2. **改 `TASKS.md` 那一节** `状态: 未开发` → `占用-<你的名字> (<时间>)`
3. **10 秒内** `git add TASKS.md && git commit && git push origin main` — **push 成功 = 占用成功**
4. **写代码** (1-3 小时)
5. **完工** 改 `TASKS.md` 那一节 → `已完成 (<你的名字> / <日期>)` + 代码 commit + push

**你不能**:
- ❌ 改 `REQUIREMENTS.md` (那是需求开发人员的活)
- ❌ 改 `ARCH-DECOMPOSITION.md` RI 拆解 (那是架构师)
- ❌ 推 `minimax` 分支 (那是沙箱的活)
- ❌ 多个 RI 写 1 个 commit (1 commit = 1 RI, 拆不开才能合)

---

## 📂 2. 需求文档位置

> **2026-06-10 业务方决策**: 把需求文档**单独放一个文件夹**, 方便识别. 业务方明确要求"放在哪都要在根 README 说明".

**位置**: `docs/requirements/`

**当前文件**:

| 文件 | 行数 | 内容 | 给谁看 |
|---|---|---|---|
| **`docs/requirements/REQUIREMENTS.md`** | 1342 | 业务需求 v1.1 (含 §13 Mavis 拓展) | 业务方 / 全员 |
| **`docs/requirements/ARCH-DECOMPOSITION.md`** | ~24KB | 需求拆解工作底稿 (RI-1~45) | 架构师 / 后端 / 前端 / DBA / 测试 |

**结构** (简单扁平, 业务方拍"简单"方案):
```
docs/requirements/
├── REQUIREMENTS.md                # 业务需求 v1.1
└── ARCH-DECOMPOSITION.md          # 拆解工作底稿 RI-1~45
```

**历史**:
- 2026-06-10 前: `docs/REQUIREMENTS-v1.md` (扁平)
- 2026-06-10 起: `docs/requirements/` (独立目录, 2 文件扁平)
- 老引用 (`docs/REQUIREMENTS-v1.md`) 已 git-rename 保留历史, 实际路径请用新位置

**重命名映射**:
- `docs/REQUIREMENTS-v1.md` → `docs/requirements/REQUIREMENTS.md` (业务需求, 874→1342 行, 内容未删)
- `docs/REQUIREMENTS-ARCH-DECOMPOSITION.md` → `docs/requirements/ARCH-DECOMPOSITION.md` (拆解底稿, RI-1~45)

---

## 🏗 3. 项目背景与版本路线

### 3.1 v1 (2026-06-08) — 单机基线

- **架构**: 双机主备 → 单机轻量 (定稿)
- **能力**: M0 (端到端) + M1 (档案 CRUD) + M2 (知识库问答框架)
- **LLM**: 智谱 GLM-4 (主) + 不用向量化
- **数据库**: MySQL 8 + FULLTEXT 索引

### 3.2 Plan A~G (2026-06-09) — 增量完工

- 7 个 plan: 业务核心 / UX 修 / RBAC / 测试打磨 / Phase1 架构修 / LLM 用量统计 / 智能 QA Agent
- **结果**: 14 个子项完工, 基线 `0c6325f`

### 3.3 Plan I (2026-06-09) — 智能问答 Agent (v1.0 完成)

- 13 个子项: Spring AI 1.1 + Agent + 5 步 ReAct 循环 + 6 个工具
- **关键工具**: `find_project` (4 级兜底) / `search_fulltext` / `query_mysql` (白名单 + 聚合) / `llm_summarize` / `ask_clarification` / `get_project_business_data`
- **多轮对话**: `MessageChatMemoryAdvisor` + `chat_memory` 表 (30 天)
- **结果**: 13/13 完工, v1.0 投产

### 3.4 v1.1 (2026-06-10 进行中) — 业务规则细化 + 智能问答 UI 升级

- **新增需求**: §5.6.7~§5.11 + §13 (Mavis 拓展: 置信度 3 级 / 隐式切换 5 级 / 决议变更 / 编号预留 / 网络查字典 / 软删 / RBAC 5 角色 / 审计加强 / 看板 / 通知中心 / 导出 / 预览 / 脱敏视图)
- **拆解**: RI-1~45 (架构师维护)
- **基线**: 业务评审通过, 进入开发

### 3.5 v1.2 (规划) — 跨项目 Plan-and-Execute

- 5 个批量工具升级为 Plan-and-Execute (LLM 自主规划查询)
- 通知系统接入邮件/钉钉
- 移动端

### 3.6 v2.0 (远期) — 多用户多项目

- 真正的多用户 (替代单用户 + 角色化)
- 多租户

---

## 📚 4. 必读文档 (按角色分类)

> **不再"按顺序"读**, 按**你的角色**读**对应的那一节**.

### 4.1 程序员必读基线包

**5 分钟扫**, 30 分钟精读, 然后看 [§5 抢任务 SOP](#-5-程序员开工-抢任务-sop) 开工.

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `TASKS.md` (仓库根) | ~300 | **任务分块清单** — 找能抢的 RI, 看 [§5](#-5-程序员开工-抢任务-sop) |
| ② | `docs/requirements/ARCH-DECOMPOSITION.md` | ~24KB | **RI-1~45 拆解底稿** — 找要抢的 RI 详细描述 |
| ③ | `docs/AGENT-IMPL-PLAN.md` | 252 | Plan I 总览 (Spring AI 1.1 + 5 步 ReAct 循环) |
| ④ | `docs/AGENT-FRAMEWORK-DECISION.md` | 885 | 决策:Spring AI 1.1 + **不引** spring-ai-alibaba (踩坑预警 §1.2.1.1 第 6 点) |
| ⑤ | `docs/LESSONS-LEARNED.md` | 19KB | 15+ 踩坑, **避免重蹈覆辙** |

**按技术栈**额外读:
- **后端** — `docs/ARCHITECTURE-v2.md` (685) + `docs/DB-SCHEMA-v2.md` (1060)
- **前端** — `frontend/README.md` + `docs/AGENT-IMPL-PLAN.md` §I-12
- **DBA** — `docs/DB-SCHEMA-v2.md` (1060) + `docs/ENVIRONMENT-DEPENDENCIES.md` (330)
- **测试** — `docs/M1-TEST-TASKS.md` + `docs/V2-TEST-TASKS.md` + `docs/ACCEPTANCE-GUIDE.md`

### 4.2 需求开发人员必读包

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `docs/requirements/REQUIREMENTS.md` | 1342 | **业务全貌** — 5 分钟扫目录, 1 小时精读, 2 小时做笔记 |
| ② | `docs/AGENT-REQUIREMENTS.md` | 257 | 业务访谈原始记录 (15 真实问题 + 7 场景) |
| ③ | `docs/SIMILAR-PRODUCTS.md` | 16KB | 行业参考 (DeepSeek / 豆包 / 类似档案系统) |
| ④ | `docs/AGENT-IMPL-PLAN.md` | 252 | 理解需求怎么落地到代码 (反向理解技术边界) |

### 4.3 需求审核人员必读包

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `docs/requirements/REQUIREMENTS.md` | 1342 | 同 4.2 — 你跟需求开发**一起读**, 你是审 |
| ② | `docs/requirements/ARCH-DECOMPOSITION.md` | ~24KB | 审架构师拆的 RI 落地 |
| ③ | `docs/LESSONS-LEARNED.md` | 19KB | 看业务方之前踩的坑 (避免重复) |

### 4.4 架构师必读包

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `docs/requirements/REQUIREMENTS.md` | 1342 | 业务全貌 |
| ② | `docs/requirements/ARCH-DECOMPOSITION.md` | ~24KB | RI 拆解样例 (RI-1~45), 你要按这个格式加 RI-N |
| ③ | `docs/ARCHITECTURE-v2.md` | 685 | 现有架构基线 |
| ④ | `docs/DB-SCHEMA-v2.md` | 1060 | 现有 schema |
| ⑤ | `docs/architecture/01~05-arch-*.md` (6 文件) | ~50KB | 另一个 AI 写的现代化分章架构 (在 `docs/architecture/`) |
| ⑥ | `docs/AGENT-FRAMEWORK-DECISION.md` | 885 | 决策 (Spring AI 1.1 + 不引 spring-ai-alibaba) |
| ⑦ | `docs/AGENT-IMPL-PLAN.md` | 252 | Plan I 总览 (理解技术决策) |

### 4.5 通用参考 (按需查)

| 文件 | 行数 | 何时读 |
|---|---|---|
| `docs/ENVIRONMENT-DEPENDENCIES.md` | 330 | 部署/排错 |
| `docs/TEAM-ARCHIVE.md` | 12KB | 沙箱 SSH / 环境 |
| `docs/DEPLOYMENT-LOG.md` | 13KB | 部署历史 (出问题翻这里) |
| `docs/GLM-KEY-SETUP.md` | 5KB | 配 GLM API key |
| `docs/DEV-STANDARDS.md` | 466 | 开发标准 (提交前看 §7.2) |
| `docs/ARCH-REUSE-AUDIT.md` | 12KB | 现有可复用模块审计 |
| `docs/M1-README.md` / `docs/M1-TEST-TASKS.md` | - | M1 阶段档案 CRUD + 测试 (历史) |
| `docs/reviews/README.md` | - | 上个接手 AI 的 review 报告 (避坑) |

---

## 🚀 5. 程序员开工: 抢任务 SOP

> **5 分钟开工**. 程序员进来按这个流程, 不会错.

### 5.1 Step 0: 验证环境 (2 分钟)

```bash
# 1. 拉仓库
git clone -b minimax git@gitee.com:frisker/projects-online.git
cd projects-online

# 2. 验证基线 = 0c6325f
git rev-parse HEAD
# 期望: 0c6325f3c454b8713c5ad3f6f86db5e1be87fcf6

# 3. 沙箱编译验证
mvn compile -DskipTests -B -o
# 期望: BUILD SUCCESS (零回归)
```

**基线不对** → 找 [§6 卡住怎么办](#-6-卡住怎么办--找谁). **编译挂** → 看 `docs/LESSONS-LEARNED.md` 15+ 坑.

### 5.2 Step 1: 找可抢任务 (2 分钟)

```bash
# 看任务表
cat TASKS.md | less
```

**找满足 3 条件的 RI**:
1. `可并行: ✅` (或非依赖项, 你能自己干)
2. `状态: 未开发` (没人占)
3. 跟你的技术栈匹配 (后端/前端/DBA/测试)

**找不到?** — 新需求 (§X.Y) 里**还没拆 RI** 的, 你可以**自己拆**(按 `ARCH-DECOMPOSITION.md` 已有 RI-1~45 的格式), 提 PR 给架构师审.

### 5.3 Step 2: 占用 (10 秒, 关键!)

1. **改** `TASKS.md` 那一节: `状态: 未开发` → `状态: 占用-<你的名字> (<当前时间>)`
2. **10 秒内**:
   ```bash
   git add TASKS.md
   git commit -m "chore(tasks): claim <RI-N> by <你的名字>"
   git push origin main
   ```
3. **push 成功 = 占用成功**. 别人看到 push 通知, 不会重复干.

**没 push 之前不算占** — 别人可能先 push, 你只是改了本地, 别人看不到.

### 5.4 Step 3: 干活 (1-3 小时)

1. **读** `ARCH-DECOMPOSITION.md` 对应 RI-N 的"业务/影响表/角色/验收/依赖/估算"
2. **读** 相关 §X.Y (`REQUIREMENTS.md`) 和现有代码
3. **写代码** + 单元测试
4. **跑验收** — `mvn test` / `npm run build` / 浏览器 (按 RI 验收标准)

### 5.5 Step 4: 完工 (10 秒, 关键!)

1. **改** `TASKS.md` 那一节: `状态: 占用-X` → `状态: 已完成 (<你的名字> / <日期>)`
2. **commit + push**:
   ```bash
   git add backend/src/main/java/... \
           backend/src/test/java/... \
           TASKS.md
   git commit -m "feat(<scope>,<RI-N>): <description>"
   git push origin main
   ```
3. **跳到下一个** Step 2 抢任务, 或退出.

### 5.6 严禁 (踩了会被回收)

- ❌ 改 `占用-A` 改回 `未开发` (A 会干掉你)
- ❌ 一个 commit 改多个 RI (1 commit = 1 RI, 拆不开才能合)
- ❌ 占用了但**没 push** 超过 10 分钟 (失联, 别人接管)
- ❌ 直推 `minimax` 分支 (沙箱专属, 接手 AI 推会 403)
- ❌ 改 `REQUIREMENTS.md` (那是需求开发人员)
- ❌ 改 `ARCH-DECOMPOSITION.md` RI 拆解 (那是架构师)

### 5.7 多人抢同一任务 SOP

- **1 个先 push main** 算赢
- **后到的** pull 看到占用, **换任务**
- 解决冲突: `git pull --rebase`, 改不同文件/方法不会冲突
- **冲突激烈** → 找沙箱 PM 仲裁

### 5.8 接管失联任务 SOP

接手 AI 看到任务被占但占用人失联:
1. `git log --author="<占用人名字>" -1` 看占用人最后一次 commit 时间
2. **> 30 分钟没动 + 没 PR 等待合并 = 失联**
3. 接管人: 改 `TASKS.md` `占用-X` → `占用-<自己> (reclaim from X)`, push main
4. 项目方 (Mavis) 后续不会追责 (被回收的任务接管人可继续干)

---

## 🆘 6. 卡住怎么办 / 找谁

| 问题 | 怎么办 |
|---|---|
| **基线 commit 拉不对** | 找项目方 frisker, 给 SSH key / 重置 |
| **编译失败** | `mvn clean compile -DskipTests -B`, 看 `docs/LESSONS-LEARNED.md` (15+ 坑) |
| **LLM 框架装不上** | **不要死磕**, 在 PR 描述里报告"卡住:XXX", Mavis 会接管 |
| **接手 AI 没遇到但 PM 漏写的** | **不要猜**, 在 PR 描述里报告"问题:XXX, 需要 PM 决策" |
| **业务方有新需求** | 转给需求开发人员, 不自己拍板 |
| **RI 拆解不清** | 转给架构师, 不自己拆 (除非占用了 1 个 RI, 可以自己细化子任务) |
| **跟另一个人冲突** | 看 [§5.7 多人抢同一任务 SOP](#57-多人抢同一任务-sop) |
| **Spring AI 1.1 API 找不到 `ReactAgent`** | 看 `docs/AGENT-FRAMEWORK-DECISION.md` §1.2.1.1 第 6 点 + `AGENT-RESEARCH.md` §3.1 |
| **智谱 GLM 4xx 错** | `application.yml` 加 `spring.ai.openai.chat.options.model=glm-4-flash` 显式指定 |
| **多轮对话 LLM 不带上下文** | 确认 `MessageChatMemoryAdvisor` bean 注入 ChatClient, `conversationId` 从 Controller 传 |
| **后端崩了** | `docs/TEAM-ARCHIVE.md` §11 |
| **数据库连不上** | `docs/TEAM-ARCHIVE.md` §11.3 |
| **推不上去** | `docs/TEAM-ARCHIVE.md` §11.5 (SSH key 检查) |

### 找谁

| 角色 | 联系人 |
|---|---|
| **业务方 / 项目方** | frisker (投委会秘书) — 业务决策 + 过目 PR |
| **Mavis 沙箱 PM** | 主 agent — 写方案 + 审完工, 不写代码 |
| **接手 AI / 程序员** | 你 — 写代码 + 提 PR |
| **运维** | 无人 (单机, 自己维护) |

---

## 📂 7. 仓库结构 + 文档演进

### 7.1 仓库结构 (2026-06-10)

```
projects-online/
├── README.md                                # 本文件 (根入口)
├── TASKS.md                                 # 任务分块清单 (谁占用了哪个 RI)
├── .gitignore
│
├── backend/                                 # Spring Boot 3.3 + JPA + Spring AI 1.1
│   ├── pom.xml
│   ├── startup.ps1
│   └── src/main/java/com/archive/
│       ├── agent/                           # Plan I: Agent 核心 (14 文件)
│       │   ├── AgentConfig.java / AgentEngine.java
│       │   ├── AgentRequest.java / AgentResponse.java / AgentStep.java
│       │   ├── ChatMemoryConfig.java        # I-13 多轮对话
│       │   ├── MultiTurnController.java     # I-13
│       │   ├── MultiTurnService.java        # I-13
│       │   ├── prompt/ (AgentSystemPrompt + AgentFewShots)
│       │   ├── tool/ (SearchFulltext / FindProject / QueryMysql / LlmSummarize / AskClarification / GetProjectBusinessData)
│       │   └── listener/ (LlmCallListener + ToolCallListener)
│       ├── controller/QaController.java     # I-10 改造
│       ├── service/GlmService.java          # 降级路径
│       ├── provider/LLMProvider.java        # LLM 抽象层
│       └── ... (现有 M0~M2 + Plan A~G 代码)
│
├── frontend/
│   ├── src/views/Knowledge.vue              # I-12 改造
│   └── src/components/AgentStepsPanel.vue  # I-12 新增
│
├── docs/                                    # ⭐ 17+ 份核心文档
│   ├── requirements/                        # 🆕 v1.1 需求文档独立目录 (2026-06-10)
│   │   ├── REQUIREMENTS.md                  #    业务需求 v1.1 (1342 行)
│   │   └── ARCH-DECOMPOSITION.md            #    v1.1 需求拆解工作底稿 (RI-1~45)
│   ├── architecture/                        # ⏳ 另一个 AI 正在写 (6 份分章架构)
│   │   ├── 01-arch-overview.md
│   │   ├── 02-backend-layer-architecture.md
│   │   ├── 03-frontend-component-architecture.md
│   │   ├── 04-database-schema.md
│   │   ├── 05-deployment-and-environment.md
│   │   └── 06-requirements-gap-analysis.md  # (待补)
│   ├── ARCHITECTURE-v2.md                   # 现有架构基线 (685 行)
│   ├── DB-SCHEMA-v2.md                      # 现有 schema (1060 行)
│   ├── AGENT-IMPL-PLAN.md / AGENT-REQUIREMENTS.md / AGENT-RESEARCH.md / AGENT-FRAMEWORK-DECISION.md  # Plan I 4 份
│   ├── ACCEPTANCE-GUIDE.md / DEV-STANDARDS.md / ENVIRONMENT-DEPENDENCIES.md / GLM-KEY-SETUP.md
│   ├── LESSONS-LEARNED.md                   # 15+ 踩坑
│   ├── TEAM-ARCHIVE.md                      # 沙箱 SSH + 环境
│   ├── DEPLOYMENT-LOG.md / ARCH-REUSE-AUDIT.md / SIMILAR-PRODUCTS.md
│   ├── M1-README.md / M1-TEST-TASKS.md / V2-TEST-TASKS.md
│   └── reviews/                             # 上个接手 AI 的 review 报告
│
├── .mavis/plans/                            # 8 个 plan (A~I, Plan I 完工)
├── config/                                  # 模板 (用户复制填真实值)
├── deploy/                                  # Caddy / WinSW / SQL
└── scripts/                                 # 部署/诊断脚本
```

### 7.2 文档演进 (基线 0c6325f 之前)

| 时间 | 事件 |
|---|---|
| 2026-06-05 | v1 架构 (双机主备, RAG 纠结) |
| 2026-06-06 | v2 架构 (定位单机轻量) |
| 2026-06-07 | v3 架构定稿 (智谱 + 不向量化) + M0 端到端 |
| 2026-06-08 上午 | M1 档案 CRUD 完工 |
| 2026-06-08 下午 | M2 知识库问答框架 + 修复 |
| 2026-06-08 傍晚 | 业务需求 v1 + 8 份核心文档 + 6 个 plan |
| 2026-06-08 晚 | 仓库扁平化 + README 重写 |
| 2026-06-09 上午 | Plan A~G 完工 + Plan H/I 方案定稿 |
| 2026-06-09 中午 | Plan I v1.1 评审修 (3 P0 + 12 P1) |
| 2026-06-09 晚 | Plan I 13/13 完工, 基线 `0c6325f` |
| 2026-06-10 上午 | 部署生产 (125) 调试: CORS / Vite / GLM key / 死循环保护 |
| 2026-06-10 下午 | 业务需求 v1.1 §5.6 + 死循环/LLM 兜底配置可配 |
| 2026-06-10 晚 | **Mavis 拓展**: 业务规则细化 10 处 + 隐含规则 6 类 + 新功能 8 项 (§13) + 需求拆解 RI-1~45 |
| 2026-06-10 深夜 | **README 重构**: 角色导航 (§1) + 需求文档位置 (§2) + 程序员开工 SOP (§5) |

### 7.3 文档维护规则 (Mavis 必守)

> **2026-06-10 Mavis 教训**: 写完磁盘的文档**必须立刻 commit** 或放到 git-tracked 路径, 避免 working copy 误删时无 recovery 路径.

- 任何 `docs/` 下新增/修改文档 → 立即 `git add + commit + push`
- 任何 `TASKS.md` 状态变更 → 立即 `git add + commit + push`
- 任何 `README.md` 重构 → 立即 `git add + commit + push`
- 业务方 PR → 立即审, 立即 merge (审过的)

---

*本文档由 Mavis (沙箱 PM) 维护. 4 类角色 (需求开发/审核/架构师/程序员) 各看 [§1 角色导航](#-1-角色导航-核心) 即可 5 分钟开工.*

*Mavis 在此待命审完工 + 答疑.*