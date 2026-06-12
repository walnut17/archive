# 多 Agent 协作仓库架构（可套用模板）

> **用途**：说明本仓库如何用**文件 + Git 占用**协调多个 AI Agent / 人类开发者并行工作。  
> **适用**：任意需要「需求 → 开发 → 测试 → 评审 → 部署 → bug 闭环」的中型项目。  
> **本仓库实例**：投委会档案管理系统（`projects-online`）。

**快速入口**：[`README.md`](README.md) · 文档索引 [`docs/README.md`](docs/README.md)

---

## 1. 设计原则

| 原则 | 说明 |
|---|---|
| **文件即协议** | 协作规则写在 Markdown 里，不靠口头或聊天历史 |
| **一处一责** | 每类信息只有一个「真相目录」，避免重复维护 |
| **占坑靠 push** | `TASKS.md` 改状态后 10 秒内 push，谁先 push 谁占 |
| **留痕必填** | 每个 Agent 段落：名字 + 时间 + 摘要 |
| **bug 与通过分离** | 缺陷进 `test-to-settle/`；测试通过进 `test_task/` |
| **评审与 bug 分离** | 代码/架构对线在 `docs/reviews/`；运行缺陷在 `test-to-settle/` |
| **根目录极简** | 根留 `README.md` + `TASKS.md` + `CASE-FORMAT.md` + **本文**（+ 代码目录） |

---

## 2. 仓库顶层地图

```text
<repo-root>/
├── README.md                        ← 项目入口：角色导航、SOP
├── TASKS.md                         ← Coder + 审查员唯一入口（Case 路由）
├── CASE-FORMAT.md                   ← Case 内 Agent Block 格式
├── CODE-REVIEWER.md                 ← 代码审查员 SOP
├── MULTI-AGENT-REPO-ARCHITECTURE.md ← 本文件
│
├── docs/                  ← 长期文档（需求/架构/评审/运维/交接）
├── test_task/             ← 自动化测试案例 + PASS 执行历史
├── test-to-settle/                  ← DEBUG case：round、test_bug、complexity
│   ├── round-*.md / test_bug-*.md / complexity.md / done/
│   ├── old/               ← 历史验收文档（只读）
│   └── logs/
├── upgrade_to_settle/               ← UPGRADE case：plan、done/
│
├── backend/ frontend/     ← 源代码
├── deploy/ config/        ← 部署脚本与配置模板
└── scripts/               ← 诊断脚本（可选）
```

### 2.1 什么放哪里（决策表）

| 我要记录… | 写哪里 | 不要写哪里 |
|---|---|---|
| 业务需求、RI 拆解 | `docs/requirements/` | 根目录（除本文/TASKS）、`test-to-settle/` |
| 架构、表结构 | `docs/architecture/` | `TASKS.md` |
| 代码/MOD 评审、对线 | `docs/reviews/review-*.md` | `test-to-settle/round` |
| 部署**操作**步骤 | `docs/operations/deployment_log.md` | `test-to-settle/round` |
| **Co-test 联调**（Guide 指挥 + Operator 执行） | 操作 → `deployment_log`；bug → `test-to-settle` | 混在 round 里记「拉代码成功」 |
| 自动化测试**案例** | `test_task/*.md` | `test-to-settle/round` |
| 测试**通过**结果 | `test_task/*.md` §执行历史 | `test-to-settle/round` |
| **缺陷（DEBUG 详情）** | `test-to-settle/round-*.md` 或 `test-to-settle/test_bug-*.md` | `test_task/` |
| 大改暂挂（**不进 TASKS**） | `test-to-settle/complexity.md` | round 里硬改、TASKS 误挂行 |
| **升级（UPGRADE 详情）** | `upgrade_to_settle/plan-*.md` | `test-to-settle/round` |
| 谁在做哪个任务 | `TASKS.md` **🎯 任务路由** | 各详情文件里自拟占用 |

---

## 3. 文档层 — `docs/`

按生命周期分层，**`docs/` 根下只留 `README.md`**：

```text
requirements/   → 做什么（需求、RI）
architecture/   → 怎么建（架构、schema）
reviews/        → 怎么审（评审对线、LESSONS-LEARNED）
operations/     → 怎么跑（部署、规范）
handoff/        → 怎么交（版本交付指南）
```

