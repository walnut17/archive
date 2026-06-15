# Co-test 0612 — qa-agent ReAct 迭代 / 工具升级

> **工作流**：[`CASE-FORMAT.md`](../CASE-FORMAT.md) · 入口 [`TASKS.md`](../TASKS.md) **`round-2026-06-12-qa-agent-react-iteration`**
> **操作时间线**：[`docs/operations/deployment_log.md`](../docs/operations/deployment_log.md) §11.1
> **轮次状态**：`OPEN`

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `round-2026-06-12-qa-agent-react-iteration` |
| **类型** | `DEBUG` |
| **Case 状态** | `OPEN` |
| **环境 / 基线** | `182.168.1.125` · qa-agent `:8001` · `main` ≥ `5fd29a8` |
| **范围** | Python `qa-agent` ReAct 多步推理；TUI `/v1/ask/stream` 或 `/time` 验收 |
| **前置** | [`done/round-2026-06-12-qa-regression.md`](done/round-2026-06-12-qa-regression.md) 已 CLOSED（T-0612-04 等） |
| **TASKS** | 路由 ID 与上表一致 |

---

## 1. 任务描述（Recorder / Co-test）

> 来源 Co-test → `DEPLOY`。

### 1.1 产品规则（Operator / PM 拍板 — 本 case 验收依据）

**ReAct 每一轮拼给 LLM 的上下文必须严格递增**；上一轮若未答完或结果不足，下一轮 **必须换工具或换策略**，禁止同参重复。

| 轮次 | 必须包含的新增信息 | 下一步策略 |
|---|---|---|
| 第 1 轮 | 用户问题 + system prompt | 选工具或 `FINAL_ANSWER` |
| 第 2 轮起 | **+** 上一轮 `tool` / `args` / **`observation` 摘要**（含命中/未命中/置信度） | 若第 1 轮已 `find_project` 命中 → **应** `get_project_business_data` / `query_mysql` / `search_fulltext` 等 **换工具**；若未命中 → **换 query 变体**，不得原样重试 |
| 第 N 轮 | 累积全部已执行步骤 | 直至 `FINAL_ANSWER` 或明确 `ask_clarification` |

**禁止**：连续两步 `tool` + `toolArgs` 完全相同（引擎层已有检测，但应在第 2 步前就引导 LLM 换策略，而不是靠强制结束糊弄用户）。

### 1.2 Bug 清单

