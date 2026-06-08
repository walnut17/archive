-- ==========================================================
-- 投委会档案管理系统 - v2 迁移脚本
-- 适用: 在 init.sql 基础上追加
-- 字符集: utf8mb4 / utf8mb4_unicode_ci
-- 数据库: archive_db
-- 依赖: 已存在 role/user/project/proposal/material/material_version
-- ==========================================================

USE archive_db;

-- ==========================================================
-- Section 1: ALTER(3 处)
-- ==========================================================

-- 1.1 material_version 加 FULLTEXT 索引(对应 SUPP P0-2)
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_version'
      AND COLUMN_NAME = 'parsed_text'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE material_version ADD COLUMN parsed_text LONGTEXT COMMENT ''解析后纯文本'' AFTER parse_error',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_version'
      AND INDEX_NAME = 'ft_parsed_text'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE material_version ADD FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 project 加累计金额 + 归档状态
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'project'
      AND COLUMN_NAME = 'initial_amount'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE project
        ADD COLUMN initial_amount DECIMAL(18,2) COMMENT ''初始总金额(元)'' AFTER amount_wan,
        ADD COLUMN remaining_amount DECIMAL(18,2) COMMENT ''剩余金额(自动算,元)'' AFTER initial_amount,
        ADD COLUMN archive_status VARCHAR(16) NOT NULL DEFAULT ''在档'' COMMENT ''在档/已结清/已作废'' AFTER status,
        ADD INDEX idx_project_archive_status (archive_status)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 user 加登录限流字段
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user'
      AND COLUMN_NAME = 'failed_login_count'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE user
        ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 COMMENT ''连续失败次数'' AFTER status,
        ADD COLUMN lockout_until DATETIME COMMENT ''锁定截止时间'' AFTER failed_login_count',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==========================================================
-- Section 2: CREATE NEW TABLES(10 张)
-- ==========================================================

-- (1) chapter_summary
CREATE TABLE IF NOT EXISTS chapter_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_version_id BIGINT NOT NULL COMMENT '所属材料版本 ID',
    chapter_no INT NOT NULL COMMENT '章节序号',
    chapter_title VARCHAR(512),
    content MEDIUMTEXT,
    summary TEXT,
    keywords VARCHAR(512),
    page_start INT,
    page_end INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    FULLTEXT KEY ft_content_summary (content, summary) WITH PARSER ngram,
    INDEX idx_material_version (material_version_id),
    INDEX idx_chapter_no (chapter_no),
    CONSTRAINT fk_cs_material_version
        FOREIGN KEY (material_version_id) REFERENCES material_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节摘要';