子目录各自有 `README.md` 索引。详见 [`docs/README.md`](docs/README.md)。

---

## 4. 任务层 — `TASKS.md` + **Case**

**`TASKS.md` = Coder 与代码审查员唯一入口**。一行 = 一个 **Case**（一份 `round-*.md` 或 `plan-*.md`）。

| 状态 | Coder | Reviewer |
|---|---|---|
| `未开发` / `开发中` | ✅ | — |
| `待审` / `审阅中` | — | ✅ |
| CLOSED | — | 删行；case 在 `done/` |

Case 内留痕：[`CASE-FORMAT.md`](CASE-FORMAT.md) **Agent Block**。

### 4.2 生成 case 的 Agent（Recorder / PM / 架构师）

**必读**：[`CASE-FORMAT.md`](CASE-FORMAT.md) **「生成 case 的 Agent」**。

```text
文件名 = 路由 ID + ".md"
DEBUG  → test-to-settle/round-YYYY-MM-DD-<简述>.md
UPGRADE → upgrade_to_settle/plan-YYYY-MM-DD-<简述>.md
TASKS 首列 = §0 路由 ID = 文件名（无 .md）
```

**禁止** `CASE-D-*`、`UP-MMDD-NN`。模板：[`round-TEMPLATE.md`](test-to-settle/round-TEMPLATE.md) · [`plan-TEMPLATE.md`](upgrade_to_settle/plan-TEMPLATE.md)。

### 4.1 占用 SOP（通用）

```text
未开发 ──改 TASKS + push──> 占用-Agent ──干活──> 已完成(Agent / 日期)
```

1. 找 `状态: 未开发` 且依赖满足的任务  
2. 改为 `占用-<名字> (<时间>)`  
3. **10 秒内** `git add TASKS.md && commit && push`  
4. 完成后改 `已完成` 并 push（开发任务另含代码 commit）

**严禁**：未 push 占坑、回退他人占用、一个 commit 改多个无关任务。

---

## 5. 自动化测试层 — `test_task/`

| 项 | 规则 |
|---|---|
| **内容** | 步骤、预期、关联 RI/MOD |
| **占用** | 有案例时在 `TASKS.md` 追加 **AT-*** |
| **PASS** | 案例文件 **§3 执行历史** 末尾追加：时间、Agent、环境、基线、**已成功** |
| **FAIL** | 新建 `test-to-settle/test_bug-*.md`，**不在此目录修 bug** |

详见 [`test_task/README.md`](test_task/README.md)。

---

## 6. Bug 层 — `test-to-settle/`

**`test-to-settle/` 只记 bug。** 测过没问题、纯部署记录 → 不进 round。

### 6.1 Bug 来源

| 来源码 | 含义 |
|---|---|
| `DEPLOY` | 交互式部署/联调中发现 |
| `AUTO` | 自主跑用例失败（含 `test_task/` FAIL） |

### 6.2 Case 时间线（`round-*.md`）

> 格式：[`CASE-FORMAT.md`](CASE-FORMAT.md) · 入口：[`TASKS.md`](TASKS.md)

```text
§1 任务描述（Bug 表，非 block）
  → Recorder? / Analyst?（Agent Blocks）
  → Coder ↔ Reviewer
  → Closer（审查员，必）
  → done/ + TASKS 删行
         ↘ Analyst ESCALATED → complexity.md → plan + TASKS UPGRADE
```

**关 case 条件**：各 `T-*` 已 APPROVED / ESCALATED / WONTFIX，且有 **`role: Closer`** 块。

**历史 round** 可能仍含旧 §2～§7 表 — **新留痕只追加 Agent Blocks**。

### 6.3 `test_bug-*.md`（自动化 FAIL 入口）

`test_task` FAIL → 复制 [`test-to-settle/test_bug-TEMPLATE.md`](test-to-settle/test_bug-TEMPLATE.md) → Recorder 收入 round §1（`T-MMDD-NN`）。

详见 [`test-to-settle/README.md`](test-to-settle/README.md)。

**辅索引**（可选）：[`test-to-settle/STATUS.md`](test-to-settle/STATUS.md) — **主入口仍是 TASKS**。

