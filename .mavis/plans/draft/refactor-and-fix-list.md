# v1.1 重构与维修清单（T2）

> 撰写人：投委会档案项目 PM | 日期：2026-06-11
> 上游输入：`.mavis/plans/draft/architecture-v1.1-extended.md`（T1，架构师 da17c92）
> 下游输出：`.mavis/plans/draft/tasks-v1.1.md`（T3，并行任务表）
> 范围：仅列"为支撑 v1.1 增量必须先修/先重做的旧代码"。**不重写已完工的 v1.0 业务**，不重复拆 RI-N（见 `docs/requirements/ARCH-DECOMPOSITION.md` RI-1~45 现有拆解）

---

## §1 后端重构点

### 1.1 Agent 工具包（`com.archive.agent`）

| # | 文件:行号 | 现状 | 改造理由 | 改造后预期 | 阻塞 RI | 优先级 |
|---|---|---|---|---|---|---|
| BE-R-01 | `tool/QueryMysqlTool.java:14-322` (整文件 322 行) | 4 重加固：`group_by` / `is_not_null` / `MAX_IN_VALUES=50` / `escapeLikePattern`（LESSONS-LEARNED §6.5.2 + sisyphus-code-review-2026-06-10 已记录） | v1.1 RI-27 要 7 重加固：增 3 重 = 5 类白名单 filters（region/industry/stage/fact_type/time_bucket）+ 行数截断 `MAX_RESULT_ROWS=1000` + 数值上限 `MAX_AMOUNT=1e8` | 工具签名加可选 `filters: Map<String,String>` 参数，**不破** 现有 query() 调用 | RI-27 | **P0** |
| BE-R-02 | `tool/FindProjectTool.java:23-162` (整文件) | 4 级兜底链（精查 → 模糊 → 客户名 → LLM 兜底），置信度二元判定（≥0.95 同 / <0.95 问用户） | RI-23 要 5 级隐式切换判定（同 projectCode 3 档 + 不同 projectCode 2 档） | 在第 4 级 LLM 兜底返回 confidence 后**内部**走 5 级判定（**不算 ReAct 步数**） | RI-23 | **P0** |
| BE-R-03 | `AgentEngine.java:27` | `private static final int MAX_ITERATIONS = 5;` 硬编码 | 不可配（决策层 T1 §0 C-7） | **保持硬编码**，5 级判定走 tool 内部不破 5 步 | — | **不开**（已决策不重构） |
| BE-R-04 | `prompt/AgentSystemPrompt.java` (整文件) | prompt 含基础指令 + Few-shot 引用 | RI-22/RI-23 需追加 2 段说明（3 级置信度 + 5 级隐式切换判定规则） | 现有 prompt 模板保留，**追加** 2 段（见 T1 §6.2.1） | RI-22, RI-23 | **P0** |
| BE-R-05 | `prompt/AgentFewShots.java` | 5 条 few-shot | RI-43 需加 1 条英文术语查询 few-shot | 现有 5 条保留，**追加** 第 6 条 | RI-43 | P1 |
| BE-R-06 | `AgentContext.java:28` (类) | 含 question / lockedProjectCode / history | RI-23 要增 `lastSwitchDecision: enum SAME_PROBABLY/DIFFERENT_PROBABLY/UNCLEAR` | 新增字段 + getter/setter，**不破** 现有序列化 | RI-23 | P1 |
| BE-R-07 | `AgentResponse.java:21` (类) | 含 answer / steps / sources / agentMode | RI-22 增可空 `projectSwitchHint: String`，RI-23 增可空 `confidenceBadge: String` | 新增 2 可空字段，**不破** JSON 兼容 | RI-22, RI-23 | P1 |
| BE-R-08 | `AgentConfig.java:79` (类) | ChatClient + 6 工具 bean | RI-26 新增 `NetworkDictLookupTool`，自动扫 @Component，**不动** AgentConfig | — | — | 无需改 |

### 1.2 Controller-Service 层

