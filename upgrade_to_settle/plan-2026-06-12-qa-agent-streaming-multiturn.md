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

<!-- 从 Coder 块开始 -->
