# 投委会档案管理系统 — Agent 智能问答实施总方案

> **作者**: Mavis(沙箱 PM 角色)
> **状态**: ✅ 方案定稿,等待执行
> **生产环境 AI Agent**: 接管本文档 §5 执行 12 子项
> **基线 commit**: `a34f540`(M0~M2 + Plan A~G 完工)
> **配套文档**(必读):
> - `docs/requirements/AGENT-REQUIREMENTS.md` (业务)
> - `AGENT-RESEARCH.md` (调研, 本目录)
> - `AGENT-FRAMEWORK-DECISION.md` v1.0(决策, 本目录)
> - `.mavis/plans/plan-I-agent-implementation.md` (12 子项 spec)
> - `../operations/DEV-STANDARDS.md`(开发标准)
> - `../reviews/LESSONS-LEARNED.md`(踩坑)
> - `../operations/TEAM-ARCHIVE.md`(环境)

---

## §1. 方案摘要(给项目方看)

**目标**:把现有 `QaController` 写死的 `search → rerank → generate` 三步管道,**升级为 Spring AI 1.1 ReAct Agent**,支持自适应选工具、语义定位项目、追问用户。

**不引入**:
- ❌ 向量化 / RAG 平台 / Docker / 多机 / 外网 SaaS
- ❌ 复杂多 Agent 协作 / Reflexion(过度设计)
- ❌ 数据库账号额外开(本期用主应用账号,可选优化下期)

**引入**:
- ✅ **Spring AI 1.1**(官方 BOM,4 starter,jar 增量 < 15MB,运行时 < 100MB)
- ✅ **6 个工具**:search_fulltext / find_project / query_mysql / get_project_business_data / llm_summarize / ask_clarification
- ✅ **白名单 + 参数化 SQL** + **审计** + **埋点**(接现有 llm_call_log + audit_log)
- ✅ **5 步 max_iterations**(硬编码 AgentEngine for 循环,不走 yml) + **降级路径**(Spring AI 挂时 QaController 走老 GlmService)
- ✅ **零回归**(M0~M2 + Plan A-G 全部代码 + 数据库 schema 不动)

**工期**:~7.3 天(可与现有 M0~M2 跑并行)

---

## §2. 关键决策(为什么是 Spring AI)

| 项 | 决策 | 理由 |
|---|---|---|
| **LLM 框架** | **Spring AI 1.1 + `spring-ai-starter-model-openai`** | 生产特性现成(HITL / 上下文压缩 / 重试 / 限流),走 OpenAI 兼容协议调智谱 GLM-4-Flash,Spring Boot 3.3 集成 0 摩擦,MCP 协议支持,Spring 官方+社区背书 |
| **不引阿里云 starter** | 用 OpenAI 兼容路径调智谱 GLM,不走 DashScope | L1 已定智谱 GLM,再引 DashScope 得多 1 套阿里云密钥 + 1 组传递依赖,价值约等于 0(详见 `AGENT-FRAMEWORK-DECISION.md` §1.2.1.1) |
| **6 工具 / 单 Agent** | 不是多 Agent 协作 | 2 角色内网单实例,过度设计,5 步上限够用 |
| **不向量化** | MySQL FULLTEXT ngram 主力检索 | 沿用 M2 决策,已验证 |
| **5 步 max_iterations** | 不是 10 / 20 | 项目方 v1.0 决策;**硬编码**在 `AgentEngine` for 循环(`MAX_ITERATIONS = 5`),不走 yml(Spring AI 1.1 无 `spring.ai.agent.max-iterations` 配置) |
| **降级路径必须有** | Spring AI 挂时 QaController 走老 GlmService | 零回归,关 `agent.enabled=false` 立即降级 |
| **不扩硬件 / 不开新数据库账号** | 沿用当前 32GB 单机 + 主应用账号 | 项目方 v1.0 决策 |

**推翻初版"自研 ReAct"的理由**(项目方 v1.0):
- 自研 300 行核心代码生产风险 = 自己背,Spring AI = 社区背书
- 担心"5-30 个传递 jar" 实际是 "< 15MB" 4 个 starter
- 担心"GLM 适配非官方" 实际"OpenAI 兼容协议可用"
- 自研保留为**回退方案**:Spring AI 跑不起来时再写

---

## §3. 工具集(6 个,按项目方 v1.0 决策"语义锁项目 → 分析"两阶段 RAG)

| # | 工具名 | 用途 | 实现成本 | 关键安全 |
|---|---|---|---|---|
| 1 | **find_project** | 语义定位项目(项目方 v1.0 关键) | 0.5 天 | 全文检索 + 置信度归一化 |
| 2 | **search_fulltext** | MySQL FULLTEXT 检索材料 | 0.5 天 | 可选 projectCode filter |
| 3 | **get_project_business_data** | 项目业务汇总(快捷) | 0.5 天 | 复用 Plan C-5 |
| 4 | **query_mysql** | 查业务数据(白名单 6 实体) | 1 天 | 严格白名单 + 参数化 + 审计 |
| 5 | **llm_summarize** | 让 LLM 摘要/抽取 | 0.5 天 | 复用 LLMProvider |
| 6 | **ask_clarification** | 追问用户,中断循环 | 0.2 天 | ctx.interrupt=true |

