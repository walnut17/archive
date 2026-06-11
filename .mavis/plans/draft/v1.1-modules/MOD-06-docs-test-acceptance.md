# MOD-06 — 文档同步 + 集成测试 + 端到端验收

> **接手 agent 只需读本文 + 现有 docs/ + MOD-01~05 全部 commit 即可开工**
> **本模块是 v1.1 的"收口"——所有上游模块完工后才能开**

---

## §0 模块目标

v1.1 收尾：
1. **文档同步**：ARCH-DECOMPOSITION 追加 RI-46~69 + 6 份分章架构同步增量 + AGENT-DECISION 标题修 + DB-SCHEMA-v2 + ARCHITECTURE-v2 同步
2. **TASKS.md publish**：v1.1 章节正式生效
3. **集成测试**：30+ 测例，5 大端到端场景
4. **端到端验收**：11 步验收 SOP + LESSONS 复盘 + review 文件

---

## §1 涉及 RI（全部 24 条 v1.1 RI）

| RI 段 | 来源 |
|---|---|
| RI-46~69 | 本模块 append 到 `ARCH-DECOMPOSITION.md` |
| 全部 RI 同步增量 | 6 份分章架构 + AGENT-DECISION + ARCHITECTURE-v2 + DB-SCHEMA-v2 |
| 30+ 集成测例 | 汇总 MOD-01~05 所有验收段的"集成测例 ≥ N 条" |
| 5 大端到端场景 | 看板 / 通知 / 导出 / 预览 / 脱敏 + 降级测试 + 多轮对话 |

---

## §2 涉及文件（独占清单）

### 2.1 新建（2 个文件）

```
backend/src/test/java/com/archive/v11/
└── V11IntegrationTest.java                 (新, 30+ 测例)

docs/reviews/
└── 2026-06-XX-v1.1-review.md              (新, 端到端 review)
```

### 2.2 修改（11 个文件，独占）

```
docs/
├── requirements/
│   └── ARCH-DECOMPOSITION.md               (改, append RI-46~69)
├── architecture/
│   ├── 01-arch-overview.md                 (改, 同步 Agent 工具 6→7 + 后端模块变化)
│   ├── 02-backend-layer-architecture.md    (改, Controller 13→18, Service 17→29)
│   ├── 03-frontend-component-architecture.md (改, View 13→18, Component 1→5)
│   ├── 04-database-schema.md               (改, 16 业务 + 7 新 + 7 ALTER = 30 实体)
│   ├── 05-deployment-and-environment.md    (改, jar 增量说明)
│   └── 06-requirements-gap-analysis.md     (改, 24 RI 估时)
├── AGENT-FRAMEWORK-DECISION.md             (改, §1.2 标题修 + §1.2.1.1 v1.1 决策段)
├── ARCHITECTURE-v2.md                      (改, 同步 v1.1 增量)
├── DB-SCHEMA-v2.md                         (改, 同步 v1.1 增量)
├── ACCEPTANCE-GUIDE.md                     (改, 加 v1.1 验收场景)
├── LESSONS-LEARNED.md                      (改, append P0-24~)
├── ENVIRONMENT-DEPENDENCIES.md             (改, 加 v1.1 配置说明)
└── TASKS.md                                (改, append v1.1 章节, 引用本模块 §3.2)
```

**总计**：2 新 + 11 改 = 13 个文件

---

## §3 设计要点

### 3.1 ARCH-DECOMPOSITION 追加 RI-46~69

**严格沿用** RI-1~45 现有 6 字段格式（业务 / 影响表 / 角色 / 验收 / 依赖 / 估算）。

```markdown
## RI-46: 置信度 3 级体系（§13.1.1）

- **业务**: 替换原 §5.8.3 "0.6 阈值"二元判定为 3 级...
- **影响表**: `project_fact` + `project_fact_event` ALTER + 回填 SQL...
- **角色**: admin / 业务部门 / 投委会委员...
- **验收**: 1. Given fact confidence=0.90... 2. Given fact confidence=0.70... 3. ...
- **依赖**: RI-50, RI-47
- **估算**: BE 0.5d / FE 0.2d / 测试 0.2d
- **对应 §X.Y**: REQUIREMENTS §13.1.1
- **对应 MOD**: MOD-01 (SQL) + MOD-02 (实体) + MOD-03 (Agent prompt)

(... RI-47 ~ RI-69 同样格式 ...)
```

