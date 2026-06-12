# plan-2026-06-12-qa-agent-tool-recovery — qa-agent 工具召回 + 冷启动降级 + 死循环自愈

> **来源**：`docs/architecture/08-qa-agent-python-service.md` §4 + PM 升级需求（应对更多场景）  
> **状态**：`OPEN` · **类型**：`UPGRADE` · **PM 拍板**：2026-06-12 19:40

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-12-qa-agent-tool-recovery` |
| **类型** | `UPGRADE` |
| **Case 状态** | `OPEN` |
| **标题** | qa-agent 工具召回 + 冷启动降级 + 死循环自愈 |
| **需求锚点** | `docs/requirements/AGENT-REQUIREMENTS.md` §4.3（鲁棒性） |
| **架构锚点** | `docs/architecture/08-qa-agent-python-service.md` §4（ReAct 引擎） |
| **依赖** | 无；可与 plan-streaming-multiturn 并行 |

---

## 1. 任务描述

### 1.1 现状痛点

| 痛点 | 现象 | 业务影响 |
|---|---|---|
| **冷启动崩溃** | GLM key 未配 / 配额耗尽 → `GLM API error 401` → 500 给用户 | 运维期 / GLM 限额时全员不可用 |
| **死循环无解** | Agent 连续 2 步相同 tool + 参数 → 强制 FINAL_ANSWER（粗暴） | 用户答"抱歉未找到"，体感差 |
| **简称/编号 miss** | 「lmz项目」→ `find_project("lmz项目")` → 4 级链全 miss（之前 round-0611 T-0611-20 修过类似） | 用户被迫复述全名 |
| **query_mysql 无 ORDER BY** | 查"前 10 个项目"做不到，只能 `LIMIT 10` | 业务受限 |

### 1.2 方案拍板

#### A. 冷启动降级（3 级 fallback）
- L1：主 GLM 调（`glm-4-flash-250414`）
- L2：主 GLM 失败 → 重试 1 次（exponential backoff 500ms/1s）
- L3：仍失败 → **降级**到 FULLTEXT 检索 + 简单模板答案
  - 「{projectName} 共 {materialCount} 份材料、最近活动 {lastActivity}」
  - 用户看到降级提示 + 标 `confidence_badge='DEGRADED'`
- L4：连 FULLTEXT 都失败 → 503 友好文案（已实现于 round-0612 T-0611-04）

#### B. 死循环自愈
- 检测条件：连续 2 步 `(tool, toolArgs) hash` 完全相同
- 自愈策略（不直接强制结束）：
  1. 第 1 次重复 → 自动改写 toolArgs（`find_project("lmz")` → `find_project("lmz项目", topN=5)` + 变体 1 个）
  2. 第 2 次仍重复 → **主动调 `ask_clarification`**「您是想查 ① lmn 项目还是 ② 林木子项目？」中断循环
  3. 用户回答后注入 toolArgs
- 配置：`.env` `AGENT_LOOP_RECOVERY_MODE=auto-clarify` (default) / `force-end` (旧行为)

#### C. find_project 4 级链升级
- 简称 token 化（`lmz项目` → `lmz`）：`build_search_variants()` 与 v1.1 Java `FindProjectTool.buildSearchVariants` 对齐
- 数字 token 排除（`PRJ-001` 不再拆 `001`）
- 5 级链：
  1. EXACT（精确 code）
  2. FULLTEXT（name + customer_name）
  3. LIKE（含变体）
  4. LLM 兜底（项目数 < 300 时调 LLM 语义匹配）
  5. 客户/业务方人名映射（v2，**本 plan 不做**）

#### D. query_mysql 加 ORDER BY
- `where` 子句支持 `order_by: [{"column":"created_at","direction":"DESC"}]`
- 默认 `ORDER BY id DESC`（按主键倒序，避全表扫）
- 安全：`ORDER BY` 列名走白名单（与 `columns` 共享白名单）

### 1.3 做

- `app/services/llm_retry.py` 新建：3 级降级
- `app/services/degraded_search.py` 新建：FULLTEXT 模板答案
- `app/agent/engine.py` 加 `AGENT_LOOP_RECOVERY_MODE` 检测 + `ask_clarification` 中断
- `qa-agent/app/agent/tools/find_project.py` 加 `build_search_variants`（从 Java `FindProjectTool` 移植）
- `qa-agent/app/agent/tools/query_mysql.py` 加 `order_by` 子句支持
- `app/api/schemas.py` 加 `degraded: bool` 字段到 `AskResponse`
- Java `QaAgentClient` 加 `DegradedFallbackController`（FALLBACK 路径返回 200 + 降级标记）
- 单测：mock GLM 失败 → 走降级；连续 2 步同 tool → 改写参数
- AT 测例：AT-003 `qa-agent-degraded-fallback`（新建）

### 1.4 不做

- ❌ 不接备 LLM（OpenAI/Qwen）（运维期 v1 走降级即可）
- ❌ 不做"客户/业务方人名映射"（v2 计划）
- ❌ 不动 `spring_ai_chat_memory` 表结构（兼容 v1.0）
- ❌ 不做 GLM 限流监控（运维层 Prometheus 接入）

### 1.5 验收

- [ ] GLM key 故意删 → 端点返 200 + `degraded=true` + 模板答案
- [ ] 连续 2 步同 tool → 自动改写 toolArgs（log 可见）
- [ ] 改写后仍重复 → 主动 `ask_clarification` 中断
- [ ] `find_project("lmz项目")` 命中 v1.1 测例同款结果
- [ ] `query_mysql` 支持 `order_by` 字段
- [ ] 单测 + AT-003 PASS

---

## 2. 开发说明

| 路径 | 说明 |
|---|---|
| `qa-agent/app/services/llm_retry.py` | 新建：3 级降级 |
| `qa-agent/app/services/degraded_search.py` | 新建：FULLTEXT 模板 |
| `qa-agent/app/agent/engine.py` | 死循环自愈 + LLM 失败降级 |
| `qa-agent/app/agent/tools/find_project.py` | `build_search_variants` 升级 |
| `qa-agent/app/agent/tools/query_mysql.py` | `order_by` 支持 |
| `qa-agent/app/agent/tools/ask_clarification.py` | 自愈调用扩参 |
| `qa-agent/app/api/schemas.py` | `degraded: bool` |
| `qa-agent/app/config.py` | `AGENT_LOOP_RECOVERY_MODE` 配置 |
| `backend/.../controller/QaController.java` | `degraded` 字段透传 |
| `qa-agent/tests/test_retry.py` | 降级单测 |
| `qa-agent/tests/test_loop_recovery.py` | 死循环单测 |
| `qa-agent/tests/test_find_project_variants.py` | 4 级链单测 |
| `test_task/AT-003-qa-agent-degraded-fallback.md` | 自动化测例 |

---

## 3. Agent Blocks

> 顺序：`Coder` ↔ `Reviewer` → **`Closer`（必）**

<!-- 从 Coder 块开始 -->
