# 投委会档案系统 — 架构方案 v2(增补版)

> **基线**: `architecture-v3-final.md`(2026-06-05 决策)
> **触发**: `SUPPLEMENTARY-REQUIREMENTS.md`(P0~P4 缺陷 + 增强)+ 业务需求 v1 方向
> **原则**: **沿用 M0~M2,只增不重构**;接口预留,业务专员最终命名可以 absorb
> **版本**: v2 / 2026-06-08
> **阅读对象**: 开发 Agent / 业务专员 / 架构评审

---

## 0. 一句话总结(本版差异)

在 v3 之上**新增** 3 个抽象层 + 6 个核心模块 + 8 张表,不动 M0~M2 已有代码;补齐 M2 未落地的 `chapter_summary` + v3 文档列出但 init.sql 缺失的 6 张业务表(时点/待办/规则/字典/审计等)。

**沿用 ≈ 70% / 增补 ≈ 30% / 重构 0%**。

---

## 1. 沿用与增补一览

### 1.1 沿用清单(M0~M2 不动 ✅)

| 模块 | 现状 | 处理 |
|---|---|---|
| **后端 实体** | `BaseEntity / User / Role / Project / Proposal / Material / MaterialVersion` | ✅ 全量沿用,字段不重构 |
| **后端 Repository** | 5 个 Spring Data Repository | ✅ 沿用,按需 `@Query` 补方法 |
| **后端 Service** | `AuthService / ProjectService / ProposalService / MaterialService / MaterialVersionService / TikaService / SectionService / KnowledgeSearchService / GlmService` | ✅ 沿用,只允许在内部**加方法**,不改签名 |
| **后端 Controller** | `Auth / Health / Project / Proposal / Material / MaterialVersion / Qa` 7 个 | ✅ 沿用;Qa 暂保留(指向 `chapter_summary`) |
| **后端 common** | `ApiResponse / GlobalExceptionHandler / StorageService` | ✅ 沿用,加 exception handler 即可 |
| **后端 config** | `ConfigJsonLoader / DatabaseProperties / GlmProperties / StorageProperties` | ✅ 沿用,**新增** `LlmProperties`(provider 切换) |
| **后端 security** | `JwtAuthFilter / JwtUtil / SecurityConfig` | ✅ 沿用,只允许追加 antMatchers |
| **前端 views** | `Login / Layout / Dashboard / ProjectList / ProjectForm / ProjectDetail / ProposalDetail / Knowledge` | ✅ 沿用,允许在文件内加 patch |
| **前端 api** | `http / auth / archive` | ✅ 沿用,**新增** `todo.ts / dict.ts / engine.ts` |
| **部署** | WinSW + Caddy + MySQL 8.0.16 + 单机 Windows | ✅ 沿用 |
| **存储** | `D:\archive\files` + `D:\archive\parsed` | ✅ 沿用 |
| **检索** | MySQL FULLTEXT + ngram 解析器 + 不向量化 | ✅ 沿用 |
| **LLM** | 智谱 GLM-4-Flash(摘要/问答)+ GLM-4V(扫描件 OCR) | ✅ 默认沿用,**抽象**成 `LLMProvider` 接口后可换 |
| **Tika** | 文本型 PDF/DOCX/XLSX 解析 | ✅ 沿用 |
| **脱敏** | 证件号(正则)+ 人名(HanLP/jieba NER),其他不动 | ✅ 沿用 |

### 1.2 增补清单(本版新增 ✏️)

| # | 模块 | 用途 | 对应需求 |
|---|---|---|---|
| 1 | `LLMProvider` 接口 + 3 实现 | 抽象 GLM/OpenAI/Mock,`llm.provider` 切换 | SUPP P0 之外、补 v3 抽象缺口 |
| 2 | `ExtractionEngine`(字段抽取,可扩展) | 报告/材料字段抽取(项目名/金额/起投时间/服务商/客户名) | 业务需求 4 阶段 |
| 3 | `TimepointExtractor`(时点抽取) | 文本 → `{截止日期, 事项, 置信度, 引用}`,`extraction_methods` 表可扩展 | 业务需求 5.1 |
| 4 | `TriggerEngine`(触发规则) | 材料分类/状态 → 自动创建待办,`trigger_rule` + `trigger_action` 表配置 | 业务需求 5.3 |
| 5 | `ComparisonEngine`(立项-申请对比) | 立项 vs 申请报告 NLP 验证,`comparison_methods` 表可扩展 | 业务需求 4.2 |
| 6 | `TodoService` + `TodoGenerator` | 3 来源汇聚(自动/手工/规则),首页展示 | 业务需求 5.2 |
| 7 | `ChapterService`(M2 应有未建) | 章节入库 + 知识库检索兜底 | SUPP P2-1 |
| 8 | `AsyncConfig` + `@Async` 解析 | 50MB 文件不阻塞上传线程 | SUPP P2-2 |
| 9 | `LoginRateLimiter` | IP + 账号双重限流 | SUPP P3-1 |
| 10 | `DictService` + `AdminDict.vue` | 参数配置表(项目/材料/议案分类状态)动态管理 | SUPP P3-6 |
| 11 | `AuditLogService` + `audit_log` 表 | 所有写操作 + 登录 + LLM 调用落库 | 业务需求 9 |
| 12 | `ProjectAmountCalculator` | 累计金额公式(`初始 - 付出 + 收回`),事务保护 | 业务需求 5.4 |
| 13 | `MaterialBatchUploadController` | 一次选多个文件统一上传 | SUPP P2-5 |
| 14 | `ProposalAutoSummaryService` | 提案状态变更时自动从材料摘要 | SUPP P2-6 |
| 15 | `useDict(typeCode)` composable(前端) | 字典项加载 + 缓存 | SUPP P3-6 |

