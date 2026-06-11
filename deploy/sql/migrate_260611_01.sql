-- =============================================================================
-- v1.1 生产库一次性迁移 — migrate_260611_01.sql
-- 合并: I-RI-22/24/25/26/28/31/33/34/35/37/39/43/44/45 (共 14 份)
-- 日期: 2026-06-11 | 作者: 阿根廷
--
-- 用法 (125 生产机):
--   1. 先备份: mysqldump -u archive_app -p archive_db > D:\archive\backup\before-v11.sql
--   2. mysql -u archive_app -p archive_db < D:\projects-online\deploy\sql\migrate_260611_01.sql
--   或在 mysql 终端: source D:/projects-online/deploy/sql/migrate_260611_01.sql
--
-- 注意:
--   - 勿对空库跑 init.sql; 本脚本用于 v1.0/v2 已有库升级到 v1.1
--   - 若报 Duplicate column / Duplicate key, 说明 Hibernate 或旧脚本已加过, 可忽略该段后继续
--   - notification 使用 is_read (与 Java 实体一致, 非 I-RI-39 原 ``read``)
-- =============================================================================

USE archive_db;

-- =============================================================================
-- I-RI-22: 置信度 3 级
-- =============================================================================

CREATE TABLE IF NOT EXISTS project_fact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(64) NOT NULL COMMENT 'mortgage/guarantor/settlement/milestone/risk/decision/transaction',
    fact_value TEXT,
    confidence DECIMAL(3,2) COMMENT 'LLM 置信度 0-1',
    evidence_material_id BIGINT COMMENT '证据材料 ID',
    evidence_snippet TEXT COMMENT '证据原文摘录',
    status VARCHAR(32) DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pf_project_type (project_id, fact_type),
    CONSTRAINT fk_pf_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目关键事实';

CREATE TABLE IF NOT EXISTS project_fact_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT 'INSERT/UPDATE/DELETE/ROLLBACK',
    fact_value TEXT,
    evidence TEXT,
    confidence DECIMAL(3,2),
    created_by BIGINT COMMENT '操作者 user_id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    INDEX idx_pfe_project_type (project_id, fact_type),
    CONSTRAINT fk_pfe_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键事实事件流(INSERT-only)';

ALTER TABLE project_fact
    ADD COLUMN confidence_level VARCHAR(16) COMMENT 'CONFIRMED/AI_INFERRED/PENDING_REVIEW' AFTER confidence;

ALTER TABLE project_fact_event
    ADD COLUMN confidence_level VARCHAR(16) COMMENT 'CONFIRMED/AI_INFERRED/PENDING_REVIEW' AFTER confidence;

UPDATE project_fact SET confidence_level = CASE
    WHEN confidence >= 0.85 THEN 'CONFIRMED'
    WHEN confidence >= 0.60 THEN 'AI_INFERRED'
    ELSE 'PENDING_REVIEW'
END WHERE confidence_level IS NULL;

UPDATE project_fact_event SET confidence_level = CASE
    WHEN confidence >= 0.85 THEN 'CONFIRMED'
    WHEN confidence >= 0.60 THEN 'AI_INFERRED'
    WHEN confidence IS NOT NULL THEN 'PENDING_REVIEW'
    ELSE 'PENDING_REVIEW'
END WHERE confidence_level IS NULL;

-- =============================================================================
-- I-RI-24: 议案附条件通过
-- =============================================================================

ALTER TABLE proposal
    ADD COLUMN condition_text TEXT COMMENT '附条件通过的条件描述' AFTER decision,
    ADD COLUMN condition_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/MET/UNMET' AFTER condition_text,
    ADD COLUMN condition_met_at DATETIME COMMENT '条件满足时间' AFTER condition_status;

-- =============================================================================
-- I-RI-25: 议案编号系列
-- =============================================================================

CREATE TABLE IF NOT EXISTS proposal_series (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL COMMENT '系列编码, 如 tx/xc',
    prefix VARCHAR(32) COMMENT '编号前缀',
    current_seq INT NOT NULL DEFAULT 0 COMMENT '当前自增序号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='议案编号系列';

ALTER TABLE proposal
    ADD COLUMN reserved_at DATETIME COMMENT '编号预留时间' AFTER updated_by,
    ADD COLUMN released_at DATETIME COMMENT '编号释放时间' AFTER reserved_at;

-- =============================================================================
-- I-RI-26: 网络查源字典种子
-- =============================================================================

INSERT IGNORE INTO dict_type (type_code, type_name, is_system, sort_order, enabled) VALUES
('network_dict_source', '网络查源', 1, 9, 1);

INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order, enabled) VALUES
('network_dict_source', 'baidu_baike',
 '{"baseUrl":"https://baike.baidu.com/api/openapi","timeout":5000}',
 0, 1, 1, 1),
