# QA Agent Python 微服务 — 架构说明

> **版本**: v1.0 (2026-06-12)  
> **决策**: 智能问答 / 立项 LLM 抽取 **迁出 Java Spring AI**，由 FastAPI 专司；Java 保留 BFF + 档案域。

---

## 1. 为什么改

| 问题 | Java Agent 现状 | Python 方案 |
|---|---|---|
| Prompt 拼合 / JSON 解析 | Spring AI + 手写 ReAct 频繁 parse fallback | OpenAI SDK + 明确 JSON schema 提示 |
| 部署耦合 | 改 Agent 需整包 `mvn package` + 重启 archive.jar | 独立进程，秒级重启 |
| 测试 | 需 Spring 容器 + 真 GLM | `pytest` + mock LLM |
| 多轮 500 | GET/POST 契约不一致（T-0612-04） | BFF 统一 POST，Python 管 session |

**不变**: MySQL 单库、单机 Windows、Caddy 反代、JWT 仍由 Java 校验。

---

## 2. 拓扑

```text
浏览器 ──► Caddy ──► Spring Boot :8080 (/api/*)
                          │
                          ├─ 档案 CRUD / RBAC / 审计
                          │
                          └─ QaAgentClient ──HTTP──► qa-agent :8001
                                                          │
                                                          ├─ GLM API
                                                          └─ MySQL (只读+chat_memory 写)
```

---

## 3. 接口契约

### 3.1 `POST /v1/ask`

**Request**

```json
{ "question": "PRJ-2026-001 剩余金额多少?", "session_id": null }
```

**Response**（与 Java `QaResponse` / `AgentResponse` 对齐）

```json
{
  "answer": "...",
  "agent_mode": true,
  "steps": [{ "iteration": 1, "thought": "...", "tool": "find_project", "toolArgs": "...", "observation": "..." }],
  "tool_calls": 3,
  "project_switch_hint": null,
  "confidence_badge": null,
  "agent_sources": []
}
```

### 3.2 `POST /v1/turn/{session_id}`

同 `/v1/ask`，`session_id` 必填；读写表 `spring_ai_chat_memory`。

### 3.3 `POST /v1/extract/project-fields`

```json
{ "material_version_id": 123 }
```

**Response**

```json
{
  "success": true,
  "data": { "projectName": "...", "amount": 5000, "customerName": "..." },
  "failure_type": null,
  "message": null
}
```

---

## 4. ReAct 引擎（Python）

- **System prompt**: 业务边界 + 工具列表 + JSON 输出格式（见 `qa-agent/app/agent/prompts.py`）
- **Parser**: 提取 markdown code block 内 JSON；失败 → `FINAL_ANSWER` 礼貌拒答，**不**把 raw text 塞进 thought
- **工具**: 注册表模式；参数 Pydantic 校验
- **安全**: `query_mysql` 表白名单 6 张；`limit` ≤ 1000

---

## 5. 配置（共用 `config/config.json`）

**与 Java `ConfigJsonLoader` 同文件、同路径规则**，不单独维护 `qa-agent/.env`。

| JSON 路径 | qa-agent | Java |
|---|---|---|
| `glm.*` | LLM | `app.glm.*` |
| `database.*` | MySQL | `spring.datasource.*` |
| `storage.*` | archive 磁盘根 | `app.storage.*` |
| `archive.networkDict.*` | 网络字典工具 | `archive.network-dict.*` |
| `archive.queryMysql.*` | SQL 上限 | `archive.query-mysql.*` |
| `qaAgent.host` / `port` / `maxIterations` | uvicorn 绑定 / ReAct | `app.qa-agent.base-url` |

**路径解析**（Python `app/config_loader.py` 与 Java 一致）：

1. 环境变量 `CONFIG_JSON_PATH`
2. `D:/archive/config/config.json`
3. `./config/config.json` · `../config/config.json`
4. 仓库根 `config/config.json`（开发）

可选 env **仅覆盖**单项（如 `GLM_API_KEY`），生产推荐只改 config.json。

Java：`application.yml` → `app.qa-agent.base-url: http://${app.qa-agent.host}:${app.qa-agent.port}`

---

## 6. 立项上传（RI-16）与 QA 关系