| # | 文件:行号 | 现状 | 改造理由 | 改造后预期 | 阻塞 RI | 优先级 |
|---|---|---|---|---|---|---|
| BE-R-09 | `controller/QaController.java` | `ask()` 单端点 | RI-22/RI-23 改造（不写新端点，仅改响应体加 2 可空字段） | 已有响应体加 `projectSwitchHint` / `confidenceBadge`（nullable） | RI-22, RI-23 | P1 |
| BE-R-10 | `controller/ProjectController.java` | `POST /api/projects` 单端点 | RI-30 失败时响应 `ExtractionFailureResponse` 5 种 failureType | 旧响应 `success: false` 保留，**新增** `failureType` 字段 | RI-30 | P1 |
| BE-R-11 | `controller/ProjectController.java` (DELETE 路径) | `delete()` 物理删 | RI-31 改软删（status=deleted, deleted_at=now, deleted_by=user） | 旧物理删路径移到 `/api/admin/projects/{id}/purge` (admin only) | RI-31 | **P0** |
| BE-R-12 | `controller/ProjectController.java` (POST 路径) | 无 rollback 端点 | RI-32 增 `POST /api/projects/{id}/rollback` body=`{targetVersion: 3}` | 新增端点 | RI-32 | P1 |
| BE-R-13 | `controller/MaterialController.java` (DELETE 路径) | `delete()` 物理删 | RI-31 改软删 | 旧物理删路径移到 `/api/admin/materials/{id}/purge` (admin only) | RI-31 | **P0** |
| BE-R-14 | `controller/ProposalController.java` (PUT 路径) | `updateDecision()` 改 meeting_result | RI-24 增 "已开投委会不可改" 校验 + `condition_status` 跟踪 + RI-25 增 revoke / changeSeries / reserve / release | 旧 PATCH 路径保留（仅改 meeting_result），**新增** PATCH `/api/proposals/{id}/condition` + POST `/api/proposals/reserve` + POST `/api/proposals/{id}/revoke` | RI-24, RI-25 | **P0** |
| BE-R-15 | `controller/AuditLogController.java` | `GET /api/audit-logs` | RI-35 增 `?type=WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM` 过滤 | 加 query param，**不破** 旧无参调用 | RI-35 | P1 |
| BE-R-16 | `controller/MaterialController.java` (GET 路径) | `GET /api/materials/{id}` 返回文件 | RI-41 增 `GET /api/materials/{id}/preview?version=3` 返回流 | 新增端点，**不破** 旧 download | RI-41 | P1 |
| BE-R-17 | `controller/ProjectController.java` (GET 路径) | `GET /api/projects/{id}` | RI-45 响应体增 `masked: boolean` + `unmaskRequestUrl: String?` | 新增 2 可空字段，**不破** 旧调用 | RI-45 | P1 |
| BE-R-18 | `service/GlmService.java` | 现有 try-catch 通用 | RI-30 扩 `callWithLog()` 异常分类 5 种（API失败/非JSON/字段缺失/字段值异常/parse失败） | 新增 `FailureType` 枚举 + 细化 catch | RI-30 | P1 |
| BE-R-19 | `engine/ExtractionEngine.java` | onSuccess / onFailure 通用回调 | RI-30 改 `onFailure(FailureType)` 接 5 种类型 | callback 签名扩 enum 参数 | RI-30 | P1 |
| BE-R-20 | `engine/TriggerEngine.java` | 触发 todo 规则 | RI-24 增 "condition_status=met → 自动 trigger todo" 规则 | 加 1 条规则分支 | RI-24 | P1 |
| BE-R-21 | `service/AuditLogService.java` | logWrite / logLogin | RI-35 增 3 类方法 `logSensitiveView()` / `logExport()` / `logToolCall()` | 新增方法，旧 API 保留 | RI-35 | P1 |

### 1.3 实体与 Repository