### 3.2 TASKS.md v1.1 章节 publish

```markdown
## v1.1 阶段（基线 7aa7bae，零回归）

### MOD-01: 数据库迁移 + 7 表 ALTER

- **任务 ID**: T-v1.1-02/03/04
- **状态**: 见 `.mavis/plans/draft/v1.1-modules/MOD-01-db-migrations.md` §4.5
- **独占文件**: `backend/src/main/resources/db/migration/I-RI-*.sql` + `init.sql`
- **依赖**: 无（关键路径第一步）
- **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-01-db-migrations.md`

### MOD-02: 核心域改造

- **任务 ID**: T-v1.1-05/06/11/12/13/14/15/19/18
- **状态**: 见 `.mavis/plans/draft/v1.1-modules/MOD-02-core-domain.md` §4.5
- **依赖**: MOD-01 完工
- **详细 spec**: `.mavis/plans/draft/v1.1-modules/MOD-02-core-domain.md`

### MOD-03: Agent 工具改造

... 同上结构 ...

### MOD-04: 业务功能新增

... 同上结构 ...

### MOD-05: 前端集成

... 同上结构 ...

### MOD-06: 文档 + 集成测试 + 验收（本模块）

- **状态**: 已完成
- **依赖**: MOD-01 ~ MOD-05 全部完工
```

### 3.3 集成测试 V11IntegrationTest（30+ 测例）

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.agent.enabled=true",
    "archive.optimistic-lock.strict=true"  // 测试时强制
})
class V11IntegrationTest {
    
    @Autowired private TestRestTemplate rest;
    @Autowired private MockBeanService mockLLM;
    
    // ============ MOD-01 数据库验证 ============
    
    @Test
    void allMigrationsApplied() {
        // 验证 7 表含 deleted_at/deleted_by/version
        // 验证 7 张新表存在
        // 验证触发器存在
    }
    
    @Test
    void confidenceLevelBackfilled() {
        // 验证历史 fact 都有 confidence_level
    }
    
    // ============ MOD-02 核心域验证 ============
    
    @Test
    void softDeleteProject() { ... }
    
    @Test
    void optimisticLockConflict() { ... }
    
    @Test
    void rbacDualTrackPriority() { ... }
    
    @Test
    void auditLog5Types() { ... }
    
    // ============ MOD-03 Agent 工具验证 ============
    
    @Test
    void findProject5LevelDecision() { ... }
    
    @Test
    void queryMysql7LayerHardening() { ... }
    
    @Test
    void networkDictLookupFallback() { ... }
    
    @Test
    void glsServiceFailureTypes() { ... }
    
    // ============ MOD-04 业务功能验证 ============
    
    @Test
    void projectBoardKanban() { ... }
    
    @Test
    void notificationPolling() { ... }
    
    @Test
    void exportPdf() { ... }
    
    @Test
    void exportExcel() { ... }
    
    @Test
    void materialPreview() { ... }
    
    @Test
    void factEventDiff() { ... }
    
    @Test
    void importExcelWithErrors() { ... }
    
    @Test
    void maskingView() { ... }
    
    // ============ MOD-05 前端集成验证（Cypress/Playwright） ============
    
    @Test
    void knowledgeConfidenceBadge() { ... }
    
    @Test
    void knowledgeSwitchHint() { ... }
    
    @Test
    void dashboardModeAnimation() { ... }
    
    @Test
    void projectFormFailureBanner() { ... }
    
    // ============ 端到端 5 大场景 ============
    
    @Test
    void scenario1_ProjectKanban() { ... }
    
    @Test
    void scenario2_Notification() { ... }
    
    @Test
    void scenario3_ExportPdf() { ... }
    
    @Test
    void scenario4_Preview() { ... }
    
    @Test
    void scenario5_Masking() { ... }
    
    @Test
    void scenario6_DowngradeWhenAgentDisabled() { ... }
    
    @Test
    void scenario7_MultiTurnConversation() { ... }
    
    // 测例数：≥ 30
}
```

### 3.4 ACCEPTANCE-GUIDE.md 加 v1.1 验收场景

**沿用 v1.0 11 步验收 SOP**，追加 5 大场景：

