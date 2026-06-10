# 投委会档案管理系统 — 代码评审报告

> 评审人：Sisyphus | 日期：2026-06-10 | 评审范围：全部后端 + 前端代码

---

## 0. 总评

这是一个**实用主义驱动**的项目，整体可用，但充满了"先跑起来再说"的痕迹。代码质量分布极不均匀——核心流程（认证、CRUD）写得还算规矩，但边缘逻辑和近期新增的 Agent 部分明显有赶工迹象。下面按严重程度从高到低讲。

---

## 1. 安全问题（最该骂的部分）

### 1.1 `QueryMysqlTool` — 白名单看起来安全，但整个设计就是错的

```java
// QueryMysqlTool.java — 工具接受用户输入动态拼 SQL
// 虽然列了白名单，但你把 SQL 构建能力直接暴露给 LLM，
// LLM 是可以被 prompt injection 操纵的
```

白名单 + 参数化 SQL 的方向对，但有几个问题：
- 白名单枚举在代码里手写，更新实体要改代码
- 聚合查询的 `GROUP BY` 字段没有单独校验
- `LIMIT` 上限只靠 LLM 自觉，没有硬编码上限

### 1.2 `SecurityConfig.java` — CSRF 全关

```java
.csrf(csrf -> csrf.disable())
```

Spring Security 的 CSRF 全关，虽然解释了"前后端分离用 token 认证不需要 CSRF"，但 JWT 存在 localStorage 意味着 XSS 即可完全仿冒用户。如果前端有任意 XSS 漏洞，攻击者可以直接拿 token 做任何操作。

### 1.3 静态文件遍历防护

```java
// StorageService.java
String canonicalPath = new File(root, relativePath).getCanonicalPath();
if (!canonicalPath.startsWith(root)) { throw ... }
```

路径遍历防护写得对，但只防了 `../`，没防符号链接攻击。Windows 下 junction point / symlink 可以绕过这个检查。

### 1.4 敏感信息记录

部分 catch block 直接把异常信息拼到日志里，如果 API key 错误信息从智谱返回时带了敏感数据，会直接写入日志文件。

---

## 2. 架构问题

### 2.1 `GlmService` 和 `GLMChatModel` 的诡异关系

```java
// GlmService.java — 手写 HTTP 客户端调智谱 API
// GLMChatModel.java — 实现 Spring AI ChatModel 接口，又调回 GlmService
```

这两层根本没有解耦。`GLMChatModel` 对 `GlmService` 是直接依赖，不是桥接，是**套娃**。Spring AI 的 `ChatModel` 接口和自有的 `LLMProvider` 接口功能高度重叠，但代码里两套并存：

```
AgentEngine → Spring AI ChatClient → GLMChatModel → GlmService → 智谱 HTTP
```
```
ExtractionEngine → LLMProviderFactory → GLMProvider → 智谱 HTTP
```

同一个智谱 API，两条调用链路，两套埋点逻辑。如果有一天要换 LLM 供应商，要改两个地方。

### 2.2 Engine 层的事务边界模糊

```java
// MaterialVersionService.triggerAfterParse()
// → ExtractionEngine.extract() // @Async, 新事务
//   → TimepointExtractor.extractTimepoints() // @Async, 新事务
//     → TriggerEngine.evaluate() // @EventListener, 新事务
```

异步调用链涉及 4 个独立事务，中间任何一步失败，前面成功的不会回滚。这不是 bug 是设计，但没有任何文档说明这个"最终一致性"假设。以后如果有人给这些方法加 `@Transactional`，会踩大坑。

### 2.3 `AgentEngine` 的依赖注入方式

```java
public AgentEngine(ChatClient.Builder builder, ...)
```

`ChatClient.Builder` 是 Spring AI 的构建器，每次注入都拿到默认配置的 builder。但 `AgentEngine` 在构造器里 `builder.defaultSystem(systemPrompt.render(null)).defaultTools(...).build()`，这意味着：

- `ChatClient` 在构造时就固定了 system prompt 和 tools
- 每次 `run()` 调用时无法动态修改 system prompt（当前的实现是 `buildPrompt()` 拼字符串再作为 user message 传入，system prompt 反而是空的）
- system prompt 里包含了 tools 描述，但 tools 是构造时传入的，两者可能不同步

