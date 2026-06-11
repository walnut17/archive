# MOD-01 — 数据库迁移 + 7 表 ALTER

> **接手 agent 只需读本文 + `docs/DB-SCHEMA-v2.md` + 已有 `db/migration/` 即可开工**

---

## §0 模块目标

完成 v1.1 增量所有数据库层变更：11 个新 SQL 迁移文件 + `init.sql` 重建脚本同步 + 7 张表 ALTER + 1 个 DB 触发器（`project_fact_event` 锁死）。

**这是 v1.1 的关键路径第一步**，必须先完工，MOD-02/03/04/05 才能开。

---

## §1 涉及 RI（24 条全部 DB 部分）

| RI | DB 改动 | 优先级 |
|---|---|---|
| RI-22（置信度 3 级） | `project_fact.confidence_level` + `project_fact_event.confidence_level` + 回填 SQL | P0 |
| RI-24（决议变更） | `proposal.condition_text/condition_status/condition_met_at` | P0 |
| RI-25（编号预留） | 建 `proposal_series` 表 + `proposal.reserved_at/released_at` | P0 |
| RI-28（事实事件字段） | `project_fact_event.owner_id/due_date/resolved_at/resolution_note` + DB 触发器 | P0 |
| RI-31（软删） | 4 实体 + `audit_log/project_fact_event/user` 7 表 ALTER `deleted_at/deleted_by/status` 扩枚举 | P0 |
| RI-33（乐观锁） | 3 表 ALTER `version` INT DEFAULT 1 | P1 |
| RI-34（RBAC 5 角色） | 建 `user_role/project_member` 表 + `role` 扩 4 行 | P0 |
| RI-35（审计加强） | `audit_log.type/entity_subtype` + 回填 SQL | P1 |
| RI-37（失败兜底） | 建 `failure_log` 表 | P1 |
| RI-39（通知中心） | 建 `notification` 表 + `idx_user_read` 索引 | P1 |
| RI-43（业务术语英文） | `business_term.english_name` | P1 |
| RI-44（旧系统导入） | 建 `import_batch/import_error` 表 | P1 |
| RI-45（脱敏视图） | `user.sensitive_view_enabled` | P1 |

**RI-23/26/27/29/30/32/36/38/40/41/42/46/52/53/54/55/56/57/58/59/60/61/62/63/64/65/66/67/68/69 无 DB 改动**（仅 tool 配置 / 业务逻辑 / 前端）

---

## §2 涉及文件（独占清单）

**接手 agent 只允许改以下文件**：

### 新建（11 个 SQL）

```
backend/src/main/resources/db/migration/
├── I-RI-22-confidence-3level.sql        (新, ~40 行)
├── I-RI-24-condition-status.sql          (新, ~50 行)
├── I-RI-25-proposal-series.sql           (新, ~60 行)
├── I-RI-28-fact-event-fields.sql         (新, ~40 行)
├── I-RI-31-soft-delete.sql               (新, ~100 行, 7 表 ALTER)
├── I-RI-33-optimistic-lock.sql           (新, ~30 行)
├── I-RI-34-rbac-5-roles.sql              (新, ~80 行)
├── I-RI-35-audit-type.sql                (新, ~40 行)
├── I-RI-37-failure-log.sql               (新, ~50 行)
├── I-RI-39-notification.sql              (新, ~50 行)
├── I-RI-43-english-name.sql              (新, ~20 行)
├── I-RI-44-import-batch.sql              (新, ~60 行)
└── I-RI-45-masking.sql                   (新, ~20 行)
```

### 修改（1 个）

```
backend/src/main/resources/db/init.sql    (append 7 ALTER + 7 新表 CREATE, ~730 行)
```

**总计**：~730 行 SQL，纯增 ALTER/CREATE/TRIGGER，**零 DELETE 现有数据**。

---

## §3 设计要点

### 3.1 SQL 命名规范（沿用 v1.0 Plan I）

- 前缀 `I-` 沿用 v1.0
- 中段 `RI-N` 跟 `docs/requirements/ARCH-DECOMPOSITION.md` RI 编号严格对齐
- 后段 `kebab-case` 简述
- 执行顺序：按数字序（I-RI-22 → I-RI-45）

### 3.2 命名约定 / 默认值

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `deleted_at` | DATETIME | NULL | 软删时间，NULL = 未删 |
| `deleted_by` | BIGINT | NULL | 软删操作者 user_id |
| `version` | INT | 1 | 乐观锁，UPDATE 自动 +1 |
| `status` 扩枚举 | VARCHAR(16) | NULL | 新增 `'deleted'` / `'revoked'` / `'archived'` |
| `condition_status` | VARCHAR(16) | `'NONE'` | 注意是 NONE 不是 PENDING（避免历史数据歧义） |
| `confidence_level` | VARCHAR(16) | NULL | 3 枚举：CONFIRMED/AI_INFERRED/PENDING_REVIEW |
| `type` (audit_log) | VARCHAR(32) | NULL | 5 枚举：WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM |
| `sensitive_view_enabled` | BOOLEAN | false | admin seed 强制 true |

