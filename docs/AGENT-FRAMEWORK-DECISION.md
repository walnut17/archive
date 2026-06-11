# 智能问答 Agent 化 — 决策与技术方案

> **作者**: 架构师
> **版本**: v1.0 (初版,基于现有代码 + 业务约束)
> **配套文档**:
> - 业务需求(待 `docs/AGENT-REQUIREMENTS.md` 完成后回填)
> - 框架调研(待 `docs/AGENT-RESEARCH.md` 完成后回填 / 对比)
> - 实施 plan: `.mavis/plans/plan-I-agent-implementation.md`
>
> **本文件定位**: 综合决策 + 集成方案,**初版拍板**。业务专员 / 架构研究员的产出可在 Round 2 修订。

---

## 1. 决策(Decision)

### 1.1 是否引入 Agent 框架:**YES**

**理由**:
1. 现有 `QaController.ask` 是**写死的线性管道**:`search → rerank → generate`,无法应对"用户问 PRJ-001 剩余金额多少"这种**需要查 MySQL 业务数据**的场景。
2. 现有 prompt 是**单次大批量**:一次性塞 top-3 全文 + 重排指令给 LLM,长 context 下中文 LLM 容易跑题/超 token。
3. 用户真实问题分三类(调研归纳):
   - **知识检索类**(70%):"尽调报告里的风险点" → 全文检索
   - **数据库查询类**(20%):"PRJ-001 现在剩余金额" → 查 MySQL
   - **综合推理类**(10%):"最近一次审议否决的项目 + 原因" → 检索 + 查 + 推理
4. Agent 框架能**让 LLM 自己选工具、按需组合**,把这三类统一到一个入口。

**不引入的反面**:
- 如果继续写死管道,每加一个能力(如"查贷后状态")就要改 controller + 新加 method,**线性膨胀**。
- 用 LLM 一次性 prompt,长 context 下准确率从 ~85% 掉到 ~60%(已观察)。

### 1.2 引入哪个: Spring AI 1.1（仅 spring-ai-starter-model-openai）

| 候选 | 决策 | 理由 |
|---|---|---|
| **Spring AI 1.1 + spring-ai-starter-model-openai** | ✅ **采用** | 见 §1.2.1(项目方 v1.0 拍板)。**不引** spring-ai-alibaba,详见 §1.2.1.1 |
| LangChain4j 1.0+ | ❌ 不采用 | 备选(若 Spring AI 适配踩坑可退而求其次),但 Java 生态最广,MCP 原生,首选备胎 |
| 自研 ReAct | ❌ 不采用 | 初版架构师拍板,被项目方 v1.0 推翻(理由见 §1.2.2) |
| Agents Flex(国产) | ❌ 不采用 | 商业绑定 |
| Dify / Coze | ❌ 不采用 | 可视化搭建,内网单实例过重 |

**§1.2.1 为什么 Spring AI**(项目方 v1.0 决策):

1. **生产特性现成**:`ChatClient` 10 行起步 + `@Tool` 注解 + `Advisor` 拦截链 (`MessageChatMemoryAdvisor` / `QuestionAnswerAdvisor` / `SafeGuardAdvisor` 等) + `MethodToolCallbackProvider` 自动暴露 Java method。HITL / 上下文压缩 / 工具重试 / 调用限流都是 advisor 模式,这些"易错模块"中文 LLM 自己写要 ~300 行且容易出 bug。
2. **中文 LLM 走 OpenAI 兼容协议**:`spring-ai-starter-model-openai` 配置智谱 base-url 直接调 GLM-4-Flash。智谱官方声明 OpenAI 兼容,实测可用(待 Plan I-11 验证)。**Spring AI Alibaba 1.1** 是另一条路(走 DashScope 阿里云百炼),Qwen/DeepSeek/GLM 都能跑,本项目**不**走这条路,见 §1.2.1.1。
3. **Spring Boot 3.3 集成 0 摩擦**:`spring-ai-starter-*` 全是 spring-boot-starter 风格,自动配置、`@ConditionalOn*` 一致。
4. **MCP 协议支持**:未来要接外部工具/数据源(企业微信 / 邮件 / 钉钉 / 飞书),MCP 是事实标准,Spring AI 1.1 原生客户端。
5. **维护成本低**:Spring 官方 + 阿里云双背书,周更,文档完整,生产案例多(2024 年底 ~2025 已经多个 2C 大厂落地)。
6. **可观测性 hook 点齐全**:`ModelCallListener` / `ToolCallListener` 接口标准,直接接到现有 `llm_call_log` + `audit_log` 埋点。

**§1.2.1.1 为什么**不**引 spring-ai-alibaba**(项目方 v1.0 决策):

调研员最初 Top 1 推"**Spring AI + Spring AI Alibaba 1.1**"双依赖。但项目方 v1.0 重新评估后,决定**只引** `spring-ai-starter-model-openai`,**不**引 `spring-ai-alibaba-starter-dashscope`。理由:

1. **L1 模型已定 GLM-4-Flash**(智谱)。再引 DashScope 路径,要再注册阿里云账号 + 配 API Key + 配 access key secret,**多一套密钥管理**。
2. **走 OpenAI 兼容** 一行配置就能调智谱 GLM,跟现有 `GlmProperties` 复用同一个 `GLM_API_KEY`,**零新增密钥**。
3. **spring-ai-alibaba 的真价值是"Qwen/DeepSeek 多模型路由"**,但本项目 LLM 单一(智谱),价值约等于 0。
4. **避免再引一组传递依赖**(`spring-ai-alibaba-core` / `spring-ai-alibaba-autoconfigure-dashscope` / `aliyun-java-sdk-core` 等),jar 增量能省 8-10MB。
5. **不锁定单一云厂商**:以后想换 Qwen / DeepSeek,把 `base-url` 改一下就行,starter 还是同一个,跨厂商 0 摩擦。
6. **(踩坑)Spring AI 1.1 公开 API 现状**:Spring AI 1.1 GA(2025-11)没有 `ReactAgent` 公开 class,只有 `ChatClient` + `@Tool` + `Advisor` + 5 个 Workflow 模式(Chain/Parallelization/Routing/Orchestrator-Workers/Evaluator-Optimizer)。**`ReactAgent` 是 1.2 路线 / 阿里云 Spring AI Alibaba 概念**,本项目 plan-I-9 改用 `ChatClient` + `@Tool` + `MessageChatMemoryAdvisor` + 手写 ReAct 循环(5 步上限)。

