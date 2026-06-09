# Plan I: Agent 智能问答实施(Spring AI 方案)

> **配套决策**:`docs/AGENT-FRAMEWORK-DECISION.md` v1.0(Spring AI 1.1 + `spring-ai-starter-model-openai`,**不**引 spring-ai-alibaba,理由见决策 §1.2.1.1)
> **配套业务**:`docs/AGENT-REQUIREMENTS.md`
> **配套调研**:`docs/AGENT-RESEARCH.md`
> **作者**: 架构师(Mavis)
> **接手 Agent**: **本 plan 由"生产环境 AI Agent"接手执行代码**——接手前**必读** §0「接手 Agent 必读」
> **基线 commit**: `e5a0208`(M0~M2 + Plan A~G 完工)
> **目标**:把现有 `QaController` 写死管道升级为 **Spring AI 1.1 智能 Agent**(`ChatClient` + `@Tool` + 手写 5 步 ReAct 循环 + `MessageChatMemoryAdvisor` 多轮对话),6 工具 / 白名单 / 5 步上限 / GlmService 兜底,**零回归**。

---

## §0. 接手 Agent 必读(本段最重要!)

### 0.1 你是谁,你在做什么

你是**生产环境的 AI Agent**,负责按本 plan 实施代码。沙箱里的我(Mavis)只**出方案 + 修决策**,**不写代码**。你的产出是 commit 推到 `minimax` 分支。

### 0.2 你必须遵守的纪律

1. **必读文档** — 开工前 `cat` 这些文件(沙箱里的我已经写好):
   - `docs/AGENT-RESEARCH.md` (194 行,7 框架调研,§2 评分 + §3 Top 3 + §4 决策)
   - `docs/AGENT-REQUIREMENTS.md` (257 行,业务需求,15 真实问题)
   - `docs/REQUIREMENTS-v1.md` (872 行,业务全貌)
   - `docs/ARCHITECTURE-v2.md` (架构基线)
   - `docs/DEV-STANDARDS.md` (开发标准 + 完工交回清单) ← **必读** §7 完工交回清单
   - `docs/TEAM-ARCHIVE.md` (环境/部署/沙箱/数据库)
   - `docs/LESSONS-LEARNED.md` (踩坑记录,15+ 条)
   - `docs/AGENT-FRAMEWORK-DECISION.md` v1.0 (本 plan 配套决策,**必读**)
   - `docs/AGENT-REQUIREMENTS.md` (业务需求,15 个真实问题)
   - `docs/AGENT-RESEARCH.md` (调研)

2. **commit 粒度** — 每子项一个 commit + push,不打囤
3. **完工必跑**:
   - `mvn compile -DskipTests -B -o` (沙箱编译 0 错)
   - `npm run build` (前端)
   - 浏览器端到端跑过(每个子项的"验收"段)
4. **完工交回清单** — 按 `docs/DEV-STANDARDS.md` §7.2,owner 才能审你的 PR
5. **如遇 LLM 框架装不上 / 调不通** — **不要死磕**,在 `deliverable.md` 报告"卡住:XXX",我会接管

### 0.3 关键技术点(必看)

- **Spring AI 1.1 GA 已发布**(2025-11),用 `spring-ai-starter-model-openai` 兼容智谱 GLM(智谱声明 OpenAI 兼容)
- **本项目选 OpenAI 兼容路径**(走 `spring-ai-starter-model-openai` + 智谱 base-url)
- **不引 `spring-ai-alibaba-starter-dashscope`**: L1 已定智谱 GLM,再引 DashScope 得多 1 套阿里云密钥 + 1 组传递依赖,价值约等于 0。详见 `AGENT-FRAMEWORK-DECISION.md` §1.2.1.1
- **实际 jar 增量 < 15MB,运行时 < 100MB,完全适合 32GB 单机**
- **(踩坑预警!)Spring AI 1.1 公开 API**: **没有** `ReactAgent` 公开 class(那是 1.2 路线 / 阿里云 Spring AI Alibaba 概念)。本项目实际 API 是 `ChatClient` + `@Tool` 注解 + `MethodToolCallbackProvider` + `Advisor` 拦截链 + `MessageChatMemoryAdvisor`(多轮对话)。**5 步 ReAct 循环在 `AgentEngine` 里手写**,Spring AI 1.1 不内置。

**资源链接**:
- Spring AI 官方文档: <https://docs.spring.io/spring-ai/reference/> (1.1 GA 2025 年底发布)
- Spring AI GitHub: <https://github.com/spring-projects/spring-ai>
- Maven Central 搜 `spring-ai-starter-model-openai` 1.1.0: <https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-starter-model-openai/versions>
- 智谱 GLM OpenAI 兼容 API 文档: <https://open.bigmodel.cn/dev/api/openai-sdk> (其实就是 OpenAI SDK + base_url=https://open.bigmodel.cn/api/paas/v4)
- 智谱 API Key 申请: <https://open.bigmodel.cn/usercenter/apikeys> (项目方已有 `GLM_API_KEY`,直接复用)

**配置片段**(写进 `application.yml`):
```yaml
spring:
  ai:
    openai:
      api-key: ${GLM_API_KEY}     # 复用现有 env 变量,不新引
      base-url: https://open.bigmodel.cn/api/paas/v4   # 智谱 OpenAI 兼容入口
      chat:
        options:
          model: glm-4-flash       # 智谱免费模型
          temperature: 0.3
          max-tokens: 2048
```

> ⚠️ 接手 AI 必看: 智谱 GLM 走 OpenAI 兼容协议,**不是**真的 OpenAI。`spring-ai-starter-model-openai` 这个 starter **接受** 自定义 `base-url`,所以能用。如果发现 4xx 错,看 I-2 § 关键 调 `model: glm-4-flash` 显式指定。

### 0.4 基线 commit

```
e5a0208  fix(frontend,G-5): LlmUsage.vue import http 错
93bec8c  ... (M0~M2 + Plan A~G 共 ~40 个 commit)
c54e320  refactor(plan-G): 简化用量统计,只统计次数
89bec8c  feat(frontend,G-5+G-6): LlmUsage.vue + 路由
88ef6d3  feat(backend,G-2+G-3): GlmService 埋点 + LlmUsage
e63750a  feat(backend,C-3): 新增 10 张业务表
...
```

**M0~M2 + Plan A-G 全部已落 minimax 分支**。

### 0.5 仓库

```
ssh:    git@gitee.com:frisker/projects-online.git
分支:  minimax
本机:  D:\projects-online\
沙箱:  /workspace/projects-online-clone/  (开发用,沙箱 SSH 配 deploy key)
```

### 0.6 关键文件路径

- 后端入口:`backend/src/main/java/com/archive/ArchiveApplication.java`
- Spring Boot 配置:`backend/src/main/resources/application.yml`
- pom:`backend/pom.xml`
- 数据库 schema:`backend/src/main/resources/db/init.sql` + `db/migration/v2-schema.sql` + `db/migration/G-llm-call-log.sql`
- LLM 抽象:`backend/src/main/java/com/archive/provider/LLMProvider.java`
- 现有问答:`backend/src/main/java/com/archive/controller/QaController.java` + `service/KnowledgeSearchService.java` + `service/GlmService.java`
- 前端问答:`frontend/src/views/Knowledge.vue`
- 用量埋点(已就绪):`backend/src/main/java/com/archive/common/LlmScenario.java` + `service/LlmUsageService.java` + `controller/LlmUsageController.java`
- 启动脚本:`backend/startup.ps1`(自动 git pull + build + 启动,生产部署用)
- 启动脚本:`deploy/caddy/Caddyfile`(反代,可不动)
- 配置:`config/config.example.json`(模板,生产用 `D:\archive\config\config.json`)

---

## §1. 子项清单(共 13 个,推 13+ commit)

### 执行顺序(强烈建议按此顺序)

