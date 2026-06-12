# plan-2026-06-12-qa-python-upload-first — Python QA 微服务 + RI-16 + 二期全量

> **状态**：`READY` — Coder 占 TASKS 行后 **一期收尾 + 二期全做**，再 push / 125 联调  
> **触发**：Co-test QA 回归 + 架构决策「Java Agent LLM 不可用 → Python FastAPI 专司 QA/抽取」

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-12-qa-python-upload-first` |
| **类型** | `UPGRADE` |
| **Case 状态** | `OPEN` |
| **标题** | Python qa-agent（MVP→全工具）+ Java BFF + RI-16 + 125 部署 + Java Agent 退役 |
| **需求锚点** | §5.6 · §5.10.2 · RI-16 · RI-47/22 · T-0612-04/05/06 |
| **架构锚点** | [`08-qa-agent-python-service.md`](../docs/architecture/08-qa-agent-python-service.md) · [`07-archive-fs-agent-tools.md`](../docs/architecture/07-archive-fs-agent-tools.md) |
| **取代** | [`plan-2026-06-12-project-create-upload-first`](plan-2026-06-12-project-create-upload-first.md) |

---

## 1. 任务描述（PM / 架构）

### 1.0 架构师已落代码（Coder 勿重复造轮子）

> 以下**已在工作区**（未 commit），Coder 以 **核对 + 补全 + 二期** 为主，不要从零重写。

| 模块 | 路径 | 状态 |
|---|---|---|
| Python 服务骨架 | `qa-agent/`（FastAPI / ReAct / GLM / 4 工具 / extract / pytest×3） | 🟡 骨架 |
| Java HTTP 客户端 | `backend/.../qaagent/QaAgentClient.java` · `QaAgentProperties.java` | 🟡 骨架 |
| QA 转发 | `QaController.java` · `MultiTurnController.java`（含 POST） | 🟡 骨架 |
| 立项 staging | `ProjectCreateStagingService.java` · `POST /api/projects/staging-upload` | 🟡 骨架 |
| 前端上传 | `ProjectCreateUpload.vue` · 路由 · `ProjectForm` draft 更新 | 🟡 骨架 |
| 配置 | `application.yml`（`app.qa-agent.*` · Java Agent 默认关） | ✅ |
| 架构文档 | `docs/architecture/08-qa-agent-python-service.md` | ✅ |

**已验证**：`mvn compile -DskipTests` ✅ · `qa-agent` 下 `pytest tests/` 3 passed ✅

---

### 1.1 一期（MVP 收尾 — 必做）

**A. Python `qa-agent`（`:8001`）**

| 端点 | 用途 |
|---|---|
| `GET /health` | 健康检查 |
| `POST /v1/ask` | 单轮问答 |
| `POST /v1/turn/{session_id}` | 多轮（`spring_ai_chat_memory`） |
| `POST /v1/extract/project-fields` | 立项字段抽取 |

- 工具 MVP：`find_project` · `search_fulltext` · `query_mysql` · `llm_summarize`
- ReAct ≤5 步；JSON 解析失败 → **结构化拒答**（修 T-0612-06）

**B. Java BFF**

- `app.qa-agent.enabled=true` → QA / extract **转发 Python**
- `spring.ai.agent.enabled=false`（默认）
- `MultiTurnController` **POST JSON**（修 T-0612-04）
- `POST /api/projects/staging-upload` + `extract-preview` 走 Python

**C. 前端 RI-16**

- 「+ 新建项目」→ 上传页 → 预填表单 → 保存草稿项目

**D. 工程卫生（push 前）**

- [ ] `qa-agent/.gitignore`：`.venv/` · `__pycache__/` · `.pytest_cache/` · `.env`
- [ ] **不要** commit `qa-agent/.venv/`
- [ ] 根 `README.md` §0.4 或 `qa-agent/README.md` 链到本 plan

---

### 1.2 二期（本 plan 一并交付 — Coder 全做）

**E. Python 工具补齐（对齐 Java Agent v1.1 能力）**

| 工具 | 参考实现 | 说明 |
|---|---|---|
| `get_project_business_data` | `GetProjectBusinessDataTool.java` | 已知 projectCode 的业务汇总 |
| `ask_clarification` | `AskClarificationTool.java` | 追问用户；ReAct 循环内返回 clarification 给前端 |
| `archive_fs` | `agent/tool/archive/*` + [`07-archive-fs-agent-tools.md`](../docs/architecture/07-archive-fs-agent-tools.md) | `list` / `grep` / `read`；双根白名单 + materialVersionId 绑路径 |
| `network_dict_lookup` | `NetworkDictLookupTool.java` | 百度百科/维基；全失败 `{found:false}` 不抛 500 |

**F. v1.1 行为（RI-22 / RI-47）**

- [ ] `find_project` 5 级隐式切换 → 响应 `project_switch_hint`
- [ ] 置信度 3 级 → 响应 `confidence_badge`（`AI_INFERRED` / `PENDING_REVIEW`）
- [ ] `agent_sources` 结构化来源（对齐 Java `Source` DTO / 前端 `ChatMessage`）
- [ ] `QueryMysqlTool` 7 重加固对齐 Java（filters 白名单 · limit ≤1000 · 数值上限）

**G. 可观测与 LLM 埋点**

- [ ] Python 侧 LLM 调用写入 `llm_call_log`（scenario=`AGENT_STEP` / `EXTRACTION`）
- [ ] Java `QaAgentClient` 超时 / 连接失败 → 明确 503 文案，**不** 500 裸栈
- [ ] Spring Boot Actuator：`QaAgentHealthIndicator`（读 `/health`）

**H. 125 生产部署（WinSW 第 5 进程）**

| 文件 | 操作 |
|---|---|
| `deploy/winsw/qa-agent.xml` | **新增** WinSW 配置 |
| `deploy/scripts/register-services.bat` | 改 — 注册 qa-agent |
| `docs/operations/RUNBOOK.md` | 改 — 启停 / 日志路径 |
| `docs/operations/deployment_log.md` | 联调后追加 §11 |

环境变量（与 `config.json` 同步）：`GLM_API_KEY` · `MYSQL_*` · `QA_AGENT_PORT=8001` · `FILE_ROOT` · `PARSED_ROOT`

**I. Java Agent 退役（Python 稳定后）**

- [ ] 删除或 `@Deprecated` + `spring.ai.agent.enabled` 默认 false 且文档说明不再维护
- [ ] 移除 `AgentEngine` 主路径 bean 条件加载；保留 **legacy FULLTEXT** 降级（`QaController.legacyAsk`）
- [ ] 清理仅 Java Agent 用的测试：`AgentIntegrationTest` 改 mock Python 或迁 Python 集成测
- [ ] `docs/architecture/08-qa-agent-python-service.md` §7 更新为「Java Agent 已退役」

**J. 测试**

| 项 | 要求 |
|---|---|
| Python | `pytest`：parser + 各 tool 单测（mock DB/GLM）；可选 `httpx` TestClient 打 `/v1/ask` |
| Java | `mvn test` 不因删 Agent 而挂；`QaAgentClient` 可 `@MockBean` |
| 125 Co-test | 见 §1.3 验收表 |

**K. DEBUG round 关单联动**

| Bug | 本 plan 负责 | 关单动作 |
|---|---|---|
| **T-0612-04** | POST 多轮 + Python turn | round §1 标 **FIXED**；Co-test 2 问均 200 |
| **T-0612-05** | RI-16 上传优先 | round §1 标 **已转本 plan** → 验收后 FIXED |
| **T-0612-06** | Python parser 拒答步骤 | round §1 标 **FIXED** |
| **T-0612-01～03** | 已 FIXED | Coder 仅核对 |

round 全过 → Reviewer **CLOSED** → `test-to-settle/done/` · **删** TASKS 行 `round-2026-06-12-qa-regression`

---

### 1.3 不做

- LibreOffice / 多模态 OCR（RI-71，另开 plan）
- Caddy 直连 Python（仍 localhost + Java BFF）
- 批量多文件立项上传（SUPPLEMENTARY，另开 plan）

---

### 1.4 验收（125 Co-test — 一期 + 二期合并）

| # | Given | When | Then |
|---|---|---|---|
| 1 | qa-agent + backend + WinSW 均启动 | `GET /health` + 业务问 | 200；`agentMode=true` |
| 2 | 同 session | 离题 1 问 + 业务第 2 问 | 均 200；无 parse fallback 丑文案 |
| 3 | 问「空债权是什么」 | Agent 调 `network_dict_lookup` | 有定义或 `{found:false}`，不 500 |
| 4 | 锁定项目后 | `archive_fs` grep 材料 | 只读；越界路径拒绝 |
| 5 | admin | 新建项目 → 上传 → 保存 | 首屏上传；预填 ≥1 字段；列表可见 |
| 6 | 无 GLM key | 上传 + 手工保存 | failure banner；仍可提交 |
| 7 | 停 qa-agent | 问 QA | Java 返 503 或降级 legacy，**不** 500 裸栈 |
| 8 | `mvn test` + `pytest` | CI / 本地 | 全绿（或 documented skip 仅 GLM 集成） |

---

## 2. 开发说明（架构师 · Coder 工单）

### 2.1 文件清单（一期已有 + 二期新增）

| 路径 | 阶段 | 操作 |
|---|---|---|
| `qa-agent/` | 一+二 | 核对 / 扩展工具 / 埋点 / 测试 |
| `qa-agent/.gitignore` | 一 | **新增** |
| `backend/.../qaagent/*` | 一+二 | 错误处理 · health |
| `backend/.../controller/QaController.java` | 一+二 | 503 降级 |
| `backend/.../agent/MultiTurnController.java` | 一 | 已有 POST |
| `backend/.../ProjectCreateStagingService.java` | 一 | 核对 Tika 解析等待 |
| `backend/.../health/QaAgentHealthIndicator.java` | 二 | **新增** |
| `backend/.../agent/**` | 二 | 退役 / 删测 |
| `deploy/winsw/qa-agent.xml` | 二 | **新增** |
| `deploy/scripts/register-services.bat` | 二 | 改 |
| `frontend/src/views/ProjectCreateUpload.vue` | 一 | 上传进度 / 解析等待 UX |
| `frontend/src/views/Knowledge.vue` | 二 | 可选：`ask_clarification` UI |
| `docs/architecture/08-qa-agent-python-service.md` | 二 | 补工具表 · 部署 · 退役 |
| `docs/operations/RUNBOOK.md` | 二 | qa-agent 启停 |
| `docs/operations/deployment_log.md` | 二 | §11 留痕 |
| `test-to-settle/round-2026-06-12-qa-regression.md` | 二 | T-* 状态 + Reviewer CLOSED |

### 2.2 Python `archive_fs` 实现要点

```text
config: FILE_ROOT / PARSED_ROOT（与 app.storage 同值）
ArchivePathGuard: normalize + startsWith(root)
materialVersionId → SQL 查 storage_path / parsed_text_path
actions: list(max 100) | grep(max 200 lines, 2MB) | read(max 512KB)
files 区扩展名白名单: .txt .md .csv .json .xml .html
```

### 2.3 部署拓扑（125）

```text
WinSW: archive-backend (:8080) ──HTTP──► qa-agent (:8001, 127.0.0.1)
         Caddy (:5173 前端) ──► /api/* → backend
MySQL :3306（共用）
```

### 2.4 Coder 执行顺序（推荐）

```text
1. 核对一期骨架 → mvn compile + pytest
2. qa-agent/.gitignore → 删误提交的 .venv
3. 125 本地/沙箱：手工 uvicorn 联调 T-0612-04/06
4. 二期工具 E + v1.1 行为 F
5. llm_call_log G + health G
6. WinSW H + RUNBOOK
7. Java Agent 退役 I + 测试 J
8. Co-test §1.4 → round 关单 K → plan Reviewer CLOSED
9. git push（单 feature commit 或按模块 2～3 commit，message 含 plan 路由 ID）
```

### 2.5 估算

| 模块 | 人天 |
|---|---|
| 一期收尾 + RI-16 联调 | 1d |
| 二期工具 + v1.1 行为 | 2d |
| 部署 WinSW + 文档 + 埋点 | 1d |
| Java 退役 + 测试 + round 关单 | 1d |
| **合计** | **~5d** |

### 2.6 Commit 规范

```
feat(qa-agent,plan-2026-06-12-qa-python-upload-first): …
fix(qa-agent,plan-2026-06-12-qa-python-upload-first): …
```

---

## 3. Agent Blocks

> Coder 占 [`TASKS.md`](../TASKS.md) **`plan-2026-06-12-qa-python-upload-first`** → 按 [`CASE-FORMAT.md`](../CASE-FORMAT.md) 追加 **Coder** 块；完工 → **待审**。

---

## 4. 关单检查

- [ ] §1.4 验收 8 条 125 通过
- [ ] §1.2 E～K 工单全部勾选
- [ ] complexity **C-0612-01** 已删（complexity 表无活跃行）
- [ ] round `round-2026-06-12-qa-regression`：**Reviewer(CLOSED)** → `done/` → TASKS 删 DEBUG 行
- [ ] 本 plan：**Reviewer(CLOSED)** → `upgrade_to_settle/done/` → TASKS 删 UPGRADE 行
- [ ] 无 `qa-agent/.venv` 进 git