---

## 6.5 功能升级层 — `upgrade_to_settle/`（含 `done/`）

**UPGRADE case** = `plan-*.md`。占用在 **TASKS.md** 一行；详情在 plan 文件。

| 阶段 | 位置 |
|---|---|
| complexity 升格 / 主动立项 | `plan-*.md` + **TASKS UPGRADE 行** |
| 定稿 | plan **§0/§1/§2**（元信息、范围验收、开发说明 — 非 block） |
| 实现与审查 | plan **§3 Agent Blocks**：Coder ↔ Reviewer → **Closer** |
| **CLOSED** | `git mv` → [`upgrade_to_settle/done/`](upgrade_to_settle/done/README.md) · **TASKS 删行** |

**路由 ID**：与 case 文件名一致（无 `.md`）· 必须链需求章节 + 架构章节（见 plan §1～§2）。

```text
§0～§2 定稿 → §3 Agent Blocks（Coder ↔ Reviewer → Closer）→ done/ → TASKS 删行
```

详见 [`upgrade_to_settle/README.md`](upgrade_to_settle/README.md) · [`CASE-FORMAT.md`](CASE-FORMAT.md)。

---

## 6.6 代码审查员 — 与 Coder 共用 `TASKS.md`

```text
TASKS（待审）→ Case 文件 Reviewer 块 → 全过 → Closer → done/ → 删 TASKS 行
```

详见 [`CODE-REVIEWER.md`](CODE-REVIEWER.md) · [`CASE-FORMAT.md`](CASE-FORMAT.md)。

---

## 7. 评审层 — `docs/reviews/`

与 **Case Reviewer**（TASKS 路由的代码审查）**独立**：

```text
Review Agent 新建 review-*.md（OPEN）
    → Subject Agent 下方写回复（接受/措施/结果）
    → Review Agent 跟进 CONTINUE 或 CLOSED
```

**仅 Review Agent 可宣布 `CLOSED`**。Subject 写「改完了」不算结束。

详见 [`docs/reviews/README.md`](docs/reviews/README.md)。

---

## 7.5 Co-test 双人联调（Guide + Operator）

> **场景**：人在 **125 / 浏览器 / 终端**上操作，**Guide Agent** 在对话里**一步一步指挥**；Operator 把结果贴回对话。  
> **与 Case 流程配合**，但不替代 Recorder/Analyst/Coder/Reviewer/Closer。

### 7.5.1 两个角色

| 角色 | 是谁 | 做什么 | 不做什么 |
|---|---|---|---|
| **Co-test Guide Agent** | 对话里的 AI（如本 session） | 拆步骤、给命令/点击路径、根据反馈给下一步；口述 bug 该记哪；**可代写** `deployment_log` / case **§1** 草稿 | 默认**不擅自改业务代码**（除非用户明确 hotfix） |
| **Co-test Operator** | 你（人类） | 在目标环境**执行**步骤；把结果（✅/❌/截图/控制台）**原文反馈**给 Guide | 不要跳过步骤；有 bug 先**记文件**再私下改代码 |

### 7.5.2 记到哪里

| 内容 | 写哪里 | 示例 |
|---|---|---|
| **做了什么**（pull、重启、点了哪、命令输出） | [`docs/operations/deployment_log.md`](docs/operations/deployment_log.md) | 「Step 3: pull 到 dd7caae，jar 重启 OK」 |
| **测过了没问题** | 同上 `deployment_log` 对应步骤旁标 ✅ | 不建 round 行 |
| **缺陷 / 与预期不符** | [`test-to-settle/`](test-to-settle/README.md) | case **§1** Bug 表追加 `T-MMDD-NN`，**来源 `DEPLOY`**；或 `test_bug-*.md` 再收入 case |
| **大改 / 搞不定** | [`test-to-settle/complexity.md`](test-to-settle/complexity.md) | `C-MMDD-NN`（**不进 TASKS**；出 plan 后挂 UPGRADE 行） |

```text
Guide 出 Step N → Operator 执行 → 反馈结果
        ├─ 正常 → Guide/Operator 更新 deployment_log
        └─ bug  → Guide 起草 case §1 行 → Operator 或 Recorder 写入 test-to-settle
                      → 后续 Analyst / Coder / Reviewer / Closer（非 co-test 当场硬修，除非用户授权）
```