('network_dict_source', 'wikipedia_zh',
 '{"baseUrl":"https://zh.wikipedia.org/w/api.php","timeout":5000}',
 0, 1, 2, 1);

-- =============================================================================
-- I-RI-28: 事实事件流字段 + INSERT-only 触发器
-- =============================================================================

ALTER TABLE project_fact_event
    ADD COLUMN owner_id BIGINT COMMENT '责任人 user_id' AFTER confidence_level,
    ADD COLUMN due_date DATE COMMENT '跟进截止日' AFTER owner_id,
    ADD COLUMN resolved_at DATETIME COMMENT '处置完成时间' AFTER due_date,
    ADD COLUMN resolution_note TEXT COMMENT '处置备注' AFTER resolved_at;

CREATE INDEX idx_owner_due ON project_fact_event(owner_id, due_date);

DROP TRIGGER IF EXISTS trg_fact_event_immutable;
DROP TRIGGER IF EXISTS trg_fact_event_no_delete;

DELIMITER $$
CREATE TRIGGER trg_fact_event_immutable
BEFORE UPDATE ON project_fact_event
FOR EACH ROW
BEGIN
  IF (NEW.event_type <> OLD.event_type
      OR NEW.fact_value <> OLD.fact_value
      OR (NEW.evidence <> OLD.evidence OR (NEW.evidence IS NULL) <> (OLD.evidence IS NULL))
      OR (NEW.confidence <> OLD.confidence OR (NEW.confidence IS NULL) <> (OLD.confidence IS NULL))
      OR NEW.project_id <> OLD.project_id
      OR NEW.fact_type <> OLD.fact_type
      OR NEW.created_at <> OLD.created_at
      OR (NEW.created_by <> OLD.created_by OR (NEW.created_by IS NULL) <> (OLD.created_by IS NULL))
      OR (NEW.confidence_level <> OLD.confidence_level OR (NEW.confidence_level IS NULL) <> (OLD.confidence_level IS NULL))) THEN
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

-- =============================================================================
-- I-RI-31: 软删 + version + 归档
-- =============================================================================

