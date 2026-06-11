# MOD-03 — Agent 工具改造（5 级 / 7 重加固 / 网络字典 / 失败兜底）

> **接手 agent 只需读本文 + `MOD-01` SQL + `MOD-02` 实体字段 + 现有 `com.archive.agent` 包即可开工**

---

## §0 模块目标

v1.1 Agent 工具集微调 + 新增 1 工具：
- `find_project` 改造：5 级隐式切换判定（in-tool，**不算 ReAct 步数**）
- `query_mysql` 改造：4 重 → 7 重加固（白名单 filters + 行数截断 + 数值上限）
- `network_dict_lookup` **新增**（工具 7，可配 2 候选）
- `prompt/AgentSystemPrompt` 追加 3 级置信度 + 5 级切换说明
- `prompt/AgentFewShots` 加英文术语 few-shot
- LLM 抽字段 5 种 FailureType 兜底
- `project_fact_event` EntityListener 拦截（DB 触发器 + 应用层双保险）

---

## §1 涉及 RI

| RI | 改造 |
|---|---|
| RI-22（置信度 3 级） | `project_fact.confidence_level` 字段读 + AgentSystemPrompt 追加说明 |
| RI-23（5 级隐式切换） | `FindProjectTool` 内部 5 级判定 + AgentContext 增字段 |
| RI-26（网络查字典） | `NetworkDictLookupTool` 新增 + `NetworkDictService` |
| RI-27（7 重加固） | `QueryMysqlTool` 5 类 filters 白名单 + 行数截断 + 数值上限 |
| RI-30（LLM 抽字段失败兜底） | `GlmService` 5 种 FailureType + `ExtractionEngine` 改 onFailure |
| RI-46（置信度 3 级实操） | `project_fact_event.confidence_level` + EntityListener 触发器白名单 |
| RI-52（事实事件字段） | `project_fact_event` EntityListener @PreUpdate/@PreDelete 拦截 |
| RI-43（业务术语英文） | AgentFewShots 加英文术语 few-shot |

---

## §2 涉及文件（独占清单）

**接手 agent 只允许改以下文件**：

### 2.1 新建（3 个文件）

```
backend/src/main/java/com/archive/
├── agent/tool/NetworkDictLookupTool.java   (新, RI-26)
├── service/NetworkDictService.java         (新, RI-26)
└── common/FailureType.java                 (新, 5 枚举, RI-30)
```

### 2.2 修改（8 个文件，独占）

```
backend/src/main/java/com/archive/
├── agent/
│   ├── tool/
│   │   ├── FindProjectTool.java            (改, RI-23, 5 级判定)
│   │   ├── QueryMysqlTool.java             (改, RI-27, 7 重加固)
│   │   └── NetworkDictLookupTool.java      (新, RI-26)
│   ├── prompt/
│   │   ├── AgentSystemPrompt.java          (改, RI-22/23, 追加 2 段)
│   │   └── AgentFewShots.java              (改, RI-43, 加英文术语)
│   ├── AgentContext.java                   (改, 增 lastSwitchDecision 字段, RI-23)
│   ├── AgentResponse.java                  (改, 增 projectSwitchHint + confidenceBadge, RI-22/23)
│   └── AgentConfig.java                    (不改, 7 工具自动扫 @Component)
├── service/
│   └── GlmService.java                     (改, RI-30, callWithLog 异常分类)
├── engine/
│   └── ExtractionEngine.java               (改, RI-30, onFailure(FailureType))
└── entity/
    └── ProjectFactEvent.java               (改, RI-46/52, @EntityListeners + @PreUpdate/@PreDelete)
```

**总计**：3 新 + 8 改 = 11 个文件

---

## §3 设计要点

### 3.1 FindProjectTool 5 级判定（RI-23 关键）

**核心约束**：5 级判定在 tool **内部**完成，**不算 ReAct 步数**（`MAX_ITERATIONS=5` 硬编码不变）。