| # | 范围 | 估时 | 依赖 | 互斥 |
|---|---|---|---|---|
| **I-1** | pom.xml 加 Spring AI 依赖(BOM 1.1.0 + 4 个 starter) | 0.2 天 | 无 | 无 |
| **I-2** | application.yml 加 Spring AI + agent 开关 | 0.1 天 | I-1 | 无 |
| **I-3** | agent 包骨架 + 5 DTO + AgentTool 接口 + 3 listener | 0.5 天 | I-2 | 无 |
| **I-4** | 工具 1: SearchFulltextTool(FULLTEXT 检索) | 0.5 天 | I-3 | 无 |
| **I-5** | 工具 2: FindProjectTool(语义定位项目) | 0.5 天 | I-3 | 与 I-4 并行 |
| **I-6** | 工具 3: QueryMysqlTool(白名单 + **聚合** + 操作符 + 注入防护,**重点**) | 1.5 天 | I-3 | 与 I-4/I-5 并行 |
| **I-7** | 工具 4: LlmSummarizeTool + 工具 5: AskClarificationTool | 0.5 天 | I-3 | 无 |
| **I-8** | 工具 6: GetProjectBusinessDataTool(项目汇总) | 0.5 天 | I-3 | 无 |
| **I-9** | AgentEngine 核心(`ChatClient` + `@Tool` + 手写 5 步 ReAct 循环) + Prompt + Few-shots | 1.5 天 | I-4~I-8 | 必须最后 |
| **I-10** | QaController 改造 + 降级路径保留 | 0.5 天 | I-9 | 无 |
| **I-11** | 端到端集成测试(mvn test + 浏览器) | 1 天 | I-10 | 无 |
| **I-12** | 前端 Knowledge.vue 改造 + AgentStepsPanel 组件 | 0.5 天 | I-10 | 与 I-11 并行 |
| **I-13** | **多轮对话** + `MessageChatMemoryAdvisor` + `JdbcChatMemoryRepository` + `chat_memory` 表(**补业务需求 §4.4 漏实现**) | 0.5 天 | I-9 | 与 I-12 并行 |

**总计**: ~8.3 天(可与现有 M0~M2 跑并行,因为零回归)

---

## §2. 每个子项的详细规范

### I-1: pom.xml 加 Spring AI 依赖

**必读**:`backend/pom.xml` 当前依赖(§10.1 已有约 25 个依赖)

**新增依赖**(5 个,都从 Spring AI 1.1 BOM 拉):
```xml
<!-- 在 <dependencyManagement> 加 spring-ai-bom -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- 在 <dependencies> 加 4 个 starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
    <!-- 智谱 GLM 走 OpenAI 兼容协议 -->
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
    <!-- 多轮对话记忆持久化到 MySQL(I-13 用),`repository.` 是 Spring AI 1.1 包名新规范 -->
</dependency>
<!-- 测试用 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-test</artifactId>
    <scope>test</scope>
</dependency>
```

**关键**:
- `spring-ai-bom` 1.1.0(BOM 管所有 Spring AI 子模块版本,**不**手动指定子模块版本)
- **不**引 `spring-ai-autoconfigure-agent`(该 artifact 是 1.2 路线,1.1 公开 API 里没有 `ReactAgent` bean,要用 `ChatClient` + `@Tool` + `Advisor` 自己组合)
- **不**引 spring-ai-alibaba(项目方决策走 OpenAI 兼容,不再多引依赖,理由 `AGENT-FRAMEWORK-DECISION.md` §1.2.1.1)

**验收**:
- `mvn dependency:tree | grep spring-ai` 应看到 5 个 spring-ai-* 工件
- `mvn compile -DskipTests -B -o` BUILD SUCCESS
- 实际 jar 增量 < 15MB

**commit**:`chore(deps): add Spring AI 1.1 BOM + 4 starters`

---

### I-2: application.yml 加 Spring AI + agent 开关

**必读**:`backend/src/main/resources/application.yml`

**新增配置**:
```yaml
spring:
  ai:
    openai:
      api-key: ${GLM_API_KEY: }    # 复用现有 GlmProperties
      base-url: ${GLM_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
      chat:
        options:
          model: glm-4-flash
          temperature: 0.3
          max-tokens: 2048
    agent:
      enabled: ${AGENT_ENABLED:true}   # 开关,关时 QaController 走老路径
      observation-truncate-chars: 2000  # 防 token 超限(Spring AI advisor 截断配置)
# 注意: 5 步上限**硬编码**在 AgentEngine.java 的 for 循环(`MAX_ITERATIONS = 5`),不走 yml
# Spring AI 1.1 没有 `spring.ai.agent.max-iterations` 这种自动配置
```

**关键**:
- `AGENT_ENABLED` 留给 `config.json`(项目方选,默认 true)
- `GLM_API_KEY` 复用现有 `GlmProperties`,**不**新引环境变量名

**验收**:
- 启动日志看到 `OpenAiChatModel configured with model=glm-4-flash`
- `spring.ai.agent.enabled=false` 时 QaController 不初始化 `ChatClient`(整个 AgentEngine 走 @ConditionalOnProperty,不进 bean 池)

**commit**:`chore(config): add Spring AI OpenAI + agent config`

---

### I-3: agent 包骨架

**新增文件**(7 个):
```
backend/src/main/java/com/archive/agent/
├── AgentConfig.java                # @Configuration 配 ChatClient + @Tool + Advisor
├── AgentRequest.java               # 输入 DTO(question + history + sessionId)
├── AgentResponse.java              # 输出 DTO(answer + steps[] + sources[] + agentMode)
├── AgentStep.java                  # 单步(iteration + thought + tool + toolArgs + observation)
├── tool/
│   ├── AgentTool.java              # @Component 接口(Spring AI ToolCallback 适配)
│   └── ToolResult.java             # 工具返回(ok/error/data)
└── listener/
    ├── LlmCallListener.java        # 接到 llm_call_log(scenario=AGENT_STEP)
    └── ToolCallListener.java       # 接到 llm_call_log + audit_log
```

**AgentTool 接口**:
```java
public interface AgentTool {
    String name();                            // "search_fulltext" 等
    String description();                     // 给 LLM 看的描述
    Class<?> argsClass();                     // 参数类(用 Jackson 转)
    ToolResult execute(Object args, AgentContext ctx);
}
```

**AgentConfig.java 关键代码**:
```java
@Configuration
@ConditionalOnProperty(name = "spring.ai.agent.enabled", havingValue = "true", matchIfMissing = true)
public class AgentConfig {

    @Bean
    public ChatClient chatClient(OpenAiChatModel model,
                                 List<AgentTool> tools,
                                 AgentSystemPrompt systemPrompt) {
        return ChatClient.builder(model)
            .defaultSystem(systemPrompt.render())
            .defaultTools(tools.toArray(new AgentTool[0]))
            .build();
    }

    // Spring AI 1.1: 没用 ReactAgent builder(那是 1.2 路线)。1.1 用 ChatClient.prompt().user().tools().call()
    // 参考 spring-ai-docs/react-agent
}
```

**LlmCallListener 关键**:
```java
@Component
@RequiredArgsConstructor
public class LlmCallListener implements ChatModelListener {
    private final LlmCallLogRepository repo;
    @Override
    public void onResponse(ChatModelResponse resp) {
        // 写 llm_call_log(scenario=AGENT_STEP 或 AGENT_FINAL)
    }
}
```

**验收**:
- `mvn compile` 通过
- `AgentConfig` 注入 `ChatClient` bean(可在测试类里 `@Autowired ChatClient` 验证)
- `LlmCallListener` 注入 `LlmCallLogRepository` 验证

**commit**:`feat(agent): add agent package skeleton with 5 DTO + Listener`

---

### I-4: 工具 1 — SearchFulltextTool

**必读**:`service/KnowledgeSearchService.java`(已就绪,直接用)