```markdown
## v1.1 增量验收场景

### 场景 1: 项目看板
1. 登录 → 主页 → 项目看板
2. 切换视图 table → card → kanban
3. 应用筛选 region=江苏 + stage=POST_LOAN
4. 排序 amount desc
5. 期望：列表实时更新，9 列完整

### 场景 2: 通知中心
1. 触发系统通知（如新建 todo）
2. 顶栏铃铛显示未读数 badge
3. 点击铃铛 → 通知中心全屏
4. 标已读 → 未读数 -1
5. 30s 后自动刷新（无新通知则无变化）

### 场景 3: 数据导出
1. 项目详情页 → 导出按钮
2. 选 PDF → 下载 project-1.pdf
3. 选 Excel → 下载 project-1.xlsx
4. 验证 PDF 内容含项目所有字段
5. 验证 audit_log.type='EXPORT'

### 场景 4: 附件预览
1. 材料列表点文件名
2. PDF 文件 → 内嵌预览
3. Word 文件 → mammoth 转 HTML 预览
4. 图片文件 → 原生 <img> 预览
5. 大文件 > 50MB → 提示"请下载查看"

### 场景 5: 脱敏视图
1. 委员登录 → 访问项目详情
2. 期望：displayName='张**', displayAmount='***万'
3. 点"申请脱敏查看" → 写 audit_log + 通知 admin
4. admin 登录 → 收到通知 + 审批
5. 审批通过后，委员可看完整信息

### 场景 6: 降级测试
1. application.yml 改 archive.optimistic-lock.strict=true
2. 2 user 同时 PATCH project
3. 期望：1 成功 1 409 + "数据已被他人修改"

### 场景 7: 多轮对话
1. 第 1 轮: "PRJ-2026-001 怎么样"
2. 第 2 轮: "它的剩余金额"
3. 第 3 轮: "谁负责"
4. 期望：第 2/3 轮自动锁定 PRJ-2026-001 不丢
```

### 3.5 LESSONS-LEARNED 追加 P0-24~

```markdown
## P0-24: v1.1 关键教训

### P0-24.1 RBAC 双轨兼容（v1.1 RBAC 5 角色改造）
**现象**: v1.1 改 RBAC 时差点删除 `user.role_id` 字段
**教训**: v1.0 单用户路径千万不能断，新增 `user_role` 多对多是主路径，`user.role_id` 保留兼容
**预防**: 任何升级前先列"v1.0 用户操作清单"逐项验证

### P0-24.2 乐观锁严格度（v1.1 灰度）
**现象**: v1.1 乐观锁 `strict=true` 会导致单用户系统频繁 409
**教训**: v1.1 期 `strict=false`（冲突仅记日志），v2 多用户时切 true
**预防**: 配置项加详细注释 + README 标注

### P0-24.3 网络查字典降级（v1.1 RI-26）
**现象**: 内网全失败时网络查字典抛异常导致 AgentEngine 崩溃
**教训**: 工具级降级必须返回 `{found: false, reason: ...}` 而非抛异常
**预防**: 所有外部 API 调用都要有"全失败兜底"

(... P0-25 ~ P0-30 视完工情况追加 ...)
```

### 3.6 review 文件

```markdown
# v1.1 review — 端到端验收复盘

## 1. 13 commit 链接（按 MOD 顺序）
- MOD-01: <hash1>, <hash2>, ...
- MOD-02: <hash3>, ...
- ...
- MOD-06: <hash11>, ...

## 2. 编译 / 测试 / 构建 截图
- mvn compile: [截图]
- mvn test: [截图, 30+ 测例全过]
- npm run build: [截图]

## 3. 端到端浏览器测试结果
- 场景 1 (项目看板): [截图 + 验证]
- 场景 2 (通知中心): [截图 + 验证]
- 场景 3 (数据导出): [PDF 截图 + Excel 截图]
- 场景 4 (附件预览): [4 类文件截图]
- 场景 5 (脱敏视图): [委员视图 + admin 通知截图]
- 场景 6 (降级测试): [409 截图]
- 场景 7 (多轮对话): [3 轮上下文截图]

## 4. 已知问题 / 留 TODO
- (列 5-10 个 v1.1 测出来的待优化点)

## 5. owner 审请关注
- 零回归: v1.0 所有功能仍可用? ✓/✗
- 5 大场景全过: ✓/✗
- 文档同步完整: ✓/✗
- LESSONS 追加到位: ✓/✗
```

---

## §4 验收

### 4.1 文档完整性