```java
// tool/FindProjectTool.java
@Component
public class FindProjectTool implements AgentTool {
    
    private static final Logger log = LoggerFactory.getLogger(FindProjectTool.class);
    
    @Override
    public String name() { return "find_project"; }
    
    @Override
    public ToolResult execute(JsonNode args, AgentContext ctx) {
        String query = args.get("query").asText();
        int topN = args.has("topN") ? args.get("topN").asInt() : 5;
        
        // 1-4 级兜底链（v1.0 已有，不破）
        List<ProjectMatch> candidates = findCandidates(query, topN);
        
        if (candidates.isEmpty()) {
            return ToolResult.fail("未找到匹配项目");
        }
        
        // 5 级判定（v1.1 新增）
        ProjectMatch top = candidates.get(0);
        String currentLock = ctx.getLockedProjectCode();
        SwitchDecision decision = applyImplicitSwitchRule(top, currentLock);
        
        ctx.setLastSwitchDecision(decision);  // 供 AgentEngine 注入 hint
        
        switch (decision) {
            case SAME_CONFIRMED:
                return ToolResult.ok(buildResult(candidates, "SAME_CONFIRMED"));
            case SAME_PROBABLY:
                return ToolResult.ok(buildResult(candidates, "SAME_PROBABLY"));
            case DIFFERENT_PROBABLY:
                return ToolResult.ok(buildResult(candidates, "DIFFERENT_PROBABLY"));
            case UNCLEAR:
                return ToolResult.ok(buildResult(candidates, "UNCLEAR"));
            default:
                return ToolResult.fail("决策异常");
        }
    }
    
    /**
     * 5 级隐式项目切换判定规则 (RI-23):
     * 1. conf >= 0.95, 同 projectCode → SAME_CONFIRMED (自动锁定)
     * 2. conf 0.7-0.95, 同 projectCode → SAME_PROBABLY (hint 注入)
     * 3. conf 0.5-0.7, 同 projectCode → UNCLEAR (反问用户)
     * 4. conf >= 0.7, 不同 projectCode → DIFFERENT_PROBABLY (反问用户切换?)
     * 5. conf < 0.5, 不同 projectCode → UNCLEAR (反问用户)
     */
    private SwitchDecision applyImplicitSwitchRule(ProjectMatch top, String currentLock) {
        double conf = top.getConfidence();
        boolean sameCode = top.getCode().equals(currentLock);
        
        if (sameCode && conf >= 0.95) return SwitchDecision.SAME_CONFIRMED;
        if (sameCode && conf >= 0.7)  return SwitchDecision.SAME_PROBABLY;
        if (sameCode && conf >= 0.5)  return SwitchDecision.UNCLEAR;
        if (!sameCode && conf >= 0.7) return SwitchDecision.DIFFERENT_PROBABLY;
        return SwitchDecision.UNCLEAR;
    }
}
```

**新增枚举**：
```java
// common/SwitchDecision.java (新, 5 枚举)
public enum SwitchDecision {
    SAME_CONFIRMED,        // 同项目 + 高置信, 自动锁定
    SAME_PROBABLY,         // 同项目 + 中置信, hint 注入
    DIFFERENT_PROBABLY,    // 不同项目 + 中高置信, 反问切换
    UNCLEAR                // 低置信, 反问用户
}
```

### 3.2 QueryMysqlTool 7 重加固（RI-27）

v1.0 有 4 重加固：`group_by` / `is_not_null` / `MAX_IN_VALUES=50` / `escapeLikePattern`
v1.1 加 3 重：**filters 白名单** / **行数截断** / **数值上限**

