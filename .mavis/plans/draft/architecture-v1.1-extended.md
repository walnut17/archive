# 投委会档案管理系统 — v1.1 增量架构扩展

> 撰写人：架构师 | 日期：2026-06-11 | 版本：v1.1（基于 `0c6325f` 基线 + Sisyphus 分章架构 v1.0 + Mavis §13 拓展）
> 上游输入：`docs/requirements/REQUIREMENTS.md` §13（24 条 RI 拆解）+ RI-22~45 + `docs/architecture/01~06-arch-*.md`
> 下游输出：T2 重构维修清单（`refactor-and-fix-list.md`）+ T3 v1.1 并行任务表（`tasks-v1.1.md`）
> **零回归原则**：v1.0 任何 .java / .vue / .sql / pom 行为不破坏；仅在原位置 ALTER 增列 / 加 Controller-Service-Repository / 加 View-Component / 加 AgentTool / 加迁移脚本

---

## §0. 执行摘要

v1.1 增量（24 条 RI，对应 `REQUIREMENTS.md` §13.1×10 + §13.2×6 + §13.3×8）对现有架构的影响面集中在 **5 大新增模块 + 6 处既有表/视图/包改造**：

1. **5 大新增模块**：项目看板（§13.3.1，RI-38）+ 站内通知中心（§13.3.2，RI-39）+ 数据导出 PDF/Excel（§13.3.3，RI-40）+ 附件预览（§13.3.4，RI-41）+ 关键事实变更对比视图（§13.3.5，RI-42）+ 旧系统 Excel 导入接口（§13.3.7，RI-44）。
2. **既有表 ALTER**：`audit_log` 增 `type` 字段（5 种枚举，§13.2.4，RI-35） + `project_fact_event` 增 `owner_id` / `due_date` / `resolved_at` / `resolution_note`（§13.1.7，RI-28） + `proposal` 增 `condition_text` / `condition_status` / `condition_met_at`（§13.1.3，RI-24） + `business_term` 增 `english_name`（§13.3.6，RI-43） + `user` 增 `sensitive_view_enabled`（§13.3.8，RI-45） + `project/proposal/material` 增 `version` 乐观锁（§13.2.2，RI-33） + `project/proposal/material` 增 `status=deleted` / `deleted_at` / `deleted_by`（§13.1.10，RI-31）。
3. **新增表**：`notification`（§13.3.2，RI-39） + `failure_log`（§13.2.6，RI-37） + 5 角色扩展到 `role`（§13.2.3，RI-34） + `project_member`（项目级 RBAC，§13.2.3，RI-34） + `user_role`（多对多，§13.2.3，RI-34）。
4. **既有 Controller 改造**：`QaController`（隐式切换 5 级 + 置信度 3 级，§13.1.1/§13.1.2，RI-22/RI-23） + `MaterialController`（软删，§13.1.10，RI-31） + `ProjectController`（软删 + 撤销，§13.1.10/§13.2.1，RI-31/RI-32） + `ProposalController`（附条件决议 + 编号预留/撤销/改系列，§13.1.3/§13.1.4，RI-24/RI-25）。
5. **Agent 工具集微调**：不新增 tool（保持 6 个）；`find_project` 加 5 级隐式切换判定（RI-23） + `query_mysql` 加 §13.1.6 安全白名单（RI-27） + 新增 `network_dict_lookup` 工具（替换原"1-2 个 API"，§13.1.5，RI-26，工具数 6→7）。

**影响面总评**：后端 ~12 个新 Service + 6 个新 Controller + 5 张新表 + 10+ 处 ALTER；前端 ~8 个新 View + 4 个新 Component + 1 个全局 Pinia store（通知）；Agent 包 1 个新 tool + 1 处 prompt/few-shot 扩写；部署零侵入（仅 yml 加 5 行 + 1 张 config.json 字段）。**架构无破坏性变更**，全部在 Sisyphus 现代化分章架构 v1.0 既有 3 层（Controller/Service/Repository + Agent/Engine/Provider）内扩展。

### 风险与冲突（与现有架构 / 文档的冲突点，2026-06-11 拍板）

| # | 冲突点 | 现状 | v1.1 增量要求 | 拍板 / 解决方向 |
|---|---|---|---|---|
| **C-1** | `AGENT-FRAMEWORK-DECISION.md` §1.2 标题写"**Spring AI 1.1 + Spring AI Alibaba 1.1**"，但 §1.2.1.1 第 1 条 + `AGENT-IMPL-PLAN.md` §2 已明确"**不引** spring-ai-alibaba" | 文档标题与内容/实施结论矛盾 | 沿用 `AGENT-IMPL-PLAN §2` 决策（不引 alibaba） | **修标题**：`§1.2` 标题改为"Spring AI 1.1（仅 spring-ai-starter-model-openai）"，正文 §1.2.1.1 保留，**不在 v1.1 范围内做 alibaba 集成**（Mavis 教训：DECISION 文档标题漏改 2026-06-10 调研阶段历史遗留） |
| **C-2** | `architecture/06-requirements-gap-analysis.md` §7.3 Keep 清单明确 "❌ 业务术语网络爬取（内网不可行）" | 评估结论："网络查=超额设计，建议推迟" | `REQUIREMENTS §13.1.5` 把网络查改为"可配字典 + 4 候选（百度百科/维基/金融百科/互动百科）" | **降级实施**：v1.1 实施时**只做配置化 + 百度百科 + 维基百科 2 个**（最低限度满足业务可配置），金融百科 / 互动百科留"已停用"占位 entry，等业务方确认出网策略再启用（业务方在 §13.1.5 段落已暗示"业务方提供 URL"） |
| **C-3** | `architecture/06-requirements-gap-analysis.md` §4 第 1 项建议"流式 SSE 降级为全量返回" | 评估结论：流式 SSE = 超额设计 | `REQUIREMENTS §13.1.8` 补充主页"双模"过渡动画 300ms（CSS transition，非 SSE） | **无冲突**：动画 ≠ 流式，沿用 Keep 清单。`REQUIREMENTS §13` 没引入新 SSE 需求，**继续保持 120s 全量返回 + AgentStepsPanel 折叠** |
| **C-4** | `REQUIREMENTS §13.2.3` RBAC 5 角色（admin / 项目经理 / 业务部门 / 投委会委员 / 秘书）跟 `role` 表现有 2 行（admin / user）冲突 | `init.sql` 预置 admin / user | v1.1 要扩到 5 角色 + `user_role` 多对多 + `project_member` 项目级 | **兼容方案**：**保留** `role` 表 admin/user 行（不删），**v1.1-RI-34** 用 `INSERT INTO role(name) VALUES ('pm'), ('legal'), ('committee'), ('secretary')` 加 4 行 + 升级 `user.role_id` FK 引用方式为**双轨**：旧 `user.role_id` 保留 admin/user 兼容 + 新增 `user_role` 多对多（v2 主用，v1.1 灰度）；**零回归**：现有 admin 登录路径不变 |
| **C-5** | `REQUIREMENTS §13.1.3` 决议变更"附条件通过"增 `proposal.condition_status` 字段 | `proposal` 当前 7 字段（id/code/project_id/title/type/status/meeting_result/llm_summary） | v1.1 加 3 字段（condition_text / condition_status / condition_met_at） | **直接 ALTER**：兼容方案，`condition_status DEFAULT 'none'`（不是 pending，而是 none 表示非附条件议案），避免历史数据迁移 |
| **C-6** | `REQUIREMENTS §13.1.10` 删除策略要求 `project/proposal/material/business_term` 4 个实体都加 `status=deleted` / `deleted_at` / `deleted_by`；但 `fact_event` 写明"**不可删，仅 INSERT**" | 既有 16 张表 + spring_ai_chat_memory 18 张，`audit_log` 已部分支持 | v1.1 要改 5 张表（含 4 实体 + 1 实体 `fact_event` 锁死不可变） | **两路并行**：(a) 5 实体加 `*_deleted_at` 软删字段；(b) `project_fact_event` 加 DB 触发器 `BEFORE UPDATE/DELETE → SIGNAL SQLSTATE '45000'`（MySQL 8 支持 SIGNAL，**应用层**也用 `EntityListener` 拦截 @PreUpdate/@PreDelete 抛异常）— 双保险 |
| **C-7** | `backend/agent/AgentEngine.java` 第 53 行硬编码 `MAX_ITERATIONS = 5`（不可配） | 不可配，Code Review 决定（`AGENT-IMPL-PLAN §2`） | v1.1 §13.1.2 隐式切换 5 级判定 + §13.1.6 跨项目工具白名单（都依赖"步数余量"） | **保持硬编码**：v1.1 改造在 `find_project` 工具内部 + AgentEngine hint 注入逻辑，**不破 5 步上限**；v2 真正多用户时再考虑 yml 可配（5 步跑 5 级判定仍够用：5 级判定走的是工具内部，**不算步数**） |
| **C-8** | `LESSONS-LEARNED §6.5.2` 提到 Sisyphus 漏 `QueryMysqlTool` 4 重加固（`group_by` / `is_not_null` / `MAX_IN_VALUES=50` / `escapeLikePattern`） | 已修 | v1.1 §13.1.6 增 5 类白名单（region / industry / stage / fact_type / time_bucket）+ 数值上限 + 行数截断 | **扩展加固**：`QueryMysqlTool` 已有 4 重基础上，**新增** 3 重 → **7 重加固**（白名单 6 实体 / 字段白名单 / 参数化 / IN≤50 / LIKE 转义 / **白名单 filters** / **行数截断≤1000**） |
| **C-9** | `README §3.2` 说 backend 包路径是 `backend/agent/`（14 文件），实际是 `backend/src/main/java/com/archive/agent/` | README 文档笔误 | v1.1 文档需引用正确路径 | **本次同步修 README**（沿 README §7.3 文档演进规则，README 跟代码不一致立即修），本架构文档 §3/§6 全部用正确路径 `com.archive.agent.*` |
| **C-10** | `architecture/06-requirements-gap-analysis.md` §6.2 估时"v1.1 剩余 13 天"，但 `REQUIREMENTS §13` 是 24 条 RI | 评估"~5+3+2+3=13 天"是 v1.0 末 P0 估时 | v1.1 增量是 24 条 RI，对应工作量 ~30 天 | **本架构文档 §6 / T2 / T3** 用 24 条 RI 全量分解估时（详细见附录 A 表） |