- **上传 / 存盘 / Tika 解析**: 仍 Java（`MaterialVersionService`）
- **字段抽取**: Python `/v1/extract/project-fields`（读 `material_version.parsed_text`）
- **staging-upload**: Java 创建 `DRAFT-*` 项目链，前端带 `draftProjectId` 最终 update

---

## 7. 迁移与降级

| 开关 | 行为 |
|---|---|
| `app.qa-agent.enabled=true` | QA + extract 走 Python |
| `app.qa-agent.enabled=false` + `spring.ai.agent.enabled=true` | 回 Java AgentEngine |
| 两者 false | QaController legacy FULLTEXT 路径 |

**后续（见 plan 二期）**: Java `agent/` 包退役 — 工单在 [`plan-2026-06-12-qa-python-upload-first`](../../upgrade_to_settle/plan-2026-06-12-qa-python-upload-first.md) §1.2 I。

---

## 9. 二期工具（Coder 工单 — 与 Java v1.1 对齐）

| 工具 | 状态 | 参考 |
|---|---|---|
| `find_project` | MVP ✅ | `FindProjectTool.java` |
| `search_fulltext` | MVP ✅ | `SearchFulltextTool.java` |
| `query_mysql` | MVP ✅ | `QueryMysqlTool.java` |
| `llm_summarize` | MVP ✅ | `LlmSummarizeTool.java` |
| `get_project_business_data` | 二期 | `GetProjectBusinessDataTool.java` |
| `ask_clarification` | 二期 | `AskClarificationTool.java` |
| `archive_fs` | 二期 | [`07-archive-fs-agent-tools.md`](07-archive-fs-agent-tools.md) |
| `network_dict_lookup` | 二期 | `NetworkDictLookupTool.java` |

v1.1 字段：`project_switch_hint` · `confidence_badge` · `agent_sources` — 二期在 `engine.py` 填充。

WinSW / `llm_call_log` / health indicator — 见 plan §1.2 G～H。

---

## 7. Java Agent 退役说明

自 v1.1 起，Java `AgentEngine` / `AgentConfig` 已标注 `@Deprecated`，**默认关闭**（`spring.ai.agent.enabled=false`）。

| 组件 | 状态 | 说明 |
|------|------|------|
| `AgentEngine.java` | `@Deprecated` | 保留仅用于降级路径（显式开启 `enabled=true`） |
| `AgentConfig.java` | `@Deprecated` | 同上 |
| `AgentIntegrationTest.java` | `@Deprecated` | 降级回归，新功能走 Python qa-agent |
| `GLMChatModel.java` | 保留 | 由 Python 侧 GLM 调用取代 |

**保留的 Java 降级组件**：
- `QaController.legacyAsk()` — FULLTEXT 检索（无 LLM）
- `MultiTurnController` 第 3 路径 — 503 友好文案
- `AgentEngine` — 仅在 `spring.ai.agent.enabled=true` 时加载

---

## 8. 部署 SOP（125）

> **阶段**：验收期 **手工启动**；WinSW / 开机自启 **后续一体化**时再上（`deploy/winsw/qa-agent.xml` 已预留）。

**目录约定**（与 Java 后端一致）：

| 路径 | 用途 |
|---|---|
| `D:\projects-online` | Git 源码；`qa-agent/` 代码 + 本地 `.venv`（不进 Git） |
| `D:\archive\config\config.json` | 生产配置（GLM、MySQL、storage、qaAgent） |
| `D:\archive\files` / `parsed` / `logs` | 材料、解析结果、服务日志 |

```powershell
cd D:\projects-online
git pull

cd qa-agent
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt

# 确认 D:\archive\config\config.json 已就绪（与 backend 同文件）
cd D:\projects-online
.\deploy\scripts\start-qa-agent.ps1
```

WinSW（后续）：[`deploy/winsw/qa-agent.xml`](../../deploy/winsw/qa-agent.xml) · [`register-services.bat`](../../deploy/scripts/register-services.bat)。

---

*关联: [`AGENT-FRAMEWORK-DECISION.md`](AGENT-FRAMEWORK-DECISION.md) §1.2 — Java Spring AI 保留为历史决策，本文件为 v1.1+ 增量 override。*