**新增**:`backend/src/main/java/com/archive/agent/tool/SearchFulltextTool.java`

**关键代码**:
```java
@Component
@RequiredArgsConstructor
public class SearchFulltextTool implements AgentTool {
    private final KnowledgeSearchService searchService;
    public String name() { return "search_fulltext"; }
    public String description() {
        return "在 material_version.parsed_text(MySQL FULLTEXT ngram)中检索相关材料片段,返回 top N 条带 project/proposal/material 元数据的命中片段。";
    }
    public Class<?> argsClass() { return SearchArgs.class; }
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        SearchArgs args = mapper.convertValue(argsObj, SearchArgs.class);
        List<SearchResult> results = searchService.search(args.query, args.topN);
        // 如果有 projectCode filter(Agent 上一步 find_project 锁定),加过滤
        if (ctx.getProjectCode() != null) {
            results = results.stream()
                .filter(r -> ctx.getProjectCode().equals(r.getProjectCode()))
                .toList();
        }
        // 埋点 llm_call_log
        saveLog(ctx, "AGENT_TOOL", "search_fulltext", results.size());
        return ToolResult.ok(results);
    }
}
```

**SearchArgs DTO**:
```java
@Data
public static class SearchArgs {
    @JsonProperty("query") String query;
    @JsonProperty("topN") @Builder.Default Integer topN = 10;
    @JsonProperty("projectCode") String projectCode;  // 可选
}
```

**AgentContext 字段**(在 I-3 已定义):
```java
public class AgentContext {
    private String question;
    private List<AgentStep> steps = new ArrayList<>();
    private String projectCode;   // find_project 锁定后填
    private Map<String, Object> state = new HashMap<>();
}
```

**验收**:
- `mvn compile` 通过
- 单元测试 `SearchFulltextToolTest`:mock `KnowledgeSearchService` 验证 args → results → ToolResult.ok
- 测 filter:AgentContext.projectCode 非空时只返该项目

**commit**:`feat(agent): add SearchFulltextTool (tool 1)`

---

### I-5: 工具 2 — FindProjectTool(项目方 v1.0 关键)

**必读**:`repository/ProjectRepository.java`(已有 findByCode 方法,需加 searchByNameOrCustomerFulltext)

**新增**:
- `backend/src/main/java/com/archive/agent/tool/FindProjectTool.java`
- `ProjectRepository` 加 1 个方法:

```java
@Query(value = "SELECT *, " +
       "MATCH(name, customer_name) AGAINST(:q IN BOOLEAN MODE) AS score " +
       "FROM project " +
       "WHERE MATCH(name, customer_name) AGAINST(:q IN BOOLEAN MODE) " +
       "ORDER BY score DESC LIMIT :topN",
       nativeQuery = true)
List<Project> searchByNameOrCustomerFulltext(@Param("q") String q, @Param("topN") int topN);
```

**注意**:
- `project` 表**可能**没有 FULLTEXT 索引(name / customer_name 上)——若没有,先跑 SQL:
  ```sql
  ALTER TABLE project
    ADD FULLTEXT INDEX ft_name_cust (name, customer_name) WITH PARSER ngram;
  ```
  把这个 ALTER 写到 `db/migration/I-find-project-fulltext.sql`,**新库 + 老库**都跑。
- Project 实体 `score` 字段是临时计算列,不能 `@Column`,用 `@SqlResultSetMapping` 或简单 `Object[]` 接。

**FindProjectTool 关键代码**:
```java
@Component
@RequiredArgsConstructor
public class FindProjectTool implements AgentTool {
    private final ProjectRepository projectRepo;
    public String name() { return "find_project"; }
    public String description() {
        return "用语义从 project.name + customer_name 中找匹配的项目,返回 Top N 候选(带置信度)。用户的'那个项目'通常指 find_project。";
    }
    public Class<?> argsClass() { return FindProjectArgs.class; }
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        FindProjectArgs args = mapper.convertValue(argsObj, FindProjectArgs.class);
        // 1. 精确匹配 projectCode
        Optional<Project> exact = projectRepo.findByCode(args.query.trim());
        if (exact.isPresent()) {
            ctx.setProjectCode(exact.get().getCode());  // 锁定!
            return ToolResult.ok(List.of(Map.of(
                "projectCode", exact.get().getCode(),
                "projectName", exact.get().getName(),
                "confidence", 1.0,
                "matchType", "exact_code"
            )));
        }
        // 2. FULLTEXT 模糊匹配
        List<Object[]> rows = projectRepo.searchByNameOrCustomerFulltext(args.query, args.topN);
        List<Map<String, Object>> candidates = new ArrayList<>();
        double maxScore = rows.isEmpty() ? 1.0 : (double) rows.get(0)[1];
        for (Object[] row : rows) {
            Project p = (Project) row[0];
            double score = (double) row[1];
            candidates.add(Map.of(
                "projectCode", p.getCode(),
                "projectName", p.getName(),
                "customerName", p.getCustomerName(),
                "confidence", score / maxScore,
                "matchType", "fulltext"
            ));
        }
        // 锁定置信度最高的项目(>= 0.7)
        if (!candidates.isEmpty() && (double) candidates.get(0).get("confidence") >= 0.7) {
            ctx.setProjectCode((String) candidates.get(0).get("projectCode"));
        }
        // 埋点
        saveLog(ctx, "AGENT_TOOL", "find_project", candidates.size());
        return ToolResult.ok(candidates);
    }
}
```

**验收**:
- `mvn compile` 通过
- `FindProjectToolTest`:
  - "PRJ-2026-001" → 精确匹配 1 条,confidence 1.0
  - "新能源" → FULLTEXT 匹配 N 条,confidence 归一化
  - "不存在的" → 空列表
- 浏览器端到端:用户问"新能源那个项目" → agent 调 find_project → 锁定项目 → 后续 search_fulltext 只查该项目

**commit**:
- `feat(agent): add FindProjectTool + project fulltext index (tool 2)`(含 SQL)

---

### I-6: 工具 3 — QueryMysqlTool(白名单 + 安全,**重点子项**)

**必读**:`AGENT-FRAMEWORK-DECISION.md` §6(数据库安全),§3.2 工具 2 spec

**新增**:`backend/src/main/java/com/archive/agent/tool/QueryMysqlTool.java`

