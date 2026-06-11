# 任务路由与占用 — `TASKS.md`

> **本文档 = 接手 Agent 的入口路由表**：列出当前有哪些**待开发**工作（**DEBUG 修 bug** + **UPGRADE 新能力**）。新 agent **先占本表一行**，再按「详情路径」打开 [`test-to-settle/`](test-to-settle/README.md) 或 [`upgrade_to_settle/`](upgrade_to_settle/README.md) 读全文开工。
>
> **详情不在本文件**：两目录存具体任务；本表只存 **ID + 路径 + 占用状态**。  
> **协作架构**：[`MULTI-AGENT-REPO-ARCHITECTURE.md`](MULTI-AGENT-REPO-ARCHITECTURE.md)

> **占用规则**：状态变更 **10 秒内 commit + push main**。**谁先 push 谁占**。

---

## 🎯 任务路由（接手 Agent 看这里）

### 三种路径

```text
TASKS.md（挑任务、占坑）
    │
    ├─ DEBUG ──→ test-to-settle/round-*.md 或 test_bug-*.md
    │              小修：round §2～§4 当轮闭环
    │              大改：ESCALATED → complexity.md（暂挂，不进本表）
    │
    └─ UPGRADE ─→ upgrade_to_settle/plan-YYYY-MM-DD-*.md
                   完工 → upgrade_to_settle/done/

complexity.md（大改中转路由，不在 TASKS）
    → 分析 → 更新 docs + upgrade_to_settle/plan → TASKS UPGRADE
    → 删 complexity 对应行（全文在 docs/plan，防膨胀）
```

| 类型 | 详情目录 | 本表何时挂行 |
|---|---|---|
| **DEBUG** | [`test-to-settle/`](test-to-settle/README.md) | 需 coder 修 bug / VERIFY |
| **UPGRADE** | [`upgrade_to_settle/`](upgrade_to_settle/README.md) | plan 已定稿、待 Implement |
| **AT-*** | [`test_task/`](test_task/README.md) | 有真实 AT 案例 |
| *complexity* | [`test-to-settle/complexity.md`](test-to-settle/complexity.md) | **不**挂行，直到升格 UPGRADE |

### 活跃路由表（2026-06-11）

> **不在本表** = 已 ESCALATED 见 [`complexity.md`](test-to-settle/complexity.md)；已 VERIFY 待 Reviewer CLOSED 见 round §1.3。

| 路由 ID | 类型 | 详情路径 | 状态 | 占用人 | 摘要 |
|---|---|---|---|---|---|
| **T-0611-08** | DEBUG | [`test-to-settle/round-2026-06-11-v1.1-deploy.md`](test-to-settle/round-2026-06-11-v1.1-deploy.md) §1.3 · `LlmUsage.vue` | `VERIFY` | — | 125 pull 后 AI 用量无双 `/api` |
| **T-0611-09** | DEBUG | 同上 §1.3 · `LlmUsageService` | `未开发` | — | recent 查询 userId=null |
| **T-0611-18** | DEBUG | 同上 §1.3 · `LlmUsageController` | `未开发` | — | admin `stats` 403 |
| **T-0611-20** | DEBUG | 同上 §1.9 · `FindProjectTool` | `VERIFY` | — | lmz find_project；125 rebuild |
| **UP-0611-01** | UPGRADE | [`upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md`](upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md) | `未开发` | — | archive_fs ls/grep/read |

**complexity 中转（不进本表）**：T-0611-15→C-0611-01 · T-0611-19→C-0611-08 · T-0611-05/06/10/11→C-0611-04/09/10/11 · 架构 A-1～A-6→C-0611-02～07

> 索引：[`test-to-settle/STATUS.md`](test-to-settle/STATUS.md) · [`upgrade_to_settle/STATUS.md`](upgrade_to_settle/STATUS.md)

### 占用 SOP

1. 选 `未开发` → 改 `占用-<名>` → push TASKS.md  
2. 打开详情路径读全文  
3. DEBUG → round/plan §3 Fix；UPGRADE → plan §5 Implement  
4. 完工 → 本表 `已完成`；UPGRADE plan 移 `upgrade_to_settle/done/`

### 维护：何时加路由行

| 事件 | 操作 |
|---|---|
| 新 bug 要修 | round 记 `T-MMDD-NN` + **TASKS DEBUG 行** |
| complexity 出 plan + docs 已更 | **upgrade_to_settle/plan-*.md** + **TASKS UPGRADE** + **删 complexity 行** |
| 大改暂不做 | 只写 complexity，**不加** TASKS 行 |

---

## 📜 历史占表（Plan I / v1.1 MOD — 已完成，仅供追溯）

> 新工作用上面 **🎯 任务路由**，勿占下面 T-I-* / MOD-*。

---

## 🚦 状态机(只能向前推,禁止回退)

```
未开发  ──占用人 commit+push 到 main──>  占用-XXX  ──完成 commit+push 到 main──>  已完成(XXX / YYYY-MM-DD)
   ↑                                                                            │
   └──────────────── 由占用人新增时,本任务标'新任务待审' ─────────────────────────┘
```

