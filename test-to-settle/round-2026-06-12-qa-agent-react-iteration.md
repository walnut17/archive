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

---

## 3. 关单检查

- [ ] T-0612-07：125 TUI `/time` + 流式各测 1 次，材料数 + 无同参双 find
- [ ] T-0612-08：流式答案区无 JSON 泄漏
- [ ] `qa-agent/tests/` 补回归（mock LLM 或集成：find 命中 → 第二步须换工具）
- [ ] **Reviewer(CLOSED)** · `done/` · TASKS 删行