**关键代码**:
```java
@Component
@RequiredArgsConstructor
public class QueryMysqlTool implements AgentTool {
    private final EntityManager em;
    private final AuditLogService auditLogService;
    private final AuthenticationFacade authFacade;  // 取当前 username

    private static final Set<String> ALLOWED_ENTITIES = Set.of(
        "project", "proposal", "material", "material_version", "todo", "timepoint"
    );

    private static final Map<String, Set<String>> ALLOWED_FIELDS = Map.of(
        "project", Set.of("id", "code", "name", "category", "ownerId", "stage", "status", "createdAt", "amountWan"),
        "proposal", Set.of("id", "code", "title", "projectId", "status", "createdAt"),
        "material", Set.of("id", "title", "proposalId", "type", "status", "createdAt"),
        "material_version", Set.of("id", "materialId", "versionNo", "parseStatus", "createdAt"),
        "todo", Set.of("id", "title", "priority", "status", "dueAt", "createdAt"),
        "timepoint", Set.of("id", "materialId", "eventAt", "title", "createdAt")
    );

    public String name() { return "query_mysql"; }
    public String description() {
        return "查询项目/议案/材料/待办/时点的业务字段,返回结构化数据。仅支持白名单操作,不可写。";
    }
    public Class<?> argsClass() { return QueryArgs.class; }
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        QueryArgs args = mapper.convertValue(argsObj, QueryArgs.class);

        // 1. 白名单校验
        if (!ALLOWED_ENTITIES.contains(args.entity)) {
            throw new ToolNotAllowedException("entity not allowed: " + args.entity);
        }
        Set<String> allowed = ALLOWED_FIELDS.get(args.entity);
        for (String f : args.filters.keySet()) {
            if (!allowed.contains(f)) throw new ToolNotAllowedException("filter not allowed: " + f);
        }
        for (String f : args.fields) {
            if (!allowed.contains(f)) throw new ToolNotAllowedException("field not allowed: " + f);
        }
        if (args.limit > 100) args.limit = 100;

        // 2. 硬编码 JPA 查询(不用反射)
        StringBuilder jpql = new StringBuilder("SELECT ");
        jpql.append(String.join(", ", args.fields));
        jpql.append(" FROM ").append(capitalize(args.entity));
        jpql.append(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        int i = 1;
        for (var e : args.filters.entrySet()) {
            jpql.append(" AND ").append(e.getKey()).append(" = :p").append(i);
            params.put("p" + i, e.getValue());
            i++;
        }
        jpql.append(" ORDER BY id DESC");

        // 3. 强制参数化
        var query = em.createQuery(jpql.toString());
        params.forEach(query::setParameter);
        query.setMaxResults(args.limit);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        // 4. 审计
        auditLogService.logSimple(
            authFacade.currentUsername(), "AGENT_QUERY",
            args.entity, null, "rows=" + rows.size()
        );
        saveLog(ctx, "AGENT_TOOL", "query_mysql", rows.size());

        // 5. 组装返回
        List<Map<String, Object>> result = rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int j = 0; j < args.fields.size(); j++) {
                m.put(args.fields.get(j), row[j]);
            }
            return m;
        }).toList();
        return ToolResult.ok(Map.of("rows", result, "rowCount", result.size(), "entity", args.entity));
    }

    private String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
```

**QueryArgs DTO**:
```java
@Data
public static class QueryArgs {
    @JsonProperty("entity") String entity;
    @JsonProperty("filters") @Builder.Default List<FilterCondition> filters = new ArrayList<>();
    @JsonProperty("fields") List<String> fields;
    @JsonProperty("limit") @Builder.Default Integer limit = 20;
    @JsonProperty("aggregate") String aggregate;  // "count" / "sum" / "avg" / "max" / "min" / "group_by" / null(原样)
    @JsonProperty("aggregateField") String aggregateField;  // "amountWan" 等
    @JsonProperty("groupByField") String groupByField;  // aggregate=group_by 时必填
}

@Data
public static class FilterCondition {
    @JsonProperty("field") String field;
    @JsonProperty("operator") String operator;  // "=" / "!=" / ">" / ">=" / "<" / "<=" / "in" / "like" / "is_null" / "is_not_null"
    @JsonProperty("value") Object value;  // 标量或 List<Object>
}
```

**关键安全**:
- `entity` 严格白名单(6 个值)
- `filters` / `fields` 严格白名单
- **不**用 `createNativeQuery`,**不**字符串拼 SQL
- 实体名 `project` → `Project`(硬编码 `capitalize` 映射)
- `audit_log` + `llm_call_log` 双写
- `aggregate` 白名单: `count` / `sum` / `avg` / `max` / `min` / `group_by` / null
- `operator` 白名单: `=` / `!=` / `>` / `>=` / `<` / `<=` / `in` / `like` / `is_null` / `is_not_null`
- **in 操作的 value 必须是 List,长度 ≤ 50**(防 DoS)
- **不允许 3 张表 JOIN**(决策 §6.2)
- **DDL/DML 严禁**:只读 `SELECT` + 严格 `EntityManager.createQuery()`(不走 createNativeQuery)

**3 大典型场景**(你举的"还没结清"对得上):

**场景 1:计数 + 列表**(你的原问题)
```json
// 问: "现在总共还没结清的项目?涉及多少金额?"
// LLM 拆 2 次:
{
  "entity": "project",
  "filters": [{"field": "status", "operator": "!=", "value": "结清"}],
  "fields": ["id"],
  "aggregate": "count"
}
// 返: {"value": 23, "rowCount": 0, "entity": "project", "aggregate": "count"}

{
  "entity": "project",
  "filters": [{"field": "status", "operator": "!=", "value": "结清"}],
  "fields": ["amountWan"],
  "aggregate": "sum",
  "aggregateField": "amountWan"
}
// 返: {"value": 128000.5, "rowCount": 0, "entity": "project", "aggregate": "sum", "aggregateField": "amountWan"}
```

**场景 2:时间范围 + 聚合**(§2.2 第 7 条"今年结清")
```json
{
  "entity": "project",
  "filters": [
    {"field": "status", "operator": "=", "value": "结清"},
    {"field": "createdAt", "operator": ">=", "value": "2026-01-01"}
  ],
  "aggregate": "count"
}
```

**场景 3:group_by 分类统计**(§2.2 第 9 条"哪个分类最多")
```json
{
  "entity": "material",
  "filters": [{"field": "createdAt", "operator": ">=", "value": "2026-06-01"}],
  "fields": ["type"],
  "aggregate": "group_by",
  "groupByField": "type"
}
// 返: {"rows": [{"type": "立项报告", "count": 23}, {"type": "申请报告", "count": 18}, ...], "rowCount": 5}
```

