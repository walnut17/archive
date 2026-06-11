# 功能升级 Plan — TEMPLATE

> **复制本文件**为 `plan-YYYY-MM-DD-<简述>.md`，删去本说明段。
>
> **命名**：文件名必须含日期 `YYYY-MM-DD`。
>
> **完工**：§7 全部勾选且 `CLOSED` 后 → `git mv` 至 [`done/`](done/)。

---

## 0. Plan 元信息

| 字段 | 内容 |
|---|---|
| **Plan ID** | `UP-MMDD-NN` |
| **标题** | |
| **状态** | `DRAFT` / `IN_PROGRESS` / `VERIFY` / `CLOSED` |
| **优先级** | P0 / P1 / P2 |
| **目标版本** | v1.2 / hotfix |
| **代码基线** | commit / tag |
| **负责人（PM）** | |
| **架构师** | |
| **需求追溯** | 见 §1 |
| **架构追溯** | 见 §2 |

### 完成条件（全部满足才 `CLOSED` + 移 done/）

- [ ] §4 开发项全部实现或明确 `WONTFIX`
- [ ] §5 Implement 留痕 + commit 列表完整
- [ ] §6 Reviewer 通过
- [ ] §7 验收（含联调 / 单测）通过
- [ ] 需求 / 架构锚点文档已同步（或注明「无正文变更」）

---

## 1. 需求追溯（Analyst）

> **开 plan 时定稿**。每条必须链到 `docs/requirements/` 具体章节。

| 字段 | 内容 |
|---|---|
| **Agent** | |
| **时间** | |
| **摘要** | |

### 1.1 业务背景

（用户 / 联测为什么要做）

### 1.2 需求锚点

| 文档 | 章节 | 要点 |
|---|---|---|
| [`REQUIREMENTS.md`](../docs/requirements/REQUIREMENTS.md) | §x.x | |
| [`AGENT-REQUIREMENTS.md`](../docs/requirements/AGENT-REQUIREMENTS.md) | §x.x | |
| [`ARCH-DECOMPOSITION.md`](../docs/requirements/ARCH-DECOMPOSITION.md) | RI-xx | |

### 1.3 验收标准（产品）

- [ ] …

---

## 2. 架构追溯（Architect）

> **开 plan 时定稿**。

| 字段 | 内容 |
|---|---|
| **Agent** | |
| **时间** | |
| **摘要** | |

### 2.1 架构锚点

| 文档 | 章节 | 设计决策 |
|---|---|---|
| [`02-backend-layer-architecture.md`](../docs/architecture/02-backend-layer-architecture.md) | | |
| 其它 | | |

### 2.2 与现有系统关系

（改哪些包、是否破坏 Agent 双路径、配置项）

---

## 3. PM 范围与决策

| 字段 | 内容 |
|---|---|
| **Agent** | |
| **时间** | |
| **摘要** | |

| 项 | 决策 |
|---|---|
| **做** | |
| **不做** | |
| **风险** | |
| **估时** | BE / FE / 测试 |

---

## 4. 开发说明（Implementer 执行清单）

> **架构师写细**：文件、类、接口、配置、测试、禁止事项。

### 4.1 改动文件清单

| 类型 | 路径 | 说明 |
|---|---|---|
| 新增 | | |
| 修改 | | |

### 4.2 实现要点

（分步骤，可勾选）

- [ ] …

### 4.3 测试

- [ ] 单测：…
- [ ] 集成：…
- [ ] 联测步骤：…

---

## 5. 实现留痕（Implement Agent）

| 字段 | 内容 |
|---|---|
| **Agent** | |
| **时间** | |
| **摘要** | |

| 项 | Commit | 说明 | 状态 |
|---|---|---|---|
| | | | `PENDING` / `FIXED` |

### 5.2 审查反馈回复（Implement Agent，审查员 `REQUEST_CHANGES` 后填写）

| 轮次 | 审查员意见摘要 | 实现回复 / 措施 | Commit | 时间 |
|---|---|---|---|---|
| R1 | | | | |

---

## 6. 评审（代码审查员）

> **入口**：[`CODE-REVIEWER.md`](../CODE-REVIEWER.md) · [`STATUS.md`](STATUS.md)

### 6.1 评审结论

| 字段 | 内容 |
|---|---|
| **Agent** | |
| **时间** | |
| **摘要** | |

| 结论 | 意见 |
|---|---|
| `PENDING` / `APPROVED` / `REQUEST_CHANGES` | |

### 6.2 审查对线（可选）

#### Round 1 — 审查员

- **时间**：
- **意见**：

#### Round 1 — Implement 回复

见 §5.2 表 R1 行。

---

## 7. 验收与归档

### 7.1 验收记录

| # | 项 | 结果 | 时间 |
|---|---|---|---|
| 1 | | | |

### 7.2 结论

| 项 | 内容 |
|---|---|
| **Plan 状态** | |
| **归档路径** | `upgrade_to_settle/done/plan-….md` |

### 7.3 变更记录

| 日期 | 作者 | 变更 |
|---|---|---|

---

*模板 · 见 [`upgrade_to_settle/README.md`](README.md)*
