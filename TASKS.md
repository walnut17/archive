# 任务路由 — `TASKS.md`

> **Coder 与代码审查员共用本表** — 唯一入口。占一行 → 打开 **Case 路径** → 在 case 文件里按 [`CASE-FORMAT.md`](CASE-FORMAT.md) 写 **Agent Block**。  
> **Case** = 一份文件（DEBUG：`test-to-settle/round-*.md` · UPGRADE：`upgrade_to_settle/plan-*.md`）。  
> **路由 ID** = case **文件名（无 `.md`）**：DEBUG → `round-*` · UPGRADE → `plan-*`（勿用 `CASE-D-*` / `UP-MMDD-NN`）。  
> **CLOSED 后**：case 移 `done/`，**本表删除该行**（不再路由）。

> **占用**：改状态 + `最后 Agent` + `最后更新` → 10 秒内 push main。

---

## 🎯 活跃 Case 路由

### 状态（只看这一列找活）

| 状态 | Coder | Reviewer |
|---|---|---|
| **`未开发`** | ✅ 占 → `开发中` | — |
| **`开发中`** | ✅ 继续写代码 | — |
| **`待审`** | — | ✅ 占 → `审阅中` |
| **`审阅中`** | 等打回 | ✅ 写 Reviewer 块 |
| *（删行）* | case 已 CLOSED 归档 | 已完工 |

```text
TASKS.md
  ├─ DEBUG case → test-to-settle/round-*.md
  └─ UPGRADE case → upgrade_to_settle/plan-*.md
        ↓ Coder 块 → 待审 → Reviewer 块 → 审查员 Reviewer(CLOSED) → done/ → 删 TASKS 行
```

### 路由表

| 路由 ID | 类型 | Case 路径 | 状态 | 最后 Agent | 最后更新 | 摘要 |
|---|---|---|---|---|---|---|
| _(无活跃 case — 0612 两份 plan 已 CLOSED，见 [`upgrade_to_settle/done/`](upgrade_to_settle/done/README.md))_ | — | — | — | — | — | — |

> **辅索引**（可选）：[`test-to-settle/STATUS.md`](test-to-settle/STATUS.md) · [`upgrade_to_settle/STATUS.md`](upgrade_to_settle/STATUS.md)  
> **不在本表**：complexity 中转 · AT-* 自动化 · 已 CLOSED（见各 `done/`）

### Coder SOP

1. 选 `未开发` 或 `开发中` → 改 **`开发中`** + 填 Agent/时间 → push  
2. 打开 **Case 路径** → 先读 **§1 任务描述** → 追加 **Coder** 块（[`CASE-FORMAT.md`](CASE-FORMAT.md)）  
3. 本项完工 → 状态改 **`待审`** → push  

### Reviewer SOP

见 [`CODE-REVIEWER.md`](CODE-REVIEWER.md)：占 **`待审`** → **`审阅中`** → Reviewer 块 → 全过则 **Reviewer(CLOSED)** + `done/` + **删本行**。

### 何时新增一行

> **命名规范**：[`CASE-FORMAT.md`](CASE-FORMAT.md) **「生成 case 的 Agent」** — 路由 ID = 文件名（无 `.md`）。

| 事件 | 操作 |
|---|---|
| 新 DEBUG case | ① `round-YYYY-MM-DD-<简述>.md` ② §0 路由 ID 同文件名 ③ TASKS 加 **DEBUG** 行，首列 = 路由 ID，状态 `未开发` |
| complexity 升格 | ① `plan-YYYY-MM-DD-<简述>.md` ② §0 路由 ID 同文件名 ③ TASKS 加 **UPGRADE** 行 ④ complexity 删行 |
| 大改暂不做 | 只写 complexity，**不加** TASKS 行 |
| 向已有 round 加 bug | **不**新建 case、**不**加 TASKS 行；只改该 round **§1** |

---

## 🤖 自动化测试（AT-*）

> **当前：无 AT 任务。** 有真实案例时再在本节追加一行。  
> 流程：[`test_task/README.md`](test_task/README.md) · 模板 [`test_task/case-TEMPLATE.md`](test_task/case-TEMPLATE.md)

---

## 📚 历史与已关单（不在本表占 token）

| 内容 | 位置 |
|---|---|
| Plan I / v1.1 MOD / 旧 UPGRADE 占表、冲突 SOP、Mavis 沙箱记录 | [`docs/reviews/archive/tasks-history-routing.md`](docs/reviews/archive/tasks-history-routing.md) |
| 已 CLOSED DEBUG case | [`test-to-settle/done/`](test-to-settle/done/) |
| 已 CLOSED UPGRADE plan | [`upgrade_to_settle/done/`](upgrade_to_settle/done/) |
| 代码/架构评审对线 | [`docs/reviews/`](docs/reviews/) |

*本表只保留 Case 路由与操作流程；历史追溯请点上方链接。*