### 1.3 重构清单(❌ 本轮不做,留 TODO)

> 原则: **不为了一致性改已有代码**。以下项均打 TODO,本轮**不动**。

| 项 | 原因 | 何时做 |
|---|---|---|
| ❌ 把 Project.amountWan 改成 DECIMAL(18,2) | 现有 BIGINT 万元精度够;改类型涉及全链路 | 等累计金额精确到元且数据规模需要时 |
| ❌ 把 Material.category 外键到 dict_item | 字符串字段灵活,改外键影响既有数据 | 等数据治理时 |
| ❌ QaController 拆 service 层 | 现在 1 个 service 暂可 | 等 Q&A 变复杂 |
| ❌ 引入 Spring Security `@PreAuthorize` | 现在用 antMatchers URL 粒度足够 | 等权限模型细化 |
| ❌ 引入 Spring AI / LangChain4j | 自研 `LLMProvider` 接口够用;不引新依赖 | 等切换多 provider 频繁时 |
| ❌ 加 Redis | 字典缓存 5s TTL 已够;不引新组件 | 等性能成瓶颈 |
| ❌ 拆 micro-service | 单机 + 4 进程足够 | 等用户/数据量翻 10 倍 |
| ❌ 把 init.sql 拆成 Flyway/Liquibase | 手工 SQL 直观;项目 1 个人维护 | 等多环境/多人维护 |

---

## 2. 分层架构(在 v3 之上)

```
┌─────────────────────────────────────────────────────────────────┐
│  Controller 层(沿用 + 新增)                                       │
│  Auth/Health/Project/Proposal/Material/MaterialVersion/Qa(沿用)  │
│  + Todo/Dict/AuditLog/MaterialBatch/Extraction/Trigger(新增)     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Service 层(沿用 + 新增)                                          │
│  Auth/Project/Proposal/Material/MaterialVersion/                  │
│  Tika/Section/KnowledgeSearch/Glm(沿用)                           │
│  + Chapter/Todo/Trigger/Extraction/Comparison/                   │
│    ProjectAmount/ProposalAutoSummary/LoginRateLimiter/           │
│    AuditLog/Dict(新增)                                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Engine 层 ★ 新增(业务规则计算,可配置)                            │
│  TriggerEngine / TimepointExtractor / ExtractionEngine /         │
│  ComparisonEngine                                                 │
│  ↳ 通过 Repository 读 trigger_rule / extraction_method /         │
│     comparison_method / dict_item 配置                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Provider 层 ★ 新增(LLM 抽象)                                     │
│  LLMProvider(接口)                                                │
│    ↳ GLMProvider(默认,智谱 GLM-4-Flash / GLM-4V)                 │
│    ↳ OpenAIProvider(可选,OpenAI / SiliconFlow 兼容)              │
│    ↳ MockProvider(测试用,返回固定结构)                            │
│  切换: app.llm.provider=glm|openai|mock(配 application.yml)       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Repository 层(沿用)                                              │
│  User/Role/Project/Proposal/Material/MaterialVersion(沿用)        │
│  + ChapterSummary/Timepoint/Todo/TriggerRule/                    │
│    ExtractionMethod/ComparisonMethod/DictItem/AuditLog(新增)      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Entity 层(沿用 + 新增 9 张表对应实体)                            │
└─────────────────────────────────────────────────────────────────┘
```

**为什么加 Provider / Engine 两层?**
- Provider 把"用哪家 LLM"隔离掉,业务代码只调 `llmProvider.chat(prompt)`,换模型不动业务
- Engine 把"可配置的规则"集中到 4 个引擎,业务代码不写死 if/else,规则改 DB 不改代码

---

## 3. 核心模块(新增重点)

### 3.1 Provider 层(LLM 抽象)

#### 3.1.1 接口设计

