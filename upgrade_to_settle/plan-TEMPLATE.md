# UPGRADE Case 模板 — `plan-YYYY-MM-DD-<简述>.md`

> **Case** = 本文件。路由在 [`TASKS.md`](../TASKS.md)（类型 UPGRADE，路由 ID = Plan ID）。  
> **留痕格式**：[`CASE-FORMAT.md`](../CASE-FORMAT.md) — 所有 agent 在 **§ Agent Blocks** 追加块。

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **Case ID / Plan ID** | `UP-MMDD-NN` |
| **类型** | `UPGRADE` |
| **Case 状态** | `DRAFT` / `OPEN` / `CLOSED` |
| **标题** | |
| **需求 / 架构锚点** | `docs/requirements/` · `docs/architecture/` 章节 |

---

## 1. 范围与验收（PM / 架构定稿）

- **做**：
- **不做**：
- **验收**：

---

## 2. 开发说明（架构师，Implement 只读）

| 路径 | 说明 |
|---|---|
| | |

---

## 3. Agent Blocks

> **按时间顺序追加**。Coder = 实现；Reviewer = 审查。

<!-- 示例
----- agent-block begin -----
role: Coder
agent: ...
time: ...
ref: UP-MMDD-NN
commits: abc1234
summary: ...

----- agent-block end -----
-->

---

## 4. 关单

- [ ] Reviewer APPROVED + Closer 块
- [ ] TASKS 行 **删除**
- [ ] `git mv` → [`done/`](done/README.md)

*模板 · 2026-06-11*