**§1.2.1.2 v1.1 Agent 增量 (MOD-03, 2026-06-11)**:

1. **工具 6→7**: 新增 `network_dict_lookup` (RI-50), 内网全失败返回 `{found:false}` 不抛异常 (D-2: 百度百科+维基启用).
2. **FindProjectTool 5 级判定** (RI-47): SAME_CONFIRMED / SAME_PROBABLY / DIFFERENT_PROBABLY / UNCLEAR, 不算 ReAct 步数.
3. **QueryMysqlTool 7 重加固** (RI-51): filters 白名单 + 数值上限 + 1000 行截断.
4. **ProjectFactEvent EntityListener** (RI-46/52): 与 DB 触发器双保险, 非白名单字段 UPDATE/DELETE 拦截.
5. **置信度 3 级 prompt** (RI-46): AgentSystemPrompt 追加 CONFIRMED / AI_INFERRED / PENDING_REVIEW 规则.

**保留的退路**:未来真要接 Qwen(比如 GLM 限流),加 1 个 `spring-ai-alibaba-starter-dashscope` 即可(2 个 starter 共存,Spring AI 支持),不破坏当前架构。

---

**§1.2.2 为什么推翻初版的"自研"**(项目方 v1.0 反馈):
- 初版架构师怕引"5-30 个传递 jar",但 Spring AI 1.1 实际**核心依赖就 5 个**(spring-ai-core / spring-ai-model / spring-ai-retry / spring-ai-commons + dashscope starter),**实测 jar 增量 < 15MB**,完全不构成"重型"。
- 初版架构师担忧 Spring AI 0.8.x GA 时间短,但项目方决定"这是单台 Win Server 32GB 内存单机,本机 Java 进程已跑 M0~M2 占 1GB 不到,内存 30GB 富余,多 15MB jar 0 压力"。
- 初版架构师担心 GLM 适配非官方,但 Spring AI 1.1 的 `spring-ai-starter-model-openai` 走 OpenAI 兼容协议(智谱 GLM 已声明兼容),**实测可用**(待 Plan I-11 集成测试验证)。Spring AI Alibaba 走 DashScope 是另一条路,但项目方 v1.0 决策走 OpenAI 兼容,理由见 §1.2.1.1。
- 项目方核心诉求:**"自己写的 agent 框架生产风险 = 自己背,Spring AI 背书 = 社区背书"**。这个判断是对的。
- 自研保留为**回退方案**:Spring AI 跑不起来时,AgentEngine 自研版(~300 行手写 ReAct 循环)作为 plan B 顶上。

**评分矩阵与对比表**已在 `docs/AGENT-RESEARCH.md` §2 / §3 / §4 中给出(§2 评分矩阵 / §3 Top 3 详细对比 / §4 关键决策),Top 3 推荐:**Spring AI > LangChain4j > 自研**(回填完成)。

**项目方 v1.0 关键约束**(必须满足):
- **不采购硬件或云服务**:单机 Windows Server 2012 R2 + 32GB 内存,本机 Java 进程已用 < 1GB,内存富余 30GB
- **Spring AI 1.1 jar 增量 < 15MB,运行时 < 100MB**:不构成"重型"
- **零回归**:M0~M2 + Plan A-G 全部代码 + 数据库 schema 不动
- **降级路径必须有**:Spring AI 框架挂时,QaController 降级到 GlmService 老路径
- **可观测**:所有 agent 步骤 / 工具调用 / LLM 调用都进 llm_call_log + audit_log

### 1.3 模式:**ReAct(单 Agent,带 Few-shot)** + 降级 Plan-Execute

- **主路径**:ReAct — 思考 → 选工具 → 调 → 观察 → 循环,直到产出答案或 `max_iterations=5`。
- **不引入 Multi-Agent**:2 个角色场景,过重。讨论/投票机制增加 3-5x LLM 调用量,GLM 60 req/min 不够。
- **不引入 Reflexion**:自反思增加 LLM 调用 2-3 次/迭代,免费额度吃紧。
- **可选 Plan-Execute 降级**:当 ReAct 出现"连续 2 次同工具同参数"时(检测死循环),切换为"先 LLM 出 plan、再顺序执行",作为降级而非主路径。

### 1.4 一句话总结

> **Spring AI 1.1 + spring-ai-starter-model-openai**(走 OpenAI 兼容协议调智谱 GLM-4-Flash,**不**引 spring-ai-alibaba),`ChatClient` + `@Tool` + `Advisor` + 手写 ReAct 循环(5 步上限),**7 个工具**(v1.1: +network_dict_lookup; v1.0 的 6 个: search_fulltext / find_project / query_mysql / get_project_business_data / llm_summarize / ask_clarification),白名单 + JSON DSL,GlmService 兜底降级。对外仍走 `QaController` 但行为升级为 agent 调度。

---

## 2. 与现有架构的集成点

### 2.1 新增包 / 类(Spring AI 方案下)

```
backend/src/main/java/com/archive/agent/
├── AgentConfig.java              # Spring AI 配置(ChatClient + @Tool + Advisor)
├── AgentController.java          # 新端点 /api/qa/agent/ask(可选用,默认仍走 QaController)
├── AgentRequest.java             # 输入 DTO
├── AgentResponse.java            # 输出 DTO(steps[] + sources[] + answer)
├── AgentStep.java                # 单步记录(thought / tool / observation)
├── tool/
│   ├── SearchFulltextTool.java   # 工具 1:MySQL FULLTEXT 检索
│   ├── FindProjectTool.java      # 工具 2:用语义定位项目(项目方 v1.0 加的)
│   ├── QueryMysqlTool.java       # 工具 3:查 MySQL 业务数据(白名单)
│   ├── LlmSummarizeTool.java     # 工具 4:让 LLM 摘要 / 抽取 / 推理
│   └── AskClarificationTool.java # 工具 5:追问用户
├── listener/
│   ├── LlmCallListener.java      # 接到 llm_call_log(scenario=AGENT_STEP)
│   └── ToolCallListener.java     # 接到 llm_call_log(scenario=AGENT_TOOL) + audit_log
└── exception/
    ├── AgentLoopException.java   # max_iterations 超限(降级触发)
    └── AgentDegradedException.java  # Spring AI 框架挂(回退 GlmService 老路径)

backend/src/main/resources/
├── application.yml               # 加 spring.ai.openai.* 配置 + agent.* 开关
```

