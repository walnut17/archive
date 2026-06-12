# 功能升级计划 — `upgrade_to_settle/`

> **Case** = 本目录 `plan-*.md`（1 文件 = 1 case）。**Coder / 审查员入口**：[`TASKS.md`](../TASKS.md) → [`CASE-FORMAT.md`](../CASE-FORMAT.md)。
>
> **来源**：① 产品/架构主动立项；② **`complexity.md` 分析完成后** 升格（出站后 complexity **删行**，与 test round **无关**）。
>
> **导航**：根 [`README.md`](../README.md) · [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md) · 需求 [`docs/requirements/`](../docs/requirements/README.md) · 架构 [`docs/architecture/`](../docs/architecture/README.md)

---

## 1. 目录结构

```text
upgrade_to_settle/
├── README.md                      ← 本文件
├── plan-TEMPLATE.md               ← 复制开新 plan
├── STATUS.md                      ← 当前活跃 plan 索引
├── plan-YYYY-MM-DD-<简述>.md      ← 活跃 plan（§0～§7）
└── done/
    ├── README.md                  ← 已完工 plan 索引
    └── plan-YYYY-MM-DD-*.md       ← CLOSED 后归档
```

| 路径 | 用途 |
|---|---|
| **`plan-*.md`**（根下） | 进行中的升级主文件 |
| [`STATUS.md`](STATUS.md) | 活跃 plan、`IN_PROGRESS` / `DRAFT` |
| [`done/`](done/README.md) | 验收 CLOSED 后的只读归档 |

---

## 2. 什么进 upgrade，什么不进

| 进 `upgrade_to_settle/plan-*.md` | 不进（去别处） |
|---|---|
| 新 Agent 工具、新 API、模块改造 | 运行缺陷 → [`test-to-settle/`](../test-to-settle/README.md) |
| 架构师已写清范围 + 需求/架构追溯 | 纯想法、未拍板 → [`docs/requirements/`](../docs/requirements/README.md) |
| PM 已确认优先级与验收 | 代码评审 thread → [`docs/reviews/`](../docs/reviews/README.md) |
| | 部署操作 → [`docs/operations/deployment_log.md`](../docs/operations/deployment_log.md) |

**Plan ID**：`UP-MMDD-NN`（例 `UP-0611-01`）

---

## 3. 四轮次 Agent（与 test-to-settle 对齐）

```text
§0～§3  需求/架构/PM 定稿（开 plan 时写好，后续少改）
§4      开发说明（Implementer 执行清单）
§5 Implement → §6 Review → §7 验收 CLOSED → 移 done/
                  ↘  scope 膨胀 → test-to-settle/complexity.md 或新开 plan
```

| 环节 | Agent | 只改 |
|---|---|---|
| §0～§3 | 需求 / 架构 / PM | 开 plan 时定稿；变更走 plan 内「变更记录」 |
| §4 | Architect | 开发细则（Implementer 不改设计） |
| §5 | Implement（Fix） | 代码 + plan §5 留痕 + §5.2 审查回复 |
| §6 | **代码审查员** | plan §6；通过后 §7 验收 → **移 `done/`** |
| §7 | PM / Operator | 验收勾选 → `CLOSED` |

**留痕必填**：Agent · 时间 · 摘要（与 test-to-settle 相同）

---

## 4. 新开 plan

```bash
cp upgrade_to_settle/plan-TEMPLATE.md upgrade_to_settle/plan-2026-06-12-my-feature.md
```

1. 填 §0 元信息、§1 需求追溯、§2 架构追溯、§3 PM 范围  
2. 写 §4 开发说明（文件清单、接口、验收）  
3. 更新 [`STATUS.md`](STATUS.md)  
4. 同步 [`docs/requirements/`](../docs/requirements/) / [`docs/architecture/`](../docs/architecture/) 锚点段落（若尚无）

---

## 5. 完工归档

全部 §7 验收勾选后：

1. 更新 plan 内 §7 结论  
2. `git mv upgrade_to_settle/plan-YYYY-MM-DD-*.md upgrade_to_settle/done/`  
3. 更新 [`STATUS.md`](STATUS.md) 与 [`done/README.md`](done/README.md) 索引  

---

## 6. 当前活跃 plan

见 [`STATUS.md`](STATUS.md)。

---

*2026-06-11：首 plan — Agent 本地 archive 只读文件工具（`UP-0611-01`）。*
