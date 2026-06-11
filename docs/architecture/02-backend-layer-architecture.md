# 投委会档案管理系统 — 后端分层架构

> 撰写人：Sisyphus | 日期：2026-06-10 | 版本：v1.0 + v1.1 (2026-06-11)

## 1. 包结构总览

```
com.archive/
├── ArchiveApplication.java            # 启动类
├── common/                            # 公共组件
├── config/                            # 配置加载
├── controller/                        # REST API (18个, v1.1 +5)
├── dto/                               # 数据传输对象 (30+个)
├── entity/                            # JPA实体 (23个, v1.1 +7)
├── repository/                        # 数据访问 (20+个)
├── security/                          # 认证授权 (4个)
├── service/                           # 业务逻辑 (29个, v1.1 +12)
├── engine/                            # 异步引擎 (4个)
├── provider/                          # LLM抽象 (2个)
└── agent/                             # 智能Agent (Plan I, 14个文件)
```

### 文件统计

| 层 | 文件数 | 注解风格 |
|-----|--------|----------|
| controller | 18 | @RestController + @RequestMapping |
| service | 29 | @Service + @Slf4j |
| engine | 4 | @Service + @Async |
| provider | 2 | @Service + 接口 |
| repository | 16 | @Repository + extends JpaRepository |
| entity | 16 | @Entity + @Table |
| security | 4 | @Component + @Configuration |
| agent | 14 | @Component + @Service + @Configuration |
| dto | 24 | @Data + @Builder |
| common | 4 | @RestControllerAdvice 等 |
| config | 1 | EnvironmentPostProcessor |
| **总计** | **~115** | |

---

## 2. Controller 层

> 职责：请求路由、参数校验、响应转换

### 路由表

| Controller | 路径 | 认证 | 说明 |
|-----------|------|------|------|
| AuthController | `/api/auth` | 公开 + JWT | login(), me() |
| HealthController | `/api/health` | 公开 | 健康检查 |
| QaController | `/api/qa` | JWT | Agent模式 + 降级路径(ask) |
| MultiTurnController | `/api/qa/turn` | JWT | 多轮对话 (P-I, @ConditionalOnProperty) |
| ProjectController | `/api/projects` | JWT | 项目CRUD |
| ProposalController | `/api/proposals` | JWT | 议案CRUD + 重生成摘要 |
| MaterialController | `/api/materials` | JWT | 材料CRUD |
| MaterialVersionController | `/api/materials/{id}/versions` | JWT | 版本控制 + 上传解析 + 章节切分 |
| TodoController | `/api/todos` | JWT | 待办CRUD + 完成 |
| AuditLogController | `/api/audit-logs` | admin | 审计日志查询 |
| LlmUsageController | `/api/llm` | JWT + admin | LLM用量统计 |
| DictController | `/api/dict` | admin | 字典管理 |
| ExtractionMethodController | `/api/extraction-methods` | admin | 抽取方法CRUD |
| ComparisonMethodController | `/api/comparison-methods` | admin | 对比方法CRUD |
| TriggerRuleController | `/api/trigger-rules` | admin | 触发规则CRUD |

### 调用约定

```java
@PostMapping
public ApiResponse<XxxResponse> create(@Valid @RequestBody XxxRequest req)
    // → 调用 XxxService.create()
    // → 返回 ApiResponse.ok(response)
```

- 全部使用 `ApiResponse<T>` 统一包装
- 请求参数使用 `@Valid` 校验
- 异常由 `GlobalExceptionHandler` 统一映射

---

## 3. Service 层

> 职责：业务逻辑编排、事务管理、跨表操作

### 服务清单