```bash
cd /workspace/projects-online
# ARCH-DECOMPOSITION 含 RI-46~69
grep -c "^### RI-" docs/requirements/ARCH-DECOMPOSITION.md
# 期望：≥ 69（45 + 24）

# 6 份分章架构同步
grep -l "v1.1" docs/architecture/01-arch-overview.md docs/architecture/02-backend-layer-architecture.md docs/architecture/03-frontend-component-architecture.md docs/architecture/04-database-schema.md docs/architecture/05-deployment-and-environment.md docs/architecture/06-requirements-gap-analysis.md
# 期望：6 个文件

# AGENT-DECISION 标题修
head -5 docs/AGENT-FRAMEWORK-DECISION.md | grep "Spring AI 1.1（仅"
# 期望：命中
```

### 4.2 集成测试

```bash
mvn test -Dtest=V11IntegrationTest -B
# 期望：30+ 测例全过
```

### 4.3 端到端验收

```bash
# 启动后端 + 前端
mvn spring-boot:run &
cd frontend && npm run dev &

# 浏览器手动跑 7 场景（详见 §3.4）
# 或 Cypress/Playwright 自动化
```

### 4.4 TASKS.md publish

```bash
grep -c "T-v1.1-" TASKS.md
# 期望：≥ 41（41 任务）
```

### 4.5 LESSONS 追加

```bash
grep -c "P0-2[4-9]\|P0-3[0-9]" docs/LESSONS-LEARNED.md
# 期望：≥ 5 条新 P0
```

### 4.6 review 文件

```bash
ls docs/reviews/2026-06-*-v1.1-review.md
# 期望：存在
```

### 4.7 完工 checklist

- [ ] 13 个文件全部 commit
- [ ] §4.1 ~ §4.6 全部验证通过
- [ ] 改 `TASKS.md` 状态 → `已完成`
- [ ] 项目方可验收

---

## §5 踩坑预警

### 5.1 ARCH-DECOMPOSITION 不要重写 RI-1~45

只 append RI-46~69，**不要**改 RI-1~45 现有拆解。如有冲突，在 §13 v1.1 增量说明段标注，不动原 RI。

### 5.2 6 份分章架构是 Sisyphus 写的

`docs/architecture/01~06-arch-*.md` 是另一个 AI（Sisyphus）写的，**风格保持一致**。追加段落而不是覆盖。

### 5.3 AGENT-DECISION 标题修只改 §1.2

`§1.2` 标题改"Spring AI 1.1（仅 spring-ai-starter-model-openai）"，**不要**改其他章节标题。

### 5.4 集成测试不能用真实 GLM API

`application-test.yml` 加 `spring.ai.agent.enabled=false` 走降级路径，或用 `MockBean` mock `LLMProvider`。**严禁**集成测试调真实 API（CI 没有 GLM key）。

### 5.5 端到端验收需要后端 + 前端都启动

`mvn spring-boot:run` + `npm run dev` 同时跑。**不要**只跑后端测端到端（前端页面的徽章 / 动画 / 弹窗测不到）。

### 5.6 review 文件命名规范

`YYYY-MM-DD-v1.1-review.md`，日期是 MOD-06 完工当天。沿用 `2026-06-09-plan-i-p0-review.md` 格式。

### 5.7 LESSONS 复盘要诚实

P0-24~ 必须记录**真实**踩过的坑，不能写"理论上应该注意"。参考 v1.0 P0-19~23（`2026-06-10-plan-i-10of10-achievement.md`）。

### 5.8 严禁直推 `minimax` 分支

MOD-06 跟其他模块一样推 main，**不能**直推 minimax（沙箱专属）。

---

## §6 接口契约

### 6.1 给项目方

- ARCH-DECOMPOSITION 是 RI-46~69 的**唯一真相源**
- TASKS.md v1.1 章节是任务协调的**唯一真相源**
- 集成测试是完工验收的**唯一标准**
- review 文件是 v1.1 经验沉淀

### 6.2 给接手 agent

- 任何 v1.1 任务完成后，改 `TASKS.md` v1.1 章节对应状态
- 任何 RI 验收失败，更新 `ARCH-DECOMPOSITION.md` 验收段
- 任何 P0 教训，更新 `LESSONS-LEARNED.md`

---

*本模块由测试 + PM agent 接手。MOD-01 ~ MOD-05 全部完工后开工。*