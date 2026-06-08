# Plan C: Phase 2 — 业务功能核心(LLM + Engine + 业务实体)

> **状态**: 准备启动(等 Plan A、B 完成)
> **优先级**: 🟢 P2(业务核心,本计划是项目最重的一块)
> **工作量**: 8-12 个 commit,1-2 周
> **依赖**: Plan A + Plan B 完成
> **互斥**: 不与 D 并行(都在改 engine / 业务表)
> **可分模块并行**: Provider 层 + Engine 层 + 业务 Service + 业务 Controller + 前端

## 必读文档(启动前)

1. `docs/REQUIREMENTS-v1.md` § 4 业务流程 + § 5 关键功能 + § 6/7 扩展机制
2. `docs/ARCHITECTURE-v2.md` § 3 核心模块
3. `docs/DB-SCHEMA-v2.md`(全量 DDL 都在这)
4. `docs/DEV-STANDARDS.md`
5. `docs/TEAM-ARCHIVE.md`

## 范围(分 6 个子批次)

### C-1. Provider 层(LLM 抽象)

**目标**: LLM Provider 可切换

**文件**:
- `provider/LLMProvider.java` 接口
- `provider/GLMProvider.java`(基于现 `GlmService` 改造)
- `provider/OpenAIProvider.java`(OpenAI 兼容)
- `provider/MockProvider.java`(测试用)
- `provider/LLMProviderFactory.java`(按 `llm.provider` 选)
- `config/ProviderConfig.java`(从 config.json 读)
- `application.yml` 改用 `${llm.provider:glm}`

**测试**:
- `provider/GLMProviderTest.java`(用 mock server)
- 切换测试:glm → openai → mock,功能不变

**验收**: config.json 改 `provider=mock` 后,系统用 mock provider 工作。

### C-2. Engine 层(Trigger / Timepoint / Comparison / Extraction)

**目标**: 4 个引擎 + 默认实现

**文件**:
- `engine/TriggerEngine.java` — 触发规则
- `engine/TimepointExtractor.java` — 时点抽取
- `engine/ComparisonEngine.java` — 立项-申请对比
- `engine/ExtractionEngine.java` — 字段抽取(可扩展)
- `engine/AsyncExecutorConfig.java` — `@EnableAsync` + 线程池

**触发规则预置**: 10 条默认(见 `REQUIREMENTS-v1.md` § 7),写入 `dict_item` seed。

**测试**:
- 单元测试覆盖 4 个引擎
- 异步执行测试

**验收**: 上传律师函材料,自动生成"评估法律风险"待办。

### C-3. 业务实体 + 仓储 + Service

**目标**: 全部新实体入库

**文件**(详见 DB-SCHEMA-v2.md):
- `entity/Timepoint.java`
- `entity/Todo.java`
- `entity/TriggerRule.java`
- `entity/ExtractionMethod.java`
- `entity/ComparisonMethod.java`
- `entity/DictType.java`
- `entity/DictItem.java`
- `entity/AuditLog.java`
- 对应 Repository × 8
- 对应 Service × 8

**测试**:
- 每个 Service 至少 1 个集成测试

**验收**: 编译通过,所有 CRUD API 可用。

### C-4. 业务 Controller + DTO

**目标**: 全部新 API 端点上线

**文件**:
- `controller/TodoController.java`
- `controller/DictController.java`
- `controller/TriggerRuleController.java`
- `controller/ExtractionMethodController.java`
- `controller/ComparisonMethodController.java`
- `controller/AuditLogController.java`(admin only)
- 对应 DTO × 12

**API 端点清单**(见 ARCHITECTURE-v2.md):
- `GET/POST /api/todos`
- `GET/PUT/DELETE /api/todos/{id}`
- `GET /api/projects/{id}/trigger-rules`
- `POST/PUT/DELETE /api/projects/{id}/trigger-rules/{ruleId}`
- `GET /api/dict/types`
- `GET /api/dict/items?typeCode=xxx`
- `GET /api/extraction-methods`
- `GET /api/comparison-methods`
- `GET /api/audit-logs`(admin)