| Service | 核心方法 | 关键行为 |
|---------|---------|----------|
| AuthService | login(), getClientIp() | LoginRateLimiter 校验 + JWT 生成 |
| ProjectService | CRUD | 项目状态流转 |
| ProposalService | CRUD, triggerComparison(), triggerAutoSummary() | 议案提交→异步对比+摘要 |
| MaterialService | CRUD, batchUpload() | 批量上传 |
| MaterialVersionService | upload(), parseVersion(), triggerAfterParse() | 上传→SHA-256→Tika→异步引擎触发 |
| SectionService | split() | 3种正则模式切分章节 |
| KnowledgeSearchService | search() | MySQL FULLTEXT NativeQuery → extractSnippet() |
| GlmService | chat(), rerank(), semanticMatchProjects(),callWithLog() | 智谱GLM HTTP调用 + 埋点 |
| TikaService | extractText(), detectMimeType() | Apache Tika 解析文本 |
| DictService | listByType(), getItem(), cache eviction | 5秒本地缓存 |
| TimepointService | CRUD | 时点管理 |
| TodoService | CRUD, complete() | 待办管理 |
| TriggerRuleService | CRUD | 规则管理 |
| AuditLogService | save(), query() | 审计日志 |
| LlmUsageService | getUsage() | LLM用量统计 |
| ComparisonMethodService | CRUD | 对比方法CRUD |
| ExtractionMethodService | CRUD | 抽取方法CRUD |

### 关键异步调用链

```
MaterialVersionService.triggerAfterParse()
  → ExtractionEngine.extract()           @Async
    → TimepointExtractor.extractTimepoints()  @Async
      → TriggerEngine.evaluate()            @EventListener
        → ProjectService.updateRemainingAmount()
```

---

## 4. Engine 层

> 职责：异步/事件驱动的复杂业务逻辑，独立于 Service 层编排

### 引擎清单

| Engine | 触发方式 | 异步 | 关键行为 |
|--------|---------|------|---------|
| ExtractionEngine | @Async 调用 | ✅ | 字段抽取，LLMProvider → JSON 解析 |
| ComparisonEngine | @Async 调用 | ✅ | 立项vs申请报告对比 |
| TimepointExtractor | @Async 调用 | ✅ | 文本→时点抽取→Timepoint实体 |
| TriggerEngine | @EventListener | ✅ | 4种事件→规则匹配→动作执行 |

### TriggerEngine 设计

**4 种事件类型**：
- MATERIAL_UPLOADED: 材料上传
- CATEGORY_CHANGED: 分类变更
- STATUS_CHANGED: 状态变更
- TIMEPOINT_APPROACHING: 时点临近

**SimpleExpressionEvaluator**：基于 Aviator 5.4.2，支持 `== / != / contains / && / ||` 操作符

---

## 5. Provider 层（LLM 抽象）

### 接口设计

```java
public interface LLMProvider {
    String chat(String systemPrompt, String userMessage);
    <T> T chatJson(String systemPrompt, String userMessage, Class<T> clazz);
    String vision(String imageBase64, String prompt);
    String name();
}
```

### 实现

| Provider | 后端 | 状态 |
|---------|------|------|
| GLMProvider | 智谱 GLM-4-Flash | ✅ 默认 |
| OpenAIProvider | OpenAI/Jina AI | 🔧 预留 |
| MockProvider | 模拟返回 | ✅ 测试用 |

**工厂策略**：`LLMProviderFactory` 根据 `app.llm.provider` 配置选择实现，未配置时默认 "glm"。

**特殊实现**：`agent.GLMChatModel` 实现 Spring AI `ChatModel` 接口，将 Agent 层的 Spring AI 调用桥接到已有的 `GlmService`，避免两套 LLM 调用代码。

---

## 6. Repository 层

> 职责：数据访问、自定义查询

### 特殊查询方法

| Repository | 方法 | 实现方式 |
|-----------|------|---------|
| ProjectRepository | searchByNameOrCustomerFulltext() | @Query nativeQuery, FULLTEXT MATCH AGAINST |
| ProjectRepository | searchByKeywordAsList() | JPQL LIKE 多字段 |
| LlmCallLogRepository | groupByScenarioBetween() | @Query GROUP BY scenario |
| LlmCallLogRepository | sumTotalTokensBetween() | @Query SELECT SUM |
| TodoRepository | countByProjectIdAndStatus() | Spring Data Method Name |

### 查询策略

- **全文检索**：MySQL FULLTEXT + ngram parser（1-2 字最小 token）
- **语义项目锁定**（Agent）：4 级兜底策略
  1. 精确项目代码匹配（`code = ?`）
  2. FULLTEXT MATCH AGAINST（`name, customer_name`）
  3. JPQL LIKE（`%keyword%`）
  4. LLM 语义匹配（GlmService.semanticMatchProjects）