```java
// tool/QueryMysqlTool.java
@Component
public class QueryMysqlTool implements AgentTool {
    
    // v1.0 4 重（保留）
    private static final Set<String> ALLOWED_AGGREGATES = Set.of("count", "sum", "avg", "max", "min", "group_by");
    private static final Set<String> ALLOWED_OPERATORS = Set.of("=", "!=", ">", ">=", "<", "<=", "in", "like", "is_null", "is_not_null");
    private static final int MAX_IN_VALUES = 50;
    private static final String LIKE_ESCAPE = "\\";
    
    // v1.1 新增 3 重
    private static final Set<String> ALLOWED_FILTER_KEYS = Set.of(
        "region", "industry", "stage", "fact_type", "time_bucket"
    );
    private static final int MAX_RESULT_ROWS = 1000;
    private static final double MAX_AMOUNT = 1e8;
    
    @Override
    public ToolResult execute(JsonNode args, AgentContext ctx) {
        String entity = args.get("entity").asText();
        JsonNode filters = args.get("filters");  // v1.1 新增
        String aggregate = args.has("aggregate") ? args.get("aggregate").asText() : null;
        String operator = args.has("operator") ? args.get("operator").asText() : "=";
        JsonNode value = args.get("value");
        
        // 重 1: 白名单 entity
        if (!ALLOWED_ENTITIES.contains(entity)) {
            return ToolResult.fail("entity 不在白名单: " + entity);
        }
        
        // 重 2: 白名单 operator
        if (!ALLOWED_OPERATORS.contains(operator)) {
            return ToolResult.fail("operator 不在白名单: " + operator);
        }
        
        // 重 3: 白名单 aggregate
        if (aggregate != null && !ALLOWED_AGGREGATES.contains(aggregate)) {
            return ToolResult.fail("aggregate 不在白名单: " + aggregate);
        }
        
        // 重 4: IN 长度上限
        if ("in".equals(operator) && value.isArray() && value.size() > MAX_IN_VALUES) {
            return ToolResult.fail("IN 值超限: " + value.size() + " > " + MAX_IN_VALUES);
        }
        
        // 重 5: LIKE 自动转义
        if ("like".equals(operator) && value.isTextual()) {
            String escaped = value.asText().replace("%", LIKE_ESCAPE + "%").replace("_", LIKE_ESCAPE + "_");
            value = JsonNodeFactory.instance.textNode(escaped);
        }
        
        // 重 6 (v1.1 新增): filters 白名单校验
        if (filters != null && filters.isObject()) {
            Iterator<String> keys = filters.fieldNames();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!ALLOWED_FILTER_KEYS.contains(key)) {
                    return ToolResult.fail("filter key 不在白名单: " + key);
                }
                // 数值范围校验
                if ("amount".equals(key) || "max_amount".equals(key)) {
                    double v = filters.get(key).asDouble();
                    if (v > MAX_AMOUNT) {
                        return ToolResult.fail("数值超限: " + v + " > " + MAX_AMOUNT);
                    }
                }
            }
        }
        
        // 执行查询
        List<Map<String, Object>> rows = queryService.execute(entity, filters, operator, value, aggregate);
        
        // 重 7 (v1.1 新增): 行数截断
        if (rows.size() > MAX_RESULT_ROWS) {
            rows = rows.subList(0, MAX_RESULT_ROWS);
            return ToolResult.okWithWarning(rows, "结果超 " + MAX_RESULT_ROWS + " 行, 已截断. 请缩小范围.");
        }
        
        return ToolResult.ok(rows);
    }
}
```

### 3.3 NetworkDictLookupTool 新增（RI-26 工具 7）

**D-2 拍板**：v1.1 实施只配 2 候选（百度百科 + 维基百科），金融/互动留占位。