---

## §1. 现有架构基线（v1.0，基线 `0c6325f`）

> 详细参见 `docs/architecture/01-arch-overview.md` + `docs/ARCHITECTURE-v2.md` + `docs/AGENT-IMPL-PLAN.md`。本节仅摘要 v1.1 增量所需基线。

### 1.1 三层架构

```
浏览器 (Vue 3 + Element Plus)
   ↕ HTTPS :443 (Caddy 反代 + TLS)
Spring Boot 3.3 单体 (WinSW 管理)
   ├─ Controller (13 个 REST, @RestController)
   ├─ Service (17 个, @Service + 事务)
   ├─ Agent (1 包 com.archive.agent.*, 14 文件, Spring AI 1.1 + 6 工具 + 5 步 ReAct)
   ├─ Engine (4 个, @Async 后台)
   ├─ Provider (LLM 抽象, GLMProvider 唯 1 实现)
   ├─ Repository (16 个, JpaRepository)
   ├─ Entity (16 个 JPA + 2 基础设施)
   └─ Security (JWT + RBAC + BCrypt + 限流)
   ↕ JDBC
MySQL 8 (FULLTEXT ngram, 无外部 ES/向量)
   ↕ HTTPS :443 (出站)
智谱 GLM-4-Flash (OpenAI 兼容协议)
```

### 1.2 关键模块清单（v1.1 增量会触达）

| 层 | 模块 | 文件数 | v1.1 触达度 |
|---|---|---|---|
| Controller | `QaController` (1) + `MaterialController` (1) + `ProjectController` (1) + `ProposalController` (1) + 新增 (5~6) | 13 → 18~19 | 🔴 高 |
| Service | 17 + 新增 12 (RBAC/看板/通知/导出/导入/回收站/失败日志/字典查) | 17 → ~29 | 🔴 高 |
| Agent | `com.archive.agent` (14 文件) + 1 新 tool (network_dict_lookup) | 14 → 15 | 🟡 中 |
| Provider | `LLMProvider` / `LLMProviderFactory` / `GlmService` 降级 | 3 | 🟢 沿用 |
| Engine | `ExtractionEngine` / `TimepointExtractor` / `TriggerEngine` / `ComparisonEngine` | 4 | 🟡 微调 |
| Repository | 16 + 新增 7 (notification / failure_log / project_member / user_role / 字典配置 / 导入批次 / 导出批次) | 16 → 23 | 🔴 高 |
| Entity | 16 + 新增 5 + ALTER 7 表 | 16 → 21 + 7 ALTER | 🔴 高 |
| 前端 views | 13 + 新增 5~6 (Board / Notification / Export / Preview / ComparisonDiff / Import) | 13 → 18~19 | 🔴 高 |
| 前端 components | 1 (`AgentStepsPanel`) + 新增 4 (DiffViewer / PreviewFrame / NotifBell / RecycleBinList) | 1 → 5 | 🟡 中 |
| 前端 store | 1 (`auth`) + 新增 1 (`notification` 全局) | 1 → 2 | 🟡 中 |
| DB migration | `init.sql` + 6 个 `I-*.sql` | 6 + 7 个新 I-RI-N.sql | 🔴 高 |
| 部署 | Caddy + WinSW + 1 个 application.yml | 0 改动结构 + 5 行 yml | 🟢 沿用 |
| LLM | 智谱 GLM-4-Flash（OpenAI 兼容） | 1 + 1 备用 OpenAI 兼容 | 🟢 沿用 |

### 1.3 6 个 Agent 工具（v1.0 完工，v1.1 微调）

| # | 工具 | 实现 | v1.1 改动 |
|---|---|---|---|
| 1 | `find_project` | `FindProjectTool.java`（4 级兜底链） | 加 §13.1.2 **5 级隐式切换判定**（在 tool 内部，**不算 ReAct 步数**） |
| 2 | `search_fulltext` | `SearchFulltextTool.java` | 沿用 |
| 3 | `query_mysql` | `QueryMysqlTool.java`（白名单 + 4 重加固） | 加 §13.1.6 跨项目白名单 + 行数截断（7 重加固） |
| 4 | `get_project_business_data` | `GetProjectBusinessDataTool.java` | 沿用 |
| 5 | `llm_summarize` | `LlmSummarizeTool.java` | 沿用 |
| 6 | `ask_clarification` | `AskClarificationTool.java` | 沿用 |
| **7 (新)** | `network_dict_lookup` | (新) `NetworkDictLookupTool.java` | 替换原"1-2 个 API"为可配字典（RI-26） |

---

## §2. v1.1 增量需求架构影响（按 §13 顺序）

> 每条需求对应：(1) §13.X 引用 (2) RI-N 拆解 (3) 影响表/模块 (4) 接口 (5) 兼容性 / 零回归注。

### §2.1 §13.1 业务规则细化（10 处）

#### §2.1.1 置信度 3 级体系（§13.1.1，RI-22）

| 项 | 内容 |
|---|---|
| **业务** | 替换原 §5.8.3 "0.6 阈值"二元判定为 3 级（≥0.85 自动入库 / 0.60-0.84 AI 推测 / <0.60 待人工） |
| **影响表/模块** | `entity.ProjectFact` (ALTER 增 `confidence_level` 枚举) + `entity.ProjectFactEvent` (新增 `confidence_level` 同上) + `service.ExtractionService` (改判定逻辑) + `engine.ExtractionEngine` (改回调) + `dto.FactResponse` (增 `confidenceBadge`) |
| **接口** | `GET /api/projects/{id}/facts?confidenceLevel=AI_INFERRED` 增 query 过滤 |
| **零回归** | `confidence` DECIMAL(3,2) 字段保留（不删），**新增** `confidence_level VARCHAR(16)` 派生字段（0/1/2/3 枚举）。`ProjectFact` 历史数据回填脚本：`UPDATE project_fact SET confidence_level = CASE WHEN confidence>=0.85 THEN 'CONFIRMED' WHEN confidence>=0.60 THEN 'AI_INFERRED' ELSE 'PENDING_REVIEW' END`（**1 次性回填**，新建 v1.1-RI-22.sql） |

#### §2.1.2 隐式项目切换 5 级判定（§13.1.2，RI-23）

| 项 | 内容 |
|---|---|
| **业务** | 替换原 §5.6.7.4 "0.95 阈值"为 5 级判定（同 projectCode 三档 + 不同 projectCode 两档） |
| **影响表/模块** | `tool.FindProjectTool.java` (改 4 级兜底链，**第 4 级 LLM 兜底返回的 confidence 走 5 级判定**) + `agent.AgentContext.java` (增 `lastSwitchDecision` 字段) + `agent.AgentEngine.java` (增 hint 注入分支) |
| **接口** | 内部接口，**无新 REST 端点**。`AgentResponse` 增可空 `projectSwitchHint: 'SAME_PROBABLY' / 'DIFFERENT_PROBABLY' / 'UNCLEAR'` |
| **零回归** | **不动** `MAX_ITERATIONS=5` 硬编码。5 级判定在 `find_project` 工具**内部**完成（不算 ReAct 步数）。原有 `search_fulltext` / `query_mysql` 行为不变 |

#### §2.1.3 决议变更业务规则（§13.1.3，RI-24）

| 项 | 内容 |
|---|---|
| **业务** | 草稿可改 / 已开投委会不可改（走"复议"= 新议案）/ 附条件通过增 `condition_status` 跟踪 |
| **影响表/模块** | `entity.Proposal` (ALTER 增 3 字段：`condition_text` / `condition_status` / `condition_met_at`) + `controller.ProposalController` (增 PATCH `/api/proposals/{id}/condition` 端点) + `service.ProposalService` (增 `markConditionMet()` / `revoke()` 业务方法) + `engine.TriggerEngine` (增"condition_status=met → 自动 trigger todo"规则) |
| **接口** | `PATCH /api/proposals/{id}/condition` body=`{status: 'MET' \| 'UNMET', note: '...'}` |
| **零回归** | `condition_status DEFAULT 'NONE'`（不是 `'PENDING'`，避免历史数据歧义）。原有 proposal 7 字段全保留 |

#### §2.1.4 投委会编号预留/撤销/改系列（§13.1.4，RI-25）

| 项 | 内容 |
|---|---|
| **业务** | draft_reserved（24h 自动释放）/ revoked（编号加 `.revoked` 后缀）/ 改系列（仅 draft_reserved 允许） |
| **影响表/模块** | `entity.ProposalSeries` (新增 sequence 表, `code` / `current_seq` / `prefix` 3 字段) + `entity.Proposal` (ALTER 增 `reserved_at` / `released_at`) + `service.ProposalNumberGenerator` (重写为可预留/可释放) + 新增 `@Scheduled` 定时任务 (每小时扫 24h 未确认的 draft_reserved) |
| **接口** | `POST /api/proposals/reserve` body=`{seriesCode, projectId}` → `{proposalCode: 'tx26003', expiresAt: '...'}` |
| **零回归** | 现有 `proposal.code` 字段保留 UNIQUE 约束（**用 prefix + `.revoked` 后缀实现唯一性**）。`ProposalNumberGenerator` 旧逻辑（直接生成）保留为 `legacyGenerate()`，新逻辑加 `reserve()` / `release()` / `changeSeries()` |

#### §2.1.5 网络查 API 字典（§13.1.5，RI-26）

| 项 | 内容 |
|---|---|
| **业务** | 4 候选（百度百科/维基/金融百科/互动百科）+ 优先级 + 可配 + 内网降级 |
| **影响表/模块** | `entity.DictType` (扩 `code='network_dict_source'` 字典类型) + `entity.DictItem` (扩 `item_value` 存 baseUrl / apiKey 配置) + 新 `service.NetworkDictService.java` (4 候选调用 + 优先级调度) + 新 `tool.NetworkDictLookupTool.java` (Agent 工具) + `application.yml` 增 `archive.network-dict.*` 配置 |
| **接口** | `GET /api/dict/network-sources` 返回当前 4 候选 + 启用状态 + 优先级（admin 可改） |
| **零回归** | **不破坏** 原 `BusinessTerm` 既有 `localDefinition` 字段。**新增** `remoteDefinition` 字段（网络查结果缓存）。**降级路径**：4 候选全失败 → 返回 `{found: false, reason: 'INTRANET_BLOCKED'}`（**不抛异常**，业务层降级到"请手工填"提示） |
| **冲突** | 见 §0 风险 C-2（v1.1 实施只配百度+维基 2 候选，金融/互动百科留占位） |