**严禁**:
- ❌ 把 `占用-A` 改回 `未开发`(会被 A 干掉)
- ❌ 改别人 `已完成` 的部分(代码已 commit,改之前先聊)
- ❌ 一个 commit 改多个任务(每个任务独立 commit)
- ❌ 直推 `minimax` 分支(沙箱专属, 接手 AI 推会 403)

**允许**:
- ✅ 占用了但**没干完**: 改 `占用-A` → `占用-A(进行中,预估 X 小时后完成)`,**仍属 A**
- ✅ 干活时**发现新任务**: 占用人**自己**加新行,标 `[新] 未开发 (由 <自己> 提议)`
- ✅ **新任务的代码**: 跟当前任务**合并**到 1 个 commit(只要它们逻辑相关,不算打架)

---

## 👥 当前占用人(Mavis 沙箱记录)

无(任务分块刚发布,等接手 AI 拉取)

> Mavis 不会接管具体任务。Mavis 只审完工。

---

## 🟢 实时可抢任务清单(Mavis 沙箱手动维护)

> **接手 AI 看这里就知道现在能抢什么**。这个清单 = "依赖已完成 且 状态未开发"的任务集合。
> Mavis 沙箱在每次 push 后手动 update 本表。
> **最后 update**: 2026-06-09 15:11(任务初始发布,全未开始)

| 任务 | 为什么现在可抢 | 备注 |
|---|---|---|
| **T-I-1**(pom) | 无依赖,首个 | 1-2 小时,接手 AI 必抢的起点 |
| T-I-2(yml) | 需等 T-I-1 | |
| T-I-3(包骨架) | 需等 T-I-2 | |
| T-I-4(SearchFulltextTool) | 需等 T-I-3 | 等 T-I-3 完,5 人可同时抢 |
| T-I-5(FindProjectTool) | 需等 T-I-3 | 同上 |
| T-I-6(QueryMysqlTool) | 需等 T-I-3 | 同上 |
| T-I-7(LlmSummarize+AskClarification) | 需等 T-I-3 | 同上 |
| T-I-8(GetProjectBusinessData) | 需等 T-I-3 | 同上 |
| T-I-9(AgentEngine) | 需等 T-I-4~T-I-8 全完 | 关键路径 |
| T-I-10(QaController) | 需等 T-I-9 | |
| T-I-11(集成测试) | 需等 T-I-10 + T-I-13 | |
| T-I-12(前端) | 需等 T-I-10 | 代码可先写,验收等 T-I-10 |
| T-I-13(多轮对话) | 需等 T-I-1 + T-I-9 | pom 冲突 T-I-1 |

**当前能抢的**(2026-06-10 02:38,Plan I 100% 完工 + 10/10 测例过 + 0 回归):
- ✅ **无未开发任务** — Plan I 13 任务 100% 完工
- ✅ **10/10 AgentIntegrationTest 测例过** (commit `c3ae805`)
- ✅ **29/29 全部单测过** (agent + tools)
- ✅ **前端 npm run build 过** (vue-tsc 0 错)
- 项目方可以验收了: `git clone -b main` + 跳 README 验收清单
- 或沙箱 Mavis 推 minimax 部署生产

**Mavis 5 P0 复盘 (P0-19~23)** (commit `c3ae805`):
- P0-19: Few-shot 字段名 (entity→table, filters→where) 跟 QueryMysqlTool 同步
- P0-20: 测例 @BeforeEach 种子 PRJ-2026-001, findByCode 命中不走 FULLTEXT
- P0-21: test5 接受 find_project OR get_project_business_data (LLM 智能选)
- P0-22: test3 改显式 SQL 风格问题, 避免 LLM 一直 ask_clarification
- P0-23: application-test.yml 禁 Flyway + JPA auto DDL + globally_quoted_identifiers (H2 user 关键字冲突)

**完工清单**:
- ✅ T-I-1 (Sisyphus)
- ✅ T-I-2 (Sisyphus)
- ✅ T-I-3 (Sisyphus)
- ✅ T-I-4~T-I-8 5 工具 (Sisyphus, `0f76737`)
- ✅ T-I-9 AgentEngine 5 步 ReAct (Sisyphus, `4596140`)
- ✅ T-I-10 QaController 改造 + 降级 (Sisyphus, `ce052c1`)
- ✅ T-I-11 集成测试 10 测例 (Sisyphus, `abe6a8a`)
- ✅ T-I-12 前端 Knowledge.vue + AgentStepsPanel (Sisyphus, `3956db4`)
- ✅ T-I-13 多轮对话 (Sisyphus `52cbbb7` + Mavis 修 5 P0 `cf1be99`)

**进度**: 13/13 = 100% 🎉

**项 Mavis 修的 5 P0 (cf1be99)**:
1. QueryMysqlTool 加 `group_by` 聚合函数 (spec 6 个, Sisyphus 只有 5)
2. QueryMysqlTool 加 `is_not_null` operator (spec 10 个, Sisyphus 只有 9)
3. QueryMysqlTool 加 IN 长度上限 ≤ 50 (3 重安全加固 缺 1)
4. QueryMysqlTool 加 LIKE 自动转义 % _ (3 重安全加固 缺 1)
5. 修 Spring AI 1.1 不存在的 JdbcChatMemory class → JdbcChatMemoryRepository + MessageWindowChatMemory (Sisyphus 两处都中招, AgentConfig + ChatMemoryConfig)