| ID | 来源 | 严重度 | 现象 / 验收 | 子项状态 |
|---|---|---|---|---|
| **T-0612-07** | DEPLOY | **P0** | **lmz 材料数未答 + 同工具死循环**（见 §1.3）。**验收**：TUI `/time lmz项目下有多少份材料？` 或流式等价问句 → ① 答案含**具体材料份数**（数字） ② ReAct 步骤中 **不出现** 连续两步相同 `find_project`+相同 `query` ③ 第 1 步 `find_project` 命中后，第 2 步须为 **`get_project_business_data`**（或 `query_mysql` 统计），见 `prompts.py` 示例「lmz项目下有几份材料」 | **FIX 待 125 复测** |
| **T-0612-08** | DEPLOY | P2 | TUI 流式模式答案区泄漏 ReAct **raw JSON**；`done.answer` 终稿未替换可见输出。**验收**：流式问 lmz 同上 → 答案区仅人类可读终稿，无 `` ```json `` 块 | **FIX 待 125 复测** |
| **T-0615-proposal-semantics** | ESCALATED | P2 | 议案计数混淆维护性 proposal 与正式投委会议案 | **已转** [`plan-2026-06-15-proposal-semantics`](../upgrade_to_settle/plan-2026-06-15-proposal-semantics.md) |

### 1.3 复现细节（TUI · 2026-06-12 · Operator 已确认）

**环境**：125 · qa-agent 已 `start.ps1` · TUI `python qa-agent/tools/tui_repl.py`

**问句**：`lmz项目下有多少份材料？`

**现象**：

| 项 | 实际 |
|---|---|
| 耗时 | 13578ms |
| 步数 | 3 |
| 徽章 | `PENDING_REVIEW`（`find_project` → `switchDecision=UNCLEAR`） |
| 步 1 | `find_project(query=lmz项目)` |
| 步 2 | **相同** `find_project(query=lmz项目)` ← **违反 §1.1** |
| 步 3 | 引擎「检测到重复工具调用，强制结束」`FINAL_ANSWER` |
| 来源 | 2× `PROJECT · lmz授信`（说明第 1 步已命中，但未继续统计材料） |
| 用户可见答案 | 两段 raw LLM JSON（流式 token 泄漏），**无**「共 N 份材料」 |

**期望链路**（[`qa-agent/app/agent/prompts.py`](../qa-agent/app/agent/prompts.py) 示例 3）：

```text
find_project → get_project_business_data(projectCode) → FINAL_ANSWER（含 materialCount）
```

**代码锚点（Coder）**：

| 文件 | 说明 |
|---|---|
| `qa-agent/app/agent/engine.py` | `build_user_prompt()` 已 append 步骤 obs，但 LLM 仍重复；需 **prompt 约束 +/或 命中后引擎 hint/路由** |
| `qa-agent/app/agent/prompts.py` | 示例 3 与场景路由表；可加「已 find 命中 → 统计类问题禁止再 find」 |
| `qa-agent/app/agent/tools/get_project_business_data.py` | 返回 `materialCount` |
| `qa-agent/tools/tui_repl.py` | T-0612-08：`done` 后重绘终稿答案 |

### 1.4 建议修复方向（Analyst 草案 · 非强制）

1. **Prompt**：在「本轮已执行步骤」后追加规则：「若上一步 `find_project` 已返回 projectCode，统计/材料数类问题下一步必须 `get_project_business_data` 或 `query_mysql`，禁止再次 `find_project` 同 query」
2. **Engine 硬规则（可选）**：解析上一步 obs 已有 PROJECT 命中 + 用户问含「材料/份数/几个」→ 下一步若仍 `find_project` 同参，**改写 prompt 注入纠正** 或直接 dispatch `get_project_business_data`
3. **死循环前升级**：第 2 步重复前，用第 1 步 observation 拼 richer context（含 `switchDecision`、候选 projectCode），而非仅 generic 强制结束文案
4. **TUI**：流式时只渲染 `FINAL_ANSWER` token 或 `done` 后清屏重印 `answer`

---

## 2. Agent Blocks

### Recorder · Co-test Guide · 2026-06-12

| 字段 | 内容 |
|---|---|
| **Agent** | Auto (Co-test Guide) |
| **时间** | 2026-06-12 |
| **摘要** | qa-agent TUI B1；立项 §1.1 ReAct 递增规则；T-0612-07/08 OPEN |

----- agent-block begin -----
role: Coder
agent: Auto
time: 2026-06-12
ref: round-2026-06-12-qa-agent-react-iteration
summary: T-0612-07/08 — ReAct 递增提示 + find 后强制 get_project_business_data + materialCount SQL + 流式/TUI 终稿展示

**改动**

| 文件 | 说明 |
|---|---|
| `qa-agent/app/agent/react_helpers.py` | **新增** — `append_step_hints` / `maybe_upgrade_step` / `try_recover_material_count_loop` |
| `qa-agent/app/agent/engine.py` | prompt 含 args+引擎提示；第 2 步升级工具；死循环材料数恢复；流式仅输出终稿 token |
| `qa-agent/app/agent/tools/get_project_business_data.py` | `materialCount` 真实 SQL（material←proposal←project） |
| `qa-agent/tools/tui_repl.py` | 流式结束后一次性打印终稿，不逐字打 ReAct JSON |
| `qa-agent/tests/test_react_helpers.py` | **新增** 4 测例 |

**验证**

- `pytest tests/test_react_helpers.py tests/test_parser.py` → **7 passed**
- 125 待 Operator：`start.ps1 -Force` 后 TUI 重问 lmz

**commits**

- （工作区，待 push）

----- agent-block end -----

----- agent-block begin -----
role: Recorder
agent: qa-agent专修专测 worker
time: 2026-06-15
ref: T-0612-07, T-0612-08
source: AUTO
summary: 离线 64/65 pytest；125 live 工具链升级 OK 但 materialCount SQL 炸；流式无 JSON 泄漏

**环境**：开发机 → `182.168.1.125:8001` · commit `1124e0a`（125 已部署新 ReAct 逻辑）

### 自动化

| 项 | 结果 | 说明 |
|---|---|---|
| `pytest tests/ -q`（离线） | **64 passed, 1 failed** | `test_streaming.py::test_run_agent_stream_yields_token_events` — `session_id` 触发 `load_memory` 连 MySQL，未 mock `db_cursor` |
| `test_react_helpers.py` | **4/4 PASS** | `maybe_upgrade_step` / `try_recover_material_count_loop` 等 T-0612-07 回归 |
| `test_api_http_live.py -m live` | **7/7 PASS** | AT-001 契约冒烟仍绿 |

### T-0612-07（125 live · `/v1/ask`）

问句：`lmz项目下有多少份材料？`

| 验收项 | 结果 |
|---|---|
| 无连续同参双 `find_project` | ✅ 步序 `find_project` → `get_project_business_data` ×2 → `FINAL_ANSWER` |
| 答案含具体材料份数 | ❌ 答案为「抱歉，多次尝试未找到匹配结果…」 |
| 第 2 步换工具 | ✅ 引擎已升级至 `get_project_business_data` |

**根因（live 步 observation）**：

```text
步 2/3 get_project_business_data → ERROR: (1054, "Unknown column 'p.stage' in 'field list'")
步 4 引擎「检测到重复工具调用，强制结束」
```

`get_project_business_data.py` SELECT 含 `p.stage`，125 `archive_db.project` 无此列（见 `docs/architecture/DATABASE.md` 无 stage）。

### T-0612-08（125 live · `/v1/ask/stream`）

| 验收项 | 结果 |
|---|---|
| 答案区无 raw JSON / `` ```json `` | ✅ `stream_acc` 仅人类可读 apology 文案，无 `find_project`/`"thought"` 泄漏 |
| `done.answer` 终稿 | ✅ 与流式累积一致 |