```java
// tool/NetworkDictLookupTool.java
@Component
public class NetworkDictLookupTool implements AgentTool {
    
    @Autowired private NetworkDictService dictService;
    
    @Override
    public String name() { return "network_dict_lookup"; }
    
    @Override
    public ToolResult execute(JsonNode args, AgentContext ctx) {
        String query = args.get("query").asText();
        String preferredSource = args.has("source") ? args.get("source").asText() : null;
        
        // 委托 NetworkDictService，6 层降级
        DictLookupResult result = dictService.lookup(query, preferredSource);
        
        // 不抛异常, 返回 found=false
        return ToolResult.ok(Map.of(
            "found", result.isFound(),
            "definition", result.getDefinition(),
            "source", result.getSource(),
            "reason", result.isFound() ? null : result.getReason()
        ));
    }
}

// service/NetworkDictService.java
@Service
public class NetworkDictService {
    
    @Autowired private DictItemRepository dictItemRepo;
    
    // 配置：dict_type.code='network_dict_source', dict_item.item_value='{"baseUrl":"...","apiKey":"..."}'
    
    public DictLookupResult lookup(String query, String preferredSource) {
        // 1. 查配置（按优先级排序）
        List<DictItem> sources = dictItemRepo.findByTypeAndEnabled("network_dict_source", true);
        if (preferredSource != null) {
            sources = sources.stream()
                .filter(s -> s.getCode().equals(preferredSource))
                .collect(Collectors.toList());
        }
        
        if (sources.isEmpty()) {
            return DictLookupResult.notFound("NO_SOURCE_ENABLED");
        }
        
        // 2. 逐个尝试（6 层降级）
        for (DictItem source : sources) {
            try {
                String definition = callApi(source, query);
                if (definition != null) {
                    return DictLookupResult.found(definition, source.getCode());
                }
            } catch (Exception e) {
                log.warn("dict source {} failed: {}", source.getCode(), e.getMessage());
                // 继续尝试下一个
            }
        }
        
        // 3. 全失败
        return DictLookupResult.notFound("INTRANET_BLOCKED");
    }
    
    private String callApi(DictItem source, String query) {
        // 调百度百科 / 维基百科 API
        // 配置在 dict_item.item_value JSON: {"baseUrl":"...","apiKey":"..."}
        // 返回 null 表示未找到
        return httpClient.get(source.getConfig().get("baseUrl") + "?query=" + URLEncoder.encode(query))
            .timeout(5000)
            .retrieve()
            .body(String.class);
    }
}
```

### 3.4 AgentSystemPrompt 追加 2 段（RI-22/23）

```java
// prompt/AgentSystemPrompt.java
@Component
public class AgentSystemPrompt {
    
    public String render(AgentContext ctx) {
        String basePrompt = """
            [v1.0 原有 prompt 内容保持不变]
            
            ---
            
            ## 置信度 3 级体系 (RI-22)
            当你抽取字段 / 总结事实时，confidence 字段请按 3 级标注：
            - ≥ 0.85: CONFIRMED — 高置信，可直接入库
            - 0.60 - 0.84: AI_INFERRED — 中置信，需 UI 标"AI 推测"
            - < 0.60: PENDING_REVIEW — 低置信，标"待人工确认"
            
            ## 隐式项目切换 5 级判定 (RI-23)
            当 find_project 返回结果与当前 locked project 冲突时，按 5 级处理：
            - conf ≥ 0.95 同 projectCode → 自动锁定
            - conf 0.7-0.95 同 projectCode → hint "可能是同项目, 请确认"
            - conf 0.5-0.7 同 projectCode → 反问用户
            - conf ≥ 0.7 不同 projectCode → 反问用户 "是切到 X 吗?"
            - conf < 0.5 / 都不命中 → 保持锁定, 反问用户
            """;
        return basePrompt;
    }
}
```

### 3.5 AgentFewShots 加英文术语（RI-43）

```java
// prompt/AgentFewShots.java
@Component
public class AgentFewShots {
    
    public List<Example> examples() {
        List<Example> all = new ArrayList<>(v10Examples());  // v1.0 5 条
        all.add(new Example("""
            用户: "vacant claim 是什么意思?"
              → Step 1: 调用 network_dict_lookup(query="空债权", source="baidu_baike")
              → Step 2: 返回中文定义 "空债权 = 借款人无财产可供执行的债权..."
              → Step 3: FINAL_ANSWER 翻译为 "vacant claim（空债权）= 借款人无财产可供执行的债权"
            """));
        return all;
    }
}
```

