# 复杂问题升级 — `test-to-settle/complexity.md`

> **用途**：本轮 `round-*.md` 里**小修小补搞不定**、或**需要大改 / 架构拍板**的问题，升级到此文件，由 **PM + 架构** 决策后再排期。
>
> **不进 complexity**：能在当轮 round 文件内走完「记录 → 分析 → 改代码 → 评审」闭环的 bug — 留在 round 里解决。

**工作流入口**：[`test-to-settle/README.md`](README.md) · 当前轮次见 [`round-*.md`](round-2026-06-11-v1.1-deploy.md)

---

## 1. 何时写入本文件

| 情况 | 写 round | 写 complexity |
|---|---|---|
| 1～3 行 null-safe、路径拼错、缺迁移列 | ✅ 当轮修完 | ❌ |
| 需改需求 / 产品规则 / 多模块重构 | ❌ 或仅记现象 | ✅ |
| 修完仍不确定对不对，要架构定方案 | 记 VERIFY | ✅ 并链 round ID |
| 涉及 RI 新增、表结构大改、Agent 契约重定 | ❌ | ✅ |
| 部署环境 / 流程类，要 PM 改 SOP | 可 WORKAROUND | ✅ 若需正式决策 |

**提交人**在 round 文件对应 bug 行标 `ESCALATED → complexity`，并在下表**追加一行**。

---

## 2. 问题清单

| ID | 来源轮次 | 来源 Bug | 严重度 | 问题摘要 | 为何不能当轮小修 | 提交人 | 提交时间 | PM/架构决策 | 状态 |
|---|---|---|---|---|---|---|---|---|---|
| **C-0611-01** | round-0611 | T-0611-15 | P2 | 离题问题拒答规则 + GLM key 配置 | 产品规则未定；需验收 checklist 与 key 配置 SOP | 阿根廷 | 2026-06-11 | | `PENDING` |
| **C-0611-02** | round-0611 | A-1 | P1 | 生产升级是否强制 backup + 单一 migrate 脚本 | 部署流程 / 文档决策 | 阿根廷 | 2026-06-11 | 写入 DEPLOY-GUIDE？ | `PENDING` |
| **C-0611-03** | round-0611 | A-2 | P2 | `ddl-auto: update` 与手工 migration 并存 | 环境策略需架构定 | 阿根廷 | 2026-06-11 | 生产改 `validate`？ | `PENDING` |
| **C-0611-04** | round-0611 | A-3 | P2 | 验收环境 5173 dev vs build+Caddy | 两阶段 checklist | 阿根廷 | 2026-06-11 | | `PENDING` |
| **C-0611-05** | round-0611 | A-4 | P2 | FIXED-IN-ROUND 是否 cherry-pick 回 I-RI-39 源文件 | 迁移源文件一致性 | 阿根廷 | 2026-06-11 | | `PENDING` |
| **C-0611-06** | round-0611 | A-5 | P1 | Agent `sources` 契约 null vs `[]` vs 检索来源 | 前后端 + MOD-05 验收标准 | 阿根廷 | 2026-06-11 | 小修已 emptyList；是否补检索来源待决 | `PENDING` |
| **C-0611-07** | round-0611 | A-6 | P1 | 全局 errorHandler 是否纳入规范 | 已加 main.ts；是否标准强制 | 阿根廷 | 2026-06-11 | errorHandler 已落地；评审是否足够 | `PENDING` |

### 状态字段

| 状态 | 含义 |
|---|---|
| `PENDING` | 已提交，待 PM/架构阅 |
| `DECIDED` | 已拍板，方案见「决策」列或链接 |
| `SCHEDULED` | 已纳入 TASKS / 下版本 |
| `WONTFIX` | 明确不做 / 延 v2 |
| `DONE` | 按决策实施并验收通过 |

---

## 3. 单条升级模板（复制到 §2 表格上方作草稿）

```markdown
### C-MMDD-NN — <标题>

| 字段 | 内容 |
|---|---|
| **来源** | `round-YYYY-MM-DD-*.md` / **T-MMDD-XX** |
| **提交人** | <Agent 名> |
| **提交时间** | YYYY-MM-DD HH:mm |
| **严重度** | P0 / P1 / P2 |

**现象**：（用户看到了什么）

**已尝试**：（当轮分析/修复 agent 做过什么、为何不够）

**需要拍板**：（架构/PM 要定什么：方案 A/B、是否改需求、是否新 RI）

**建议**：（提交人倾向，可空）
```

---

## 4. 与 `round-*.md` 的联动

1. Analyst 或 Fix agent 判断「当轮搞不定」→ 在 round **§2 分析** 写明原因 → 标 bug `ESCALATED`
2. 在本文件 §2 **追加一行**，ID 格式 **`C-MMDD-NN`**（与 round 的 `T-MMDD-NN` 同日期前缀）
3. round **§5 轮次结论** 注明：「T-XX 已升级 C-YY，本轮不阻塞其余 CLOSED」
4. PM/架构填「决策」后，若需开发 → 在 `TASKS.md` 或 `docs/requirements/` 开 RI，**不要**在 round 里偷偷大改

---

*2026-06-11 起与四轮次 agent 工作流配套使用。*