```java
package com.archive.llm;

/**
 * LLM 提供方抽象。所有业务代码只调这个接口,不直接调具体厂商 API。
 * 切换实现: application.yml 设 app.llm.provider=glm|openai|mock
 */
public interface LLMProvider {

    /** 同步对话,返回纯文本(摘要/抽取/重排序都走这个) */
    String chat(String systemPrompt, String userPrompt);

    /** 同步对话,带 JSON Schema 约束(GLM-4-Flash/OpenAI 均支持 response_format) */
    <T> T chatJson(String systemPrompt, String userPrompt, Class<T> type);

    /** 多模态:图片 → 文本(GLM-4V 走这个;MockProvider 直接返回占位) */
    String vision(String prompt, byte[] imageBytes, String mimeType);

    /** 当前 provider 名(用于 audit_log) */
    String name();
}
```

#### 3.1.2 三个实现

| 实现 | 类 | 调用方 | 备注 |
|---|---|---|---|
| `GLMProvider` | `com.archive.llm.GLMProvider` | 智谱 OpenAPI | 默认,沿用 `GlmService` 的 HTTP 调用,包成接口 |
| `OpenAIProvider` | `com.archive.llm.OpenAIProvider` | OpenAI / SiliconFlow / DeepSeek(OpenAI 兼容协议) | 可选,作为备用 |
| `MockProvider` | `com.archive.llm.MockProvider` | 无 | 单测 / dev 环境用,返回固定 JSON |

#### 3.1.3 切换配置

```yaml
# application.yml 增量
app:
  llm:
    provider: glm              # glm | openai | mock
    glm:
      api-key: ${GLM_API_KEY}
      chat-url: https://open.bigmodel.cn/api/paas/v4/chat/completions
      vision-url: https://open.bigmodel.cn/api/paas/v4/vision
      chat-model: glm-4-flash
      vision-model: glm-4v
      timeout-seconds: 60
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1   # 也可指 SiliconFlow/DeepSeek
      chat-model: gpt-4o-mini
      timeout-seconds: 60
```

#### 3.1.4 兼容现有 `GlmService`

`GlmService` 是 v3 写的具体类(直接 HTTP 调智谱)。本版**不删** `GlmService`,而是:
- 新建 `GLMProvider implements LLMProvider`,内部委托给 `GlmService`(或直接搬过来)
- 业务代码(`KnowledgeSearchService` / `ProposalAutoSummaryService` 等)**只**依赖 `LLMProvider`
- 这样: 业务可换,`GlmService` 留作兜底

> 演进路径: 等业务稳定后,可把 `GlmService` 标记 `@Deprecated` 并删掉 HTTP 代码,目前先共存。

#### 3.1.5 异常 & 重试

- `LLMProvider.chat()` 内部 catch IOException → 抛 `LLMCallException`(RuntimeException)
- `GlobalExceptionHandler` 增 handler,返回 HTTP 502(`50200` 错误码,业务侧 502)
- 业务侧**不**重试 LLM 调用(避免重复扣费);失败就 warn log + 走兜底(MockProvider / 跳过 AI 抽取)

---

### 3.2 Engine 层(规则引擎 + AI 抽取)

> 4 个引擎,职责单一,**全部**通过 Repository 读 DB 里的可配置表(规则/方法/字典),改配置不改代码。

#### 3.2.1 `ExtractionEngine`(字段抽取,可扩展)

**职责**: 从报告/材料正文抽业务字段(项目名/初始金额/起投时间/服务商/客户名),调用 `LLMProvider.chatJson()`。

**扩展机制**:
- 表 `extraction_method`(id, code, name, prompt_template, output_schema(JSON), enabled, builtin)
- 默认方法 `DEFAULT_PROJECT_FIELDS`:抽 `{项目名称, 初始总金额, 起投时间, 服务商名称, 客户名称}`
- 用户在管理后台 / 业务方提交新方法 → 入库 → 立即生效
- 多方法并存:同字段多方法结果,UI 提示冲突,人工选

**入口**:
```java
ExtractionEngine.extract(materialVersionId, methodCode /* nullable → 用默认 */)
  → Map<String, Object> fields
```

**降级**:
- LLM 失败 → 返回 `{}` + warn log,业务方弹窗全手填(沿用现状)

#### 3.2.2 `TimepointExtractor`(时点抽取)

**职责**: 文本 → `List<Timepoint>{截止日期, 事项, 置信度, 引用}`。

**复用 `ExtractionEngine`**: 实际就是 `ExtractionEngine` 配一个特殊的 `output_schema = [Timepoint]`,但单独拆类是为了语义清晰 + 单独的兜底。

**入口**:
```java
TimepointExtractor.extract(materialVersionId)
  → 落库 timepoint 表(extracted_by='llm', confidence>=0.6 才落)
```

**降级**:
- LLM 失败 → 跳过本次抽取,人工手填走 Service 的 `TimepointService.create()`

