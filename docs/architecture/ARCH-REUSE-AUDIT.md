# 现有框架可沿用性评估 (M0~M2)

> **基线代码**: 投委会档案管理系统 (Spring Boot 3.3 + JPA + MySQL 8 + Vue 3 + Element Plus)
> **已落模块**: M0(登录) + M1(档案 CRUD) + M2(知识库问答 MySQL FULLTEXT)
> **目标**: 评估 M3~M4 实施时哪些可直接复用,哪些需增补,哪些需重构
> **标记**: ✅ 沿用 / ✏️ 增补 / ❌ 重构
> **更新日期**: 2026-06-08

---

## 1. 后端可沿用 — 实体层

| 实体 | 状态 | 说明 |
|------|------|------|
| `BaseEntity` | ✅ 沿用 | createdAt/updatedAt/createdBy/updatedBy 完整 |
| `User` | ✅ 沿用 | 配合 RBAC (P3-2) 加 role 字段已通过 `User.roleId` 引用 Role |
| `Role` | ✅ 沿用 | code/name 足够;P3-2 RBAC 直接用 |
| `Project` | ✅ 沿用 | 字段完整,无新增 |
| `Proposal` | ✅ 沿用 | 字段完整,无新增 |
| `Material` | ✅ 沿用 | 字段完整,无新增 |
| `MaterialVersion` | ✏️ 增补 | 加 `parsed_text` 已 OK;`chapter_summary` / `todo` / `timepoint` / `trigger_rule` / `extraction_method` / `comparison_method` 需新表(避免实体过度膨胀) |
| `ChapterSummary` (新) | 🆕 新增 | 章节切分结果(P2-1) |
| `Todo` (新) | 🆕 新增 | 触发规则生成的待办(P3 增强) |
| `Timepoint` (新) | 🆕 新增 | 时点日程(M3 核心) |
| `TriggerRule` (新) | 🆕 新增 | 规则配置(M4 核心) |
| `TriggerRecord` (新) | 🆕 新增 | 规则触发历史/审计 |
| `ExtractionMethod` (新) | 🆕 新增 | 时点抽取方法配置(LLM/正则/HanLP) |
| `ComparisonMethod` (新) | 🆕 新增 | 文档比对方法配置(diff/diff-match-patch) |
| `DictType` + `DictItem` (新) | 🆕 新增 | 参数配置表(P3-6) |
| `AuditLog` (新) | 🆕 新增 | 审计日志 |

## 2. 后端可沿用 — Repository 层

| Repository | 状态 | 说明 |
|------------|------|------|
| `ProjectRepository` | ✅ 沿用 | 已有 `searchByKeyword` |
| `ProposalRepository` | ✅ 沿用 | 已有 `searchByKeyword` |
| `MaterialRepository` | ✅ 沿用 | 已有搜索 + N+1 已识别需修(P2-3) |
| `MaterialVersionRepository` | ✅ 沿用 | FULLTEXT MATCH AGAINST 已用 |
| `UserRepository` | ✅ 沿用 | `findByUsername` 已用 |
| `RoleRepository` | ✅ 沿用 | `findById` 已用 |
| `ChapterSummaryRepository` | 🆕 新增 | `findByMaterialVersionIdOrderByChapterNo` + FULLTEXT search |
| `TimepointRepository` | 🆕 新增 | `findBySourceMaterialIdAndTriggeredAtBetween` |
| `TriggerRuleRepository` | 🆕 新增 | CRUD |
| `DictItemRepository` | 🆕 新增 | `findByTypeCodeAndEnabled` |

## 3. 后端可沿用 — Service 层

