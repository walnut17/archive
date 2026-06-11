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
| **根目录极简** | 根留 `README.md` + `TASKS.md` + **本文**（+ 代码目录） |

---

## 2. 仓库顶层地图

```text
<repo-root>/
├── README.md                        ← 项目入口：角色导航、SOP
├── TASKS.md                         ← 任务占用真相表（开发 + 自动化测试）
├── MULTI-AGENT-REPO-ARCHITECTURE.md ← 本文件：多 Agent 协作框架（可套用）
│
├── docs/                  ← 长期文档（需求/架构/评审/运维/交接）
├── test_task/             ← 自动化测试案例 + PASS 执行历史
├── test-to-settle/                  ← 仅 bug：round、test_bug、complexity
│   ├── round-*.md / test_bug-*.md / complexity.md
│   ├── old/               ← 历史验收文档（只读）
│   └── logs/
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
| 自动化测试**案例** | `test_task/*.md` | `test-to-settle/round` |
| 测试**通过**结果 | `test_task/*.md` §执行历史 | `test-to-settle/round` |
| **缺陷** | `test-to-settle/round-*.md` 或 `test-to-settle/test_bug-*.md` | `test_task/` |
| 大改需 PM 拍板 | `test-to-settle/complexity.md` | round 里硬改 |
| 谁在做哪个任务 | `TASKS.md` | 各案例文件里自拟状态 |

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

## 4. 任务层 — `TASKS.md`

**两种任务类型，共用同一占用机制：**

| 类型 | ID 前缀 | 案例/规格位置 | 完工标志 |
|---|---|---|---|
| **开发** | RI / MOD / T-* | `docs/requirements/`、`docs/architecture/` | 代码 commit + 本节 `已完成` |
| **自动化测试** | **AT-*** | `test_task/<案例>.md`（有案例时才建） | 案例 §3 有 PASS + 本节 `已完成` |

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

### 6.2 四轮次 Agent（`round-*.md`）

```text
§1 Recorder（记 bug）
    → §2 Analyst（根因+建议）
    → §3 Fix（小修+commit）
    → §4 Reviewer（审 diff → CLOSED / REOPEN）
         ↘ 大改 → complexity.md → PM/架构
```

**轮次 CLOSED 条件**：§1 每条 bug 都 `CLOSED`（修完+审完）或 `ESCALATED → complexity`（已转交）；两条都没完成的 = 还不能关。

### 6.3 `test_bug-*.md`（自动化 FAIL 入口）

`test_task` FAIL → 复制 [`test-to-settle/test_bug-TEMPLATE.md`](test-to-settle/test_bug-TEMPLATE.md) → Recorder 收入 round §1（`T-MMDD-NN`）。

详见 [`test-to-settle/README.md`](test-to-settle/README.md)。

**上手索引**：bug 状态变 → 同步 [`test-to-settle/STATUS.md`](test-to-settle/STATUS.md)，方便接手 agent 快速找到自己要处理的文件。

---

## 7. 评审层 — `docs/reviews/`

与 bug 四轮次**独立**：

```text
Review Agent 新建 review-*.md（OPEN）
    → Subject Agent 下方写回复（接受/措施/结果）
    → Review Agent 跟进 CONTINUE 或 CLOSED
```

**仅 Review Agent 可宣布 `CLOSED`**。Subject 写「改完了」不算结束。

详见 [`docs/reviews/README.md`](docs/reviews/README.md)。

---

## 8. Agent 角色总表

| 角色 | 主要写 | 边界 |
|---|---|---|
| 需求开发 / 审核 / 架构师 | `docs/requirements/` | 不写代码（架构师示范除外） |
| 程序员 | 代码 + `TASKS.md` 开发任务 | 不擅自改需求 |
| **测试 Agent** | `test_task/` + `TASKS.md` AT-* | FAIL 时不擅自改业务代码 |
| **Recorder** | `test-to-settle/round` §1 | 只记 bug |
| **Analyst** | `test-to-settle/round` §2 | 默认不改代码 |
| **Fix** | `test-to-settle/round` §3 + 代码 | 大改 → complexity |
| **Reviewer**（round） | `test-to-settle/round` §4 | 不偷偷改代码 |
| **Review Agent**（reviews） | `docs/reviews/` | 唯一可 CLOSED 评审 |
| **Subject Agent** | reviews 回复块 | 不可自行 CLOSED |

---

## 9. ID 与文件命名约定

| 类型 | 格式 | 示例 |
|---|---|---|
| Bug | `T-MMDD-NN` | `T-0611-12` |
| Complexity 升级 | `C-MMDD-NN` | `C-0611-01` |
| 测试轮次 | `round-YYYY-MM-DD-<简述>.md` | `round-2026-06-11-v1.1-deploy.md` |
| test_bug | `test_bug-YYYY-MM-DD-<简述>.md` | `test_bug-2026-06-12-npe.md` |
| 自动化案例 | `AT-<序号>-<简述>.md` | （按实际案例命名） |
| 评审 | `YYYY-MM-DD-<范围>-review.md` | `2026-06-12-MOD-05-review.md` |

---

## 10. 模板清单（新项目可复制）

| 模板 | 路径 |
|---|---|
| 协作框架 | `MULTI-AGENT-REPO-ARCHITECTURE.md`（根，即本文） |
| Bug 轮次 | `test-to-settle/round-TEMPLATE.md` |
| test_bug 入口 | `test-to-settle/test_bug-TEMPLATE.md` |
| 自动化案例 | `test_task/case-TEMPLATE.md` |
| 评审对线 | `docs/reviews/review-TEMPLATE.md` |
| AT 任务条目 | 见 [`test_task/README.md`](test_task/README.md) |

---

## 11. 新项目套用检查清单

### 11.1 目录（最小集）

- [ ] 根 `README.md`（角色导航）
- [ ] 根 `TASKS.md`（开发 + AT 节，无任务时不编例）
- [ ] 根 **`MULTI-AGENT-REPO-ARCHITECTURE.md`**（本文）
- [ ] `docs/` 五子目录 + `docs/README.md`
- [ ] `test_task/` + `README.md` + `case-TEMPLATE.md`
- [ ] `test-to-settle/` + `README.md` + `round-TEMPLATE.md` + `test_bug-TEMPLATE.md` + `complexity.md`
- [ ] `docs/reviews/` + `README.md` + `review-TEMPLATE.md`

### 11.2 规则（写入根 README）

- [ ] TASKS 占坑必须 push
- [ ] test 只记 bug；PASS 写 test_task
- [ ] reviews 仅 Review Agent 可 CLOSED
- [ ] 每 Agent 段留痕：名字、时间、摘要

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
                    │ log（操作）   │      │ round §1～§4 │
                    └──────────────┘      └──────┬───────┘
                           │                     │
                           │ 发现 bug            │ 小修闭环
                           └──────────┬──────────┘
                                      ▼
                              ┌──────────────┐
                              │ test-to-settle/round   │
                              │ complexity   │
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
| Bug | [`test-to-settle/README.md`](test-to-settle/README.md) |

---

*版本：2026-06-11 · 维护：流程变更时同步更新本文 + 各层 README。*