| # | 文件:行号 | 现状 | 改造理由 | 改造后预期 | 阻塞 RI | 优先级 |
|---|---|---|---|---|---|---|
| BE-R-22 | `entity/Project.java` | 13 字段 | RI-31/32/33 ALTER +3 字段（deleted_at / deleted_by / version / archive_status） | 增 4 字段含 @Version 乐观锁 | RI-31, RI-32, RI-33 | **P0** |
| BE-R-23 | `entity/Proposal.java` | 7 字段 | RI-24/25/31/33 ALTER +8 字段（condition_text/condition_status/condition_met_at/deleted_at/deleted_by/version/reserved_at/released_at） | 增 8 字段 | RI-24, RI-25, RI-31, RI-33 | **P0** |
| BE-R-24 | `entity/Material.java` | 12 字段 | RI-31/32/33/36 ALTER +4 字段（deleted_at/deleted_by/version/archived_at） | 增 4 字段 | RI-31, RI-32, RI-33, RI-36 | **P0** |
| BE-R-25 | `entity/AuditLog.java` | 8 字段 | RI-35 ALTER +2 字段（type / entity_subtype） | 增 2 字段 | RI-35 | P1 |
| BE-R-26 | `entity/ProjectFactEvent.java` | 8 字段 | RI-22/28 ALTER +5 字段（owner_id/due_date/resolved_at/resolution_note/confidence_level） | 增 5 字段 | RI-22, RI-28 | P1 |
| BE-R-27 | `entity/BusinessTerm.java` | 7 字段 | RI-43 ALTER +1 字段（english_name） | 增 1 可空字段 | RI-43 | P1 |
| BE-R-28 | `entity/User.java` | 9 字段 | RI-45 ALTER +1 字段（sensitive_view_enabled） | 增 1 字段（默认 false，admin seed 强制 true） | RI-45 | P1 |
| BE-R-29 | `entity/ProjectFactEvent.java` | INSERT-only 业务约束 | RI-22/28 DB 触发器锁死 + EntityListener 拦截 | 加 `@PreUpdate` / `@PreDelete` 拦截 + 触发器 SQL | RI-22, RI-28 | P1 |
| BE-R-30 | `repository/ProjectRepository.java` | findAll(Pageable) + 现有方法 | RI-38 看板增 `findBoardView()` 聚合 JPQL | 加 @Query 方法 | RI-38 | P1 |
| BE-R-31 | `repository/ProjectFactEventRepository.java` | 现有方法 | RI-28 增 `findByStatusAndDueDateBefore()` | 加 1 方法 | RI-28 | P1 |
| BE-R-32 | `service/ProjectService.java` (UPDATE 路径) | 直接 save() | RI-33 乐观锁 `@Version` 自动 + 异常处理 | `OptimisticLockException → 409` | RI-33 | P1 |
| BE-R-33 | `common/GlobalExceptionHandler.java` | 现有 | RI-33 增 `OptimisticLockException → ApiResponse.fail(409, '数据已被他人修改')` | 加 1 handler | RI-33 | P1 |
| BE-R-34 | `entity/Role.java` | 预置 admin/user 2 行 | RI-34 扩 4 行 + 改 FK 引用方式 | INSERT 4 新 role + 双轨（user.role_id 兼容 + user_role 多对多） | RI-34 | **P0** |
| BE-R-35 | `security/SecurityConfig.java` | 现有 JWT + BCrypt | RI-34 5 角色 @PreAuthorize / JwtAuthFilter userRoles claim | 加 @PreAuthorize 注解 + JwtAuthFilter 加 claim | RI-34 | P1 |
| BE-R-36 | `pom.xml` | 现有依赖 | RI-40 增 OpenPDF 2.0.2 / RI-40 增 Apache POI 5.2.5 | 加 2 依赖（**纯 jar 增量 < 10MB**） | RI-40 | P1 |

### 1.4 配置 & 工具类

