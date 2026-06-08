# Plan F: Phase 4 — 测试 + 治理 + 文档收尾

> **状态**: 准备启动(等 Plan A-E 全部完成)
> **优先级**: 🔵 P4(质量收尾)
> **工作量**: 4-6 个 commit,1-2 天
> **依赖**: Plan A-E 全部完成
> **互斥**: 不与其它 plan 并行(收尾阶段)

## 必读文档

1. `docs/REQUIREMENTS-v1.md` § 10 验收标准
2. `docs/DEV-STANDARDS.md`
3. `docs/TEAM-ARCHIVE.md`
4. `docs/LESSONS-LEARNED.md`

## 范围(5 个子项)

### F-1. P4-1 后端集成测试

**目标**: 关键 service 测过,CI 跑 `mvn test` 全过

**新增测试**:
- `service/AuthServiceTest.java` — 登录成功/失败/限流
- `service/ProjectServiceTest.java` — CRUD + 阶段流转
- `service/MaterialVersionServiceTest.java` — 上传 + 解析 + SHA-256 去重
- `service/TodoServiceTest.java` — CRUD + 自动从 timepoint / trigger 生成
- `engine/TriggerEngineTest.java` — 6 个条件组合
- `engine/TimepointExtractorTest.java`(用 mock LLM)
- `engine/ComparisonEngineTest.java`(用 mock LLM)
- `engine/ExtractionEngineTest.java`(用 mock LLM)
- `provider/LLMProviderTest.java` — 切换 provider

**测试数据**:
- `src/test/resources/test-data.sql` 种子数据
- 用 H2 或 Testcontainers MySQL

**验收**: `mvn test` 全过,覆盖率 >= 60%(关键 service)。

### F-2. P4-2 前端 TypeScript 类型安全

**目标**: 清 `as any`,加类型保护

**改动**:
- `tsconfig.json` 启用严格模式 + `noUnusedLocals` + `noUnusedParameters`
- 扫所有 `as any`:
  ```bash
  cd frontend
  grep -rn "as any" src/
  ```
- 每个都改:
  - 优先用真类型
  - 次之用 `unknown` + 类型守卫
  - 真不行才用 `as`(加注释说明)
- 补全 API 响应泛型:
  - `api/http.ts` 的 `getData<T>` 已存在
  - 业务函数全用 `<T>` 标注返回类型

**验收**: `npm run build` 0 警告,`grep "as any" src/` 数量显著减少。

### F-3. 性能基线 + 监控

**目标**: 验证性能指标

**改动**:
- `application.yml` 启用 `actuator/health` / `actuator/metrics`
- 加 `actuator/prometheus`(可选,简单)
- 跑性能基线测试(100 个项目 / 1000 份材料):
  - 首页加载 < 2 秒
  - 列表 API P95 < 500ms
  - 知识库问答 < 3 秒(10 万字库)
- 写结果到 `docs/PERFORMANCE-BASELINE.md`

**验收**: 报告齐全,基线达标。

### F-4. 文档收尾

**目标**: README + CHANGELOG + 给接手者

**改动**:
- 根 `README.md` 完整更新:
  - 项目简介
  - 接手必读(指向 8 份 docs)
  - 快速开始(部署步骤)
  - 当前状态(M0~M4 完工)
- 新增 `CHANGELOG.md`: v1.0 → v2.0 所有变更
- `docs/LESSONS-LEARNED.md`:
  - 整合新增踩坑
  - 标日期和影响版本
- `docs/TEAM-ARCHIVE.md`:
  - 更新"已知问题"段(M0~M4 都修了)
  - 加新发现的部署/工具问题

**验收**: 新人拿到仓库,看 README + docs,能从 0 启动到上线。

### F-5. 端到端测试任务清单(交付前)

**目标**: v2 完整端到端测试,所有功能跑过

**新建**:`docs/V2-TEST-TASKS.md`,类似 M1-TEST-TASKS.md

**测试场景**:
- [ ] 4 阶段流程(立项→申请→贷后→结清)
- [ ] 批量上传 5 个材料
- [ ] 触发规则: 上传律师函 → 待办出现
- [ ] 累计金额: 收款 → 增加
- [ ] 累计金额: 付款 → 减少
- [ ] 立项-申请对比: 落实问题 ✓ / ✗
- [ ] 知识库问答: 10 个问题能答
- [ ] admin 加字典项 → 创建项目下拉出现
- [ ] admin 加抽取方法 → 新上传材料自动跑
- [ ] admin 加触发规则 → 新上传材料自动触发
- [ ] RBAC: user 看不到参数管理
- [ ] 登录限流: 5 次失败后 429
- [ ] 性能: 首页 < 2 秒,问答 < 3 秒
- [ ] LLM Provider 切换: glm → mock → openai 都工作

**每条**填:测试人 / 日期 / 期望 / 实际 / 截图

**验收**: 全过,贴最终结果。

## 提交规范

```
test(backend,F-1): 关键 service 集成测试(Auth/Project/MaterialVersion/Todo/Engine/Provider)
refactor(frontend,F-2): 消除 as any + 启用 TypeScript 严格模式
chore(backend,F-3): 启用 actuator + 性能基线测试
docs(repo,F-4): README/CHANGELOG/LESSONS/TEAM-ARCHIVE 收尾
test(repo,F-5): V2-TEST-TASKS 端到端测试清单
```

## 自测

完工后:
1. `mvn clean test` 全过
2. `npm run build` 通过
3. 端到端:按 V2-TEST-TASKS 走一遍
4. 给 owner 完整报告

## 交回物

1. ✅ 所有 commit + push
2. ✅ `mvn test` + `npm run build` 截图
3. ✅ `docs/PERFORMANCE-BASELINE.md`
4. ✅ `docs/V2-TEST-TASKS.md` 全部勾选
5. ✅ v2.0 完工报告
6. ✅ **完整工作交接文档**(给接手者)

## 不在本 plan 范围

- ❌ 任何新功能(都做完了)
- ❌ 任何重构(优化也单独 plan)

## 风险/注意

- ⚠️ F-1 集成测试**注意数据库隔离**(用 `@Transactional` 回滚,或 Testcontainers)
- ⚠️ F-2 TypeScript 严格模式可能暴露**多个隐藏 bug**,慢慢改不一次全开
- ⚠️ F-4 文档收尾是**体力活**,不能省
- ⚠️ F-5 是**真实工作量**,预计 4-8 小时,分 2 天做
