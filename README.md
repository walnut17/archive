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
> **查 bug 四轮次 Agent** → [§8](#-8-bug-跟踪与修复-test) · **抢自动化测试** → [§9](#-9-自动化测试任务-test_task) · **协作架构（可套用其他项目）** → [§10](#-10-多-agent-协作架构可套用) · [`MULTI-AGENT-REPO-ARCHITECTURE.md`](MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 📑 目录

- [§0. 项目是什么](#-0-项目是什么)
- [§0.4 快速寻址（30 秒）](#-04-快速寻址30-秒)
- [§1. 角色导航 (核心)](#-1-角色导航-核心)
- [§2. 文档与文件夹导航](#-2-文档与文件夹导航)
- [§3. 项目背景与版本路线](#-3-项目背景与版本路线)
- [§4. 必读文档 (按角色分类)](#-4-必读文档-按角色分类)
- [§5. 程序员开工: 抢任务 SOP](#-5-程序员开工-抢任务-sop)
- [§6. 卡住怎么办 / 找谁](#-6-卡住怎么办--找谁)
- [§7. 仓库结构 + 文档演进](#-7-仓库结构--文档演进)
- [§8. Bug 跟踪与修复 (`test/`)](#-8-bug-跟踪与修复-test)
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
├── README.md / TASKS.md / MULTI-AGENT-REPO-ARCHITECTURE.md  ← 入口 + 看板 + 协作框架
├── test_task/               ← 自动化测试案例 + 通过时的执行历史
├── docs/                    ← 长期文档（需求/架构/review/运维/交接）
├── test/                    ← bug：round、test_bug、complexity
│   ├── round-*.md / test_bug-*.md / complexity.md
│   ├── old/                 ← 历史验收文档（只读）
│   └── logs/
├── backend/ / frontend/     ← 源代码
└── deploy/                  ← SQL / Caddy / WinSW 配置
```

| 我想… | 第一站 |
|---|---|
| 认项目 / 选角色 | 本文 [§1 角色导航](#-1-角色导航-核心) |
| 抢 RI / MOD 开发任务 | [`TASKS.md`](TASKS.md) + [`docs/requirements/ARCH-DECOMPOSITION.md`](docs/requirements/ARCH-DECOMPOSITION.md) |
| 抢自动化测试任务 AT-* | [`TASKS.md`](TASKS.md) AT 节 + [`test_task/`](test_task/README.md) |
| 跑通案例、记成功 | 在 `test_task/<案例>.md` **§3** 追加执行历史 |
| 看业务需求 | [`docs/requirements/REQUIREMENTS.md`](docs/requirements/REQUIREMENTS.md) |
| 看架构 / 表结构 | [`docs/architecture/`](docs/architecture/README.md) |
| 部署 / 配 key / 运维 | [`docs/operations/`](docs/operations/README.md) |
| 发现 bug、记 round | [`test/round-*.md`](test/round-2026-06-11-v1.1-deploy.md) · **仅缺陷** |
| 自动化案例 FAIL | [`test/test_bug-*.md`](test/test_bug-TEMPLATE.md) → 收入 round |
| 小修闭环（分析/改/审） | 当前 round 的 **§2～§4** |
| 大改 / 搞不定 | [`test/complexity.md`](test/complexity.md) → PM + 架构 |
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

> **本文档核心目的**: 按角色找入口 — 开发见 §1.4 / §5；**bug 四轮次**见 §1.5 + [§8](#-8-bug-跟踪与修复-test)；**代码/架构评审对线**见 [§1.6](#16-评审对线-docsreviews)。

| 角色 | 你是谁 | 你要读 | 你要写 | 你怎么开工 | 边界 |
|---|---|---|---|---|---|
| **[需求开发人员](#11-需求开发人员)** (产品/业务分析师) | 把业务方嘴里"我们希望 XX"翻译成结构化需求 §X.Y | `docs/requirements/REQUIREMENTS.md` (现有) + 业务方访谈 | 在 `docs/requirements/REQUIREMENTS.md` 加 §X.Y 新章节, 含 5 字段 (业务/数据/角色/验收/依赖) | [§1.1](#11-需求开发人员) | ❌ 不写代码, 不写 RI 拆解 |
| **[需求审核人员](#12-需求审核人员)** (PM/业务方代表) | 审新需求 / 拍板模糊点 / 维护术语 | `docs/requirements/REQUIREMENTS.md` + 业务背景资料 | 在 PR 评审里批 +/- 反馈, 维护 `docs/requirements/REQUIREMENTS.md` §13 决策记录 | [§1.2](#12-需求审核人员) | ❌ 不写代码, 不审 RI 拆解 |
| **[架构师](#13-架构师)** (技术 Lead) | 把需求 §X.Y 拆成可落地的 RI (Requirement Item) | `docs/requirements/REQUIREMENTS.md` + 现有 `docs/architecture/ARCHITECTURE-v2.md` + `docs/architecture/DB-SCHEMA-v2.md` | 在 `docs/requirements/ARCH-DECOMPOSITION.md` 加 RI-N: 业务/影响表/角色/验收/依赖/估算 | [§1.3](#13-架构师) | ❌ 不直接写代码 (除非示范) |
| **[程序员](#14-程序员)** (接手 AI / 后端 / 前端 / DBA) | 按 RI 抢任务 + 写代码 + 跑测试 + 提 PR | 必读 [§4.1](#41-程序员必读基线包) + 抢到的 RI 节 | 代码 + 单元测试 + 1 commit / 任务, push 到 main | [§5 抢任务 SOP](#-5-程序员开工-抢任务-sop) | ❌ 不擅自改需求, 不擅自拆别人的 RI |
| **[测试 Agent](#19-测试-agenttest_task)** | 抢 **AT-*** 自动化测试任务 | [`TASKS.md`](TASKS.md) AT 节 + [`test_task/`](test_task/README.md) | 执行案例；PASS 写 §3 历史；FAIL 建 `test_bug` | [§9](#-9-自动化测试任务-test_task) | ❌ FAIL 时不擅自改业务代码 |
| **[Recorder Agent](#15-recorder-agent)** | 发现 bug 并记入 round | 当前 `test/round-*.md` | **§1** 现象、复现、ID、**来源** | 名字 + 时间 + 摘要 | [§8.2](#82-四轮次-agent-工作流) | ❌ 不写根因、不改代码；**无 bug 不记** |
| **[Analyst Agent](#15-analyst-agent)** | 根因分析 + 修改建议 | round **§1** 的 `RECORDED` 项 | **§2** 建议；大改写 [`complexity.md`](test/complexity.md) | 留痕 | [§8.2](#82-四轮次-agent-工作流) | ❌ 默认不改代码 |
| **[Fix Agent](#15-fix-agent)** | 按 §2 小修代码 | round **§2** 的 `SMALL_FIX` 项 | **§3** + commit | 留痕 | [§8.2](#82-四轮次-agent-工作流) | ❌ 大改应升级 complexity |
| **[Reviewer Agent](#15-reviewer-agent)** | 评审 §3 改动（**仅** `test/round`） | round **§3** 的 `FIXED` 项 | **§4** 通过/打回 | 留痕 | [§8.2](#82-四轮次-agent-工作流) | ❌ 应打回 Fix，不偷偷改 |
| **[Review Agent](#16-review-agent)** | 代码/架构评审，**新开** review 文件 | [`docs/reviews/`](docs/reviews/README.md) | Round 1 意见 → 跟进 → **宣布 CLOSED** | 留痕 | [§1.6](#16-评审对线-docsreviews) | ❌ 替 Subject 写回复 |
| **[Subject Agent](#16-subject-agent)** | 被评审方 | 对应 `review-*.md` | 文件下方写回复（接受/措施/结果） | 留痕 | [§1.6](#16-评审对线-docsreviews) | ❌ **不得**自行 CLOSED |

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
- ❌ 直接动 `TASKS.md` (那是程序员自己维护的)
- ❌ 不审就放行程序员开工 (PR 流转: 需求审 → 架构审 → 实现)

### 1.4 程序员

**你是谁**: 后端 / 前端 / DBA / 测试 / 接手 AI. 你的**唯一任务** = 抢 RI + 写代码 + 提 PR. **不写需求, 不拆 RI** (那是需求开发/架构师).

**你要读** (按角色细分, 见 [§4.1 必读基线包](#41-程序员必读基线包)):
- **后端 / 接手 AI** — `docs/architecture/AGENT-IMPL-PLAN.md` + `docs/architecture/DB-SCHEMA-v2.md` + `docs/architecture/ARCHITECTURE-v2.md` + 抢到的 RI
- **前端** — `frontend/README.md` + `docs/architecture/AGENT-IMPL-PLAN.md` §I-12 + 抢到的 RI
- **DBA** — `docs/architecture/DB-SCHEMA-v2.md` + 抢到的 RI
- **测试** — [`test_task/`](test_task/README.md) 抢 AT-*；历史清单见 [`test/old/`](test/old/README.md)

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

### 1.5 测试轮次四 Agent（`test/round-*.md`）

> **`test/` 只记 bug** — 交互式 deploy 发现，或 Agent 自主跑用例发现。**测过无缺陷不要开 round 行。**
> **一轮一文件**；§1～§4 各由不同 agent 接手，**每段必填：Agent 名 + 时间 + 摘要**。  
> 全部 bug **CLOSED** 或已升级 [`test/complexity.md`](test/complexity.md) 后，轮次标 **`CLOSED`**。  
> 细节：[§8](#-8-bug-跟踪与修复-test) · [`test/README.md`](test/README.md) · 模板 [`test/round-TEMPLATE.md`](test/round-TEMPLATE.md)

| Agent | 改 round 哪一节 | 做什么 |
|---|---|---|
| **Recorder** | §1 | **只记 bug**（来源 DEPLOY/AUTO）、复现步骤 |
| **Analyst** | §2 | 根因、修改建议；搞不定 → complexity |
| **Fix** | §3 | 小修、commit |
| **Reviewer** | §4 | 审 diff、CLOSED 或打回 |

#### 1.5.1 Recorder Agent

**你要读**：[`test/round-TEMPLATE.md`](test/round-TEMPLATE.md) · [`test_task/README.md`](test_task/README.md) · [`docs/handoff/v1.1-DEPLOY-GUIDE.md`](docs/handoff/v1.1-DEPLOY-GUIDE.md)

**你要写**：§1 Agent 留痕 + Bug 表（`T-MMDD-NN`，状态 `RECORDED`，**来源** `DEPLOY` / `AUTO`）

**Bug 从哪来**：交互式 deploy 联调；或自主跑 [`test_task/`](test_task/README.md) 案例 / `mvn test` / `npm run build` 失败。

**你不能**：写根因方案、改代码；**没有 bug 不要往 round 里加行**

#### 1.5.2 Analyst Agent

**你要读**：round **§1** 全部 `RECORDED` 行

**你要写**：§2 逐条分析；标 `SMALL_FIX` 或 `ESCALATED`；升级项写入 [`test/complexity.md`](test/complexity.md)（`C-MMDD-NN`）

**你不能**：默认直接改代码（除非用户明确授权 hotfix）

#### 1.5.3 Fix Agent

**你要读**：round **§2** 中 `SMALL_FIX` 项

**你要写**：§3 改动 + commit；状态 `FIXED`

**你不能**：擅自做大改；搞不定 → 升级 complexity，等 PM/架构

#### 1.5.4 Reviewer Agent

**你要读**：round **§3** + git diff

**你要写**：§4 `APPROVED`/`REJECTED`；通过则 bug **`CLOSED`**

**你不能**：自己改代码代替评审（应 `REOPEN` 给 Fix）

> **注意**：此 **Reviewer Agent** 只管 `test/round-*.md` §4 的验收 bug 评审，与 [§1.6](#16-评审对线-docsreviews) 的 **Review Agent**（`docs/reviews/`）不是同一套流程。

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

与 **§1.5 Reviewer Agent**（`test/round` §4）分工：round 审 **bug 的小修**；`docs/reviews/` 审 **代码/架构/MOD 交付** 等，可多轮对线。

#### 1.6.1 Review Agent

**你要读**：Subject 的 commit / diff · 相关 `architecture/` · 可选关联 `test/round-*.md`

**你要写**：Round 1 意见表（P0/P1/P2）；跟进 Round；结案时改顶部状态 **`CLOSED`** + 评审结束声明

**你不能**：自己改被审代码（应打回 Subject）

#### 1.6.2 Subject Agent

**你要读**：review 文件最新 Round 的评审意见

**你要写**：对应 Round 回复块 — 总体态度（全部/部分/不接受）+ 逐项表（措施、commit、结果）

**你不能**：改 Review Agent 上文；**不能**自行宣布评审结束

### 1.9 测试 Agent（`test_task/` + TASKS **AT-***）

> **抢任务、跑案例、记结果** — 与 §5 程序员抢 RI 同一套占用规则（TASKS.md push 占坑）。

**你要读**：[`test_task/README.md`](test_task/README.md) · [`TASKS.md`](TASKS.md) **AT-*** 节 · 对应案例文件

**你要写**：
- **PASS**：案例文件 **§3 执行历史** 追加（Agent、时间、基线、**已成功**）+ TASKS 标 `已完成`
- **FAIL**：[`test/test_bug-TEMPLATE.md`](test/test_bug-TEMPLATE.md) → `test_bug-*.md`；案例 §3 记 FAIL；**不要**自己修 bug

**你不能**：FAIL 时擅自改业务代码（交给 round §2～§4）；通过结果写进 `test/` round（那是 bug 专用）

详情：[§9](#-9-自动化测试任务-test_task)

---

## 📂 2. 文档与文件夹导航

> **2026-06-11 整理原则**: 长期文档不进根目录；**需求 / 架构 / 评审 / 运维 / 测试** 各归其位。
> 详细逻辑与子目录文件列表 → **[`docs/README.md`](docs/README.md)**

### 2.1 根目录 Markdown

| 文件 | 为何留根 | 链接 |
|---|---|---|
| **`README.md`** | 项目入口，角色导航 | 本文 |
| **`TASKS.md`** | 开发 + AT 任务占用看板 | [TASKS.md](TASKS.md) |
| **`MULTI-AGENT-REPO-ARCHITECTURE.md`** | 多 Agent 协作框架（可套用） | [协作架构](MULTI-AGENT-REPO-ARCHITECTURE.md) |

其它 markdown **不应**出现在根目录（历史文件已迁入 `docs/` / `test/`）。

### 2.2 四大工作区

| 区域 | 路径 | 放什么 |
|---|---|---|
| **长期文档** | [`docs/`](docs/README.md) | 需求、架构、review、运维、交接 |
| **自动化测试案例** | [`test_task/`](test_task/README.md) | 案例步骤 + **PASS** 执行历史 |
| **Bug 跟踪** | [`test/`](test/README.md) | `round-*.md`、`test_bug-*.md`、`complexity.md` |
| **任务占用** | [`TASKS.md`](TASKS.md) | MOD/RI **开发** + **AT-*** 测试任务占用 |

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
| 根 `VERIFICATION-REPORT.md` | `test/old/` |
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

### 4.1 程序员必读基线包

**5 分钟扫**, 30 分钟精读, 然后看 [§5 抢任务 SOP](#-5-程序员开工-抢任务-sop) 开工.

| 序 | 文件 | 行数 | 必读理由 |
|---|---|---|---|
| ① | `TASKS.md` (仓库根) | ~300 | **任务分块清单** — 找能抢的 RI, 看 [§5](#-5-程序员开工-抢任务-sop) |
| ② | `docs/requirements/ARCH-DECOMPOSITION.md` | ~24KB | **RI-1~45 拆解底稿** — 找要抢的 RI 详细描述 |
| ③ | `docs/architecture/AGENT-IMPL-PLAN.md` | 252 | Plan I 总览 (Spring AI 1.1 + 5 步 ReAct 循环) |
| ④ | `docs/architecture/AGENT-FRAMEWORK-DECISION.md` | 885 | 决策:Spring AI 1.1 + **不引** spring-ai-alibaba (踩坑预警 §1.2.1.1 第 6 点) |
| ⑤ | `docs/reviews/LESSONS-LEARNED.md` | 19KB | 15+ 踩坑, **避免重蹈覆辙** |

**按技术栈**额外读:
- **后端** — `docs/architecture/ARCHITECTURE-v2.md` (685) + `docs/architecture/DB-SCHEMA-v2.md` (1060)
- **前端** — `frontend/README.md` + `docs/architecture/AGENT-IMPL-PLAN.md` §I-12
- **DBA** — `docs/architecture/DB-SCHEMA-v2.md` (1060) + `docs/operations/ENVIRONMENT-DEPENDENCIES.md` (330)
- **测试** — [`test_task/`](test_task/README.md) + [`test/old/ACCEPTANCE-GUIDE.md`](test/old/ACCEPTANCE-GUIDE.md)（历史参考）

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
| ① | [§8](#-8-bug-跟踪与修复-test) + [`test/README.md`](test/README.md) | 四 Agent 分工 + 留痕规则 |
| ② | [`test/round-TEMPLATE.md`](test/round-TEMPLATE.md) | 新开一轮复制 |
| ③ | 当前 [`test/round-*.md`](test/round-2026-06-11-v1.1-deploy.md) | 只改你负责的那一节 |
| ④ | [`test/complexity.md`](test/complexity.md) | 升级 / PM 架构拍板 |
| ⑤ | [`docs/operations/deployment_log.md`](docs/operations/deployment_log.md) | 部署操作时间线 |

### 4.6 通用参考 (按需查)

| 文件 | 行数 | 何时读 |
|---|---|---|
| `docs/operations/ENVIRONMENT-DEPENDENCIES.md` | 330 | 部署/排错 |
| `docs/operations/TEAM-ARCHIVE.md` | 12KB | 沙箱 SSH / 环境 |
| `docs/operations/DEPLOYMENT-LOG.md` | 13KB | 部署历史 (出问题翻这里) |
| `docs/operations/GLM-KEY-SETUP.md` | 5KB | 配 GLM API key |
| `docs/operations/DEV-STANDARDS.md` | 466 | 开发标准 (提交前看 §7.2) |
| `docs/architecture/ARCH-REUSE-AUDIT.md` | 12KB | 现有可复用模块审计 |
| `test/old/M1-README.md` / `test/old/M1-TEST-TASKS.md` | - | M1 阶段档案 CRUD + 测试 (历史) |
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

**基线不对** → 找 [§6 卡住怎么办](#-6-卡住怎么办--找谁). **编译挂** → 看 `docs/reviews/LESSONS-LEARNED.md` 15+ 坑.

### 5.2 Step 1: 找可抢任务 (2 分钟)

```bash
# 看任务表
cat TASKS.md | less
```

**找满足 3 条件的任务**（**RI/MOD/T-*** 或 **AT-***）:
1. `可并行: ✅` (或非依赖项, 你能自己干)
2. `状态: 未开发` (没人占)
3. 跟你的角色匹配 (开发 / 自动化测试)

- **开发**：见 `ARCH-DECOMPOSITION.md` 对应 RI  
- **自动化测试**：见 [`test_task/README.md`](test_task/README.md) + TASKS **AT-*** 节 → [§9](#-9-自动化测试任务-test_task)

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
├── TASKS.md               ← 开发任务 + AT 占用
├── MULTI-AGENT-REPO-ARCHITECTURE.md  ← 多 Agent 协作框架
│
├── test_task/             ← 自动化测试案例 + PASS 执行历史
│   └── README.md
│
├── docs/                  ← 长期文档（docs 根仅 docs/README.md）
│   ├── README.md          ← 文档总索引 + 按场景查找表
│   ├── requirements/      ← 做什么：需求、RI、访谈
│   ├── architecture/      ← 怎么建：架构、schema、Agent
│   ├── reviews/           ← 评审对线：review-*.md、LESSONS-LEARNED
│   ├── operations/        ← 怎么跑：部署、运维、规范
│   └── handoff/           ← 怎么交：版本交付指南
│
├── test/                  ← bug：round、test_bug、complexity
│   ├── README.md
│   ├── round-*.md / test_bug-*.md / complexity.md
│   ├── old/               ← 历史 ACCEPTANCE-GUIDE、M1/V2、VERIFICATION
│   └── logs/
│
├── backend/               ← Spring Boot 源码 + src/test/ 测试代码
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
| 2026-06-11 | **文档整理**: 长期文档迁入 `docs/` 子目录; 测试文档迁入 `test/`; `docs/` 根只留 README |

### 7.3 文档维护规则 (Mavis 必守)

> **2026-06-10 Mavis 教训**: 写完磁盘的文档**必须立刻 commit** 或放到 git-tracked 路径, 避免 working copy 误删时无 recovery 路径.

- 任何 `docs/` 下新增/修改文档 → 立即 `git add + commit + push`
- 任何 `TASKS.md` 状态变更 → 立即 `git add + commit + push`
- 任何 `README.md` 重构 → 立即 `git add + commit + push`
- 业务方 PR → 立即审, 立即 merge (审过的)

---

## 🧪 8. Bug 跟踪与修复 (`test/`)

> **`test/` 只记 bug。** 发现途径：**(1) 交互式 deploy 联调** **(2) Agent 自主跑测试用例**。测过无缺陷、纯部署步骤 → 不进 `round-*.md`。
>
> **一轮一文件** `round-YYYY-MM-DD-*.md`：**§1 记 bug → §2 分析 → §3 改代码 → §4 评审**，各由不同 Agent 接手，**每段留痕（名字 + 时间 + 摘要）**。  
> **小修**在 round 内闭环；**大改 / 搞不定** → [`test/complexity.md`](test/complexity.md) 交 PM + 架构。  
> **细节以 [`test/README.md`](test/README.md) 为准**。

### 8.1 四轮次 Agent 工作流

```text
Recorder (§1)  →  Analyst (§2)  →  Fix (§3)  →  Reviewer (§4)  →  §5 CLOSED
                      ↘ ESCALATED → complexity.md → PM/架构
```

| § | Agent | 做什么 | 留痕 |
|---|---|---|---|
| **§1** | Recorder | **只记 bug**（来源、复现、ID） | Agent + 时间 + 摘要 |
| **§2** | Analyst | 根因、修改建议；大改标 ESCALATED | 同上 |
| **§3** | Fix | 按建议小修、commit | 同上 |
| **§4** | Reviewer | 审 diff、CLOSED 或打回 | 同上 |

**轮次 `CLOSED` 条件**：§1～§4 留痕齐全；每条 bug 为 **`CLOSED`** 或已在 **`complexity.md`** 登记。

### 8.2 目录与文件

| 路径 | 用途 |
|---|---|
| [`test/README.md`](test/README.md) | 工作流、状态机、Agent 一句话指引 |
| [`test/round-TEMPLATE.md`](test/round-TEMPLATE.md) | **新开一轮**时复制 |
| [`test/round-*.md`](test/round-2026-06-11-v1.1-deploy.md) | 当轮主文件（§1～§5） |
| [`test/complexity.md`](test/complexity.md) | 当轮解决不了、需拍板 |
| [`docs/operations/deployment_log.md`](docs/operations/deployment_log.md) | 部署**操作**时间线（不是 bug 清单） |

**Bug ID**：`T-MMDD-NN` · **Complexity ID**：`C-MMDD-NN` · **Commit**：`fix(<scope>,T-0611-XX): …`

### 8.3 各 Agent 只改哪一节

| 你是… | 打开 | 只编辑 |
|---|---|---|
| Recorder | 当前 `round-*.md` | **§1** |
| Analyst | 同上 | **§2**（升级写 `complexity.md`） |
| Fix | 同上 | **§3** + 代码 |
| Reviewer | 同上 + git diff | **§4** |
| PM / 架构 | `complexity.md` | 决策列 |

### 8.4 当前轮次

| 轮次 | 文件 | 轮次状态 |
|---|---|---|
| v1.1 / 0611 | [`round-2026-06-11-v1.1-deploy.md`](test/round-2026-06-11-v1.1-deploy.md) | `IN_PROGRESS`（§2 Analyst / §4 Reviewer 待留痕） |

### 8.5 与 `TASKS.md` 的关系

| 目录 | 何时用 |
|---|---|
| **`TASKS.md`** | RI / MOD **开发** + **AT-*** 自动化测试占用 |
| **`test_task/`** | 测试案例；**PASS** 写 §3 执行历史 |
| **`test/test_bug-*.md`** | 案例 **FAIL** 入口 → 收入 round §1 |
| **`test/round-*.md`** | bug 四轮次闭环（DEPLOY / AUTO） |
| **`test/complexity.md`** | 需 PM/架构拍板后再进 TASKS 或新 RI |

---

## 🤖 9. 自动化测试任务 (`test_task/`)

> **案例在 `test_task/`**（有案例才有文件；模板 [`case-TEMPLATE.md`](test_task/case-TEMPLATE.md)）。**占用**：有案例时在 `TASKS.md` AT 节追加，**当前无 AT 任务**。
> **细节以 [`test_task/README.md`](test_task/README.md) 为准。**

### 9.1 三分工

| 位置 | 内容 |
|---|---|
| **`test_task/*.md`** | 步骤、预期、**通过**时的执行历史 |
| **`TASKS.md` AT-*** | 谁占用 / 谁完工 |
| **`test/test_bug-*.md`** | **失败** bug 单 → 进 `round-*.md` 四轮次 |

### 9.2 工作流

```text
抢 AT-*（TASKS.md push 占坑）
        ↓
执行 test_task/<案例>.md
        ├─ PASS → 案例 §3 追加「已成功」+ TASKS 已完成 + push
        └─ FAIL → test/test_bug-*.md → Recorder 收入 round §1 → 其他 Agent 处理
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
| [`test/test_bug-TEMPLATE.md`](test/test_bug-TEMPLATE.md) | FAIL 时复制 |

---

## 📐 10. 多 Agent 协作架构（可套用）

> **完整说明**（给其他项目复制时用）：[`MULTI-AGENT-REPO-ARCHITECTURE.md`](MULTI-AGENT-REPO-ARCHITECTURE.md)

本仓库用 **文件 + Git 占用** 协调多 Agent，核心分层：

| 层 | 目录 | 职责 |
|---|---|---|
| 入口 | `README.md` | 角色导航、SOP |
| 任务 | `TASKS.md` | 开发 RI/MOD + 测试 **AT-*** 占用 |
| 文档 | `docs/` | 需求 / 架构 / 评审 / 运维 / 交接 |
| 自动化测试 | `test_task/` | 案例 + **PASS** 执行历史 |
| Bug | `test/` | `round` 四轮次、`test_bug` 入口、`complexity` 升级 |
| 评审 | `docs/reviews/` | Review ↔ Subject 对线，**仅 Review Agent CLOSED** |

各层 README 均指向上述架构文档。新项目套用见该文档 **§11 检查清单**。

---

*本文档由 Mavis (沙箱 PM) 维护. 角色导航 [§1](#-1-角色导航-核心)；bug [§8](#-8-bug-跟踪与修复-test)；自动化测试 [§9](#-9-自动化测试任务-test_task)；架构 [§10](#-10-多-agent-协作架构可套用).*

*Mavis 在此待命审完工 + 答疑.*