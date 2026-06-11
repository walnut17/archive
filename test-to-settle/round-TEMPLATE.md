# Bug 跟踪轮次 — Round TEMPLATE

> **复制本文件**为 `round-YYYY-MM-DD-<简述>.md`，删去本说明段，填元信息。
>
> **仅在有 bug 时使用**：交互式 deploy 发现，或 Agent 自主跑用例发现。**测过无缺陷不要开 round。**
>
> **一轮一文件**；文件内走齐四段 agent 工作流，**全部 bug CLOSED 或已升级 `complexity.md`**，轮次才算结束。

---

## 0. 轮次元信息

| 字段 | 内容 |
|---|---|
| **轮次** | vX.X / YYYYMMDD 简述 |
| **环境** | 例 `182.168.1.125` / 本地 / staging |
| **代码基线** | commit / tag |
| **轮次状态** | `IN_PROGRESS` / `CLOSED` |
| **操作时间线** | 见 `docs/operations/deployment_log.md`（如有） |

### 轮次完成条件（全部满足才改 `CLOSED`）

- [ ] §1 每条 bug 已记录且有 ID
- [ ] §2 每条可分析项已有根因 + 修改建议（或已标 `ESCALATED`）
- [ ] §3 每条拟修项已有 commit / 改动说明（或 `ESCALATED` / `WONTFIX`）
- [ ] §4 每条已修项已评审通过（或 `ESCALATED`）
- [ ] §5 结论已写；升级项已写入 [`complexity.md`](complexity.md)

---

## 1. 测试记录（Recorder Agent）

> **职责**：执行/汇总测试，记现象、复现步骤、严重度。**不改代码、不写修复方案。**

### 1.1 Agent 留痕

| 字段 | 内容 |
|---|---|
| **Agent** | *填写你的名字* |
| **时间** | YYYY-MM-DD HH:mm |
| **摘要** | 本轮从哪发现 bug（DEPLOY / AUTO）、涉及哪些模块 |

### 1.2 测试范围（可选，便于追溯）

| # | 项 | 结果 | 备注 |
|---|---|---|---|
| 1 | | PASS / FAIL | FAIL 须在 §1.3 有对应 bug 行 |

### 1.3 Bug 清单

| ID | 来源 | 严重度 | 模块 | 现象 | 复现步骤 | 状态 |
|---|---|---|---|---|---|---|
| **T-MMDD-01** | `DEPLOY` / `AUTO` | P? | | | | `RECORDED` |

**来源**：`DEPLOY` = 交互式部署/联调发现；`AUTO` = Agent 自主跑用例/脚本发现。

**状态（§1）**：`RECORDED` → 交给 §2 分析

---

## 2. 分析与修改建议（Analyst Agent）

> **职责**：根因分析、改哪几个文件、建议怎么改。**默认不改代码**；若判断为大改，标 `ESCALATED` 并写 `complexity.md`。

### 2.1 Agent 留痕

| 字段 | 内容 |
|---|---|
| **Agent** | |
| **时间** | |
| **摘要** | 分析了 N 条，建议修 M 条，升级 K 条 |

### 2.2 逐条分析

#### T-MMDD-01 — <标题>

| 字段 | 内容 |
|---|---|
| **根因** | |
| **建议修改** | 文件 + 改法（尽量可执行） |
| **建议级别** | `SMALL_FIX` / `ESCALATED` |
| **状态** | `ANALYZED` / `ESCALATED` |

*若 `ESCALATED`：在 [`complexity.md`](complexity.md) 追加 **C-MMDD-NN**，此处链过去。*

---

## 3. 代码修改（Fix Agent）

> **职责**：按 §2 建议做小修；commit；在表内写清改动与 commit。**不搞不定还硬改** — 升级 complexity。

### 3.1 Agent 留痕

| 字段 | 内容 |
|---|---|
| **Agent** | |
| **时间** | |
| **摘要** | 修了 N 条，commit … |

### 3.2 修改记录

| Bug ID | 改动摘要 | 涉及文件 | Commit | 状态 |
|---|---|---|---|---|
| T-MMDD-01 | | | `abc1234` | `FIXED` / `SKIPPED` / `ESCALATED` |

**状态（§3）**：`FIXED` → 交给 §4 评审

---

## 4. 修改评审（Reviewer Agent）

> **职责**：对 §3 的 diff 做代码评审：是否修对、有无副作用、是否需回归。**可打回 Fix agent 重做。**

### 4.1 Agent 留痕

| 字段 | 内容 |
|---|---|
| **Agent** | |
| **时间** | |
| **摘要** | 评审 N 条，通过 M 条，打回 K 条 |

### 4.2 评审记录

| Bug ID | 结论 | 意见 | 状态 |
|---|---|---|---|
| T-MMDD-01 | `APPROVED` / `REJECTED` | | `CLOSED` / `REOPEN` |

**状态（§4）**：`CLOSED` = 本条 bug 在本轮结束；`REOPEN` = 回到 §3 由 Fix agent 再改

---

## 5. 轮次结论

| 项 | 结论 |
|---|---|
| **可继续功能验收？** | 🟢 / 🟡 / 🔴 |
| **可正式上线？** | 🟢 / 🟡 / 🔴 |
| **未闭合 bug** | 无 / 列表（含 complexity ID） |
| **下轮回归** | checklist |

---

*模板版本：2026-06-11*
