# Bug 跟踪与修复 — `test/`

> **`test/` 只记 bug。** 没有缺陷、只是「测过了没问题」或「部署步骤」——**不要**写进 `round-*.md`。
>
> **Bug 从哪来**（二选一或兼有，记入 round §1）：
> 1. **交互式 deploy** — 上环境、点页面、走验收时**发现**的异常
> 2. **Agent 自主跑用例** — 按 [`test_task/`](../test_task/README.md) 案例、`mvn test` 等**失败**
>
> **导航**：根 [`README.md` §8`](../README.md#-8-bug-跟踪与修复-test) · [`test_task/`](../test_task/README.md) · [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 1. 目录结构（2026-06-11 整理后）

```text
test/
├── README.md                 ← 本文件
├── round-TEMPLATE.md         ← 新开 bug 轮次（复制）
├── round-YYYY-MM-DD-*.md     ← 当轮 bug 主文件（§1～§5）
├── test_bug-TEMPLATE.md      ← 自动化案例 FAIL 入口（复制）
├── test_bug-YYYY-MM-DD-*.md  ← FAIL 实例（收入 round §1）
├── complexity.md             ← 大改 / PM 拍板升级
├── logs/                     ← mvn 原始日志（gitignore）
└── old/                      ← 历史验收文档（只读，见 old/README.md）
```

| 路径 | 用途 |
|---|---|
| **`round-*.md`** | **有 bug 才记** — 四轮次 Agent 主文件 |
| [`test_bug-TEMPLATE.md`](test_bug-TEMPLATE.md) | 案例 FAIL → 复制为 `test_bug-*.md` |
| [`complexity.md`](complexity.md) | 当轮搞不定、需 PM/架构决策 |
| [`logs/`](logs/README.md) | `mvn-*.log`（gitignore） |
| [`old/`](old/README.md) | 旧版 ACCEPTANCE-GUIDE、M1/V2、VERIFICATION-REPORT |

---

## 2. 什么进 `test/`，什么不进

| 进 `round-*.md` §1 | 不进 round（去别处） |
|---|---|
| 复现步骤明确的 **缺陷** | 纯部署操作 → [`docs/operations/deployment_log.md`](../docs/operations/deployment_log.md) |
| 交互式 deploy 发现的 bug | 功能需求 → [`TASKS.md`](../TASKS.md) |
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

## 4. 小修 vs complexity

| 类型 | 位置 |
|---|---|
| 小修 | round §2～§4 |
| 大改 / 搞不定 | [`complexity.md`](complexity.md)（`C-MMDD-NN`） |

---

## 5. 新开一轮

```bash
cp test/round-TEMPLATE.md test/round-2026-06-12-v1.1-regression.md
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
| PM / 架构 | `complexity.md` | 决策列 |

---

*2026-06-11：根目录仅保留 round / test_bug / complexity 新标准；历史文档在 [`old/`](old/README.md)。*