### 7.5.3 Co-test 会话留痕

每次联调在 `deployment_log` 开一小节，至少含：

| 字段 | 说明 |
|---|---|
| **日期 / 环境** | 如 125、`main` commit |
| **Guide** | Agent 名 |
| **Operator** | 你的名字 |
| **目标** | 如「VERIFY T-0611-08/12/16」 |
| **步骤表** | Step / 操作 / 结果 / 时间 |

bug 写入 case §1 后，可选同步 [`test-to-settle/STATUS.md`](test-to-settle/STATUS.md)（辅索引；**主入口 TASKS**）。

### 7.5.4 与 DEPLOY 来源的关系

Co-test 里发现的 bug，case §1 **来源一律 `DEPLOY`**（见 §6.1），与 `test_task` FAIL（`AUTO`）区分。

---

## 8. Agent 角色总表

| 角色 | 主要写 | 边界 |
|---|---|---|
| **Co-test Guide Agent** | 对话指挥 + 代拟 `deployment_log` / case §1 | 默认不改代码 |
| **Co-test Operator** | 执行步骤；反馈结果；确认落盘 | 有 bug 先记 `test-to-settle` |
| 需求开发 / 审核 / 架构师 | `docs/requirements/` · 开 plan 时 `plan-*.md` + TASKS | 不写代码（架构师示范除外）；**plan 路由 ID = 文件名** |
| **Coder** | **Coder** block + 代码 + `TASKS.md` | 不擅自改需求；大改 → complexity |
| **测试 Agent** | `test_task/` + `TASKS.md` AT-* | FAIL 时不擅自改业务代码 |
| **Recorder** | `test-to-settle/round` §1 + 开 round 时 TASKS | 只记 bug；**新 round 按 CASE-FORMAT 命名** |
| **Analyst** | **Analyst** block | 默认不改代码 |
| **代码审查员** | **Reviewer** / **Closer** block · [`CODE-REVIEWER.md`](CODE-REVIEWER.md) | CLOSED → **done/** · TASKS 删行 |
| **Review Agent**（reviews） | `docs/reviews/` | 唯一可 CLOSED 评审 |
| **Subject Agent** | reviews 回复块 | 不可自行 CLOSED |

---

## 9. ID 与文件命名约定

| 类型 | 格式 | 示例 |
|---|---|---|
| **TASKS 路由 ID（DEBUG）** | `round-YYYY-MM-DD-<简述>` | `round-2026-06-11-v1.1-deploy` |
| **TASKS 路由 ID（UPGRADE）** | `plan-YYYY-MM-DD-<简述>` | `plan-2026-06-11-archive-local-fs-tools` |
| Bug 子项 | `T-MMDD-NN` | `T-0611-12` |
| Complexity 升级 | `C-MMDD-NN` | `C-0611-01` |
| DEBUG case 文件 | `round-YYYY-MM-DD-<简述>.md` | `round-2026-06-11-v1.1-deploy.md` |
| UPGRADE case 文件 | `plan-YYYY-MM-DD-<简述>.md` | `plan-2026-06-11-archive-local-fs-tools.md` |
| test_bug | `test_bug-YYYY-MM-DD-<简述>.md` | `test_bug-2026-06-12-npe.md` |
| 自动化案例 | `AT-<序号>-<简述>.md` | （按实际案例命名） |
| 评审 | `YYYY-MM-DD-<范围>-review.md` | `2026-06-12-MOD-05-review.md` |

---

## 10. 模板清单（新项目可复制）

| 模板 | 路径 |
|---|---|
| 协作框架 | `MULTI-AGENT-REPO-ARCHITECTURE.md`（根，即本文） |
| Case 格式 | `CASE-FORMAT.md` |
| DEBUG case | `test-to-settle/round-TEMPLATE.md` |
| UPGRADE case | `upgrade_to_settle/plan-TEMPLATE.md` |
| test_bug 入口 | `test-to-settle/test_bug-TEMPLATE.md` |
| 自动化案例 | `test_task/case-TEMPLATE.md` |
| 评审对线 | `docs/reviews/review-TEMPLATE.md` |
| AT 任务条目 | 见 [`test_task/README.md`](test_task/README.md) |

---

## 11. 新项目套用检查清单

### 11.1 目录（最小集）

- [ ] 根 `README.md`（角色导航）
- [ ] 根 `TASKS.md`（Case 路由 + AT 节）
- [ ] 根 `CASE-FORMAT.md` + `CODE-REVIEWER.md`
- [ ] 根 **`MULTI-AGENT-REPO-ARCHITECTURE.md`**（本文）
- [ ] `docs/` 五子目录 + `docs/README.md`
- [ ] `test_task/` + `README.md` + `case-TEMPLATE.md`
- [ ] `test-to-settle/` + `README.md` + `round-TEMPLATE.md` + `test_bug-TEMPLATE.md` + `complexity.md` + `done/`
- [ ] `upgrade_to_settle/` + `README.md` + `plan-TEMPLATE.md` + `done/`
- [ ] `docs/reviews/` + `README.md` + `review-TEMPLATE.md`

### 11.2 规则（写入根 README）

- [ ] TASKS 占坑必须 push
- [ ] test 只记 bug；PASS 写 test_task
- [ ] reviews 仅 Review Agent 可 CLOSED
- [ ] 生成 case 遵守 `round-*` / `plan-*` 路由 ID（见 `CASE-FORMAT.md`）

### 11.3 可选增强

- [ ] `test-to-settle/logs/` + gitignore（mvn 原始日志）
- [ ] `docs/operations/deployment_log.md`（部署操作时间线）
- [ ] `docs/reviews/LESSONS-LEARNED.md`（踩坑汇总）
- [ ] `test-to-settle/old/`（历史验收文档归档）

---

## 12. 端到端数据流（一图）

```text
                    ┌─────────────────────────────────────────┐
                    │           TASKS.md（占用真相）            │
                    │     开发 RI/MOD/T-*  │  测试 AT-*        │
                    └──────────┬──────────────────┬───────────┘
                               │                  │
              开发完成          │                  │ 执行案例
                               ▼                  ▼
                    ┌──────────────┐      ┌──────────────┐
                    │  代码仓库     │      │  test_task/  │
                    │ backend/...  │      │  案例+PASS历史│
                    └──────┬───────┘      └──────┬───────┘
                           │                     │
              部署/联调     │                     │ FAIL
                           ▼                     ▼
                    ┌──────────────┐      ┌──────────────┐
                    │ deployment_  │      │ test_bug →   │
                    │ log（操作）   │      │ case §1 +    │
                    └──────────────┘      │ Agent Blocks │
                           │              └──────┬───────┘
                           │                     │
                           │ 发现 bug            │ Coder ↔ Reviewer → Closer
                           └──────────┬──────────┘
                                      ▼
                              ┌──────────────┐
                              │ test-to-settle/      │
                              │ upgrade_to_settle/   │
                              │ complexity           │
                              └──────────────┘

     代码/MOD 交付 ──→ docs/reviews/（Review ↔ Subject，Review CLOSED）
```

---

## 13. 本仓库文档索引

| 层级 | README / 文档 |
|---|---|
| 根入口 | [`README.md`](README.md) |
| **协作框架** | **本文** |
| 文档总索引 | [`docs/README.md`](docs/README.md) |
| 需求 | [`docs/requirements/README.md`](docs/requirements/README.md) |
| 架构 | [`docs/architecture/README.md`](docs/architecture/README.md) |
| 评审 | [`docs/reviews/README.md`](docs/reviews/README.md) |
| 运维 | [`docs/operations/README.md`](docs/operations/README.md) |
| 交接 | [`docs/handoff/README.md`](docs/handoff/README.md) |
| 自动化测试 | [`test_task/README.md`](test_task/README.md) |
| Bug / Case | [`test-to-settle/README.md`](test-to-settle/README.md) · [`CASE-FORMAT.md`](CASE-FORMAT.md) |
| UPGRADE | [`upgrade_to_settle/README.md`](upgrade_to_settle/README.md) |

---

*版本：2026-06-12 · 维护：流程变更时同步更新本文 + 各层 README。*
