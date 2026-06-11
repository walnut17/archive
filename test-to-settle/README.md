# Bug 跟踪与修复 — `test-to-settle/`

> **`test-to-settle/` 存 DEBUG 类任务的详情**（现象、根因、改法、评审）。**接手 coder 不要只读这里** — 先到根 [`TASKS.md`](../TASKS.md) **🎯 任务路由** 占 `DEBUG` 行，再按路径打开本目录具体文件。
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
├── round-YYYY-MM-DD-*.md     ← 当轮 bug 主文件（§1～§5）
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
| **`round-*.md`** | **有 bug 才记** — 四轮次 Agent 主文件 |
| [`test_bug-TEMPLATE.md`](test_bug-TEMPLATE.md) | 案例 FAIL → 复制为 `test_bug-*.md` |
| [`complexity.md`](complexity.md) | 大改中转路由（出站删行） |
| [`STATUS.md`](STATUS.md) | **索引**：coder 队列 + **审查员待审** |
| [`done/`](done/README.md) | CLOSED 的 `round-*.md` 归档 |
| [`logs/`](logs/README.md) | `mvn-*.log`（gitignore） |
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

## 3. 四轮次 Agent（`round-*.md`）

```text
§1 Recorder → §2 Analyst → §3 Fix → §4 Reviewer → §5 轮次 CLOSED
                  ↘ ESCALATED → complexity.md
```

| 环节 | Agent | 只改 |
|---|---|---|
| §1 | Recorder | 记 bug（无 bug 不建行） |
| §2 | Analyst | 根因 + 建议 |
| §3 | Fix | 小修 + commit |
| §4 | Reviewer | 审 diff → CLOSED / REOPEN |

**留痕必填**：Agent · 时间 · 摘要

**与 `docs/reviews/` 区别**：round §4 审 **运行缺陷小修**；reviews 审 **代码/架构对线**（仅 Review Agent 可 CLOSED）。

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

## 5. 新开一轮

```bash
cp test-to-settle/round-TEMPLATE.md test-to-settle/round-2026-06-12-v1.1-regression.md
```

**Bug ID**：`T-MMDD-NN` · **Complexity ID**：`C-MMDD-NN`

---

## 6. 当前轮次

| 轮次 | 文件 | 状态 |
|---|---|---|
| v1.1 / 0611 部署 | [round-2026-06-11-v1.1-deploy.md](round-2026-06-11-v1.1-deploy.md) | `IN_PROGRESS` |

---

## 7. 给 Agent 一句话

| 你是… | 打开… | 只改… |
|---|---|---|
| Recorder | 当前 `round-*.md` | **§1** |
| Analyst | 同上 | **§2** |
| Fix | 同上 | **§3** + 代码 |
| Reviewer | 同上 + diff | **§4** |
| **代码审查员** | [`STATUS.md`](STATUS.md) + [`CODE-REVIEWER.md`](../CODE-REVIEWER.md) | **§4**；CLOSED → [`done/`](done/README.md) |
| PM / 架构 | [`complexity.md`](complexity.md) | 分析 → docs/plan → 删 §2 行 |

---

*2026-06-11：根目录仅保留 round / test_bug / complexity 新标准；历史文档在 [`old/`](old/README.md)。*