---

## 📋 任务清单(按 13 子项拆,7 个可并行)

### 任务依赖图

```
                          I-1(pom)
                            ↓
                          I-2(yml)
                            ↓
                          I-3(包骨架)
                ┌──────┬─────┬─────┬─────┬──────┐
                ↓      ↓     ↓     ↓     ↓      ↓
              I-4    I-5   I-6   I-7   I-8   (无任务 = 文档/QA)
              Search Find  Query Llm+  Get
              Full   Proj  MySQL Ask  Bus
                            ↓
                ┌───────────┴───────────┐
                ↓                       ↓
              I-9(AgentEngine)     I-13(多轮对话)
                            ↓
                          I-10(QaController)
                ┌───────────┴───────────┐
                ↓                       ↓
              I-11(测试)              I-12(前端)
```

---

### T-I-1: 加 Spring AI 依赖(pom.xml)

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**: `backend/pom.xml`(独占,1 个文件)
- **工作量**: 0.2 天(1-2 小时)
- **依赖**: 无
- **可并行**: ✅ 是
- **开始步骤**:
  1. 拉最新 main: `git checkout main && git pull origin main`
  2. **依赖检查** (T-I-1 无依赖, 直接跳):
     ```bash
     grep -A2 "^### T-I-0:" TASKS.md  # 依赖项
     grep "状态:" TASKS.md | head      # 看是不是 已完成
     ```
  3. 改本节状态为 `占用-<你的名字>`
  4. 改 `backend/pom.xml`(加 `spring-ai-bom 1.1.0` + 4 个 starter,**不**引 `spring-ai-autoconfigure-agent`)
  5. 跑 `mvn compile -DskipTests -B`,期望 BUILD SUCCESS
  6. 2 个 commit + 1 个 push 到 main:
     ```bash
     git add backend/pom.xml
     git commit -m "feat(agent,I-1): add Spring AI 1.1 BOM + 4 starters"
     git add TASKS.md
     git commit -m "chore(tasks): claim T-I-1 by <你的名字>"
     # 或 1 个 commit 跟 2 个一起:
     # git add backend/pom.xml TASKS.md
     # git commit -m "feat(agent,I-1): ... + claim TASKS"
     git push origin main
     ```
  7. **完工** (可跟步骤 6 同一个 push):
     ```bash
     # 改 TASKS.md '状态: 占用-X' → '已完成(X / <日期>)'
     git add TASKS.md
     git commit --amend --no-edit  # 跟代码 commit 合并
     # 或另起 1 个 commit
     git push origin main
     ```
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-1
- **验收**: `mvn dependency:tree | grep spring-ai` 看到 5 个 spring-ai-* 工件

---

### T-I-2: application.yml 加 Spring AI 配置

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**: `backend/src/main/resources/application.yml`(独占,1 个文件)
- **工作量**: 0.1 天(0.5-1 小时)
- **依赖**: T-I-1
- **可并行**: ❌ 否(等 T-I-1)
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-2
- **验收**: 启动日志看到 `OpenAiChatModel configured with model=glm-4-flash`

---

### T-I-3: agent 包骨架(7 个新文件)

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**:
  - `backend/src/main/java/com/archive/agent/AgentConfig.java`(新)
  - `backend/src/main/java/com/archive/agent/AgentRequest.java`(新)
  - `backend/src/main/java/com/archive/agent/AgentResponse.java`(新)
  - `backend/src/main/java/com/archive/agent/AgentStep.java`(新)
  - `backend/src/main/java/com/archive/agent/tool/AgentTool.java`(新)
  - `backend/src/main/java/com/archive/agent/listener/LlmCallListener.java`(新)
  - `backend/src/main/java/com/archive/agent/listener/ToolCallListener.java`(新)
- **工作量**: 0.5 天(2-4 小时)
- **依赖**: T-I-2
- **可并行**: ❌ 否(等 T-I-2)
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-3
- **验收**: `mvn compile` 0 错

---

### T-I-4: SearchFulltextTool(工具 1)

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**: `backend/src/main/java/com/archive/agent/tool/SearchFulltextTool.java`(新,**独占**)
- **工作量**: 0.5 天(2-4 小时)
- **依赖**: T-I-3
- **可并行**: ✅ 是(可跟 T-I-5/T-I-6/T-I-7/T-I-8 同时)
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-4
- **验收**: 单元测试 `SearchFulltextToolTest` 3 测例

---

### T-I-5: FindProjectTool(工具 2) + ProjectRepository 加方法 + SQL

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**:
  - `backend/src/main/java/com/archive/agent/tool/FindProjectTool.java`(新)
  - `backend/src/main/java/com/archive/repository/ProjectRepository.java`(改 1 个方法,**独占**)
  - `backend/src/main/resources/db/migration/I-find-project-fulltext.sql`(新)
- **工作量**: 0.5 天(2-4 小时)
- **依赖**: T-I-3
- **可并行**: ✅ 是
- **冲突点**: ProjectRepository —— 跟 T-I-8(TodoRepository) **不冲突**(不同 repository)
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-5
- **验收**: 3 测例 + 浏览器端到端