CREATE TABLE IF NOT EXISTS business_term (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    aliases VARCHAR(512),
    category VARCHAR(64),
    definition TEXT,
    standard_definition TEXT,
    source_url VARCHAR(512),
    data_mapping JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'draft' COMMENT 'draft/pending_review/active/deprecated',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bt_name (name),
    INDEX idx_bt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务术语字典';

ALTER TABLE project
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER updated_by,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by,
    ADD COLUMN archive_status VARCHAR(32) DEFAULT NULL COMMENT '归档状态' AFTER version;

ALTER TABLE proposal
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER released_at,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

ALTER TABLE material
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER updated_by,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by,
    ADD COLUMN archived_at DATETIME COMMENT '归档时间' AFTER version;

ALTER TABLE business_term
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER updated_at,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

ALTER TABLE audit_log
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER created_at,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

ALTER TABLE project_fact_event
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间(逻辑标记, 仍不可物理删)' AFTER resolution_note,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at;

ALTER TABLE user
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER updated_at,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

CREATE INDEX idx_deleted_at ON project(deleted_at);

-- =============================================================================
-- I-RI-33: 乐观锁默认值
-- =============================================================================

ALTER TABLE project MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本';
ALTER TABLE proposal MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本';
ALTER TABLE material MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本';

-- =============================================================================
-- I-RI-34: RBAC 5 角色 + user_role / project_member
-- =============================================================================

UPDATE role SET code = 'pm', name = '项目经理', description = '负责项目的项目经理,可创建项目、编辑材料、触发规则'
WHERE code = 'project_owner';

UPDATE role SET code = 'user', name = '普通用户', description = 'v1.0 兼容普通用户,基础查询、提交流转'
WHERE code = 'employee';

INSERT IGNORE INTO role (code, name, description, permissions) VALUES
('committee', '投委会委员', '审议、查看所有项目、查询知识库、查阅议案', JSON_ARRAY('project:read', 'proposal:read', 'qa:ask', 'task:view')),
('legal', '法务', '法务审查权限', JSON_ARRAY('project:read', 'legal:review')),
('secretary', '投委会秘书', '议案登记与秘书事务', JSON_ARRAY('project:read', 'proposal:write', 'task:write'));

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色多对多';

CREATE TABLE IF NOT EXISTS project_member (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_in_project VARCHAR(64) NOT NULL DEFAULT 'member' COMMENT 'member/owner/legal',
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员';

-- =============================================================================
-- I-RI-35: 审计日志 5 类 type
-- =============================================================================

ALTER TABLE audit_log
    ADD COLUMN type VARCHAR(32) COMMENT 'WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM' AFTER action,
    ADD COLUMN entity_subtype VARCHAR(32) COMMENT '实体子类型' AFTER entity_type;

UPDATE audit_log SET type = CASE
    WHEN action IN ('LOGIN', 'LOGOUT') THEN 'LOGIN'
    WHEN action LIKE 'EXPORT%' THEN 'EXPORT'
    WHEN action LIKE 'LLM%' THEN 'LLM'
    WHEN action LIKE 'SENSITIVE%' THEN 'SENSITIVE_VIEW'
    ELSE 'WRITE'
END WHERE type IS NULL;

CREATE INDEX idx_type ON audit_log(type, created_at);

-- =============================================================================
-- I-RI-37: 失败兜底日志
-- =============================================================================

CREATE TABLE IF NOT EXISTS failure_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    path VARCHAR(512) NOT NULL COMMENT '失败路径/端点',
    failure_type VARCHAR(64) NOT NULL COMMENT '失败类型枚举',
    error_msg TEXT COMMENT '错误信息',
    stack_trace TEXT COMMENT '堆栈',
    resolved TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已解决',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    resolved_at DATETIME COMMENT '解决时间',
    INDEX idx_path_resolved (path, resolved, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失败兜底日志';

-- =============================================================================
-- I-RI-39: 通知中心 (is_read 对齐 Java 实体)
-- =============================================================================

CREATE TABLE IF NOT EXISTS notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL COMMENT 'TODO/FACT/PROPOSAL/SYSTEM',
    title VARCHAR(256) NOT NULL,
    content TEXT,
    link VARCHAR(512) COMMENT '跳转链接',
    is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read, created_at),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知';

-- 若曾跑过旧 I-RI-39 (列名 read), 重命名为 is_read
SET @has_read_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'read'
);
SET @has_is_read_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'is_read'
);
SET @fix_notif_sql := IF(
    @has_read_col > 0 AND @has_is_read_col = 0,
    'ALTER TABLE notification CHANGE COLUMN `read` is_read TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT ''notification column OK'' AS msg'
);
PREPARE fix_notif_stmt FROM @fix_notif_sql;
EXECUTE fix_notif_stmt;
DEALLOCATE PREPARE fix_notif_stmt;

-- =============================================================================
-- I-RI-43: 业务术语英文对照
-- =============================================================================

ALTER TABLE business_term
    ADD COLUMN english_name VARCHAR(128) COMMENT '英文名称(可选)' AFTER name;

-- =============================================================================
-- I-RI-44: 旧系统导入批次
-- =============================================================================

CREATE TABLE IF NOT EXISTS import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(64) NOT NULL COMMENT '导入类型: project/proposal/...',
    total INT NOT NULL DEFAULT 0,
    success INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    created_by BIGINT COMMENT '操作者',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_created (type, created_at),
    CONSTRAINT fk_ib_user FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据导入批次';

CREATE TABLE IF NOT EXISTS import_error (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    row_num INT NOT NULL COMMENT 'Excel 行号',
    column_name VARCHAR(128) COMMENT '列名',
    error_msg VARCHAR(1000) NOT NULL,
    INDEX idx_batch (batch_id),
    CONSTRAINT fk_ie_batch FOREIGN KEY (batch_id) REFERENCES import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入错误明细';

-- =============================================================================
-- I-RI-45: 脱敏视图开关
-- =============================================================================

ALTER TABLE user
    ADD COLUMN sensitive_view_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许查看脱敏原始值' AFTER version;

UPDATE user SET sensitive_view_enabled = 1 WHERE username = 'admin' AND sensitive_view_enabled = 0;

-- =============================================================================
-- 验证
-- =============================================================================

SELECT 'failure_log' AS tbl, COUNT(*) AS cnt FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'failure_log'
UNION ALL
SELECT 'user_role', COUNT(*) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_role'
UNION ALL
SELECT 'notification', COUNT(*) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification'
UNION ALL
SELECT 'import_batch', COUNT(*) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batch';

SELECT 'migrate_260611_01 done' AS status, NOW() AS finished_at;