### 3.6 LLM 抽字段失败 5 种 FailureType（RI-30）

```java
// common/FailureType.java
public enum FailureType {
    API_ERROR,         // LLM API 4xx/5xx
    PARSE_ERROR,       // 返回非 JSON
    FIELD_MISSING,     // 必填字段缺失
    VALUE_INVALID,     // 字段值异常（如 amount=-1）
    TIMEOUT            // 调用超时
}

// service/GlmService.java
public ExtractionFailureResponse callWithLog(String prompt) {
    try {
        String response = llmProvider.call(prompt);
        JsonNode json = parseJson(response);
        
        // 校验必填字段
        if (!json.has("projectName") || !json.has("amount")) {
            return ExtractionFailureResponse.of(FailureType.FIELD_MISSING, "缺少必填字段", true);
        }
        
        // 校验字段值
        double amount = json.get("amount").asDouble();
        if (amount < 0) {
            return ExtractionFailureResponse.of(FailureType.VALUE_INVALID, "amount 不能为负", false);
        }
        
        return ExtractionFailureResponse.ok(json);
        
    } catch (HttpClientErrorException e) {
        return ExtractionFailureResponse.of(FailureType.API_ERROR, e.getMessage(), true);
    } catch (JsonProcessingException e) {
        return ExtractionFailureResponse.of(FailureType.PARSE_ERROR, e.getMessage(), true);
    } catch (SocketTimeoutException e) {
        return ExtractionFailureResponse.of(FailureType.TIMEOUT, e.getMessage(), true);
    } catch (Exception e) {
        return ExtractionFailureResponse.of(FailureType.API_ERROR, e.getMessage(), true);
    }
}

// engine/ExtractionEngine.java
public void onFailure(FailureType type, String message) {
    log.error("Extraction failed: type={}, msg={}", type, message);
    // 触发器: triggerRuleEngine("extraction_failure", type);
    // 通知: notificationService.notifyAdmin("抽字段失败: " + type);
}
```

### 3.7 ProjectFactEvent EntityListener 双保险（RI-46/52）

```java
// entity/ProjectFactEvent.java
@Entity
@Table(name = "project_fact_event")
@EntityListeners(ProjectFactEventListener.class)
public class ProjectFactEvent {
    // ... 字段 ...
}

// 新建: entity/ProjectFactEventListener.java
public class ProjectFactEventListener {
    
    private static final Set<String> UPDATABLE_FIELDS = Set.of(
        "ownerId", "dueDate", "resolvedAt", "resolutionNote"
    );
    
    @PreUpdate
    public void preUpdate(ProjectFactEvent evt) {
        // Hibernate DirtyChecking: 拿不到 dirty fields 列表, 用反射或简单方案:
        // 业务层调 service.update(evt) 时, service 自己校验
        // 此处仅记录日志, 真正校验在 ProjectFactEventService.update()
    }
    
    @PreRemove
    public void preRemove(ProjectFactEvent evt) {
        throw new IllegalStateException("project_fact_event is INSERT-only (DELETE forbidden, see DB trigger trg_fact_event_no_delete)");
    }
}
```

**业务层校验**（更可靠）：
```java
// service/ProjectFactEventService.java
@Transactional
public ProjectFactEvent update(Long eventId, ProjectFactEvent patch) {
    ProjectFactEvent existing = repo.findById(eventId).orElseThrow();
    
    // 校验：只允许改 4 字段
    Set<String> allowed = Set.of("ownerId", "dueDate", "resolvedAt", "resolutionNote");
    
    // 简化方案: 用业务方法而非反射
    if (patch.getOwnerId() != null) existing.setOwnerId(patch.getOwnerId());
    if (patch.getDueDate() != null) existing.setDueDate(patch.getDueDate());
    if (patch.getResolvedAt() != null) existing.setResolvedAt(patch.getResolvedAt());
    if (patch.getResolutionNote() != null) existing.setResolutionNote(patch.getResolutionNote());
    
    return repo.save(existing);
}
```