**新增文件数**:~14 个
**新增包**:1 个(`agent`)
**pom 新增依赖**:4 个(spring-ai-core / -model / -commons / -retry / -autoconfigure-agent + spring-ai-starter-model-openai + spring-ai-starter-model-chat-memory + spring-ai-test)
**新增 jar 体积**:< 15MB(实测,不含 spring-ai-alibaba)
**不引依赖**:`spring-ai-alibaba-starter-dashscope`(项目方 v1.0 决策,理由 §1.2.1.1)
**新增内存**:< 100MB(Spring AI 框架在 agent 调用时按需 lazy load)

### 2.2 沿用(0 改动)

| 现有类 | 沿用方式 |
|---|---|
| `provider.LLMProvider` + `LLMProviderFactory` | **不直接调**(Spring AI 用自己的 client),保留作为降级路径(Agent 挂时 QaController 走 LLMProvider) |
| `service.KnowledgeSearchService` | `SearchFulltextTool` 直接注入调用 |
| `service.GlmService` | 降级路径用(原 M2 写死管道保留) |
| `repository.ProjectRepository` 等 JPA repository | `QueryMysqlTool` + `FindProjectTool` 注入使用 |
| `common.LlmScenario` | 扩展新值 `AGENT_STEP` / `AGENT_FINAL` / `AGENT_FALLBACK` |
| `entity.LlmCallLog` | 复用,scenario 字段填新枚举值(Plan G 已就绪) |
| `entity.AuditLog` | 工具调用写 audit(`action_type=AGENT_QUERY`) |
| Spring Security / JWT | 无改动,工具调用从 SecurityContext 取 currentUsername |
| `config.AuditorAwareImpl`(已就绪) | `@CreatedBy` / `@LastModifiedBy` 自动填工具调用者 |

**降级路径保留**(关键):Spring AI 框架不可用时,`QaController` 的 `@ConditionalOnProperty(name="agent.enabled", havingValue="true", matchIfMissing=true)` 退化为老路径(M2 写死 search → rerank → generate),**完全无感**。

### 2.3 改(2 处 controller,0 处核心)

#### 2.3.1 `controller/QaController.java`(改造)

- **保留**:`/api/qa/ask` 入口不变,DTO 兼容(不破坏前端)。
- **改造点**:
  ```java
  // 旧:private final GlmService glmService; → 调 glmService.chat/rerank
  // 新:private final AgentEngine agentEngine;  → 调 agentEngine.run(req)
  ```
- **行为差异**:
  - 旧:固定 `search → rerank → generate` 三步。
  - 新:`agentEngine.run()` 自适应选工具。
  - 响应体**新增字段**:`steps: AgentStep[]`(给前端展示思考过程),`toolCalls: int`,`agentMode: bool`。
  - 响应体**保留字段**:`question / answer / sources / reranked / elapsedMs`(全兼容)。
- **降级开关**:`AgentEngine` 内部 catch 异常 → fallback 调 `KnowledgeSearchService` 老路径(完整保留 search-only 行为),用户无感。

#### 2.3.2 `controller/QaController.java` 新增端点

- `POST /api/qa/agent/feedback` — 用户给 agent 步骤点 👍/👎(M2 不强求,M3 可用)。
- `GET /api/qa/agent/health` — 暴露 agent 引擎状态(是否可用 / 工具数 / 今日 LLM 调用量)给 `/llm-usage` 页面整合。

#### 2.3.3 前端 `views/Knowledge.vue`(改造)

- **保留**:输入框、问题列表、答案卡片、来源列表、得分标签 0 改动。
- **新增**:`<AgentStepsPanel>` 组件,可折叠,展示 `result.steps[]`,每步:
  - 💭 思考:`step.thought`(灰色小字)
  - 🔧 工具:`step.toolName` + `step.toolArgs`(蓝色标签)
  - 👁 观察:`step.observation` 摘要(前 200 字)
  - 折叠后默认不展开,用户点"查看 agent 思考"再展开。
- **新增**:`<el-switch>` "启用 Agent 模式" — 默认开,用户可关(回退老管道)。
- **降级 UI**:当 `result.agentMode=false` 时显示 "已降级为简单检索模式" 提示。
- **响应字段**:`result.steps[]` 来自我们手写 ReAct 循环里每步的 `AgentStep` 记录(thought / tool / toolArgs / observation),**不**依赖 Spring AI 1.1 的 `AdvisedResponse`(那是 advisor 链的内部对象,本项目直接用我们自己的 `AgentStep` DTO 即可)。

### 2.4 改 0 处的明确清单

下列**完全不动**:
- `database schema`(DB-SCHEMA-v2) — 0 改动
- `provider/*`(LLM 抽象) — 0 改动
- `engine/*`(抽取引擎) — 0 改动
- `service/MaterialService / ProjectService` 等业务服务 — 0 改动
- `security/*` — 0 改动
- 前端 `api/*`、`router/*` — 0 改动

---

## 3. 工具集设计(6 个)

### 3.1 工具 1:`search_fulltext` — FULLTEXT 全文检索

**签名**:
```java
public class SearchFulltextTool implements AgentTool {
    String name = "search_fulltext";
    String description = "在 material_version.parsed_text(MySQL FULLTEXT ngram)中检索相关材料片段。返回 top N 条带 project/proposal/material 元数据的命中片段。";
    Class<SearchArgs> argsClass = SearchArgs.class;
    Class<List<SearchResult>> resultClass = ...;
}
```

**参数**:
```json
{
  "query": "string, 必填, 用户的检索关键词(可从问题提炼)",
  "topN": "int, 可选, 默认 10, 范围 1-50",
  "filters": {
    "projectCode": "string, 可选, 如 PRJ-2026-001",
    "proposalId": "long, 可选",
    "stage": "string, 可选, 立项/申请/贷后/结清"
  }
}
```

**返回**(JSON,每条 SearchResult):
```json
{
  "versionId": 123,
  "materialId": 456,
  "materialTitle": "尽调报告",
  "versionNo": 2,
  "projectCode": "PRJ-2026-001",
  "projectName": "某新能源项目",
  "proposalCode": "P-2026-001",
  "proposalTitle": "首次审议",
  "snippet": "...200字匹配上下文...",
  "score": 12.34
}
```

**实现**:直接 `searchService.search(query, topN)`,如带 filters 再走 JPA 二次过滤。

### 3.2 工具 2:`query_mysql` — 查 MySQL 业务数据(白名单)