#### §2.1.6 跨项目批量工具安全白名单（§13.1.6，RI-27）

| 项 | 内容 |
|---|---|
| **业务** | 5 类 filters 字段（region/industry/stage/fact_type/time_bucket）白名单 + 数值上限 + 行数截断 ≤1000 |
| **影响表/模块** | `tool.QueryMysqlTool.java` (扩 ALLOWED_FILTER_KEYS / MAX_RESULT_ROWS=1000 / MAX_AMOUNT=1e8) + `entity.DictType` (扩 5 类字典：fact_region / industry / fact_type / time_bucket) + `application.yml` 增 `archive.query-mysql.*` |
| **接口** | 内部 Agent 工具参数校验，**无新 REST** |
| **零回归** | 现有 4 重加固（`group_by` / `is_not_null` / `MAX_IN_VALUES=50` / `escapeLikePattern`）保留，新增 3 重 → **7 重加固**（见 §0 风险 C-8） |

#### §2.1.7 关键事实事件流字段细化（§13.1.7，RI-28）

| 项 | 内容 |
|---|---|
| **业务** | `project_fact_event` 增 4 字段（owner_id / due_date / resolved_at / resolution_note） |
| **影响表/模块** | `entity.ProjectFactEvent` (ALTER 增 4 字段) + `repository.ProjectFactEventRepository` (增 `findByStatusAndDueDateBefore()` 查询) + `dto.ProjectFactEventResponse` (同 4 字段) + 新 `service.ProjectFactEventService.java` (CRUD) + 新 `controller.ProjectFactEventController` (GET /api/projects/{id}/fact-events/pending 端点) |
| **接口** | `GET /api/projects/{id}/fact-events/pending?asOf=2026-06-11` → 待处置事实列表 |
| **零回归** | **不动** `project_fact_event` 不可变约束（INSERT-only），新增字段**允许 UPDATE**（仅 owner_id / due_date / resolved_at / resolution_note 这 4 个，**触发器级白名单**） |

#### §2.1.8 主页"双模"过渡动画（§13.1.8，RI-29）

| 项 | 内容 |
|---|---|
| **业务** | 待办数 0→1 / N→0 触发 300ms CSS transition，顶部"问点什么"按钮常驻 |
| **影响表/模块** | 前端 `views/Dashboard.vue` (改 transition 逻辑) + 新前端 `components/AnimatedModeSwitch.vue` (300ms 过渡) |
| **接口** | 纯前端，**无后端改动** |
| **零回归** | 现有 `Dashboard.vue` 改造点 < 50 行（沿用 Element Plus `el-transition`） |

#### §2.1.9 LLM 抽字段失败兜底（§13.1.9，RI-30）

| 项 | 内容 |
|---|---|
| **业务** | 5 种失败类型差异化兜底（API 失败/非 JSON/字段缺失/字段值异常/parse 失败） |
| **影响表/模块** | `service.GlmService.java` (扩 `callWithLog()` 异常分类) + `engine.ExtractionEngine.java` (改回调 `onFailure(FailureType)`) + `dto.ExtractionFailureResponse` (新 DTO，`failureType` 枚举) + 前端 `views/ProjectForm.vue` (改顶部 banner + 重试按钮) |
| **接口** | `POST /api/projects` 失败时响应 `ExtractionFailureResponse`，含 `failureType` 5 枚举 + `retryable: boolean` |
| **零回归** | **不破** 现有降级路径。GlmService 现有 try-catch 保留，新增 `FailureType` 枚举细化 |

#### §2.1.10 软删 + 回收站（§13.1.10，RI-31）

| 项 | 内容 |
|---|---|
| **业务** | 4 实体软删（Project/Proposal/Material/BusinessTerm） + 1 实体不可删（FactEvent） + 30 天回收站 + 物理删 + 归档 |
| **影响表/模块** | `entity.Project/Proposal/Material/BusinessTerm` (ALTER 增 3 字段：`status` 扩 deleted 枚举 / `deleted_at` / `deleted_by`) + `entity.ProjectFactEvent` (DB 触发器 + EntityListener 锁死) + `service.RecycleBinService.java` (新，30 天扫描 + 物理删) + `controller.RecycleBinController` (新，GET/POST /api/recycle-bin) + `@Scheduled` cron `0 2 * * *` 每天凌晨 2 点扫过期 |
| **接口** | `GET /api/recycle-bin?type=project` / `POST /api/recycle-bin/{id}/restore` / `DELETE /api/recycle-bin/{id}` (admin) |
| **零回归** | 见 §0 风险 C-6（双保险：DB 触发器 + EntityListener）。`status` 字段扩枚举 `deleted` / `revoked` / `archived` / `deprecated`，**默认 NULL**（不破坏现有查询 `WHERE status IS NULL OR status != 'deleted'`） |

### §2.2 §13.2 隐含业务规则（6 大类）

#### §2.2.1 撤销/回滚/反悔（§13.2.1，RI-32）

| 项 | 内容 |
|---|---|
| **业务** | 项目 24h 整撤销 / 议案投委会前撤销 / 材料 24h 撤销 / 历史版本回滚 |
| **影响表/模块** | `entity.Project/Proposal/Material` 增 `version` 字段（乐观锁，与 §2.2.2 合并） + `entity.MaterialVersion` 已有 `version_no`（沿用） + `service.ProjectRollbackService.java` (新) + `controller.ProjectController.rollback(projectId, version)` |
| **接口** | `POST /api/projects/{id}/rollback` body=`{targetVersion: 3}` |
| **零回归** | 回滚**也是新事件**（`project_fact_event` 加一条 UPDATE 记录），**保留所有历史**。不删旧数据 |

#### §2.2.2 并发编辑乐观锁（§13.2.2，RI-33）

| 项 | 内容 |
|---|---|
| **业务** | `version` INT 默认 1，UPDATE +1，SQL `WHERE id=? AND version=?` 影响 0 行 → 提示刷新 |
| **影响表/模块** | `entity.Project/Proposal/Material` (ALTER 增 `version` INT DEFAULT 1) + `service` 三处 (Project/Proposal/Material) UPDATE 加 `@Version` 注解 + `GlobalExceptionHandler` 增 `OptimisticLockException → ApiResponse.fail(409, '数据已被他人修改')` |
| **接口** | 现有 PATCH/PUT 接口响应体增 `version: 4` 字段，前端提交时回带 |
| **零回归** | v1.1 灰度：单用户系统**不强制**校验失败（v1.1 期，**仅记录日志**）；v2 多用户时启用强制。详见 `application.yml` 开关 `archive.optimistic-lock.strict: false` |

#### §2.2.3 RBAC 5 角色（§13.2.3，RI-34）

| 项 | 内容 |
|---|---|
| **业务** | admin / 项目经理 / 业务部门 / 投委会委员 / 秘书 + `user_role` 多对多 + `project_member` 项目级 |
| **影响表/模块** | `entity.Role` (扩 4 行 INSERT) + 新 `entity.UserRole` (user_id / role_id) + 新 `entity.ProjectMember` (project_id / user_id / role_in_project) + `security.SecurityConfig` (扩 `hasRole('COMMITTEE')` 等) + 新 `service.RbacService.java` (5 角色权限矩阵) + `JwtAuthFilter` 增 `userRoles` claim |
| **接口** | `GET /api/admin/users/{id}/roles` (admin) / `POST /api/projects/{id}/members` |
| **零回归** | 见 §0 风险 C-4（admin/user 双轨 + user_role 灰度）。**不动** `user.role_id` 字段，仅 INSERT 新 role 行 + 新增 `user_role` 关联表。v1.1 期，**前端按 `user_role` 优先**，`user.role_id` 兼容 admin 登录 |

#### §2.2.4 审计加强（§13.2.4，RI-35）

| 项 | 内容 |
|---|---|
| **业务** | 5 类审计事件（WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM） |
| **影响表/模块** | `entity.AuditLog` (ALTER 增 `type` VARCHAR(32) 枚举 + `entity_subtype` VARCHAR(32) 可空) + `service.AuditLogService` 增 5 类写入方法 + `controller.AuditLogController` 增 `GET /api/audit-logs?type=SENSITIVE_VIEW&userId=...` 筛选 |
| **接口** | `GET /api/audit-logs?type=SENSITIVE_VIEW` / `GET /api/audit-logs/export` (CSV) |
| **零回归** | **保留** `audit_log.action` 字段（细粒度动作名），**新增** `type` 字段（粗粒度分类）。历史数据回填：`UPDATE audit_log SET type = CASE WHEN action='LOGIN' THEN 'LOGIN' WHEN action='LOGOUT' THEN 'LOGIN' ELSE 'WRITE' END` |

#### §2.2.5 数据生命周期（§13.2.5，RI-36）

| 项 | 内容 |
|---|---|
| **业务** | 软删 30 天 → 物理删（DB 保留） → 归档 1 年 → 长期归档 5 年 → 不允许永久删 |
| **影响表/模块** | `entity.Material` (增 `archived_at` DATETIME) + `service.RecycleBinService` 扩 30 天扫描（与 RI-31 合并） + 新 `service.ArchiveService.java` (1 年扫归档) + `application.yml` 增 `archive.retention.*` 周期配置 |
| **接口** | 内部 `@Scheduled`，**无新 REST** |
| **零回归** | **不删** DB 记录（业务必保留审计），仅文件 + parsed_text 物理删。归档只标记 `status=archived`，DB 仍可查 |

#### §2.2.6 失败兜底全景（§13.2.6，RI-37）

| 项 | 内容 |
|---|---|
| **业务** | 10 路径 × N 失败类型 → 统一进 `failure_log` 表 + admin 可查 |
| **影响表/模块** | 新 `entity.FailureLog` (id / path / failure_type / error_msg / stack_trace / occurred_at / resolved_at) + 新 `service.FailureLogService.java` + `controller.FailureLogController` (`GET /api/failure-logs?resolved=false`) + 各业务 Service 加 `@Around` AOP 拦截 + 写 `failure_log` |
| **接口** | `GET /api/failure-logs?path=project.create&resolved=false&from=2026-06-01` |
| **零回归** | **不破坏** 现有 `audit_log` + `llm_call_log`。FailureLog 是**第 3 张日志表**，专门记"业务级失败"（区别于"用户操作审计"和"LLM 调用"） |