**实现片段**(execute 关键逻辑):
```java
public ToolResult execute(Object argsObj, AgentContext ctx) {
    QueryArgs args = mapper.convertValue(argsObj, QueryArgs.class);

    // 1. 白名单校验
    validateWhitelist(args);  // entity + fields + aggregate + operator 都在白名单

    // 2. 构造 JPQL
    StringBuilder jpql = new StringBuilder();

    // SELECT 段
    if ("count".equals(args.aggregate)) {
        jpql.append("SELECT COUNT(t) FROM ").append(capitalize(args.entity)).append(" t");
    } else if ("sum".equals(args.aggregate) || "avg".equals(args.aggregate)
            || "max".equals(args.aggregate) || "min".equals(args.aggregate)) {
        jpql.append("SELECT ").append(args.aggregate.toUpperCase()).append("(t.")
          .append(args.aggregateField).append(") FROM ").append(capitalize(args.entity)).append(" t");
    } else if ("group_by".equals(args.aggregate)) {
        jpql.append("SELECT t.").append(args.groupByField)
          .append(", COUNT(t) FROM ").append(capitalize(args.entity)).append(" t");
    } else {
        jpql.append("SELECT ").append(toJpqlFields(args.fields))
          .append(" FROM ").append(capitalize(args.entity)).append(" t");
    }

    // WHERE 段
    if (!args.filters.isEmpty()) {
        jpql.append(" WHERE 1=1");
        int i = 1;
        for (FilterCondition f : args.filters) {
            String field = "t." + f.field;
            switch (f.operator) {
                case "=": jpql.append(" AND ").append(field).append(" = :p").append(i); break;
                case "!=": jpql.append(" AND ").append(field).append(" <> :p").append(i); break;
                case ">": case ">=": case "<": case "<=":
                    jpql.append(" AND ").append(field).append(" ").append(f.operator).append(" :p").append(i); break;
                case "in":
                    if (!(f.value instanceof List)) throw new ToolNotAllowedException("'in' requires List value");
                    if (((List) f.value).size() > 50) throw new ToolNotAllowedException("'in' list too long");
                    jpql.append(" AND ").append(field).append(" IN :p").append(i); break;
                case "like":
                    jpql.append(" AND ").append(field).append(" LIKE :p").append(i); break;
                case "is_null": jpql.append(" AND ").append(field).append(" IS NULL"); i--; break;  // 不占位
                case "is_not_null": jpql.append(" AND ").append(field).append(" IS NOT NULL"); i--; break;
                default: throw new ToolNotAllowedException("operator not allowed: " + f.operator);
            }
            i++;
        }
    }

    // GROUP BY 段
    if ("group_by".equals(args.aggregate)) {
        jpql.append(" GROUP BY t.").append(args.groupByField);
    }

    // ORDER BY 段(只非聚合 + 非 group_by 才生效)
    if (args.aggregate == null) {
        jpql.append(" ORDER BY t.id DESC");
    }

    // 3. 强制参数化
    var query = em.createQuery(jpql.toString());
    int i = 1;
    for (FilterCondition f : args.filters) {
        if (f.operator.equals("is_null") || f.operator.equals("is_not_null")) continue;
        query.setParameter("p" + i, f.value);
        i++;
    }
    if (args.aggregate == null && args.limit != null) {
        query.setMaxResults(Math.min(args.limit, 100));
    }

    // 4. 执行 + 审计
    Object result;
    if (args.aggregate == null) {
        List<Object[]> rows = query.getResultList();
        result = rows.stream().map(row -> /* 同原代码 */).toList();
    } else if ("group_by".equals(args.aggregate)) {
        List<Object[]> rows = query.getResultList();
        result = rows.stream().map(row -> Map.of("value", row[0], "count", row[1])).toList();
    } else {
        // count / sum / avg / max / min: 单值
        result = Map.of("value", query.getSingleResult());
    }

    // 5. 审计(同原代码)
    auditLogService.logSimple(
        authFacade.currentUsername(), "AGENT_QUERY",
        args.entity, null, "aggregate=" + args.aggregate + " value=" + result
    );
    saveLog(ctx, "AGENT_TOOL", "query_mysql", 1);

    return ToolResult.ok(Map.of(
        "entity", args.entity,
        args.aggregate == null ? "rows" : "value", result,
        args.aggregate == null ? "rowCount" : "aggregate", result instanceof List ? ((List) result).size() : args.aggregate,
        "aggregate", args.aggregate == null ? "none" : args.aggregate
    ));
}

private void validateWhitelist(QueryArgs args) {
    if (!ALLOWED_ENTITIES.contains(args.entity))
        throw new ToolNotAllowedException("entity not allowed: " + args.entity);
    Set<String> allowedFields = ALLOWED_FIELDS.get(args.entity);
    if (args.fields != null) {
        for (String f : args.fields) {
            if (!allowedFields.contains(f))
                throw new ToolNotAllowedException("field not allowed: " + f);
        }
    }
    for (FilterCondition f : args.filters) {
        if (!allowedFields.contains(f.field))
            throw new ToolNotAllowedException("filter field not allowed: " + f.field);
    }
    if (args.aggregate != null) {
        if (!Set.of("count", "sum", "avg", "max", "min", "group_by").contains(args.aggregate))
            throw new ToolNotAllowedException("aggregate not allowed: " + args.aggregate);
        if (args.aggregateField != null && !allowedFields.contains(args.aggregateField))
            throw new ToolNotAllowedException("aggregateField not allowed: " + args.aggregateField);
        if (args.groupByField != null && !allowedFields.contains(args.groupByField))
            throw new ToolNotAllowedException("groupByField not allowed: " + args.groupByField);
    }
}
```

**关键安全加固**(项目方 v1.0 触发,你这个问题暴露的):
1. `operator` 白名单(10 个值,防止 `OR 1=1` / `; DROP TABLE` 等)
2. `aggregate` 白名单
3. `IN` value List 长度 ≤ 50(防 DoS)
4. `IS NULL` / `IS NOT NULL` 不占位
5. LIKE 不能含 `_` / `%` 通配符(防贪婪匹配全表)—— LLM 调用时**转义**: `value.replace("%", "\\%").replace("_", "\\_")`
6. 3 张表 JOIN **直接禁**(决策 §6.2)
7. DDL/DML **createQuery 只支持 SELECT**(底层拒绝)

**LLM 提示词更新**(AgentSystemPrompt 第 3 段):
```
query_mysql(entity, filters, fields, aggregate?, aggregateField?, groupByField?):
  - 查业务数据,**支持**: = / != / > / >= / < / <= / in(<= 50 个) / like / is_null / is_not_null
  - **支持聚合**: count / sum / avg / max / min / group_by(field)
  - **必须用 SQL 算聚合**,**不要**自己 rows[].size() / sum()(精度 / 截断问题)
  - **返回示例**:
    - count 返 {value: 23, aggregate: "count"}
    - sum(amountWan) 返 {value: 128000.5, aggregate: "sum"}
    - group_by(type) 返 {value: [{value: "立项报告", count: 23}, ...], aggregate: "group_by"}
```

**测试用例**(`QueryMysqlToolTest`):
1. **你的问题**:"还没结清的项目" → `[{field: "status", operator: "!=", value: "结清"}]` + `aggregate: "count"` → 23
2. **场景 2**:"今年结清的" → `[=, >=]` + `count` → 5
3. **场景 3**:"金额求和" → `sum(amountWan)` → 128000.5
4. **场景 4**:"分类 group_by" → `group_by(type)` + `count` → [{value: "立项", count: 23}, ...]
5. **越权 entity**:`entity="user"` → 抛 `ToolNotAllowedException`
6. **越权 operator**:`operator="OR 1=1"` → 抛异常
7. **in 长度 51** → 抛异常
8. **like 贪婪**:LLM 传 `value="%admin%"` → 自动转义 `value="\%admin\%"`

**commit**:
- `feat(agent,I-6): add aggregate + operator support to QueryMysqlTool` (改 I-6)
- `test(agent,I-6): 8 测试用例含聚合 + 越权 + 注入 + 边界`

**数据库账号**(可选,本期可省):
- DBA 加 `archive_agent_app` 用户,只 `GRANT SELECT ON archive_db.*`
- `application.yml` 加独立 datasource `spring.datasource.agent.*`
- **本期**:用主应用账号(已有 SELECT 权限),**降级方案**

**验收**:
- `mvn compile` 通过
- `QueryMysqlToolTest`:
  - 正常查询(project + name)返 rows
  - 越权 entity(`"user"`)抛 `ToolNotAllowedException`
  - 越权 field(`"passwordHash"`)抛异常
  - SQL 注入测试:`filters={"id":"1' OR '1'='1"}` 返 0 行(参数化生效)
- **必跑**:`mvn test`

**commit**:`feat(agent): add QueryMysqlTool with whitelist + audit (tool 3, security-critical)`

---

### I-7: 工具 4 + 5 — LlmSummarizeTool + AskClarificationTool

**新增**:
- `backend/src/main/java/com/archive/agent/tool/LlmSummarizeTool.java`
- `backend/src/main/java/com/archive/agent/tool/AskClarificationTool.java`

**LlmSummarizeTool 关键代码**:
```java
@Component
@RequiredArgsConstructor
public class LlmSummarizeTool implements AgentTool {
    private final LLMProviderFactory providerFactory;
    public String name() { return "llm_summarize"; }
    public String description() {
        return "把上游传入的文本材料,让 LLM 做摘要/抽取/推理,返回精炼结果。";
    }
    public Class<?> argsClass() { return SummarizeArgs.class; }
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        SummarizeArgs args = mapper.convertValue(argsObj, SummarizeArgs.class);
        LLMProvider provider = providerFactory.getProvider();
        String system = "你是档案分析助手。按用户要求处理文本,简洁准确。";
        String user = "任务: " + args.task + "\n关注点: " + args.focus + "\n文本: " + args.text;
        String result = provider.chat(system, user);
        saveLog(ctx, "AGENT_TOOL", "llm_summarize", result.length());
        return ToolResult.ok(Map.of("result", result, "task", args.task));
    }
}
```

**AskClarificationTool 关键代码**:
```java
@Component
public class AskClarificationTool implements AgentTool {
    public String name() { return "ask_clarification"; }
    public String description() {
        return "当问题歧义、信息不足时,生成追问话术返回给前端,中断 agent 循环。";
    }
    public Class<?> argsClass() { return ClarificationArgs.class; }
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        ClarificationArgs args = mapper.convertValue(argsObj, ClarificationArgs.class);
        // 标 ctx.interrupt=true,AgentEngine 主循环会检测并立即返回
        ctx.setInterrupt(true);
        return ToolResult.ok(Map.of(
            "interrupt", true,
            "question", args.question,
            "options", args.options != null ? args.options : List.of()
        ));
    }
}
```