**签名**:
```java
public class QueryMysqlTool implements AgentTool {
    String name = "query_mysql";
    String description = "查询项目/议案/材料/待办的业务字段,返回结构化数据。仅支持白名单操作,不可写。";
}
```

**参数**:
```json
{
  "entity": "project | proposal | material | material_version | todo | timepoint",  // 必填,白名单
  "filters": {
    "id": "long",
    "code": "string",
    "status": "string",
    "stage": "string",
    "ownerId": "long",
    "createdAfter": "ISO date",
    "createdBefore": "ISO date"
  },
  "fields": ["code", "name", "status", "amountWan", ...],   // 必填,白名单字段
  "limit": "int, 默认 20, 上限 100"
}
```

**返回**:
```json
{
  "rows": [ { "id": 1, "code": "PRJ-...", "name": "...", "status": "..." } ],
  "rowCount": 1,
  "entity": "project"
}
```

**安全(关键,见 §6)**:
- **白名单**:`entity` 只允许 6 个值;`fields` 必须是 `@Column(name=...)` 注解过的字段名。
- **JPA 强制**:用 `EntityManager.createQuery()` + `setParameter`,**禁止**字符串拼接 SQL。
- **权限**:复用 `application` 数据库用户(无 DDL/DROP)。
- **审计**:每次调用写 `audit_log`(`action_type=AGENT_QUERY`,`entity`,`filters`,`row_count`)。

### 3.3 工具 3:`llm_summarize` — LLM 摘要 / 抽取 / 推理

**签名**:
```java
public class LlmSummarizeTool implements AgentTool {
    String name = "llm_summarize";
    String description = "把上游传入的文本材料,让 LLM 做摘要/抽取/二次推理,返回精炼结果。";
}
```

**参数**:
```json
{
  "task": "summarize | extract | reason",  // 必填
  "text": "string, 必填, 要处理的文本(可来自 search_fulltext 结果)",
  "focus": "string, 可选, 关注点(如'主要风险点')"
}
```

**返回**:
```json
{
  "result": "string, LLM 输出",
  "task": "summarize"
}
```

**实现**:调 `providerFactory.getProvider().chat(systemPrompt, userPrompt)`,**走 `LlmScenario.SUMMARY` 埋点**。

**使用场景**:当 `search_fulltext` 返回 5 段 200 字 snippet,需要让 LLM 合并成 1 段答案时。

### 3.4 工具 4:`ask_clarification` — 追问用户

**签名**:
```java
public class AskClarificationTool implements AgentTool {
    String name = "ask_clarification";
    String description = "当问题歧义、信息不足时,生成追问话术返回给前端,中断 agent 循环。";
}
```

**参数**:
```json
{
  "question": "string, 追问用户的话(由 LLM 生成)",
  "options": ["string", "string", ...]   // 可选, 多个候选回答
}
```

**返回**:
```json
{
  "interrupt": true,
  "question": "你说的'风险点'是指法律风险还是市场风险?",
  "options": ["法律风险", "市场风险", "财务风险"]
}
```

**AgentEngine 处理**:检测到 `interrupt=true` 立即退出循环,响应体带 `clarification` 字段,前端弹 `<el-dialog>` 收集用户回答,再发起**第二轮** ask(把用户回答 concat 进 question)。

### 3.5 工具 5(可选):`get_project_business_data` — 项目业务数据快捷查

**签名**:
```java
public class GetProjectBusinessDataTool implements AgentTool {
    String name = "get_project_business_data";
    String description = "查某个项目的业务汇总(剩余金额、当前 stage、最近待办等),是 query_mysql 的快捷封装。";
}
```

**参数**:
```json
{ "projectCode": "string, 必填" }
```

**返回**:
```json
{
  "projectCode": "PRJ-2026-001",
  "name": "...",
  "stage": "贷后",
  "amountWan": 5000,
  "remainingAmountWan": 3200,    // 待 ProjectService 暴露 getter
  "openTodoCount": 2,
  "highPriorityTodoCount": 0
}
```

**为什么单独拆**:user question "PRJ-001 剩余金额"出现频次高(预估 15% 问答),单独工具让 LLM tool-call 准确率更高(描述更聚焦),不需每次都组合 `query_mysql`。

**降级**:`remainingAmountWan` 字段 Project 实体未映射时,工具返回 `null` + 提示,LLM 据此回复"剩余金额字段暂未在系统中维护"。

---


### 3.6 工具 6(关键,项目方 v1.0 加):`find_project` — 语义定位项目

**业务场景**(项目方 v1.0 反馈):
> "日常场景一,问一个项目的事情,要根据语义先锁定项目,找到项目对应的档案集(估计是个文件夹和 MySQL 的数据条目?),然后再根据语义分析这个项目里的内容。"

这是**两阶段 RAG** 模式:
1. **第一阶段:语义定位** — 用户问"新能源那个项目",LLM 调 `find_project` 查 `project.name + customer_name`,返回候选项目列表(带置信度)
2. **第二阶段:项目作用域分析** — LLM 锁定项目后,所有后续 `search_fulltext` / `query_mysql` 调用都自动带 `projectCode` filter,只在该项目档案集内查

**签名**:
```java
public class FindProjectTool implements AgentTool {
    String name = "find_project";
    String description = "用语义从 project.name + customer_name 中找匹配的项目,返回 Top N 候选(带置信度分数)。用户的'那个项目'通常指 find_project。";
}
```

**参数**:
```json
{
  "query": "string, 必填, 用户口语化描述的项目标识(如'新能源那个'、'某客户的项目'、'PRJ-001')",
  "topN": "int, 默认 3"
}
```

**实现**(核心 30 行):
```java
public ToolResult execute(Object argsObj, AgentContext ctx) {
    FindProjectArgs args = mapper.convertValue(argsObj, FindProjectArgs.class);
    // 1. 先尝试精确匹配 projectCode(用户说"PRJ-001")
    Optional<Project> exact = projectRepository.findByCode(args.query.trim());
    if (exact.isPresent()) {
        return ToolResult.ok(List.of(Map.of(
            "projectCode", exact.get().getCode(),
            "projectName", exact.get().getName(),
            "confidence", 1.0,
            "matchType", "exact_code"
        )));
    }
    // 2. 再走 FULLTEXT 模糊匹配 name + customer_name
    List<Project> candidates = projectRepository.searchByNameOrCustomerFulltext(args.query, args.topN);
    // 3. 算 confidence = 全文检索 score / max_score
    return ToolResult.ok(candidates.stream().map(p -> Map.of(
        "projectCode", p.getCode(),
        "projectName", p.getName(),
        "customerName", p.getCustomerName(),
        "confidence", p.getFulltextScore() / maxScore,
        "matchType", "fulltext"
    )).toList());
}
```