### §2.3 §13.3 业务方没说但应该有的功能（6-8 项）

#### §2.3.1 项目看板（§13.3.1，RI-38）

| 项 | 内容 |
|---|---|
| **业务** | 主页"🏠" → "项目看板" 子页，3 视图（表格/卡片/看板分组） + 7 筛选 + 4 排序 + 9 列 |
| **影响表/模块** | `entity.Project` (无 ALTER) + `repository.ProjectRepository` (扩 `@Query` 聚合查询：累计议案数 / 待办数 / 最后更新时间) + 新 `service.ProjectBoardService.java` + 新 `controller.ProjectBoardController` (`GET /api/projects/board?view=kanban&stage=POST_LOAN`) |
| **接口** | `GET /api/projects/board?view=table\|card\|kanban&region=江苏&stage=POST_LOAN&sort=amount&order=desc&page=1&size=20` |
| **零回归** | 纯聚合查询，**不写**新表。`ProjectRepository` 现有 `findAll(Pageable)` 沿用，新增 `findBoardView()` 走 @Query JPQL |

#### §2.3.2 站内通知中心（§13.3.2，RI-39）

| 项 | 内容 |
|---|---|
| **业务** | 顶栏"🔔" + 弹窗 + 4 类来源（待办/议案/事实/系统） + 已读/未读 |
| **影响表/模块** | 新 `entity.Notification` (id / user_id / type / title / content / link / read / created_at) + 新 `repository.NotificationRepository` + 新 `service.NotificationService.java` (CRUD + markRead) + 新 `controller.NotificationController` (`GET /api/notifications?unread=true`) + **新前端 Pinia store** `store/notification.ts` + `components/NotifBell.vue` |
| **接口** | `GET /api/notifications?unread=true&page=1&size=20` / `PATCH /api/notifications/{id}/read` / `POST /api/notifications/mark-all-read` |
| **零回归** | **不动** 现有 `todo` 表，**新增** `notification` 表（通知 ≠ 待办，通知是"事件流"，待办是"行动项"）。SSE 不引入（沿用 120s 轮询：`setInterval(30s)`） |

#### §2.3.3 数据导出（§13.3.3，RI-40）

| 项 | 内容 |
|---|---|
| **业务** | 项目详情页"导出"按钮 + PDF（单项目报告）/ Excel（4 类列表） + 审计 |
| **影响表/模块** | `controller.ProjectController` 增 `GET /api/projects/{id}/export?format=pdf` / `GET /api/projects/export?format=xlsx&type=materials` + 新 `service.ExportService.java` (PDF: OpenPDF 5.x / Excel: Apache POI 5.x) + 审计走 `AuditLogService.logExport()` (与 RI-35 联动) |
| **接口** | `GET /api/projects/{id}/export?format=pdf` 返回 `application/pdf` 流 |
| **零回归** | **不引** 新服务（沿用单体）。`pom.xml` 加 `com.github.librepdf:openpdf:2.0.2` + `org.apache.poi:poi-ooxml:5.2.5`（**纯 jar 增量 < 10MB**，跟 Spring AI 1.1 增量 < 15MB 同一个量级，符合"零硬件改动"决策） |

#### §2.3.4 附件预览（§13.3.4，RI-41）

| 项 | 内容 |
|---|---|
| **业务** | 材料列表点文件名 → 浏览器内嵌预览（PDF/Word/图片/文本） |
| **影响表/模块** | `controller.MaterialController` 增 `GET /api/materials/{id}/preview?version=3` 返回 `application/pdf` 流 + 新 `service.PreviewService.java` (Word→PDF 转码用 LibreOffice headless 或 mammoth.js) + **前端** 新 `components/PreviewFrame.vue` (pdf.js / mammoth.js) |
| **接口** | `GET /api/materials/{id}/preview?version=3` 返回流；`Accept: text/html` 时返回 `<iframe src="...">` |
| **零回归** | **不引** LibreOffice 进程（Word 预览走前端 mammoth.js 转 HTML）；PDF 直接 `<iframe :src="pdfUrl">`。**新增前端依赖** `pdfjs-dist: ^4.0` + `mammoth: ^1.7`（**纯前端**，不打入 jar） |

#### §2.3.5 关键事实变更对比视图（§13.3.5，RI-42）

| 项 | 内容 |
|---|---|
| **业务** | 事实时间线 → 单条 UPDATE → 弹窗"变更对比" + JSON tree diff + 证据引用 |
| **影响表/模块** | `service.ProjectFactEventService` 增 `getDiff(eventId)` 返回 `{before, after, evidenceSnippet}` + **前端** 新 `components/DiffViewer.vue` (JSON tree diff，用 `jsondiffpatch: ^0.5` 库) + `views/ProjectDetail.vue` 改事实 tab |
| **接口** | `GET /api/projects/{id}/fact-events/{eventId}/diff` |
| **零回归** | **不破** 现有 `project_fact_event` INSERT-only 约束（diff 是**派生计算**，不写新数据） |

#### §2.3.6 业务术语中英对照（§13.3.6，RI-43）

| 项 | 内容 |
|---|---|
| **业务** | `business_term` 增 `english_name` 字段 + UI 显示 + Agent 英文查询 |
| **影响表/模块** | `entity.BusinessTerm` (ALTER 增 `english_name` VARCHAR(128) 可空) + 前端 `views/AdminDict.vue` (术语 tab 增英文列) + `prompt.AgentFewShots` 加 1 条 few-shot："用户问 'vacant claim' → 自动查表返回中文定义" |
| **接口** | **无新 REST**，现有 `GET /api/dict/terms?q=vacant` 增 `englishName` 字段 |
| **零回归** | **不破坏** 现有 `business_term` 中文定义流程。`english_name` 可空（v1.1 期允许不全填） |

#### §2.3.7 旧系统 Excel 导入接口（§13.3.7，RI-44）

| 项 | 内容 |
|---|---|
| **业务** | admin "数据导入"入口 + 4 类模板下载 + 字段校验 + 唯一索引冲突 + 导入审计 |
| **影响表/模块** | `controller.ImportController` (新) `POST /api/admin/import/{type}` (type=project/material/proposal/fact) + 新 `service.ImportService.java` (Apache POI 读 .xlsx + Bean Validation + 业务校验) + 新 `entity.ImportBatch` (id / type / total / success / failed / created_by / created_at) + 新 `entity.ImportError` (batch_id / row / column / error_msg) |
| **接口** | `POST /api/admin/import/project` multipart/form-data + `GET /api/admin/import/{batchId}/errors` |
| **零回归** | **v1.1 灰度**：仅 admin 可调，业务方不实际跑导入流程（只做接口 + 模板，业务方接受"接口预留"）。pom.xml 复用 RI-40 的 Apache POI 5.x |

#### §2.3.8 数据脱敏视图（§13.3.8，RI-45）

| 项 | 内容 |
|---|---|
| **业务** | 投委会委员看脱敏视图（`张**` / `***万` / 关联方脱敏 / 决策类正常）+ 申请脱敏查看留痕 + admin 通知 |
| **影响表/模块** | `entity.User` (ALTER 增 `sensitive_view_enabled` BOOLEAN DEFAULT false) + `service.MaskingService.java` (新，按 `user.roles` 决定是否脱敏) + `dto.ProjectResponse` 增 `displayName` / `displayAmount` 派生字段 + 前端 `views/ProjectDetail.vue` 改显示逻辑 + 审计走 `AuditLogService.logSensitiveView()` (与 RI-35 联动) + 通知走 `NotificationService.notifyAdminSensitiveView()` (与 RI-39 联动) |
| **接口** | 现有 `GET /api/projects/{id}` 响应体增 `masked: boolean` + `unmaskRequestUrl: string \| null` |
| **零回归** | **不破** admin 视图（`user.sensitive_view_enabled=true`）。**新增**前端 MaskingFilter 拦截器 + 后端 `MaskingService.mask(project, user)` 方法（纯函数，**不写**新表） |

---

## §3. 后端扩展

### 3.1 包结构扩展（`com.archive.*`）

```
com.archive/
├── ArchiveApplication.java
├── common/                            # 现有 4 文件 + 扩 FailureType.java（新）
├── config/                            # 现有 1 文件 + 扩 RbacConfig.java（新, 5 角色）
├── controller/                        # 现有 13 → 18 (新增 5)
│   ├── ... (现有 13 不动)
│   ├── ProjectBoardController.java    # 新 (RI-38)
│   ├── NotificationController.java    # 新 (RI-39)
│   ├── RecycleBinController.java      # 新 (RI-31)
│   ├── ProjectFactEventController.java # 新 (RI-28)
│   └── ImportController.java          # 新 (RI-44)
├── dto/                               # 现有 24 + 新增 9 (FactResponse/ExtractionFailure/NotificationRequest/...)
├── entity/                            # 现有 16 + 新增 5 + ALTER 7
│   ├── ... (现有 16)
│   ├── Notification.java              # 新 (RI-39)
│   ├── FailureLog.java                # 新 (RI-37)
│   ├── UserRole.java                  # 新 (RI-34)
│   ├── ProjectMember.java             # 新 (RI-34)
│   ├── ImportBatch.java               # 新 (RI-44)
│   ├── ImportError.java               # 新 (RI-44)
│   └── ... (7 个 ALTER: Project/Proposal/Material/AuditLog/ProjectFactEvent/BusinessTerm/User)
├── repository/                        # 现有 16 → 23
│   ├── ... (现有 16)
│   ├── NotificationRepository.java    # 新
│   ├── FailureLogRepository.java      # 新
│   ├── UserRoleRepository.java        # 新
│   ├── ProjectMemberRepository.java   # 新
│   ├── ImportBatchRepository.java     # 新
│   ├── ImportErrorRepository.java     # 新
│   └── ProposalSeriesRepository.java  # 新 (RI-25)
├── security/                          # 现有 4 + 扩 RbacExpressionRoot.java（新, @PreAuthorize 5 角色）
├── service/                           # 现有 17 → 29 (新增 12)
│   ├── ... (现有 17)
│   ├── NetworkDictService.java        # 新 (RI-26)
│   ├── RbacService.java               # 新 (RI-34)
│   ├── MaskingService.java            # 新 (RI-45)
│   ├── ProjectBoardService.java       # 新 (RI-38)
│   ├── NotificationService.java       # 新 (RI-39)
│   ├── RecycleBinService.java         # 新 (RI-31)
│   ├── ArchiveService.java            # 新 (RI-36)
│   ├── FailureLogService.java         # 新 (RI-37)
│   ├── ExportService.java             # 新 (RI-40)
│   ├── PreviewService.java            # 新 (RI-41)
│   ├── ImportService.java             # 新 (RI-44)
│   └── ProjectRollbackService.java    # 新 (RI-32)
├── engine/                            # 现有 4 + 扩 ExtractionEngine 改 onFailure 回调 (RI-30)
├── provider/                          # 现有 2 + 沿用 GlmService 改 FailureType 分类 (RI-30)
└── agent/                             # 现有 14 → 15 (新增 NetworkDictLookupTool)
    ├── ... (现有 14 全部沿用)
    ├── prompt/AgentFewShots.java      # 改 (RI-43 加英文术语 few-shot)
    ├── prompt/AgentSystemPrompt.java  # 改 (RI-22/RI-23 加 3 级 + 5 级说明)
    ├── tool/FindProjectTool.java      # 改 (RI-23 加 5 级判定)
    ├── tool/QueryMysqlTool.java       # 改 (RI-27 加白名单 + 行数截断)
    └── tool/NetworkDictLookupTool.java # 新 (RI-26)
```

