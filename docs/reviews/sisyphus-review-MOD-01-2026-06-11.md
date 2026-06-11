# MOD-01 代码审查报告 — 数据库迁移

> 审查人：Sisyphus | 日期：2026-06-11 | 审查范围：MOD-01（13 SQL 迁移文件 + init.sql 同步）
> 执行人：阿根廷 | 基线 commit：`8b1aa7c`

---

## 0. 总体评价

**质量尚可，但有两个硬伤必须修**。整体结构一致、命名规范、索引策略合理。但两个 P0 漏项（丢 `version` 字段、丢 `committee` 角色）会直接导致 MOD-02 等下游模块的功能异常，必须回修后才能 merge 到 minimax。

13 个文件中 10 个干净，3 个有问题。

---

## 1. 🔴 严重问题（必须修）

### 1.1 `I-RI-22-confidence-3level.sql` — 两张新表缺少 `version` 字段

**问题**：`project_fact` 和 `project_fact_event` 的 CREATE TABLE 语句中没有包含 `version INT NOT NULL DEFAULT 1`，但 `init.sql` 里有。

**证据**：

```sql
-- I-RI-22.sql（阿根廷版，漏了 version）
CREATE TABLE IF NOT EXISTS project_fact (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  ...
  confidence DECIMAL(3,2) DEFAULT NULL,
  confidence_level VARCHAR(16) DEFAULT NULL,
  source VARCHAR(64) DEFAULT NULL,
  ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- init.sql（基线版，有 version）
CREATE TABLE IF NOT EXISTS project_fact (
  ...
  version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  ...
);
```

**影响**：如果现有数据库通过 Flyway 顺序执行迁移（而非跑 `init.sql` 重建），`project_fact` 和 `project_fact_event` 就会没有 `version` 字段。MOD-02 的 JPA Entity 加了 `@Version` 注解后，应用层会报 `Spring Data JPA optimistic locking requires a version property` 异常，直接 500。

**修复**：在两张表的 CREATE TABLE 末尾加 `version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本'`。

---

### 1.2 `I-RI-34-rbac-5-roles.sql` — 缺少 `committee` 角色

**问题**：INSERT IGNORE 只插入了 `legal` 和 `secretary`，`committee` 角色漏了。

```sql
INSERT IGNORE INTO role (code, name, description, permissions) VALUES
('legal', '法务', '法务审查权限', JSON_ARRAY('project:read', 'legal:review')),
('secretary', '投委会秘书', '议案登记与秘书事务', JSON_ARRAY('project:read', 'proposal:write', 'task:write'));
-- 注意：没有 committee！
```

`init.sql` 种子数据中是 6 个角色齐全的：

```sql
INSERT INTO role (code, name, description, permissions) VALUES
('admin', '系统管理员', ...),
('committee', '投委会委员', ...),   -- ← 有这个
('pm', '项目经理', ...),
('user', '普通用户', ...),
('legal', '法务', ...),
('secretary', '投委会秘书', ...);
```

**影响**：
- `init.sql` 重建的库没事（6 角色齐全）
- **已有库通过迁移升级的**，`committee` 角色永远不会被创建
- MOD-02 的 RBAC 逻辑如果依赖 `committee` 角色来授权，迁移后的库会找不到该角色
- TASKS.md 写的验收条件是"6 role"，但这个迁移跑完只有 5 个（或更少，取决于基线）

**修复**：加一行 `('committee', '投委会委员', '审议权限', JSON_ARRAY('project:read', 'proposal:read', 'qa:ask', 'task:view'))` 到 INSERT IGNORE。

---

## 2. ⚠️ 次要问题（建议修复）

### 2.1 `I-RI-28-fact-event-fields.sql` — 触发器缺少 NULL 安全比较

**问题**：触发器的字段比较用了 `<>`，但 MySQL 中 `NULL <> NULL` 返回的是 TRUE 而非 FALSE——这意味着当 `evidence`、`confidence`、`created_by` 为 NULL 时，触发器会误判为"字段被改"而抛错。

**迁移版本**（阿根廷版）：

```sql
IF (NEW.evidence <> OLD.evidence
    OR NEW.confidence <> OLD.confidence
    OR NEW.created_by <> OLD.created_by
    OR NEW.confidence_level <> OLD.confidence_level) THEN
  SIGNAL ...;
END IF;
```

**init.sql 版本**（正确版）：

```sql
IF ((NEW.evidence <> OLD.evidence) OR ((NEW.evidence IS NULL) <> (OLD.evidence IS NULL))
    OR (NEW.confidence <> OLD.confidence) OR ((NEW.confidence IS NULL) <> (OLD.confidence IS NULL))
    OR (NEW.created_by <> OLD.created_by) OR ((NEW.created_by IS NULL) <> (OLD.created_by IS NULL))
    OR (NEW.confidence_level <> OLD.confidence_level) OR ((NEW.confidence_level IS NULL) <> (OLD.confidence_level IS NULL))) THEN
  SIGNAL ...;
END IF;
```