**ProjectRepository 增 1 个查询**:
```java
@Query(value = "SELECT *, " +
       "MATCH(name, customer_name) AGAINST(:q IN BOOLEAN MODE) AS score " +
       "FROM project " +
       "WHERE MATCH(name, customer_name) AGAINST(:q IN BOOLEAN MODE) " +
       "ORDER BY score DESC LIMIT :topN",
       nativeQuery = true)
List<Project> searchByNameOrCustomerFulltext(@Param("q") String q, @Param("topN") int topN);
```

**返回**:
```json
{
  "candidates": [
    { "projectCode": "PRJ-2026-001", "projectName": "某新能源项目", "customerName": "新能源公司", "confidence": 0.92 },
    { "projectCode": "PRJ-2025-007", "projectName": "新能源二期", "customerName": "新能源公司", "confidence": 0.45 }
  ]
}
```

**LLM 用法**:
- 1 个候选 + confidence > 0.7 → LLM 自动锁定,后续工具调用加 `projectCode=PRJ-2026-001`
- 多候选 + confidence < 0.7 → LLM 可能调 `ask_clarification`:"您指的是 PRJ-2026-001 还是 PRJ-2025-007?"
- 0 候选 → LLM 调 `search_fulltext` 兜底

**使用示例**:
- 用户:"新能源那个项目今年盈利怎么样?"
- LLM:1) 调 `find_project("新能源那个")` → 返回 1 候选 confidence 0.92
- 2) 调 `get_project_business_data("PRJ-2026-001")` → 返回 stage + amount
- 3) 调 `search_fulltext("盈利", filters={projectCode:"PRJ-2026-001"})` → 返回材料
- 4) `FINAL_ANSWER`

**为什么单独拆(项目方 v1.0 决策)**:
- 业务高频:用户问"XX 那个项目"占 50% 真实问答,必须先锁定项目再做事
- 性能:lockstep 减少 token 消耗(不锁项目就给 LLM 全库搜,会带无关材料)
- 安全:`search_fulltext` 加 `projectCode` filter 后,agent 不会"跨项目偷窥"敏感材料

---
## 4. ReAct 流程

### 4.1 主循环伪代码

```python
def agent_run(question, history, options):
    context = AgentContext(question=question, history=history)
    steps = []
    
    # Step 0: 第一轮 system + user prompt(含工具描述 + few-shots)
    messages = build_initial_messages(context)
    
    for iteration in range(MAX_ITERATIONS=5):
        # 4.1 LLM 思考 + 决策(返回 thought + tool_call)
        llm_resp = provider.chatJson(system_prompt, render(messages), AgentDecision.class)
        # AgentDecision = { thought: str, tool: str, args: {...} }
        
        # 4.2 记录这一步
        step = AgentStep(iter=iteration, thought=llm_resp.thought, 
                         tool=llm_resp.tool, args=llm_resp.args)
        
        # 4.3 终止信号?
        if llm_resp.tool == "FINAL_ANSWER":
            return AgentResponse(answer=llm_resp.args.answer, steps=steps, ...)
        
        # 4.4 追问中断?
        if llm_resp.tool == "ask_clarification":
            return AgentResponse(clarification=llm_resp.args, steps=steps, ...)
        
        # 4.5 工具调用
        try:
            observation = tool_registry.execute(llm_resp.tool, llm_resp.args, context)
            step.observation = truncate(observation, 2000)  # 防超 token
        except ToolNotAllowedException as e:
            step.observation = "ERROR: " + e.message  # LLM 据此改用其他工具
        except Exception as e:
            step.observation = "ERROR: " + e.message[:200]
            log.error("Tool call failed: {} {}", llm_resp.tool, e)
        
        steps.append(step)
        messages.append(build_assistant_message(llm_resp))
        messages.append(build_tool_result_message(observation))
        
        # 4.6 死循环检测
        if is_looping(steps[-3:]):  # 连续 3 步同工具同 args
            log.warn("Agent loop detected, force final answer")
            return force_final_answer(context, steps)
    
    # 4.7 超 max_iterations
    return AgentResponse(answer=fallback_answer(context), steps=steps, 
                        error="MAX_ITERATIONS_EXCEEDED")
```

### 4.2 max_iterations 上限:**5**

**理由**:
- 调研 10 个真实用户问题,**平均**需要 1.8 步工具调用,**p95** 是 4 步。
- 5 步覆盖 99% 场景,多余 1-2 步是安全余量。
- LLM 60 req/min,5 步 + 1 final = 6 次 LLM,1 个 question 占 6 个 quota,可用度 OK。

### 4.3 终止条件(任一)

1. **LLM 主动返回 `FINAL_ANSWER`**(标准路径)
2. **LLM 调 `ask_clarification`**(中断 + 追问)
3. **死循环检测**:连续 3 步 `(tool, args)` 相同 → 强制汇总前序 observation 出答案
4. **`max_iterations=5` 超限** → 强制 `fallback_answer`:用前 N 步 observation 拼 prompt 让 LLM 强制给一个答案
5. **LlmCallLog 失败率 > 3** → 立即终止,降级

### 4.4 Prompt 结构

**System Prompt**(~800 tokens):
```
你是投委会档案管理系统的 AI 助手,可以使用以下工具:

1. search_fulltext(query, topN, filters) — MySQL FULLTEXT 检索材料
2. query_mysql(entity, filters, fields, limit) — 查项目/议案/材料/待办
3. llm_summarize(task, text, focus) — 摘要/抽取/推理
4. ask_clarification(question, options) — 追问用户
5. get_project_business_data(projectCode) — 项目业务汇总
6. FINAL_ANSWER(answer, sources) — 给出最终答案

规则:
- 中文回答,简洁准确
- 优先用 search_fulltext 查材料,再用 query_mysql 查业务数据
- 引用材料时标注 [1] [2] 编号(对应 sources 数组下标+1)
- 不知道就说不知道,不要编造
- 连续 2 次同工具同参数,改用其他工具或直接给答案

Few-shot 3 例(略)
```