| # | 文件:行号 | 现状 | 改造理由 | 改造后预期 | 阻塞 RI | 优先级 |
|---|---|---|---|---|----|---|
| BE-R-37 | `application.yml` | 现有 | 7 处新 key（v1.1 RI-22~45 各 1 段，合计 5 段） | 见 T1 §7.1 | 全部 RI | P1 |
| BE-R-38 | `config.json` 模板 | 现有 | 5 字段扩（network-dict / query-mysql / optimistic-lock / retention / audit） | 改模板 + README 同步 | 全部 RI | P1 |
| BE-R-39 | `common/FailureType.java` (新文件) | 不存在 | RI-30 5 种 failureType 枚举 | 新建 enum（5 值） | RI-30 | P1 |
| BE-R-40 | `common/ConfidenceLevel.java` (新文件) | 不存在 | RI-22 3 级置信度枚举 | 新建 enum（CONFIRMED/AI_INFERRED/PENDING_REVIEW） | RI-22 | P1 |
| BE-R-41 | `common/SwitchDecision.java` (新文件) | 不存在 | RI-23 5 级隐式切换判定 | 新建 enum（SAME_PROBABLY/.../UNCLEAR） | RI-23 | P1 |

---

## §2 前端重构点

| # | 文件 | 现状 | 改造 | 阻塞 RI | 优先级 |
|---|---|---|---|---|---|
| FE-R-01 | `views/Knowledge.vue` | I-12 完工，含 AgentStepsPanel 折叠 | RI-22 加置信度徽章 / RI-23 加切换 hint 文案（**改造 < 50 行**） | RI-22, RI-23 | P1 |
| FE-R-02 | `views/ProjectDetail.vue` | 现有事实 tab | RI-42 加事实变更对比弹窗（用 DiffViewer 组件） | RI-42 | P1 |
| FE-R-03 | `views/ProjectList.vue` | 现有列表 | RI-38 加"项目看板"入口按钮 | RI-38 | P1 |
| FE-R-04 | `views/Layout.vue` | 顶栏含 logo + 退出 | RI-39 加 NotifBell 顶栏铃铛 + 未读数 badge | RI-39 | P1 |
| FE-R-05 | `views/ProjectForm.vue` | 表单 | RI-30 失败时顶部 banner + 重试按钮 | RI-30 | P1 |
| FE-R-06 | `views/AdminDict.vue` | 术语 tab | RI-43 增英文名列 | RI-43 | P1 |
| FE-R-07 | `views/Dashboard.vue` | 现有 | RI-29 300ms CSS transition 双模动画 | RI-29 | P1 |
| FE-R-08 | `package.json` | 现有依赖 | RI-41 增 pdfjs-dist ^4.0 + mammoth ^1.7 / RI-42 增 jsondiffpatch ^0.5 / RI-39 增 dayjs ^1.11 | RI-39, RI-41, RI-42 | P1 |
| FE-R-09 | `router/index.ts` | 11 路由 | RI-38/39/31/44 增 4 路由（projects/board / notifications / recycle-bin / admin/import） | RI-31, RI-38, RI-39, RI-44 | P1 |
| FE-R-10 | `store/notification.ts` (新文件) | 不存在 | RI-39 全局通知 store（unreadCount + 30s 轮询） | RI-39 | P1 |
| FE-R-11 | `api/notification.ts` (新文件) | 不存在 | RI-39 通知 API 封装 | RI-39 | P1 |
| FE-R-12 | `api/archive.ts` | 现有 | 增 ~30 个端点（board/notif/import/export/preview/diff/recycle-bin/fact-events） | RI-28, RI-31, RI-38, RI-39, RI-40, RI-41, RI-42, RI-44 | P1 |

---

## §3 数据库迁移修补