| Service | 状态 | 说明 |
|---------|------|------|
| `AuthService` | ✅ 沿用 | JWT 流程已通;P3-1 登录限流加在 service 内 |
| `ProjectService` | ✅ 沿用 | CRUD + 状态校验 |
| `ProposalService` | ✏️ 增补 | 加 `submitProposal()` 触发 P2-6 自动摘要 |
| `MaterialService` | ✅ 沿用 | N+1 需修(P2-3),加 `countVersionsForMaterials` |
| `MaterialVersionService` | ✏️ 增补 | ① 加 `@Async` 解析(P2-2) ② 加 SHA-256 去重(P3-3) ③ 加 `deleteParsedText`(P2-4) |
| `TikaService` | ✅ 沿用 | 完整,无需改 |
| `SectionService` | ✏️ 增补 | 加 `extractKeywords()` + `extractTimepoints()` |
| `KnowledgeSearchService` | ✅ 沿用 | 已有 FULLTEXT + snippet,P3-4 改进 snippet 提取 |
| `GlmService` | ❌ 重构 | 抽象成 `LlmProvider` 接口(P3+),新增 `extractTimepoint` / `generateSummary` / `compareVersions` |
| `ChapterService` | 🆕 新增 | 章节切分 + 持久化(P2-1) |
| `TimepointService` | 🆕 新增 | 时点抽取 + 调度(M3) |
| `RuleEngineService` | 🆕 新增 | 规则执行 + Aviator 集成(M4) |
| `DictService` | 🆕 新增 | 字典 CRUD(P3-6) |
| `AuditService` | 🆕 新增 | 操作审计 |

## 4. 后端可沿用 — Controller 层

| Controller | 状态 | 说明 |
|------------|------|------|
| `AuthController` | ✅ 沿用 | /api/auth/login |
| `ProjectController` | ✅ 沿用 | 5 个端点已完整 |
| `ProposalController` | ✅ 沿用 | 5 个端点已完整 |
| `MaterialController` | ✏️ 增补 | 加 `POST /api/proposals/{id}/materials/batch-upload`(P2-5) |
| `MaterialVersionController` | ✅ 沿用 | 8 个端点已完整 |
| `QaController` | ✅ 沿用 | /api/qa/ask |
| `HealthController` | ✅ 沿用 | /api/health |
| `ChapterController` | 🆕 新增 | 章节 CRUD + FULLTEXT 搜索 |
| `TimepointController` | 🆕 新增 | 时点 CRUD + 提醒 |
| `RuleController` | 🆕 新增 | 规则 CRUD + 手动触发 |
| `DictController` | 🆕 新增 | admin 字典管理 + 公开 options |
| `ComparisonController` | 🆕 新增 | 文档版本比对 |

## 5. 后端可沿用 — 通用层

| 通用类 | 状态 | 说明 |
|--------|------|------|
| `ApiResponse<T>` | ✅ 沿用 | 统一响应包装 |
| `GlobalExceptionHandler` | ✏️ 增补 | 加 `NoSuchElementException` → 404(P1-1) + `IllegalStateException` → 400 |
| `StorageService` | ✏️ 增补 | 加 `deleteParsedText()`(P2-4) |
| `ConfigJsonLoader` | ✅ 沿用 | 多路径回退机制已完整 |
| `JwtUtil` | ✅ 沿用 | HS256 + 8h 过期 |
| `JwtAuthFilter` | ✅ 沿用 | 拦截器逻辑完整 |
| `SecurityConfig` | ✏️ 增补 | ① 加 `@EnableMethodSecurity`(P3-2 RBAC) ② `/api/auth/login` 加 IP 限流(P3-1) ③ `/api/**` 加 `hasRole` |
| `GlmProperties` | ✅ 沿用 | 配置结构 OK |
| `StorageProperties` | ✅ 沿用 | 配置结构 OK |
| `DatabaseProperties` | ✅ 沿用 | 配置结构 OK |
| `AsyncConfig` | 🆕 新增 | `@EnableAsync` 线程池(core=2, max=4, queue=10) |
| `AuditorAwareImpl` | 🆕 新增 | P0-4 必加 |

## 6. DTO 可沿用