**验收**:
- `mvn compile` 通过
- 单元测试覆盖 2 个工具

**commit**:`feat(agent): add LlmSummarizeTool + AskClarificationTool (tools 4,5)`

---

### I-8: 工具 6 — GetProjectBusinessDataTool

**新增**:`backend/src/main/java/com/archive/agent/tool/GetProjectBusinessDataTool.java`

**关键代码**:
```java
@Component
@RequiredArgsConstructor
public class GetProjectBusinessDataTool implements AgentTool {
    private final ProjectRepository projectRepo;
    private final TodoRepository todoRepo;
    private final LlmCallLogRepository llmLogRepo;

    public String name() { return "get_project_business_data"; }
    public String description() {
        return "查某个项目的业务汇总(剩余金额、当前 stage、最近待办数等),是 query_mysql 的快捷封装。";
    }
    public Class<?> argsClass() { return ProjectArgs.class; }
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        ProjectArgs args = mapper.convertValue(argsObj, ProjectArgs.class);
        Project p = projectRepo.findByCode(args.projectCode)
            .orElseThrow(() -> new NoSuchElementException("项目不存在: " + args.projectCode));
        // remainingAmount 由 Plan C-5 的累计金额公式自动算(已就绪)
        long openTodo = todoRepo.countByProjectIdAndStatus(p.getId(), "pending");
        Map<String, Object> result = Map.of(
            "projectCode", p.getCode(),
            "name", p.getName(),
            "stage", p.getStage(),
            "amountWan", p.getAmountWan(),
            "remainingAmountWan", p.getRemainingAmountWan(),  // 字段可能为 null
            "openTodoCount", openTodo,
            "customerName", p.getCustomerName()
        );
        saveLog(ctx, "AGENT_TOOL", "get_project_business_data", 1);
        return ToolResult.ok(result);
    }
}
```

**注意**:
- `remainingAmountWan` 字段 Project 实体**可能没映射**——若没有,工具返回 `null` + description 加 "字段暂未在系统中维护"
- `countByProjectIdAndStatus` 可能在 `TodoRepository` **没就绪**——加 1 个方法:
  ```java
  long countByProjectIdAndStatus(Long projectId, String status);
  ```

**验收**:
- `mvn compile` 通过
- 端到端:用户问"PRJ-2026-001 剩余金额" → agent 调 `find_project` → 调 `get_project_business_data` → 返回 amount + remaining + openTodoCount

**commit**:`feat(agent): add GetProjectBusinessDataTool (tool 6)`

---

### I-9: AgentEngine 核心 + Prompt + Few-shots(最关键的子项)

**新增**:
- `backend/src/main/java/com/archive/agent/AgentEngine.java`
- `backend/src/main/java/com/archive/agent/prompt/AgentSystemPrompt.java`
- `backend/src/main/java/com/archive/agent/prompt/AgentFewShots.java`

**重要事实(踩坑)**:Spring AI 1.1 **没有** `ReactAgent` 公开 class。要用 `ChatClient` + `@Tool` + `MethodToolCallbackProvider` + `MessageChatMemoryAdvisor` + 自己手写 5 步 ReAct 循环。

**AgentSystemPrompt 关键**:
```java
@Component
public class AgentSystemPrompt {
    public String render() {
        return """
        你是投委会档案管理系统的 AI 助手,使用中文回答。

        你有以下 6 个工具可用(必须输出 JSON 格式调用):
        1. find_project(query, topN) — 用语义定位项目
        2. search_fulltext(query, topN, projectCode) — MySQL FULLTEXT 检索材料
        3. query_mysql(entity, filters, fields, limit) — 查业务数据(白名单 6 个实体)
        4. get_project_business_data(projectCode) — 项目业务汇总
        5. llm_summarize(task, text, focus) — 让 LLM 摘要/抽取
        6. ask_clarification(question, options) — 追问用户(中断循环)

        工具调用格式(JSON,严格):
        {
          "thought": "我先要锁定项目",
          "tool": "find_project",
          "args": {"query": "新能源那个", "topN": 3}
        }

        终止(给最终答案):
        {
          "thought": "我已经找到信息",
          "tool": "FINAL_ANSWER",
          "args": {"answer": "PRJ-2026-001 剩余金额 3200 万元。来源 [1]", "sources": [...]}
        }

        规则:
        - 优先 find_project 锁定项目,再 search_fulltext + query_mysql
        - search_fulltext 加 projectCode filter 限定作用域
        - 引用材料用 [1] [2] 编号
        - 不知道就说不知道,不要编造
        - 连续 2 次同工具同参数,改用其他工具或直接 FINAL_ANSWER
        - 最多 5 步循环

        Few-shot 示例:
        """ + AgentFewShots.examples();
    }
}
```

**AgentFewShots**(3 个示例,1-2 段每个):
```
Q: "新能源那个项目今年盈利怎么样?"
→ thought: 锁定项目
→ tool: find_project, args: {query: "新能源那个"}
→ obs: [PRJ-2026-001 confidence 0.92, ...]
→ thought: 锁定成功,查业务数据
→ tool: get_project_business_data, args: {projectCode: "PRJ-2026-001"}
→ obs: {stage: "贷后", amountWan: 5000, remainingAmountWan: 3200}
→ thought: 查材料
→ tool: search_fulltext, args: {query: "盈利", projectCode: "PRJ-2026-001"}
→ obs: [5 段匹配]
→ thought: 够了
→ tool: FINAL_ANSWER, args: {answer: "PRJ-2026-001 当前剩余 3200 万元,据立项报告...", sources: [...]}
```

**AgentEngine**(ReAct 循环,~150 行):

**关键**:Spring AI 1.1 **没有 `ReactAgent` class**。用 `ChatClient` + `@Tool` 注解 + `MessageChatMemoryAdvisor` + **自己手写 5 步 ReAct 循环**。

```java
@Component
@ConditionalOnProperty(name = "spring.ai.agent.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AgentEngine {
    private static final int MAX_ITERATIONS = 5;  // 5 步上限,硬编码

    private final ChatClient chatClient;          // 已注入 6 个 @Tool
    private final List<AgentTool> tools;          // 6 工具
    private final KnowledgeSearchService searchService;  // 降级用
    private final LlmCallLogRepository llmLogRepo;

    public AgentResponse run(AgentRequest req) {
        AgentContext ctx = new AgentContext();
        ctx.setUsername(SecurityContextHolder.getContext().getAuthentication().getName());
        long start = System.currentTimeMillis();
        try {
            // 手写 ReAct 循环
            List<AgentStep> steps = new ArrayList<>();
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                ctx.setIteration(i);
                // 1. Thought + Action: 让 LLM 选工具
                ChatResponse response = chatClient.prompt()
                    .system(AgentSystemPrompt.render(ctx))    // 包含工具描述 + 上文
                    .user(req.getQuestion())
                    .tools(tools.toArray(new AgentTool[0]))   // 6 个 @Tool method
                    .call();

                // 2. 解析 LLM 输出:FINAL_ANSWER 退出 / 否则调工具
                String content = response.getResult().getOutput().getContent();
                AgentStep step = parseAgentStep(content);
                steps.add(step);

                if ("FINAL_ANSWER".equals(step.getTool())) {
                    return buildResponse(step, steps, start, true);
                }
                if (ctx.isInterrupt()) {  // ask_clarification 触发
                    return buildResponse(step, steps, start, true);
                }
                // 3. Observation: 调工具, 把结果累加到 ctx
                ToolResult obs = dispatchTool(step.getTool(), step.getArgs(), ctx);
                ctx.addObservation(obs);
            }
            // 4. 超 5 步: 强制 FINAL_ANSWER(拿前 N 步 observation 拼 prompt)
            return forceFinalAnswer(ctx, steps, start);
        } catch (Exception e) {
            log.warn("Agent run failed, fallback to search-only", e);
            saveLog(ctx, "AGENT_FALLBACK", e.getMessage());
            return fallbackSearch(req, start);
        }
    }
}
```