| # | 文件 | 现状 | 改造 | 阻塞 RI | 优先级 |
|---|---|---|---|---|---|
| DB-R-01 | `init.sql` (重建脚本) | 含 18 表 | 7 ALTER 段必须 append 到对应表 + 7 新表 CREATE（见 T1 §5.1 / §5.2） | RI-22~45 | **P0**（阻塞全部） |
| DB-R-02 | `db/migration/I-RI-22-confidence-3level.sql` (新) | 不存在 | 增 `project_fact.confidence_level` + `project_fact_event.confidence_level` + 回填 SQL | RI-22 | **P0** |
| DB-R-03 | `db/migration/I-RI-24-condition-status.sql` (新) | 不存在 | 增 `proposal.condition_text/condition_status/condition_met_at` | RI-24 | **P0** |
| DB-R-04 | `db/migration/I-RI-25-proposal-series.sql` (新) | 不存在 | 建 `proposal_series` 表 + 增 `proposal.reserved_at/released_at` | RI-25 | P1 |
| DB-R-05 | `db/migration/I-RI-28-fact-event-fields.sql` (新) | 不存在 | 增 `project_fact_event.owner_id/due_date/resolved_at/resolution_note` + DB 触发器 | RI-28 | P1 |
| DB-R-06 | `db/migration/I-RI-31-soft-delete.sql` (新) | 不存在 | 7 表 ALTER 加 `deleted_at/deleted_by` + `status` 扩枚举 + `project_fact_event` 触发器 | RI-31 | **P0** |
| DB-R-07 | `db/migration/I-RI-33-optimistic-lock.sql` (新) | 不存在 | 3 表 ALTER 加 `version` INT DEFAULT 1 | RI-33 | P1 |
| DB-R-08 | `db/migration/I-RI-34-rbac-5-roles.sql` (新) | 不存在 | 建 `user_role` / `project_member` 表 + INSERT 4 新 role 行 | RI-34 | **P0** |
| DB-R-09 | `db/migration/I-RI-35-audit-type.sql` (新) | 不存在 | 增 `audit_log.type/entity_subtype` + 回填 SQL | RI-35 | P1 |
| DB-R-10 | `db/migration/I-RI-37-failure-log.sql` (新) | 不存在 | 建 `failure_log` 表 | RI-37 | P1 |
| DB-R-11 | `db/migration/I-RI-39-notification.sql` (新) | 不存在 | 建 `notification` 表 + 索引 | RI-39 | P1 |
| DB-R-12 | `db/migration/I-RI-43-english-name.sql` (新) | 不存在 | 增 `business_term.english_name` | RI-43 | P1 |
| DB-R-13 | `db/migration/I-RI-44-import-batch.sql` (新) | 不存在 | 建 `import_batch` / `import_error` 表 | RI-44 | P1 |
| DB-R-14 | `db/migration/I-RI-45-masking.sql` (新) | 不存在 | 增 `user.sensitive_view_enabled` | RI-45 | P1 |

**说明**：T1 §5.4 已列 23 个迁移文件名建议，本表只标**P0 阻塞**的 4 个（DB-R-02/03/06/08）。其余按 RI 优先级随业务改造点并行做。

---

## §4 配置重构

| # | 文件 | 现状 | 改造 | 阻塞 RI | 优先级 |
|---|---|---|---|---|---|
| CFG-R-01 | `config/config.example.json` | 现有模板 | 扩 5 字段（network-dict / query-mysql / optimistic-lock / retention / audit） | 全部 RI | P1 |
| CFG-R-02 | `config/README.md` | 现有 | 增 v1.1 配置项说明 | 全部 RI | P2 |
| CFG-R-03 | `application.yml` | 见 BE-R-37 | 5 段新增 | 全部 RI | P1 |
| CFG-R-04 | `docs/GLM-KEY-SETUP.md` | 现有 | 增 `archive.network-dict.*` 段落（百度/维基 API key 申请指引） | RI-26 | P1 |
| CFG-R-05 | `docs/ENVIRONMENT-DEPENDENCIES.md` | 现有 | 增 2 段（pdfjs-dist / mammoth 纯前端说明 + OpenPDF/POI 后端说明） | RI-40, RI-41 | P2 |

---

## §5 文档同步

