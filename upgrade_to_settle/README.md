# 功能升级计划 — `upgrade_to_settle/`

> **Case** = 本目录 `plan-*.md`（1 文件 = 1 case）。**Coder / 审查员入口**：[`TASKS.md`](../TASKS.md) → [`CASE-FORMAT.md`](../CASE-FORMAT.md)。
>
> **来源**：① 产品/架构主动立项；② **`complexity.md` 分析完成后** 升格（出站后 complexity **删行**，与 DEBUG case **无关**）。
>
> **导航**：根 [`README.md`](../README.md) · [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md) · 需求 [`docs/requirements/`](../docs/requirements/README.md) · 架构 [`docs/architecture/`](../docs/architecture/README.md)

---

## 1. 目录结构

```text
upgrade_to_settle/
├── README.md                      ← 本文件
├── plan-TEMPLATE.md               ← 复制开新 case
├── STATUS.md                      ← 辅索引（主入口 TASKS）
├── plan-YYYY-MM-DD-<简述>.md      ← 活跃 case（§0/§1/§2 + Agent Blocks）
└── done/
    ├── README.md                  ← 已 CLOSED case 索引
    └── plan-YYYY-MM-DD-*.md       ← 归档
```

| 路径 | 用途 |
|---|---|
| **`plan-*.md`**（根下） | 进行中的 UPGRADE case |
| [`STATUS.md`](STATUS.md) | 辅索引（**主入口 [`TASKS.md`](../TASKS.md)**） |
| [`done/`](done/README.md) | 审查员关单后只读归档 |

---

## 2. 什么进 upgrade，什么不进

| 进 `upgrade_to_settle/plan-*.md` | 不进（去别处） |
|---|---|
| 新 Agent 工具、新 API、模块改造 | 运行缺陷 → [`test-to-settle/`](../test-to-settle/README.md) |
| 架构师已写清范围 + 需求/架构追溯 | 纯想法、未拍板 → [`docs/requirements/`](../docs/requirements/README.md) |
| PM 已确认优先级与验收 | 代码评审 thread → [`docs/reviews/`](../docs/reviews/README.md) |
| | 部署操作 → [`docs/operations/deployment_log.md`](../docs/operations/deployment_log.md) |

**路由 ID**：`plan-YYYY-MM-DD-<简述>`（= 文件名无 `.md`，例 `plan-2026-06-11-archive-local-fs-tools`）= TASKS 行首列

---

## 3. Case 时间线

> 格式：[`CASE-FORMAT.md`](../CASE-FORMAT.md)

```text
§0  元信息
§1  任务描述（PM/架构，非 block）
§2  开发说明（架构，非 block；Coder 只读）
§3  Agent Blocks：Coder ↔ Reviewer → Reviewer(CLOSED) → done/ → TASKS 删行
```

| 环节 | 写什么 |
|---|---|
| §0～§2 | 开 case 时定稿；变更走 plan 内「变更记录」 |
| **Coder** block | 代码 + `commits` |
| **Reviewer** block | 审 diff；**verdict: CLOSED** 关 case |

**历史 plan** 可能仍含旧 §3～§7 — **新留痕只追加 Agent Blocks**。

---

## 4. 新开 case（PM / 架构师）

> **命名规范**：[`CASE-FORMAT.md`](../CASE-FORMAT.md) **「生成 case 的 Agent」**

```bash
cp upgrade_to_settle/plan-TEMPLATE.md upgrade_to_settle/plan-2026-06-12-my-feature.md
```

| 步骤 | 动作 |
|---|---|
| 1 | 文件名 = `plan-YYYY-MM-DD-<英文简述>.md` |
| 2 | §0 **路由 ID** = 文件名无 `.md`（= TASKS 首列，禁止 `UP-MMDD-NN`） |
| 3 | §1 范围验收、§2 开发说明 |
| 4 | [`TASKS.md`](../TASKS.md) 加 **UPGRADE** 行，路由 ID 同文件名 |
| 5 | complexity 升格时：round 标「已转 plan-…」、complexity **删行** |
| 6 | 可选 [`STATUS.md`](STATUS.md) · 同步 docs 锚点 · push |

---

## 5. 完工归档

审查员 **Reviewer(CLOSED)** 后：

1. 元信息 `Case 状态` → `CLOSED`  
2. `git mv upgrade_to_settle/plan-YYYY-MM-DD-*.md upgrade_to_settle/done/`  
3. **TASKS 删除该行** · 可选更新 [`done/README.md`](done/README.md)

详见 [`CODE-REVIEWER.md`](../CODE-REVIEWER.md)。

---

## 6. 当前活跃 plan

见 [`TASKS.md`](../TASKS.md) **🎯 活跃 Case 路由** · 辅索引 [`STATUS.md`](STATUS.md)。

---

*2026-06-12：Case + Agent Block 流程与 DEBUG 对齐。*