**6 个工具都是 `@Component` 实现的 `@Tool` 注解 method**:
```java
@Component
public class FindProjectTool {
    private final ProjectRepository projectRepo;

    @Tool(description = "用语义从 project.name + customer_name 中找匹配的项目")
    public List<Map<String, Object>> findProject(
        @ToolParam(description = "用户口语化描述的项目标识") String query,
        @ToolParam(description = "返回 top N,默认 3") Integer topN) {
        // 业务代码
    }
}
```

**ChatClient 配置**(在 `AgentConfig.java`):
```java
@Bean
public ChatClient chatClient(ChatModel chatModel, List<AgentTool> tools) {
    return ChatClient.builder(chatModel)
        .defaultSystem(AgentSystemPrompt.render(null))
        .defaultTools(tools.toArray())  // 6 个 @Tool 自动暴露
        .build();
}
```

**为什么不用 `ReactAgent`**:Spring AI 1.1 GA(2025-11) 实际没有 `ReactAgent` 公开 class(`spring-ai-autoconfigure-agent` 是 1.2 路线),只有 `ChatClient` + `@Tool` + `Advisor`。`ReactAgent` 是 阿里云 Spring AI Alibaba 概念 或 Spring AI 1.2 计划。

**验收**:
- `mvn compile` 通过
- `AgentEngineTest`(用 mock ChatClient):返 answer + steps
- 降级测试:mock ChatClient 抛异常 → fallback 走老路径

**commit**:`feat(agent): add AgentEngine with ChatClient + @Tool + 5-step ReAct loop + fallback`

---

### I-10: QaController 改造 + 降级路径保留

**必读**:`controller/QaController.java` 当前实现(写死 search → rerank → generate)

**改造方案**:
- 保留 `/api/qa/ask` 端点路径
- 注入 `AgentEngine`(用 `@Autowired(required = false)`,关开关时不报)
- `ask()` 方法先看 `application.yml` 的 `spring.ai.agent.enabled`:
  - true → `agentEngine.run(req)`,返 `AgentResponse`(含 steps)
  - false → 老 `glmService` 路径(原 M2)
- **响应 DTO 兼容**:`QaResponse` 加 `steps`、`agentMode` 可空字段
- `QaResponse` 现有 `answer / sources / reranked` 字段保留(老前端不会崩)

**关键代码**:
```java
@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final KnowledgeSearchService searchService;
    private final GlmService glmService;  // 降级路径
    @Autowired(required = false)
    private AgentEngine agentEngine;       // 可空(关开关时)

    @Value("${spring.ai.agent.enabled:true}")
    private boolean agentEnabled;

    @PostMapping("/ask")
    public ApiResponse<QaResponse> ask(@Valid @RequestBody QaRequest req) {
        long start = System.currentTimeMillis();
        // 路径 1:Agent 模式
        if (agentEnabled && agentEngine != null) {
            try {
                AgentResponse ar = agentEngine.run(AgentRequest.fromQaRequest(req));
                QaResponse qr = QaResponse.fromAgentResponse(ar);
                qr.setElapsedMs(System.currentTimeMillis() - start);
                return ApiResponse.ok(qr);
            } catch (Exception e) {
                log.warn("Agent 失败,降级", e);
                // 不返 500,降级走老路径
            }
        }
        // 路径 2:老 FULLTEXT 路径(原 M2)
        List<SearchResult> results = searchService.search(req.getQuestion(), 10);
        String answer = null;
        if (glmService != null && req.isRerank()) {
            String order = glmService.rerank(req.getQuestion(), results);
            // ... 原 M2 逻辑
        }
        QaResponse qr = new QaResponse();
        qr.setQuestion(req.getQuestion());
        qr.setAnswer(answer);
        qr.setSources(...);
        qr.setAgentMode(false);
        qr.setElapsedMs(System.currentTimeMillis() - start);
        return ApiResponse.ok(qr);
    }
}
```

**`QaResponse` 加字段**(兼容老前端):
```java
@Data
public class QaResponse {
    private String question;
    private String answer;
    private List<Source> sources;
    private boolean reranked;
    private long elapsedMs;
    // 新加:
    private Boolean agentMode;     // null = 老路径,false = 降级,true = Agent
    private List<AgentStep> steps;  // null = 老路径
    private Integer toolCalls;     // null = 老路径
}
```

**验收**:
- `mvn compile` 通过
- `QaControllerTest`:
  - Agent 模式返 `agentMode=true` + `steps[]`
  - 关开关返 `agentMode=false` + `steps=null`
  - Agent 抛异常降级
- 浏览器端到端:问个问题 → 看 Network `/api/qa/ask` 响应里有 `steps` 字段

**commit**:`refactor(qa): QaController routes to AgentEngine with fallback to GlmService`

---

### I-11: 端到端集成测试(1 天)

**新增**:`backend/src/test/java/com/archive/agent/AgentIntegrationTest.java`

**测试用例**(10 个,基于 `AGENT-REQUIREMENTS.md` §6 验收场景):

1. **检索类**:问"新能源项目风险点" → 返答案 + 来源 [1][2]
2. **查库类**:问"PRJ-2026-001 剩余金额" → 调 get_project_business_data → 数字准确
3. **推理类**:问"今年否决了哪些项目" → 调 query_mysql + search_fulltext 综合
4. **追问类**:问"那个项目" → 调 ask_clarification → 前端弹 dialog
5. **锁项目**:问"PRJ-001" → find_project 1 个候选 → 锁定 → 后续 search 加 projectCode filter
6. **跨表查**:问"今天有哪些待办" → query_mysql(todo, status='pending')
7. **降级**:模拟 LLM 抛异常 → fallback 走老路径
8. **白名单**:尝试 entity="user" → 抛 ToolNotAllowedException
9. **埋点**:llm_call_log 新增 N 条(scenario=AGENT_STEP / AGENT_TOOL)
10. **审计**:audit_log 新增 N 条(action=AGENT_QUERY)

**测试方式**:
- 用 `MockBean` mock `ChatClient` 的响应(避免真实调 GLM)
- 用 `@SpringBootTest` 起完整 Spring 上下文
- 工具调用**真实**(SearchFulltextTool 真调 KnowledgeSearchService,QueryMysqlTool 真查 MySQL)

**验收**:`mvn test` 全过

**commit**:`test(agent): 10 end-to-end integration tests`

---

### I-12: 前端 Knowledge.vue 改造

**必读**:`frontend/src/views/Knowledge.vue` 当前实现

**新增组件**:`frontend/src/components/AgentStepsPanel.vue`

**AgentStepsPanel.vue 结构**:
```vue
<script setup lang="ts">
defineProps<{ steps: AgentStep[] }>()
</script>
<template>
  <el-collapse v-if="steps && steps.length > 0">
    <el-collapse-item title="查看 agent 思考过程" name="agent">
      <el-steps direction="vertical" :active="steps.length">
        <el-step v-for="(step, i) in steps" :key="i">
          <template #title>
            <span style="color: #909399">💭 {{ step.thought }}</span>
          </template>
          <template #description>
            <div>🔧 {{ step.tool }}<span v-if="step.toolArgs"> {{ JSON.stringify(step.toolArgs) }}</span></div>
            <div>👁 {{ step.observation?.substring(0, 200) }}{{ step.observation?.length > 200 ? '...' : '' }}</div>
          </template>
        </el-step>
      </el-steps>
    </el-collapse-item>
  </el-collapse>
</template>
```