| # | 文件 | 改造 | 阻塞 RI | 优先级 |
|---|---|---|---|---|
| DOC-R-01 | `README.md §3.2` | README 写 backend/agent/ 实际是 backend/src/main/java/com/archive/agent/ — 笔误修 | — | **立即**（T1 §0 C-9） |
| DOC-R-02 | `docs/AGENT-FRAMEWORK-DECISION.md §1.2` 标题 | 标题改"Spring AI 1.1（仅 spring-ai-starter-model-openai）" | — | P1（T1 §0 C-1） |
| DOC-R-03 | `docs/architecture/01~06-arch-*.md` | 6 份分章架构按 T1 §1.2/§3.5/§4.7/§5.6/§6.6 同步增量 | 全部 RI | P1 |
| DOC-R-04 | `docs/DB-SCHEMA-v2.md` | 同步 7 ALTER + 7 新表 | 全部 RI | P1 |
| DOC-R-05 | `docs/ARCHITECTURE-v2.md` | 同步 Agent 工具 6→7 + 后端 13→18 Controller | 全部 RI | P1 |
| DOC-R-06 | `docs/requirements/ARCH-DECOMPOSITION.md` | 同步追加 RI-46~N（v1.1 拆解） | T3 | **P0**（T3 输入） |
| DOC-R-07 | `docs/AGENT-FRAMEWORK-DECISION.md §1.2.1.1` | 加 v1.1 §13.1.2 五级判定决策段 | RI-23 | P1 |
| DOC-R-08 | `TASKS.md` | 加 v1.1 阶段章节（按 T3 任务表） | T3 | **P0**（T3 产出） |
| DOC-R-09 | `docs/AGENT-IMPL-PLAN.md §6` 验收场景 | 加 7 重加固验收测例 | RI-27 | P1 |
| DOC-R-10 | `docs/ACCEPTANCE-GUIDE.md` | 加 v1.1 验收场景 | 全部 RI | P2 |

---

## §6 优先级总览

### 6.1 P0 阻塞性（不开工就阻塞 v1.1 任何 RI）

| 编号 | 改造点 | 阻塞 RI |
|---|---|---|
| BE-R-01 | QueryMysqlTool 7 重加固 | RI-27 |
| BE-R-02 | FindProjectTool 5 级判定 | RI-23 |
| BE-R-04 | AgentSystemPrompt 追加 2 段 | RI-22, RI-23 |
| BE-R-11, 13 | 软删 Project/Material | RI-31 |
| BE-R-14 | Proposal 决议 + 编号预留 | RI-24, RI-25 |
| BE-R-22, 23, 24 | 3 实体 ALTER（Project/Proposal/Material） | RI-31~33 |
| BE-R-34 | Role 扩 4 行 + 双轨 | RI-34 |
| DB-R-01, 02, 03, 06, 08 | 5 个 SQL 迁移 | 全部 RI |
| DOC-R-01 | README 笔误修（即时） | — |
| DOC-R-06, 08 | ARCH-DECOMPOSITION + TASKS 同步 | T3 |

**P0 总数**：14 个改造点 + 5 个 SQL + 2 个文档 = 21 个阻塞项

### 6.2 P1 业务增量（v1.1 主要工作量）

~50 个 P1 改造点，详见 §1-§5。每条对应 1 个 RI-22~45 拆解。

### 6.3 P2 文档/收尾

DOC-R-05, 10 + CFG-R-02, 05 = 4 项。

---

## §7 总结

**P0 改造点总数**：21 项（不含 P1）
**P1 改造点总数**：~50 项（与 T3 任务表 1:1 对应）
**P2 改造点总数**：4 项
**总改造点**：~75 项

**关键路径**（P0 阻塞链）：
```
DB-R-02/03/06/08 → BE-R-22/23/24 (实体 ALTER) → BE-R-11/13/14 (Controller 改) → BE-R-01/02/04 (Agent 工具/Prompt) → T3 业务 RI
```

**零回归注**：所有 P0 改造点都在"原位置 ALTER 增列 / 加方法 / 加端点"，**不破** v1.0 任何 .java / .vue / .sql / pom 行为。

---

*本表由 PM 维护。下游 T3 任务表直接消费本表的"独占文件清单"+"零回归注"。*
