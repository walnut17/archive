# Plan I 任务分块清单(物理并行开发协调表)

> **本文档是"谁占用了哪个任务"的实时真相表**。每个任务独占 1 个 section,**多人同时开发互不覆盖**。
> **同步规则**: **状态变更立即 commit + push 到 main 分支**(10 秒内完成)。**push 成功 = 占用成功**。
> **冲突解决**: **谁先 push 谁占**。别人看到 push 通知就放弃,找下一个 `未开发` 任务。
> **任务粒度**: 1 个任务 = 1 个 commit + 1 个 push(典型 1-3 小时工作量,接手 AI 一次完成)
>
> **2026-06-09 项目方新口径**: 多人多 agent 推 **main 分支**(开发中, 代码未经审核), Mavis 沙箱 审核后 推到 **minimax 分支**(成品, 生产用)。接手 AI 推 main 是 OK 的(项目方授权过)。

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

**当前能抢的**(2026-06-09 16:17,Sisyphus 已完工 T-I-1~T-I-3):
- 🟢 T-I-4(SearchFulltextTool) — 可并行
- 🟢 T-I-5(FindProjectTool) — 可并行
- 🟢 T-I-6(QueryMysqlTool) — 可并行
- 🟢 T-I-7(LlmSummarize+AskClarification) — 可并行
- 🟢 T-I-8(GetProjectBusinessData) — 可并行

**5 工具全完能抢**:
- T-I-9(AgentEngine) + T-I-13(多轮对话)— 2 个并行

**5 工具全完能抢**:
- T-I-9(AgentEngine) + T-I-13(多轮对话)— 2 个并行

**T-I-9 完能抢**:
- T-I-10(QaController) — 串行

**T-I-10 完能抢**:
- T-I-11(测试) + T-I-12(前端) — 2 个并行(但 I-11 等 I-13)

**全部完**: 13+ commits, ~8.3 天总工期

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

- **状态**: 占用-Sisyphus
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

- **状态**: 占用-Sisyphus
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

- **状态**: 占用-Sisyphus
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

- **状态**: 占用-Sisyphus
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

- **状态**: 占用-Sisyphus
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

- **状态**: 未开发
- **占用者**: -
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

- **状态**: 未开发
- **占用者**: -
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

- **状态**: 未开发
- **占用者**: -
- **影响文件**:
  - `backend/src/test/java/com/archive/agent/AgentIntegrationTest.java`(新,大文件 ~400 行)
  - `backend/src/test/resources/application-test.yml`(可能加)
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

- **状态**: 未开发
- **占用者**: -
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

- **状态**: 未开发
- **占用者**: -
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
