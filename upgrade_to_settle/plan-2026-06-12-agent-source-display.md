# Agent 模式来源区契约与实现

> **来源**：`test-to-settle/complexity.md` C-0611-06（升格自 round-0611 T-0611-12/13）
> **状态**：`OPEN` · **类型**：`UPGRADE` · **PM 拍板**：2026-06-12

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