### 3.3 DB 触发器（关键）

```sql
-- I-RI-28 / I-RI-31 合并段
DELIMITER $$
CREATE TRIGGER trg_fact_event_immutable
BEFORE UPDATE ON project_fact_event
FOR EACH ROW
BEGIN
  -- 允许改 4 字段：owner_id / due_date / resolved_at / resolution_note
  -- 其他字段改 = 抛错
  IF (NEW.event_type <> OLD.event_type
      OR NEW.fact_value <> OLD.fact_value
      OR NEW.evidence <> OLD.evidence
      OR NEW.confidence <> OLD.confidence
      OR NEW.project_id <> OLD.project_id
      OR NEW.fact_type <> OLD.fact_type
      OR NEW.created_at <> OLD.created_at
      OR NEW.created_by <> OLD.created_by) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'project_fact_event is INSERT-only (only owner_id/due_date/resolved_at/resolution_note may be updated)';
  END IF;
END$$

CREATE TRIGGER trg_fact_event_no_delete
BEFORE DELETE ON project_fact_event
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'project_fact_event is INSERT-only (DELETE forbidden)';
END$$
DELIMITER ;
```

### 3.4 回填 SQL（关键）

| 文件 | 回填 |
|---|---|
| `I-RI-22-confidence-3level.sql` | `UPDATE project_fact SET confidence_level = CASE WHEN confidence >= 0.85 THEN 'CONFIRMED' WHEN confidence >= 0.60 THEN 'AI_INFERRED' ELSE 'PENDING_REVIEW' END WHERE confidence_level IS NULL;` |
| `I-RI-35-audit-type.sql` | `UPDATE audit_log SET type = CASE WHEN action IN ('LOGIN','LOGOUT') THEN 'LOGIN' WHEN action LIKE 'EXPORT%' THEN 'EXPORT' WHEN action LIKE 'LLM%' THEN 'LLM' WHEN action LIKE 'SENSITIVE%' THEN 'SENSITIVE_VIEW' ELSE 'WRITE' END WHERE type IS NULL;` |
| `I-RI-34-rbac-5-roles.sql` | `INSERT IGNORE INTO role(name) VALUES ('pm'), ('legal'), ('committee'), ('secretary');` |

### 3.5 索引策略

```sql
-- I-RI-39-notification.sql
CREATE INDEX idx_user_read ON notification(user_id, read, created_at);

-- I-RI-37-failure-log.sql
CREATE INDEX idx_path_resolved ON failure_log(path, resolved, occurred_at);

-- I-RI-44-import-batch.sql
CREATE INDEX idx_type_created ON import_batch(type, created_at);
CREATE INDEX idx_batch ON import_error(batch_id);

-- I-RI-25-proposal-series.sql
CREATE UNIQUE INDEX uq_code ON proposal_series(code);

-- I-RI-28-fact-event-fields.sql (扩 project_fact_event 索引)
CREATE INDEX idx_owner_due ON project_fact_event(owner_id, due_date);

-- I-RI-35-audit-type.sql (扩 audit_log 索引)
CREATE INDEX idx_type ON audit_log(type, created_at);

-- I-RI-31-soft-delete.sql (扩 project 索引)
CREATE INDEX idx_deleted_at ON project(deleted_at);
```

---

## §4 验收

### 4.1 编译验证（不需要 mvn，仅 SQL）

```bash
cd /workspace/projects-online
# 顺序执行所有迁移
for f in backend/src/main/resources/db/migration/I-RI-*.sql; do
  echo "=== $f ==="
  mysql -u root archive_db < "$f" || { echo "FAIL: $f"; exit 1; }
done
echo "All migrations OK"

# init.sql 重建（验证完整可重建）
mysql -u root -e "DROP DATABASE archive_db; CREATE DATABASE archive_db;"
mysql -u root archive_db < backend/src/main/resources/db/init.sql
# 期望 0 错
```

### 4.2 表结构验证

```bash
mysql -u root archive_db -e "
-- 7 表含 deleted_at/deleted_by
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='archive_db'
  AND COLUMN_NAME IN ('deleted_at','deleted_by','version')
  AND TABLE_NAME IN ('project','proposal','material','business_term','audit_log','project_fact_event','user')
ORDER BY TABLE_NAME, COLUMN_NAME;
-- 期望：21 行（每表 3 字段）

-- 7 张新表存在
SHOW TABLES LIKE 'notification';
SHOW TABLES LIKE 'failure_log';
SHOW TABLES LIKE 'user_role';
SHOW TABLES LIKE 'project_member';
SHOW TABLES LIKE 'import_batch';
SHOW TABLES LIKE 'import_error';
SHOW TABLES LIKE 'proposal_series';
-- 期望：每个 SHOW 返回 1 行

-- role 表有 6 行
SELECT COUNT(*) FROM role;  -- 期望：6

-- 触发器存在
SHOW TRIGGERS LIKE 'project_fact_event';
-- 期望：2 行（trg_fact_event_immutable + trg_fact_event_no_delete）
"
```