-- (2) timepoint
CREATE TABLE IF NOT EXISTS timepoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    material_version_id BIGINT,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT '其他',
    due_at DATE NOT NULL,
    reminder_days VARCHAR(64) DEFAULT '30,7,1,0',
    status VARCHAR(16) NOT NULL DEFAULT '待提醒',
    source_text TEXT,
    source_page INT,
    confidence DECIMAL(3,2),
    extracted_by VARCHAR(16) NOT NULL DEFAULT 'manual',
    owner_id BIGINT,
    remark VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_tp_project (project_id),
    INDEX idx_tp_due (due_at),
    INDEX idx_tp_status (status),
    INDEX idx_tp_type (type),
    INDEX idx_tp_owner (owner_id),
    CONSTRAINT fk_tp_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_material_version
        FOREIGN KEY (material_version_id) REFERENCES material_version(id) ON DELETE SET NULL,
    CONSTRAINT fk_tp_owner
        FOREIGN KEY (owner_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时点';

-- (3) todo
CREATE TABLE IF NOT EXISTS todo (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(16) NOT NULL,
    source_ref_id BIGINT,
    project_id BIGINT,
    owner_id BIGINT,
    priority VARCHAR(16) NOT NULL DEFAULT 'medium',
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    due_at DATETIME,
    completed_at DATETIME,
    remark VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_todo_status (status),
    INDEX idx_todo_due (due_at),
    INDEX idx_todo_owner_status (owner_id, status),
    INDEX idx_todo_project (project_id),
    INDEX idx_todo_source (source, source_ref_id),
    CONSTRAINT fk_todo_project
        FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_todo_owner
        FOREIGN KEY (owner_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待办';

-- (4) trigger_rule
CREATE TABLE IF NOT EXISTS trigger_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    trigger_event VARCHAR(64) NOT NULL,
    trigger_condition VARCHAR(1000) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    priority INT NOT NULL DEFAULT 3,
    last_run_at DATETIME,
    last_match_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_tr_code (code),
    INDEX idx_tr_event_enabled (trigger_event, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发规则';

-- (5) trigger_action
CREATE TABLE IF NOT EXISTS trigger_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    action_template JSON NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_ta_rule (rule_id),
    INDEX idx_ta_rule_sort (rule_id, sort_order),
    CONSTRAINT fk_ta_rule
        FOREIGN KEY (rule_id) REFERENCES trigger_rule(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发动作';

-- (6) extraction_method
CREATE TABLE IF NOT EXISTS extraction_method (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    apply_to VARCHAR(32) NOT NULL DEFAULT 'material',
    prompt_template TEXT NOT NULL,
    output_schema JSON NOT NULL,
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_em_code (code),
    INDEX idx_em_apply_enabled (apply_to, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段抽取方法';

-- (7) comparison_method
CREATE TABLE IF NOT EXISTS comparison_method (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    from_type VARCHAR(32) NOT NULL DEFAULT '立项',
    to_type VARCHAR(32) NOT NULL DEFAULT '申请',
    prompt_template TEXT NOT NULL,
    output_schema JSON NOT NULL,
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_cm_code (code),
    INDEX idx_cm_from_to (from_type, to_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对比方法';

-- (8) dict_type
CREATE TABLE IF NOT EXISTS dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(64) NOT NULL UNIQUE,
    type_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    is_system TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    INDEX idx_dt_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典分类';

-- (9) dict_item
CREATE TABLE IF NOT EXISTS dict_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(64) NOT NULL,
    item_key VARCHAR(64) NOT NULL,
    item_value VARCHAR(256) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    is_system TINYINT(1) NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    UNIQUE KEY uk_di_type_key (type_code, item_key),
    INDEX idx_di_type_enabled (type_code, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项';

-- (10) audit_log
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id BIGINT,
    old_value JSON,
    new_value JSON,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    request_id VARCHAR(64),
    extra JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_al_actor_created (actor, created_at),
    INDEX idx_al_action_created (action, created_at),
    INDEX idx_al_entity (entity_type, entity_id),
    INDEX idx_al_request (request_id),
    INDEX idx_al_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';

-- ==========================================================
-- Section 3: 种子数据
-- ==========================================================

-- 3.1 字典预置
INSERT IGNORE INTO dict_type (type_code, type_name, is_system, sort_order) VALUES
('project_category',  '项目类别',  1, 1),
('project_status',    '项目状态',  1, 2),
('material_category', '材料类别',  1, 3),
('material_status',   '材料状态',  1, 4),
('proposal_type',     '议案类型',  1, 5),
('proposal_status',   '议案状态',  1, 6),
('timepoint_type',    '时点类型',  1, 7),
('todo_priority',     '待办优先级', 1, 8);

-- 项目类别
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('project_category', '股权类', '股权类', 0, 1, 1),
('project_category', '固收类', '固收类', 0, 1, 2),
('project_category', '混合类', '混合类', 0, 1, 3),
('project_category', '不动产类', '不动产类', 0, 1, 4),
('project_category', '其他', '其他', 1, 1, 99);

-- 项目状态
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('project_status', '草稿', '草稿', 1, 1, 1),
('project_status', '待审议', '待审议', 0, 1, 2),
('project_status', '审议中', '审议中', 0, 1, 3),
('project_status', '通过', '通过', 0, 1, 4),
('project_status', '暂缓', '暂缓', 0, 1, 5),
('project_status', '否决', '否决', 0, 1, 6),
('project_status', '撤回', '撤回', 0, 1, 7);

-- 材料类别
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('material_category', '尽调报告', '尽调报告', 0, 1, 1),
('material_category', '法律意见', '法律意见', 0, 1, 2),
('material_category', '财务审计', '财务审计', 0, 1, 3),
('material_category', '风险评估', '风险评估', 0, 1, 4),
('material_category', '投委会决议', '投委会决议', 0, 1, 5),
('material_category', '立项报告', '立项报告', 0, 1, 6),
('material_category', '申请报告', '申请报告', 0, 1, 7),
('material_category', '结清报告', '结清报告', 0, 1, 8),
('material_category', '付款凭证', '付款凭证', 0, 1, 9),
('material_category', '收款凭证', '收款凭证', 0, 1, 10),
('material_category', '其他', '其他', 1, 1, 99);

-- 材料状态
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('material_status', '草稿', '草稿', 1, 1, 1),
('material_status', '评审中', '评审中', 0, 1, 2),
('material_status', '已通过', '已通过', 0, 1, 3),
('material_status', '已归档', '已归档', 0, 1, 4),
('material_status', '已作废', '已作废', 0, 1, 5);

-- 议案类型
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('proposal_type', '主体', '主体', 1, 1, 1),
('proposal_type', '担保', '担保', 0, 1, 2),
('proposal_type', '联合', '联合', 0, 1, 3),
('proposal_type', '调整', '调整', 0, 1, 4),
('proposal_type', '终止', '终止', 0, 1, 5),
('proposal_type', '其他', '其他', 0, 1, 6);

-- 议案状态
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('proposal_status', '草稿', '草稿', 1, 1, 1),
('proposal_status', '已提交', '已提交', 0, 1, 2),
('proposal_status', '审议中', '审议中', 0, 1, 3),
('proposal_status', '通过', '通过', 0, 1, 4),
('proposal_status', '暂缓', '暂缓', 0, 1, 5),
('proposal_status', '否决', '否决', 0, 1, 6),
('proposal_status', '撤回', '撤回', 0, 1, 7);

-- 时点类型
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('timepoint_type', '到期', '到期', 0, 1, 1),
('timepoint_type', '审议', '审议', 0, 1, 2),
('timepoint_type', '披露', '披露', 0, 1, 3),
('timepoint_type', '付款', '付款', 0, 1, 4),
('timepoint_type', '法律意见', '法律意见', 0, 1, 5),
('timepoint_type', '工商变更', '工商变更', 0, 1, 6),
('timepoint_type', '其他', '其他', 1, 1, 99);

-- 待办优先级
INSERT IGNORE INTO dict_item (type_code, item_key, item_value, is_default, is_system, sort_order) VALUES
('todo_priority', 'low', '低', 0, 1, 1),
('todo_priority', 'medium', '中', 1, 1, 2),
('todo_priority', 'high', '高', 0, 1, 3),
('todo_priority', 'urgent', '紧急', 0, 1, 4);

-- 3.2 字段抽取方法预置
INSERT IGNORE INTO extraction_method (code, name, description, apply_to, prompt_template, output_schema, builtin, sort_order) VALUES
('DEFAULT_PROJECT_FIELDS', '项目基础字段', '从立项报告抽项目名称/初始总金额/起投时间/服务商名称/客户名称',
 'material',
 '请从以下材料中提取项目关键字段:\n1. 项目名称\n2. 初始总金额(元)\n3. 起投时间(YYYY-MM-DD)\n4. 服务商名称\n5. 客户名称\n\n材料标题:${material_title}\n材料正文:\n${material_content}',
 JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
        '项目名称', JSON_OBJECT('type', 'string'),
        '初始总金额', JSON_OBJECT('type', 'number'),
        '起投时间', JSON_OBJECT('type', 'string'),
        '服务商名称', JSON_OBJECT('type', 'string'),
        '客户名称', JSON_OBJECT('type', 'string')
    )
 ), 1, 1),
('DEFAULT_TIMEPOINT', '时点抽取', '从材料中提取带日期的事项',
 'material',
 '请从以下材料中提取所有带日期的事项,每条包含截止日期和事项描述:\n\n材料:\n${material_content}',
 JSON_OBJECT(
    'type', 'array',
    'items', JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
            '截止日期', JSON_OBJECT('type', 'string'),
            '事项', JSON_OBJECT('type', 'string'),
            '置信度', JSON_OBJECT('type', 'number', 'minimum', 0, 'maximum', 1)
        )
    )
 ), 1, 2),
('DEFAULT_PROPOSAL_SUMMARY', '议案摘要', '从材料生成 200-500 字议案摘要',
 'proposal',
 '请从以下材料内容中提取关键信息,生成 200-500 字的议案摘要,包括:项目背景、主要风险、审议要点\n\n材料:\n${material_content}',
 JSON_OBJECT('type', 'string'), 1, 3);

-- 3.3 对比方法预置
INSERT IGNORE INTO comparison_method (code, name, description, from_type, to_type, prompt_template, output_schema, builtin, sort_order) VALUES
('DEFAULT_QA_VERIFY', '待落实问题 Q&A 验证', '对每个待落实问题,验证在目标报告里是否解决',
 '立项', '申请',
 '以下是立项报告的"待落实问题"清单:\n${from_questions}\n\n以下是申请报告内容:\n${to_content}\n\n请对每个问题判断:已解决 / 部分解决 / 未解决,引用申请报告原文。',
 JSON_OBJECT(
    'type', 'array',
    'items', JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
            '问题', JSON_OBJECT('type', 'string'),
            '状态', JSON_OBJECT('type', 'string', 'enum', JSON_ARRAY('已解决', '部分解决', '未解决')),
            '引用', JSON_OBJECT('type', 'string'),
            '置信度', JSON_OBJECT('type', 'number')
        )
    )
 ), 1, 1);

-- 3.4 触发规则预置
INSERT IGNORE INTO trigger_rule (code, name, description, trigger_event, trigger_condition, enabled, builtin, priority) VALUES
('RECEIPT_AUTO_BOOK', '收款凭证自动走账', '上传收款凭证后,自动给财务生成走账待办',
 'MaterialCategorizedEvent', 'event.material.category == ''收款凭证''', 1, 1, 3),
('PAYMENT_REDUCE_AMOUNT', '付款凭证触发累减', '上传付款凭证后,自动给财务生成记账待办,并触发金额重算',
 'MaterialCategorizedEvent', 'event.material.category == ''付款凭证''', 1, 1, 3),
('TIMEPOINT_30D', '时点 30 天前提醒', '时点到期前 30 天,生成待办',
 'TimepointApproachingEvent', 'event.daysToDue == 30', 1, 1, 2),
('TIMEPOINT_7D', '时点 7 天前提醒', '时点到期前 7 天,生成紧急待办',
 'TimepointApproachingEvent', 'event.daysToDue == 7', 1, 1, 4),
('TIMEPOINT_1D', '时点 1 天前提醒', '时点到期前 1 天,生成紧急待办',
 'TimepointApproachingEvent', 'event.daysToDue == 1', 1, 1, 5),
('PROPOSAL_AUTO_SUMMARY', '议案提交自动摘要', '议案从草稿变已提交,自动从材料生成摘要',
 'ProposalStatusChangedEvent', 'event.newStatus == ''已提交''', 1, 1, 3);

-- 配套动作
INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '走账:${event.material.title}',
    'owner_role', 'finance',
    'due_days', 3,
    'priority', 'medium'
), 1 FROM trigger_rule WHERE code = 'RECEIPT_AUTO_BOOK';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '记账:${event.material.title}',
    'owner_role', 'finance',
    'due_days', 3,
    'priority', 'medium'
), 1 FROM trigger_rule WHERE code = 'PAYMENT_REDUCE_AMOUNT';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '[30天] ${event.timepoint.name}',
    'owner_role', '${event.timepoint.ownerRole}',
    'due_days', 30,
    'priority', 'medium'
), 1 FROM trigger_rule WHERE code = 'TIMEPOINT_30D';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '[7天] ${event.timepoint.name}',
    'owner_role', '${event.timepoint.ownerRole}',
    'due_days', 7,
    'priority', 'high'
), 1 FROM trigger_rule WHERE code = 'TIMEPOINT_7D';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'create_todo', JSON_OBJECT(
    'todo_name', '[明天] ${event.timepoint.name}',
    'owner_role', '${event.timepoint.ownerRole}',
    'due_days', 1,
    'priority', 'urgent'
), 1 FROM trigger_rule WHERE code = 'TIMEPOINT_1D';

INSERT INTO trigger_action (rule_id, action_type, action_template, sort_order)
SELECT id, 'auto_summarize', JSON_OBJECT('proposal_id', '${event.proposal.id}'), 1
FROM trigger_rule WHERE code = 'PROPOSAL_AUTO_SUMMARY';

-- ==========================================================
-- Section 4: 验证
-- ==========================================================
SELECT 'v2-schema OK' AS migration_status,
       COUNT(*) AS new_tables
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (
    'chapter_summary', 'timepoint', 'todo', 'trigger_rule', 'trigger_action',
    'extraction_method', 'comparison_method', 'dict_type', 'dict_item', 'audit_log'
);
