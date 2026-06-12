# Co-test 0612 — 知识库 / 部署编译回归

> **工作流**：[`CASE-FORMAT.md`](../CASE-FORMAT.md) · 入口 [`TASKS.md`](../TASKS.md) **`round-2026-06-12-qa-regression`**
> **操作时间线**：[`docs/operations/deployment_log.md`](../docs/operations/deployment_log.md) §10
> **轮次状态**：`OPEN`

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `round-2026-06-12-qa-regression` |
| **类型** | `DEBUG` |
| **Case 状态** | `OPEN` |
| **环境 / 基线** | `182.168.1.125`，`main` ≥ `5408bee`（Co-test 2026-06-12） |
| **范围** | 智能问答（多轮 / 离题拒答 / 编译阻塞）；**不含** RI-16 立项流程（→ upgrade plan） |
| **TASKS** | 路由 ID 与上表一致 |

---

## 1. 任务描述（Recorder / Co-test）

> 来源 Co-test → `DEPLOY`。大改 → [`complexity.md`](complexity.md) / [`upgrade_to_settle/`](../upgrade_to_settle/README.md)。

### 1.1 Co-test 已测范围

| # | 项 | 结果 |
|---|---|---|
| 1 | 125 git 对齐 + `mvn` 重建 + jar 重启 | ✅（经 T-0612-01～03 三轮 fix） |
| 2 | 离题问「今天天气怎么样？」 | ✅ 拒答文案正确；思考链见 T-0612-06 |
| 3 | 同会话第 2 问起任意业务问 | 🔴 全 500，见 T-0612-04 |
| 4 | 新建项目入口 | 🔴 与 RI-16 不符 → **ESCALATED** [`plan-2026-06-12-qa-python-upload-first`](../upgrade_to_settle/plan-2026-06-12-qa-python-upload-first.md) |

### 1.2 Bug / 验收清单

| ID | 来源 | 严重度 | 现象 / 验收 | 子项状态 |
|---|---|---|---|---|
| **T-0612-01** | DEPLOY | P0 | `mvn compile`：`AgentEngine.java` 缺类闭合 `}` | **FIXED** @ `96abc32` · 125 已验证 BUILD SUCCESS |
| **T-0612-02** | DEPLOY | P0 | `ArchiveMaterialPathResolver.getMaterial()` 不存在；`AuditLogService.truncate` 缺失 | **FIXED** @ `16eec7a` |
| **T-0612-03** | DEPLOY | P1 | `ClientErrorControllerTest` 误 import `mock.bean.MockBean`（`-DskipTests` 仍 testCompile 失败） | **FIXED** @ `5408bee` |
| **T-0612-04** | DEPLOY | **P0** | 知识库聊天 UI：第 1 问 `/api/qa/ask` OK；**第 2 问起** `POST /api/qa/turn/{sessionId}` → **500**。根因：`MultiTurnController` qa-agent 降级遗漏，Python 服务未跑时抛异常不降级 Java。**验收**：同 session 连续 2 问均 200 | **VERIFY** | 修 `MultiTurnController.ask()` qa-agent 失败后降级 Java |
| **T-0612-06** | DEPLOY | P2 | 离题拒答时思考过程显示「无法解析 LLM 输出,直接返回原文」+ `FINAL_ANSWER`；用户可见答案正确但思考链不美观。**验收**：离题问仍拒答；思考链不出现 parse fallback 文案（或改为明确「直接拒答」步骤） | **VERIFY** | `parseAgentStep` 改为 "直接返回结果" |
| **T-0612-05** | DEPLOY | P1 | 新建项目仍手工表单入口，非 RI-16 上传优先 | **ESCALATED** → [`plan-2026-06-12-qa-python-upload-first`](../upgrade_to_settle/plan-2026-06-12-qa-python-upload-first.md)（含 RI-16 + 二期） |

### 1.3 T-0612-04 复现（Operator 已确认）

1. 知识库 → 第 1 问「今天天气怎么样？」→ ✅
2. 第 2 问「lmz项目下有多少份材料？」→ `POST …/api/qa/turn/{uuid}` **500**
3. 后续同会话任意再问 → **均 500**
4. **临时绕过**：Ctrl+F5 后仅问 1 条（仍走 `/api/qa/ask`）

---

## 2. Agent Blocks

### Recorder · Co-test Guide · 2026-06-12

| 字段 | 内容 |
|---|---|
| **Agent** | Auto (Co-test Guide) |
| **时间** | 2026-06-12 |
| **摘要** | 0612 新轮 Co-test；§1 收录 T-0612-01～06；T-0612-05 升格 upgrade；本 case 主攻 QA 多轮与编译留痕 |

----- agent-block begin -----
role: Reviewer
agent: Auto
time: 2026-06-12 16:30
ref: round-2026-06-12-qa-regression
verdict: REQUEST_CHANGES
summary: T-0612-01～03/06 可过；T-0612-04 在 125 默认配置下仍 500，不可关单

**APPROVED（子项）**

| ID | commit / 位置 | 结论 |
|---|---|---|
| T-0612-01 | `96abc32` AgentEngine 闭合括号 | ✅ |
| T-0612-02 | `16eec7a` PathResolver + AuditLogService.truncate | ✅ |
| T-0612-03 | `5408bee` MockBean import | ✅ |
| T-0612-06 | `848e116` `parseAgentStep` thought→「直接返回结果」；Python `parser.py` 结构化拒答 | ✅ Java 路径；Python 路径待 qa-agent 部署后 Co-test |
| T-0612-05 | ESCALATED → upgrade plan | ✅ 不在本 round 阻塞 |

**P0 — T-0612-04 未修完**

125 默认：`app.qa-agent.enabled=true` · `spring.ai.agent.enabled=false` · **Python 未部署**。

- 第 1 问 `/api/qa/ask`：Python 失败 → 降级 `legacyAsk` → **200** ✅
- 第 2 问 `/api/qa/turn/{sessionId}`：Python 失败 → Java Agent 未启用 → `IllegalStateException` → **500** ❌

`848e116` 只加了「Python 失败 → Java AgentEngine」，**未覆盖「双路径皆不可用」**。与 §1.3 验收「同 session 连续 2 问均 200」不符。

**建议修（二选一或组合）**

1. `MultiTurnController` 第三路径：qa-agent + Java 均失败时 **503 明确文案** 或 **无状态降级**（调用 legacy 并提示丢失多轮上下文）
2. 125 在 qa-agent WinSW 就绪前：`QA_AGENT_ENABLED=false` 或 `AGENT_ENABLED=true` 作临时兜底
3. Co-test 复现后 §1.2 标 FIXED 再 `待审`

**附：upgrade plan 一期骨架（同工作区，非本 round 关单条件）**

- `qa-agent/` pytest **18 passed**（不含 live HTTP）
- Java BFF + RI-16 上传页骨架已落；**二期 E～K 未做**
- ⚠️ `main` HEAD 为 stash 式 commit `ee35f7c`，push 前需整理为正常 feature commit

----- agent-block end -----

---

## 3. 关单检查（审查员 Reviewer(CLOSED) 完成后打勾）

- [ ] T-0612-04 / T-0612-06 已 FIX + Reviewer APPROVED（或 ESCALATED 有去向）
- [ ] T-0612-01～03 标 FIXED 已核对
- [ ] T-0612-05 已在 upgrade plan 路由，本表保持 ESCALATED
- [ ] 已有 **`Reviewer` + `verdict: CLOSED`** 块
- [ ] `git mv` → [`done/`](done/README.md) · [`TASKS.md`](../TASKS.md) 删行