**总文件数**：~115 → ~165（+50，新增量对应 v1.1 估时 ~30 天 / 6 程序员并行 = 5 周工期）

### 3.2 新增 Controller-Service 矩阵

| Controller | 端点前缀 | Service | 对应 RI | 优先级 |
|---|---|---|---|---|
| `ProjectBoardController` | `/api/projects/board` | `ProjectBoardService` | RI-38 | P0 |
| `NotificationController` | `/api/notifications` | `NotificationService` | RI-39 | P0 |
| `RecycleBinController` | `/api/recycle-bin` | `RecycleBinService` | RI-31 | P0 |
| `ProjectFactEventController` | `/api/projects/{id}/fact-events` | `ProjectFactEventService` | RI-28 | P0 |
| `ImportController` | `/api/admin/import` | `ImportService` | RI-44 | P1 |
| `FailureLogController` (现有 `AuditLogController` 扩) | `/api/failure-logs` | `FailureLogService` | RI-37 | P0 |

### 3.3 既有 Controller-Service 改造点

| 现有类 | 改造 | 对应 RI | 零回归注 |
|---|---|---|---|
| `QaController` | `ask()` 加 §13.1.1 置信度 3 级 / §13.1.2 隐式切换 5 级 | RI-22, RI-23 | 保留 `agentMode=false` 老路径，`agentMode=true` 才走新逻辑 |
| `MaterialController` | `delete()` 改软删（status=deleted, deleted_at=now） | RI-31 | 旧 `delete()` 行为 = 物理删，**保留**为 admin only `/api/admin/materials/{id}/purge` 端点 |
| `ProjectController` | `delete()` 改软删 / `rollback()` 新增 / `restore()` 新增 | RI-31, RI-32 | `delete()` 加 `@PreAuthorize('hasRole("ADMIN") or @projectPermission.isOwner')`，仍走软删 |
| `ProposalController` | `updateDecision()` 校验"已开投委会不可改" / `revoke()` / `changeSeries()` / `markCondition()` | RI-24, RI-25 | 旧 `updateDecision()` 保留 PATCH 路径（仅改 `meeting_result` 不改 `status`），新逻辑加 `PUT /api/proposals/{id}/decision` 端点 |
| `AuditLogController` | `list()` 加 `?type=SENSITIVE_VIEW` 过滤 | RI-35 | 旧 `list()` 不带 type 返回全部，**新增** query param 默认 backward-compat |
| `ProjectController` (`POST /api/projects`) | 改 `ExtractionFailureResponse` 失败类型枚举 | RI-30 | 旧响应体保留 `success: false` + `message`，**新增** `failureType` 字段（nullable） |
| `MaterialController` (`GET /api/materials/{id}/versions`) | 改 `GET /api/materials/{id}/preview` 流 | RI-41 | **不破** 旧 download 端点 |

### 3.4 与现有 `agent/` 集成点

| 改造 | 路径 | 零回归 |
|---|---|---|
| `AgentEngine` 调用 `find_project` 时，把 5 级判定 hint 注入 | `AgentEngine.java:run()` 改 | 5 步上限不变 |
| `QueryMysqlTool` 加白名单 filters + 行数截断 | `QueryMysqlTool.java:execute()` 改 | 现有 `query()` 签名不变，加可选 `filters` Map 参数 |
| 新 `NetworkDictLookupTool` 注入 `AgentEngine` 的 `toolMap` | Spring 自动扫描 `@Component` | 自动注入，**不动** `AgentConfig` |
| `AgentFewShots` 加英文术语 few-shot | `AgentFewShots.java:examples()` 改 | 现有 5 条 few-shot 全部保留 |
| `AgentSystemPrompt` 加 3 级 + 5 级说明 | `AgentSystemPrompt.java:render()` 改 | 现有 prompt 模板保留，**追加** §2.1.1 + §2.1.2 说明段 |

### 3.5 跟 Sisyphus 6 份分章架构的对应

| 改造 | Sisyphus 现有文档 | v1.1 增量要点 |
|---|---|---|
| `01-arch-overview.md §4 后端分层` | "Controller 13 / Service 17 / Engine 4 / Repository 16" | 变 "Controller 18 / Service 29 / Engine 4 / Repository 23" |
| `02-backend-layer-architecture.md §2 Controller 路由表` | 列 13 个 Controller | **追加 5 个新 Controller 行** |
| `02-backend-layer-architecture.md §3 Service 清单` | 列 17 个 Service | **追加 12 个新 Service 行** |
| `04-database-schema.md §3 表清单` | 列 16 业务表 + 2 基础设施 | **追加 5 新表 + 7 ALTER 段** |
| `04-database-schema.md §5 迁移历史` | 列 6 个迁移文件 | **追加 7 个 I-RI-N.sql 段**（命名规则见 §5.4） |
| `06-requirements-gap-analysis.md §6.2 v1.1 剩余需求` | 估时 13 天 | **重写为 24 条 RI 估时 ~30 天**（v1.1 全量） |

---

## §4. 前端扩展

### 4.1 现有前端结构（v1.0 沿用）

```
frontend/src/
├── main.ts
├── App.vue
├── api/                   # http.ts / auth.ts / archive.ts
├── store/                 # auth.ts (Pinia)
├── router/                # index.ts (11 路由)
├── views/                 # 13 页面
└── components/            # AgentStepsPanel.vue
```

### 4.2 v1.1 扩展

```
frontend/src/
├── api/
│   ├── http.ts            # 沿用 + 增 notif 轮询拦截器
│   ├── auth.ts            # 沿用
│   ├── archive.ts         # 沿用 + 增 30+ 端点 (board/notif/import/export/preview/diff)
│   └── notification.ts    # 新 (RI-39 SSE 轮询封装)
├── store/
│   ├── auth.ts            # 沿用
│   └── notification.ts    # 新 (RI-39 全局未读数 + 弹窗状态)
├── router/
│   └── index.ts           # 改 (11 → 16 路由)
├── views/                 # 13 → 18 (新增 5)
│   ├── ... (现有 13)
│   ├── ProjectBoard.vue           # 新 (RI-38)
│   ├── Notification.vue           # 新 (RI-39 通知中心全屏)
│   ├── RecycleBin.vue             # 新 (RI-31)
│   ├── ImportWizard.vue           # 新 (RI-44)
│   └── FactEventDiff.vue          # 新 (RI-42 弹窗 / 或作 component)
└── components/            # 1 → 5 (新增 4)
    ├── AgentStepsPanel.vue # 沿用
    ├── NotifBell.vue       # 新 (RI-39 顶栏铃铛)
    ├── DiffViewer.vue      # 新 (RI-42 JSON tree diff)
    ├── PreviewFrame.vue    # 新 (RI-41 pdf.js + mammoth)
    └── MaskedField.vue     # 新 (RI-45 脱敏显示 + 申请查看按钮)
```

### 4.3 路由扩展

```ts
// router/index.ts 增量
{
  path: '/projects/board',
  component: () => import('@/views/ProjectBoard.vue'),
  meta: { title: '项目看板', icon: 'View' }
},
{
  path: '/notifications',
  component: () => import('@/views/Notification.vue'),
  meta: { title: '通知中心', icon: 'Bell' }
},
{
  path: '/recycle-bin',
  component: () => import('@/views/RecycleBin.vue'),
  meta: { title: '回收站', icon: 'Delete', roles: ['ADMIN'] }
},
{
  path: '/admin/import',
  component: () => import('@/views/ImportWizard.vue'),
  meta: { title: '数据导入', icon: 'Upload', roles: ['ADMIN'] }
}
// 弹窗式 FactEventDiff 不占路由
```

### 4.4 状态管理（Pinia）

| Store | 现有/v1.1 | 用途 |
|---|---|---|
| `auth.ts` | 沿用 | token + user + isAdmin |
| `notification.ts` | **新** | unreadCount + notificationList + markRead() + 30s 轮询 |

**`notification.ts` 设计**（避免引入 SSE，沿用 120s axios timeout 兼容）：
- `state`: `notifications: Notification[]` / `unreadCount: number` / `loading: boolean`
- `actions`: `fetchUnread()` / `markRead(id)` / `markAllRead()` / `startPolling()` / `stopPolling()`
- `startPolling()` 用 `setInterval(30_000)` 调 `GET /api/notifications?unread=true&size=10`，登录时启动，登出时停止

### 4.5 与现有 `Knowledge.vue` 关系

| 改造点 | 行为 |
|---|---|
| §13.1.1 置信度 3 级 | `Knowledge.vue` 显示答案时，新增"AI 推测" / "待确认" 徽章（**改造 < 30 行**） |
| §13.1.2 隐式切换 5 级 | `Knowledge.vue` 顶部"当前项目" pill 在切换时显示 hint 文案（**改造 < 20 行**） |
| `AgentStepsPanel` 增强 | 步骤面板新增 "switchHint" 字段显示（**改造 < 10 行**） |
| **不破坏** | 现有 `Knowledge.vue` 折叠/展开/历史/导出 markdown 功能（v1.0 I-12 完工） |