---

## §4 验收

### 4.1 编译验证

```bash
cd /workspace/projects-online
mvn compile -DskipTests -B
# 期望：BUILD SUCCESS
```

### 4.2 单元测试（关键）

```bash
mvn test -B \
  -Dtest='FindProjectToolTest,QueryMysqlToolTest,NetworkDictLookupToolTest,AgentSystemPromptTest,AgentFewShotsTest,GlmServiceTest'
# 期望：≥ 30 测例全过
```

**测例分布**：
- `FindProjectToolTest` ≥ 5 条（5 级判定全覆盖）
- `QueryMysqlToolTest` ≥ 8 条（4 重 v1.0 + 3 重 v1.1 = 7 重）
- `NetworkDictLookupToolTest` ≥ 3 条（命中 / 降级 / 全失败）
- `GlmServiceTest` ≥ 5 条（5 种 FailureType）
- `AgentSystemPromptTest` ≥ 1 条（渲染验证）
- `AgentFewShotsTest` ≥ 1 条（5 + 1 = 6 条 few-shot）

### 4.3 Agent 集成测例（AgentEngineTest）

```bash
mvn test -Dtest=AgentEngineTest -B
# 期望：ReAct 5 步循环 + 7 工具自动注入（含 NetworkDictLookupTool）
```

### 4.4 关键场景验证

| RI | 场景 | 期望 |
|---|---|---|
| RI-23 | 锁定 PRJ-001，问"新能源项目" conf=0.92（同项目） | ToolResult `SAME_PROBABLY` + AgentContext.lastSwitchDecision=SAME_PROBABLY |
| RI-23 | 锁定 PRJ-001，问"江苏那个" conf=0.92（不同项目） | ToolResult `DIFFERENT_PROBABLY` + AgentResponse.projectSwitchHint='DIFFERENT_PROBABLY' |
| RI-23 | 锁定 PRJ-001，问"什么项目" conf=0.5 | ToolResult `UNCLEAR` |
| RI-27 | query_mysql filters={region:'江苏'} | 命中 |
| RI-27 | query_mysql filters={region:'<script>'} | reject + log |
| RI-27 | query_mysql result rows=1500 | 截断 + warning |
| RI-27 | query_mysql amount=1e10 | reject + "数值超限" |
| RI-26 | network_dict_lookup("空债权") | 命中百度百科返回定义 |
| RI-26 | network_dict_lookup("XYZ") 全失败 | 返回 `{found: false, reason: 'INTRANET_BLOCKED'}` |
| RI-30 | GlmService API 4xx | 响应 `failureType=API_ERROR, retryable=true` |
| RI-30 | GlmService 返回非 JSON | 响应 `failureType=PARSE_ERROR` |
| RI-46/52 | UPDATE project_fact_event fact_value（非白名单） | DB 触发器 SIGNAL 抛错 |
| RI-46/52 | DELETE project_fact_event | DB 触发器 + EntityListener 双抛错 |

### 4.5 完工 checklist

- [ ] 3 新文件 + 8 改文件全部 commit
- [ ] `mvn compile` 0 错
- [ ] `mvn test` ≥ 30 测例过
- [ ] §4.4 关键场景全过
- [ ] 改 `TASKS.md` 状态 → `已完成`

---

## §5 踩坑预警

### 5.1 5 级判定 in-tool 不算 ReAct 步数（**关键**）