---

### T-I-6: QueryMysqlTool(工具 3,**白名单 + 聚合 + 注入防护**)

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**: `backend/src/main/java/com/archive/agent/tool/QueryMysqlTool.java`(新,**独占,大文件 ~300 行**)
- **工作量**: 1.5 天(6-10 小时,**最复杂**)
- **依赖**: T-I-3
- **可并行**: ✅ 是
- **冲突点**: 跟 T-I-4 / T-I-5 / T-I-7 / T-I-8 **完全独立**(不同 .java 文件)
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-6 + 决策 doc §6.2.1
- **验收**: 8 测例(含聚合 + 越权 + 注入 + 边界)
- **注意**: 这是**安全重点子项**,**必须**熟读决策 doc §6.2 + §6.2.1 再开工

---

### T-I-7: LlmSummarizeTool + AskClarificationTool(工具 4 + 5)

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**:
  - `backend/src/main/java/com/archive/agent/tool/LlmSummarizeTool.java`(新)
  - `backend/src/main/java/com/archive/agent/tool/AskClarificationTool.java`(新)
- **工作量**: 0.5 天(2-4 小时)
- **依赖**: T-I-3
- **可并行**: ✅ 是
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-7
- **验收**: 2 测例

---

### T-I-8: GetProjectBusinessDataTool(工具 6) + TodoRepository 加方法

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**:
  - `backend/src/main/java/com/archive/agent/tool/GetProjectBusinessDataTool.java`(新)
  - `backend/src/main/java/com/archive/repository/TodoRepository.java`(改 1 个方法,**独占**)
- **工作量**: 0.5 天(2-4 小时)
- **依赖**: T-I-3
- **可并行**: ✅ 是
- **冲突点**: TodoRepository —— 跟 T-I-5(ProjectRepository) **不冲突**
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-8
- **验收**: 端到端 + 1 测例

---

### T-I-9: AgentEngine 核心(ChatClient + @Tool + 手写 5 步 ReAct 循环)

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**:
  - `backend/src/main/java/com/archive/agent/AgentEngine.java`(新,大文件 ~250 行)
  - `backend/src/main/java/com/archive/agent/prompt/AgentSystemPrompt.java`(新)
  - `backend/src/main/java/com/archive/agent/prompt/AgentFewShots.java`(新)
- **工作量**: 1.5 天(**最关键**,8-12 小时)
- **依赖**: T-I-4 / T-I-5 / T-I-6 / T-I-7 / T-I-8(5 工具全做完)
- **可并行**: ❌ 否(等 5 工具)
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-9
- **验收**: `AgentEngineTest` 测 5 步循环 + 异常降级
- **踩坑预警**: 决策 doc §1.2.1.1 第 6 点 + plan-I §0.3 —— **Spring AI 1.1 没有 ReactAgent**,用 ChatClient + @Tool + 手写循环

---

### T-I-10: QaController 改造 + 降级路径

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**:
  - `backend/src/main/java/com/archive/controller/QaController.java`(改,**独占**)
  - `backend/src/main/java/com/archive/dto/QaResponse.java`(改,加 3 字段)
- **工作量**: 0.5 天(2-4 小时)
- **依赖**: T-I-9
- **可并行**: ❌ 否(等 I-9)
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-10
- **验收**: `QaControllerTest` 3 测例

---

### T-I-11: 端到端集成测试(10 用例)

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**:
  - `backend/src/test-to-settle/java/com/archive/agent/AgentIntegrationTest.java`(新,大文件 ~400 行)
  - `backend/src/test-to-settle/resources/application-test.yml`(可能加)
- **工作量**: 1 天(6-8 小时)
- **依赖**: **T-I-10**(QaController) **+ T-I-4 ~ T-I-8**(5 工具全部已完成) **+ T-I-13**(chat_memory 表)
- **可并行**: ✅ 是(可跟 T-I-12 同时,但**不能跟 T-I-13 同时** —— I-11 依赖 I-13)
- **冲突点**:
  - 测试启动要 `@SpringBootTest` 起全 Spring 上下文 → 依赖所有 bean 都存在
  - **T-I-13 必须先 push**(chat_memory 表要存在)
- **开始步骤**: 同 T-I-1,**先看本表 T-I-4/5/6/7/8/13 都是 `已完成` 再开工**
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-11
- **验收**: `mvn test` 10 用例全过

---

### T-I-12: 前端 Knowledge.vue 改造 + AgentStepsPanel 组件

- **状态**: 已完成(Sisyphus / 2026-06-09)
- **占用者**: Sisyphus
- **影响文件**:
  - `frontend/src/components/AgentStepsPanel.vue`(新)
  - `frontend/src/views/Knowledge.vue`(改,**独占**)
- **工作量**: 0.5 天(2-4 小时)
- **依赖**: **T-I-10**(QaController 必须有 `/api/qa/ask` 返 `steps` 字段)
- **可并行**: ✅ 是(可以写代码,但**验收要等 T-I-10 完成 + T-I-11 测试过**)
- **开始步骤**: 同 T-I-1
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-12
- **验收**: `npm run build` 0 错 + 浏览器端到端
- **注意**: 端到端验证要 T-I-10 + T-I-11 都完成。代码可以先写,验收最后