### 4.6 新增前端依赖

| 包 | 版本 | 用途 | 体积 |
|---|---|---|---|
| `pdfjs-dist` | ^4.0 | PDF 预览 (RI-41) | ~3MB |
| `mammoth` | ^1.7 | Word → HTML 转码 (RI-41) | ~200KB |
| `jsondiffpatch` | ^0.5 | JSON tree diff (RI-42) | ~50KB |
| `dayjs` | ^1.11 | 时间格式化 (RI-39 通知时间) | ~10KB |
| **合计** | | | **~3.3MB** (gzip ~1MB) |

**决策**：**不引** `element-plus` 全量升级（沿用 v1.0 2.8.4），**不引** `echarts` / `d3`（看板用 el-table 即可，避免 v1.0 Keep 清单的"超额设计"原则）

### 4.7 跟 Sisyphus 6 份分章架构的对应

| 改造 | Sisyphus 现有文档 | v1.1 增量要点 |
|---|---|---|
| `03-frontend-component-architecture.md §2 项目结构` | 13 views + 1 component | 变 18 views + 5 components |
| `03-frontend-component-architecture.md §3 组件树` | Layout + 11 路由 | 追加 5 路由节点 |
| `03-frontend-component-architecture.md §5 状态管理` | 仅 auth store | 追加 notification store |

---

## §5. 数据库扩展

### 5.1 新增表（5 张）

| 表 | 列数 | 主键 | FK | 索引 | 对应 RI |
|---|---|---|---|---|---|
| `notification` | 8 (id/user_id/type/title/content/link/read/created_at) | BIGINT AUTO | user_id | `idx_user_read` (user_id, read, created_at) | RI-39 |
| `failure_log` | 7 (id/path/failure_type/error_msg/stack_trace/occurred_at/resolved_at) | BIGINT AUTO | - | `idx_path_resolved` (path, resolved, occurred_at) | RI-37 |
| `user_role` | 3 (user_id/role_id/assigned_at) | (user_id, role_id) COMPOSITE | user/role | - | RI-34 |
| `project_member` | 4 (project_id/user_id/role_in_project/assigned_at) | (project_id, user_id) COMPOSITE | project/user | - | RI-34 |
| `import_batch` | 7 (id/type/total/success/failed/created_by/created_at) | BIGINT AUTO | user | `idx_type_created` | RI-44 |
| `import_error` | 5 (id/batch_id/row/column/error_msg) | BIGINT AUTO | batch_id | `idx_batch` | RI-44 |
| `proposal_series` | 4 (id/code/prefix/current_seq) | BIGINT AUTO | - | `uq_code` UNIQUE | RI-25 |

> 修正：6 张新增表（notification / failure_log / user_role / project_member / import_batch / import_error / proposal_series），共 7 张

### 5.2 ALTER TABLE 改动（7 张）

| 表 | 改动 | 列数 | 对应 RI | 零回归注 |
|---|---|---|---|---|
| `project` | +`deleted_at` / +`deleted_by` / +`version` / +`archive_status` 扩 | 13 → 16 | RI-31, RI-32, RI-33 | 默认 NULL，**不破** 现有查询 |
| `proposal` | +`condition_text` / +`condition_status` / +`condition_met_at` / +`deleted_at` / +`deleted_by` / +`version` / +`reserved_at` / +`released_at` | 7 → 15 | RI-24, RI-25, RI-31, RI-33 | `condition_status DEFAULT 'NONE'`（见 §0 风险 C-5） |
| `material` | +`deleted_at` / +`deleted_by` / +`version` / +`archived_at` | 12 → 16 | RI-31, RI-32, RI-33, RI-36 | 沿用 |
| `audit_log` | +`type` VARCHAR(32) / +`entity_subtype` VARCHAR(32) | 8 → 10 | RI-35 | 旧数据回填 SQL（见 §2.2.4） |
| `project_fact_event` | +`owner_id` / +`due_date` / +`resolved_at` / +`resolution_note` / +`confidence_level` | 8 → 13 | RI-22, RI-28 | 仍 INSERT-only，UPDATE 触发器白名单 |
| `business_term` | +`english_name` VARCHAR(128) | 7 → 8 | RI-43 | 可空 |
| `user` | +`sensitive_view_enabled` BOOLEAN DEFAULT false | 9 → 10 | RI-45 | 默认 false，admin 默认 true 由 seed 强制 |

### 5.3 索引策略汇总

| 表 | 索引 | 用途 |
|---|---|---|
| `notification` | `idx_user_read` (user_id, read, created_at) | 顶栏铃铛 30s 轮询 `WHERE user_id=? AND read=false ORDER BY created_at DESC LIMIT 10` |
| `failure_log` | `idx_path_resolved` (path, resolved, occurred_at) | admin 失败大盘筛选 |
| `project_fact_event` | 已有 `idx_project_type` (project_id, fact_type) 扩 `idx_owner_due` (owner_id, due_date) | 待处置事实查询 |
| `audit_log` | 已有 + `idx_type` (type, created_at) | 5 类审计筛选 |
| `import_batch` | `idx_type_created` (type, created_at) | 导入批次列表 |
| `import_error` | `idx_batch` (batch_id) | 错误详情 |
| `proposal_series` | `uq_code` UNIQUE | 编号唯一性 |
| `project` (扩) | 已有 `ft_name_cust` + `idx_status` (status) 扩 `idx_deleted_at` (deleted_at) | 软删过滤 |

### 5.4 迁移脚本命名（I-RI-N.sql）

| 迁移文件 | 对应 RI | 行数估 |
|---|---|---|
| `I-RI-22-confidence-3level.sql` | RI-22 | ~40 |
| `I-RI-23-implicit-switch-5level.sql` | RI-23 (无 DB 改动，仅 tool 内部) | ~10 |
| `I-RI-24-condition-status.sql` | RI-24 | ~50 |
| `I-RI-25-proposal-series-and-reserve.sql` | RI-25 | ~60 |
| `I-RI-26-network-dict-config.sql` | RI-26 (扩 dict 表) | ~30 |
| `I-RI-27-query-mysql-whitelist.sql` | RI-27 (无 DB 改动，仅 tool 配置) | ~10 |
| `I-RI-28-fact-event-fields.sql` | RI-28 | ~40 |
| `I-RI-29-mode-transition.sql` | RI-29 (无 DB 改动) | ~5 |
| `I-RI-30-extraction-failure-types.sql` | RI-30 (无 DB 改动，仅 enum) | ~5 |
| `I-RI-31-soft-delete.sql` | RI-31 | ~100 (7 个表 ALTER) |
| `I-RI-32-rollback.sql` | RI-32 (无新表，沿用 RI-31) | ~5 |
| `I-RI-33-optimistic-lock.sql` | RI-33 | ~30 |
| `I-RI-34-rbac-5-roles.sql` | RI-34 | ~80 |
| `I-RI-35-audit-type.sql` | RI-35 | ~40 |
| `I-RI-36-retention.sql` | RI-36 (无 DB 改动，沿用 RI-31 字段) | ~5 |
| `I-RI-37-failure-log.sql` | RI-37 | ~50 |
| `I-RI-38-board-view.sql` | RI-38 (无新表，沿用聚合) | ~5 |
| `I-RI-39-notification.sql` | RI-39 | ~50 |
| `I-RI-40-export-config.sql` | RI-40 (无新表) | ~5 |
| `I-RI-41-preview-config.sql` | RI-41 (无新表) | ~5 |
| `I-RI-42-diff-config.sql` | RI-42 (无新表) | ~5 |
| `I-RI-43-english-name.sql` | RI-43 | ~20 |
| `I-RI-44-import-batch.sql` | RI-44 | ~60 |
| `I-RI-45-masking.sql` | RI-45 | ~20 |
| **合计** | **23 个迁移文件** | **~730 行 SQL** |

**命名规范**：
- 前缀 `I-` 沿用 v1.0 Plan I 规范
- 中段 `RI-N` 跟 `docs/requirements/ARCH-DECOMPOSITION.md` 严格对齐
- 后段 `kebab-case` 简述
- **执行顺序**：数字顺序（I-RI-22 → I-RI-45），**并行规则**：无 FK 依赖的迁移可同批执行（如 I-RI-37 + I-RI-39 + I-RI-44 可并行），有 FK 的必须按序（I-RI-31 必须先于 I-RI-32/33/36）

### 5.5 DB 触发器（项目级）

| 触发器 | 表 | 触发时机 | 行为 | 对应 RI |
|---|---|---|---|---|
| `trg_fact_event_immutable` | `project_fact_event` | BEFORE UPDATE/DELETE | `SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'project_fact_event is INSERT-only'`（仅 4 字段白名单：owner_id/due_date/resolved_at/resolution_note 可改） | RI-22, RI-28 |

**应用层双保险**：`com.archive.entity.ProjectFactEvent` 用 `@EntityListeners(AuditingEntityListener.class)` + `@PreUpdate` / `@PreDelete` 检查 dirty field 是否在白名单，**不在白名单抛 `IllegalStateException`**（与 DB 触发器互为兜底）

### 5.6 跟 Sisyphus 6 份分章架构的对应

| 改造 | Sisyphus 现有文档 | v1.1 增量要点 |
|---|---|---|
| `04-database-schema.md §3 表清单` | 16 业务 + 2 基础设施 = 18 | 变 16 + 7 新 + 7 ALTER = 30 实体 |
| `04-database-schema.md §4 FULLTEXT 索引策略` | 3 FULLTEXT | 沿用（**不增** FULLTEXT） |
| `04-database-schema.md §5 迁移历史` | 6 迁移 | 追加 23 迁移 |

---

## §6. Agent 工具集扩展

### 6.1 决策：不新增 tool 数 / 改造 3 个 / 加 1 个