#### 3.2.3 `TriggerEngine`(触发规则)

**职责**: 监听业务事件(材料上传/分类变更/状态变更),按 `trigger_rule` 表条件评估,生成待办或通知。

**事件**:
- `MaterialUploadedEvent`(材料上传成功后 publish)
- `MaterialCategorizedEvent`(AI 分类后 publish)
- `ProposalStatusChangedEvent`(状态机流转时 publish)
- `TimepointApproachingEvent`(@Scheduled 每日 09:00 跑,扫 30/7/1/0 天)

**规则格式**(`trigger_rule` + `trigger_action` 两表):

```json
// trigger_rule 字段
{
  "code": "RECEIPT_AUTO_BOOK",
  "name": "收款凭证入库自动生成走账任务",
  "trigger_event": "MaterialCategorizedEvent",
  "trigger_condition": "event.material.category == '收款凭证'",
  "expression_lang": "aviator",          // 预留,目前先用简单 expression
  "enabled": true,
  "priority": 3
}

// trigger_action 字段(action 与 rule 1:N)
{
  "rule_id": 1,
  "action_type": "create_todo",           // create_todo | send_notification
  "action_template": {
    "todo_name": "走账:${event.material.title}",
    "owner_role": "finance",
    "due_days": 3,
    "priority": 3
  },
  "sort_order": 1
}
```

**评估器 v1(简单)**:
- 表达式语言先不引 Aviator(Drools/Aviator 都是 JDK 上几 MB,但目前需求是 `field == 'value'` 级别)
- 自研 `SimpleExpressionEvaluator` 支持:`==, !=, >, <, &&, ||, in [a,b,c]`,够用
- Aviator 留 TODO(等规则变复杂时)

**入口**:
```java
TriggerEngine.onApplicationEvent(event)
  → 加载 enabled 规则 → 评估 → 执行动作(create_todo / send_notification)
```

**降级**:
- 规则评估抛异常 → catch,error log,**不**阻断业务主流程

#### 3.2.4 `ComparisonEngine`(立项-申请对比)

**职责**: 立项报告 vs 申请报告,**不做**字段 diff(用户决策),只对"待落实问题清单"做 NLP 验证(LLM 问"该问题是否在申请报告里得到解答")。

**复用 `ExtractionEngine`**: 也是配一个特殊 `output_schema` 即可。

**扩展机制**:
- 表 `comparison_method`(同 `extraction_method` 结构)
- 默认方法 `DEFAULT_QA_VERIFY`:对每个待落实问题问 LLM"在申请报告里是否解决"
- 用户可加新方法(如"逐字段对比"、"风险点重评估")

**入口**:
```java
ComparisonEngine.compare(projectId, fromReportId, toReportId, methodCode)
  → List<{问题, 状态(已解决/未解决/部分解决), 引用, 置信度}>
```

---

### 3.3 待办服务(TodoService + TodoGenerator)

#### 3.3.1 3 来源

| 来源 | 写入方 | 标志 |
|---|---|---|
| **自动**(从时点抽) | `TimepointExtractor` 抽取后,`TimepointService` 自动生成 | `todo.source='auto_timepoint'` |
| **手工**(用户加) | `TodoController.create()` 接收表单 | `todo.source='manual'` |
| **触发规则** | `TriggerEngine` 评估后调用 `TodoService.createFromTrigger()` | `todo.source='trigger'` |

#### 3.3.2 数据模型核心字段

```java
class Todo {
  Long id;
  String title;
  String source;            // auto_timepoint | manual | trigger
  String priority;          // low | medium | high | urgent
  String status;            // pending | in_progress | done | cancelled | expired
  Long ownerId;             // 责任人(可空,空=所有人均可见)
  Long projectId;           // 关联项目
  Long timepointId;         // 来源(自动/触发可回链)
  Long triggerRuleId;
  LocalDateTime dueAt;
  LocalDateTime completedAt;
  String remark;
  // 审计字段
}
```

#### 3.3.3 首页展示

- 临近待办(dueAt 在 [now, now+7d]):橙色 chip
- 已过期(dueAt < now 且 status != done):红色 chip + 置顶
- 统计:`/api/todos/summary?owner=me` → `{pending, expiringSoon, overdue, doneThisWeek}`

#### 3.3.4 自动转过期

- `@Scheduled` 每日 00:30 跑:`UPDATE todo SET status='expired' WHERE dueAt < now() AND status='pending'`
- 单独跑,**不**混入业务事务

---

### 3.4 累计金额(`ProjectAmountCalculator`)

#### 3.4.1 公式

```
remaining_amount = initial_amount - Σ(payout_amount) + Σ(receive_amount)
```