### 4.3 回填验证

```bash
mysql -u root archive_db -e "
-- 历史 data 都带 confidence_level
SELECT COUNT(*) FROM project_fact WHERE confidence_level IS NULL;
-- 期望：0

-- 历史 audit_log 都带 type
SELECT COUNT(*) FROM audit_log WHERE type IS NULL;
-- 期望：0
"
```

### 4.4 触发器验证（应用层之前）

```sql
-- 应该成功（白名单字段）
UPDATE project_fact_event SET owner_id = 1 WHERE id = 1;

-- 应该失败（非白名单字段）
UPDATE project_fact_event SET fact_value = '篡改' WHERE id = 1;
-- 期望：ERROR 1644 (45000): project_fact_event is INSERT-only ...

-- 应该失败（DELETE）
DELETE FROM project_fact_event WHERE id = 1;
-- 期望：ERROR 1644 (45000): project_fact_event is INSERT-only ...
```

### 4.5 完工 checklist

- [ ] 13 个 SQL 文件全部新建并 commit
- [ ] `init.sql` 同步 append 完毕
- [ ] §4.1 顺序执行 0 错
- [ ] §4.2 表结构 21 行 + 7 张新表 + 6 role + 2 触发器
- [ ] §4.3 回填 0 NULL
- [ ] §4.4 触发器白名单生效
- [ ] `git add + commit + push` 到 main
- [ ] 改 `TASKS.md` 状态 → `已完成`

---

## §5 踩坑预警

### 5.1 MySQL 版本必须 ≥ 8.0

SIGNAL 语法 MySQL 5.7 不支持。本项目 `MySQL 8.0+`（见 `docs/ENVIRONMENT-DEPENDENCIES.md`）。

### 5.2 DELIMITER 在 JDBC 迁移中可能失效

Flyway / Liquibase 不识别 `DELIMITER` 指令。本项目用裸 SQL `mysql < file.sql` 手动跑，**OK**。如未来引入 Flyway，需把触发器拆成单行 SQL（去掉 BEGIN/END 块）。

### 5.3 回填 SQL 必须加 WHERE ... IS NULL

避免重复跑迁移时把已经填过的数据再覆盖一次（虽然值相同，但是稳妥起见）。

### 5.4 7 表 ALTER 顺序不能乱

**先** `I-RI-31-soft-delete.sql`（含 `deleted_at`） → **再** 任何引用 `deleted_at` 的迁移。本模块内部已经按数字序排好，**不要**打乱。

### 5.5 `condition_status DEFAULT 'NONE'`

业务方 §13.1.3 写的是 "PENDING"，但 PENDING 暗示"待处理"，对历史未附条件决议会误导。沿用 v1.0 §0 风险 C-5 拍板：**默认值 `'NONE'`**（明示"非附条件"）。

### 5.6 严禁 DELETE 现有数据

回填 SQL 只用 `UPDATE`，不用 `DELETE`。触发器锁死 `project_fact_event` 不可删/不可改非白名单字段。

---

## §6 接口契约

### 6.1 给 MOD-02（核心域）

- **3 实体 ALTER** 后字段可用：`Project.deletedAt / deletedBy / version / archiveStatus`、`Proposal.conditionText / conditionStatus / conditionMetAt / reservedAt / releasedAt / version / deletedAt / deletedBy`、`Material.deletedAt / deletedBy / version / archivedAt`
- **`role` 表 6 行**：`admin / user / pm / legal / committee / secretary`，既有 admin/user 不动
- **`user_role` / `project_member`** 多对多表已建
- **`project_fact_event` 触发器**：白名单 4 字段（owner_id/due_date/resolved_at/resolution_note）可改

### 6.2 给 MOD-03（Agent 工具）

- **`project_fact.confidence_level`** + 回填完毕
- **`project_fact_event.confidence_level`** + 回填完毕
- **`project_fact_event` 触发器**：阻止非白名单字段改（应用层 + DB 双保险）

### 6.3 给 MOD-04（业务功能）

- **7 张新表**（notification / failure_log / user_role / project_member / import_batch / import_error / proposal_series）已建
- **`audit_log.type` 字段** + 回填完毕
- **`user.sensitive_view_enabled`** 字段已加

### 6.4 给 MOD-06（文档/测试/验收）

- 13 个 SQL 文件路径在 `backend/src/main/resources/db/migration/I-RI-*.sql`
- `init.sql` 是重建脚本（DB 损毁时 `mysql < init.sql` 完整恢复）
- 所有 ALTER 是 nullable，默认值安全，**零回归**

---

*本模块由 DBA / 后端 agent 接手。MOD-02/03/04/05 必须等本模块完工。*