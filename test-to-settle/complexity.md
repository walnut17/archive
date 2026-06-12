# 复杂问题升级 — `test-to-settle/complexity.md`

> **本文件 = 大改中转路由表**（与 [`TASKS.md`](../TASKS.md) 同级：只存 **ID + 摘要 + 链 round**，**不存**完整方案全文）。  
> **不进 TASKS**：避免 coder 误抢尚未定稿的工作。  
> **详情最终归宿**：[`docs/requirements/`](../docs/requirements/README.md) · [`docs/architecture/`](../docs/architecture/README.md) · [`upgrade_to_settle/`](../upgrade_to_settle/README.md) plan。

---

## 接 agent：你读到本文件该干什么

**先认角色，再动手：**

| 你是谁 | 你要做什么 |
|---|---|
| **架构师 / PM / 分析 Agent** | 1. 扫 §2 **`PENDING`** 行，挑一条 `C-MMDD-NN`<br>2. 读链上的 round / `T-*` 现象<br>3. **拆解分析** → 更新需求/架构文档<br>4. 需要开发时：按 [`CASE-FORMAT.md`](../CASE-FORMAT.md) 建 `plan-YYYY-MM-DD-<简述>.md`（**路由 ID = 文件名无 .md**）→ [`TASKS.md`](../TASKS.md) **UPGRADE** 行<br>5. round bug 标「已转 `plan-…`」<br>6. **删 complexity §2 对应行** |
| **Coder** | ❌ **不要**从这里直接写代码。去 **TASKS** 占 **DEBUG**（小修）或 **UPGRADE**（plan 已定稿） |
| **Analyst Agent** | 判断大改 → round 标 `ESCALATED` → **只追加** §2 一行路由（勿写长文） |

**出站（必做，防文件无限增大）：**

分析完成且满足 **全部** 下列条件时，**删除** §2 对应行（不在 complexity 留档案）：

1. 结论已写入 **需求 / 架构 / 运维** 文档（或明确 `WONTFIX` 记入 round §5）
2. 若要开发：已有 **`upgrade_to_settle/plan-*.md`** + **TASKS UPGRADE** 行
3. round 源 bug 已标「已转 `plan-…` / WONTFIX / 文档已更」

```text
complexity 一行（路由）  →  分析  →  docs + plan + TASKS UPGRADE  →  删 complexity 行
                     ↘  仅文档/SOP  →  docs/operations 等  →  删 complexity 行
                     ↘  WONTFIX  →  round §5 一句  →  删 complexity 行
```

**工作流入口**：[`test-to-settle/README.md`](README.md) · 当前轮次 [`round-*.md`](round-2026-06-11-v1.1-deploy.md)

---

## 1. 何时写入 / 何时删除

| 情况 | 写 complexity §2 | 删 complexity §2 |
|---|---|---|
| 1～3 行小修，round 当轮可闭环 | ❌ → TASKS DEBUG | — |
| 需改需求 / 产品规则 / 多模块重构 | ✅ 追加一行 | 分析完 + plan/TASKS 或 docs 已更 |
| 部署 / 测试治理 / SOP 类 | ✅ 追加一行 | 决策写入 `docs/operations` 等后删除 |
| 明确不做（WONTFIX） | ✅ 可暂挂 | round §5 记录后立即删 |

**提交人**（Analyst）：round bug 标 `ESCALATED → complexity`，§2 **只追加一行**（摘要 + 链 round ID）。

---

## 2. 问题清单（活跃路由）

> **本表应保持短**：出站即删。

| ID | 来源轮次 | 来源 Bug | 严重度 | 问题摘要 | 为何不能当轮小修 | 提交人 | 提交时间 | 出站链（plan / docs） | 状态 |
|---|---|---|---|---|---|---|---|---|---|
| *(无活跃行 — C-0612-01 已转 [`plan-2026-06-12-qa-python-upload-first`](../upgrade_to_settle/plan-2026-06-12-qa-python-upload-first.md) + TASKS)* |

### 状态（仅活跃行用）

| 状态 | 含义 |
|---|---|
| `PENDING` | 待分析 / 拍板 |
| `IN_ANALYSIS` | 有人正在拆（可选，占坑防重复） |

出站后**无状态** — 该行已从表中删除；追溯看 round、docs、plan、`upgrade_to_settle/done/`。

---

## 3. 追加新行模板（复制后压成表格一行）

```markdown
| **C-MMDD-NN** | round-… | T-MMDD-XX | P? | 一句话摘要 | 为何不能小修 | <Agent> | YYYY-MM-DD | | `PENDING` |
```

长分析、方案全文 → 写 **`docs/`** 或 **`upgrade_to_settle/plan-*.md`**，**不要**堆在 complexity。

---

## 4. 与 round / TASKS / upgrade 的联动

```text
round ESCALATED → complexity §2 追加一行（路由）
       ↓
架构/PM 分析 → 更新 docs/requirements + docs/architecture（+ operations 若 SOP）
       ↓
要开发 → upgrade_to_settle/plan-*.md → TASKS UPGRADE 行
       ↓
round bug 标「已转 UP-xx」→ 删 complexity 该行
```

| 步骤 | 谁 | 做什么 |
|---|---|---|
| 1 | Analyst | round `ESCALATED` + complexity **加一行** |
| 2 | 架构/PM | 读 round + `T-*`；更新需求/架构文档 |
| 3 | 架构 | 需编码 → `upgrade_to_settle/plan-*.md` |
| 4 | PM/架构 | TASKS **UPGRADE** 行 → coder 占 plan |
| 5 | 同上 | round 源 bug 更新；**删 complexity §2 行** |

---

*2026-06-11：complexity = 中转路由，出站即删；全文在 docs + upgrade_to_settle。*
