# UPGRADE Case — `plan-YYYY-MM-DD-<简述>.md`

> **生成 case 的 Agent 必读**：[`CASE-FORMAT.md`](../CASE-FORMAT.md) **「生成 case 的 Agent」**  
> **路由 ID** = 本文件名去掉 `.md`（= TASKS 首列 = §0 路由 ID）

## 开新 case 步骤

1. 复制本模板为 `plan-YYYY-MM-DD-<英文简述>.md`（例 `plan-2026-06-12-export-api.md`）
2. 删本「开新 case」说明块
3. §0 **路由 ID** 填与文件名一致（无 `.md`）
4. §1 范围验收、§2 开发说明
5. [`TASKS.md`](../TASKS.md) 加 UPGRADE 行，**路由 ID 列 = 文件名无后缀**
6. 若自 complexity 升格：round 标「已转 plan-…」、complexity **删行** · push

**禁止**：`UP-MMDD-NN`、路由 ID 与文件名不一致。

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-YYYY-MM-DD-<简述>`（= 本文件名无 `.md`） |
| **类型** | `UPGRADE` |
| **Case 状态** | `DRAFT` → `OPEN` → `CLOSED` |
| **标题** | |
| **需求 / 架构锚点** | `docs/requirements/` · `docs/architecture/` 章节 |

---

## 1. 任务描述（PM / 架构，只写一次，非 agent-block）

- **做**：
- **不做**：
- **验收**（Given/When/Then 或 checklist）：

---

## 2. 开发说明（架构师，非 agent-block；Coder 只读）

| 路径 | 说明 |
|---|---|
| | |

---

## 3. Agent Blocks

> 顺序：`Coder` ↔ `Reviewer` → **审查员 `Reviewer(CLOSED)`** · 格式见 [`CASE-FORMAT.md`](../CASE-FORMAT.md)

---

## 4. 关单检查

- [ ] Reviewer 对全 scope **`APPROVED`**
- [ ] 已有 **`Reviewer` + `verdict: CLOSED`** 块
- [ ] `git mv` → [`done/`](done/README.md) · TASKS **删行**