**核心两阶段 RAG 流程**(项目方 v1.0 关键场景):
```
用户: "新能源那个项目今年盈利怎么样?"
  ↓
[step 1] find_project("新能源那个")
  → 返回 1 候选,confidence 0.92 → 锁定 projectCode=PRJ-2026-001
  ↓
[step 2] get_project_business_data("PRJ-2026-001")
  → {stage: "贷后", amountWan: 5000, remainingAmountWan: 3200}
  ↓
[step 3] search_fulltext("盈利", projectCode="PRJ-2026-001")
  → 5 段匹配材料
  ↓
[step 4] llm_summarize(task="综合 2 步结果写一段答案", text=...)
  → 答案 + 引用 [1][2]
  ↓
[FINAL_ANSWER]
```

---

## §4. 集成点(零回归)

**新增**:1 个包 `com.archive.agent`(~14 个文件) + 5 个 pom 依赖 + application.yml 5 行

**沿用**(0 改动):
- `provider.LLMProvider` — 降级路径用
- `service.KnowledgeSearchService` — SearchFulltextTool 注入
- `service.GlmService` — 降级路径
- 所有 JPA repository — FindProjectTool / QueryMysqlTool 注入
- `common.LlmScenario` — 扩展新值 AGENT_STEP / AGENT_FINAL / AGENT_FALLBACK
- `entity.LlmCallLog` — 复用,scenario 填新枚举
- `entity.AuditLog` — 工具调用写 audit
- Spring Security / JWT — 无改动
- `config.AuditorAwareImpl` — 已就绪,自动填工具调用者

**改造**:`controller/QaController.java` — 加 1 个 `@Autowired(required = false) AgentEngine`,`ask()` 方法先看开关;响应 DTO 加 3 个可空字段(`agentMode` / `steps` / `toolCalls`)

**I-13 新增文件**(多轮对话,补业务需求 §4.4):
- `db/migration/I-chat-memory.sql` — `chat_memory` 表(会话 ID + 消息列表, MySQL JDBC ChatMemoryRepository)
- `agent/MultiTurnController.java` — `/api/qa/turn/{sessionId}` 端点(支持前端传 sessionId,后端拉历史)
- `agent/ChatMemoryConfig.java` — `JdbcChatMemoryRepository` bean + `MessageChatMemoryAdvisor` bean

**降级路径**:`spring.ai.agent.enabled=false` 立即降级到 GlmService,**前端无感**

**前端**:`Knowledge.vue` 加 `<AgentStepsPanel>` 折叠组件 + `<el-switch>` 启用 Agent 模式开关

---

## §5. 12 子项实施计划(给接手 AI 看)

**详细 spec**:`.mavis/plans/plan-I-agent-implementation.md`(1059 行,逐子项带代码 + 验收 + commit 规范)

**执行顺序**:
| # | 子项 | 估时 | 依赖 |
|---|---|---|---|
| **I-1** | pom.xml 加 Spring AI 依赖 | 0.2 天 | 无 |
| **I-2** | application.yml 加 Spring AI + agent 开关 | 0.1 天 | I-1 |
| **I-3** | agent 包骨架 + 5 DTO + Listener | 0.5 天 | I-2 |
| **I-4** | 工具 1: SearchFulltextTool | 0.5 天 | I-3 |
| **I-5** | 工具 2: FindProjectTool + project FULLTEXT 索引 | 0.5 天 | I-3 |
| **I-6** | 工具 3: QueryMysqlTool(白名单 + **聚合** + 操作符 + 注入防护,**重点**) | 1.5 天 | I-3 |
| **I-7** | 工具 4 + 5: LlmSummarize + AskClarification | 0.5 天 | I-3 |
| **I-8** | 工具 6: GetProjectBusinessDataTool | 0.5 天 | I-3 |
| **I-9** | AgentEngine 核心 + Prompt + Few-shots(**最关键**) | 1.5 天 | I-4~I-8 |
| **I-10** | QaController 改造 + 降级路径 | 0.5 天 | I-9 |
| **I-11** | 端到端集成测试(10 用例) | 1 天 | I-10 |
| **I-12** | 前端 Knowledge.vue 改造 | 0.5 天 | I-10 |
| **合计** | | **~8.3 天**(原 7.3 + I-13 多轮对话 0.5 + I-6 聚合加固 0.5) | |

**关键路径**:I-3 → I-9 → I-10 → I-11,其他可并行(分工给多个 sub-agent)

---

## §6. 完工验收(每子项必跑)

```bash
# 沙箱编译(接手 AI 必跑)
cd /workspace/projects-online-clone
mvn compile -DskipTests -B -o
# 期望: BUILD SUCCESS

# I-11 之后
mvn test
# 期望: 10 个 AgentIntegrationTest 全过

# I-12 之后
cd frontend
npm run build
# 期望: 0 错
```

