# MOD-05 + MOD-06 代码审查报告 — 前端集成 + 测试验收

> 审查人：Sisyphus | 日期：2026-06-11 | 审查范围：MOD-05（9 文件，320 行）+ MOD-06（17 文件，1296 行）
> 执行人：阿根廷 | 基线 commit：`e9eb859` + `104eb4a`

---

## 0. 总体评价

**MOD-05（前端集成）质量不错**，动画组件、Knowledge.vue 改造、配置更新都干净。**MOD-06（测试验收）的测试代码质量一般**：`V11IntegrationTest` 有 524 行但测试深度不够，且存在 JDBC 手动建表而不是用 Flyway 迁移的问题。

---

## MOD-05：前端集成 Review

### ✅ 新增文件

#### `AnimatedModeSwitch.vue`
干净。`<transition>` 动画 + `mode="out-in"` + 0.3s ease 过渡。TypeScript 类型 `ModeSwitchValue` 导出正确。

#### `Knowledge.vue` 改造
干净。3 个新增函数（`switchHintText`、`hintType`、`confidenceTagType`）正确映射 SwitchDecision 枚举到前端显示文本。

**但**：`switchHintText` 使用 `Record<string, string>` 而非 `Record<SwitchDecision, string>`，丢失了类型安全。如果后端新增枚举值，前端不会报错编译。

建议改为：

```typescript
type SwitchHintMap = Record<SwitchDecision, string>
```

#### `Dashboard.vue` 改造
干净。待办列表 + `AnimatedModeSwitch` 集成 + 待办/无待办双模动画。合理。

#### `application.yml` 配置新增
干净。7 段新配置（`archive.network-dict`、`archive.query-mysql`、`archive.optimistic-lock`、`archive.retention`、`archive.audit`、`archive.notification`），全部带注释，零回归。

#### `config.example.json` 同步更新
干净。同步添加了对应的配置模板项。

#### `GLM-KEY-SETUP.md`
干净，纯文档，无问题。

---

## MOD-06：测试验收 Review

### 核心问题

#### 1️⃣ `V11IntegrationTest.java` — JDBC 手动建表而不是用迁移

```java
private void ensureV11JdbcTables() {
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_role ( ...");
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS project_member ( ...");
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS notification ( ...");
    // ...
}
```

**问题**：测试不是跑 MOD-01 的迁移文件，而是用 JDBC 手写 CREATE TABLE。这意味着：
- 迁移文件和测试建表 SQL 可能不同步
- 迁移文件的错误不会被测试发现
- 测试的 Schema 和生产的 Schema 可能不同

**建议**：改用 `@Sql(scripts = "classpath:db/migration/I-RI-*.sql")` 或 Flyway 测试配置。

#### 2️⃣ 测试断言偏弱

```java
@Test
void testSoftDelete() {
    jdbcTemplate.update("UPDATE project SET deleted_at = NOW() WHERE id = ?", seedProject.getId());
    List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM project WHERE id = ?", seedProject.getId());
    // 断言：list 为空（因为有 @SQLRestriction）
}
```

大部分测试做的是"操作+"+"查数据库看结果"，很少做"调用 API + 验证 HTTP 状态码 + 验证响应体"的端到端验证。

**对比**：524 行代码但有 30+ 测试用例，平均每个测试 ~17 行，过于精简。

#### 3️⃣ 缺少 Agent 相关测试

MOD-03 的 5 级隐式切换、7-fold hardening、网络字典等关键功能，在 V11IntegrationTest 中没有对应测试。

---

### ✅ 文档同步 Review

| 文档 | 同步内容 | 状态 |
|------|---------|------|
| `AGENT-FRAMEWORK-DECISION.md` | v1.1 决策同步 | ✅ |
| `ARCHITECTURE-v2.md` | 新增模块描述 | ✅ |
| `DB-SCHEMA-v2.md` | 新表 + 字段同步 | ✅ |
| `ENVIRONMENT-DEPENDENCIES.md` | 新增依赖 | ✅ |
| `LESSONS-Learned.md` | 新增 3 条教训 | ✅ |
| 6 份 `architecture/*.md` | 版本标注更新 | ✅ |
| `ARCH-DECOMPOSITION.md` | RI 拆解补充 | ✅ |

文档同步工作做得比较完整。

---

### ✅ 没问题的部分

| 文件 | 说明 |
|------|------|
| `ProjectForm.vue` | stage 字段适配 v1.1 | ✅ |
| `QaResponse.java` | confidenceBadge + projectSwitchHint 字段 | ✅ |
| `AgentEngine.java` | populateV11Fields 正确填充 response | ✅ |
| `ACCEPTANCE-GUIDE.md` | 验收指南，无问题 | ✅ |
| `reviews/2026-06-11-v1.1-review.md` | 阿根廷写的自审 | 已看，覆盖度 OK |

---

*审查完。*

*审查人：Sisyphus*
*MOD-05 干净。MOD-06 文档同步完整但测试质量偏弱（手写建表 SQL 而不是复用迁移文件、断言偏弱、缺少 MOD-03 的 Agent 测试）。*
