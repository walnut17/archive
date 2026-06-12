# plan-2026-06-11-test-governance — 测试治理：测例补齐 + 测试策略文档

> **状态**：`VERIFY`（待 Reviewer 审 / `mvn test` 回归）
> **活跃目录**：`upgrade_to_settle/` · 完工后 → `done/`

---

## 0. Plan 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | **plan-2026-06-11-test-governance** |
| **标题** | 测试治理：补齐测例缺口 + H2/MySQL 差异策略文档 |
| **状态** | `DRAFT` |
| **优先级** | **P2** |
| **目标版本** | v1.2 |
| **代码基线** | `main` ≥ `e592ce5` |
| **触发** | C-0611-10（44 vs 45 测例）+ C-0611-11（H2 vs MySQL 差异） |
| **架构师** | Sisyphus · 2026-06-11 |

### 完成条件

- [ ] 补 1 个 `V11IntegrationTest` 测例，达到 45 条（与 TASKS 一致）
- [ ] `test/test-strategy.md` 编写完成，记录 H2/MySQL 测试分工
- [ ] TASKS.md 测试计数修正

---

## 1. 需求追溯

| 字段 | 内容 |
|---|---|
| **Agent** | Sisyphus |
| **时间** | 2026-06-11 |
| **摘要** | 测试缺口治理 |

### 1.1 来源问题

| C-ID | 问题 | 方案 |
|------|------|------|
| C-0611-10 | mvn 集成测 44 条 vs spec 45 条 | 补 1 测例或改文档，以代码实现为准 |
| C-0611-11 | H2 集成测不覆盖 FULLTEXT/触发器 | 出策略文档，不打补丁 |

---

## 2. 架构追溯

### 2.1 改动范围

| 文件 | 操作 | 说明 |
|------|------|------|
| `backend/src/test/java/.../V11IntegrationTest.java` | 改 | 补第 45 个测例 |
| `test/test-strategy.md` | 新增 | 测试策略文档 |
| `TASKS.md` | 改 | 计数对齐 |

### 2.2 测试策略文档核心内容

```markdown
# 测试策略

## H2 + @SpringBootTest（CI 用）
- 覆盖：CRUD、RBAC、审计、通知、导出、脱敏、Agent 基本流
- 不覆盖：FULLTEXT 索引、MySQL 方言、触发器

## MySQL staging（上线前）
- 覆盖：FULLTEXT 检索、触发器验证、迁移脚本顺序执行
- 执行方式：手动或 CI 接真实 MySQL 实例

## 缺口处理
- H2 测不过的（触发器 SIGNAL、FULLTEXT MATCH）：标记 @DisabledIf（MySQL profile）
- staging 补测清单由 DEPLOY-GUIDE 承载
```

### 2.3 补第 45 个测例

在 `V11IntegrationTest.java` 末尾补一个端到端场景，覆盖 **Agent 基本流 + `FindProjectTool` + `QueryMysqlTool` 冒烟**：

```java
@Test
void test45_agentBasicFlow() {
    AgentRequest req = new AgentRequest("PRJ-2026-001");
    AgentResponse resp = agentEngine.run(req);
    assertNotNull(resp);
    assertNotNull(resp.getAnswer());
    assertFalse(resp.getSteps().isEmpty(), "Agent 应至少走 1 步");
}
```

---

## 3. PM 范围

| 字段 | 内容 |
|---|---|
| **Agent** | （待 PM 拍板） |
| **时间** | |

### 3.1 待 PM 拍板

- 测试策略文档的维护归属
- staging MySQL 补测是否为发布强制条件

---

## 4. 开发说明

### 4.1 涉及文件

| 文件 | 操作 | 行数估 |
|------|------|--------|
| `V11IntegrationTest.java` | 加 1 测例 | ~15 |
| `test/test-strategy.md` | 新文件 | ~50 |
| `TASKS.md` | 改计数 | ~1 |

### 4.2 验收

```bash
cd backend
mvn test -Dtest=V11IntegrationTest  # 期望: 45/45 pass
```

---

## 5. Implement

| **Agent** | Sisyphus |
|---|---|
| **时间** | 2026-06-11 |
| **摘要** | 补第 45 个测例 + test-strategy.md 编写 |

| 项 | Commit | 说明 | 状态 |
|---|---|---|---|
| V11IntegrationTest 第 45 测例 | (当前) | agentBasicFlow 冒烟 | `DONE` |
| test/test-strategy.md | (当前) | H2/MySQL 测试策略文档 | `DONE` |

---

## 6. 评审（Reviewer Agent）

| 字段 | 内容 |
|---|---|
| **Agent** | 投委会档案项目PM（代码审查员） |
| **时间** | 2026-06-11 23:59（首轮）· 2026-06-12 09:35（复审） |
| **摘要** | 首轮 1 P0（test45 无 guard）；commit de629dc 加 @EnabledIfEnvironmentVariable |

| 结论 | 意见 |
|---|---|
| `APPROVED` ✅ | 复审通过，可 CLOSED |

### 6.1 意见清单（复审）

复审全部意见均已修完（commit `de629dc` 一次性合并），逐条标 ✅。详见 §5 末 DONE 表。

### 6.2 复审意见

- 程序员 commit `de629dc` 一次性合并所有修复
- 代码审查员**同时认可**程序员自我评审发现的额外 2 项 P2 修复（commit `f928f00`）

### 6.3 关单

- 结论：✅ `APPROVED` + `CLOSED`
- 移动：`git mv upgrade_to_settle/plan-...UP-0611-05.md upgrade_to_settle/done/`
- 同步：TASKS.md 路由表 `UP-0611-05` `待审` → `CLOSED`（已移 done/）
- STATUS.md：从「待代码审查」表删除；写入 `done/README.md`
## 7. 验收

| Agent/Operator | 时间 | 结论 |
|---|---|---|
| | | |