| DTO | 状态 | 说明 |
|-----|------|------|
| `PageResponse<T>` | ✅ 沿用 | 已统一,前/后端解包问题需修(P0-1) |
| `ProjectRequest/Response` | ✅ 沿用 | 字段完整 |
| `ProposalRequest/Response` | ✅ 沿用 | 字段完整 |
| `MaterialRequest/Response` | ✅ 沿用 | 字段完整 |
| `MaterialVersionResponse` | ✅ 沿用 | 字段完整 |
| `QaRequest/Response` | ✅ 沿用 | 字段完整 |
| `ChapterSummaryRequest/Response` | 🆕 新增 | |
| `TimepointRequest/Response` | 🆕 新增 | |
| `TriggerRuleRequest/Response` | 🆕 新增 | |
| `DictItemRequest/Response` | 🆕 新增 | |
| `VersionComparisonResponse` | 🆕 新增 | |

## 7. 前端可沿用 — views

| 页面 | 状态 | 说明 |
|------|------|------|
| `Login.vue` | ✅ 沿用 | 登录流已通 |
| `Layout.vue` | ✏️ 增补 | ① 加图标 import(P0-5) ② 删重复菜单 ③ 加 admin/规则/时点菜单(M3/M4) |
| `Dashboard.vue` | ✅ 沿用 | 简单工作台 |
| `ProjectList.vue` | ✏️ 增补 | 分页 0→1-based(P0-3);字典动态加载 |
| `ProjectForm.vue` | ✏️ 增补 | ApiResponse 解包(P0-1);字典动态加载 |
| `ProjectDetail.vue` | ✏️ 增补 | ApiResponse 解包(P0-1) |
| `ProposalDetail.vue` | ✏️ 增补 | ApiResponse 解包 4 处(P0-1);加自动摘要标记(P2-6);批量上传按钮(P2-5) |
| `Knowledge.vue` | ✏️ 增补 | 回车发送(P0-6) |
| `ChapterList.vue` | 🆕 新增 | 章节列表 + 检索 |
| `TimepointList.vue` | 🆕 新增 | 时点日程(M3) |
| `RuleEditor.vue` | 🆕 新增 | 规则配置(M4) |
| `AdminDict.vue` | 🆕 新增 | 参数管理(P3-6) |
| `DocumentCompare.vue` | 🆕 新增 | 版本比对 |

## 8. 前端可沿用 — api/store/router

| 文件 | 状态 | 说明 |
|------|------|------|
| `api/http.ts` | ✅ 沿用 | `getData()` 解包已修 |
| `api/auth.ts` | ✅ 沿用 | |
| `api/archive.ts` | ✏️ 增补 | ① 删硬编码 status/category 数组 ② 加 dict API ③ 加 chapter/timepoint/rule API |
| `api/qa.ts` | 🆕 新增 | QaController 调用(可放 archive.ts) |
| `store/auth.ts` (Pinia) | ✅ 沿用 | token + user |
| `router/index.ts` | ✏️ 增补 | 加新页面路由 |
| `composables/useDict.ts` | 🆕 新增 | 字典动态加载 composable(P3-6) |

## 9. 数据库可沿用 — 已有表

| 表 | 状态 | 说明 |
|----|------|------|
| `user` | ✅ 沿用 | 含 role_id 关联 Role |
| `role` | ✅ 沿用 | code/name |
| `project` | ✅ 沿用 | 7 字段 + 审计 |
| `proposal` | ✅ 沿用 | 8 字段 + 审计 |
| `material` | ✅ 沿用 | 7 字段 + 审计 |
| `material_version` | ✏️ 增补 | ① 加 `parsed_text LONGTEXT` ② 加 `FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram`(P0-2) |

## 10. 数据库可沿用 — 新增表