`MAX_ITERATIONS=5` 硬编码（`AgentEngine.java:27`）。5 级判定**必须**在 `FindProjectTool.execute()` 内部完成，**不能**让 AgentEngine 多走 1 步来判定。否则会破坏 T1 §0 风险 C-7 决策。

### 5.2 QueryMysqlTool 旧测例必须全过

v1.0 已有 4 重加固的 8 测例必须保留全过，**不能**因为 v1.1 加 3 重就改 v1.0 测例。v1.0 测例是 `src/test/java/com/archive/agent/tool/QueryMysqlToolTest.java`，**先跑**确认全过再写 v1.1 测例。

### 5.3 NetworkDictLookupTool 不抛异常

D-2 拍板：网络查失败**不抛异常**，返回 `{found: false, reason: ...}`。AgentEngine 拿到这个结果应继续走其他 tool 或 FINAL_ANSWER，不能因为字典查不到就崩。

### 5.4 字典配置存 dict_item.item_value JSON

不新建表，沿用 `dict_type` / `dict_item` 现有结构。配置 JSON 格式：
```json
{
  "baseUrl": "https://baike.baidu.com/api/...",
  "apiKey": "xxx",
  "timeout": 5000
}
```

### 5.5 AgentResponse 新增字段必须 nullable

```java
public class AgentResponse {
    private String answer;
    private List<AgentStep> steps;
    private List<String> sources;
    private Boolean agentMode;
    // v1.1 新增（nullable, 零回归）
    private String projectSwitchHint;  // null = v1.0 路径, 非 null = v1.1 触发
    private String confidenceBadge;    // null = 不显示, "AI_INFERRED" / "PENDING_REVIEW"
}
```

### 5.6 AgentSystemPrompt 现有 prompt 模板**不能改**

**只追加** 2 段，**不删** v1.0 内容。改坏会导致 10 个 AgentIntegrationTest 测例挂。

### 5.7 Spring AI 1.1 公开 API class 名必查

参考 `docs/reviews/sisyphus-code-review-2026-06-10.md` P0 #5 教训：本项目用 `ChatClient` + `@Tool` 注解，**不是** `ReactAgent`。本模块不涉及 ChatClient 装配，**但** NetworkDictLookupTool 必须用 `@Component` + 实现 `AgentTool` 接口，**不要**用 `@Tool`（那是 Spring AI 原生注解，本项目是自定义 `AgentTool` 接口）。

### 5.8 ProjectFactEvent @PreRemove 抛 RuntimeException

必须是 RuntimeException（不是 checked Exception），否则 Hibernate 不会回滚。`IllegalStateException` 是 RuntimeException 子类，OK。

### 5.9 测试 GLM key 缺失

`mvn test` 默认不调真实 GLM API，全部 mock。`application-test.yml` 加 `spring.ai.agent.enabled=false` 跳过真实调用。

---

## §6 接口契约

### 6.1 给 MOD-04（业务功能）

- `NetworkDictLookupTool` 已在 toolMap 自动注入，MOD-04 不需要改 AgentEngine
- `FailureType` 枚举已暴露，MOD-04 可在 Service 异常处理里用
- `AuditLogService.logToolCall(toolName, args, result, durationMs)` MOD-02 已暴露，AgentEngine 调用每个 tool 后调一次

### 6.2 给 MOD-05（前端集成）

- `/api/qa/ask` 响应体新增可空 `projectSwitchHint` + `confidenceBadge`
- `/api/projects` POST 失败响应体新增 `failureType`（nullable, 5 枚举）
- `Knowledge.vue` 显示这 2 字段（MOD-05 自己改，本模块不提供前端代码）

### 6.3 给 MOD-06（文档/测试）

- AgentIntegrationTest 测例 ≥ 10 条（v1.0 I-11 已有的扩）
- 5 级判定 + 7 重加固 + NetworkDictLookup 都要有 integration test 覆盖

---

*本模块由 Agent 后端 agent 接手。MOD-01 + MOD-02 完工后开工。*