- **安全查询**（Agent.QueryMysqlTool）：严格白名单 + 参数化 SQL

---

## 7. Entity 层

### 实体关系

```
User ──→ Project ──→ Proposal ──→ Material ──→ MaterialVersion
                                        │
                                        ├── ChapterSummary
                                        └── Timepoint

Project ──→ Todo
TriggerRule ──→ TriggerAction
DictType ──→ DictItem
```

### 公共基类

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {
    LocalDateTime createdAt;    // @CreatedDate
    LocalDateTime updatedAt;   // @LastModifiedDate
    String createdBy;          // @CreatedBy
    String updatedBy;          // @LastModifiedBy
}
```

**例外**：`AuditLog` 和 `LlmCallLog` 不继承 BaseEntity（需要自定义时间戳处理）。

---

## 8. Security 层

### 认证流程

```
请求 → JwtAuthFilter.doFilterInternal()
  ├── 提取 Authorization: Bearer <token>
  ├── JwtUtil.parse() → Claims (uid, username, role)
  ├── 验证 JWT 过期 (8h)
  └── 设置 SecurityContext (AuthenticatedUser)

/login → AuthController.login()
  ├── LoginRateLimiter.check() (5次/分钟, 锁定15分钟)
  ├── JwtUtil.generate() (HS256, 8h)
  └── 返回 token + user info
```

### 授权规则

```java
// SecurityConfig
requestMatchers("/api/health").permitAll()
requestMatchers("/api/auth/**").permitAll()
requestMatchers("/api/actuator/**").permitAll()
requestMatchers(HttpMethod.GET, "/api/dict/**").permitAll()  // 部分公开
requestMatchers("/api/admin/**").hasRole("ADMIN")
requestMatchers("/api/**").authenticated()
```

### 限流机制

`LoginRateLimiter`：ConcurrentHashMap<IP, AttemptRecord> 实现
- 5 次/分钟 阈值
- 15 分钟锁定
- 应用重启后重置

---

## 9. Agent 层（Plan I）

### 架构图

```
QaController (/api/qa/ask)
  │
  ├── [enabled=true] → AgentEngine.run()
  │     │
  │     ├── AgentSystemPrompt.render(ctx)  → system prompt
  │     ├── LLM call → parseAgentStep()
  │     ├── dispatchTool() → 6 AgentTool implementations
  │     │     ├── FindProjectTool (4-tier fallback)
  │     │     ├── SearchFulltextTool (FULLTEXT)
  │     │     ├── QueryMysqlTool (whitelist + aggregation)
  │     │     ├── GetProjectBusinessDataTool (项目汇总)
  │     │     ├── LlmSummarizeTool (LLM摘要)
  │     │     └── AskClarificationTool (追问)
  │     │
  │     ├── MessageChatMemoryAdvisor (多轮对话)
  │     └── LlmCallListener / ToolCallListener (埋点+审计)
  │
  └── [enabled=false / exception] → GlmService 老路径 (降级)
```

### 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| ReAct 循环 | 手写 for 循环，MAX_ITERATIONS=5 硬编码 | Spring AI 1.1 无 ReactAgent |
| 工具接口 | 自定义 `AgentTool` 接口，非 Spring AI @Tool 注解 | 更好的控制力和可测试性 |
| LLM 桥接 | GLMChatModel 包装 GlmService | 复用现有 API key + 埋点 |
| 降级策略 | 异常 → catch → 走 GlmService 老路径 | 零回归要求 |

---

## v1.1 增量 (MOD-01~05, 2026-06-11)

| 层 | v1.0 | v1.1 | 新增 |
|---|---|---|---|
| Controller | 13 | **18** | ProjectBoard, Notification, RecycleBin, Import, FailureLog |
| Service | 17 | **29** | ProjectBoard, Notification, Export, Preview, Masking, NetworkDict, Rbac, RecycleBin, Import, FailureLog, Archive, ProjectFactEvent |
| AgentTool | 6 | **7** | +network_dict_lookup (RI-50) |
| Entity | 16 | **23** | Notification, ImportBatch, ImportError, ProjectFactEvent, BusinessTerm 等 |

**v1.1 核心域**: 7 表软删 ALTER + RBAC 双轨 + 乐观锁 (D-3 strict=false) + 5 类审计 + failure_log.