### 结论

**REQUEST_CHANGES** — ReAct 递增与工具升级逻辑已生效，T-0612-08 流式展示 OK；**T-0612-07 主验收未过**（`get_project_business_data` SQL 与 125 库表不一致 → 材料数无法返回）。Coder 须修 SQL（去掉或兼容 `p.stage`）并补 `test_streaming` 的 `db_cursor` mock；修后 125 `start.ps1 -Force` 重验 lmz。

----- agent-block end -----

----- agent-block begin -----
role: Reviewer
agent: Auto
time: 2026-06-15
ref: round-2026-06-12-qa-agent-react-iteration
ref_commit: 1124e0a..e9d4bfc,5fd29a8
verdict: APPROVED
summary: ReAct 递增+工具升级+流式终稿代码通过；T-0612-07 待 125 复测确认

**已通过 ✅**

| Bug | commit | 结论 |
|---|---|---|
| T-0612-07 逻辑 | `1124e0a` `08c897c` | `react_helpers` 升级 find→biz；无同参双 find |
| T-0612-07 SQL | `e9d4bfc` | 去掉 `p.stage`；对齐 125 schema |
| T-0612-08 | Coder 块 | 流式仅终稿；Recorder live 已 PASS |
| 单测 | `test_react_helpers` 等 | 离线通过 |

**未关单**

- §1.2 仍标 **FIX 待 125 复测**：`e9d4bfc` 后无新 lmz live 留痕
- `test_get_project_business_data` mock 未更新（见 proposal-semantics）

----- agent-block end -----

----- agent-block begin -----
role: Reviewer
agent: Auto
time: 2026-06-15
ref: round-2026-06-12-qa-agent-react-iteration
ref_commit: 4007956 + 工作区 WIP（未 push）
verdict: APPROVED
summary: 抵押物细问路由（4007956 + 本地 WIP）代码通过；须 commit 一体交付 + 125 live

**说明**：上轮只审了 T-0612-07/08 与两个 plan，**未单独回应** Coder 在 `4007956` 中 bundled 的债权抵押物细问能力 — 本条补审。

**已通过 ✅（`4007956` 已 commit）**

| 模块 | 内容 |
|---|---|
| `react_helpers.py` | `FEATURE_COLLATERAL_DETAIL_ROUTING` · `question_needs_collateral_detail` · `extract_collateral_items_from_texts` · `format_collateral_inventory_answer` · `extract_debt_anchor_from_question` · 剩余金额不误走 material evidence |
| 合成路径 | `synthesize_evidence_answer` / `try_finalize_evidence_from_search` 细问走清单式答案 |
| 提示 | `append_step_hints` 抵押物 query 须含锚点+估值关键词 |

**已通过 ✅（工作区 WIP · 尚未 commit）**

| 文件 | 内容 |
|---|---|
| `prompts.py` | 场景路由表 + 示例 2f（还剩哪些/初始估值） |
| `engine.py` | `_prepare_session_context` 从问句解析 `last_debt_target` |
| `memory.py` | `resolve_session_references` 支持问句内嵌债权名 |
| `tests/test_react_helpers.py` | +6 测（估值清单/锚点/细问合成等）— **本地全绿** |
| `tests/test_memory.py` | +2 测（内嵌债权不重复注入） |
| `desc/08-债权抵押物问答.md` 等 | 经验文档 |

**Coder 须做（非打回代码逻辑）**

1. 将上述 WIP **与 proposal 单测修复一并 commit push**（当前 Reviewer 只审到工作区，不算已交付）
2. **125 live**：细问「岭兜建材二厂债权下的抵押物还剩哪些，初始估值分别是多少？」— 清单式答案 + 来源
3. T-0612-07 lmz 材料数复测仍缺（与抵押物独立）

**与 plan 边界**：抵押物路由 **不属于** `plan-proposal-semantics` / `plan-cutover`；留在本 round 或后续 complexity，不阻塞 proposal plan 关单（proposal 只关心议案计数字段）。

----- agent-block end -----

---

## 3. 关单检查

- [ ] T-0612-07：125 TUI `/time` + 流式各测 1 次，材料数 + 无同参双 find
- [ ] T-0612-08：流式答案区无 JSON 泄漏
- [ ] `qa-agent/tests/` 补回归（mock LLM 或集成：find 命中 → 第二步须换工具）
- [ ] **Reviewer(CLOSED)** · `done/` · TASKS 删行
