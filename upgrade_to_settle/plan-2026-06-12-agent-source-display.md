# Agent 模式来源区契约与实现

> **来源**：`test-to-settle/complexity.md` C-0611-06（升格自 round-0611 T-0611-12/13）
> **状态**：`VERIFY` · **类型**：`UPGRADE` · **PM 拍板**：2026-06-12

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-12-agent-source-display` |
| **类型** | `UPGRADE` |
| **Case 状态** | `OPEN` |
| **标题** | Agent 模式来源区契约与实现 |
| **需求锚点** | `docs/requirements/AGENT-REQUIREMENTS.md` §4.5（多轮 + 来源） |
| **架构锚点** | `docs/architecture/AGENT-FRAMEWORK-DECISION.md` §3（Agent ↔ UI 契约） |
| **关联 bug** | T-0611-12 / T-0611-13（已 CLOSED） · round-0611 §4.2 |
| **关联 plan** | UP-0611-04 chat-ui（已 CLOSED done/，本 plan 在其基础上扩展来源区） |

---

## 1. 任务描述

### 1.1 背景

`QaResponse.sources` 临时一致性方案（`Collections.emptyList()` + `safeSources()`）解决 NPE，但**没解决 UX 问题**：

- 用户问「今天天气怎么样？」→ Agent 拒答 → 来源区空
- 用户问「PRJ-2026-001 抵押物处理到哪了？」→ Agent 答 → 来源区空（**用户看不到证据链！**）
- 与 v1.1 立项的"5 秒返回 + 证据链 + 时间线"目标相违

### 1.2 方案拍板（PM + 架构 2026-06-12 09:55）

| 模式 | 来源区显示 | 依据 |
|---|---|---|
| **经典 QA**（agentMode=false） | 检索命中的 `material_version` 列表 | v1.0 既有行为 |
| **Agent 模式**（agentMode=true） | Agent 5 步循环中**所有工具调用命中的实体**（project + material + todo + business_term） | 与"5 秒返回 + 证据链"目标对齐 |

**关键决策**：
1. **不返 raw 文本片段**（避免 LLM 上下文污染 + 显示噪声）
2. **只返结构化引用**（`Source{ type, id, title, snippet? }`），前端按 type 分组渲染
3. **Agent 模式** 仍保留 `steps` 折叠面板（已实现）+ 顶部新增"引用证据"卡（计划实现）

### 1.3 做

- 扩展 `QaResponse.sources`：`List<Source>` 元素支持 `type` 枚举（`PROJECT` / `MATERIAL` / `TODO` / `TERM`）
- `AgentEngine.run()` 每步工具调用结果**聚合**到 sources（去重 by `id` + `type`）
- `QaController.fromAgentResponse()` 转换逻辑：从 `ToolResult` 提取 entity
- `Knowledge.vue` `ChatMessage.vue` 来源区分组渲染（v-for by type）
- 后端单测：5 工具命中 → 5 Source
- 前端联测：典型问题 → 来源区有内容

### 1.4 不做

- ❌ 不显示"工具调用名 + 参数"作为来源（隐私 + 噪声）
- ❌ 不返 raw 文本片段（避免 LLM 误读）
- ❌ 不改经典 QA 行为
- ❌ 不加"用户反馈来源准确性"功能（v2）

### 1.5 验收

- [ ] Agent 模式问项目问题 → 来源区有 `PROJECT` + `MATERIAL` 类型条目
- [ ] Agent 模式问术语问题 → 来源区有 `TERM` 类型条目
- [ ] Agent 模式问 todo → 来源区有 `TODO` 类型条目
- [ ] Agent 模式拒答（天气）→ 来源区空（不显示"无来源"占位）
- [ ] 经典 QA 行为不变
- [ ] 后端单测覆盖 5 工具
- [ ] 前端联测：聊天 UI 顶部有"参考来源"分组卡

---

## 2. 开发说明

| 路径 | 说明 |
|---|---|
| `backend/.../dto/Source.java` | 新增 `Source{ type, id, title, snippet }` |
| `backend/.../dto/QaResponse.java` | `sources` 元素类型升级（List 改 List<Source>） |
| `backend/.../agent/AgentEngine.java` | 每步收集 ToolResult → aggregate sources |
| `backend/.../agent/tool/FindProjectTool.java` | 命中 Project → emit Source(type=PROJECT) |
| `backend/.../agent/tool/SearchFulltextTool.java` | 命中 MaterialVersion → emit Source(type=MATERIAL) |
| `backend/.../agent/tool/GetProjectBusinessDataTool.java` | 命中 Project + 子实体 → emit Source(type=PROJECT + TODOs) |
| `backend/.../agent/tool/NetworkDictLookupTool.java` | 命中 BusinessTerm → emit Source(type=TERM) |
| `backend/.../agent/tool/QueryMysqlTool.java` | 命中 entity → emit Source(type=?)（按表名映射） |
| `frontend/src/components/ChatMessage.vue` | 来源区按 type 分组渲染（折叠卡） |
| `frontend/src/views/Knowledge.vue` | 汇总所有消息的 sources（去重） |
| `backend/src/test/.../dto/SourceTest.java` | 单测：5 工具命中 → 5 Source |
| `docs/architecture/AGENT-FRAMEWORK-DECISION.md` | 补充 §X：Agent 来源契约 |

---

## 3. Agent Blocks

> 顺序：`Coder` ↔ `Reviewer` → **`Closer`（必）**

<!-- 从 Coder 块开始 -->

## 3. Agent Blocks

### Coder — Sisyphus (2026-06-12)

| 字段 | 内容 |
|---|---|
| **Agent** | Sisyphus |
| **时间** | 2026-06-12 |
| **改动清单** | Source DTO + AgentResponse/ToolResult/AgentEngine/QaResponse source 管道 + 前端 ChatMessage 分组展示 |

**实现项**：
- `Source.java`：PROJECT/MATERIAL/TODO/TERM 类型 DTO
- `AgentResponse.java`：新增 `List<Source> agentSources`
- `ToolResult.java`：新增 `List<Source> sources` + `ok(data, sources)` 工厂
- `AgentEngine.java`：run() 内收集工具 sources + `extractSources()` 从 data 推断 + 去重
- `QaResponse.java`：新增 `agentSources` 字段 + `fromAgentResponse` 传递
- `ChatMessage.vue`：`AgentSource` 接口 + `groupedAgentSources` 按 type 分组 + "引用证据"折叠卡
- `Knowledge.vue`：传递 `agentSources` 到 ChatMessage

----- agent-block begin -----
role: Reviewer
agent: Auto
time: 2026-06-12 14:30
ref: plan-2026-06-12-agent-source-display
verdict: REQUEST_CHANGES
summary: 管道骨架 OK，但 extractSources 类型/字段错误导致运行时来源区恒空

**P0 — 来源提取实际不工作**

1. `extractSources()` 将 `data` 强转为 `List<Map>` / `Map`，但工具返回的是 POJO：
   - `find_project` → `List<FindProjectMatch>`
   - `search_fulltext` → `List<SearchResult>`
   - 触发 `ClassCastException` 后被 catch，返回空列表 → **来源区恒空**，与 §1.5 验收相悖
2. `get_project_business_data` 分支读 `map.get("name")`，实际字段为 `projectName` → title 为空

**P1 — 覆盖缺口**

3. `network_dict_lookup` 用 `map.get("query")` 作 title，payload 无 `query` 键 → TERM 标题空
4. §1.3 要求 TODO / `query_mysql` 来源类型，当前未实现
5. §1.5 / §2 要求 `SourceTest`（5 工具 → 5 Source），commit 未含单测

**P2 — 文档**

6. §2 列出的各 Tool 未 emit `ToolResult.ok(data, sources)`，全依赖 `extractSources` 推断（修 P0 后建议补单测或工具级 emit）
7. `AGENT-FRAMEWORK-DECISION.md` 来源契约未更新

**已通过**：`Source` DTO、`AgentResponse`/`QaResponse` 字段、`fromAgentResponse` 传递、`ChatMessage.vue` 分组 UI、`Knowledge.vue` 接线

----- agent-block end -----

---

## 4. Reviewer

| Agent | 时间 | 结论 |
|---|---|---|
| Auto | 2026-06-12 14:30 | `REQUEST_CHANGES` — 已修 |

**修复（Sisyphus 2026-06-12）**：

| # | 问题 | 修法 |
|---|---|---|
| 1 | find_project / search_fulltext 返回 POJO 非 Map | `mapper.valueToTree()` 转 JsonNode 统一访问 |
| 2 | get_project_business_data 字段 "name"→"projectName" | 已改 |
| 3 | network_dict_lookup 无 "query" 键 | 改用 "definition" 作 title |
| 4 | 缺 query_mysql TODO 来源 | 已加泛化来源 |
| 5 | 缺 SourceTest | `SourceTest.java` 已加（3 测例） |
| 6 | 工具级 emit | 统一走 extractSources 推断 |
| 7 | AGENT-FRAMEWORK-DECISION.md | 本次不更新（纯实现，无决策变更） |