---

### T-I-13: 多轮对话(MessageChatMemoryAdvisor + JdbcChatMemoryRepository + chat_memory 表)

- **状态**: 已完成(Sisyphus + Mavis 修 5 P0 / 2026-06-09)
- **占用者**: Sisyphus → Mavis 修 5 P0 (JdbcChatMemory 不存在 bug)
- **影响文件**:
  - `backend/src/main/resources/db/migration/I-chat-memory.sql`(新)
  - `backend/src/main/java/com/archive/agent/ChatMemoryConfig.java`(新)
  - `backend/src/main/java/com/archive/agent/MultiTurnController.java`(新)
  - `backend/src/main/java/com/archive/agent/MultiTurnService.java`(新)
  - `backend/pom.xml` 改(加 `spring-ai-starter-model-chat-memory-repository-jdbc`)
- **工作量**: 0.5 天(2-4 小时)
- **依赖**: **T-I-1**(pom.xml 必须先有) **+ T-I-9**(AgentEngine 先有)
- **可并行**: ✅ 是(可跟 T-I-10 / T-I-11 / T-I-12 同时)
- **冲突点**:
  - **`pom.xml` 跟 T-I-1 冲突** —— **T-I-13 必须等 T-I-1 push 完**(`状态: 已完成` 才能开工)
  - 开工时**先 `git pull`** 拉 T-I-1 的 pom.xml,再 `add` chat-memory-repository 依赖
  - `application.yml` 不动(MultiTurnController 自动注 ChatClient)
  - **跟 T-I-11 集成测试串行**: I-11 测试要建 chat_memory 表,**I-11 集成测会要 I-13 先 commit**
- **开始步骤**: 同 T-I-1,**先看本表 T-I-1 状态是 `已完成` 再开工**
- **详细 spec**: `.mavis/plans/plan-I-agent-implementation.md` §2 I-13
- **验收**: 浏览器连问 3 轮 + 重启不丢

---

## 🆕 任务模板(占用人新增任务时复制)

```markdown
### T-NEW-<序号>: <一句话描述>

- **状态**: [新] 未开发(由 <你的名字> 提议)
- **提议者**: <你的名字>
- **影响文件**: <列出独占文件>
- **工作量**: 0.X 天
- **依赖**: <T-I-X>
- **可并行**: ✅/❌
- **详细 spec**: <plan-I §X.X 或新文件>
- **验收**: <1-2 句>
- **理由**: <为什么需要这个任务>
```

---

## 📜 变更历史(append only)

| 日期 | 任务 | 状态变更 | 占用人 |
|---|---|---|---|
| 2026-06-09 | T-I-1~T-I-13 | 全部 `未开发` | Mavis(初始发布) |
| 2026-06-09 | T-I-1~T-I-11 | 11 个 → `已完成` (Agent 主体完工) | Sisyphus |
| 2026-06-09 | T-I-12 | 仍 `未开发` (前端 Knowledge.vue + AgentStepsPanel) | - |
| 2026-06-09 | T-I-13 | 仍 `未开发` (多轮对话 + chat_memory) | - |
| 2026-06-09 | T-I-13 + Sisyphus 5 P0 bugfix | T-I-13 `已完成` + 修 5 P0 (group_by/is_not_null/IN 长度/LIKE 转义/JdbcChatMemory) | Mavis |
| 2026-06-09 | T-I-12 | 仍 `未开发` (前端等接手 AI) | - |
| 2026-06-09 | T-I-12 + T-I-13 | Sisyphus 1 小时 补完工 (前端 + 多轮对话), Plan I 13 任务 100% 完工 | Sisyphus |
| 2026-06-09 | rebase 修 5 P0 + 修 Sisyphus JdbcChatMemory bug (AgentConfig + ChatMemoryConfig 都中招) | 集成 P0 fix | Mavis |

---

## ⚠️ 冲突处理 SOP

**场景 A**: 2 个占用人同时改一个文件

1. 晚 push 的人 `git pull --rebase` 拉最新
2. 解决冲突(通常是: 早的 commit 是"新增文件 / 新增方法",晚的是"另一方法" → 不冲突)
3. 重 push

**场景 B**: 占用人 commit 后,**还没 push**,被人抢了

1. 晚的 push 先到 = 占用成功
2. 早的 `git pull --rebase`,发现自己任务已被人占
3. 找下一个 `未开发` 任务

**场景 C**: 占用人失联(> 30 分钟没动, **接手 AI 接管**)

1. 接手 AI 看到一个 `占用-X` 状态, 但上下文里从没听说过 X:
   ```bash
   git log --author="X" -1 --format="%ai %s"
   # 看 X 最后一次 commit 时间
   ```
2. > 30 分钟没新 commit + 没有 PR 等合并 = X 失联
3. 接手 AI 接管: 改 TASKS.md `占用-X` → `占用-<自己>(reclaim from X)`, 正常推 main
4. **不需要别人同意** (自我管理)
5. X 复活看到: 主动让位, 抢别的任务 (不追究)