- 单位: **元**(DECIMAL(18,2)),不在前端展示时再 ÷10000 转万元
- 字段:
  - `project.initial_amount DECIMAL(18,2)` — 立项时从 LLM 抽取 / 手工填
  - `material.amount DECIMAL(18,2)` — 仅当 `material.category in ('付款凭证','收款凭证')` 才纳入计算
  - `project.remaining_amount DECIMAL(18,2)` — **冗余字段**,service 层每次写完自动算

#### 3.4.2 实现

```java
@Service
public class ProjectAmountCalculator {
    @Transactional
    public BigDecimal recalc(Long projectId) {
        Project p = projectRepo.findById(projectId).orElseThrow(...);
        // 累加:SELECT SUM(amount) FROM material WHERE project_id=? AND category='付款凭证' ...
        BigDecimal payouts = materialRepo.sumAmountByProjectAndCategory(projectId, "付款凭证");
        BigDecimal receives = materialRepo.sumAmountByProjectAndCategory(projectId, "收款凭证");
        BigDecimal remaining = p.getInitialAmount().subtract(payouts).add(receives);
        p.setRemainingAmount(remaining);
        // 不显式 save,JPA dirty checking 会 flush
        return remaining;
    }
}
```

#### 3.4.3 触发点

- `MaterialService.createOrUpdate()`:若 category ∈ 收/付款凭证,事务内调用 `recalc(projectId)`
- `MaterialService.delete()`:同上
- 项目详情 GET 时,若 `remainingAmount IS NULL` 主动算一次(冷数据兜底)

#### 3.4.4 审计

- `audit_log.action='recalc_amount'` 记录:operator, projectId, oldValue, newValue, txId

---

## 4. 数据库设计(详见 `DB-SCHEMA-v2.md`)

> 这里只放高层一览,SQL 全文在 `DB-SCHEMA-v2.md`。

### 4.1 沿用表(6 张,init.sql 已建)

- `user / role / project / proposal / material / material_version`

### 4.2 新增表(8 张)

| # | 表名 | 用途 | 备注 |
|---|---|---|---|
| 1 | `chapter_summary` | 章节切分 + 摘要 + FULLTEXT 检索 | v3 文档有,init.sql 缺 |
| 2 | `timepoint` | 时点(到期/审议/披露/付款/工商变更) | v3 文档有,init.sql 缺 |
| 3 | `todo` | 待办(3 来源汇聚) | v3 文档里叫 `task`,本版改名 `todo` |
| 4 | `trigger_rule` | 触发规则主表 | v3 文档叫 `rule`,本版加 `_rule` 后缀以避免和 rule 库冲突 |
| 5 | `trigger_action` | 规则动作(1:N) | v3 文档把 action 塞 JSON,本版独立表 |
| 6 | `extraction_method` | 字段抽取方法(用户可加) | **业务需求 6 明确要求** |
| 7 | `comparison_method` | 立项-申请对比方法(用户可加) | **业务需求 5.5 明确要求** |
| 8 | `dict_type / dict_item` | 参数配置表 | SUPP P3-6 明确要求 |
| 9 | `audit_log` | 审计 | 业务需求 9 明确要求 |

> 实际新增 **10 张表**(1+1+1+1+1+1+1+2+1)。**v3 文档里 v3 列 11 张表,init.sql 只有 6 张**——这 10 张补的正好覆盖 v3 缺的部分 + 业务新需求。

### 4.3 ALTER TABLE 改动(3 处)

- `material_version` 加 `parsed_text LONGTEXT`(init.sql 已有,`MaterialVersion.java` 实体已有,但 P0-2 还要加 FULLTEXT 索引,详见 DB-SCHEMA §3)
- `project` 加 `initial_amount DECIMAL(18,2)` + `remaining_amount DECIMAL(18,2)` + `archive_status VARCHAR(16) DEFAULT '在档'`
- `user` 加 `failed_login_count INT DEFAULT 0` + `lockout_until DATETIME`(P3-1 登录限流用)

### 4.4 索引策略

- **全文**: `material_version.parsed_text` + `chapter_summary(content,summary)`(P0-2 + M2)+ `material.title`(备选)
- **业务**: 外键 / 状态 / 时间三件套,沿用 v3 风格
- **唯一**: `(material_id, version_no)`、`(type_code, item_key)`、`(rule_code)`、`(method_code)`

---

## 5. API 端点设计

> 沿用端点**不**列出详细参数,只标"沿用 + 用途"。新增端点给出 method + path + 用途。

### 5.1 沿用(15 个,M0~M2 已实现)