| # | 工具 | 改造 | 理由 | 零回归 |
|---|---|---|---|---|
| 1 | `find_project` | **改造**：第 4 级 LLM 兜底返回 confidence 后，走 §13.1.2 5 级判定（in-tool 内部，**不算 ReAct 步数**） | RI-23 | tool 签名不变（query, topN），**新增** 内部 `applyImplicitSwitchRule()` 方法 |
| 2 | `search_fulltext` | **沿用** | - | - |
| 3 | `query_mysql` | **改造**：加 5 类白名单 filters + 行数截断 + 数值上限（7 重加固） | RI-27 | 旧 SQL 路径保留，**新增** `MAX_RESULT_ROWS=1000` 常量 |
| 4 | `get_project_business_data` | **沿用** | - | - |
| 5 | `llm_summarize` | **沿用** | - | - |
| 6 | `ask_clarification` | **沿用** | - | - |
| **7 (新)** | `network_dict_lookup` | **新增**：调 §13.1.5 4 候选（v1.1 实施 2 候选），返回 `definition` + `source` | RI-26 | Spring 自动扫描 `@Component`，**不动** `AgentConfig` |

**工具总数**：6 → 7。ReAct `MAX_ITERATIONS=5` 保持硬编码不变（见 §0 风险 C-7）

### 6.2 Prompt / Few-shot 改写

#### 6.2.1 `AgentSystemPrompt.java` 追加 2 段

```text
[原 prompt 内容保持]

---

## 置信度 3 级 (RI-22)
当你抽取字段 / 总结事实时，confidence 字段请按 3 级标注：
- ≥ 0.85: CONFIRMED — 高置信，可直接入库
- 0.60 - 0.84: AI_INFERRED — 中置信，需 UI 标"AI 推测"
- < 0.60: PENDING_REVIEW — 低置信，标"待人工确认"

## 隐式项目切换 5 级判定 (RI-23)
当用户问的项目上下文（find_project 结果）跟当前 locked project 冲突时：
- conf ≥ 0.95 不同项目 → 自动切换
- conf 0.7-0.95 不同项目 → 反问用户 "是切到 X 吗?"
- conf 0.5-0.7 同项目 → hint "可能是同项目, 请确认"
- conf < 0.5 同项目 → 自动 inject "请重新确认"
- 都不命中 → 保持锁定
```

#### 6.2.2 `AgentFewShots.java` 追加 1 条

```text
[原 5 条 few-shot 保持]

---

### Few-shot 6: 英文术语查询 (RI-43)
用户: "vacant claim 是什么意思?"
  → Step 1: 调用 network_dict_lookup(query="空债权", source="baidu_baike")
  → Step 2: 返回中文定义 "空债权 = 借款人无财产可供执行的债权..."
  → Step 3: FINAL_ANSWER 翻译为 "vacant claim（空债权）= 借款人无财产可供执行的债权"
```

### 6.3 降级路径（5 层 → 5 层，不变）

| 层 | 失败 | 降级 |
|---|---|---|
| 1 | Tool call 失败 | 返回错误 observation，LLM 下一步用其他 tool |
| 2 | LLM 解析失败 | 重试 1 次不同 prompt，仍失败 → 业务降级 |
| 3 | LLM API 挂 | 走老 GlmService 路径（`agentMode=false`） |
| 4 | Agent 框架挂 | `QaController.legacyAsk()` 老 3 步管道 |
| 5 | DB / FULLTEXT 挂 | 返回"系统维护中" |

**v1.1 增量点**：
- `NetworkDictLookupTool` 内部加 6 层降级（工具级）：**API 失败 → 试下个候选 → 全失败 → 返回 `{found: false, reason: 'INTRANET_BLOCKED'}`**（**不抛异常**，业务层降级）
- `QueryMysqlTool` 加行数截断降级：`result.size() > 1000` → 截断 + 提示"请缩小范围"
- 写 `failure_log` (RI-37)

### 6.4 工具调用审计

| 工具 | 审计场景 | 字段 |
|---|---|---|
| `find_project` | 隐式切换 hint 注入 | `audit_log.type=WRITE, entity_type=PROJECT, action=IMPLICIT_SWITCH` |
| `query_mysql` | 跨项目聚合 + filters 白名单校验 | `audit_log.type=WRITE, entity_type=QUERY, action=AGGREGATE` |
| `network_dict_lookup` | 字典来源 + 命中 | `audit_log.type=LLM, entity_type=DICT, action=LOOKUP` |
| `llm_summarize` | 已有 | 沿用 |

**统一**：`AuditLogService.logToolCall(toolName, args, result, durationMs)` 在 `AgentEngine.run()` 每个 tool 调完后调一次

### 6.5 性能影响

| 维度 | v1.0 | v1.1 | 备注 |
|---|---|---|---|
| 单次 Agent LLM 调 | 2-5 次 | 2-5 次 | 不变（5 级判定走 tool 内部，**不算步数**） |
| 单次响应时间 | < 30s | < 30s | 不变（5 步上限） |
| 月度 LLM 调用 | ~5000 次 | ~5500 次 | +500 次（10% 来自 network_dict_lookup） |
| 智谱 API 限速 60 req/min | 不超 | 不超 | 沿用 |
| 内存峰值 | ~1.2GB | ~1.4GB | +200MB（Notification 内存缓存 + Diff 计算） |

### 6.6 跟 Sisyphus 6 份分章架构的对应

| 改造 | Sisyphus 现有文档 | v1.1 增量要点 |
|---|---|---|
| `01-arch-overview.md §1 核心能力` | "5 步 ReAct 循环 + 6 个工具" | 变 "5 步 + **7 个工具**（含 network_dict_lookup）" |
| `01-arch-overview.md §4 后端分层` | "Agent 层 (Plan I 智能问答)" | "Agent 层 14 → 15 文件" |
| `01-arch-overview.md §7 注意事项` | "Agent 内存问题 / 流式未实现" | 追加 "AgentTools 7 重加固 / 降级 6 层 / 工具级审计" |
| `02-backend-layer-architecture.md §5 Agent 包` | 14 文件 | 15 文件 + 1 工具 + Prompt 改写 |

---

## §7. 部署 & 配置扩展

### 7.1 `application.yml` 新增（5 段，纯增量）

```yaml
# === v1.1 新增 (零回归: 全部新 key, 沿用默认值) ===

# RI-22 置信度阈值
archive:
  extraction:
    confidence:
      auto-confirm: 0.85
      ai-inferred-min: 0.60
      pending-review-max: 0.60

  # RI-23 隐式切换 5 级
  implicit-switch:
    same-project-conf-high: 0.7
    same-project-conf-low: 0.5
    diff-project-conf-high: 0.95
    diff-project-conf-low: 0.7

  # RI-25 编号生成
  proposal:
    reserve-ttl-hours: 24

  # RI-26 网络查字典
  network-dict:
    enabled: true
    timeout-ms: 3000
    retry-on-empty: true
    sources:
      baidu-baike:
        enabled: true
        priority: 1
        url: https://baike.baidu.com/api/openapi/BaikeLemmaCardApi
        api-key: ${BAIDU_BAIKE_API_KEY:}
      wikipedia-zh:
        enabled: true
        priority: 2
        url: https://zh.wikipedia.org/w/api.php
        api-key:
      jinrong-baike:
        enabled: false  # v1.1 灰度: 待业务方提供 URL
        priority: 3
        url:
        api-key:
      hudong-baike:
        enabled: false  # v1.1 灰度: 见 C-2
        priority: 4
        url:
        api-key:

  # RI-27 跨项目白名单
  query-mysql:
    max-result-rows: 1000
    max-amount: 100000000
    allowed-filter-keys: region,industry,stage,fact_type,time_bucket

  # RI-31 软删
  soft-delete:
    recycle-bin-days: 30

  # RI-33 乐观锁 (单用户系统 v1.1 不强制)
  optimistic-lock:
    strict: false

  # RI-36 数据生命周期
  retention:
    archive-after-days: 365
    long-term-archive-years: 5

  # RI-39 通知
  notification:
    poll-interval-seconds: 30
    bell-cache-size: 50

  # RI-40 导出
  export:
    pdf:
      page-size: A4
      margin-pt: 36
    excel:
      max-rows: 100000

# Spring AI 已有 (沿用 v1.0)
spring:
  ai:
    agent:
      enabled: true
      find-project:
        loop-trigger-threshold: 1
        llm-fallback-max-total: 300
```

### 7.2 `config.json` 模板新增（5 段，参考 `config/config.json.template`）

```json
{
  "v11": {
    "baidu_baike_api_key": "<从百度 AI 开放平台申请, 内网可访问则填, 否则留空降级>",
    "baidu_baike_url": "https://baike.baidu.com/api/openapi/BaikeLemmaCardApi",
    "notification_poll_interval_seconds": 30,
    "recycle_bin_days": 30,
    "pdf_export_enabled": true,
    "excel_export_enabled": true,
    "import_enabled_admin_only": true
  }
}
```

### 7.3 灰度策略

| 维度 | v1.1 灰度方案 | 切换到 v1.1 全量 |
|---|---|---|
| RBAC 5 角色 | 双轨：旧 `user.role_id` (admin/user) + 新 `user_role` 多对多；前端按 `user_role` 优先，admin 登录路径不变 | v2 多用户时强制迁移到 `user_role` |
| 软删 | 默认开启；`MaterialController.delete()` 软删 + admin 物理删 `/purge` 端点 | - |
| 乐观锁 | `archive.optimistic-lock.strict: false`（v1.1 期仅记录日志，不强制失败） | v2 多用户时改为 `true` |
| 编号预留 | 默认开启；`proposal.status=draft_reserved` | - |
| 网络查字典 | 默认开启；2 候选（百度 + 维基），金融/互动百科 `enabled: false` 留占位 | 业务方提供金融百科 URL 后启用 |
| 通知中心 | 默认开启；30s 轮询（**不引** SSE） | 未来可改 SSE（**保持 Keep 清单"不引 SSE"**） |
| 数据脱敏 | 默认开启；委员默认 `sensitive_view_enabled=false`，admin=true | - |
| 看板 | 默认开启 | - |
| 导出 | 默认开启；admin 全权限 | - |
| 导入 | 默认 admin only | 业务方要求时放开 |

### 7.4 部署清单