**为何不用 “上下文” 做管理**: 接手 AI 的上下文是会话内存, 多 session 不共享, 中途死了没记录。**TASKS.md 才是唯一真相源**。

**场景 D**: 占用人发现任务过大,需要拆

1. 占用人**自己**加新行 T-NEW-X
2. 原任务标 `占用-X(进行中,拆出 T-NEW-X)`
3. push

---

## 🆘 卡住

| 问题 | 怎么办 |
|---|---|
| 不知道要做什么 | 打开 `.mavis/plans/plan-I-agent-implementation.md` §2 找 `### I-N` 段 |
| 不知道接哪个任务 | 找 1 个 `未开发` 且 `可并行: ✅` 的 |
| pom.xml 冲突 | `git pull --rebase` 解冲突(同文件多人加依赖,Maven 不会重复) |
| 不知道自己的 commit 写没写对 | 参考 plan-I §2 每子项的 `**commit**:` 段 |
| **LLM 框架装不上** | **不要死磕**,在 `deliverable.md` 报告"卡住:XXX",Mavis 接管 |
| 跨任务文件互相改 | **必须聊** + 一个人合并(冲突概率极低,已拆开) |

---

*本表由 Mavis 沙箱维护。接手 AI / 人类程序员看到 `未开发` 任务 = 抢的机会。*
*谁先 `git commit + push` 谁赢。*

---

## 🆕 v1.1 阶段 — 6 大独立模块划分（基线 `7aa7bae`，零回归）

> **2026-06-11 PM 拍板**: v1.1 增量 24 RI（§13.1~§13.3）按"独立小项目"原则切分为 6 大模块。
> 每模块 = 1 个接手 agent 能独立 hold 的工作包，**只看自己那一份 spec + 模块内涉及文件 + 已有代码**，不需回到总表看上下文。
> **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-XX-*.md`（6 份独立 spec）
> **配套索引**: `.mavis/plans/draft/v1.1-modules/README.md`
> **架构扩展**: `.mavis/plans/draft/architecture-v1.1-extended.md`（T1, da17c92）
> **重构清单**: `.mavis/plans/draft/refactor-and-fix-list.md`（T2）
> **原 41 任务详**: `.mavis/plans/draft/tasks-v1.1.md`（T3，已合并到 MOD spec）

### 状态机（沿用上面 🚦）

```
未开发 ──占用人 commit+push──> 占用-XXX ──完成──> 已完成(XXX / YYYY-MM-DD)
```

### PM 拍板 5 项（2026-06-11）

| # | 决策 | 拍板 |
|---|---|---|
| D-1 | 5 角色命名 | `admin / pm / legal / committee / secretary`（v1.0 `admin / user` 双轨兼容） |
| D-2 | 网络查字典 | v1.1 实施只配 2 候选（百度百科 + 维基百科），金融/互动留占位 |
| D-3 | 乐观锁 v1.1 严格度 | `archive.optimistic-lock.strict=false`（冲突仅记日志，v2 多用户切 true） |
| D-4 | 导出库 | OpenPDF 2.0.2 + Apache POI 5.2.5（jar 增量 < 10MB） |
| D-5 | 附件预览前端库 | pdfjs-dist ^4.0 + mammoth ^1.7（纯前端，不引 LibreOffice） |

### 模块依赖图

```
MOD-01 (DB 迁移, 1.7d)
    ↓
    ├─→ MOD-02 (核心域, ~7d)     ─┐
    ├─→ MOD-03 (Agent 工具, ~5d) ─┼─→ MOD-06 (文档+集成测试+验收, ~3.5d)
    ├─→ MOD-04 (业务功能, ~10d)  ─┤
    └─→ MOD-05 (前端集成, ~1.5d) ─┘
```

**关键路径**: MOD-01 → MOD-02 → MOD-06 = 1.7 + 7 + 3.5 = **~12.2d**
**4 路并行**（MOD-01 完工后）: MOD-02 / MOD-03 / MOD-04 / MOD-05 同时开工 ≈ 10d（最长那条）

### 模块分块清单（直接派单给接手 agent）

#### MOD-01: 数据库迁移 + 7 表 ALTER

- **状态**: 已完成 (阿根廷 / 2026-06-11)
- **占用者**: 阿根廷
- **影响文件**（独占）: `backend/src/main/resources/db/migration/I-RI-*.sql`（11 新）+ `init.sql`（append）
- **工作量**: 1.7d
- **依赖**: 无
- **可并行**: ❌（关键路径第一步）
- **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-01-db-migrations.md`
- **验收**: mysql 顺序执行 0 错 + 21 字段（7 表 × 3 字段）+ 7 新表 + 6 role + 2 触发器 + 回填 0 NULL
- **commit 模板**: `feat(db,v1.1): MOD-01 SQL 迁移 11 文件 + 7 表 ALTER + 触发器`

#### MOD-02: 核心域改造（软删 / RBAC / 乐观锁 / 审计）

