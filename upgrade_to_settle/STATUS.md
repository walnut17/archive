# upgrade_to-settle — 活跃 Plan 索引

> **Coder** → [`TASKS.md`](../TASKS.md) **🎯 UPGRADE 路由**  
> **代码审查员** → [`CODE-REVIEWER.md`](../CODE-REVIEWER.md) · 下方 **「待代码审查」**  
> 完工 plan → [`done/`](done/README.md)

---

## 待代码审查（审查员入口）

| Plan 文件 | Plan ID | Plan 状态 | 审查状态 | 待审项 | 审查员 | 更新 |
|---|---|---|---|---|---|---|
| _(无)_ | — | — | — | — | — | — |

---

## 活跃 plan（全量）

| Plan 文件 | Plan ID | 状态 | 摘要 | 依赖 | 更新 |
|---|---|---|---|---|---|
| [`plan-2026-06-15-analysis-framework-scaffold.md`](plan-2026-06-15-analysis-framework-scaffold.md) | `plan-2026-06-15-analysis-framework-scaffold` | 未开发 | DDL + Worker 验收 + get_project_analysis | — | 2026-06-15 |
| [`plan-2026-06-15-analysis-ownership-cutover.md`](plan-2026-06-15-analysis-ownership-cutover.md) | `plan-2026-06-15-analysis-ownership-cutover` | 未开发 | Java 瘦身 + enqueue + fact/timepoint | scaffold CLOSED | 2026-06-15 |
| [`plan-2026-06-15-proposal-semantics.md`](plan-2026-06-15-proposal-semantics.md) | `plan-2026-06-15-proposal-semantics` | 未开发 | 议案 vs 维护计数语义 | 可并行 | 2026-06-15 |

> plan **CLOSED** 后 `git mv` → `done/`，从本表删除，写入 [`done/README.md`](done/README.md)。

**架构总览**: [`docs/architecture/09-analysis-ownership-python.md`](../docs/architecture/09-analysis-ownership-python.md)