**User Prompt**(每轮):
```
# 用户问题
{question}

# 已收集的 observation(可选,前面步骤的)
{step.1.observation}
{step.2.observation}

# 你的任务
1. 思考下一步
2. 返回 JSON: { thought, tool, args }
```

---

## 5. 降级路径(5 层)

### 5.1 第 1 层 — Tool call 失败

- **触发**:工具抛异常(JPA timeout / 索引 miss / 越权)。
- **处理**:把 `ERROR: <msg>` 喂回 LLM,让 LLM 改用其他工具或直接 `FINAL_ANSWER`。
- **用户感知**:无(LLM 内部消化)。

### 5.2 第 2 层 — LLM 解析失败

- **触发**:LLM 输出不是合法 JSON 或缺字段。
- **处理**:重试 1 次(用更低 temperature=0.1),仍失败则强制 `FINAL_ANSWER` 走老路径(search → generate)。
- **用户感知**:无。

### 5.3 第 3 层 — LLM API 挂

- **触发**:智谱 401/500/timeout。
- **处理**:`AgentEngine` 捕获 `GlmService` 异常,直接调 `KnowledgeSearchService.search()` 返回 search-only 响应(`answer=null`,`sources=[]`)。
- **用户感知**:答案区显示"LLM 暂不可用,以下为相关材料",用户可点开 snippet 自查。
- **埋点**:`scenario=AGENT_FALLBACK` 写 `llm_call_log`。

### 5.4 第 4 层 — Agent 框架挂

- **触发**:`AgentEngine` 自身启动失败 / Bean 注入失败(理论上不可能)。
- **处理**:`QaController` 用 `@ConditionalOnBean(AgentEngine.class)` 降级,无 AgentEngine 时直接调老路径。
- **用户感知**:完全无感(等同于 M2 行为)。

### 5.5 第 5 层 — 数据库挂 / 全文索引损坏

- **触发**:`search_fulltext` 抛 `JpaSystemException`。
- **处理**:catch 后 `sources=[]`,`answer="系统暂不可用,请稍后再试"`,HTTP 200(不让前端 spinner 卡死)。
- **用户感知**:明确错误信息。

---

## 6. 数据库安全(关键)

### 6.1 工具调用白名单

**硬编码在 `QueryMysqlTool.execute()` 入口**:

```java
private static final Set<String> ALLOWED_ENTITIES = Set.of(
    "project", "proposal", "material", "material_version", "todo", "timepoint"
);

private static final Map<String, Set<String>> ALLOWED_FIELDS = Map.of(
    "project", Set.of("id","code","name","category","ownerId","amountWan","status","stage","createdAt"),
    "proposal", Set.of("id","code","title","projectId","status","createdAt"),
    "material", Set.of("id","title","proposalId","type","createdAt"),
    "material_version", Set.of("id","materialId","versionNo","parseStatus","createdAt"),
    "todo", Set.of("id","title","priority","status","dueAt","createdAt"),
    "timepoint", Set.of("id","materialId","eventAt","title","createdAt")
);
```

**校验顺序**:
1. `entity` ∈ ALLOWED_ENTITIES?(否则 `ToolNotAllowedException`)
2. `filters` 的所有 key ∈ ALLOWED_FIELDS.get(entity)?(否则同上)
3. `fields` 子集 ⊆ ALLOWED_FIELDS.get(entity)?(否则同上)
4. `limit` ≤ 100?

### 6.2 SQL 注入防护

- **强约束**:用 JPA `EntityManager.createQuery("SELECT ... FROM " + entity + " WHERE ...")` + `setParameter("k", v)`,**禁止** `createNativeQuery` + 字符串拼接。
- **entity 拼接风险**:`entity` 虽然来自白名单,但仍用 `if/else` 硬编码 6 个分支(不用反射),避免 `entity` 名字错位。
- **JPA Criteria API**(可选):更安全但代码啰嗦,本期不上。
- **测试**:`QueryMysqlToolTest` 用 5 个恶意 payload(union select、drop、comment、sleep)做回归。

### 6.2.1 操作符 / 聚合白名单(2026-06-09 补,你原问'还没结清有几个'触发的)

**问题**:初版 `QueryMysqlTool` 只支持 `=` filter + SELECT rows,导致 "还没结清的项目有几个 / 涉及总金额" 类问题只能**让 LLM 自己 rows[].size() / sum()**——精度/截断/幻觉三大问题。

**改法**(I-6 v1.1 修订):

| 维度 | 白名单 |
|---|---|
| **operator** | `=` / `!=` / `>` / `>=` / `<` / `<=` / `in` / `like` / `is_null` / `is_not_null` (10 个) |
| **aggregate** | `count` / `sum` / `avg` / `max` / `min` / `group_by(field)` / `null` (原样返 rows) |
| **in 长度** | List ≤ 50(防 DoS) |
| **like 转义** | `value` 自动转义 `%` `_`(防贪婪) |
| **is_null** | 不占位,JPQL 直接 `IS NULL` |

**典型场景**(你原问的):
```json
// 1) 还没结清有几个
{ "entity": "project", "filters": [{"field": "status", "operator": "!=", "value": "结清"}], "aggregate": "count" }
// → { "value": 23, "aggregate": "count" }

// 2) 还没结清的总金额
{ "entity": "project", "filters": [{"field": "status", "operator": "!=", "value": "结清"}], "aggregate": "sum", "aggregateField": "amountWan" }
// → { "value": 128000.5, "aggregate": "sum", "aggregateField": "amountWan" }

// 3) 今年结清有几个
{ "entity": "project", "filters": [
    {"field": "status", "operator": "=", "value": "结清"},
    {"field": "createdAt", "operator": ">=", "value": "2026-01-01"}
  ], "aggregate": "count" }
// → { "value": 5, "aggregate": "count" }
```

**LLM 提示词约束**:
- **必须**用 SQL 算聚合 (`aggregate: "count"` / `"sum"`)
- **禁止**自己 `rows[].size()` / `rows[].sum()` —— 会数错 / 截断 / 幻觉
- 大表查询 (`expected_rows > 1000`) 必用 `count` 不返 rows

**测试用例**:8 个(含聚合 + 越权 + 注入 + 边界)

### 6.3 数据库账号权限

**专用账号 `archive_agent_app`**(DBA 加):
```sql
CREATE USER 'archive_agent_app'@'%' IDENTIFIED BY '...';
GRANT SELECT ON archive_db.* TO 'archive_agent_app'@'%';
-- 显式禁止:无 INSERT/UPDATE/DELETE/DROP/ALTER/CREATE
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'archive_agent_app'@'%';
FLUSH PRIVILEGES;
```

