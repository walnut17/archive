# Bug 跟踪与修复 — `test-to-settle/`

> **`test-to-settle/` 存 DEBUG case**（`round-*.md`）。**Coder / 审查员入口**：[`TASKS.md`](../TASKS.md) → Case 路径 → [`CASE-FORMAT.md`](../CASE-FORMAT.md)。
>
> **Bug 从哪来**（二选一或兼有，记入 round §1）：
> 1. **交互式 deploy** — 上环境、点页面、走验收时**发现**的异常
> 2. **Agent 自主跑用例** — 按 [`test_task/`](../test_task/README.md) 案例、`mvn test` 等**失败**
>
> **导航**：根 [`README.md` §8`](../README.md#-8-bug-跟踪与修复-test) · [`test_task/`](../test_task/README.md) · [`MULTI-AGENT-REPO-ARCHITECTURE.md` §7.5](../MULTI-AGENT-REPO-ARCHITECTURE.md#75-co-test-双人联调guide--operator)

> **Co-test**：Guide 指挥 + Operator 在环境上执行 → 操作 [`deployment_log`](../docs/operations/deployment_log.md)，bug 本目录 round §1（来源 `DEPLOY`）。

---

## 1. 目录结构（2026-06-11 整理后）

```text
test-to-settle/
├── README.md                 ← 本文件
├── round-TEMPLATE.md         ← 新开 bug 轮次（复制）
├── round-YYYY-MM-DD-*.md     ← DEBUG **case** 文件
├── test_bug-TEMPLATE.md      ← 自动化案例 FAIL 入口（复制）
├── test_bug-YYYY-MM-DD-*.md  ← FAIL 实例（收入 round §1）
├── done/                     ← CLOSED 的 round 归档
│   └── README.md
├── complexity.md             ← 大改中转路由
├── logs/                     ← mvn 原始日志（gitignore）
└── old/                      ← 历史验收文档（只读，见 old/README.md）
```

| 路径 | 用途 |
|---|---|
| **`round-*.md`** | **DEBUG case**（1 文件 = 1 case） |
| [`round-TEMPLATE.md`](round-TEMPLATE.md) | 新开 case |
| [`STATUS.md`](STATUS.md) | 辅索引（**主入口 TASKS**） |
| [`complexity.md`](complexity.md) | 大改中转（不进 TASKS） |
| [`test_bug-TEMPLATE.md`](test_bug-TEMPLATE.md) | FAIL 入口 → 收入 case |
| [`done/`](done/README.md) | CLOSED case 归档 |
| [`old/`](old/README.md) | 旧版 ACCEPTANCE-GUIDE、M1/V2、VERIFICATION-REPORT |

---

## 2. 什么进 `test-to-settle/`，什么不进

| 进 `round-*.md` §1 | 不进 round（去别处） |
|---|---|
| 复现步骤明确的 **缺陷** | 纯部署操作 → [`docs/operations/deployment_log.md`](../docs/operations/deployment_log.md) |
| 交互式 deploy 发现的 bug | **小修** → [`TASKS.md`](../TASKS.md) DEBUG 行 + 本目录 round |
| 大改 / 新能力方案 | complexity → [`upgrade_to_settle/`](../upgrade_to_settle/README.md) → TASKS UPGRADE |
| `test_task` / 用例失败 | 代码评审 → [`docs/reviews/`](../docs/reviews/README.md) |
| | **测过且通过** → [`test_task/`](../test_task/README.md) §3 |
| | 历史验收清单 → [`old/ACCEPTANCE-GUIDE.md`](old/ACCEPTANCE-GUIDE.md)（只读） |

**来源**（round §1 Bug 表 **`来源`** 列）：`DEPLOY` | `AUTO`

**来自 `test_task/` 的失败**：`cp test_bug-TEMPLATE.md test_bug-…` → Recorder 收入 round §1。

---

## 3. Case 时间线（`round-*.md`）

> 格式：[`CASE-FORMAT.md`](../CASE-FORMAT.md) · Coder/审查员入口：[`TASKS.md`](../TASKS.md)

```text
§1 任务描述（Bug 表，非 block）
  → Recorder? / Analyst?（Agent Blocks）
  → Coder ↔ Reviewer
  → Closer（审查员，必）
  → done/ + TASKS 删行
                  ↘ Analyst ESCALATED → complexity.md
```

| 环节 | 写什么 |
|---|---|
| §1 | Recorder / Co-test：Bug 表（`T-MMDD-NN`，来源 `DEPLOY`/`AUTO`） |
| Agent Blocks | **Recorder** / **Analyst** / **Coder** / **Reviewer** / **Closer**（a-b 格式） |

**与 `docs/reviews/` 区别**：case Reviewer 审 **TASKS 路由的代码改动**；reviews 审 **架构/交付对线**。

**历史 case** 可能仍有旧 §2～§7 — 新工作**只追加 Agent Blocks**。

---

## 4. 小修 vs complexity vs upgrade

| 类型 | 位置 | TASKS 路由 |
|---|---|---|
| 小修（coder 当轮修） | round §2～§4 | **DEBUG 行** |
| 大改（暂挂路由） | [`complexity.md`](complexity.md) | **无**（出站删行） |
| 大改方案已定 | [`upgrade_to_settle/`](../upgrade_to_settle/README.md) plan | **UPGRADE 行** |

```text
小修 → TASKS DEBUG → round §3 Fix
大改 → complexity 加一行 → 分析 → docs + plan + TASKS UPGRADE → 删 complexity 行
```

---

## 5. 新开一轮（Recorder / Co-test Guide）

> **命名规范**：[`CASE-FORMAT.md`](../CASE-FORMAT.md) **「生成 case 的 Agent」**

**何时新建文件**：当前没有合适 **活跃 round**，或 PM/架构明确开新验收轮。  
**何时不新建**：已有活跃 round（如 `round-2026-06-11-v1.1-deploy`）→ **只追加 §1 行**，不加 TASKS 行。

```bash
cp test-to-settle/round-TEMPLATE.md test-to-settle/round-2026-06-12-v1.1-regression.md
```

| 步骤 | 动作 |
|---|---|
| 1 | 文件名 = `round-YYYY-MM-DD-<英文简述>.md` |
| 2 | §0 **路由 ID** = 文件名无 `.md` |
| 3 | §1 Bug 表（`T-MMDD-NN`） |
| 4 | [`TASKS.md`](../TASKS.md) 加 DEBUG 行，**首列路由 ID = 文件名无后缀** |
| 5 | push |

**Bug ID**：`T-MMDD-NN` · **Complexity ID**：`C-MMDD-NN`（complexity 用，不是 case 路由 ID）

---

## 6. 当前轮次

| 轮次 | 文件 | 状态 |
|---|---|---|
| v1.1 / 0611 部署 | [round-2026-06-11-v1.1-deploy.md](round-2026-06-11-v1.1-deploy.md) | `OPEN`（T-0611-09/18 待修） |

---

## 7. 给 Agent 一句话

| 你是… | 打开… | 只改… |
|---|---|---|
| Recorder | 当前 `round-*.md` | **§1** + 可选 **Recorder** block |
| Analyst | 同上 | **Analyst** block |
| Coder | [`TASKS.md`](../TASKS.md) → case | **Coder** block + 代码 |
| Reviewer | [`TASKS.md`](../TASKS.md) → case + diff | **Reviewer** / **Closer** block |
| PM / 架构 | [`complexity.md`](complexity.md) | 分析 → plan → 删 §2 行 |

---

*2026-06-11：根目录仅保留 round / test_bug / complexity 新标准；历史文档在 [`old/`](old/README.md)。*
