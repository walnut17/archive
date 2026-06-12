# 投委会档案管理系统

> 投委会专属档案管理与智能分析 Web 应用 (Spring Boot 3.3 + Vue 3)
>
> **项目一句话**: 让投委会秘书/委员 5 分钟内回答"这个项目抵押物处理到哪一步了 / 江苏地区空债权平均回收率多少"
>
> **当前阶段**: **v1.1 业务需求评审** (2026-06-10) — Plan I v1.0 (智能问答 Agent) 已完工, 现在按 `docs/requirements/ARCH-DECOMPOSITION.md` 的 **RI-1~45** 推进 v1.1 增量
> **基线 commit**: `0c6325f` (v1.0 + v1.1 §5.6 评审修)
> **生产服务器**: 182.168.1.125 (单机, Windows + Caddy + Spring Boot)
>
> 🚨 **接手 Agent 必读**：**Coder** 与 **代码审查员** 共用 [`TASKS.md`](TASKS.md) → **Case 文件** → [`CASE-FORMAT.md`](CASE-FORMAT.md) 写块。

---

## 📑 目录

- [§0. 项目是什么](#-0-项目是什么)
- [§0.4 快速寻址（30 秒）](#-04-快速寻址30-秒)
- [§1. 角色导航 (核心)](#-1-角色导航-核心)
- [§1.0 找任务（TASKS → Case）](#10-找任务tasks--case)
- [§1.11 代码审查员](#111-代码审查员)
- [§2. 文档与文件夹导航](#-2-文档与文件夹导航)
- [§3. 项目背景与版本路线](#-3-项目背景与版本路线)
- [§4. 必读文档 (按角色分类)](#-4-必读文档-按角色分类)
- [§5. Coder 开工: 抢任务 SOP](#-5-coder-开工-抢任务-sop)
- [§6. 卡住怎么办 / 找谁](#-6-卡住怎么办--找谁)
- [§7. 仓库结构 + 文档演进](#-7-仓库结构--文档演进)
- [§8. Bug 跟踪与修复 (`test-to-settle/`)](#-8-bug-跟踪与修复-test)
- [§9. 自动化测试任务 (`test_task/`)](#-9-自动化测试任务-test_task)
- [§10. 多 Agent 协作架构（可套用）](#-10-多-agent-协作架构可套用)

> **文档总索引**: [`docs/README.md`](docs/README.md)（子目录逻辑 + 按场景查找表）

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

### 0.4 快速寻址（30 秒）

> 不确定文件在哪？**完整查找表**见 [`docs/README.md`](docs/README.md) §2。

**仓库顶层逻辑** — 根目录只留入口和看板，其余按类型进子目录：

```text
projects-online/
├── README.md / TASKS.md / MULTI-AGENT-REPO-ARCHITECTURE.md  ← 入口 + **任务路由** + 协作框架
├── test_task/               ← 自动化测试案例 + 通过时的执行历史
├── docs/                    ← 长期文档（需求/架构/review/运维/交接）
├── test-to-settle/                    ← DEBUG 详情：round、test_bug、complexity
│   ├── round-*.md / test_bug-*.md / complexity.md
│   ├── old/                 ← 历史验收文档（只读）
│   └── logs/
├── upgrade_to_settle/       ← 功能升级 plan（活跃 + done/ 归档）
├── backend/ / frontend/     ← 源代码
└── deploy/                  ← SQL / Caddy / WinSW 配置
```

| 我想… | 第一站 |
|---|---|
| 认项目 / 选角色 | 本文 [§1 角色导航](#-1-角色导航-核心) |
| 抢 DEBUG / UPGRADE（coder） | [`TASKS.md`](TASKS.md) **🎯 任务路由** → 详情路径 |
| 抢自动化测试任务 AT-* | [`TASKS.md`](TASKS.md) AT 节 + [`test_task/`](test_task/README.md) |
| 历史 RI / MOD（Plan I 已完工） | TASKS **📜 历史占表** + [`docs/requirements/ARCH-DECOMPOSITION.md`](docs/requirements/ARCH-DECOMPOSITION.md) |
| 跑通案例、记成功 | 在 `test_task/<案例>.md` **§3** 追加执行历史 |
| 看业务需求 | [`docs/requirements/REQUIREMENTS.md`](docs/requirements/REQUIREMENTS.md) |
| 看架构 / 表结构 | [`docs/architecture/`](docs/architecture/README.md) |
| 部署 / 配 key / 运维 | [`docs/operations/`](docs/operations/README.md) |
| 发现 bug、记 round | [`test-to-settle/round-*.md`](test-to-settle/round-2026-06-11-v1.1-deploy.md) · **仅缺陷** |
| 要修 bug / 做升级（coder） | [`TASKS.md`](TASKS.md) 占 **DEBUG** 或 **UPGRADE** 行 → 打开详情路径 |
| 自动化案例 FAIL | [`test-to-settle/test_bug-*.md`](test-to-settle/test_bug-TEMPLATE.md) → 收入 round |
| 小修闭环（分析/改/审） | 当前 round 的 **§2～§4** |
| 大改 / 搞不定 | [`test-to-settle/complexity.md`](test-to-settle/complexity.md)（路由一行；分析完删行）→ plan → TASKS UPGRADE |
| **新功能 / 升级 plan** | [`upgrade_to_settle/plan-*.md`](upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md) · TASKS **UPGRADE** 行 · 完工 → [`upgrade_to_settle/done/`](upgrade_to_settle/done/README.md) |
| 踩坑 / 历史 review | [`docs/reviews/LESSONS-LEARNED.md`](docs/reviews/LESSONS-LEARNED.md) |
| v1.1 上生产步骤 | [`docs/handoff/v1.1-DEPLOY-GUIDE.md`](docs/handoff/v1.1-DEPLOY-GUIDE.md) |
| 协作架构 / 给其他项目套用 | [§10](#-10-多-agent-协作架构可套用) · [`MULTI-AGENT-REPO-ARCHITECTURE.md`](MULTI-AGENT-REPO-ARCHITECTURE.md) |

**`docs/` 内五层**（详见 [`docs/README.md`](docs/README.md) §1）：

| 目录 | 一句话 |
|---|---|
| `docs/requirements/` | **做什么** — 需求、RI 拆解 |
| `docs/architecture/` | **怎么建** — 架构、schema、Agent 设计 |
| `docs/reviews/` | **评审对线** — Review/Subject 多轮 thread + LESSONS-LEARNED |
| `docs/operations/` | **怎么跑** — 部署、运维、环境、开发规范 |
| `docs/handoff/` | **怎么交** — 版本交付与部署指南 |

---

## 👥 1. 角色导航 (核心)

> **接 agent 第一件事**：认角色 → 看 **[§1.0 找任务](#10-找任务接-agent-第一站)** → 打开 [`TASKS.md`](TASKS.md) **🎯 任务路由**（coder 类工作）或对应 round / `test_task`（记录 / 测试类）。

### 1.0 找任务（TASKS → Case）

> **Case** = 一份文件：`test-to-settle/round-*.md`（DEBUG）或 `upgrade_to_settle/plan-*.md`（UPGRADE）。  
> **Coder 与代码审查员入口都是 [`TASKS.md`](TASKS.md)** — 看 **状态** 列找活，打开 **Case 路径**，按 [`CASE-FORMAT.md`](CASE-FORMAT.md) 追加块。

| 你是谁 | TASKS 找什么状态 | Case 里写什么块 |
|---|---|---|
| **Coder（程序员 / Fix）** | `未开发` · `开发中` | **Coder**（完工 → TASKS 改 **`待审`**） |
| **代码审查员** | `待审` · `审阅中` | **Reviewer**（全过 → **Closer** → `done/` → **删 TASKS 行**） |
| **Recorder / Analyst** | case 已存在、无单独 TASKS 行时 | 在 case 内 **Recorder** / **Analyst** 块 |
| **测试 Agent** | TASKS **AT-***（若有） | `test_task/`（非 case 流程） |
| **架构师 / PM** | complexity `PENDING` | docs + plan → 新 case + TASKS 行 |

```text
TASKS 占行 → Case 文件 Agent Block → 待审 → Review → CLOSED → done/ → 删 TASKS 行
```

---

| 角色 | 你是谁 | 你要读 | 你要写 | 你怎么开工 | 边界 |
|---|---|---|---|---|---|
| **[需求开发人员](#11-需求开发人员)** (产品/业务分析师) | 把业务方嘴里"我们希望 XX"翻译成结构化需求 §X.Y | `docs/requirements/REQUIREMENTS.md` (现有) + 业务方访谈 | 在 `docs/requirements/REQUIREMENTS.md` 加 §X.Y 新章节, 含 5 字段 (业务/数据/角色/验收/依赖) | [§1.1](#11-需求开发人员) | ❌ 不写代码, 不写 RI 拆解 |
| **[需求审核人员](#12-需求审核人员)** (PM/业务方代表) | 审新需求 / 拍板模糊点 / 维护术语 | `docs/requirements/REQUIREMENTS.md` + 业务背景资料 | 在 PR 评审里批 +/- 反馈, 维护 `docs/requirements/REQUIREMENTS.md` §13 决策记录 | [§1.2](#12-需求审核人员) | ❌ 不写代码, 不审 RI 拆解 |
| **[架构师](#13-架构师)** (技术 Lead) | 拆 RI；complexity 升格写 UPGRADE plan | `ARCH-DECOMPOSITION.md` + [`complexity.md`](test-to-settle/complexity.md) | RI-N 或 [`upgrade_to_settle/plan-*.md`](upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md) + TASKS UPGRADE 行 | [§1.3](#13-架构师) · [§1.0](#10-找任务接-agent-第一站) | ❌ 不直接写业务代码 (除非示范) |
| **[程序员 / Coder](#14-程序员)** (接手 AI / 后端 / 前端 / DBA) | 按 TASKS 修 bug / 做 UPGRADE | [`TASKS.md`](TASKS.md) → Case 路径 | **Coder** block + 代码 + TASKS 状态 | [§1.0](#10-找任务接-agent-第一站) · [§5](#-5-coder-开工-抢任务-sop) | ❌ 大改走 complexity |
| **[测试 Agent](#19-测试-agenttest_task)** | 抢 **AT-*** 自动化测试 | TASKS AT 节 + [`test_task/`](test_task/README.md) | PASS 写 §3；FAIL 建 `test_bug` | [§1.0](#10-找任务接-agent-第一站) · [§9](#-9-自动化测试任务-test_task) | ❌ FAIL 时不擅自改业务代码 |
| **[Recorder Agent](#15-recorder-agent)** | 发现 bug 并记入 case | `test-to-settle/round-*.md` | **§1 任务描述**（表）+ 可选 **Recorder** block | [§1.0](#10-找任务接-agent-第一站) · [§8](#-8-bug-跟踪与修复-test) | ❌ 不写根因、不改代码 |
| **[Analyst Agent](#15-analyst-agent)** | 根因分析 + 修改建议 | case **§1** Bug 表 | **Analyst** block；大改 → complexity | [§8](#-8-bug-跟踪与修复-test) | ❌ 默认不改代码 |
| **[代码审查员](#111-代码审查员)** | 审 DEBUG/UPGRADE | [`TASKS.md`](TASKS.md) + [`CODE-REVIEWER.md`](CODE-REVIEWER.md) | **Reviewer** / **Closer** block → **done/** | [§1.11](#111-代码审查员) | ❌ 不替 Coder 改代码 |
| **[Co-test Guide](#110-co-test-双人联调)** | 对话里指挥联调 | `deployment_log` + case §1 | 代拟 log / round 草稿 | [§1.10](#110-co-test-双人联调) | ❌ 默认不改代码 |
| **[Co-test Operator](#110-co-test-双人联调)** | 在 125/浏览器执行 | Guide 给的步骤 | 反馈结果 | [§1.10](#110-co-test-双人联调) | ❌ 有 bug 先记再修 |
| **[Review Agent](#16-review-agent)** | 代码/架构评审 | [`docs/reviews/`](docs/reviews/README.md) OPEN 项 | review 意见 → CLOSED | [§1.6](#16-评审对线-docsreviews) | ❌ 替 Subject 写回复 |
| **[Subject Agent](#16-subject-agent)** | 被评审方 | 对应 `review-*.md` | 文件下方回复 | [§1.6](#16-评审对线-docsreviews) | ❌ **不得**自行 CLOSED |

### 1.1 需求开发人员

**你是谁**: 投委会业务方 (frisker) 嘴里"我想要 XX", 你翻译成结构化需求. **不写代码, 不写 RI 拆解** (那是架构师的事).

**你要读** (按顺序):
1. `docs/requirements/REQUIREMENTS.md` (1342 行, 业务全貌) — **5 分钟扫目录, 1 小时精读**
2. 业务方 4 轮访谈纪要 (在 `docs/requirements/AGENT-REQUIREMENTS.md` + git history) — 看"原话"vs"结构化"的差距
3. `docs/reviews/LESSONS-LEARNED.md` (15+ 踩坑) — 避免重复别人错过的需求

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
3. 业务背景资料 (`docs/requirements/SIMILAR-PRODUCTS.md` + 内部 wiki)

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
2. `docs/architecture/ARCHITECTURE-v2.md` (685 行) — 现有架构基线
3. `docs/architecture/DB-SCHEMA-v2.md` (1060 行) — 现有 schema (你要加表/字段看这里)
4. `docs/architecture/01~06-arch-*.md` (另一个 AI 写的分章架构) — 现代化拆解参考
5. `docs/architecture/AGENT-FRAMEWORK-DECISION.md` (885 行) — 关键决策 (Spring AI 1.1 + 不引 spring-ai-alibaba)

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
- ❌ 不审就放行程序员开工 (PR 流转: 需求审 → 架构审 → 实现)

**开 UPGRADE case 时**（complexity 升格 / 主动立项）:
1. 读 [`CASE-FORMAT.md`「生成 case 的 Agent」](CASE-FORMAT.md)
2. 文件名 `plan-YYYY-MM-DD-<英文简述>.md` · **路由 ID = 文件名无 `.md`**
3. [`TASKS.md`](TASKS.md) UPGRADE 行首列 = 同一路由 ID（**禁止 `UP-MMDD-NN`**）
4. complexity 升格后 **删 complexity 行** · round bug 标「已转 plan-…」

**你不能**（case 相关）:
- ❌ 自造与文件名不一致的路由 ID

### 1.4 程序员（Coder：DEBUG + UPGRADE）

**你是谁**: 后端 / 前端 / DBA / 接手 AI。**Coder 类工作** = 修 bug（DEBUG）或做升级（UPGRADE），**都从 [`TASKS.md`](TASKS.md) 🎯 任务路由 占坑**。

**找任务**（见 [§1.0](#10-找任务接-agent-第一站)）:
1. 打开 [`TASKS.md`](TASKS.md) **🎯 任务路由** — 挑 `未开发` 的 **DEBUG** 或 **UPGRADE** 行
2. 按「详情路径」打开 `test-to-settle/round-*.md` 或 `upgrade_to_settle/plan-*.md` 读全文
3. **10 秒内 push 占用**（见 [§5](#-5-coder-开工-抢任务-sop)）

**你要读** (按任务类型):
- **DEBUG** — round 对应 §（现象/根因/建议）+ 相关源码
- **UPGRADE** — plan §0～§4（需求/架构/开发说明）+ [`docs/architecture/`](docs/architecture/README.md)
- **基线包** — [§4.1](#41-coder-必读基线包)

**你要写**:
- **代码** + 单元测试
- **1 commit / 任务**
- **commit message**: `fix(<scope>,T-MMDD-NN): …` 或 `feat(<scope>,plan-…): …`（scope 可用路由 ID 或模块名）
- **TASKS.md** — `占用-<名>` → `已完成`
- **round §3**（DEBUG）或 **plan §5**（UPGRADE）留痕

**你不能**:
- ❌ 只读 `test-to-settle/` 不占 TASKS（会被别的 agent 重复抢）
- ❌ 大改硬在 round 里做（应 ESCALATED → complexity → UPGRADE plan）
- ❌ 改 `REQUIREMENTS.md` / 擅自拆 RI
- ❌ 推 `minimax` 分支

**历史 Plan I RI**：见 TASKS **📜 历史占表**，新工作勿占。

### 1.5 DEBUG case 各 Agent（`test-to-settle/round-*.md`）

> **格式权威**：[`CASE-FORMAT.md`](CASE-FORMAT.md) · 模板 [`round-TEMPLATE.md`](test-to-settle/round-TEMPLATE.md)  
> **开新 round 命名**：见 [`CASE-FORMAT.md`「生成 case 的 Agent」](CASE-FORMAT.md) — 路由 ID = `round-YYYY-MM-DD-<简述>` = 文件名无 `.md`  
> **§1 任务描述**（Bug 表，只写一次）→ **Agent Blocks**（之后全部留痕，a-b 格式）

```text
§1 任务描述 → Recorder? → Analyst? → Coder ↔ Reviewer → Closer → done/
```

| Agent | 写什么 | 做什么 |
|---|---|---|
| **Recorder** | §1 表 + 可选 **Recorder** block | 记 bug；**开新 round 时**按 CASE-FORMAT 命名 + TASKS 行 |
| **Analyst** | **Analyst** block | 根因；`ESCALATED` → complexity |
| **Coder** | **Coder** block + commit | TASKS 占 **DEBUG** 行 |
| **Reviewer** | **Reviewer** / **Closer** block | 审 diff；**Closer** 才关 case |

> **注意**：case **Reviewer** ≠ [§1.6](#16-评审对线-docsreviews) 的 **Review Agent**（`docs/reviews/`）。

### 1.6 评审对线（`docs/reviews/`）

> **一轮 review 一个文件**；Review Agent 与 Subject Agent **在文件里对线**；**只有 Review Agent 宣布 `CLOSED`，该 review 才算结束**。
> 细节：[`docs/reviews/README.md`](docs/reviews/README.md) · 模板 [`docs/reviews/review-TEMPLATE.md`](docs/reviews/review-TEMPLATE.md)

```text
Review Agent 新建 review-*.md（OPEN）→ Round 1 意见
        ↓
Subject Agent 下方写 Round 1 回复（是否接受 / 措施 / commit / 结果）
        ↓
Review Agent Round 2：CONTINUE 续提要求 或 CLOSED 宣布评审结束
```

| Agent | 做什么 | 不能做什么 |
|---|---|---|
| **Review Agent** | `cp review-TEMPLATE.md` 新建文件；写/追加评审；验证后 **宣布 CLOSED** | 替 Subject 写回复 |
| **Subject Agent** | 在文件**末尾**追加回复；改代码；填 commit/结果 | 把状态改 **CLOSED** |

与 **§1.5 case Reviewer** 分工：case 审 **TASKS 路由的代码**；`docs/reviews/` 审 **架构/交付对线**。

#### 1.6.1 Review Agent

**你要读**：Subject 的 commit / diff · 相关 `architecture/` · 可选关联 `test-to-settle/round-*.md`

**你要写**：Round 1 意见表（P0/P1/P2）；跟进 Round；结案时改顶部状态 **`CLOSED`** + 评审结束声明

**你不能**：自己改被审代码（应打回 Subject）

#### 1.6.2 Subject Agent

**你要读**：review 文件最新 Round 的评审意见

**你要写**：对应 Round 回复块 — 总体态度（全部/部分/不接受）+ 逐项表（措施、commit、结果）

**你不能**：改 Review Agent 上文；**不能**自行宣布评审结束

### 1.9 测试 Agent（`test_task/` + TASKS **AT-***）

> **找任务**：[§1.0](#10-找任务接-agent-第一站) → TASKS **AT-*** 节。与 §5 Coder 同一套 push 占坑规则。

**你要读**：[`test_task/README.md`](test_task/README.md) · [`TASKS.md`](TASKS.md) **AT-*** 节 · 对应案例文件

**你要写**：
- **PASS**：案例文件 **§3 执行历史** 追加（Agent、时间、基线、**已成功**）+ TASKS 标 `已完成`
- **FAIL**：[`test-to-settle/test_bug-TEMPLATE.md`](test-to-settle/test_bug-TEMPLATE.md) → `test_bug-*.md`；案例 §3 记 FAIL；**不要**自己修 bug

**你不能**：FAIL 时擅自改业务代码（交给 round §2～§4）；通过结果写进 `test-to-settle/` round（那是 bug 专用）

详情：[§9](#-9-自动化测试任务-test_task)

### 1.10 Co-test 双人联调

> **Guide（AI）指挥 + Operator（你）在 125/浏览器执行**；操作记 [`deployment_log.md`](docs/operations/deployment_log.md)，bug 记 [`test-to-settle/`](test-to-settle/README.md)。详见 [`MULTI-AGENT-REPO-ARCHITECTURE.md` §7.5](MULTI-AGENT-REPO-ARCHITECTURE.md#75-co-test-双人联调guide--operator)。

| 角色 | 做什么 |
|---|---|
| **Guide Agent** | 一步一步给命令/点击路径；根据你的反馈给下一步；帮写 log / round 草稿 |
| **Operator（你）** | 执行并**原文反馈**结果（✅/❌/控制台）；有 bug 确认写入 round |

**本轮默认**：先 **VERIFY** 已修项（T-0611-08/12/16 等），测过无 bug 只更新 `deployment_log`。

---

### 1.11 代码审查员

> **入口**：[`TASKS.md`](TASKS.md) 状态 **`待审` / `审阅中`**（与 Coder 同表）。  
> **SOP**：[`CODE-REVIEWER.md`](CODE-REVIEWER.md) · **块格式**：[`CASE-FORMAT.md`](CASE-FORMAT.md)

1. TASKS 占 **`待审`** → **`审阅中`**
2. 打开 Case 路径 → 先读 **§1 任务描述** → 再读 **Agent Blocks** + diff → 追加 **Reviewer** 块
3. 打回：TASKS → **`开发中`**
4. 整 case 通过：**Closer** 块 → `done/` → **删除 TASKS 行**

**你不能**：未读 diff 通过；自己改业务代码；CLOSED 后仍留 TASKS 行。

---

## 📂 2. 文档与文件夹导航

> **2026-06-11 整理原则**: 长期文档不进根目录；**需求 / 架构 / 评审 / 运维 / 测试** 各归其位。
> 详细逻辑与子目录文件列表 → **[`docs/README.md`](docs/README.md)**

### 2.1 根目录 Markdown

| 文件 | 为何留根 | 链接 |
|---|---|---|
| **`README.md`** | 项目入口，角色导航 | 本文 |
| **`TASKS.md`** | **Coder + 审查员唯一入口**（Case 路由） | [TASKS.md](TASKS.md) |
| **`CASE-FORMAT.md`** | Case 格式 + **生成 case 命名规范** | [CASE-FORMAT.md](CASE-FORMAT.md) |
| **`CODE-REVIEWER.md`** | 审查员 SOP | [CODE-REVIEWER.md](CODE-REVIEWER.md) |
| **`MULTI-AGENT-REPO-ARCHITECTURE.md`** | 多 Agent 协作框架（可套用） | [协作架构](MULTI-AGENT-REPO-ARCHITECTURE.md) |

其它 markdown **不应**出现在根目录（历史文件已迁入 `docs/` / `test-to-settle/`）。

### 2.2 五大工作区

| 区域 | 路径 | 放什么 |
|---|---|---|
| **任务路由** | [`TASKS.md`](TASKS.md) | **入口**：DEBUG / UPGRADE / AT-* 占坑 + 详情路径 |
| **DEBUG 详情** | [`test-to-settle/`](test-to-settle/README.md) | `round-*.md`、`test_bug-*.md`、`complexity.md` |
| **UPGRADE 详情** | [`upgrade_to_settle/`](upgrade_to_settle/README.md) | `plan-*.md`（活跃 + `done/` 归档） |
| **长期文档** | [`docs/`](docs/README.md) | 需求、架构、review、运维、交接 |
| **自动化测试** | [`test_task/`](test_task/README.md) | 案例步骤 + **PASS** 执行历史 |

### 2.3 需求文档 — `docs/requirements/`

| 文件 | 内容 | 给谁 |
|---|---|---|
| [`REQUIREMENTS.md`](docs/requirements/REQUIREMENTS.md) | 业务需求 v1.1 主文档 | 业务方 / 全员 |
| [`ARCH-DECOMPOSITION.md`](docs/requirements/ARCH-DECOMPOSITION.md) | RI-1~45 拆解 | 架构师 / 程序员 |
| [`AGENT-REQUIREMENTS.md`](docs/requirements/AGENT-REQUIREMENTS.md) | Agent 业务访谈 | 架构师 / 后端 |
| [`SUPPLEMENTARY-REQUIREMENTS.md`](docs/requirements/SUPPLEMENTARY-REQUIREMENTS.md) | 审计补充需求 | 程序员 |
| [`SIMILAR-PRODUCTS.md`](docs/requirements/SIMILAR-PRODUCTS.md) | 行业参考 | 需求 / 产品 |

```
docs/requirements/
├── README.md                  ← 本子目录索引
├── REQUIREMENTS.md
├── ARCH-DECOMPOSITION.md
├── AGENT-REQUIREMENTS.md
├── SUPPLEMENTARY-REQUIREMENTS.md
└── SIMILAR-PRODUCTS.md
```

### 2.4 架构文档 — `docs/architecture/`

| 首读 | 文件 |
|---|---|
| 架构基线 | [`ARCHITECTURE-v2.md`](docs/architecture/ARCHITECTURE-v2.md) |
| 数据库 | [`DB-SCHEMA-v2.md`](docs/architecture/DB-SCHEMA-v2.md) |
| Agent | [`AGENT-IMPL-PLAN.md`](docs/architecture/AGENT-IMPL-PLAN.md) · [`AGENT-FRAMEWORK-DECISION.md`](docs/architecture/AGENT-FRAMEWORK-DECISION.md) |
| 分章 | [`01-arch-overview.md`](docs/architecture/01-arch-overview.md) ~ `06-*.md` |
| 历史 | [`history/`](docs/architecture/history/) |

完整列表 → [`docs/architecture/README.md`](docs/architecture/README.md)

### 2.5 其它 `docs/` 子目录

| 目录 | 索引 | 用途 |
|---|---|---|
| [`reviews/`](docs/reviews/README.md) | README + `review-TEMPLATE.md` + `LESSONS-LEARNED.md` | 评审对线（OPEN→CLOSED）、踩坑 |
| [`operations/`](docs/operations/README.md) | README | 部署、运维、GLM、DEV-STANDARDS |
| [`handoff/`](docs/handoff/) | — | v1.1 交付报告与部署指南 |

### 2.6 历史路径（旧链接对照）

| 旧位置 | 现位置 |
|---|---|
| 根 `architecture-v*.md` / `DEPLOYMENT.md` | `docs/architecture/history/` · `docs/operations/` |
| 根 `VERIFICATION-REPORT.md` | `test-to-settle/old/` |
| `docs/` 根下的 `*.md`（除 README） | 已迁入对应子目录，见 [`docs/README.md` §5](docs/README.md#5-路径迁移备忘-2026-06-11) |

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

### 4.1 Coder 必读基线包（DEBUG / UPGRADE）

**5 分钟扫**，然后 [§1.0 找任务](#10-找任务接-agent-第一站) → [§5 占坑 SOP](#-5-coder-开工-抢任务-sop)。

| 序 | 文件 | 必读理由 |
|---|---|---|
| ① | [`TASKS.md`](TASKS.md) **🎯 任务路由** | **第一站** — 挑 DEBUG/UPGRADE、占坑、拿详情路径 |
| ② | 详情文件（TASKS 指向的路径） | DEBUG → round §；UPGRADE → plan §0～§4 |
| ③ | `docs/reviews/LESSONS-LEARNED.md` | 15+ 踩坑，避免重蹈覆辙 |
| ④ | `docs/architecture/AGENT-IMPL-PLAN.md` | Agent 框架总览（改后端/Agent 时） |
| ⑤ | `docs/architecture/DB-SCHEMA-v2.md` | 表结构（改数据层时） |

**按技术栈**额外读:
- **后端** — `docs/architecture/ARCHITECTURE-v2.md` + `AGENT-FRAMEWORK-DECISION.md`
- **前端** — `frontend/README.md`
- **历史 RI 背景** — `docs/requirements/ARCH-DECOMPOSITION.md`（Plan I 已完，仅供理解上下文）

### 4.2 需求开发人员必读包

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `docs/requirements/REQUIREMENTS.md` | 1342 | **业务全貌** — 5 分钟扫目录, 1 小时精读, 2 小时做笔记 |
| ② | `docs/requirements/AGENT-REQUIREMENTS.md` | 257 | 业务访谈原始记录 (15 真实问题 + 7 场景) |
| ③ | `docs/requirements/SIMILAR-PRODUCTS.md` | 16KB | 行业参考 (DeepSeek / 豆包 / 类似档案系统) |
| ④ | `docs/architecture/AGENT-IMPL-PLAN.md` | 252 | 理解需求怎么落地到代码 (反向理解技术边界) |

### 4.3 需求审核人员必读包

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `docs/requirements/REQUIREMENTS.md` | 1342 | 同 4.2 — 你跟需求开发**一起读**, 你是审 |
| ② | `docs/requirements/ARCH-DECOMPOSITION.md` | ~24KB | 审架构师拆的 RI 落地 |
| ③ | `docs/reviews/LESSONS-LEARNED.md` | 19KB | 看业务方之前踩的坑 (避免重复) |

### 4.4 架构师必读包

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `docs/requirements/REQUIREMENTS.md` | 1342 | 业务全貌 |
| ② | `docs/requirements/ARCH-DECOMPOSITION.md` | ~24KB | RI 拆解样例 (RI-1~45), 你要按这个格式加 RI-N |
| ③ | `docs/architecture/ARCHITECTURE-v2.md` | 685 | 现有架构基线 |
| ④ | `docs/architecture/DB-SCHEMA-v2.md` | 1060 | 现有 schema |
| ⑤ | `docs/architecture/01~05-arch-*.md` (6 文件) | ~50KB | 另一个 AI 写的现代化分章架构 (在 `docs/architecture/`) |
| ⑥ | `docs/architecture/AGENT-FRAMEWORK-DECISION.md` | 885 | 决策 (Spring AI 1.1 + 不引 spring-ai-alibaba) |
| ⑦ | `docs/architecture/AGENT-IMPL-PLAN.md` | 252 | Plan I 总览 (理解技术决策) |

### 4.5 测试轮次 Agent 必读包

| 序 | 文件 | 必读理由 |
|---|---|---|
| ① | [§1.0 找任务](#10-找任务接-agent-第一站) | 按角色定位：Fix 看 TASKS DEBUG；Recorder 看 round |
| ② | [§8](#-8-bug-跟踪与修复-test) + [`test-to-settle/README.md`](test-to-settle/README.md) | 四 Agent 分工 + 留痕规则 |
| ③ | [`TASKS.md`](TASKS.md) **🎯 任务路由** | **Coder 占 DEBUG/UPGRADE 行**；新 bug 时 Recorder 配合 §1 |
| ④ | 当前 [`test-to-settle/round-*.md`](test-to-settle/round-2026-06-11-v1.1-deploy.md) | 只改你负责的那一节 |
| ⑤ | [`test-to-settle/complexity.md`](test-to-settle/complexity.md) | 大改中转（不进 TASKS） |

### 4.6 通用参考 (按需查)

| 文件 | 行数 | 何时读 |
|---|---|---|
| `docs/operations/ENVIRONMENT-DEPENDENCIES.md` | 330 | 部署/排错 |
| `docs/operations/TEAM-ARCHIVE.md` | 12KB | 沙箱 SSH / 环境 |
| `docs/operations/DEPLOYMENT-LOG.md` | 13KB | 部署历史 (出问题翻这里) |
| `docs/operations/GLM-KEY-SETUP.md` | 5KB | 配 GLM API key |
| `docs/operations/DEV-STANDARDS.md` | 466 | 开发标准 (提交前看 §7.2) |
| `docs/architecture/ARCH-REUSE-AUDIT.md` | 12KB | 现有可复用模块审计 |
| `test-to-settle/old/M1-README.md` / `test-to-settle/old/M1-TEST-TASKS.md` | - | M1 阶段档案 CRUD + 测试 (历史) |
| `docs/reviews/README.md` | - | 上个接手 AI 的 review 报告 (避坑) |

---

## 🚀 5. Coder 开工: 抢任务 SOP

> **Coder 通用**。5 分钟：TASKS 占坑 → 读 case §1 → 写 **Coder** block + 代码。

### 5.1 Step 0: 验证环境 (2 分钟)

```bash
git clone git@gitee.com:frisker/projects-online.git
cd projects-online
git pull origin main
mvn compile -DskipTests -B -o   # 期望 BUILD SUCCESS
```

**编译挂** → `docs/reviews/LESSONS-LEARNED.md`

### 5.2 Step 1: TASKS 找 Case (1 分钟)

打开 [`TASKS.md`](TASKS.md) → 选 **`未开发` / `开发中`** 行 → 记下 **Case 路径**。

### 5.3 Step 2: 占坑 (10 秒)

1. 改该行：`状态` → **`开发中`**，`最后 Agent` / `最后更新` 填好  
2. push TASKS.md

### 5.4 Step 3: 写 Case + 代码

1. TASKS 占 **`未开发`/`开发中`** → 打开 Case → 先读 **§1** → 追加 **Coder** 块（[`CASE-FORMAT.md`](CASE-FORMAT.md)）  
2. commit 代码 + case + TASKS  
3. 本 case 项完工 → TASKS **`待审`**

### 5.5 Step 4: 审查与关单（审查员）

见 [`CODE-REVIEWER.md`](CODE-REVIEWER.md)：Reviewer 块 → Closer → `done/` → **删 TASKS 行**

### 5.6 严禁

- ❌ 只读 `test-to-settle/` / `upgrade_to_settle/` **不占 TASKS**
- ❌ 占坑不 push 超过 10 分钟
- ❌ 大改硬在 round 里做（走 complexity → UPGRADE）
- ❌ 一个 commit 混多个无关任务
- ❌ 直推 `minimax` 分支

### 5.7 多人抢同一任务

谁先 push 谁占；后到换下一行 `未开发`。

### 5.8 接管失联任务

占用人 >30 分钟无 commit → 改 `占用-<自己> (reclaim from X)` 并 push。

---

## 🆘 6. 卡住怎么办 / 找谁

| 问题 | 怎么办 |
|---|---|
| **基线 commit 拉不对** | 找项目方 frisker, 给 SSH key / 重置 |
| **编译失败** | `mvn clean compile -DskipTests -B`, 看 `docs/reviews/LESSONS-LEARNED.md` (15+ 坑) |
| **LLM 框架装不上** | **不要死磕**, 在 PR 描述里报告"卡住:XXX", Mavis 会接管 |
| **接手 AI 没遇到但 PM 漏写的** | **不要猜**, 在 PR 描述里报告"问题:XXX, 需要 PM 决策" |
| **业务方有新需求** | 转给需求开发人员, 不自己拍板 |
| **RI 拆解不清** | 转给架构师, 不自己拆 (除非占用了 1 个 RI, 可以自己细化子任务) |
| **跟另一个人冲突** | 看 [§5.7 多人抢同一任务 SOP](#57-多人抢同一任务-sop) |
| **Spring AI 1.1 API 找不到 `ReactAgent`** | 看 `docs/architecture/AGENT-FRAMEWORK-DECISION.md` §1.2.1.1 第 6 点 + `docs/architecture/AGENT-RESEARCH.md` §3.1 |
| **智谱 GLM 4xx 错** | `application.yml` 加 `spring.ai.openai.chat.options.model=glm-4-flash` 显式指定 |
| **多轮对话 LLM 不带上下文** | 确认 `MessageChatMemoryAdvisor` bean 注入 ChatClient, `conversationId` 从 Controller 传 |
| **后端崩了** | `docs/operations/TEAM-ARCHIVE.md` §11 |
| **数据库连不上** | `docs/operations/TEAM-ARCHIVE.md` §11.3 |
| **推不上去** | `docs/operations/TEAM-ARCHIVE.md` §11.5 (SSH key 检查) |

### 找谁

| 角色 | 联系人 |
|---|---|
| **业务方 / 项目方** | frisker (投委会秘书) — 业务决策 + 过目 PR |
| **Mavis 沙箱 PM** | 主 agent — 写方案 + 审完工, 不写代码 |
| **接手 AI / 程序员** | 你 — 写代码 + 提 PR |
| **运维** | 无人 (单机, 自己维护) |

---

## 📂 7. 仓库结构 + 文档演进

### 7.1 仓库结构 (2026-06-11)

> **导航**: [§0.4 快速寻址](#-04-快速寻址30-秒) · 详细文档逻辑 [`docs/README.md`](docs/README.md)

```
projects-online/
├── README.md              ← 项目入口（本文件）
├── TASKS.md               ← **任务路由** + 占用（DEBUG / UPGRADE / AT-*）
├── MULTI-AGENT-REPO-ARCHITECTURE.md  ← 多 Agent 协作框架
│
├── test_task/             ← 自动化测试案例 + PASS 执行历史
│
├── test-to-settle/        ← DEBUG 详情：round、test_bug、complexity
│   ├── round-*.md / test_bug-*.md / complexity.md
│   └── old/
│
├── upgrade_to_settle/     ← UPGRADE 详情：plan-*.md + done/
│
├── docs/                  ← 长期文档
├── backend/               ← Spring Boot 源码
├── frontend/              ← Vue 3 源码
├── deploy/                ← SQL 迁移、Caddy、WinSW
├── config/                ← 配置模板
└── scripts/               ← 诊断脚本
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
| 2026-06-11 | **文档整理**: 长期文档迁入 `docs/` 子目录; 测试文档迁入 `test-to-settle/`; `docs/` 根只留 README |

### 7.3 文档维护规则 (Mavis 必守)

> **2026-06-10 Mavis 教训**: 写完磁盘的文档**必须立刻 commit** 或放到 git-tracked 路径, 避免 working copy 误删时无 recovery 路径.

- 任何 `docs/` 下新增/修改文档 → 立即 `git add + commit + push`
- 任何 `TASKS.md` 状态变更 → 立即 `git add + commit + push`
- 任何 `README.md` 重构 → 立即 `git add + commit + push`
- 业务方 PR → 立即审, 立即 merge (审过的)

---

## 🧪 8. Bug 跟踪与修复 (`test-to-settle/`)

> **`test-to-settle/` 只记 bug。** 发现途径：**(1) 交互式 deploy 联调** **(2) Agent 自主跑测试用例**。测过无缺陷、纯部署步骤 → 不进 `round-*.md`。
>
> **Case** = `round-YYYY-MM-DD-*.md`。**§1 任务描述**（固定）→ **Agent Blocks**（动态，见 [`CASE-FORMAT.md`](CASE-FORMAT.md)）。  
> **小修**在 case 内闭环；**大改** → [`complexity.md`](test-to-settle/complexity.md) → UPGRADE plan。  
> **细节**：[`test-to-settle/README.md`](test-to-settle/README.md)

### 8.1 Case 时间线

```text
§1 任务描述 → Recorder? → Analyst? → Coder → Reviewer(APPROVED) → Closer → done/ → TASKS 删行
                      ↘ ESCALATED → complexity.md → PM/架构 → plan
```

**关 case 条件**：各 `T-*` 已 APPROVED / ESCALATED / WONTFIX，且有 **`role: Closer`** 块。  
**历史 round** 可能仍含旧 §2～§7 表 — **新留痕只追加 Agent Blocks**。

### 8.2 目录与文件

| 路径 | 用途 |
|---|---|
| [`test-to-settle/README.md`](test-to-settle/README.md) | 工作流、状态机、Agent 一句话指引 |
| [`test-to-settle/round-TEMPLATE.md`](test-to-settle/round-TEMPLATE.md) | **新开一轮**时复制 |
| [`test-to-settle/round-*.md`](test-to-settle/round-2026-06-11-v1.1-deploy.md) | DEBUG case（§0/§1 + Agent Blocks） |
| [`test-to-settle/complexity.md`](test-to-settle/complexity.md) | 大改中转路由；分析完删行，见文件文首 |
| [`docs/operations/deployment_log.md`](docs/operations/deployment_log.md) | 部署**操作**时间线（不是 bug 清单） |

**Bug ID**：`T-MMDD-NN` · **Complexity ID**：`C-MMDD-NN` · **Commit**：`fix(<scope>,T-0611-XX): …`

### 8.3 各 Agent 写什么

| 你是… | 找任务 | 打开 | 写什么 |
|---|---|---|---|
| **Coder** | TASKS **DEBUG/UPGRADE** | Case 路径 | **Coder** block + 代码 |
| Recorder | case 已存在 | `round-*.md` | **§1** 表 + 可选 **Recorder** block |
| Analyst | case §1 Bug 表 | 同上 | **Analyst** block |
| Reviewer | TASKS **待审** | 同上 + diff | **Reviewer** / **Closer** block |
| PM / 架构 | complexity | → plan §1/§2 → TASKS UPGRADE | 见 [`complexity.md`](test-to-settle/complexity.md) |

### 8.4 当前轮次

| 轮次 | 文件 | 轮次状态 |
|---|---|---|
| v1.1 / 0611 | [`round-2026-06-11-v1.1-deploy.md`](test-to-settle/round-2026-06-11-v1.1-deploy.md) | `OPEN`（TASKS **`round-2026-06-11-v1.1-deploy`**） |

### 8.5 与 `TASKS.md` 的关系

| 目录 | 角色 |
|---|---|
| **`TASKS.md` 🎯 任务路由** | **入口**：DEBUG / UPGRADE / AT-* 占坑 + 详情路径（不含全文） |
| **`test-to-settle/`** | **DEBUG 详情**（round、test_bug）；小修当轮闭环 |
| **`test-to-settle/complexity.md`** | 大改**中转路由**（不进 TASKS）；出站删行，全文在 docs/plan |
| **`upgrade_to_settle/`** | **UPGRADE case**（plan：§0/§1/§2 + Agent Blocks）；与 DEBUG **脱钩** |
| **`test_task/`** | 测试案例；**PASS** 写 §3 执行历史 |

```text
coder 开工：TASKS 占行 → 读 test-to-settle 或 upgrade_to_settle 详情 → 写代码
大改：complexity 加一行 → docs/plan/TASKS UPGRADE → 删 complexity 行
```

---

## 🤖 9. 自动化测试任务 (`test_task/`)

> **案例在 `test_task/`**（有案例才有文件；模板 [`case-TEMPLATE.md`](test_task/case-TEMPLATE.md)）。**占用**：有案例时在 `TASKS.md` AT 节追加，**当前无 AT 任务**。
> **细节以 [`test_task/README.md`](test_task/README.md) 为准。**

### 9.1 三分工

| 位置 | 内容 |
|---|---|
| **`test_task/*.md`** | 步骤、预期、**通过**时的执行历史 |
| **`TASKS.md` AT-*** | 谁占用 / 谁完工 |
| **`test-to-settle/test_bug-*.md`** | **失败** bug 单 → 进 case **§1** |

### 9.2 工作流

```text
抢 AT-*（TASKS.md push 占坑）
        ↓
执行 test_task/<案例>.md
        ├─ PASS → 案例 §3 追加「已成功」+ TASKS 已完成 + push
        └─ FAIL → test-to-settle/test_bug-*.md → Recorder 收入 round §1 → 其他 Agent 处理
```

### 9.3 抢任务 SOP（与 §5 相同）

1. 确认 `TASKS.md` AT 节已有真实 **AT-XXX**（无则先按 [`test_task/README.md`](test_task/README.md) 建案例 + 条目）
2. `占用-<名字>` → 10 秒内 push
3. 跑案例 → 按结果写 **test_task** 或 **test_bug**
4. PASS 时：`已完成` + push

### 9.4 文件

| 路径 | 用途 |
|---|---|
| [`test_task/README.md`](test_task/README.md) | 完整规范 |
| [`test_task/case-TEMPLATE.md`](test_task/case-TEMPLATE.md) | 新建案例 |
| [`test-to-settle/test_bug-TEMPLATE.md`](test-to-settle/test_bug-TEMPLATE.md) | FAIL 时复制 |

---

## 📐 10. 多 Agent 协作架构（可套用）

> **完整说明**（给其他项目复制时用）：[`MULTI-AGENT-REPO-ARCHITECTURE.md`](MULTI-AGENT-REPO-ARCHITECTURE.md)

本仓库用 **文件 + Git 占用** 协调多 Agent，核心分层：

| 层 | 目录 | 职责 |
|---|---|---|
| 入口 | `README.md` | **角色导航** + [§1.0 找任务](#10-找任务接-agent-第一站) |
| Coder 路由 | `TASKS.md` | DEBUG / UPGRADE / AT-* **占坑 + 详情路径** |
| **代码审查** | `CODE-REVIEWER.md` + 两目录 `STATUS.md` | 待审队列 → §4/§6 → **done/** |
| DEBUG 详情 | `test-to-settle/` | case（§1 + Agent Blocks）、`complexity` 中转 |
| UPGRADE 详情 | `upgrade_to_settle/` | plan §0～§7，完工 → `done/` |
| 文档 | `docs/` | 需求 / 架构 / 评审 / 运维 |
| 自动化测试 | `test_task/` | 案例 + PASS 历史 |
| 评审 | `docs/reviews/` | Review ↔ Subject 对线 |

各层 README 均指向上述架构文档。新项目套用见该文档 **§11 检查清单**。

---

*本文档由 Mavis (沙箱 PM) 维护. **接 agent 第一站**：[§1.0 找任务](#10-找任务接-agent-第一站) → [`TASKS.md`](TASKS.md)；bug [§8](#-8-bug-跟踪与修复-test)；测试 [§9](#-9-自动化测试任务-test_task).*

*Mavis 在此待命审完工 + 答疑.*