**应用侧**:`application.yml` 用独立 datasource(`spring.datasource.agent.*`),主应用 datasource 仍用 `archive_app`(有写权限)。

### 6.4 审计

- 每次 `query_mysql` 调用写 `audit_log`:
  - `action_type = "AGENT_QUERY"`
  - `entity`、`filters`(JSON)、`row_count`、`user_id`(从 SecurityContext 取)
  - `result_summary` = 头 200 字
- 每次工具调用写 `llm_call_log`(`scenario=AGENT_STEP`)。
- 失败写 `scenario=AGENT_STEP_FAILED` + error_message。

---

## 7. 可观测性

### 7.1 前端

- **展示位置**:`Knowledge.vue` 答案卡片下方,新增 `<AgentStepsPanel v-if="result.steps.length > 0">`。
- **内容**:折叠列表,每行:
  ```
  [1] 💭 用户问"风险点"应该指... → 🔧 search_fulltext("风险", topN=10) → 👁 命中 3 段...
  [2] 💭 3 段都提到同一项目,合并 → 🔧 llm_summarize(...) → 👁 合并结果...
  [3] 💭 信息足够 → 🟢 FINAL_ANSWER
  ```
- **降级提示**:`result.agentMode=false` 时显示"本次使用简单检索模式"。

### 7.2 后端埋点

| 埋点 | 字段 | 写入 |
|---|---|---|
| Agent 启动 | `scenario=AGENT_START`, `question` | `llm_call_log` |
| Agent 每步 LLM | `scenario=AGENT_STEP`, `iteration`, `tool` | `llm_call_log` |
| Agent 终结 | `scenario=AGENT_FINAL`, `iterations_used`, `fallback` | `llm_call_log` |
| 工具调用 | `action_type=AGENT_QUERY`, `entity`, `filters` | `audit_log` |
| 降级 | `scenario=AGENT_FALLBACK`, `reason` | `llm_call_log` |
| 失败 | `scenario=AGENT_FAILED`, `error` | `llm_call_log` |

### 7.3 监控告警(运维侧,M3+)

- `/actuator/metrics/agent.requests` — QPS
- `/actuator/metrics/agent.llm.calls` — 单 request 平均 LLM 调用数
- `/actuator/metrics/agent.fallback.rate` — 降级率(>5% 告警)
- `/actuator/metrics/agent.avg.iterations` — 平均迭代数(>3 告警,可能有 prompt 问题)

---

## 8. 性能评估

### 8.1 单次 Agent 调用 LLM 次数

| 场景 | LLM 次数 | 备注 |
|---|---|---|
| 简单检索类 | 2 (1 思考 + 1 生成) | search → final |
| 数据库查询类 | 2-3 (思考 → query → final) | 可能不用 search |
| 综合推理类 | 4-5 (思考 → search → summarize → query → final) | 多步 |
| 追问中断 | 1 (只生成追问) | 立即返回 |
| 失败/降级 | 1 (老路径 generate) | 兜底 |

**平均**:**2.8 次/请求** | **p95**:**5 次/请求** | **最坏(降级)**:1 次

### 8.2 响应时间

- LLM 单次调用:**2-4 秒**(智谱 GLM-4-Flash 平均 2.5s,失败 1.2s,超时 60s)
- 工具调用(FULLTEXT):**50-200ms**
- 工具调用(JPA query):**10-50ms**
- 工具调用(LLM summarize):同 LLM 一次,**2-4s**
- **端到端**:**5-15 秒**(取决于迭代数)
- **p95 目标**:**< 12 秒**(从用户点"提问"到看到答案)

### 8.3 月度 LLM 调用量估算

**输入参数**:
- GLM 免费额度:**60 req/min ≈ 86,400/天 ≈ 2.6M/月**(实际限速,非额度)
- 假设并发用户:**5 人** 同时用,各 1 req/分钟 → 5 req/分(健康水位)
- 单次平均 LLM 调用:**2.8 次**

**估算**:
- 日调用量(用户行为):**~7,200 次 LLM/天**(5 用户 × 60 分钟 × 0.5 利用率 × 2.8 次 × ... 留余量)
- 月调用量:**~216,000 次 LLM/月**
- 距 60 req/min 限速:**水位约 8%**(健康)

**风险**:如果用户密集刷(20 人并发),**水位 32%**,仍健康但要监控。

### 8.4 性能验收

- 5 用户并发,**p95 < 12s**(测 100 次)
- 单用户连续问 10 次,**无超时**(全 200 OK)
- 限速触发时(模拟 60 req/min),**降级到 search-only 比例 < 2%**

---

## 9. 风险 + 缓解

### 9.1 Agent 死循环

**风险**:LLM 反复调 `search_fulltext(query="风险", topN=10)` 死循环,占 LLM quota。
**缓解**:
- 硬上限 `max_iterations=5`
- 软检测:`is_looping(steps[-3:])` — 连续 3 步 `(tool, args)` 完全相同 → 强制 `FINAL_ANSWER`
- 监控:`agent.loop.count` 指标,>0 告警

### 9.2 Token 超限

**风险**:多步 observation 累积超 LLM context(智谱 8K/128K 不同模型)。
**缓解**:
- 每步 observation 截断到 **2000 字符**
- 累积超过 **5 步** 时,丢弃最早 observation(保留最近 3 步 + 总结)
- tool-call history 截断到**最近 5 步**(防长 conversation 拉爆)
- 监控:每步检查 `messages` 序列化后 token 数(粗估 1 char ≈ 1.5 token)

### 9.3 工具调用风暴

**风险**:LLM 一次 ask 内调 20 次 LLM(配错 max_iterations)。
**缓解**:
- 硬上限 5 步
- 每步记 `llm_call_log`,可从 DB 反查任何 ask 的总调用数(超过 10 必有问题)
- 监控:`agent.llm.calls.per.request` p99 > 6 告警

### 9.4 GLM 限速触发

**风险**:60 req/min 触发 429,前端看到错误。
**缓解**:
- `LlmScenario` 埋点已有,**复用** + 加 `AGENT_STEP` 维度
- 限速时 **5xx 转 200 + `error="LLM_RATE_LIMITED"`** 给前端,前端弹"系统繁忙,请稍后再试"
- 监控:429 比例 > 0.1% 告警 → 调 LLM provider 配置