**Knowledge.vue 改造**:
- 响应处理:`response.data.steps` 存在时传给 `<AgentStepsPanel>`
- 答案卡片下方加 `<AgentStepsPanel :steps="response.data.steps" />`
- 顶部加 `<el-switch v-model="agentMode"> 启用 Agent 模式` — 关时发请求带 `?agentMode=false`(QaController 收到后强制走老路径)
- `response.data.agentMode=false` 时显示 "已降级为简单检索模式" el-alert
- 响应 `data.clarification` 字段非空时,弹 `<el-dialog>` 收集用户选择,把选择拼回 question 重发

**验收**:
- `npm run build` 通过
- 浏览器:问"新能源那个项目" → 看到折叠的 "查看 agent 思考过程" → 展开看到 4-5 步
- 关 switch → 重问 → 看到 "已降级"

**commit**:
- `feat(frontend): add AgentStepsPanel component`
- `feat(frontend): Knowledge.vue integrate agent steps display + switch`

---

## §3. 完工验收 checklist

每子项完工后,接手 Agent **必跑**:

- [ ] `mvn compile -DskipTests -B -o` — 0 错
- [ ] `mvn test` (I-11 之后) — 10 个测试全过
- [ ] `npm run build` (I-12) — 0 错
- [ ] 浏览器端到端:问 5 个问题都能答(看 `AGENT-REQUIREMENTS.md` §6 验收场景)
- [ ] 关 `AGENT_ENABLED=false` 重启后端 → 问问题 → 走老路径(零回归)
- [ ] `llm_call_log` 新增 ≥ 3 条(scenario=AGENT_*)

完工后**写** `deliverable.md`:
- 12 个 commit 链接
- 编译/测试/构建截图
- 端到端浏览器测试结果
- 已知问题 / 留 TODO

---

## §4. 风险与注意

1. **Spring AI 1.1 与现有 Spring Boot 3.3 兼容性**:
   - 1.1 GA 已发布(2025-11),应该兼容
   - **若编译报错**:把 Spring AI 降到 1.0.x(API 略不同,但 `ChatClient` + `@Tool` API 兼容)

2. **智谱 GLM 走 OpenAI 兼容协议**:
   - 智谱声明兼容,但实际可能有 `model` 字段差异
   - **若 400 错**:在 `application.yml` 加 `spring.ai.openai.chat.options.model=glm-4-flash`(显式指定)

3. **5 步 ReAct 循环硬编码在 AgentEngine**:
   - 5 步 max_iterations **要自己写** — Spring AI 1.1 **没有** `spring.ai.agent.max-iterations` 配置
   - `AgentEngine.MAX_ITERATIONS = 5` 常量 + `for (int i = 0; i < 5; i++)`
   - 死循环检测**也要自己写**(同工具同 args 连续 2 次就强 FINAL_ANSWER)

4. **性能**:
   - 单次 Agent 调用 LLM 次数:典型 2-4 次,最高 6 次(含 FINAL_ANSWER)
   - GLM 60 req/min,1 个 question 占 4 个 quota,可用度 OK

5. **数据库账号**(可选优化,本期不做):
   - 加 `archive_agent_app` 用户,只 SELECT
   - 减少主应用被 SQL 注入波及的风险
   - **本期**:用主应用账号(已有 SELECT 权限),**降级方案**

6. **Prompt 调优**:
   - Few-shot 3 例是初始版本,实际跑起来根据错答调整
   - **I-11 测试时**收集 5-10 个错答,在 `deliverable.md` 列出

---

## §5. 完工后 push + 通知

```bash
# 12+ commit,每子项一个
git add backend/src/main/java/com/archive/agent/
git commit -m "feat(agent,I-N): ..."
git push origin minimax

# 完工后,推一个聚合 commit:
git add docs/AGENT-FRAMEWORK-DECISION.md  # 如果有微调
git commit -m "docs(agent,Plan I): complete - 12 subitems, all green"
git push origin minimax

# 然后通知 Mavis(沙箱)看:
# 在 deliverable.md 写一句:
# "Plan I 完工,请 owner 审"
```

**我(Mavis)看到后**:
- 跑 mvn compile 验证
- 浏览器端到端跑过
- 接受 / 打回 / 修订
- 给你(用户)报告

---

## §6. 工期估算

| 子项 | 估时 |
|---|---|
| I-1 + I-2 (依赖) | 0.3 天 |
| I-3 (骨架) | 0.5 天 |
| I-4 / I-5 / I-7 / I-8 (简单工具) | 0.5 天 × 4 = 2 天 |
| I-6 (白名单 + 聚合 + 注入防护) | 1.5 天(原 1, +0.5 加聚合) |
| I-9 (AgentEngine) | 1.5 天 |
| I-10 (QaController) | 0.5 天 |
| I-11 (集成测试) | 1 天 |
| I-12 (前端) | 0.5 天 |
| I-13 (多轮对话,补漏) | 0.5 天 |
| **合计** | **~8.3 天** |

**关键路径**:I-3 → I-9 → I-10 → I-11(顺序),其他可并行(分工给多个 sub-agent)。

---

*本 plan 由 Mavis 在沙箱出方案,生产环境 AI Agent 接手执行。任何冲突以 `AGENT-FRAMEWORK-DECISION.md` v1.0 为准。*


### I-13: 多轮对话(补业务需求 §4.4 漏实现)

**必读**:`docs/AGENT-REQUIREMENTS.md` §4.4 — "后续问题自动带上文实体" / "第一轮说 PRJ-001,第二轮'它多少钱'自动补全主语" / "历史 chip 恢复上文"。这是**业务明确要求**,原 plan-I 12 子项**漏了**。

**新增文件**(4 个):
- `db/migration/I-chat-memory.sql` — `chat_memory` 表
- `agent/ChatMemoryConfig.java` — `JdbcChatMemoryRepository` bean
- `agent/MultiTurnController.java` — `/api/qa/turn/{sessionId}` 端点
- `agent/MultiTurnService.java` — 多轮上下文装配

**chat_memory 表 SQL**:
```sql
CREATE TABLE chat_memory (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  message_type VARCHAR(16) NOT NULL,  -- 'user' / 'assistant' / 'system' / 'tool'
  content TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**ChatMemoryConfig 关键代码**:
```java
@Configuration
public class ChatMemoryConfig {
    @Bean
    public JdbcChatMemoryRepository chatMemoryRepository(DataSource ds) {
        return JdbcChatMemoryRepository.builder()
            .dataSource(ds)
            .tableName("chat_memory")
            .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repo) {
        return new JdbcChatMemory(repo);  // 走 MySQL 持久化,重启不丢
    }

    @Bean
    public Advisor memoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
            .conversationId("default")  // sessionId 后续从 MultiTurnController 注入
            .build();
    }
}
```

**MultiTurnController 关键代码**:
```java
@RestController
@RequestMapping("/api/qa/turn")
@RequiredArgsConstructor
public class MultiTurnController {
    private final ChatClient chatClient;  // 已经注入 @Tool + memoryAdvisor

    @PostMapping("/{sessionId}")
    public ApiResponse<QaResponse> turn(
        @PathVariable String sessionId,
        @RequestBody QaRequest req) {
        // 用 sessionId 拉历史 + 拼上下文
        ChatResponse response = chatClient.prompt()
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .user(req.getQuestion())
            .call();
        return ApiResponse.ok(...);
    }
}
```

**验收**:
- `mvn compile` 通过
- 浏览器:问"新能源那个项目"+ 重问"它的剩余金额" → 第 2 轮 LLM 自动锁定 PRJ-2026-001
- 查 chat_memory 表:每轮 question + answer 都落库
- 重启后端:再问第 1 个 session,历史能恢复(走 MySQL 持久化)

**commit**:`feat(agent,I-13): add multi-turn chat with MessageChatMemoryAdvisor + JdbcChatMemoryRepository`