| Method | Path | 用途 |
|---|---|---|
| `POST` | `/api/auth/login` | 登录 |
| `GET`  | `/api/auth/me` | 当前用户 |
| `GET/POST/PUT/DELETE` | `/api/projects[/{id}]` | 项目 CRUD(沿用) |
| `GET/POST/PUT/DELETE` | `/api/proposals[/{id}]` | 议案 CRUD(沿用) |
| `GET/POST/PUT/DELETE` | `/api/materials[/{id}]` | 材料 CRUD(沿用) |
| `GET/POST/PUT/DELETE` | `/api/materials/{mid}/versions[/{vid}]` | 版本 CRUD(沿用) |
| `GET`  | `/api/materials/{mid}/versions/{vid}/download` | 下载 |
| `POST` | `/api/materials/{mid}/versions/{vid}/reparse` | 重解析 |
| `GET`  | `/api/materials/{mid}/versions/{vid}/sections` | 章节 |
| `POST` | `/api/qa/ask` | 知识库问答(M2 沿用) |
| `GET`  | `/api/health` | 健康检查 |

### 5.2 新增(本版 v2,~22 个)

#### 待办(7 个)
| Method | Path | 用途 |
|---|---|---|
| `GET`    | `/api/todos?owner=me&status=&page=&size=` | 我的待办列表 |
| `GET`    | `/api/todos/{id}` | 详情 |
| `POST`   | `/api/todos` | 手工新建 |
| `PUT`    | `/api/todos/{id}` | 编辑 |
| `POST`   | `/api/todos/{id}/complete` | 标记完成 |
| `DELETE` | `/api/todos/{id}` | 删除(仅 manual 来源) |
| `GET`    | `/api/todos/summary?owner=me` | 首页统计(pending/overdue/expiringSoon) |

#### 时点(5 个)
| Method | Path | 用途 |
|---|---|---|
| `GET`    | `/api/timepoints?projectId=&from=&to=` | 项目时点列表 |
| `POST`   | `/api/timepoints` | 手工新建 |
| `PUT`    | `/api/timepoints/{id}` | 编辑 |
| `POST`   | `/api/timepoints/{id}/resolve` | 标记已处理 |
| `POST`   | `/api/projects/{id}/timepoints/extract` | 触发 LLM 抽取 |

#### 触发规则(6 个)
| Method | Path | 用途 |
|---|---|---|
| `GET`    | `/api/triggers/rules` | 规则列表 |
| `POST`   | `/api/triggers/rules` | 新建规则(admin) |
| `PUT`    | `/api/triggers/rules/{id}` | 编辑 |
| `DELETE` | `/api/triggers/rules/{id}` | 删除 |
| `POST`   | `/api/triggers/rules/{id}/test` | 用最近 10 条事件试跑 |
| `GET`    | `/api/triggers/rules/{id}/actions` | 规则动作列表 |

#### 抽取/对比方法(2 个)
| Method | Path | 用途 |
|---|---|---|
| `GET/POST/PUT/DELETE` | `/api/admin/extraction-methods[/{id}]` | 抽取方法 CRUD(admin) |
| `GET/POST/PUT/DELETE` | `/api/admin/comparison-methods[/{id}]` | 对比方法 CRUD(admin) |

#### 字典(2 个)
| Method | Path | 用途 |
|---|---|---|
| `GET`  | `/api/dict/options?typeCode=xxx` | 公开,前端下拉框 |
| `GET/POST/PUT/DELETE` | `/api/admin/dict/items[/{id}]` | 字典项 CRUD(admin) |

#### 审计(1 个)
| Method | Path | 用途 |
|---|---|---|
| `GET`  | `/api/audit-logs?actor=&action=&from=&to=&page=&size=` | 审计日志查询(admin) |

#### 累计金额(1 个)
| Method | Path | 用途 |
|---|---|---|
| `GET`  | `/api/projects/{id}/amount` | 实时计算 remaining(供详情页显示) |

#### 批量上传(1 个,SUPP P2-5)
| Method | Path | 用途 |
|---|---|---|
| `POST` | `/api/proposals/{pid}/materials/batch-upload` | 多文件批量上传 |

#### 自动摘要(1 个,SUPP P2-6)
| Method | Path | 用途 |
|---|---|---|
| `POST` | `/api/proposals/{id}/auto-summary` | 重新触发 LLM 摘要 |

### 5.3 弃用(0 个)

> 本版**不弃用**任何端点,保持向后兼容。

---

## 6. 前端架构

### 6.1 沿用

- 路由 / Pinia / axios / Element Plus / 主题色 `#1f4e79` / 中文字体 / 浅色 + 深色侧边栏
- 所有 view 文件: 沿用,允许内联 patch(如 SUPP P0-1 解包错误修复)

### 6.2 增补