**影响**：低。实际业务中这些字段很少同时为 NULL。但如果有空值场景，这个触发器会误触发，导致合法的 `UPDATE` 被拦。

**修复**：为 `evidence`、`confidence`、`created_by`、`confidence_level` 四个字段加 `(field IS NULL) <> (OLD.field IS NULL)` 判断。

---

## 3. 📋 文档偏差（记录，非 bug）

### 3.1 新表数量偏差

MOD-01 spec 写的"7 张新表"，实际交付了 10 张（`project_fact`、`project_fact_event`、`proposal_series`、`business_term`、`user_role`、`project_member`、`failure_log`、`notification`、`import_batch`、`import_error`）。spec 可能是把简单表合并计数了，非功能问题。

### 3.2 迁移文件数量偏差

MOD-01 spec 预期 11 个 SQL 文件，实际交付 13 个。多出的 2 个是 `business_term` 和 `proposal_series` 相关的拆分，属于更细致的拆分。无实质影响。

---

## 4. ✅ 干净的文件（没有问题）

| 文件 | 备注 |
|------|------|
| `I-RI-24-condition-status.sql` | 干净，`condition_status DEFAULT 'NONE'` 正确 |
| `I-RI-25-proposal-series.sql` | 干净，`uq_code` 唯一索引正确 |
| `I-RI-31-soft-delete.sql` | 干净，7 表 ALTER 所有列正确 |
| `I-RI-33-optimistic-lock.sql` | 干净，`MODIFY COLUMN version` 强制 DEFAULT 1 |
| `I-RI-35-audit-type.sql` | 干净，回填 SQL 用 `WHERE type IS NULL` 安全 |
| `I-RI-37-failure-log.sql` | 干净，索引策略正确 |
| `I-RI-39-notification.sql` | 干净，`read` 关键字用反引号包裹正确 |
| `I-RI-43-english-name.sql` | 干净 |
| `I-RI-44-import-batch.sql` | 干净，FK `ON DELETE CASCADE` 合理 |
| `I-RI-45-masking.sql` | 干净，admin 回填正确 |

---

## 5. 与 init.sql 的一致性核对

| 检查项 | 结果 |
|--------|------|
| 列类型、长度、默认值 | ✅ 全部一致 |
| FK 引用 | ✅ 全部指向已存在表 |
| 索引定义 | ✅ 全部一致 |
| 字符集 | ✅ 全 `utf8mb4 / utf8mb4_unicode_ci` |
| 触发器 | ⚠️ NULL 安全比较不一致 |
| `version` 字段（project_fact / project_fact_event） | ❌ 迁移漏了 |
| `committee` 角色 | ❌ 迁移漏了 |

---

## 6. 验收对照表

| TASKS.md 验收条件 | 结果 | 说明 |
|------------------|------|------|
| 13 个 SQL 文件新建并 commit | ✅ | 实际 13 个 |
| `init.sql` 同步 append 完毕 | ✅ | 内容对齐，除上述两个漏项 |
| 顺序执行 0 错 | 🟡 | 需修完上面两个 P0 再验证 |
| 7 表 21 字段 | ✅ | 7 表 ALTER 完整 |
| 7 新表 | ✅ | 实际 10 张 |
| 6 role | ❌ | 迁移只加了 2 个，整体靠 init.sql 才够 6 个 |
| 2 触发器 | ✅ | 但 NULL 安全比较有问题 |
| 回填 0 NULL | ✅ | 所有回填 SQL 正确 |
| 触发器白名单生效 | 🟡 | 需先修 NULL 比较问题 |

---

## 7. 改进建议

### 7.1 工具链建议

当前项目用裸 `mysql < file.sql` 跑迁移，没有用 Flyway/Liquibase。13 个文件靠文件名前缀排序执行，人肉保证顺序。**建议考虑引入 Flyway**，原因：
- 自动跟踪哪些迁移已执行（避免了重复跑 `INSERT IGNORE` 这种"假装幂等"的写法）
- 版本回滚能力
- 和 Spring Boot 集成方便（`spring.flyway.locations`）

### 7.2 测试建议

这些迁移文件都是纯 SQL，无法通过 `mvn test` 验证。建议加一个自动化 SQL 验证脚本：
- 在 MySQL 测试实例（或 H2）上顺序执行每个迁移
- 跑完后执行 MOD-01 spec §4.2 的检查 SQL
- 失败则阻断 CI

---

*审查完。*

*审查人：Sisyphus*
*立场：SQL 质量整体 OK，但两个 P0 漏项（version 字段 + committee 角色）必须修，否则下游 MOD-02 会崩。*