### 2.4 前端组件化程度低

所有页面都是"一个文件一个页面"的巨石组件。比如 `ProposalDetail.vue` 里混在一起的有：
- 议案信息展示
- 材料列表 + CRUD
- 版本管理 dialog
- 章节查看 dialog
- 批量上传
- 摘要重生成

没有拆成任何子组件（除了新加的 AgentStepsPanel），导致单个文件很难维护。

---

## 3. 代码质量问题

### 3.1 异常处理太粗

```java
// QaController.java — Agent 降级
try {
    AgentResponse ar = agentEngine.run(agentReq);
} catch (Exception e) {
    log.warn("Agent 失败,降级到老路径", e);
    // 不返 500,降级走老路径
}
```

`catch (Exception e)` 在关键路径上出现了至少 3 次。这是懒。`AgentException`、`LLMTimeoutException`、`ToolExecutionException` 应该分开处理，而不是一棒子打死。

### 3.2 LLM 输出解析是字符串硬刚

```java
// AgentEngine.java
private String extractJson(String text) {
    // 先试直接解析
    if (text.startsWith("{")) return text;
    // 试 ```json ... ```
    int start = text.indexOf("```json");
    // 试 ``` ... ```
    // 试找第一个 { 到最后一个 }
    int braceStart = text.indexOf('{');
    int braceEnd = text.lastIndexOf('}');
}
```

LLM 输出解析是手写字符串搜索，没有用结构化输出（比如 JSON mode / function calling）。一旦 GLM 返回格式稍有变化（比如嵌套了 markdown、JSON 里含转义字符），这个解析就会崩。

### 3.3 注释过度和注释不足并存

```java
/** 用户原问题. */
private String question;
/** LLM 生成的答案(可能为空,表示跳过了 LLM 阶段). */
private String answer;
```

getter/setter 和 trivial 字段上的注释完全多余（`@Data` 已生成，字段名自解释）。但关键的地方（`TriggerEngine.SimpleExpressionEvaluator` 的表达式解析逻辑、`QueryMysqlTool` 的白名单构造过程、`FindProjectTool` 的 4 级兜底策略选择算法）反而没有注释。

### 3.4 魔法字符串

```java
// LlmScenario.java
EXTRACTION, TIMEPOINT, COMPARE, QA, RERANK, SUMMARY, PROJECT_MATCH
```

枚举定义完整，但使用处：

```java
// GlmService.callWithLog()
llmLog.setScenario("QA"); // 硬编码字符串
```

有些地方用枚举，有些地方写字符串，不一致。

### 3.5 `if` 嵌套过深

```java
// KnowledgeSearchService.java
if (results != null && !results.isEmpty()) {
    for (...) {
        if (...) {
            if (...) {
                // 4 层缩进
            }
        }
    }
}
```

层级较深的地方不少，可以用 early return / stream / 提取方法压平。

---

## 4. 测试问题

### 4.1 测试覆盖率虚高

`AgentIntegrationTest.java` 有 10 个测试用例，但所有测试都是：

```java
AgentResponse resp = agentEngine.run(req);
assertNotNull(resp);
assertNotNull(resp.getAnswer());
```

没有 mock ChatClient 的真实返回，测试跑的是真实 LLM 调用？还是 `@MockBean` 后返回 null 然后测引擎的兜底路径？从代码看，`@MockBean private GlmService glmService` 只 mock 了老路径的 GlmService，但 AgentEngine 用的是 GLMChatModel，后者在构造时注入的不是 mock 的 `glmService`。

**这些测试要么跑不过，要么跑的是降级路径而不是 Agent 路径。**

### 4.2 缺少单元测试

核心逻辑几乎没有单元测试：
- `AgentEngine.parseAgentStep()` — 解析 LLM 输出的最核心函数，0 测试
- `AgentEngine.dispatchTool()` — 工具分发逻辑，0 测试
- `QueryMysqlTool` 的白名单校验逻辑 — 0 测试（那个 `QueryMysqlToolTest.java` 被我看到了，测的是端到端，不是白名单边界）
- `FindProjectTool` 的 4 级兜底选择 — 0 测试

### 4.3 工具测试依赖真实数据库

所有 `*ToolTest.java` 都依赖真实 MySQL。不能本地 `mvn test` 就跑，要等 CI 环境。应该加 H2 内存数据库的测试 profile。

---

## 5. 前端问题

### 5.1 类型定义重复

```typescript
// Knowledge.vue
interface AgentStep {
  iteration: number
  thought: string
  tool: string
  toolArgs: string
  observation: string
}
```

`AgentStep` 接口在 `AgentStepsPanel.vue` 和 `Knowledge.vue` 里各定义了一次，没有抽取到共享的类型文件。以后改了后端字段，前端要同步改两个地方。

### 5.2 错误处理不完整

```typescript
// Knowledge.vue onAsk()
} catch (e: any) {
    ElMessage.error(e.message || '问答失败')
}
```

`catch (e: any)` 的 `e.message` 在 Axios 错误时可能是 `undefined`，会显示 "undefined"。而且 Agent 模式 120s timeout 时没有单独的提示。

### 5.3 `archive.ts` 是上帝 API 文件

49 个 API 端点全部在 `api/archive.ts` 一个文件里。现在 500 行还行，但如果继续加，这个文件会膨胀到不可维护。应该按模块拆（projects.ts / proposals.ts / dict.ts / triggers.ts / ...）。

### 5.4 没有加载状态组件

`el-table` 的 `v-loading` 只在少数页面用了，多数列表页（AdminDict、AdminExtraction 等）没有骨架屏或 loading 指示器。

---

## 6. 配置和构建问题

### 6.1 `config.json` 没有 schema 校验

`ConfigJsonLoader` 用了一堆 `null` 检查和 `try-catch` 来加载配置，但缺少正式的 JSON Schema 校验。字段名拼错了不会报错，只会默默地用 null。

### 6.2 前端构建产物硬编码路径

Caddyfile 里写死了 `D:\archive\apps\frontend\dist`，Windows 路径风格。如果要迁移到别的机器或 Linux（Caddy 本来就是 Linux 出生的），路径要全改。

---

## 7. 正面清单（不能只骂不夸）

公平起见，说几句好的：

| 好的地方 | 理由 |
|---------|------|
| 分层清晰 | Controller→Service→Repository 标准三层，团队新人容易上手 |
| 降级路径意识强 | QaController 双路径、GlmService fallback、spring.ai.agent.enabled=false |
| 安全基线不低 | JWT + BCrypt + 白名单 SQL + 限流 + Audit Log，中小企业需求够了 |
| 文档齐全 | 8 份 doc + README + 架构文档，相比很多项目算优秀的 |
| 部署自动化 | startup.ps1 + WinSW + Caddy，单机部署半小时搞定 |
| Agent 实现务实 | 没有强行上 ReactAgent，手写 5 步循环控制力更好 |
| FULLTEXT 决策正确 | 不引入 ES，降低运维负担 |

---

## 8. 改进优先级

| 优先级 | 改进项 | 影响 |
|--------|--------|------|
| P0 | 修复 AgentIntegrationTest 测试（当前大概率跑不过） | 质量门禁 |
| P0 | AgentEngine 支持 JSON mode / function calling | LLM 输出可靠性 |
| P1 | GlmService 和 GLMChatModel 合并 LLM 调用链路 | 可维护性 |
| P1 | 前端类型定义提取到 shared/types.ts | 可维护性 |
| P1 | 单元测试补上（parseAgentStep、dispatchTool、whitelist） | 质量 |
| P2 | 组件化 ProposalDetail.vue | 可维护性 |
| P2 | archive.ts 拆成多文件 | 可维护性 |
| P2 | 异常精细化处理 | 健壮性 |
| P3 | config.json JSON Schema 校验 | 配置安全 |
| P3 | 注释清理（删多余的，补缺的） | 可读性 |

---

*评审完。*

*评审人：Sisyphus*
*日期：2026-06-10*
*立场：尖刻但诚实。这个项目可以跑、可以交付、可以给用户用。但如果想做得更好，上面列的问题迟早要面对。*