| 改动 | 步骤 | 零回归 |
|---|---|---|
| 后端 jar 增量 | < 30MB（Spring AI 15MB + OpenPDF 8MB + POI 5MB + jsondiffpatch 0.05MB） | 沿用 WinSW + startup.ps1 |
| 前端 dist 增量 | < 5MB | Vite 5 build，**不动** nginx/caddy 配置 |
| 数据库 | 23 个迁移文件按序执行 | 沿用 MySQL 8.0.16 |
| 配置文件 | yml + config.json 加 5 段 | 旧 key 全部保留 |
| **新硬件** | **无** | 沿用 4 核 16GB |
| **新服务** | **无** | 沿用 Caddy + Spring Boot + MySQL |
| **新外网** | 仅百度 + 维基 2 个 HTTPS (RI-26 灰度) | 内网出站 60 req/min 配额 |

### 7.5 跟 Sisyphus 6 份分章架构的对应

| 改造 | Sisyphus 现有文档 | v1.1 增量要点 |
|---|---|---|
| `05-deployment-and-environment.md §2 软件依赖` | Java 17 / MySQL 8 / Node 20 / Caddy / WinSW | 追加 OpenPDF 2.0.2 / POI 5.2.5 (Maven) + pdfjs-dist 4 / mammoth 1.7 (npm) |
| `05-deployment-and-environment.md §3 网络拓扑` | 出站到智谱 GLM | 追加出站到百度百科 + 维基 (HTTPS 443, 灰度) |
| `05-deployment-and-environment.md §4 部署流程` | startup.ps1 + WinSW | 沿用（**不破**） |

---

## 附录 A 跟现有 RI-1~45 的对应关系表

> 每条 v1.1 RI 严格映射到 `docs/requirements/ARCH-DECOMPOSITION.md` RI-22~45（Mavis 已拆解 24 条），本表说明：(1) 跟 RI-1~45 旧拆解的合并/独立关系 (2) 数据库 schema 改动 vs 业务 Service 改动 vs Agent 工具改动的归类

| v1.1 RI | §13 引用 | 归类 | 与 RI-1~45 关系 | 跟现有代码的"碰撞点" | 估时 (BE/FE/测) |
|---|---|---|---|---|---|
| **RI-22 置信度 3 级** | §13.1.1 | DB ALTER + Engine 改 | **独立**（不复用旧 RI） | `engine.ExtractionEngine` 改 onConfidence 回调 | 1.5 / 0.5 / 0.5 |
| **RI-23 隐式切换 5 级** | §13.1.2 | Agent 改 + Tool 改 | **独立** | `tool.FindProjectTool` 改 4 级 → 5 级（in-tool） | 1.0 / 0.3 / 0.5 |
| **RI-24 决议变更 + 附条件** | §13.1.3 | DB ALTER + Service 改 | **独立**（扩 RI-15） | `entity.Proposal` +3 列 / `ProposalService` 改 | 1.5 / 1.0 / 0.5 |
| **RI-25 编号预留/撤销/改系列** | §13.1.4 | DB 新表 + Service 改 | **独立**（扩 RI-17） | 新 `proposal_series` 表 + `ProposalNumberGenerator` 重写 | 2.0 / 0.5 / 0.5 |
| **RI-26 网络查 API 字典** | §13.1.5 | DB 扩 + 新 Service + 新 Tool | **独立**（扩 RI-12） | 新 `NetworkDictService` + 新 `NetworkDictLookupTool` | 2.0 / 0.5 / 1.0 |
| **RI-27 批量工具白名单** | §13.1.6 | Agent 改 | **独立**（扩 RI-27 = 自身） | `QueryMysqlTool` 加 3 重加固 | 0.5 / 0 / 0.5 |
| **RI-28 事实事件流字段** | §13.1.7 | DB ALTER + 新 Service | **独立**（扩 RI-7） | `entity.ProjectFactEvent` +4 列 + 新 Controller | 1.0 / 0.5 / 0.5 |
| **RI-29 主页双模动画** | §13.1.8 | 前端改 | **独立** | `views/Dashboard.vue` 改 transition | 0 / 0.5 / 0.2 |
| **RI-30 LLM 抽失败兜底** | §13.1.9 | Service 改 | **独立** | `GlmService` 改 FailureType / `ExtractionEngine` 改 onFailure | 1.0 / 0.5 / 0.5 |
| **RI-31 软删 + 回收站** | §13.1.10 | DB ALTER(5 表) + Service 改 | **独立**（跨 RI-14/15/16/18） | 5 表 ALTER + 新 `RecycleBinService` + 新 `RecycleBinController` | 2.0 / 1.0 / 1.0 |
| **RI-32 撤销/回滚** | §13.2.1 | DB ALTER(沿用 RI-31) + Service 改 | **合并到 RI-31** | 同 RI-31 | 1.0 / 0.5 / 0.3 |
| **RI-33 乐观锁** | §13.2.2 | DB ALTER(3 表) + Service 改 | **独立** | 3 表 ALTER + `version` 字段 + `@Version` 注解 | 1.0 / 0 / 0.5 |
| **RI-34 RBAC 5 角色** | §13.2.3 | DB 新表(2) + ALTER + Service 改 | **独立**（扩 RI-2） | 新 `user_role` + `project_member` + 4 role 行 + `RbacService` | 3.0 / 1.5 / 1.0 |
| **RI-35 审计加强** | §13.2.4 | DB ALTER + Service 改 | **独立**（扩 RI-19） | `audit_log` ALTER + 5 类写入方法 | 1.0 / 0.5 / 0.5 |
| **RI-36 数据生命周期** | §13.2.5 | 沿用 RI-31 + 新 Service | **合并到 RI-31** | 同 RI-31 | 0.5 / 0 / 0.3 |
| **RI-37 失败兜底全景** | §13.2.6 | DB 新表 + AOP 改 | **独立** | 新 `failure_log` + AOP 拦截 10 路径 | 2.0 / 0.5 / 1.0 |
| **RI-38 项目看板** | §13.3.1 | Service 改 + 新 View | **独立** | 新 `ProjectBoardService` + 新 `ProjectBoard.vue` | 1.5 / 2.0 / 0.5 |
| **RI-39 站内通知中心** | §13.3.2 | DB 新表 + 新 Service + 新 View | **独立** | 新 `notification` 表 + 新 `NotificationService` + 新 `Notification.vue` + 新 Pinia store | 2.5 / 2.0 / 1.0 |
| **RI-40 数据导出** | §13.3.3 | Service 改 + pom 加依赖 | **独立** | 新 `ExportService` (OpenPDF + POI) + 2 新端点 | 2.0 / 1.0 / 0.5 |
| **RI-41 附件预览** | §13.3.4 | Service 改 + 新 Component | **独立** | 新 `PreviewService` + 新 `PreviewFrame.vue` (pdf.js + mammoth) | 1.5 / 2.0 / 0.5 |
| **RI-42 关键事实变更对比** | §13.3.5 | Service 改 + 新 Component | **独立** | 新 `DiffViewer.vue` (jsondiffpatch) + `ProjectFactEventService.getDiff()` | 1.0 / 1.5 / 0.5 |
| **RI-43 业务术语中英对照** | §13.3.6 | DB ALTER + 前端改 + Prompt 改 | **独立** | `business_term` ALTER + 1 few-shot | 0.3 / 0.3 / 0.2 |
| **RI-44 旧系统 Excel 导入** | §13.3.7 | DB 新表(2) + 新 Service | **独立** | 新 `import_batch` + `import_error` + `ImportService` + `ImportWizard.vue` | 2.5 / 1.5 / 1.0 |
| **RI-45 数据脱敏视图** | §13.3.8 | DB ALTER + Service 改 + 前端改 | **独立** | `user` ALTER + 新 `MaskingService` + 新 `MaskedField.vue` | 1.5 / 1.0 / 0.5 |
| **合计** | **24 条 RI** | | | | **~33 人天**（BE 17 / FE 17 / 测 ~12，**3 路并行**） |

**关键路径**：
- RI-31（软删，5 表 ALTER）→ RI-32, RI-33, RI-36（都依赖 soft_delete 字段）
- RI-34（RBAC 5 角色）→ RI-45（脱敏视图，依赖 user_role）
- RI-25（编号生成器重写）→ RI-24（附条件决议，依赖 proposal_series 索引）

**可并行机会（5 路）**：
1. **A 路** Agent 增强：RI-22 + RI-23 + RI-27 + RI-30 + RI-43（**全改 agent/ 包**，无 DB ALTER 风险）
2. **B 路** 软删 + 审计 + 失败日志：RI-31 + RI-33 + RI-35 + RI-37（**全改 entity ALTER + 新 service**）
3. **C 路** RBAC + 脱敏：RI-34 + RI-45（**改 security + user**）
4. **D 路** 通知 + 看板：RI-38 + RI-39（**新 View + 新 Pinia**）
5. **E 路** 导出 + 预览 + 导入：RI-40 + RI-41 + RI-44（**新 Component + 第三方依赖**）

---

## 文档元信息

- **作者**：架构师（Mavis 沙箱 t1-arch-extended 任务，2026-06-11）
- **基线 commit**：`7aa7bae`（Sisyphus 分章架构 v1.0 + README 重构）
- **配套文档**：
  - 上游：`docs/requirements/REQUIREMENTS.md` §13 + `docs/requirements/ARCH-DECOMPOSITION.md` RI-22~45
  - 现有基线：`docs/architecture/01~06-arch-*.md` + `docs/ARCHITECTURE-v2.md` + `docs/DB-SCHEMA-v2.md` + `docs/AGENT-IMPL-PLAN.md` + `docs/AGENT-FRAMEWORK-DECISION.md`
  - 踩坑：`docs/LESSONS-LEARNED.md` §6.5（Plan I Agent 类）
  - 下游（待写）：`docs/architecture/07-v1.1-extended-arch.md`（Sisyphus 风格同步到 6 份分章时引用本文件）
- **下游产出**：
  - T2 `refactor-and-fix-list.md`（基于本文件 §2/§3/§4/§5 列改造点）
  - T3 `tasks-v1.1.md`（基于本文件附录 A 列并行任务表 + RI-46~N 拆解）
- **零回归承诺**：v1.0 任何 .java / .vue / .sql / pom 行为不破坏；本文件所有"改造"明确写"零回归注"
- **维护规则**：本文件修改后立即 `git add + commit + push`（沿 README §7.3）

---

*架构师在沙箱出方案,接手 AI 拿本文件做实施,Mavis 审完工。*

*本文档 v1.1 是对 Sisyphus 现代化分章架构 (01~06) 的 v1.1 增量补丁,非另起炉灶。*
