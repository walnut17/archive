# DEBUG Case 模板 — `round-YYYY-MM-DD-<简述>.md`

> **Case** = 本文件。路由在 [`TASKS.md`](../TASKS.md)（类型 DEBUG）。  
> **留痕格式**：[`CASE-FORMAT.md`](../CASE-FORMAT.md) — 所有 agent 在 **§ Agent Blocks** 追加块。

复制本文件 → 删本说明段 → 填元信息 → 在 TASKS 加一行。

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **Case ID** | `CASE-D-MMDD` 或自定义 |
| **类型** | `DEBUG` |
| **Case 状态** | `OPEN` / `CLOSED` |
| **文件路径** | `test-to-settle/round-YYYY-MM-DD-*.md` |
| **环境 / 基线** | |
| **关联** | `deployment_log` · complexity |

---

## 1. 背景与 Bug 清单（Recorder / 初稿）

| ID | 来源 | 严重度 | 现象 | 状态 |
|---|---|---|---|---|
| **T-MMDD-01** | DEPLOY / AUTO | P? | | `OPEN` |

> 大改不在这里硬修 → complexity；case 内子项状态由 Agent Blocks 与上表同步。

---

## 2. Agent Blocks

> **按时间顺序追加**。格式见 [`CASE-FORMAT.md`](../CASE-FORMAT.md)。

<!-- 示例
----- agent-block begin -----
role: Recorder
agent: ...
time: 2026-06-11 10:00
ref: T-MMDD-01
source: DEPLOY
summary: ...

----- agent-block end -----
-->

---

## 3. 关单（Closer 块 + 移 done/）

- [ ] 清单项均已 CLOSED / ESCALATED / WONTFIX
- [ ] TASKS 对应行已 **删除**
- [ ] `git mv` → [`done/`](done/README.md)

*模板 · 2026-06-11*