**验收**: 用 curl/Postman 测每个端点,200 / 业务码正确。

### C-5. 累计金额自动化 + 触发规则 + 时点入库

**目标**: 业务规则全部跑通

**改动**:
- `service/MaterialVersionService.parseVersion()` 解析成功后:
  1. 异步跑 ExtractionEngine(基于 extraction_method 表)
  2. 异步跑 TimepointExtractor → 写 timepoint + todo
  3. 异步跑 TriggerEngine(基于 trigger_rule 表)
  4. 触发审计
- `service/ProjectService.updateRemainingAmount()` — 收到凭证后自动重算
- 金额联动走 service 层事务

**测试**:
- 上传收款凭证 → remaining 增加
- 上传付款凭证 → remaining 减少
- 上传律师函 → 待办"评估法律风险"出现

**验收**: 浏览器端到端跑通 4 阶段流程,见 `M3-TEST-TASKS.md`。

### C-6. 立项-申请对比

**目标**: 上传申请报告时自动跟立项对比

**改动**:
- `service/ProposalService.submitProposal()` 提交申请时:
  1. 跑 ComparisonEngine(基于 comparison_method 表)
  2. 默认 Q&A 验证
  3. 写结果到 proposal.comparison_result_json
- 列表/详情页面展示对比结果

**测试**:
- 单元测试 ComparisonEngine(用 mock LLM)
- 端到端: 上传申请报告,看对比结果

**验收**: 浏览器看对比结果页面,每个"待落实问题"显示 ✓/✗ + 引用。

## 提交规范

每个子项一个或多个 commit:
```
feat(backend,C-1): LLMProvider 接口 + GLM/OpenAI/Mock 实现 + Factory
feat(backend,C-2): Engine 层 Trigger/Timepoint/Comparison/Extraction + 异步配置
feat(backend,C-3): 业务实体 8 个 + Repository × 8 + Service × 8
feat(backend,C-4): 业务 Controller × 6 + DTO × 12
feat(backend,C-5): MaterialVersionService 触发 Extraction + Timepoint + Trigger + 累计金额
feat(backend,C-6): ProposalService 立项-申请对比 + Q&A 验证
```

每个子项内部可以拆 2-3 个 commit(后端一批、前端一批、迁移一批)。

## 自测

每个子项完工后:
1. `mvn compile -DskipTests -B -o`
2. `mvn test`(对应的单元/集成测试)
3. 浏览器跑相关页面
4. 填到 `docs/M3-TEST-TASKS.md`(完工后写)

## 交回物

完工后向 owner 交:
1. ✅ 所有 commit hash + push 链接
2. ✅ `mvn test` 通过截图
3. ✅ 端到端测试结果(4 阶段流程)
4. ✅ `docs/DB-SCHEMA-v2.md` v2-schema.sql 实际跑过
5. ✅ `docs/M3-README.md` 完工报告
6. ✅ `docs/M3-TEST-TASKS.md` 测试任务清单

## 不在本 plan 范围

- ❌ 字典管理 UI(在 Plan E)
- ❌ 触发规则 UI(在 Plan E)
- ❌ 审计日志查看 UI(在 Plan E)
- ❌ RBAC 权限细化(在 Plan D)
- ❌ 性能优化(在 Plan F)

## 风险/注意

- ⚠️ 这是最重的 plan,**拆 2-3 周**做,不一次推完
- ⚠️ Provider 层 / Engine 层**先写 mock**,真 LLM 后接
- ⚠️ 数据库迁移脚本**先在测试库跑**,再上生产
- ⚠️ 异步执行**注意事务边界**(Engine 不能在事务内调)
- ⚠️ LLM 输出当 JSON 用必须 try-parse,失败兜底
- ⚠️ 累计金额用 BigDecimal,**不**用 float/double

## 推荐执行顺序

1. C-3(实体,数据先到位)
2. C-1(Provider,LLM 抽象)
3. C-2(Engine,核心业务)
4. C-4(Controller,API 上线)
5. C-5(业务联动)
6. C-6(对比)