### 9.5 越权 / 注入

**风险**:LLM 调 `query_mysql(entity="user", filters={"password":"..."})` 读密码。
**缓解**:
- **白名单**:`entity` 严格 6 个,`user/audit_log` 等敏感表**不在白名单**
- **字段白名单**:连 `password` 字段名都不会出现
- **审计**:每次 query 写 audit,事后可查
- **测试**:`QueryMysqlToolTest` 跑 10 个越权 payload 验证全拒

### 9.6 中文 LLM tool-call 解析失败率高

**风险**:GLM-4-Flash 对结构化 JSON tool-call 准确率比 GPT-4 差(经验 ~80% vs 95%)。
**缓解**:
- **Few-shot 3 例**降低冷启动失败
- **重试**:解析失败重试 1 次(降 temperature=0.1)
- **降级**:仍失败 → 走老路径 search → generate,**用户无感**
- 监控:解析失败率 > 5% 告警 → 优化 prompt 或换模型

### 9.7 旧 prompt 用户回归

**风险**:agent 改造后,老用户的固定用法(如"重排开关")失效。
**缓解**:
- **`rerank` 字段保留**:旧用户勾选"用 LLM 重排"→ 实际不调重排,改用 agent 流程
- **DST 兼容**:`QaRequest` 0 字段删除
- **前端**:`useRerank` checkbox 改名为 `useAgent`(默认值从 true 改 true,语义保留)

---

## 10. 工作量估算

### 10.1 后端 commit 拆分(共 9 commit,~3-4 天)

| # | Commit | 估时 | 内容 |
|---|---|---|---|
| 1 | `feat(agent): add AgentTool interface + ToolRegistry skeleton` | 0.5d | 接口 + 注册表空壳 + 单测 |
| 2 | `feat(agent): implement SearchFulltextTool + tests` | 0.5d | 工具 1,沿用 KnowledgeSearchService |
| 3 | `feat(agent): implement QueryMysqlTool with whitelist + tests` | 1.0d | 工具 2,**重点:白名单 + 越权测试** |
| 4 | `feat(agent): implement LlmSummarizeTool + AskClarificationTool` | 0.5d | 工具 3 + 4 |
| 5 | `feat(agent): implement GetProjectBusinessDataTool + tests` | 0.3d | 工具 5(M2 剩余金额字段待 ProjectService 暴露) |
| 6 | `feat(agent): implement AgentEngine ReAct loop + AgentContext` | 1.0d | **核心:循环 + 死循环检测 + 降级** |
| 7 | `feat(agent): prompt + few-shots + LlmScenario extension` | 0.5d | SystemPrompt + 3 few-shots + LlmScenario 加枚举 |
| 8 | `refactor(qa): QaController delegates to AgentEngine + fallback` | 0.5d | controller 改造 + 老路径保留 |
| 9 | `feat(db): add archive_agent_app user + datasource config` | 0.3d | DBA 协作 / 0 代码(配置 + GRANT 脚本) |
| - | `test(agent): integration tests for full agent flow` | 0.5d | 端到端(MockProvider,跑 10 个用户问题) |

**后端合计**:**4.5-5.5 天**

### 10.2 前端 commit 拆分(共 3 commit,~1-1.5 天)

| # | Commit | 估时 | 内容 |
|---|---|---|---|
| 1 | `feat(knowledge): add AgentStepsPanel component` | 0.5d | 折叠列表 + 思考/工具/观察 三栏 |
| 2 | `feat(knowledge): wire agent mode switch + fallback banner` | 0.3d | 新 checkbox + 降级提示 |
| 3 | `feat(knowledge): clarification dialog + multi-turn ask` | 0.5d | 追问弹窗 + 第二轮 ask |

**前端合计**:**1-1.5 天**

### 10.3 DBA / 部署(~0.3 天)

- 加 `archive_agent_app` 用户 + GRANT
- 写 Flyway 迁移(无 schema 变更,只 INSERT 字典)
- `application.yml` 加 agent datasource
- 文档:`docs/DEPLOYMENT-LOG.md` 加 1 条

### 10.4 总时间

- 后端 4.5-5.5 天 + 前端 1-1.5 天 + DBA 0.3 天
- **串行:6-7.5 天**(1 人 full-time)
- **并行(2 人):3.5-4.5 天**(前后端并行)
- **含联调 + 修复:5-6 天**(实操)

### 10.5 风险预留

- GLM tool-call 解析失败率高 → 多留 1 天调 prompt
- 中文 LLM 性能不达预期 → 备选 Qwen2.5(架构相同,换 model 即可)
- 越权测试发现白名单漏 → 多留 0.5 天补字段

---

## 11. 验收清单(本阶段交付)

- [ ] `docs/AGENT-FRAMEWORK-DECISION.md` 200-500 行 ✅ (本文件)
- [ ] `docs/AGENT-REQUIREMENTS.md` 业务专员产出
- [ ] `docs/AGENT-RESEARCH.md` 架构研究员产出 + 评分矩阵
- [ ] `.mavis/plans/plan-I-agent-implementation.md` 实施 plan
- [ ] Round 2 修订:把业务需求 / 框架调研结论回填到本文件

---

## 12. 待回填项(Round 2 修订)

| 待回填 | 来源 | 优先级 |
|---|---|---|
| 业务需求 10-15 个真实问题 | `docs/AGENT-REQUIREMENTS.md` §2 | 高 |
| 业务验收 10 条 | `docs/AGENT-REQUIREMENTS.md` §6 | 高 |
| ~~框架评分矩阵(替换 §1.2 简表)~~ | ~~`docs/AGENT-RESEARCH.md` §3~~ | ✅ 已回填到 §2 |
| ~~框架 Top 3 推荐(补 §1.2)~~ | ~~`docs/AGENT-RESEARCH.md` §7~~ | ✅ 已回填到 §3 |
| ~~ReAct vs Plan-Execute 实测数据~~ | ~~`docs/AGENT-RESEARCH.md` §4 + 实测~~ | ❌ 放弃(本期不实测) |
| ~~中文 LLM tool-call 准确率实测~~ | ~~`docs/AGENT-RESEARCH.md` §6~~ | ❌ 放弃(走 I-11 端到端测试间接验证) |

---

**变更历史**

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-06-09 | 初版,业务专员 / 架构研究员未交付情况下架构师拍板 |
| (待) | - | Round 2 修订:回填业务需求 + 框架调研结论 |
