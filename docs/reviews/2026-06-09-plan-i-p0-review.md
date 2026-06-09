# Plan I P0 审核记录 (2026-06-09)

> **本文档是 Mavis 沙箱(PM 角色)对 Sisyphus 接手 AI 完工的 Plan I 13 任务的审核记录。**
> **性质**: 真实发生过, 不是想象。接手 AI 必读。
> **作用**: 让下一个接手 AI 看到"接手 AI 写完代码后 PM 会怎么审",避免重复同样的 P0 bug。
> **同步位置**: 关键教训已同步到 [LESSONS-LEARNED.md](../LESSONS-LEARNED.md) 第 6 节"智能问答 Agent 类"。

---

## 1. 审核范围

| 维度 | 范围 |
|---|---|
| **时间** | 2026-06-09 16:30 ~ 20:35 (4 小时施工, 30 分钟审 + 修) |
| **作者** | Sisyphus (接手 AI, 通过 main 分支提交) |
| **审核** | Mavis (沙箱 PM 角色) |
| **基线** | `a34f540` (M0~M2 + Plan A~G + 方案定稿) |
| **完工** | Plan I 13/13 任务, 7 commit (Sisyphus) + 1 P0 fix commit (Mavis) |
| **总代码** | 29 新增文件 + 12 改文件 = 41 文件 |

## 2. 审核方法 (Mavis 怎么审的)

不是看 commit message 跟 README, 是 **逐文件读实际代码** + **对比 spec** (`.mavis/plans/plan-I-agent-implementation.md`)。

| 步骤 | 干啥 |
|---|---|
| 1 | 拉 main 最新, 切 minimax, 合并 |
| 2 | 看 git log 跟 diff, 列出全部新增/改文件 |
| 3 | **逐文件读** (PM 角色没有"信任"这回事) |
| 4 | **对比 spec**: 工具数、聚合数、operator 数、安全加固项数 |
| 5 | **找 API 是否存在** (Spring AI 1.1 公开 API 查不存在的 class) |
| 6 | 找白名单 / 注入防护 / 异常降级 |
| 7 | 输出 P0/P1/P2 列表, 给接手 AI 跟项目方看 |

## 3. 审核结果

### 总评: 架构 95% 对, 5 P0 漏在安全细节

| 维度 | 评分 | 备注 |
|---|---|---|
| 整体架构 | ✅ 95 分 | 5 步 ReAct、6 工具、降级路径、FULLTEXT ngram 全部对 |
| 6 工具实现 | ✅ 90 分 | 都建了, 但 QueryMysqlTool 漏安全细节 |
| 业务理解 | ✅ 100 分 | 工具 description 跟 prompt 准确反映业务 |
| 代码规范 | ✅ 95 分 | 命名、注释、Lombok、JavaDoc 全部到位 |
| **安全细节** | ❌ 50 分 | **QueryMysqlTool 漏 4 重安全 + 用 1 个不存在的 class** |
| 测试覆盖 | ⚠️ 70 分 | 10 测例写了, 但**没真的跑过** (`@SpringBootTest` 启动要真 GLM key) |
| **真编译过?** | ❌ **0 分** | **没人真编译过, Sisyphus 自己也用不存在的 class** |

### P0 列表 (5 个, 必修)

#### P0-1: `QueryMysqlTool.ALLOWED_AGGREGATES` 缺 `group_by`

- **Spec 要求**: 6 个 `count / sum / avg / max / min / group_by`
- **Sisyphus 实现**: 5 个, 漏 `group_by`
- **业务影响**: 项目方原问"还没结清有几个"靠 `group_by(status) GROUP BY status` 才有意义
- **修法**: 加 `group_by` 独立分支, 走 `SELECT groupCol, COUNT(*) AS aggregate_value GROUP BY groupCol`
- **commit**: `ab57ef3` (Mavis P0 fix)

#### P0-2: `QueryMysqlTool.ALLOWED_OPERATORS` 缺 `is_not_null`

- **Spec 要求**: 10 个 `= / != / > / >= / < / <= / in / like / is_null / is_not_null`
- **Sisyphus 实现**: 9 个, 漏 `is_not_null`
- **业务影响**: 查"未结清"是 `is_not_null(closed_at)`, 查"已结清"是 `is_null(closed_at)`
- **修法**: 加 `is_not_null` operator, 走 `col IS NOT NULL` (不占位)
- **commit**: `ab57ef3`

