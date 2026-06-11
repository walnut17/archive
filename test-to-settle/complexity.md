# 复杂问题升级 — `test-to-settle/complexity.md`

> **用途**：`round-*.md` 里**当轮小修搞不定**、需**大改 / 架构拍板**的问题，**暂挂本文件** — **不进 [`TASKS.md`](../TASKS.md) 路由**，避免 agent 误抢半成品任务。
>
> **出口**：PM/架构分析完成后 → 在 [`upgrade_to_settle/`](../upgrade_to_settle/README.md) 写 **`plan-YYYY-MM-DD-*.md`** → 在 **TASKS.md 新增 UPGRADE 行** → coder 按 plan 开发（**与原先 test round 完全脱钩**）。
>
> **不进 complexity**：能在当轮 round 内走完「记录 → 分析 → 改代码 → 评审」的小修 — 留在 round + TASKS DEBUG 行。

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
|---|---|---|---|---|---|---|---|---|---|---|
| **C-0611-01** | round-0611 | T-0611-15 | P1 | 离题问题拒答规则 + Agent 意图分类 | 产品规则未定；prompt/工具链/验收文案需 PM | Auto | 2026-06-11 | → UP-0611-02 | `SCHEDULED` |
| **C-0611-02** | round-0611 | T-0611-07 | P1 | 生产升级是否强制 backup + 单一 migrate 脚本 | 部署流程 / 发布 SOP 决策 | 阿根廷 | 2026-06-11 | → UP-0611-03 | `SCHEDULED` |
| **C-0611-03** | round-0611 | A-2 | P2 | `ddl-auto: update` 与手工 migration 并存 | 环境策略需架构定 | 阿根廷 | 2026-06-11 | → UP-0611-03，生产改 `validate` | `SCHEDULED` |
| **C-0611-04** | round-0611 | T-0611-06 | P2 | 验收环境 5173 dev vs build+Caddy `:80` | 两阶段 checklist；Caddy 托管 SPA | 阿根廷 | 2026-06-11 | → UP-0611-03 | `SCHEDULED` |
| **C-0611-05** | round-0611 | T-0611-03 | P2 | FIXED-IN-ROUND 是否 cherry-pick 回 I-RI-39 源文件 | 迁移源文件一致性 | 阿根廷 | 2026-06-11 | → UP-0611-03，修 I-RI-39 源文件 | `SCHEDULED` |
| **C-0611-06** | round-0611 | T-0611-12/13 | P1 | Agent `sources` 契约 null vs `[]` vs 检索来源 | 小修已 emptyList；是否补检索来源、MOD-05 验收待决 | 阿根廷 | 2026-06-11 | ✅ 已在 `e592ce5` 修完，`sources=emptyList()` | `DONE` |
| **C-0611-07** | round-0611 | T-0611-17 | P1 | 全局 errorHandler 是否纳入前端规范 | 已加 main.ts；是否标准强制 | 阿根廷 | 2026-06-11 | ✅ 已在 `e592ce5` 修完，`app.config.errorHandler` 已加 | `DONE` |
| **C-0611-08** | round-0611 | T-0611-19 | P2 | 知识库聊天式 UI（滚动消息区 + 底栏输入 + 多轮） | 前端重构 + 接 `/api/qa/turn`；非热修 | Auto | 2026-06-11 | → UP-0611-04，对齐 AGENT-REQUIREMENTS §4.5 | `SCHEDULED` |
| **C-0611-09** | round-0611 | T-0611-05 | P2 | healthcheck 不带 JWT vs 浏览器带 token 路径差异 | 文档 gap 或 healthcheck 增 token 冒烟 | Auto | 2026-06-11 | → UP-0611-03 | `SCHEDULED` |
| **C-0611-10** | round-0611 | T-0611-10 | P2 | mvn 集成测 44 条 vs spec 写 45 | 测试治理：补用例或改文档 | Auto | 2026-06-11 | → UP-0611-05 | `SCHEDULED` |
| **C-0611-11** | round-0611 | T-0611-11 | P2 | H2 集成测不覆盖 FULLTEXT/触发器 | staging MySQL 补测策略 | Auto | 2026-06-11 | → UP-0611-05，延 v2 或 staging 清单 | `SCHEDULED` |

### 状态字段

| 状态 | 含义 |
|---|---|
| `PENDING` | 已提交，待 PM/架构阅 |
| `DECIDED` | 已拍板，方案见「决策」列或链接 |
| `SCHEDULED` | 已纳入 TASKS UPGRADE / 下版本 |
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

## 4. 与 `round` / `TASKS` / `upgrade_to_settle` 的联动

```text
round 发现大改 → bug 标 ESCALATED → complexity §2 追加（不进 TASKS）
       ↓
PM/架构分析 → upgrade_to_settle/plan-*.md 定稿
       ↓
TASKS.md 新增 UPGRADE 行 → coder 占坑 → plan §5 开发
       ↓
原 round 该 bug 可标「已转 UP-xx」或 WONTFIX 当轮 — 不再在 round §3 做大改
```

1. Analyst / Fix 判断「当轮搞不定」→ round **§2** 写明 → bug **`ESCALATED`**
2. 本文件 §2 **追加** `C-MMDD-NN` — **不要**在 TASKS 加路由行
3. round **§5** 注明：「T-XX 已升级 C-YY，本轮不阻塞其余项」
4. 方案就绪 → 架构写 **`upgrade_to_settle/plan-*.md`** → PM 在 **TASKS** 加 **UPGRADE** 行 → 开发走 plan，**与 round §3 无关**

---

*2026-06-11 起与四轮次 agent 工作流配套使用。*