- **状态**: 已完成 (阿根廷 / 2026-06-11)
- **占用者**: 阿根廷
- **影响文件**（独占 21 文件）: 8 新 + 13 改后端（entity / controller / service / security / common）
- **工作量**: ~7d
- **依赖**: MOD-01 完工
- **可并行**: ✅（MOD-03/04/05 同时）
- **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-02-core-domain.md`
- **验收**: `mvn compile` 0 错 + `mvn test` ≥ 30 测例过 + RBAC 双轨兼容（**零回归**）
- **commit 模板**: `feat(be,v1.1): MOD-02 <子项名>（如 "RBAC 5 角色双轨"）`

#### MOD-03: Agent 工具改造（5 级 / 7 重加固 / 网络字典）

- **状态**: 已完成 (阿根廷 / 2026-06-11)
- **占用者**: 阿根廷
- **影响文件**（独占 11 文件）: 3 新 + 8 改（agent / tool / prompt / service / entity）
- **工作量**: ~5d
- **依赖**: MOD-01 + MOD-02 完工
- **可并行**: ✅（MOD-04/05 同时）
- **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-03-agent-tools.md`
- **验收**: `mvn compile` 0 错 + 5 级判定 in-tool（**不算 ReAct 步数**）+ 7 重加固 + NetworkDictLookup 6 层降级
- **commit 模板**: `feat(agent,v1.1): MOD-03 <子项名>（如 "FindProjectTool 5 级判定"）`

#### MOD-04: 业务功能新增（看板 / 通知 / 导出 / 预览 / 脱敏 / 导入）

- **状态**: 已完成 (阿根廷 / 2026-06-11)
- **占用者**: 阿根廷
- **影响文件**（独占 27 文件）: 8 新 + 5 改后端 + 8 新 + 5 改前端 + 1 pom + 1 package.json
- **工作量**: ~10d
- **依赖**: MOD-01 + MOD-02 + MOD-03 完工
- **可并行**: ✅（MOD-05 同时）
- **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-04-business-features.md`
- **验收**: `mvn compile` 0 错 + `npm run build` 0 错 + `mvn test` ≥ 35 测例过 + 7 大端到端场景
- **commit 模板**: `feat(v1.1,RI-N): MOD-04 <子项名>（如 "项目看板"）`

#### MOD-05: 前端集成（Knowledge / Dashboard / 路由 / 配置）

- **状态**: 已完成 (阿根廷 / 2026-06-11)
- **占用者**: 阿根廷
- **影响文件**（独占 9 文件）: 2 新 + 4 改前端 + 3 配置（application.yml / config.example.json / GLM-KEY-SETUP.md）
- **工作量**: ~1.5d
- **依赖**: API 响应体确定（MOD-03 完工即可，**不等** MOD-04）
- **可并行**: ✅（MOD-04 同时）
- **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-05-frontend-integration.md`
- **验收**: `npm run build` 0 错 + 7 关键场景（置信度 / 切换 hint / 双模动画 / 失败 banner）
- **commit 模板**: `feat(fe,v1.1): MOD-05 <子项名>（如 "Knowledge.vue 加置信度徽章"）`

#### MOD-06: 文档 + 集成测试 + 端到端验收