| 类型 | 文件 | 说明 |
|---|---|---|
| 视图 | `views/Dashboard.vue` | **重写**首页(原本是占位):待办概览 + 临近时点 + 过期红牌 + 最近项目 |
| 视图 | `views/TodoList.vue` | 待办列表(全量 + 我的 + 过期筛选) |
| 视图 | `views/TodoSettings.vue` | 待办设置(批量改 owner/优先级) |
| 视图 | `views/AdminDict.vue` | 参数管理(admin) |
| 视图 | `views/AdminExtractionMethod.vue` | 字段抽取方法管理(admin) |
| 视图 | `views/AdminComparisonMethod.vue` | 对比方法管理(admin) |
| 视图 | `views/AdminTriggerRule.vue` | 触发规则编辑(admin) |
| 视图 | `views/AdminAuditLog.vue` | 审计日志查询(admin) |
| 视图 | `views/ProjectAmount.vue` | 累计金额明细(项目下 tab) |
| 视图 | `views/TimepointList.vue` | 时点列表(项目下 tab) |
| 视图 | `views/ProposalCompareResult.vue` | 立项-申请对比结果 |
| API  | `api/todo.ts` | 待办 API |
| API  | `api/dict.ts` | 字典 API |
| API  | `api/engine.ts` | 抽取/对比/触发 API |
| API  | `api/timepoint.ts` | 时点 API |
| API  | `api/audit.ts` | 审计 API |
| Composable | `composables/useDict.ts` | 字典项加载 + 内存缓存(5min TTL) |
| Composable | `composables/useTodoSummary.ts` | 首页待办统计 |
| 路由 | `router/index.ts` | 加 `/todos /admin/dict /admin/extraction /admin/comparison /admin/triggers /admin/audit` |

### 6.3 主题与 UX

- 沿用 `#1f4e79` 主色
- 待办 chip: 临近=orange(浅),过期=red(深),完成=gray
- 列表默认按 `dueAt ASC, priority DESC` 排序

---

## 7. 部署(沿用,新增 0)

- 进程组: 4 个(Spring Boot JAR / Caddy / MySQL / WinSW 管家)
- 目录结构: 沿用 v3 §8.1
- 配置文件: `application.yml` 增加 `app.llm.provider` 节点
- Caddyfile: 沿用 v3,允许按 P1-3 加 HTTP 80 端口限流
- 备份脚本: 沿用,新增无
- 健康检查: 沿用 `/api/health`,扩 `/api/health/llm`(GET 时 ping 下 provider,可选)

---

## 8. 测试

### 8.1 单元测试(JUnit 5 + Mockito)

| 类 | 覆盖方法 |
|---|---|
| `ProjectAmountCalculator` | recalc()(空 / 纯收入 / 纯支出 / 混合) |
| `TriggerEngine` | 事件评估命中 / 不命中 / 异常降级 |
| `TimepointExtractor` | LLM 成功 / 失败 / 部分成功 |
| `TodoService` | 3 来源去重(同一时点已生成过则跳过) |
| `LLMProvider.Mock` | 固定返回 + 延迟 |

### 8.2 集成测试(`@SpringBootTest` + TestContainers 或本地 MySQL)

- `AuthServiceTest`: 登录成功 / 失败 / 锁定(P3-1)
- `ProjectServiceTest`: 创建 / 状态流转校验
- `MaterialVersionServiceTest`: 上传 / SHA-256 去重 / 重解析
- `TodoServiceTest`: 手工新建 / 完成 / 过期扫描
- `TriggerEngineIT`: 上传收款凭证 → 自动生成待办(整链路)

### 8.3 端到端(手测 / 提单前必跑)

- **4 阶段流程**: 立项 → 申请 → 贷后(1 次)→ 结清
- **待办链路**: 时点抽取 → 待办生成 → 邮件提醒 → 标记完成
- **触发规则**: 配 1 条规则 → 上传对应分类材料 → 验证待办自动出现
- **LLM 切换**: 改 `app.llm.provider=mock` → 验证功能不挂,只是 LLM 调用返固定

---

## 9. 风险与决策记录

| 决策 | 选择 | 备选 | 理由 |
|---|---|---|---|
| LLM 抽象 | 自研 `LLMProvider` 接口 | Spring AI / LangChain4j | 单人项目,新依赖 = 新升级负担,接口够用 |
| 触发表达式 | 自研 `SimpleExpressionEvaluator` | Aviator | 当前需求是 `==/in` 级别,Aviator 留 TODO |
| 累计金额单位 | 元(DECIMAL(18,2)) | 万元(BIGINT) | 累计涉及加减,DECIMAL 必要 |
| 待办表名 | `todo` | `task` | 业务需求用 "待办",`task` 留 SQL 关键字风险 |
| 触发规则表名 | `trigger_rule` + `trigger_action` | `rule` + JSON | 业务多变,JSON 难查;拆 2 表更易扩展 |
| 抽取方法表 | `extraction_method` | JSON 配置 | 用户可加,要持久化 + 列表 + 编辑,必须独立表 |
| 字典表 | `dict_type` + `dict_item` | 单一表 | SUPP P3-6 明确指定 |
| 审计表 | `audit_log`(独立表) | 业务表内嵌 | 跨业务查询,独立表更合理 |
| Provider 切换方式 | `app.llm.provider` 节点 | 动态注册 | 单进程 + 配置切换够用,不用 SPI |
| 待办过期扫描 | `@Scheduled` 每日 00:30 | 实时计算 | 数据量小,扫描一次就够,实时算反而费 CPU |