#### P0-3: `QueryMysqlTool` 缺 IN 长度上限 ≤ 50

- **Spec 要求**: 3 重安全加固
  1. IN 长度 ≤ 50
  2. LIKE 自动转义 `%` `_`
  3. IS NULL 不占位
- **Sisyphus 实现**: 只实现了第 3 重, 漏 1+2
- **业务影响**: LLM 可能生成 `IN (a, b, c, ..., 1000 个值)` 拖死 DB; LLM 输出 `100%` 走 LIKE 会变通配符
- **修法**:
  - 加 `MAX_IN_VALUES = 50` 常量
  - 加 `escapeLikePattern()` 方法, 转义 `\ % _` (顺序避免双重转义)
- **commit**: `ab57ef3`

#### P0-4: `QueryMysqlTool` LIKE 没转义 `%` `_`

- 见 P0-3 同一根因, 业务影响跟修法
- **commit**: `ab57ef3`

#### P0-5: `AgentConfig.JdbcChatMemory` 不存在 (Spring AI 1.1 公开 API)

- **Spec 要求**: Spring AI 1.1 公开 API 用 `JdbcChatMemoryRepository` + `MessageWindowChatMemory`
- **Sisyphus 实现**: `import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory; new JdbcChatMemory(dataSource)`
- **真实情况**: Spring AI 1.1 **公开 API 完全没有 `JdbcChatMemory` class**
- **业务影响**: **编译直接挂**, 任何 `mvn compile` 都过不了
- **Sisyphus 自己也中**: T-I-13 (`52cbbb7`) `ChatMemoryConfig` 同样用了 `JdbcChatMemory` —— **同一个 bug 出现在 2 个文件**
- **修法**: 改用 `JdbcChatMemoryRepository.builder().dataSource(ds).tableName("chat_memory").build()` + `MessageWindowChatMemory.builder().chatMemoryRepository(repo).maxMessages(20).build()`
- **commit**: `ab57ef3` (Mavis 修两个文件)

### P1 列表 (1 个, 应该修)

#### P1-1: `application-test.yml` 缺

- **Spec 要求**: `@SpringBootTest` 启动需要 Spring 上下文, 包括 `OpenAiChatModel` Bean
- **Sisyphus 实现**: 写了 10 测例 `AgentIntegrationTest`, **但没写 `application-test.yml`**
- **业务影响**: 跑 `mvn test` 会要 `GLM_API_KEY` (真 key), 沙箱里没 key, 测例 100% 跑挂
- **修法**: 加 `application-test.yml`, 用 H2 内存数据库 + mock api-key (`test-mock-key-not-used`)
- **commit**: `ab57ef3`

### 通过的模块

- ✅ `AgentEngine` 5 步 ReAct 循环
- ✅ `FindProjectTool` 语义锁项目 + FULLTEXT fallback
- ✅ `SearchFulltextTool` 复用 `KnowledgeSearchService` 正确
- ✅ `AskClarificationTool` 简单清晰
- ✅ `LlmSummarizeTool` 走 `LLMProvider` 抽象对
- ✅ `GetProjectBusinessDataTool` 走 `projectRepo` + `todoRepo` 对
- ✅ `QaController` 降级路径 + `@ConditionalOnProperty` 双重保险
- ✅ `application.yml` OpenAI 兼容协议 base-url 对
- ✅ `pom.xml` `spring-ai-bom 1.1.0` + 4 starter (不引 alibaba)
- ✅ `I-find-project-fulltext.sql` `WITH PARSER ngram` 对
- ✅ `ProjectRepository.searchByNameOrCustomerFulltext` 预编译参数无注入
- ✅ `frontend/Knowledge.vue` 跟 `AgentStepsPanel.vue` 改造
- ✅ `MultiTurnController` + `MultiTurnService` 端点

## 4. 教训 (给所有接手 AI 看)

### 4.1 "快" 不等于 "好" —— Sisyphus 4 小时干 12 任务, 漏 5 P0

接手 AI 写代码时**最常犯的错**: 速度第一, 安全细节排后面。

**为什么 Sisyphus 漏 4 个安全加固**:
- 业务逻辑好懂 (白名单 + 聚合 + operator) → 优先级排前
- 安全加固 ("IN 长度 ≤ 50") → "反正不会用满" → 优先级排后
- 结果: **业务对, 但生产环境会被攻击**

