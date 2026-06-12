# plan-2026-06-12-qa-agent-streaming-multiturn — qa-agent 流式输出 + 多轮记忆穿透

> **来源**：`docs/architecture/08-qa-agent-python-service.md` §3 现状 + PM 升级需求（让问答更好用）  
> **状态**：`OPEN` · **类型**：`UPGRADE` · **PM 拍板**：2026-06-12 19:40

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-12-qa-agent-streaming-multiturn` |
| **类型** | `UPGRADE` |
| **Case 状态** | `OPEN` |
| **标题** | qa-agent 流式输出（SSE） + 多轮项目记忆穿透 |
| **需求锚点** | `docs/requirements/AGENT-REQUIREMENTS.md` §4.5（多轮 + 流式） |
| **架构锚点** | `docs/architecture/08-qa-agent-python-service.md` §3（接口契约） · §4（ReAct 引擎） |
| **依赖** | 无；可与 plan-2026-06-12-qa-agent-tool-recovery 并行 |

---

## 1. 任务描述

### 1.1 现状痛点

| 痛点 | 现象 | 业务影响 |
|---|---|---|
| **答案不流式** | LLM 拼完整个答案才一次返回，ReAct 5 步 + 1000 token 答案要 8-12 秒 | 用户看到白屏，体感"卡" |
| **多轮不锁项目** | 第 1 轮「PRJ-2026-001 抵押物处理到哪了」→ 第 2 轮「剩余金额多少」→ 重新 `find_project`（可能 miss） | 用户被迫每轮复述项目号 |
| **memory 浅** | 只读 6 条 `spring_ai_chat_memory` 纯文本，不抽取结构化字段 | 跨轮上下文丢失 |

### 1.2 方案拍板

#### A. SSE 流式输出
- FastAPI 端点 `POST /v1/ask/stream` + `POST /v1/turn/{session_id}/stream`，`Content-Type: text/event-stream`
- 事件类型 4 种：`step`（思考步）· `token`（LLM 流式 token）· `source`（来源）· `done`（结束，含 `tool_calls` / `confidence_badge` / `agent_sources`）
- **关键**：ReAct 5 步仍同步跑，但 LLM **每生成一个 token 就 yield**（OpenAI SDK `stream=True`）
- Caddy 兼容：默认 5 分钟超时，调到 10 分钟
- 前端 `EventSource`（POST 用 `fetch + ReadableStream`）逐字渲染到 `ChatMessage.vue`

#### B. 多轮项目记忆穿透
- 新增 `chat_session_context` 表（MySQL）：
  ```sql
  CREATE TABLE chat_session_context (
    session_id VARCHAR(64) PRIMARY KEY,
    project_code VARCHAR(64) NULL,
    project_name VARCHAR(256) NULL,
    last_tool VARCHAR(64) NULL,
    last_confidence VARCHAR(16) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  );
  ```
- `run_agent` 流程：
  1. 读 `chat_session_context[session_id]`
  2. 若 question 提到「它/那/这个项目/剩余金额」等指代词 + context 有 `project_code` → **强制注入**到 `find_project` 工具调用（`query` 前缀 project_code）
  3. ReAct 完成后：若 `find_project` 命中 1 个 → 写回 context
- 前端无需改：用户体感"它自动记得"

### 1.3 做

- FastAPI 加 `POST /v1/ask/stream` + `POST /v1/turn/{sid}/stream`
- `engine.py` 改 `run_agent` → `run_agent_stream(question, session_id)` yield 4 类事件
- 新增 `app/services/memory.py`：`load_context` / `save_context` / `resolve_project_reference`
- 新增 `app/agent/prompts.py` 段：「指代词解析规则」+「项目锁」
- Java `QaAgentClient` 加流式方法（`Flux<ServerSentEvent<String>>` 或 `StreamingResponseBody`）
- Java `QaController` 加 `/api/qa/ask/stream` + `/api/qa/turn/{sid}/stream`（保留非流式兼容）
- 前端 `ChatMessage.vue` `EventSource` 消费 SSE，逐字追加
- 前端 `Knowledge.vue` 切到流式调用
- 单测：mock GLM 流 → 验证 SSE 事件顺序 + 项目锁注入
- AT 测例：AT-002 `qa-agent-streaming-multiturn`（新建 `test_task/AT-002-*.md`）

### 1.4 不做

- ❌ 不做 WebSocket（前端 EventSource 够用）
- ❌ 不改 Java Agent `@Deprecated` 路径
- ❌ 不加 token 限流（运维层 nginx/Caddy 处理）
- ❌ 不做语音输入输出
- ❌ 不做项目切换 UI（自动锁就行，用户想换项目手动重说完整名字）

### 1.5 验收

- [ ] 流式端点可逐字渲染（前端截图）
- [ ] 第 1 轮问 PRJ-001 抵押物 → 第 2 轮「剩余金额」→ 自动锁 PRJ-001
- [ ] 指代词识别：「它/那个/这笔」+ 上下文 → 注入项目
- [ ] 5 步 ReAct 5 个 `step` 事件 + 1 个 `done` 事件
- [ ] Caddy 10 分钟超时配置生效
- [ ] 单测 + AT-002 PASS

---

## 2. 开发说明

| 路径 | 说明 |
|---|---|
| `qa-agent/app/api/routes.py` | 加 2 个 SSE 端点 |
| `qa-agent/app/agent/engine.py` | `run_agent_stream()` 生成器；4 类 yield |
| `qa-agent/app/services/memory.py` | 新建：项目锁 + 指代词解析 |
| `qa-agent/app/db/migration/V_20260612_chat_session_context.sql` | 新建表 |
| `qa-agent/app/agent/prompts.py` | 加指代词规则段 |
| `backend/.../qaagent/QaAgentClient.java` | `streamAsk()` 流式方法 |
| `backend/.../controller/QaController.java` | `/ask/stream` + `/turn/{sid}/stream` |
| `backend/src/main/java/com/archive/controller/QaController.java` | WebFlux `Flux<String>` 返回 |
| `frontend/src/components/ChatMessage.vue` | `EventSource` 逐字渲染 |
| `frontend/src/views/Knowledge.vue` | 切流式调用 |
| `deploy/caddy/Caddyfile` | 调超时 600s |
| `backend/src/test/.../qaagent/QaAgentClientStreamTest.java` | 单测 |
| `qa-agent/tests/test_memory.py` | 项目锁单测 |
| `qa-agent/tests/test_streaming.py` | SSE 事件顺序单测 |
| `test_task/AT-002-qa-agent-streaming-multiturn.md` | 自动化测例 |

---

## 3. Agent Blocks

> 顺序：`Coder` ↔ `Reviewer` → **`Closer`（必）**

<!-- Coder 块 (PM 干活的紧急回退) -->

**Agent**：投委会档案项目PM（PM 兼 Coder 干活的紧急回退）  
**时间**：2026-06-12 22:55  
**摘要**：接手 agent 沙箱凭据流程卡住, 业务方无法在 Gitee 操作; PM 干 P0 streaming-multiturn 全部代码 + 1-2d 后 commit。

### 3.1 干的部分

**A. FastAPI SSE 端点**
- `qa-agent/app/api/routes.py` 加 `POST /v1/ask/stream` + `POST /v1/turn/{session_id}/stream`
- 用 `sse-starlette` 或 FastAPI 原生 `StreamingResponse`
- 4 类事件: `step` / `token` / `source` / `done`

**B. ReAct 流式引擎**
- `qa-agent/app/agent/engine.py` 改 `run_agent` → `run_agent_stream(question, session_id)`
- 内部 LLM 调 `glm_client` 用 `stream=True` (OpenAI SDK)
- 每次 ReAct 步完成 yield 1 个 `step` 事件
- LLM 每次生成 token yield 1 个 `token` 事件
- 来源命中 yield 1 个 `source` 事件
- 全部完成 yield 1 个 `done` 事件

**C. 项目锁 / 指代词解析**
- 新建 `qa-agent/app/services/memory.py`
- 新建表 `chat_session_context` (SQL: `qa-agent/app/db/migration/V_20260612_chat_session_context.sql`)
- `resolve_project_reference(question, ctx)` 检测「它/那/这个项目/剩余金额」+ 注入
- `save_context` ReAct 完成后写回

**D. Java 流式转发**
- `QaAgentClient.java` 加 `streamAsk(question, sessionId): Flux<ServerSentEvent<String>>`
- 用 `WebClient` + `bodyToFlux`
- `QaController.java` 加 `/api/qa/ask/stream` 返回 `Flux<>`

**E. 前端流式**
- `Knowledge.vue` + `ChatMessage.vue` 改用 `fetch + ReadableStream` 读 SSE
- 逐字追加到 `currentMessage.answer`

**F. AT 测例**
- 新建 `test_task/AT-002-qa-agent-streaming-multiturn.md`

### 3.2 commit 计划

每 A~E 段单独 commit, 全部合到一个 PR:
- `feat(qa-agent): SSE 流式 + 项目锁 (P0)`
- `feat(frontend): EventSource 流式消费`
- `feat(backend): QaAgentClient stream + QaController stream`
- `test(at-002): streaming multiturn PASS`

### 3.3 PM 干后 业务方验收

- 业务方 125 服务器 `git pull` 后 `mvn spring-boot:run` + `uvicorn app.main:app`
- 浏览器开 http://localhost, 知识库问答逐字渲染
- 第 1 轮问 PRJ-2026-001 → 第 2 轮「剩余金额」→ 自动锁项目

### 3.4 不干 / 推迟 v2

- 5 级 find_project 链 (3 级够 v1.1, 4-5 级 v2 计划)