---

## 10. 验收标准(架构层面)

- [ ] init.sql 执行后,16 张表全在(INFORMATION_SCHEMA 查)
- [ ] 改 `app.llm.provider=mock`,重启后 `/api/qa/ask` 仍能返回(Mock 答案)
- [ ] 上传 1 份立项报告 → `chapter_summary` 表有行 / `timepoint` 表有行(若 LLM 抽到) / `todo` 表有行(若有时点)
- [ ] 配 1 条触发规则,上传对应分类材料 → `todo` 表自动有 1 行
- [ ] 立项 1 个项目,初始金额 1000 万 → 上传 1 张付款凭证 200 万 → 项目详情 remainingAmount = 800 万
- [ ] admin 登录 → 侧边栏看到"参数管理" → 改 1 个字典项 → 业务页面下拉框立即变化
- [ ] `audit_log` 表里能找到刚才所有写操作(actor + action + entity_id + old/new JSON)

---

## 附录 A: 模块清单(速查)

**新增 9 个 Service / 4 个 Engine / 3 个 Provider / 1 个 Calculator / 9 个 Controller / 9 个前端 View**

**后端新增包**:
- `com.archive.llm` — LLMProvider / GLMProvider / OpenAIProvider / MockProvider / LLMCallException
- `com.archive.engine` — ExtractionEngine / TimepointExtractor / TriggerEngine / ComparisonEngine / SimpleExpressionEvaluator
- `com.archive.service.todo` — TodoService / TodoGenerator
- `com.archive.service.timepoint` — TimepointService
- `com.archive.service.trigger` — TriggerService
- `com.archive.service.dict` — DictService
- `com.archive.service.extraction` — ExtractionMethodService
- `com.archive.service.comparison` — ComparisonMethodService
- `com.archive.service.audit` — AuditLogService
- `com.archive.service.amount` — ProjectAmountCalculator
- `com.archive.security` — LoginRateLimiter(追加)

**新增 10 个 Entity**:
- `ChapterSummary / Timepoint / Todo / TriggerRule / TriggerAction / ExtractionMethod / ComparisonMethod / DictType / DictItem / AuditLog`

**新增 10 个 Repository**: 一一对应

**新增 9 个 Controller**:
- `TodoController / TimepointController / TriggerRuleController / ExtractionMethodController / ComparisonMethodController / DictController / AuditLogController / MaterialBatchUploadController / ProposalAutoSummaryController`

---

## 附录 B: 接口预留(给业务专员的余地)

> 业务专员最终命名可能跟本版不同,以下位置**留出**可改空间:

| 预留点 | 当前命名 | 改起来成本 |
|---|---|---|
| 待办表名 | `todo` | 改 1 张表 + 4 处代码,30 分钟 |
| 触发规则表名 | `trigger_rule` | 改 1 张表 + 5 处代码,30 分钟 |
| 时点表名 | `timepoint` | 改 1 张表 + 4 处代码,30 分钟 |
| 4 阶段 | 字段化(用 `project.stage` 字符串) | 状态字段加 ENUM 注释,不改表 |
| 角色 | 沿用 v3 的 4 角色 | 改 role 表 permissions 字段,30 分钟 |
| 抽取方法 | 通用(不绑定项目/材料/议案) | 通过 `apply_to` 字段留扩展 |

> 总结: **命名改得起,字段加得起,接口不动**。

---

*文档作者: 架构师 架构设计 Agent*
*版本: v2 / 2026-06-08 + v1.1 增量 2026-06-11*
*配套文档: `DB-SCHEMA-v2.md`*

---

## 附录 B: v1.1 增量摘要 (MOD-01~06, 基线 7aa7bae)

| 维度 | v1.0 | v1.1 | 说明 |
|---|---|---|---|
| Controller | 13 | **18** | +看板/通知/回收站/导入/失败日志 |
| Service | 17 | **29** | +导出/预览/脱敏/RBAC/网络字典等 |
| AgentTool | 6 | **7** | +network_dict_lookup |
| Entity/表 | 18 | **30** | +7 新表 + 7 ALTER |
| 前端 View | 13 | **18** | +看板/导入/回收站等 |
| 集成测例 | 10 (AgentIntegrationTest) | **45+** (V11IntegrationTest) | MOD-06 收口 |

**零回归**: v1.0 全部 API / 页面行为保留; Agent 降级路径 (spring.ai.agent.enabled=false) 仍可用.
**详细 RI**: `docs/requirements/ARCH-DECOMPOSITION.md` §四 RI-46~69.
