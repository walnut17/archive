-- =============================================================================
-- 生产库一次性迁移 — migrate_260611_01.sql (完整版)
-- 日期: 2026-06-11 | 作者: 阿根廷
--
-- 覆盖范围:
--   A. v1/v2 旧表补丁 (material_version / project / user / llm_call_log 等)
--   B. v1.1 旧表 ALTER (project / proposal / material / user / audit_log / business_term / fact*)
--   C. v1.1 新表 CREATE + 种子数据 + 触发器 + 回填
--
-- 用法:
--   mysqldump -u archive_app -p archive_db > D:\archive\backup\before-v11.sql
--   mysql -u archive_app -p archive_db < D:\projects-online\deploy\sql\migrate_260611_01.sql
--   或: source D:/projects-online/deploy/sql/migrate_260611_01.sql
--
-- 特性: 列/索引已存在则跳过 (可重复执行, Duplicate 报错大幅减少)
-- 勿对空库跑 init.sql; 本脚本用于已有 archive_db 升级
-- =============================================================================

USE archive_db;

-- =============================================================================
-- 工具: 列不存在才 ADD (MySQL 客户端 source 友好)
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE sp_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE sp_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_index_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_index_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- =============================================================================
-- A1. v2-schema: material_version 全文检索字段
-- =============================================================================

CALL sp_add_column_if_missing('material_version', 'parsed_text',
    'LONGTEXT NULL COMMENT ''解析后纯文本(M2 FULLTEXT)''');
CALL sp_add_index_if_missing('material_version', 'ft_parsed_text',
    'ALTER TABLE material_version ADD FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram');

-- =============================================================================
-- A2. Plan I: project.customer_name + FULLTEXT
-- =============================================================================

CALL sp_add_column_if_missing('project', 'customer_name',
    'VARCHAR(256) NULL COMMENT ''客户名称(FindProjectTool)''');
CALL sp_add_index_if_missing('project', 'ft_name_cust',
    'ALTER TABLE project ADD FULLTEXT INDEX ft_name_cust (name, customer_name) WITH PARSER ngram');

-- =============================================================================
-- A3. v2-schema: project 金额 + 归档状态
-- =============================================================================

CALL sp_add_column_if_missing('project', 'initial_amount',
    'DECIMAL(18,2) NULL COMMENT ''初始总金额(元)''');
CALL sp_add_column_if_missing('project', 'remaining_amount',
    'DECIMAL(18,2) NULL COMMENT ''剩余金额(元)''');
CALL sp_add_column_if_missing('project', 'archive_status',
    'VARCHAR(32) NULL COMMENT ''归档/在档状态''');
CALL sp_add_index_if_missing('project', 'idx_project_archive_status',
    'ALTER TABLE project ADD INDEX idx_project_archive_status (archive_status)');

-- =============================================================================
-- A4. v2-schema: user 登录限流
-- =============================================================================

CALL sp_add_column_if_missing('user', 'failed_login_count',
    'INT NOT NULL DEFAULT 0 COMMENT ''连续登录失败次数''');
CALL sp_add_column_if_missing('user', 'lockout_until',
    'DATETIME NULL COMMENT ''账号锁定截止时间''');

-- =============================================================================
-- A5. Plan G: llm_call_log (表 + token 列)
-- =============================================================================

CREATE TABLE IF NOT EXISTS llm_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL COMMENT '调用用户 ID',
    username VARCHAR(64) NULL COMMENT '用户名冗余',
    scenario VARCHAR(64) NOT NULL COMMENT '场景',
    model VARCHAR(64) NOT NULL COMMENT '模型名',
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    duration_ms INT NOT NULL COMMENT '耗时毫秒',
    status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED',
    error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_scenario_created (scenario, created_at),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 调用日志';

CALL sp_add_column_if_missing('llm_call_log', 'prompt_tokens', 'INT NULL');
CALL sp_add_column_if_missing('llm_call_log', 'completion_tokens', 'INT NULL');
CALL sp_add_column_if_missing('llm_call_log', 'total_tokens', 'INT NULL');

-- =============================================================================
-- A6. Plan I: 多轮对话记忆表
-- =============================================================================

CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话 ID',
    content TEXT NOT NULL COMMENT '消息内容',
    type VARCHAR(16) NOT NULL COMMENT 'user/assistant/system/tool',
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_timestamp (conversation_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 多轮对话记忆';

-- =============================================================================
-- B1. I-RI-24: proposal 附条件通过 (旧表 ALTER)
-- =============================================================================

CALL sp_add_column_if_missing('proposal', 'condition_text',
    'TEXT NULL COMMENT ''附条件通过的条件描述''');
CALL sp_add_column_if_missing('proposal', 'condition_status',
    'VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''NONE/PENDING/MET/UNMET''');
CALL sp_add_column_if_missing('proposal', 'condition_met_at',
    'DATETIME NULL COMMENT ''条件满足时间''');

-- =============================================================================
-- B2. I-RI-25: proposal 编号预留 (旧表 ALTER)
-- =============================================================================

CALL sp_add_column_if_missing('proposal', 'reserved_at',
    'DATETIME NULL COMMENT ''编号预留时间''');
CALL sp_add_column_if_missing('proposal', 'released_at',
    'DATETIME NULL COMMENT ''编号释放时间''');

-- =============================================================================
-- B3. I-RI-31: 7 表软删 + version (旧表 ALTER)
-- =============================================================================

-- project
CALL sp_add_column_if_missing('project', 'deleted_at', 'DATETIME NULL COMMENT ''软删时间''');
CALL sp_add_column_if_missing('project', 'deleted_by', 'BIGINT NULL COMMENT ''软删操作者''');
CALL sp_add_column_if_missing('project', 'version', 'INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本''');
CALL sp_add_index_if_missing('project', 'idx_deleted_at',
    'ALTER TABLE project ADD INDEX idx_deleted_at (deleted_at)');

-- proposal
CALL sp_add_column_if_missing('proposal', 'deleted_at', 'DATETIME NULL COMMENT ''软删时间''');
CALL sp_add_column_if_missing('proposal', 'deleted_by', 'BIGINT NULL COMMENT ''软删操作者''');
CALL sp_add_column_if_missing('proposal', 'version', 'INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本''');

-- material
CALL sp_add_column_if_missing('material', 'deleted_at', 'DATETIME NULL COMMENT ''软删时间''');
CALL sp_add_column_if_missing('material', 'deleted_by', 'BIGINT NULL COMMENT ''软删操作者''');
CALL sp_add_column_if_missing('material', 'version', 'INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本''');
CALL sp_add_column_if_missing('material', 'archived_at', 'DATETIME NULL COMMENT ''归档时间''');

-- user
CALL sp_add_column_if_missing('user', 'deleted_at', 'DATETIME NULL COMMENT ''软删时间''');
CALL sp_add_column_if_missing('user', 'deleted_by', 'BIGINT NULL COMMENT ''软删操作者''');
CALL sp_add_column_if_missing('user', 'version', 'INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本''');

-- audit_log (旧表可能来自 v2-schema, 仅 audit 列)
CALL sp_add_column_if_missing('audit_log', 'deleted_at', 'DATETIME NULL COMMENT ''软删时间''');
CALL sp_add_column_if_missing('audit_log', 'deleted_by', 'BIGINT NULL COMMENT ''软删操作者''');
CALL sp_add_column_if_missing('audit_log', 'version', 'INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本''');

-- business_term (表可能尚未存在, 先建表再加列 — 见 C 节)

-- =============================================================================
-- B4. I-RI-33: 乐观锁默认值 (旧表 MODIFY)
-- =============================================================================

-- 仅当 version 列已存在时 MODIFY (忽略不存在的情况)
SET @has_pv = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='project' AND COLUMN_NAME='version');
SET @sql = IF(@has_pv > 0,
    'ALTER TABLE project MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本''',
    'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_ppv = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='proposal' AND COLUMN_NAME='version');
SET @sql = IF(@has_ppv > 0,
    'ALTER TABLE proposal MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本''',
    'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_mv = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='material' AND COLUMN_NAME='version');
SET @sql = IF(@has_mv > 0,
    'ALTER TABLE material MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本''',
    'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- =============================================================================
-- B5. I-RI-35: audit_log 审计分类 (旧表 ALTER)
-- =============================================================================

CALL sp_add_column_if_missing('audit_log', 'type',
    'VARCHAR(32) NULL COMMENT ''WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM''');
CALL sp_add_column_if_missing('audit_log', 'entity_subtype',
    'VARCHAR(32) NULL COMMENT ''实体子类型''');
CALL sp_add_index_if_missing('audit_log', 'idx_type',
    'ALTER TABLE audit_log ADD INDEX idx_type (type, created_at)');

UPDATE audit_log SET type = CASE
    WHEN action IN ('LOGIN', 'LOGOUT') THEN 'LOGIN'
    WHEN action LIKE 'EXPORT%' THEN 'EXPORT'
    WHEN action LIKE 'LLM%' THEN 'LLM'
    WHEN action LIKE 'SENSITIVE%' THEN 'SENSITIVE_VIEW'
    ELSE 'WRITE'
END WHERE type IS NULL;

-- =============================================================================
-- B6. I-RI-45: user.sensitive_view_enabled (旧表 ALTER)
-- =============================================================================

CALL sp_add_column_if_missing('user', 'sensitive_view_enabled',
    'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否允许查看脱敏原始值''');

UPDATE user SET sensitive_view_enabled = 1 WHERE username = 'admin' AND sensitive_view_enabled = 0;

-- (business_term / user_role 列补丁见 C3/C4 建表后)

-- =============================================================================
-- C1. I-RI-22: project_fact / project_fact_event (新表 + 旧表 ALTER)
-- =============================================================================

CREATE TABLE IF NOT EXISTS project_fact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(64) NOT NULL,
    fact_value TEXT,
    confidence DECIMAL(3,2),
    evidence_material_id BIGINT,
    evidence_snippet TEXT,
    status VARCHAR(32) DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pf_project_type (project_id, fact_type),
    CONSTRAINT fk_pf_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_fact_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    fact_value TEXT,
    evidence TEXT,
    confidence DECIMAL(3,2),
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    INDEX idx_pfe_project_type (project_id, fact_type),
    CONSTRAINT fk_pfe_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL sp_add_column_if_missing('project_fact', 'confidence_level',
    'VARCHAR(16) NULL COMMENT ''CONFIRMED/AI_INFERRED/PENDING_REVIEW''');
CALL sp_add_column_if_missing('project_fact_event', 'confidence_level',
    'VARCHAR(16) NULL COMMENT ''CONFIRMED/AI_INFERRED/PENDING_REVIEW''');

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
-- C2. I-RI-28 + I-RI-31: project_fact_event 扩展字段 (旧表 ALTER)
-- =============================================================================

CALL sp_add_column_if_missing('project_fact_event', 'owner_id',
    'BIGINT NULL COMMENT ''责任人 user_id''');
CALL sp_add_column_if_missing('project_fact_event', 'due_date',
    'DATE NULL COMMENT ''跟进截止日''');
CALL sp_add_column_if_missing('project_fact_event', 'resolved_at',
    'DATETIME NULL COMMENT ''处置完成时间''');
CALL sp_add_column_if_missing('project_fact_event', 'resolution_note',
    'TEXT NULL COMMENT ''处置备注''');
CALL sp_add_column_if_missing('project_fact_event', 'deleted_at',
    'DATETIME NULL COMMENT ''软删标记''');
CALL sp_add_column_if_missing('project_fact_event', 'deleted_by',
    'BIGINT NULL COMMENT ''软删操作者''');
CALL sp_add_index_if_missing('project_fact_event', 'idx_owner_due',
    'ALTER TABLE project_fact_event ADD INDEX idx_owner_due (owner_id, due_date)');

-- =============================================================================
-- C3. I-RI-31: business_term 新表 + 软删列
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
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bt_name (name),
    INDEX idx_bt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL sp_add_column_if_missing('business_term', 'english_name',
    'VARCHAR(128) NULL COMMENT ''英文名称''');
CALL sp_add_column_if_missing('business_term', 'deleted_at', 'DATETIME NULL');
CALL sp_add_column_if_missing('business_term', 'deleted_by', 'BIGINT NULL');
CALL sp_add_column_if_missing('business_term', 'version', 'INT NOT NULL DEFAULT 1');

-- =============================================================================
-- C4. I-RI-25: proposal_series
-- C5. I-RI-34: RBAC 角色 + user_role / project_member
-- C6. I-RI-37: failure_log
-- C7. I-RI-39: notification
-- C8. I-RI-44: import_batch / import_error
-- =============================================================================

CREATE TABLE IF NOT EXISTS proposal_series (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    prefix VARCHAR(32),
    current_seq INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE role SET code = 'pm', name = '项目经理',
    description = '负责项目的项目经理,可创建项目、编辑材料、触发规则'
WHERE code = 'project_owner';

UPDATE role SET code = 'user', name = '普通用户',
    description = 'v1.0 兼容普通用户,基础查询、提交流转'
WHERE code = 'employee';

INSERT IGNORE INTO role (code, name, description, permissions) VALUES
('committee', '投委会委员', '审议、查看所有项目、查询知识库、查阅议案',
 JSON_ARRAY('project:read', 'proposal:read', 'qa:ask', 'task:view')),
('legal', '法务', '法务审查权限', JSON_ARRAY('project:read', 'legal:review')),
('secretary', '投委会秘书', '议案登记与秘书事务',
 JSON_ARRAY('project:read', 'proposal:write', 'task:write'));

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL sp_add_column_if_missing('user_role', 'assigned_at',
    'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''角色分配时间''');

CREATE TABLE IF NOT EXISTS project_member (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_in_project VARCHAR(64) NOT NULL DEFAULT 'member',
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS failure_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    path VARCHAR(512) NOT NULL,
    failure_type VARCHAR(64) NOT NULL,
    error_msg TEXT,
    stack_trace TEXT,
    resolved TINYINT(1) NOT NULL DEFAULT 0,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME,
    INDEX idx_path_resolved (path, resolved, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT,
    link VARCHAR(512),
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read, created_at),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(64) NOT NULL,
    total INT NOT NULL DEFAULT 0,
    success INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_created (type, created_at),
    CONSTRAINT fk_ib_user FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS import_error (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    row_num INT NOT NULL,
    column_name VARCHAR(128),
    error_msg VARCHAR(1000) NOT NULL,
    INDEX idx_batch (batch_id),
    CONSTRAINT fk_ie_batch FOREIGN KEY (batch_id) REFERENCES import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- C9. I-RI-26: 网络查源字典种子
-- =============================================================================

INSERT IGNORE INTO dict_type (type_code, type_name, is_system, sort_order, enabled) VALUES
('network_dict_source', '网络查源', 1, 9, 1);

INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order, enabled) VALUES
('network_dict_source', 'baidu_baike',
 '{"baseUrl":"https://baike.baidu.com/api/openapi","timeout":5000}', 0, 1, 1, 1),
('network_dict_source', 'wikipedia_zh',
 '{"baseUrl":"https://zh.wikipedia.org/w/api.php","timeout":5000}', 0, 1, 2, 1);

-- =============================================================================
-- C10. notification: 旧列 read -> is_read
-- =============================================================================

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
-- C11. I-RI-28: project_fact_event INSERT-only 触发器
-- =============================================================================

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
-- 清理工具存储过程
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_add_column_if_missing;
DROP PROCEDURE IF EXISTS sp_add_index_if_missing;

-- =============================================================================
-- 验证
-- =============================================================================

SELECT '--- 旧表关键列 ---' AS section;
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
    (TABLE_NAME = 'project' AND COLUMN_NAME IN ('customer_name','deleted_at','version','archive_status'))
    OR (TABLE_NAME = 'proposal' AND COLUMN_NAME IN ('condition_status','reserved_at','version','deleted_at'))
    OR (TABLE_NAME = 'material' AND COLUMN_NAME IN ('version','deleted_at','archived_at'))
    OR (TABLE_NAME = 'user' AND COLUMN_NAME IN ('version','sensitive_view_enabled','failed_login_count'))
    OR (TABLE_NAME = 'audit_log' AND COLUMN_NAME IN ('type','version','deleted_at'))
    OR (TABLE_NAME = 'material_version' AND COLUMN_NAME = 'parsed_text')
  )
ORDER BY TABLE_NAME, COLUMN_NAME;

SELECT '--- v1.1 新表 ---' AS section;
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'failure_log','user_role','project_member','notification',
    'import_batch','import_error','proposal_series',
    'project_fact','project_fact_event','business_term'
  )
ORDER BY TABLE_NAME;

SELECT 'migrate_260611_01 done' AS status, NOW() AS finished_at;