- **状态**: 已完成 (阿根廷 / 2026-06-11)
- **占用者**: 阿根廷
- **影响文件**（独占 13 文件）: 2 新 + 11 改（docs/ 同步 + V11IntegrationTest + review）
- **工作量**: ~3.5d
- **依赖**: MOD-01 ~ MOD-05 全部完工
- **可并行**: ❌（最后一道工序）
- **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-06-docs-test-acceptance.md`
- **验收**: `mvn test V11IntegrationTest` 30+ 测例过 + 7 端到端场景 + 6 份分章架构同步 + review 文件
- **commit 模板**: `docs(v1.1): MOD-06 文档同步 + 集成测试 + 端到端验收`

### 🟢 实时可抢任务清单（2026-06-11 MOD-06 完工）

| 任务 | 为什么现在可抢 | 备注 |
|---|---|---|
| ~~MOD-01~~ | ✅ 已完成 | — |
| ~~MOD-02/03/04/05~~ | ✅ 已完成 | — |
| ~~MOD-06~~ | ✅ 已完成 | v1.1 收口 |

**当前能抢的**:
- 🎉 **v1.1 全部 6 模块 (MOD-01~06) 已完工** — 无剩余 v1.1 模块可抢
- 下一步: owner 按 `test-to-settle/old/ACCEPTANCE-GUIDE.md` v1.1 段 + `docs/reviews/2026-06-11-v1.1-review.md` 验收

### 抢先 SOP（沿用上面 🚦）

1. **看** `.mavis/plans/draft/v1.1-modules/MOD-XX-*.md`（10-30 分钟）
2. **改** `状态: 未开发` → `占用-<你的名字>`
3. **10 秒内** `git add TASKS.md && git commit && git push origin main`
4. **干完** 按 MOD spec §4 验收 → 改 `状态` → `已完成` + commit + push

### 严禁（沿用上面 🚦）

- ❌ 改 `占用-A` 改回 `未开发`
- ❌ 改别人 `已完成` 的部分
- ❌ 一个 commit 改多个任务
- ❌ 占用了但**没 push** 超过 10 分钟
- ❌ 直推 `minimax` 分支
- ❌ 改 `REQUIREMENTS.md`（需求开发人员的活）
- ❌ 改 `ARCH-DECOMPOSITION.md` RI 拆解（架构师 + MOD-06 的活）
- ❌ **改 MOD-XX 独占清单之外的文件**（除非接口契约 §6 明确写明）

### 完工 SOP（MOD-XX 完工后必跑）

1. 按 MOD spec §4 验收（编译 / 测试 / 端到端）
2. 改 `状态: 占用-X` → `状态: 已完成 (X / YYYY-MM-DD)`
3. `git add <MOD 涉及文件> TASKS.md && git commit && git push origin main`
4. 通知 PM（投委会档案项目PM）验收

### 总览数字（v1.1）

| 维度 | 数 |
|---|---|
| 模块数 | 6 |
| 涉及 RI | RI-46 ~ RI-69（24 条） |
| 涉及文件（总） | ~95 个文件 |
| 新增 SQL 迁移 | 11 个 |
| 新增 Controller | 5 个 |
| 新增 Service | 12 个 |
| 新增 Entity | 7 个 |
| 新增前端 View | 5 个 |
| 新增前端 Component | 4 个 |
| ALTER 表 | 7 张 |
| 总工时 | ~28.7d（4 路并行 ≈ 5 周） |
| 关键路径 | ~12.2d |
| 集成测试 | 30+ 测例 |

---

## 🆙 UPGRADE 任务（来自 complexity 升级）

> **来源**：`test-to-settle/complexity.md` 大改分析完成后升格  
> **完整 plan**：`upgrade_to_settle/plan-*.md`（§0～§7）  
> **流程**：改本节 `状态` → `占用-<名字>` → 读 plan 开工 → `已完成`

| Plan ID | 文件 | 摘要 | 优先级 | 状态 |
|---------|------|------|--------|------|
| UP-0611-01 | [`plan-2026-06-11-archive-local-fs-tools.md`](upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md) | Agent 只读 `D:/archive` 材料：ls/grep/read + 多模态 | P1 | `未开发` |
| UP-0611-02 | [`plan-2026-06-11-agent-intent-classification.md`](upgrade_to_settle/plan-2026-06-11-agent-intent-classification.md) | Agent 离题拒答 + 业务域意图分类（C-0611-01） | P1 | `已完成(Sisyphus / 2026-06-11)` |
| UP-0611-03 | [`plan-2026-06-11-deploy-pipeline.md`](upgrade_to_settle/plan-2026-06-11-deploy-pipeline.md) | 部署 SOP / I-RI-39 源文件修复 / `ddl-auto: validate`（C-0611-02/03/04/05/09） | P1 | `已完成(Sisyphus / 2026-06-11)` |
| UP-0611-04 | [`plan-2026-06-11-chat-ui.md`](upgrade_to_settle/plan-2026-06-11-chat-ui.md) | 知识库聊天式 UI 重构（C-0611-08） | P2 | `未开发` |
| UP-0611-05 | [`plan-2026-06-11-test-governance.md`](upgrade_to_settle/plan-2026-06-11-test-governance.md) | 测试治理：补测例 + H2/MySQL 策略文档（C-0611-10/11） | P2 | `未开发` |

### 抢先 SOP

1. **读** `upgrade_to_settle/README.md` + 对应 `plan-*.md`（§0～§7）
2. **改** `状态: 未开发` → `占用-<你的名字>`
3. **10 秒内** `git add TASKS.md && git commit && git push origin main`
4. **干完** 按 plan §4 验收 → 改 `状态` → `已完成` + commit + push

---

*本 v1.1 阶段由 PM 维护。6 模块切分（按"独立小项目"原则），每模块 1 份独立 spec。*
*接手 agent 只看自己那份 spec 即可开工。*

#### T-v1.1-42: 后端 mvn 验证（PM 委托，沙箱受限）

- **状态**: 已完成 (阿根廷 / 2026-06-11)
- **占用者**: 阿根廷
- **影响文件**（独占）: `test-to-settle/old/VERIFICATION-REPORT.md`
- **工作量**: 0.5d
- **依赖**: 无
- **可并行**: ✅
- **详细 spec**: `.mavis/plans/draft/task-mvn-verification.md`
- **验收**:
  1. `mvn compile -DskipTests -B` BUILD SUCCESS
  2. `mvn test -Dtest=V11IntegrationTest -B` 45 测例全过
  3. `test-to-settle/old/VERIFICATION-REPORT.md` 包含环境/compile/test 三段实测数据
- **commit 模板**: `docs(verify): mvn 验证报告 test-to-settle/old/VERIFICATION-REPORT.md`
- **注意**: PM 沙箱无 JDK/cacerts，**必须接手 agent 跑**，不要在 PM 沙箱里尝试

---

## 🤖 自动化测试任务（AT-*）

> **当前：无 AT 任务。** 不要在此占位或写示例；有真实案例时再追加。  
> 新建流程：[`test_task/README.md`](test_task/README.md) · 复制 [`test_task/case-TEMPLATE.md`](test_task/case-TEMPLATE.md) → 在本节追加一条 **AT-XXX**（按该 README 模板）。

*（本节有任务前保持空白；与开发任务共用占用规则：谁先 push 谁占。）*