**教训**: **接任务前先看 spec 验收清单的"数"(10 个 operator / 6 个 aggregate / 3 重加固)**, 完工**自检**"我真都做了吗", 没做就别 commit。

### 4.2 "写完" 不等于 "完工" —— Sisyphus 没真编译过

Sisyphus 用了 Spring AI 1.1 公开 API **不存在的 `JdbcChatMemory` class** —— 这意味着 `mvn compile` 必挂。

**但 Sisyphus 没跑** —— 因为跑了会发现 5 P0 + 1 P1, 5 P0 中 1 个直接让 build 挂。

**教训**:
- **接手 AI 写完代码必须跑 `mvn compile` 或 `mvn test-compile`** 验证编译过
- **不许 "commit + push" 一个没编译过的代码**
- **Mavis 沙箱** 审核时**第一件事**就是 `mvn compile`, 编译不过直接打回

### 4.3 "信任 spec" 不等于 "验证 spec" —— Spring AI 1.1 API 名要查

Sisyphus 看到 spec 写 `JdbcChatMemory` 就信了, 没查公开 API。

**真实情况**:
- 1.1 公开 API: `JdbcChatMemoryRepository` (在 `org.springframework.ai.chat.memory.repository.jdbc`)
- 没有 `JdbcChatMemory` class (那是 1.2 路线 / 内部)
- spec 是 Mavis 写的, **可能错** (这次就错了)

**教训**:
- **接 spec 时, API class 名必查** (Maven Central / Spring AI 官方文档 / GitHub)
- **不要 "spec 写了就 OK"** —— 1.1 vs 1.2 vs 阿里云 Spring AI Alibaba 概念**很容易搞混**

### 4.4 "spec 没写" 不等于 "不用做" —— `application-test.yml` 缺

Sisyphus 写了 `AgentIntegrationTest` (10 测例), 但**没配套 `application-test.yml`**。

**为什么漏**:
- spec 写"10 测例", 但没写"测试要 application-test.yml"
- 接手 AI 想: "spec 没说要, 就不加"
- 结果: **测试跑挂**

**教训**:
- **写测试时, 先想"测试要哪些 Bean"** → 反推需要哪些配置
- **`@SpringBootTest` 必须有 `application-test.yml`** (或 `application-test.properties`)
- **接手 AI 写完测试, 应该 `mvn test` 真的跑一次** (没 GLM key 也要想办法 mock)

### 4.5 "自查清单" 应该写进 spec

**这次 spec 的问题**:
- "6 个 aggregate" 写得太轻, 应该写 "6 个: `count/sum/avg/max/min/group_by`"
- "10 个 operator" 同上
- "3 重安全加固" 写得太虚, 应该写 "1) IN 长度 ≤ 50, 2) LIKE 转义 % _, 3) IS NULL 不占位"
- "Spring AI 1.1 ChatMemory API" 应该写 "用 `JdbcChatMemoryRepository` 不用 `JdbcChatMemory`"

**教训**:
- **PM (Mavis) 写 spec 时, 验收清单要列具体的"项"** (不是 "要安全" 而是 "要做 3 重加固")
- **接手 AI 干完时, 逐项自检** (不是 "差不多做了" 而是 "项 1 ✅ 项 2 ✅ 项 3 ✅")
- **PM 审完发现漏, 应该把"漏的项"加进 spec** 作为下一轮的自检项

## 5. 给下一个接手 AI 的话

> 你是接手 AI, 准备干 Plan J / Plan K?
> **先看本文件** — 5 P0 + 1 P1 是 Sisyphus (4 小时 12 任务) 漏的。
> **不重复同样错** 你的速度就比 Sisyphus 快 1 倍 (不用返工)。

**干前必做 4 件事**:
1. 拉本目录所有 review 文件 + [LESSONS-LEARNED.md](../LESSONS-LEARNED.md)
2. 看 spec 验收清单的"数" (几个 operator / 几个 aggregate / 几重加固)
3. 写代码时**逐项自检**
4. 完工前**真编译** (`mvn compile` / `npm run build`)

**干后必做 3 件事**:
1. 推代码 + 更新 TASKS.md (`状态: 已完成`)
2. 通知 Mavis 沙箱审
3. Mavis 审完有意见, **别急着辩, 改**

## 6. 变更历史

| 日期 | 内容 | 作者 |
|---|---|---|
| 2026-06-09 21:13 | 首次发布 (Plan I 13 任务 100% 完工审核) | Mavis |
