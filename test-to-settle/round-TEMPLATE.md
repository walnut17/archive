# DEBUG Case — `round-YYYY-MM-DD-<简述>.md`

> **生成 case 的 Agent 必读**：[`CASE-FORMAT.md`](../CASE-FORMAT.md) **「生成 case 的 Agent」**  
> **路由 ID** = 本文件名去掉 `.md`（= TASKS 首列 = §0 路由 ID）

## 开新 case 步骤

1. 复制本模板为 `round-YYYY-MM-DD-<英文简述>.md`（例 `round-2026-06-12-login-regression.md`）
2. 删本「开新 case」说明块
3. §0 **路由 ID** 填与文件名一致（无 `.md`）
4. §1 填 Bug 表
5. [`TASKS.md`](../TASKS.md) 加 DEBUG 行，**路由 ID 列 = 文件名无后缀**
6. push

**禁止**：`CASE-D-*`、路由 ID 与文件名不一致。

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `round-YYYY-MM-DD-<简述>`（= 本文件名无 `.md`） |
| **类型** | `DEBUG` |
| **Case 状态** | `OPEN` |
| **环境 / 基线** | |
| **TASKS** | 路由 ID 与上表一致 |

---

## 1. 任务描述（只写一次，非 agent-block）

> 来源：Co-test → `DEPLOY` · Auto-test → `AUTO` · 子项用 `T-MMDD-NN`。

| ID | 来源 | 严重度 | 现象 / 验收 | 子项状态 |
|---|---|---|---|---|
| **T-MMDD-01** | DEPLOY | P? | | `OPEN` |

大改 → [`complexity.md`](complexity.md)，不在此 case 硬修。

---

## 2. Agent Blocks

> **按时间追加**。格式见 [`CASE-FORMAT.md`](../CASE-FORMAT.md) §2。  
> 顺序：`Recorder?` → `Analyst?` → `Coder` ↔ `Reviewer` → **`Closer`（必）**

<!-- 从 Recorder 或 Coder 块开始 -->

---

## 3. 关单检查（Closer 完成后打勾）

- [ ] 各 `T-*` 已 APPROVED / ESCALATED / WONTFIX
- [ ] 已有 **`role: Closer`** 块
- [ ] `Case 状态` = `CLOSED` · `git mv` → [`done/`](done/README.md)
- [ ] [`TASKS.md`](../TASKS.md) **已删除**本 case 行