| 表 | 状态 | 主键 | 索引 | 说明 |
|----|------|------|------|------|
| `chapter_summary` | 🆕 新增 | id | FULLTEXT(content, summary) | 章节切分(P2-1) |
| `timepoint` | 🆕 新增 | id | idx(source_type, source_id, trigger_date) | 时点日程(M3) |
| `trigger_rule` | 🆕 新增 | id | idx(rule_code) | 规则配置(M4) |
| `trigger_record` | 🆕 新增 | id | idx(rule_id, triggered_at) | 触发历史 |
| `extraction_method` | 🆕 新增 | id | unique(type) | 抽取方法配置 |
| `comparison_method` | 🆕 新增 | id | unique(type) | 比对方法配置 |
| `dict_type` | 🆕 新增 | id | unique(type_code) | 字典分类(P3-6) |
| `dict_item` | 🆕 新增 | id | unique(type_code, item_key) | 字典项 |
| `audit_log` | 🆕 新增 | id | idx(user_id, action, created_at) | 审计 |

## 11. 不可沿用需重构

| 项 | 状态 | 说明 |
|----|------|------|
| `ApiResponse` 解包(P0-1) | ❌ 重构 | 前端 4 处 `resp.data` 错误,后端 `getData` 已修,前端需删 `.data` |
| `GlmService` 抽象(F-3) | ❌ 重构 | 改成 `LlmProvider` 接口 + 多实现(智谱/OpenAI/通义千问) |
| N+1 查询(P2-3) | ❌ 重构 | `MaterialController.list()` 循环 count,改批量查 |
| `@EnableAsync` 缺失(P2-2) | ❌ 重构 | 50MB+ 文件解析阻塞线程,加 `@Async` |
| `AuditorAware` 缺失(P0-4) | ❌ 重构 | createdBy/updatedBy 始终 null |

## 12. 增补建议(优先级)

| 优先级 | 模块 | 改动量 | 备注 |
|--------|------|--------|------|
| P0 | ApiResponse 解包 / FULLTEXT 索引 / 分页 / 审计 / 图标 / 回车发送 | 小 | 必修 |
| P1 | NoSuchElementException 404 / RBAC / Caddy 限流 / jwt.secret 配置 | 中 | 必修 |
| P2 | chapter_summary / 异步解析 / N+1 / 批量上传 / 自动摘要 | 中 | 应做 |
| P3 | 登录限流 / 文件去重 / snippet 改进 / 字典表 | 中 | 应做 |
| M3 | Timepoint 表 + LLM 时点抽取 + 调度任务 | 中 | 核心 |
| M4 | TriggerRule 表 + Aviator 引擎 + 规则 CRUD UI | 大 | 核心 |

## 13. 沿用率统计

| 层 | 沿用 | 增补 | 重构 | 新增 | 沿用率 |
|----|------|------|------|------|--------|
| 实体 | 6 | 1 | 0 | 9 | 38% |
| Repository | 5 | 0 | 0 | 4 | 56% |
| Service | 5 | 3 | 1 | 5 | 36% |
| Controller | 4 | 1 | 0 | 5 | 40% |
| 通用层 | 5 | 3 | 0 | 2 | 50% |
| 前端 views | 3 | 4 | 0 | 5 | 25% |
| 前端 api/store/router | 1 | 1 | 0 | 2 | 25% |
| 数据库表 | 5 | 1 | 0 | 9 | 33% |
| **平均** | - | - | - | - | **~38%** |

## 14. 总结

- **后端底座扎实**:BaseEntity / Security / Storage / JWT / ConfigLoader 可直接复用
- **M1 档案 CRUD 业务逻辑完整**:Project/Proposal/Material 三层结构是后续 M3/M4 的天然容器
- **M2 FULLTEXT 全文检索可直接升级到章节级**(chapter_summary)
- **GlmService 是最大重构点**:从单一智谱封装升级为 LlmProvider 多 provider 抽象
- **P0~P1 必修项集中在 ApiResponse 解包 + 异常处理 + 异步 + 审计** — 1-2 天可全部修完
- **M3/M4 主要是新增表 + 新增 service + Aviator 集成** — 不动现有数据

---

**审计员**: Architecture-Researcher
**日期**: 2026-06-08
**关联文档**: `../requirements/SUPPLEMENTARY-REQUIREMENTS.md` / `../../test-to-settle/old/M1-README.md` / `../requirements/SIMILAR-PRODUCTS.md`