**端到端浏览器测试**(5 个问题):
1. "新能源那个项目今年盈利怎么样?" → 看到 4-5 步,锁定项目
2. "PRJ-2026-001 剩余金额?" → 调 get_project_business_data
3. "今年否决了哪些项目?" → 调 query_mysql + search_fulltext 综合
4. "那个项目" → 调 ask_clarification,前端弹 dialog
5. "工程进度如何?"(无关项目) → 调 search_fulltext 无果 + FINAL_ANSWER "未找到"

**降级测试**:`application.yml` 设 `spring.ai.agent.enabled=false` → 重启后端 → 问问题 → 走老路径(`agentMode=false`)

**多轮对话测试**(I-13):浏览器连问 3 轮(第 1 轮说 PRJ-2026-001, 第 2 轮说 "它的剩余金额", 第 3 轮说 "谁负责")→ 第 2/3 轮 LLM 自动带上文(自动锁定 PRJ-2026-001 不丢)

**埋点测试**:`mysql -e "SELECT scenario, COUNT(*) FROM llm_call_log GROUP BY scenario"` 应有 `AGENT_STEP` / `AGENT_FINAL` / `AGENT_TOOL`

---

## §7. 风险与降级

| 风险 | 降级方案 |
|---|---|
| Spring AI 1.1 与 Spring Boot 3.3 不兼容 | 降到 1.0.x(API 略不同) |
| 智谱 OpenAI 兼容协议 400 错 | `application.yml` 显式 `model: glm-4-flash` |
| Spring AI 1.1 实际 API 不熟 | 看 `AGENT-RESEARCH.md` §3.1 实战代码 + `plan-I-agent-implementation.md` §0.3 资源链接 |
| LLM 限流 60 req/min | Spring AI 内置限流 + 5 步上限 |
| SQL 注入 | 严格白名单 + 参数化 + audit_log |
| 跨项目偷窥敏感材料 | search_fulltext 加 projectCode filter |
| Agent 异常崩溃 | catch + fallback 走老 GlmService |

---

## §8. 完工后流程

**接手 AI 完工后**:
1. 12+ commit 推 `minimax` 分支
2. 写 `deliverable.md`(commit 链接 + 编译截图 + 端到端结果 + 已知 TODO)
3. 通知 Mavis 沙箱审

**Mavis 审完**:
- 跑 `mvn compile` 验证
- 浏览器端到端跑过
- 接受 / 打回 / 修订
- 报告给项目方(你)

**项目方(你)收尾**:
- 本机 `git pull`
- 部署到 `D:\projects-online\`
- 启动 `startup.ps1`
- 浏览器验收

---

## §9. 推送清单(本次 commit 给 Gitee)

1. `AGENT-FRAMEWORK-DECISION.md`(决策更新 v1.0:自研 → Spring AI)
2. `AGENT-IMPL-PLAN.md`(本总方案,**新增**)
3. `.mavis/plans/plan-I-agent-implementation.md`(12 子项 spec,Spring AI 版)
4. `docs/requirements/AGENT-REQUIREMENTS.md`(无改动,引用)
5. `AGENT-RESEARCH.md`(无改动,引用)
6. `../operations/DEV-STANDARDS.md`(无改动,引用)
7. `../reviews/LESSONS-LEARNED.md`(无改动,引用)

**重要**:
- ✅ **零代码** (.java / .vue / .sql 都**不动**)
- ✅ **不引依赖**(pom.xml **不动**)
- ✅ **不改数据库 schema**
- ✅ **不创 / 删表**
- ✅ **不跑 SQL**

**给接手 AI 的所有信息全在文档里**:
- 决策(`AGENT-FRAMEWORK-DECISION.md` v1.0)
- 业务(`AGENT-REQUIREMENTS.md`)
- 调研(`AGENT-RESEARCH.md`)
- 实施(`AGENT-IMPL-PLAN.md` + `plan-I-agent-implementation.md`)
- 规范(`DEV-STANDARDS.md` + `LESSONS-LEARNED.md` + `TEAM-ARCHIVE.md`)

---

## §10. 关键约束再强调

**接手 AI 必看**(`plan-I-agent-implementation.md` §0):

1. **你只写代码,不当 PM** —— 任何问题先看文档,文档没写再问
2. **必读 7 份文档** — 开工前 `cat` 完
3. **每子项一个 commit** — 不囤
4. **每子项必跑编译 / 测试 / 端到端** — 不偷工
5. **完工必写 deliverable.md** — 不甩手
6. **遇到 LLM 框架装不上** — **不要死磕**,在 deliverable.md 报告,我会接管
7. **零回归** — 现有 M0~M2 + Plan A-G 任何功能不能挂

---

*本方案由 Mavis 在沙箱出方案,生产环境 AI Agent 接手执行。*
*推送到 Gitee 后,项目方(用户)会在生产环境拉取并分配给"别处的 AI"执行。*
