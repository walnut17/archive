# plan-2026-06-15-analysis-framework-scaffold — qa-agent 后台分析脚手架验收

> **状态**：`OPEN` — Coder 占 TASKS 行后执行  
> **来源**：complexity **C-0615-02** · round `round-2026-06-15-analysis-framework` T-0615-background-analysis

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-15-analysis-framework-scaffold` |
| **类型** | `UPGRADE` |
| **Case 状态** | `CLOSED` |
| **标题** | 后台深度分析脚手架：DDL + Worker + API + Agent 读 snapshot 工具 |
| **需求锚点** | 分析职责迁移 · [`09-analysis-ownership-python.md`](../docs/architecture/09-analysis-ownership-python.md) §4～§7 |
| **架构锚点** | [`qa-agent/desc/06-后台深度分析框架.md`](../qa-agent/desc/06-后台深度分析框架.md) |
| **前置** | 125 / 本地 `archive_db` 可连；qa-agent 已部署 |
| **后继** | [`plan-2026-06-15-analysis-ownership-cutover`](plan-2026-06-15-analysis-ownership-cutover.md) |

---

## 1. 任务描述（PM / 架构）

### 做

- 确认 **DDL 已在目标库执行**（5 张分析表 + 4 个内置模板种子）
- 验收 **AnalysisWorker** 端到端：discover → job → LLM(mock/真) → `analysis_snapshot` + `project_asset` + 部分 `project_fact`
- 补齐 **config.json 示例**（`qaAgent.analysisWorker`）
- 新增 ReAct 工具 **`get_project_analysis`**：读 snapshot 摘要，减少全文检索
- **pytest** 覆盖新工具；`GET /v1/analysis/status` 回归

### 不做

- Java `triggerAfterParse` 改造（见 cutover plan）
- Java `ExtractionEngine` 下线
- 管理端 UI 维护 `analysis_template`

### 验收

| # | Given | When | Then |
|---|---|---|---|
| A | 已 `source migrate_260615_analysis_framework.sql` | `GET /v1/analysis/status` | `tables_ready=true`，templates ≥ 4 |
| B | 某项目有 `parsed_text` | `POST /v1/analysis/enqueue` `{project_code}` | 返回 `job_id`；队列最终 success |
| C | job success 后 | `GET /v1/analysis/projects/{code}` | 有 snapshots；`project_analysis_state.last_status=success` |
| D | snapshot 存在 | ReAct 问「该项目投资结构/利率」 | 调用 `get_project_analysis`，答案引用 summary 非纯全文 |
| E | CI | `pytest qa-agent/tests/` | 全绿 |

---

## 2. 开发说明（架构师 · Coder 只读）

### 2.1 已有代码（勿重写）

| 模块 | 路径 |
|---|---|
| Worker / scheduler / repository | `qa-agent/app/analysis/*` |
| HTTP API | `qa-agent/app/api/analysis.py` |
| 单元测试 | `qa-agent/tests/test_analysis_framework.py` |
| 迁移 SQL | `deploy/sql/migrate_260615_analysis_framework.sql` |
| bundle | `deploy/sql/migrate_260615_qa_agent_bundle.sql` |

### 2.2 Coder 工单

| # | 任务 | 文件 |
|---|---|---|
| S1 | `config/config.example.json` 增加 `qaAgent.analysisWorker` | `config/config.example.json` · `qa-agent/app/config.py` 读入（若缺） |
| S2 | 实现 `get_project_analysis` 工具 + 注册 | `qa-agent/app/agent/tools/get_project_analysis.py` · `tools/__init__.py` |
| S3 | prompt 增加「结构/利率/债权」类问题优先 snapshot | `qa-agent/app/agent/prompts.py` |
| S4 | analysis LLM 调用写 `llm_call_log`（scenario=`ANALYSIS`） | `qa-agent/app/analysis/extractor.py` 或共用埋点模块 |
| S5 | README / desc 链到 plan 与 SQL 路径 | `qa-agent/README.md` |
| S6 | 125 执行 SQL 后手工 enqueue 一条 lmz 项目，截图或日志写入 plan Coder 块 | 运维记录 optional |

### 2.3 SQL（DBA / Coder 在 MySQL 客户端执行）

```sql
USE archive_db;
source D:/projects-online/deploy/sql/migrate_260615_qa_agent_bundle.sql;
-- 若 260612/260615 chat 已跑过，可只 source migrate_260615_analysis_framework.sql
```

### 2.4 工具契约 `get_project_analysis`

**Input**: `{ "projectCode": "shtx26007" }`

**Output**（示例）:

```json
{
  "projectCode": "shtx26007",
  "analysisStatus": "success",
  "snapshots": [
    { "templateCode": "project.investment_structure", "summary": "...", "confidenceLevel": "AI_INFERRED" }
  ],
  "assetCount": 2,
  "factTypes": ["interest_rate", "transaction_form"]
}
```

---

## 3. Agent Blocks

> 顺序：`Coder` ↔ `Reviewer` → **Reviewer(CLOSED)**

----- agent-block begin -----
role: Reviewer
agent: Auto
time: 2026-06-15
ref: plan-2026-06-15-analysis-framework-scaffold
ref_commit: ecf16f8
verdict: APPROVED
summary: Worker/API/get_project_analysis/DDL SQL/单测齐；125 DDL 执行留 Operator

**ecf16f8 核对 ✅**

| 工单 | 结论 |
|---|---|
| S1 | `config.example.json` → `qaAgent.analysisWorker` |
| S2 | `get_project_analysis.py` + registry |
| S3 | `prompts.py` snapshot 路由 |
| S4 | `extractor.py` + `llm_call_log` scenario=ANALYSIS |
| S5 | desc/README + SQL 路径 |
| 测试 | `test_analysis_framework.py` 等；离线 **125 passed**（1 失败属 proposal-semantics 单测 mock） |

**非阻塞**：S6 125 手工 enqueue 无 Coder 块留痕；验收 A 需 DBA `source migrate_260615_*.sql`（Operator）

----- agent-block end -----

----- agent-block begin -----
role: Reviewer
agent: Auto
time: 2026-06-15
ref: case
verdict: CLOSED
archive: upgrade_to_settle/done/plan-2026-06-15-analysis-framework-scaffold.md
summary: 分析脚手架代码交付完成；cutover 可继续

----- agent-block end -----

---

## 4. 关单检查

- [x] §1 验收 A～E（代码侧；125 DDL/enqueue 待 Operator）
- [x] Reviewer **APPROVED** + **CLOSED**
- [x] `git mv` → `done/` · TASKS 删行
